package com.b3.notification.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.b3.notification.R

/**
 * 通知工具 —— P3 伴生模块的通知通道与通知发送
 *
 * 本模块是独立 App，通知均由自己发出（NotificationManager 按 applicationId 区分）。
 *
 * 【通知通道（NotificationChannel）】
 * Android 8.0（API 26）起，所有通知必须挂在某个"通道"下，
 * 用户可在系统设置里按通道单独控制开关/铃声/横幅。
 * createChannel 是幂等的：同一 id 重复创建无副作用。
 *
 * 【一条新文章通知包含】
 * - 点击通知：深链接打开主 App（rssreader://article/{id}）跳转文章详情
 * - 快捷 Action"标为已读"：通过 MarkReadReceiver 跨进程调主 App 的
 *   ContentProvider 远程标记，并顺手撤销通知
 */
object NotificationHelper {

    /** 新文章提醒通道 id */
    const val CHANNEL_ID_NEW_ARTICLE = "new_articles"

    /** 深链接 scheme/host（与主 App nav_graph 的 deepLink、manifest intent-filter 一致） */
    private const val DEEP_LINK_SCHEME = "rssreader"
    private const val DEEP_LINK_HOST = "article"

    /**
     * 创建通知通道（幂等）。
     * 重要度 IMPORTANCE_HIGH：通知会弹横幅 + 响铃（用户可在系统设置里自行调低）。
     */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID_NEW_ARTICLE,
            context.getString(R.string.channel_new_article_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_new_article_desc)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    /**
     * 检查通知权限。
     * Android 13（API 33）起 POST_NOTIFICATIONS 是运行时权限；
     * 更早版本默认已授权（声明即可用）。
     */
    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * 发送一条"新文章"通知（含点击跳转 + 快捷标已读）。
     *
     * @param articleId    文章 id（通知 id + 深链接参数）
     * @param feedTitle    订阅源名（通知标题）
     * @param articleTitle 文章标题（通知正文）
     * @return true = 已发送；false = 无权限或异常（降级：静默失败，绝不崩溃）
     */
    fun showNewArticleNotification(
        context: Context,
        articleId: Long,
        feedTitle: String,
        articleTitle: String
    ): Boolean {
        // 1. 保证通道存在（幂等）
        createChannel(context)

        // 2. 权限降级检查：Android 13+ 未授权 → 不发送、不崩溃
        if (!hasNotificationPermission(context)) return false

        // 3. 点击通知 → 深链接打开主 App 的文章详情页
        //    （主 App 未安装时 ActivityNotFoundException 会被系统吞掉，仅表现为点击无响应）
        val deepLinkIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("$DEEP_LINK_SCHEME://$DEEP_LINK_HOST/$articleId")
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            articleId.toInt(),
            deepLinkIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 4. 快捷 Action"标为已读" → 指向本模块的 MarkReadReceiver（显式 Intent，安全）
        val markReadIntent = Intent(context, MarkReadReceiver::class.java).apply {
            action = MarkReadReceiver.ACTION_MARK_READ
            putExtra(MarkReadReceiver.EXTRA_ARTICLE_ID, articleId)
        }
        // requestCode 与 contentIntent 错开，防止两个 PendingIntent 互相覆盖
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            articleId.toInt() + REQUEST_CODE_OFFSET,
            markReadIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 5. 构建通知
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_NEW_ARTICLE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(feedTitle)
            .setContentText(articleTitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(articleTitle))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true) // 点击通知后自动消失
            .setContentIntent(contentPendingIntent) // 点击 → 跳主 App 详情
            .addAction( // 快捷按钮 → 远程标已读
                R.drawable.ic_read_done,
                context.getString(R.string.notification_action_mark_read),
                markReadPendingIntent
            )
            .build()

        // 6. 发送（notify 的 id = articleId：同文章不重复发，不同文章并存）
        return try {
            NotificationManagerCompat.from(context).notify(articleId.toInt(), notification)
            true
        } catch (e: SecurityException) {
            // 极端情况：权限被系统回收后立刻发送 → 捕获降级
            false
        }
    }

    /** 快捷按钮 PendingIntent 的 requestCode 偏移量（与 contentIntent 区分） */
    private const val REQUEST_CODE_OFFSET = 1_000_000
}
