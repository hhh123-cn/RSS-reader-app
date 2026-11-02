package com.b3.notification.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.b3.notification.R

/**
 * 通知工具 —— P3 伴生模块的通知通道与通知发送
 *
 * 本模块是独立 App，通知均由自己发出（NotificationManager 按 applicationId 区分）。
 * 后续阶段的"后台检测新文章"会调用 showNewArticleNotification。
 *
 * 【通知通道（NotificationChannel）】
 * Android 8.0（API 26）起，所有通知必须挂在某个"通道"下，
 * 用户可在系统设置里按通道单独控制开关/铃声/横幅。
 * createChannel 是幂等的：同一 id 重复创建无副作用，可放心在每次发送前调用。
 */
object NotificationHelper {

    /** 新文章提醒通道 id（本模块唯一通道，后续快捷标记已读等也复用） */
    const val CHANNEL_ID_NEW_ARTICLE = "new_articles"

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
     * 发送一条"新文章"通知。
     *
     * 本阶段只做基础通知（无点击跳转、无快捷按钮），
     * 下一阶段接入：点击跳转文章详情（setContentIntent）、
     * 快捷标记已读（addAction）。
     *
     * @param articleId    文章 id（用作通知 id：同一篇文章多次检测不会重复堆积通知）
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

        // 2. 权限降级检查：Android 13+ 未授权 → 不发送、不崩溃，直接返回 false
        if (!hasNotificationPermission(context)) return false

        // 3. 构建通知
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_NEW_ARTICLE)
            // 小图标：必须为"纯白剪影"风格（系统会按需着色），见 drawable/ic_notification.xml
            .setSmallIcon(R.drawable.ic_notification)
            // 标题 = 订阅源名，正文 = 文章标题，一眼知道"哪个源来了什么文章"
            .setContentTitle(feedTitle)
            .setContentText(articleTitle)
            // 长文章标题展开全文
            .setStyle(NotificationCompat.BigTextStyle().bigText(articleTitle))
            // 重要度：横幅级别（与通道一致）
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // 用户点掉通知自动消失
            .setAutoCancel(true)
            .build()

        // 4. 发送。notify 的 id 用 articleId：同文章不重复发，不同文章并存。
        //    （notify 要求 int id，Long 溢出概率在本课程场景可忽略）
        return try {
            NotificationManagerCompat.from(context).notify(articleId.toInt(), notification)
            true
        } catch (e: SecurityException) {
            // 极端情况：权限被系统回收后立刻发送 → 捕获降级，不崩溃
            false
        }
    }
}
