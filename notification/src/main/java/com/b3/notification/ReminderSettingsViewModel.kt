package com.b3.notification

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.b3.notification.data.NotificationFeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 设置页 UI 状态（三态模型，与主 App 同构）
 * Loading → 进度圈；Success(空) → 空态；Success(非空) → 列表；Error → 失败提示
 */
sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Success(val feeds: List<NotificationFeedRepository.FeedInfo>) : SettingsUiState
    data class Error(val message: String) : SettingsUiState
}

/**
 * 提醒设置页 ViewModel
 *
 * 数据来源：主 App 的 ContentProvider（跨进程），
 * 因为 Provider 是快照式查询（非响应式），
 * 每次修改开关后重新拉取一次列表来刷新界面。
 */
class ReminderSettingsViewModel(
    private val repository: NotificationFeedRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** 加载订阅源列表（跨进程查询主 App 数据库） */
    fun loadFeeds() {
        viewModelScope.launch {
            _uiState.value = SettingsUiState.Loading
            _uiState.value = try {
                SettingsUiState.Success(repository.fetchFeeds())
            } catch (e: Exception) {
                // 主 App 未安装 / Provider 不可用 → 失败态（不崩溃）
                SettingsUiState.Error(e.message ?: "加载失败")
            }
        }
    }

    /**
     * 切换单个订阅源的通知开关：
     * 跨进程写回主 App 数据库（feeds 表 notification_enabled 字段），
     * 成功后重新拉取列表刷新界面。
     */
    fun setFeedEnabled(feedId: Long, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setNotificationEnabled(feedId, enabled) }
            loadFeeds()
        }
    }

    companion object {
        /** 工厂：注入跨进程仓库 */
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as Application
                ReminderSettingsViewModel(NotificationFeedRepository(application))
            }
        }
    }
}
