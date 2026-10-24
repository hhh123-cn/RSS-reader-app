package com.b3.rssreader.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 订阅源实体 —— 对应数据库表 feeds
 *
 * 一个订阅源 = 一个 RSS 地址，例如少数派 https://sspai.com/feed。
 * 用户可增删改查订阅源，每篇文章都属于某个订阅源。
 *
 * Room 注解说明：
 * - @Entity：标记为数据库表，tableName 指定表名（默认是类名）
 * - @PrimaryKey(autoGenerate = true)：id 自增主键，由 SQLite 的 AUTOINCREMENT 实现，
 *   保证 id 只增不复用（伴生模块用"记住最大 id"的方式检测新文章，依赖这个特性）
 * - @ColumnInfo：指定列名；Kotlin 属性默认用属性名做列名
 * - Index(unique = true)：url 唯一索引，防止重复订阅同一个地址
 */
@Entity(
    tableName = "feeds",
    indices = [Index(value = ["url"], unique = true)]
)
data class Feed(
    /** 主键。0 表示"尚未插入数据库"，插入后由 Room 回填真实 id */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 订阅源标题：优先用 RSS 解析出的站点名，解析失败时用 url 兜底 */
    @ColumnInfo(name = "title")
    val title: String,

    /** RSS 地址（唯一，重复添加会触发唯一约束） */
    @ColumnInfo(name = "url")
    val url: String,

    /** 站点描述（RSS <description> 标签，可为空） */
    @ColumnInfo(name = "description")
    val description: String? = null,

    /**
     * 是否开启新文章通知（自选功能：按单个订阅源设置是否开启通知）。
     * 由伴生模块（P3）的设置页通过 ContentProvider 跨进程读写：
     * 主 App 负责存储，伴生模块负责修改和消费。
     * defaultValue = "1"：新订阅默认开启通知。
     */
    @ColumnInfo(name = "notification_enabled", defaultValue = "1")
    val notificationEnabled: Boolean = true,

    /** 添加时间戳（毫秒），列表按此倒序展示 */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
