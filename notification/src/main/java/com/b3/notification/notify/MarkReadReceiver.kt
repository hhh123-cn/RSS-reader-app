package com.b3.notification.notify

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 通知快捷操作接收器 —— 远程把文章标记为已读
 *
 * 触发方式：用户点击通知上的"标为已读"按钮
 * （NotificationHelper 里构造的 PendingIntent 显式指向本 Receiver）。
 *
 * 核心逻辑：跨进程调用主 App 导出的 ContentProvider（RssContentProvider），
 * 把文章 is_read 置为 true——数据落的是主 App 的数据库，
 * 这正是"两个 App 共享数据库"设计的落地（伴生模块不持有数据库）。
 */
class MarkReadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val articleId = intent.getLongExtra(EXTRA_ARTICLE_ID, -1L)
        if (articleId == -1L) return

        // goAsync()：onReceive 跑在主线程且系统约 10 秒后可能杀进程，
        // 跨进程调用 ContentProvider 属于耗时 IO，必须切后台线程并让系统等待完成
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. 远程标记已读（调用主 App 的 ContentProvider）
                val uri = Uri.parse("content://$PROVIDER_AUTHORITY/articles/$articleId")
                val values = ContentValues().apply { put("is_read", true) }
                context.contentResolver.update(uri, values, null, null)

                // 2. 撤销这条通知（已读的文章无需再提醒用户）
                NotificationManagerCompat.from(context).cancel(articleId.toInt())
            } finally {
                // 无论成败都要结束 goAsync，避免系统误杀
                pendingResult.finish()
            }
        }
    }

    companion object {
        /** 广播 action（显式 Intent 场景下仅作标识） */
        const val ACTION_MARK_READ = "com.b3.notification.action.MARK_READ"

        /** Intent extra：文章 id */
        const val EXTRA_ARTICLE_ID = "extra_article_id"

        /**
         * 主 App ContentProvider 的 authority。
         * 注意：这是两个模块间唯一的字符串耦合点，
         * 必须与主 App RssContentProvider.AUTHORITY 保持一致！
         */
        private const val PROVIDER_AUTHORITY = "com.b3.rssreader.provider"
    }
}
