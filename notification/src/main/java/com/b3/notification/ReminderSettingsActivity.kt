package com.b3.notification

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.b3.notification.databinding.ActivityReminderSettingsBinding
import com.b3.notification.notify.NotificationHelper
import com.b3.notification.utils.NetworkUtil
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * P3 通知提醒中心 —— 独立提醒设置页
 *
 * 页面组成：
 * 1. 通知权限卡片：Android 13+ 运行时申请；被拒绝 → Snackbar 降级提示（不崩溃）
 * 2. 网络状态行：运行时检测联网状态，无网给出降级提示
 * 3. 订阅源通知开关列表（自选功能）：跨进程读写主 App 数据库，
 *    可单独控制每个源是否推送新文章通知
 */
class ReminderSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReminderSettingsBinding

    private val viewModel: ReminderSettingsViewModel by viewModels { ReminderSettingsViewModel.factory() }

    /** 订阅源开关列表适配器 */
    private val feedAdapter: FeedSwitchAdapter by lazy {
        FeedSwitchAdapter { feed, enabled ->
            viewModel.setFeedEnabled(feed.id, enabled)
        }
    }

    /**
     * 通知权限申请器（现代写法）：
     * 系统弹权限框 → 用户选择 → 回调 granted。
     */
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            refreshPermissionState()
        } else {
            // 权限被拒绝 → 降级提示（引导用户去系统设置，不崩溃）
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

        // 通知通道（幂等创建）
        NotificationHelper.createChannel(this)

        setupList()
        setupListeners()

        // 观察订阅源列表三态
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次回到页面刷新：
        // - 权限状态（用户可能去系统设置改过）
        // - 网络状态
        // - 订阅源列表（主 App 侧可能增删了订阅源）
        refreshPermissionState()
        refreshNetworkState()
        viewModel.loadFeeds()
    }

    private fun setupList() {
        binding.rvFeeds.layoutManager = LinearLayoutManager(this)
        binding.rvFeeds.adapter = feedAdapter
    }

    private fun setupListeners() {
        // 权限按钮：有权限时隐藏；点击 → 发起申请
        binding.btnGrantPermission.setOnClickListener {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // 失败态重试（StateLayout 内的按钮）
        binding.stateLayout.setOnRetryListener { viewModel.loadFeeds() }
    }

    /** 刷新通知权限卡片状态 */
    private fun refreshPermissionState() {
        val granted = NotificationHelper.hasNotificationPermission(this)
        if (granted) {
            // 已授权：隐藏申请按钮（卡片说明文字已足够表达"已开启"）
            binding.btnGrantPermission.isVisible = false
        } else {
            // 未授权：显示申请按钮（API 32 及以下系统默认已授权，不会走到这）
            binding.btnGrantPermission.isVisible = true
        }
    }

    /** 刷新网络状态行（无网降级提示） */
    private fun refreshNetworkState() {
        binding.tvNetworkStatus.text = getString(
            if (NetworkUtil.isNetworkAvailable(this)) R.string.network_ok
            else R.string.network_offline
        )
    }

    /** 三态渲染（StateLayout 统一样式） */
    private fun render(state: SettingsUiState) {
        when (state) {
            is SettingsUiState.Loading -> {
                binding.stateLayout.showLoading()
                binding.rvFeeds.isVisible = false
            }

            is SettingsUiState.Success -> {
                if (state.feeds.isEmpty()) {
                    binding.rvFeeds.isVisible = false
                    binding.stateLayout.showEmpty(
                        getString(R.string.empty_feeds_title),
                        getString(R.string.empty_feeds_hint)
                    )
                } else {
                    binding.stateLayout.hideAll()
                    binding.rvFeeds.isVisible = true
                    feedAdapter.submitList(state.feeds)
                }
            }

            is SettingsUiState.Error -> {
                binding.rvFeeds.isVisible = false
                binding.stateLayout.showError(state.message)
            }
        }
    }
}
