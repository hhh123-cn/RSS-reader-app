package com.b3.notification

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.b3.notification.databinding.ActivityReminderSettingsBinding

/**
 * P3 通知提醒中心 —— 独立提醒设置页
 *
 * 阶段 1 仅作占位，证明伴生模块可独立编译、独立运行。
 * 阶段 4 起实现真正的设置项：
 *   - 全局提醒开关
 *   - 每个订阅源单独的新文章通知开关（自选功能）
 *   - 检测频率设置
 *   - 通知权限状态提示与降级处理
 */
class ReminderSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReminderSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityReminderSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 占位提示：证明伴生模块骨架搭建成功
        binding.tvPlaceholder.text = getString(R.string.settings_placeholder)
    }
}
