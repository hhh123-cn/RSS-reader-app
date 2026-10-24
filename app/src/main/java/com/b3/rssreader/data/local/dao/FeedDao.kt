package com.b3.rssreader.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.b3.rssreader.data.local.entity.Feed
import kotlinx.coroutines.flow.Flow

/**
 * 订阅源数据访问对象（DAO）
 *
 * 约定：
 * - 需要"界面自动跟随数据变化"的查询 → 返回 Flow（Room 会在数据变化时自动重新发射）
 * - 一次性操作（插入/更新/删除/按 id 查一条）→ suspend 函数，跑在 Room 的 IO 线程
 *
 * 本 DAO 文件还包含一个聚合查询结果类 FeedWithStats（见文件底部）。
 */
@Dao
interface FeedDao {

    /**
     * 插入订阅源。
     * OnConflictStrategy.ABORT：url 重复时抛出 SQLiteConstraintException，
     * 由上层捕获后提示用户"该订阅源已存在"（重复添加的提示逻辑放阶段 3）
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(feed: Feed): Long

    /** 更新订阅源（改标题、改地址等） */
    @Update
    suspend fun update(feed: Feed)

    /** 删除订阅源（外键 CASCADE 会自动删除其下所有文章） */
    @Delete
    suspend fun delete(feed: Feed)

    /** 观察全部订阅源：新增/修改/删除都会触发界面刷新 */
    @Query("SELECT * FROM feeds ORDER BY created_at DESC")
    fun observeAll(): Flow<List<Feed>>

    /** 观察订阅源列表 + 每源的"文章总数 / 未读数"统计（列表页直接展示） */
    @Query(
        """
        SELECT
            feeds.*,
            COUNT(articles.id) AS article_count,
            COALESCE(SUM(CASE WHEN articles.is_read = 0 THEN 1 ELSE 0 END), 0) AS unread_count
        FROM feeds
        LEFT JOIN articles ON feeds.id = articles.feed_id
        GROUP BY feeds.id
        ORDER BY feeds.created_at DESC
        """
    )
    fun observeAllWithStats(): Flow<List<FeedWithStats>>

    /** 按 id 查单个订阅源（编辑页回显数据） */
    @Query("SELECT * FROM feeds WHERE id = :feedId LIMIT 1")
    suspend fun getById(feedId: Long): Feed?

    /** 按 url 查订阅源（添加前判断是否已存在） */
    @Query("SELECT * FROM feeds WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): Feed?

    /**
     * 设置单个订阅源的通知开关（自选功能）。
     * 主 App 侧由 Repository 调用；伴生模块侧通过 ContentProvider 跨进程调用
     * （RssContentProvider 的 feeds/# update 接口），两边写的是同一张表。
     */
    @Query("UPDATE feeds SET notification_enabled = :enabled WHERE id = :feedId")
    suspend fun setNotificationEnabled(feedId: Long, enabled: Boolean)
}

/**
 * 订阅源 + 统计信息的聚合结果。
 *
 * @Embedded：把 Feed 的列"摊平"进查询结果；
 * @ColumnInfo：对应 SQL 中 AS 起的别名。
 *
 * 注意：LEFT JOIN 时没有文章的订阅源 COUNT 为 0、SUM 为 NULL，
 * 所以 SQL 里用 COALESCE 把 NULL 转成 0，否则映射到非空 Int 会崩溃。
 */
data class FeedWithStats(
    @Embedded val feed: Feed,
    @ColumnInfo(name = "article_count") val articleCount: Int,
    @ColumnInfo(name = "unread_count") val unreadCount: Int
)
