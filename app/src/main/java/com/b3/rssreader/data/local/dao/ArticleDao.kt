package com.b3.rssreader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.b3.rssreader.data.local.entity.Article
import kotlinx.coroutines.flow.Flow

/**
 * 文章数据访问对象（DAO）
 */
@Dao
interface ArticleDao {

    /**
     * 批量插入文章（刷新时一次写入一批）。
     *
     * 【关键】冲突策略用 IGNORE 而不是 REPLACE，这是 RSS 阅读器最经典的坑：
     * - REPLACE：遇到 (feed_id, guid) 重复时"删掉旧行、插入新行"，
     *   看起来能更新内容，但旧行上的 is_read / is_favorite 会随之丢失，
     *   表现为"刷新一次，已读的全部变未读"。
     * - IGNORE：重复的整行跳过，只插入新文章，已读/收藏状态完好保留。
     *   RSS 文章发布后内容一般不再变化，跳过旧文章没有实际损失。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(articles: List<Article>)

    /** 观察某个订阅源下的全部文章，按发布时间倒序（列表页） */
    @Query("SELECT * FROM articles WHERE feed_id = :feedId ORDER BY pub_date DESC")
    fun observeByFeed(feedId: Long): Flow<List<Article>>

    /** 观察全部文章（"全部"页），按发布时间倒序 */
    @Query("SELECT * FROM articles ORDER BY pub_date DESC")
    fun observeAll(): Flow<List<Article>>

    /** 观察收藏的文章（自选功能：收藏夹） */
    @Query("SELECT * FROM articles WHERE is_favorite = 1 ORDER BY pub_date DESC")
    fun observeFavorites(): Flow<List<Article>>

    /** 按 id 查单篇文章（详情页；也供伴生模块跳转文章详情时定位文章） */
    @Query("SELECT * FROM articles WHERE id = :articleId LIMIT 1")
    suspend fun getById(articleId: Long): Article?

    /** 标记单篇已读/未读（参数化，列表点击与详情页共用） */
    @Query("UPDATE articles SET is_read = :isRead WHERE id = :articleId")
    suspend fun markRead(articleId: Long, isRead: Boolean)

    /** 某个订阅源下全部标为已读（"全部已读"按钮） */
    @Query("UPDATE articles SET is_read = 1 WHERE feed_id = :feedId")
    suspend fun markAllRead(feedId: Long)

    /** 收藏/取消收藏单篇文章 */
    @Query("UPDATE articles SET is_favorite = :isFavorite WHERE id = :articleId")
    suspend fun markFavorite(articleId: Long, isFavorite: Boolean)

    /** 观察全局未读总数（App 角标/主页提示用） */
    @Query("SELECT COUNT(*) FROM articles WHERE is_read = 0")
    fun observeUnreadCount(): Flow<Int>
}
