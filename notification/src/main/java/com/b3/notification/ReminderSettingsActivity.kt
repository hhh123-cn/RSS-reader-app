package com.b3.notification

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.b3.notification.databinding.ActivityReminderSettingsBinding
import com.b3.notification.notify.NotificationHelper
import com.google.android.material.snackbar.Snackbar

/**
 * P3 通知提醒中心 —— 提醒设置页
 *
 * 本阶段（第 15 次提交）内容：
 * - 创建通知通道（App 启动即建）
 * - "发送测试通知"临时入口：验证通道与通知工具可用
 * - 通知权限申请流程 + 被拒绝时的降级提示（不崩溃）
 *
 * 下一阶段将把占位内容替换为正式设置页：
 * 全局提醒开关、按订阅源开关通知、检测频率、通知跳转等。
 */
class ReminderSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReminderSettingsBinding

    /**
     * 运行时权限申请器（现代写法，替代过时的 onRequestPermissionsResult）：
     * 系统弹权限框 → 用户选择 → 回调 granted 布尔值。
     */
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // 授权成功 → 立刻发送测试通知
            sendTestNotification()
        } else {
            // 降级提示：权限被拒绝不能崩溃，告知用户如何补救
            Snackbar.make(
                binding.root,
                R.string.notification_permission_denied,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityReminderSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // App 启动即创建通知通道（幂等，后续每次发通知前也会再调一次）
        NotificationHelper.createChannel(this)

        // 测试入口：点按钮 → 有权限直接发，没权限先申请
        binding.btnTestNotification.setOnClickListener {
            if (NotificationHelper.hasNotificationPermission(this)) {
                sendTestNotification()
            } else {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /** 发送一条测试通知（后续阶段由"检测到新文章"自动触发） */
    private fun sendTestNotification() {
        val sent = NotificationHelper.showNewArticleNotification(
            context = this,
            articleId = System.currentTimeMillis(), // 测试用随机 id，避免与真实文章冲突
            feedTitle = getString(R.string.test_feed_title),
            articleTitle = getString(R.string.test_article_title)
        )
        Snackbar.make(
            binding.root,
            if (sent) R.string.notification_sent else R.string.notification_send_failed,
            Snackbar.LENGTH_SHORT
        ).show()
    }
}
