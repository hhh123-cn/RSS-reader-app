package com.b3.rssreader.data.repository

import android.content.Context
import com.b3.rssreader.data.local.AppDatabase
import com.b3.rssreader.data.local.dao.ArticleDao
import com.b3.rssreader.data.local.dao.FeedDao
import com.b3.rssreader.data.local.dao.FeedWithStats
import com.b3.rssreader.data.local.entity.Feed
import com.b3.rssreader.data.remote.RssHttpClient
import com.b3.rssreader.data.remote.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 订阅源仓库 —— 订阅源数据的唯一入口（MVVM 中的 Repository 层）
 *
 * 分层关系：
 *   UI(Activity/Fragment) → ViewModel → FeedRepository → FeedDao → Room 数据库
 *   UI 永远不直接碰 DAO，保证数据访问逻辑集中、可测试、可替换。
 *
 * 协程约定（本项目统一遵循）：
 * - 查询类操作返回 Flow：Room 原生支持 Flow，数据一变自动推送给界面，
 *   无需手动刷新。Flow 本身不阻塞线程，collect 时才执行查询。
 * - 写操作（增删改）用 suspend 函数，内部 withContext(Dispatchers.IO)
 *   切到 IO 线程池，绝不阻塞主线程（ANR 风险点）。
 *
 * 单例说明：课程设计用"懒加载单例"简化依赖管理；
 * 正式项目推荐用 Hilt 等依赖注入框架按需提供实例。
 */
class FeedRepository private constructor(
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao
) {

    /** 观察全部订阅源（编辑态数据：不含统计） */
    fun observeAll(): Flow<List<Feed>> = feedDao.observeAll()

    /** 观察订阅源列表 + 每源的"文章总数 / 未读数"统计（订阅源列表页使用） */
    fun observeAllWithStats(): Flow<List<FeedWithStats>> = feedDao.observeAllWithStats()

    /** 按 id 查单个订阅源（编辑页回显） */
    suspend fun getById(feedId: Long): Feed? = withContext(Dispatchers.IO) {
        feedDao.getById(feedId)
    }

    /**
     * 添加订阅源。
     *
     * 返回值 Result：成功携带新订阅源的 id；失败（地址已存在）携带
     * DuplicateFeedException。用 Result 而不是抛异常，是为了让
     * ViewModel/UI 层可以优雅地提示"该订阅源已存在"，而不是崩溃。
     *
     * 双重防重：
     * 1. 先 getByUrl 查询——覆盖绝大多数场景，能给出友好错误；
     * 2. 数据库 url 唯一索引兜底——并发场景下（例如极端情况下两个
     *    协程同时添加），唯一约束会抛出 SQLiteConstraintException，
     *    这里捕获并同样转成 DuplicateFeedException。
     */
    suspend fun addFeed(
        title: String,
        url: String,
        description: String? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        if (feedDao.getByUrl(url) != null) {
            return@withContext Result.failure(DuplicateFeedException(url))
        }
        try {
            val id = feedDao.insert(
                Feed(title = title, url = url, description = description)
            )
            Result.success(id)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            // 唯一索引兜底：并发重复添加
            Result.failure(DuplicateFeedException(url))
        }
    }

    /**
     * 更新订阅源信息（编辑保存）。
     *
     * 边界场景防护：编辑时把 url 改成"另一个已存在订阅源的地址"，
     * 数据库 url 唯一索引会抛 SQLiteConstraintException——
     * 不处理会直接崩溃，这里捕获并转成 Result.failure(DuplicateFeedException)，
     * ViewModel 层提示用户"地址已存在"。
     */
    suspend fun updateFeed(feed: Feed): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            feedDao.update(feed)
            Result.success(Unit)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            Result.failure(DuplicateFeedException(feed.url))
        }
    }

    /**
     * 删除订阅源。
     * 数据库外键 onDelete = CASCADE 会自动级联删除其下所有文章，
     * 无需（也不要）手动先删文章。
     */
    suspend fun deleteFeed(feed: Feed) = withContext(Dispatchers.IO) {
        feedDao.delete(feed)
    }

    /**
     * 设置单个订阅源的通知开关（自选功能，主 App 侧入口）。
     * 伴生模块设置页也能改同一字段（走 ContentProvider），最终落库的都是这张表。
     */
    suspend fun setNotificationEnabled(feedId: Long, enabled: Boolean) =
        withContext(Dispatchers.IO) {
            feedDao.setNotificationEnabled(feedId, enabled)
        }

    /** 订阅源总数（设置页统计） */
    suspend fun countFeeds(): Int = withContext(Dispatchers.IO) {
        feedDao.countFeeds()
    }

    /**
     * 刷新单个订阅源：拉取 → 解析 → 入库 的完整链路（第 8、9 阶段能力的合流点）。
     *
     * 调用方：RssRefreshWorker（后台）与手动刷新（前台）。
     *
     * @return Result<Int>：成功携带"本次新增文章数"（旧文章被 IGNORE 跳过，不计入）
     *         失败携带 NetworkException / RssParseException（message 可直接展示）
     */
    suspend fun refreshFeed(feed: Feed): Result<Int> = withContext(Dispatchers.IO) {
        // 1. 拉取原始 XML（网络异常在此被翻译成中文提示）
        val xmlResult = RssHttpClient.fetchRssXml(feed.url)

        xmlResult.fold(
            onSuccess = { xml ->
                // 2. 解析 XML → Article 列表（解析异常同样被翻译）
                RssParser.parse(xml, feed.id).fold(
                    onSuccess = { parsed ->
                        // 3. 入库：IGNORE 策略自动去重，只插新文章，保住已读/收藏状态。
                        //    返回的 rowid 列表里 -1 = 冲突跳过的旧文章，
                        //    统计大于 0 的数量即"本次新增文章数"——
                        //    新文章同时带上了 is_new = true 标记（实体默认值），
                        //    供伴生模块查询识别。
                        val insertedIds = articleDao.insertAll(parsed.articles)
                        val newCount = insertedIds.count { it != -1L }

                        // 4. 回填订阅源信息：用户没自定义名称（标题仍是 url）时，
                        //    用 RSS 频道名替换，列表更好看
                        val channelTitle = parsed.channelTitle
                        if (!channelTitle.isNullOrBlank() && feed.title == feed.url) {
                            feedDao.update(feed.copy(title = channelTitle))
                        }

                        Result.success(newCount)
                    },
                    onFailure = { e -> Result.failure(e) }
                )
            },
            onFailure = { e -> Result.failure(e) }
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: FeedRepository? = null

        /** 获取仓库单例（双重检查锁，同数据库单例套路） */
        fun getInstance(context: Context): FeedRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: FeedRepository(
                    AppDatabase.getInstance(context).feedDao(),
                    AppDatabase.getInstance(context).articleDao()
                ).also { INSTANCE = it }
            }
    }
}

/**
 * 自定义业务异常：订阅源地址重复。
 * 继承 Exception 而非 RuntimeException，明确表达"这是可预期的业务失败"，
 * ViewModel 捕获后转成界面提示。
 */
class DuplicateFeedException(url: String) : Exception("该订阅源已存在: $url")
