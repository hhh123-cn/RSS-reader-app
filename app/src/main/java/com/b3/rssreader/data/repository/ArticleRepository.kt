package com.b3.rssreader.data.repository

import android.content.Context
import com.b3.rssreader.data.local.AppDatabase
import com.b3.rssreader.data.local.dao.ArticleDao
import com.b3.rssreader.data.local.entity.Article
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 文章仓库 —— 文章数据的唯一入口（Repository 层）
 *
 * 职责：
 * - 离线缓存文章的读写（阶段 2 只接数据库；阶段 3 的"拉取+解析"结果也经这里入库）
 * - 已读/未读、收藏状态的修改
 * - 以 Flow 形式向 ViewModel 暴露可观察数据
 *
 * 协程约定与 FeedRepository 一致：
 * 查询 → Flow 懒执行不阻塞；写操作 → suspend + Dispatchers.IO。
 */
class ArticleRepository private constructor(private val articleDao: ArticleDao) {

    /** 观察某订阅源下的文章（文章列表页），按发布时间倒序 */
    fun observeByFeed(feedId: Long): Flow<List<Article>> = articleDao.observeByFeed(feedId)

    /** 观察全部文章（"全部"聚合页），按发布时间倒序 */
    fun observeAll(): Flow<List<Article>> = articleDao.observeAll()

    /** 观察收藏文章（收藏夹页，自选功能） */
    fun observeFavorites(): Flow<List<Article>> = articleDao.observeFavorites()

    /** 观察全局未读总数（App 内角标/主页提示） */
    fun observeUnreadCount(): Flow<Int> = articleDao.observeUnreadCount()

    /** 按 id 查单篇文章（详情页；伴生模块点击通知跳转后也用它定位文章） */
    suspend fun getById(articleId: Long): Article? = withContext(Dispatchers.IO) {
        articleDao.getById(articleId)
    }

    /**
     * 批量缓存文章（阶段 3 拉取解析后调用）。
     * DAO 内部用 IGNORE 冲突策略：guid 已存在的旧文章自动跳过，
     * 既去重又不覆盖已读/收藏状态。
     */
    suspend fun insertArticles(articles: List<Article>) = withContext(Dispatchers.IO) {
        if (articles.isNotEmpty()) {
            articleDao.insertAll(articles)
        }
    }

    /** 标记单篇已读/未读 */
    suspend fun markRead(articleId: Long, isRead: Boolean) = withContext(Dispatchers.IO) {
        articleDao.markRead(articleId, isRead)
    }

    /** 某订阅源全部标为已读 */
    suspend fun markAllRead(feedId: Long) = withContext(Dispatchers.IO) {
        articleDao.markAllRead(feedId)
    }

    /** 收藏 / 取消收藏 */
    suspend fun markFavorite(articleId: Long, isFavorite: Boolean) =
        withContext(Dispatchers.IO) {
            articleDao.markFavorite(articleId, isFavorite)
        }

    companion object {
        @Volatile
        private var INSTANCE: ArticleRepository? = null

        /** 获取仓库单例 */
        fun getInstance(context: Context): ArticleRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ArticleRepository(
                    AppDatabase.getInstance(context).articleDao()
                ).also { INSTANCE = it }
            }
    }
}
