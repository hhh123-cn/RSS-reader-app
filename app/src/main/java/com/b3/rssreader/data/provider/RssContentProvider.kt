package com.b3.rssreader.data.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import androidx.sqlite.db.SimpleSQLiteQuery
import com.b3.rssreader.data.local.AppDatabase

/**
 * RSS 内容提供者 —— 两个 App 共享数据库的桥梁
 *
 * 【为什么需要它】
 * Android 规定：两个独立 APK 各自有私有沙箱，互相读不到对方的文件。
 * P3 伴生模块要"检测新文章、快捷标已读"，就必须通过本 App 导出的
 * ContentProvider 跨进程访问数据库。这是 Android 上"两 App 共享数据库"的标准做法。
 *
 * 【支持的操作】（对应伴生模块的必做/自选功能）
 * 1. query  ：feeds 订阅源列表 / articles 文章查询（支持"只查新增、只查未读"）
 * 2. update ：articles/# 把某篇文章标记已读（通知快捷操作）；
 *             feeds/#   修改单源通知开关（自选功能：按源设置是否通知）
 * 3. insert / delete：有意【不支持】——写操作只允许主 App 进程内的 DAO 完成，
 *    防止外部 App 篡改数据（如删掉订阅源）。越界调用直接抛异常。
 *
 * 【安全说明】
 * exported="true" 意味着任何 App 都能读取（课程设计简化处理）。
 * 正式项目应加自定义权限（android:permission + 伴生模块 uses-permission）
 * 或使用签名级权限，只允许同一签名的应用访问。
 */
class RssContentProvider : ContentProvider() {

    companion object {
        /** authority：Uri 的"域名"，全局唯一，伴生模块靠它找到本 Provider */
        const val AUTHORITY = "com.b3.rssreader.provider"

        /** 便捷 Uri 构造：content://com.b3.rssreader.provider/feeds */
        val URI_FEEDS: Uri = Uri.parse("content://$AUTHORITY/feeds")

        /** 便捷 Uri 构造：content://com.b3.rssreader.provider/articles */
        val URI_ARTICLES: Uri = Uri.parse("content://$AUTHORITY/articles")

        /** 单篇文章 Uri：content://com.b3.rssreader.provider/articles/{文章id} */
        fun uriArticle(id: Long): Uri = Uri.parse("content://$AUTHORITY/articles/$id")

        /** 单个订阅源 Uri：content://com.b3.rssreader.provider/feeds/{订阅源id} */
        fun uriFeed(id: Long): Uri = Uri.parse("content://$AUTHORITY/feeds/$id")

        // UriMatcher 匹配码
        private const val CODE_FEEDS = 1
        private const val CODE_ARTICLES = 2
        private const val CODE_ARTICLE_ITEM = 3
        private const val CODE_FEED_ITEM = 4
    }

    /**
     * Uri 路由表：把 content://xxx/feeds 这类 Uri 映射成"匹配码"，
     * 每个 Provider 方法里 switch 匹配码分发逻辑。
     * "#" 表示数字通配符（id）。
     */
    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, "feeds", CODE_FEEDS)
        addURI(AUTHORITY, "feeds/#", CODE_FEED_ITEM)
        addURI(AUTHORITY, "articles", CODE_ARTICLES)
        addURI(AUTHORITY, "articles/#", CODE_ARTICLE_ITEM)
    }

    private lateinit var db: AppDatabase

    /**
     * Provider 的 onCreate 可能比 Application.onCreate 更早被调用
     * （首次访问时系统会先拉起 Provider），因此这里只初始化数据库，
     * 不依赖 Application 里的任何初始化逻辑。
     */
    override fun onCreate(): Boolean {
        val context = requireNotNull(context) { "ContentProvider 的 context 不应为 null" }
        db = AppDatabase.getInstance(context)
        return true
    }

    /**
     * 查询接口 —— 伴生模块的两个核心用法：
     *
     * 1. 查订阅源列表（设置页"按源开关通知"）：
     *    contentResolver.query(URI_FEEDS, ...)
     *
     * 2. 检测新文章（记住上次最大文章 id，只查更大的）：
     *    content://com.b3.rssreader.provider/articles?after_id=123
     *
     * articles 支持的 query 参数（全部可选，可组合）：
     *   after_id      = 只查 id 大于该值的文章（自增主键，即"上次之后新增"）
     *   feed_id       = 只查某个订阅源的文章
     *   unread_only=true  = 只查未读文章
     *   favorite_only=true = 只查收藏文章
     */
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = when (uriMatcher.match(uri)) {
        // 注意：Room 2.6+ 废弃了 query(String) 旧接口，2.7 起只能传 SimpleSQLiteQuery 对象
        CODE_FEEDS -> db.query(SimpleSQLiteQuery("SELECT * FROM feeds ORDER BY created_at DESC"))

        CODE_ARTICLES -> {
            // 把 query 参数翻译成安全的参数化 SQL（? 占位 + 参数数组，天然防注入）
            val (where, args) = buildArticleWhere(uri)
            db.query(
                SimpleSQLiteQuery(
                    "SELECT * FROM articles WHERE $where ORDER BY pub_date DESC",
                    args.toTypedArray()
                )
            )
        }

        CODE_ARTICLE_ITEM -> {
            // lastPathSegment = "articles/5" 中的 "5"
            val id = requireNotNull(uri.lastPathSegment)
            db.query(SimpleSQLiteQuery("SELECT * FROM articles WHERE id = ?", arrayOf(id)))
        }

        // 未匹配任何已注册路径：宁可抛异常也不要静默返回 null（方便排查）
        else -> throw IllegalArgumentException("未知的 Uri: $uri")
    }

    /**
     * 更新接口 —— 支持两件事：
     *
     * 1. articles/# 更新单篇文章的标记字段（伴生模块远程操作）：
     *    - is_read：通知快捷"标为已读"
     *    - is_new ：后台检测推送后清除"新文章"标记（防止重复推送）
     *    ContentValues 里带哪个字段就更新哪个（可同时带两个）
     *
     * 2. feeds/# 修改单源通知开关（伴生模块"按订阅源设置是否通知"）
     */
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        // 块体函数：分支里需要提前 return 0（无可更新字段）
        return when (uriMatcher.match(uri)) {
            CODE_ARTICLE_ITEM -> {
                val id = requireNotNull(uri.lastPathSegment)
                // 动态拼接 SET 子句：只更新 ContentValues 里提供的字段
                val sets = mutableListOf<String>()
                val args = mutableListOf<String>()

                values?.getAsBoolean("is_read")?.let {
                    sets += "is_read = ?"
                    args += if (it) "1" else "0"
                }
                values?.getAsBoolean("is_new")?.let {
                    sets += "is_new = ?"
                    args += if (it) "1" else "0"
                }

                // 没有可更新的字段：返回 0 表示未更新
                if (sets.isEmpty()) return 0

                // 用 openHelper 的 execSQL 执行 UPDATE（标准写入路径，真正落库）。
                // 注意：不要用 db.query(SimpleSQLiteQuery("UPDATE ..."))——
                // query 走的是 rawQuery 只读路径，UPDATE 在部分实现上不会生效。
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE articles SET ${sets.joinToString(", ")} WHERE id = ?",
                    (args + id).toTypedArray()
                )
                1 // 返回"影响 1 行"（失败时 SQL 异常会直接抛出）
            }

            CODE_FEED_ITEM -> {
                val id = requireNotNull(uri.lastPathSegment)
                // 只允许改 notification_enabled 一列；缺省视为 true（开启通知）
                val enabled = values?.getAsBoolean("notification_enabled") ?: true
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE feeds SET notification_enabled = ? WHERE id = ?",
                    arrayOf(if (enabled) "1" else "0", id)
                )
                1
            }

            else -> throw IllegalArgumentException("不支持对该 Uri 执行 update: $uri")
        }
    }

    /**
     * 插入接口：有意不支持。
     * 订阅源/文章的写入必须走主 App 进程内的 DAO（含去重、外键等逻辑），
     * 防止外部 App 绕过校验直接塞脏数据。
     */
    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("不允许通过 Provider 插入数据: $uri")

    /**
     * 删除接口：有意不支持，理由同上——防篡改。
     * 订阅源删除请使用主 App 的 DAO（会级联删文章）。
     */
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("不允许通过 Provider 删除数据: $uri")

    /** 返回各 Uri 对应的 MIME 类型（ContentResolver 规范要求） */
    override fun getType(uri: Uri): String = when (uriMatcher.match(uri)) {
        CODE_FEEDS -> "vnd.android.cursor.dir/vnd.b3.rssreader.feed"
        CODE_FEED_ITEM -> "vnd.android.cursor.item/vnd.b3.rssreader.feed"
        CODE_ARTICLES -> "vnd.android.cursor.dir/vnd.b3.rssreader.article"
        CODE_ARTICLE_ITEM -> "vnd.android.cursor.item/vnd.b3.rssreader.article"
        else -> throw IllegalArgumentException("未知的 Uri: $uri")
    }

    /** 把 articles 路径上的 query 参数翻译成参数化 SQL 片段 */
    private fun buildArticleWhere(uri: Uri): Pair<String, List<String>> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()

        uri.getQueryParameter("feed_id")?.let {
            clauses += "feed_id = ?"
            args += it
        }
        uri.getQueryParameter("after_id")?.let {
            clauses += "id > ?"
            args += it
        }
        if (uri.getQueryParameter("unread_only") == "true") {
            clauses += "is_read = 0"
        }
        if (uri.getQueryParameter("favorite_only") == "true") {
            clauses += "is_favorite = 1"
        }
        // 只查"新增文章"（is_new = 1）：伴生模块后台检测新文章用
        if (uri.getQueryParameter("new_only") == "true") {
            clauses += "is_new = 1"
        }

        // 没有任何条件时查询全部
        if (clauses.isEmpty()) return "1" to emptyList()
        return clauses.joinToString(" AND ") to args
    }
}
