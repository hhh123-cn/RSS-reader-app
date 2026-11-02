package com.b3.rssreader.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.b3.rssreader.R
import com.b3.rssreader.databinding.FragmentSettingsBinding
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设置页 —— 主 App 自身信息与入口
 *
 * 内容：
 * - 数据统计：订阅源数 / 文章数 / 未读数
 * - 自动刷新信息：频率（每 1 小时）、上次刷新时间、新增文章数
 * - 提醒设置入口：跳转 P3 伴生模块（未安装时提示）
 * - 关于：课程设计信息
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels { SettingsViewModel.factory() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 观察统计信息（每次 loadStats 后自动更新）
        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            binding.tvStats.text = getString(
                R.string.settings_stats_format,
                stats.feedCount, stats.articleCount, stats.unreadCount
            )
        }

        // 提醒设置卡片点击 → 打开 P3 伴生模块
        binding.cardNotification.setOnClickListener {
            openNotificationApp()
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次回到页面刷新数据（统计可能变化、上次刷新时间可能更新）
        viewModel.loadStats()
        renderRefreshInfo()
    }

    /** 渲染自动刷新信息（上次刷新时间 + 新增文章数） */
    private fun renderRefreshInfo() {
        val timeText = if (viewModel.lastRefreshTime == 0L) {
            getString(R.string.settings_never_refreshed)
        } else {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(Date(viewModel.lastRefreshTime))
        }
        binding.tvRefreshInfo.text = getString(
            R.string.settings_refresh_format,
            timeText, viewModel.lastNewCount
        )
    }

    /**
     * 打开 P3 伴生模块（另一个独立 App）：
     * 通过包名获取启动 Intent；未安装时降级提示，不崩溃。
     */
    private fun openNotificationApp() {
        val intent = runCatching {
            requireContext().packageManager.getLaunchIntentForPackage("com.b3.notification")
        }.getOrNull()

        if (intent != null) {
            startActivity(intent)
        } else {
            Snackbar.make(
                binding.root,
                R.string.settings_notification_missing,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
