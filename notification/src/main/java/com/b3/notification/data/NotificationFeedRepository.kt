package com.b3.notification.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 伴生模块的订阅源仓库 —— 全部通过主 App 的 ContentProvider 跨进程访问
 *
 * 与主 App 的 FeedRepository（直接操作 Room）不同：
 * 本模块不持有数据库，只能通过 ContentResolver 读写主 App 导出的数据，
 * 这正是"两个 App 共享数据库"的 Android 标准实现。
 *
 * 注意：ContentProvider 查询是"快照式"（无 Flow 响应流），
 * 数据变化后需要重新调用 fetchFeeds() 刷新界面。
 */
class NotificationFeedRepository(private val context: Context) {

    /** 订阅源信息（仅取设置页需要的字段） */
    data class FeedInfo(
        val id: Long,
        val title: String,
        val url: String,
        val notificationEnabled: Boolean
    )

    /** 新增文章信息（后台检测推送用） */
    data class NewArticle(
        val id: Long,
        val feedId: Long,
        val title: String
    )

    /**
     * 拉取全部订阅源（跨进程查询主 App 数据库）。
     *
     * @throws IllegalStateException 主 App 未安装或 Provider 不可用时抛出，
     *         message 可直接展示给用户
     */
    suspend fun fetchFeeds(): List<FeedInfo> = withContext(Dispatchers.IO) {
        val cursor = context.contentResolver.query(URI_FEEDS, null, null, null, null)
            ?: throw IllegalStateException("无法访问主应用数据，请确认已安装 B3 RSS 阅读器")

        val feeds = mutableListOf<FeedInfo>()
        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow("id")
            val titleCol = c.getColumnIndexOrThrow("title")
            val urlCol = c.getColumnIndexOrThrow("url")
            val enabledCol = c.getColumnIndexOrThrow("notification_enabled")
            while (c.moveToNext()) {
                feeds += FeedInfo(
                    id = c.getLong(idCol),
                    title = c.getString(titleCol) ?: "",
                    url = c.getString(urlCol) ?: "",
                    notificationEnabled = c.getInt(enabledCol) == 1
                )
            }
        }
        feeds
    }

    /**
     * 设置单个订阅源的通知开关（自选功能）。
     * 通过 Provider 的 feeds/# update 接口跨进程写回主 App 数据库，
     * 后台检测新文章时据此决定该源是否推送。
     */
    suspend fun setNotificationEnabled(feedId: Long, enabled: Boolean) =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply { put("notification_enabled", enabled) }
            context.contentResolver.update(uriFeed(feedId), values, null, null)
        }

    /**
     * 查询主 App 数据库中所有"新增文章"（is_new = 1）。
     * 后台检测 Worker 的数据源。
     *
     * @throws IllegalStateException 主 App 未安装或 Provider 不可用
     */
    suspend fun fetchNewArticles(): List<NewArticle> = withContext(Dispatchers.IO) {
        // 拼 query 参数：?new_only=true → Provider 端翻译成 WHERE is_new = 1
        val uri = URI_ARTICLES.buildUpon()
            .appendQueryParameter("new_only", "true")
            .build()

        val cursor = context.contentResolver.query(uri, null, null, null, null)
            ?: throw IllegalStateException("无法访问主应用数据，请确认已安装 B3 RSS 阅读器")

        val articles = mutableListOf<NewArticle>()
        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow("id")
            val feedIdCol = c.getColumnIndexOrThrow("feed_id")
            val titleCol = c.getColumnIndexOrThrow("title")
            while (c.moveToNext()) {
                articles += NewArticle(
                    id = c.getLong(idCol),
                    feedId = c.getLong(feedIdCol),
                    title = c.getString(titleCol) ?: ""
                )
            }
        }
        articles
    }

    /**
     * 清除文章的"新增"标记（is_new = false）。
     * 后台检测推送完毕后调用，防止下一轮检测重复推送同一篇文章。
     */
    suspend fun markArticleNotNew(articleId: Long) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("is_new", false) }
        context.contentResolver.update(uriArticle(articleId), values, null, null)
    }

    companion object {
        /**
         * 主 App ContentProvider 的 authority。
         * 与主 App RssContentProvider.AUTHORITY 保持一致（两模块唯一的字符串耦合点）。
         */
        const val PROVIDER_AUTHORITY = "com.b3.rssreader.provider"

        /** 订阅源列表 Uri */
        val URI_FEEDS: Uri = Uri.parse("content://$PROVIDER_AUTHORITY/feeds")

        /** 文章列表 Uri（配 query 参数使用） */
        val URI_ARTICLES: Uri = Uri.parse("content://$PROVIDER_AUTHORITY/articles")

        /** 单个订阅源 Uri（更新通知开关用） */
        fun uriFeed(id: Long): Uri = Uri.parse("content://$PROVIDER_AUTHORITY/feeds/$id")

        /** 单篇文章 Uri（清新增标记、标已读用） */
        fun uriArticle(id: Long): Uri = Uri.parse("content://$PROVIDER_AUTHORITY/articles/$id")
    }
}
