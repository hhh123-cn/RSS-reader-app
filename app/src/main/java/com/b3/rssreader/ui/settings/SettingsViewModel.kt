package com.b3.rssreader.ui.settings

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.b3.rssreader.data.repository.ArticleRepository
import com.b3.rssreader.data.repository.FeedRepository
import com.b3.rssreader.utils.PrefUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 设置页统计数据（卡片展示用）
 */
data class SettingsStats(
    val feedCount: Int,
    val articleCount: Int,
    val unreadCount: Int
)

/**
 * 设置页 ViewModel
 *
 * 内容：
 * - 数据库统计（订阅源数 / 文章数 / 未读数）
 * - 周期刷新信息（上次刷新时间 + 新增文章数，来自 PrefUtil，由周期 Worker 写入）
 */
class SettingsViewModel(
    private val application: Application,
    private val feedRepository: FeedRepository,
    private val articleRepository: ArticleRepository
) : ViewModel() {

    private val _stats = MutableLiveData(SettingsStats(0, 0, 0))
    val stats: LiveData<SettingsStats> = _stats

    /** 上次周期刷新的时间戳（毫秒，0 = 从未刷新） */
    val lastRefreshTime: Long get() = PrefUtil.getLastRefreshTime(application)

    /** 上次周期刷新发现的新文章数 */
    val lastNewCount: Int get() = PrefUtil.getLastNewCount(application)

    /** 加载统计数据（每次页面可见时刷新） */
    fun loadStats() {
        viewModelScope.launch {
            val feedCount = feedRepository.countFeeds()
            val articleCount = articleRepository.countArticles()
            // observeUnreadCount 是 Flow，first() 取一次当前值
            val unreadCount = articleRepository.observeUnreadCount().first()
            _stats.value = SettingsStats(feedCount, articleCount, unreadCount)
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                SettingsViewModel(
                    application,
                    FeedRepository.getInstance(application),
                    ArticleRepository.getInstance(application)
                )
            }
        }
    }
}
