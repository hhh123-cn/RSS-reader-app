package com.b3.rssreader.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 文章实体 —— 对应数据库表 articles
 *
 * 由 RSS XML 解析得到的文章离线缓存到本地，
 * 即使断网也能阅读（离线缓存需求）。
 *
 * 关键设计：
 * - 外键 feed_id → feeds.id，onDelete = CASCADE：
 *   删除订阅源时自动级联删除其所有文章，避免孤儿数据
 * - (guid, feed_id) 联合唯一索引：判断"这篇文章是否已存在"的依据。
 *   配合 DAO 里的 IGNORE 冲突策略实现"刷新时只插入新文章"，
 *   同时保住已读/收藏状态不被覆盖（RSS 阅读器的经典坑，详见 ArticleDao 注释）
 */
@Entity(
    tableName = "articles",
    foreignKeys = [
        ForeignKey(
            entity = Feed::class,
            parentColumns = ["id"],
            childColumns = ["feed_id"],
            onDelete = ForeignKey.CASCADE // 删订阅源 → 自动删文章
        )
    ],
    indices = [
        Index(value = ["feed_id"]),                       // 按订阅源查文章时加速
        Index(value = ["feed_id", "guid"], unique = true) // 去重依据
    ]
)
data class Article(
    /** 主键，自增且不复用 */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 所属订阅源 id（外键） */
    @ColumnInfo(name = "feed_id")
    val feedId: Long,

    /**
     * 文章在订阅源内的唯一标识。
     * RSS 规范中 <guid> 是唯一标识，但部分源缺失 guid，
     * 解析阶段（阶段 3）会用 link 兜底，保证该字段恒有值。
     */
    @ColumnInfo(name = "guid")
    val guid: String,

    /** 文章标题 */
    @ColumnInfo(name = "title")
    val title: String,

    /** 原文链接（跳转浏览器阅读全文） */
    @ColumnInfo(name = "link")
    val link: String,

    /** 作者名（可为空） */
    @ColumnInfo(name = "author")
    val author: String? = null,

    /** 摘要 HTML（RSS <description> 标签） */
    @ColumnInfo(name = "description")
    val description: String? = null,

    /** 正文 HTML（RSS <content:encoded> 标签，部分源才有，可为空） */
    @ColumnInfo(name = "content")
    val content: String? = null,

    /** 发布时间戳（毫秒）。解析失败时可为空，列表排序会把它排到最后 */
    @ColumnInfo(name = "pub_date")
    val pubDate: Long? = null,

    /**
     * 已读标记。defaultValue 给 SQL 默认值 0（false），
     * 即使代码忘了赋值，数据库也不会出现 NULL
     */
    @ColumnInfo(name = "is_read", defaultValue = "0")
    val isRead: Boolean = false,

    /** 收藏标记（自选功能：文章收藏） */
    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    val isFavorite: Boolean = false
)
