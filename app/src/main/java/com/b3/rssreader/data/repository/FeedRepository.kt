package com.b3.rssreader.data.repository

import android.content.Context
import com.b3.rssreader.data.local.AppDatabase
import com.b3.rssreader.data.local.dao.FeedDao
import com.b3.rssreader.data.local.dao.FeedWithStats
import com.b3.rssreader.data.local.entity.Feed
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
class FeedRepository private constructor(private val feedDao: FeedDao) {

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

    /** 更新订阅源信息（编辑保存） */
    suspend fun updateFeed(feed: Feed) = withContext(Dispatchers.IO) {
        feedDao.update(feed)
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

    companion object {
        @Volatile
        private var INSTANCE: FeedRepository? = null

        /** 获取仓库单例（双重检查锁，同数据库单例套路） */
        fun getInstance(context: Context): FeedRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: FeedRepository(
                    AppDatabase.getInstance(context).feedDao()
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
