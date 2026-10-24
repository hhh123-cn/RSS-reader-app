package com.b3.rssreader.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.b3.rssreader.data.local.dao.ArticleDao
import com.b3.rssreader.data.local.dao.FeedDao
import com.b3.rssreader.data.local.entity.Article
import com.b3.rssreader.data.local.entity.Feed

/**
 * 应用数据库 —— 整个 App 唯一的 Room 数据库入口
 *
 * 架构定位（MVVM）：
 *   UI(Activity/Fragment) → ViewModel → Repository → DAO → AppDatabase
 *   本阶段只完成最底层的 DAO/数据库，Repository 在阶段 3 加入
 *   （它负责同时封装 Room 与 OkHttp 两个数据源）。
 *
 * 数据库文件：rss_reader.db，位于本 App 的私有 data 目录。
 * 伴生模块（P3）不直接打开这个文件——它通过本 App 导出的
 * ContentProvider（RssContentProvider）跨进程读取，实现"共享数据库"。
 */
@Database(
    entities = [Feed::class, Article::class],
    // v2：feeds 表新增 notification_enabled 列（单源通知开关）。
    // 表结构变化必须升版本号，否则 Room 校验 identity hash 不匹配会直接崩溃。
    // 本课程设计采用 fallbackToDestructiveMigration 兜底：老用户升级时自动清库重建。
    version = 2,
    // 不导出 schema 备份文件。若想用 Room 自动迁移测试，可改为 true 并配置 room.schemaLocation
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /** Room 生成实现类时使用，声明各 DAO 的获取方法 */
    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao

    companion object {
        // 数据库单例。
        // @Volatile：保证多线程下 INSTANCE 的可见性；
        // synchronized：双重检查锁（DCL），避免并发创建两个实例
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * 获取数据库单例。
         *
         * @param context 任意 Context 都行，内部统一转 applicationContext，
         *                避免 Activity 持有导致内存泄漏
         */
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rss_reader.db"
                )
                    // 课程设计策略：数据库版本升级时"清空重建"。
                    // 好处是无需编写 Migration 代码；
                    // 坏处是用户数据会丢失（正式项目应编写 Migration 按版本迁移）
                    .fallbackToDestructiveMigration()
                    // 注册回调：数据库每次打开时启用外键约束
                    .addCallback(FOREIGN_KEY_CALLBACK)
                    .build()
                    .also { INSTANCE = it }
            }

        /**
         * SQLite 默认【不启用】外键约束（历史原因）。
         * 不启用的话，"删除订阅源 → CASCADE 删除其文章"不会生效，
         * 数据库里会留下孤儿文章。必须在每次打开数据库时手动开启。
         */
        private val FOREIGN_KEY_CALLBACK = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.setForeignKeyConstraintsEnabled(true)
            }
        }
    }
}
