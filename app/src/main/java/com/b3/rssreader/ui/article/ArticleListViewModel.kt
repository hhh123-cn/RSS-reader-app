package com.b3.rssreader.ui.article

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.b3.rssreader.R
import com.b3.rssreader.data.local.entity.Article
import com.b3.rssreader.data.repository.ArticleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * 文章列表 UI 状态（三态模型）
 * 所有界面必须处理的三种状态，这里用密封接口建模：
 * - Loading：加载中（显示进度圈）
 * - Success：成功（articles 为空 → 空态；非空 → 列表）
 * - Error  ：失败（显示错误提示 + 重试按钮）
 */
sealed interface ArticleListUiState {
    data object Loading : ArticleListUiState
    data class Success(val articles: List<Article>) : ArticleListUiState
    data class Error(val message: String) : ArticleListUiState
}

/**
 * 文章列表 ViewModel —— 按订阅源展示文章 / 全部文章聚合
 *
 * 两种模式由导航参数 feedId 决定：
 * - feedId == -1（底部"文章列表"tab 进入）→ 观察全部文章
 * - feedId >= 0 （订阅源列表点击进入）    → 观察该订阅源的文章
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArticleListViewModel(
    private val application: Application,
    private val repository: ArticleRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** 订阅源 id（导航参数，-1 = 全部文章） */
    private val feedId: Long = savedStateHandle["feedId"] ?: -1L

    /** 订阅源名（导航参数，全部文章模式下为空，用于页面标题） */
    val feedTitle: String = savedStateHandle["feedTitle"] ?: ""

    /** 重试触发器：值每 +1，就重新建立一次数据管道（flatMapLatest） */
    private val retryTrigger = MutableStateFlow(0)

    /**
     * UI 状态流：
     * - stateIn：把冷流变成热流（StateFlow），多个观察者共享同一份数据
     * - WhileSubscribed(5000)：界面停止观察后 5 秒才取消上游，
     *   切 tab 回来不用重新查询（顺便避免旋转屏幕重建时重新加载）
     */
    val uiState = retryTrigger
        .flatMapLatest { observeArticles() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArticleListUiState.Loading)

    /** 按模式选择数据源，并包装成三态流 */
    private fun observeArticles(): Flow<ArticleListUiState> {
        // Room 的查询按 pub_date DESC 排序（DAO 里已定义），天然满足"时间倒序"
        val source: Flow<List<Article>> =
            if (feedId == -1L) repository.observeAll()
            else repository.observeByFeed(feedId)

        return source
            // 数据到达 → Success（列表为空时 UI 层会切成空态）
            .map<List<Article>, ArticleListUiState> { ArticleListUiState.Success(it) }
            // 订阅后先发一次 Loading（正式查询发出前显示进度圈）
            .onStart { emit(ArticleListUiState.Loading) }
            // 查询出错（罕见，如数据库损坏）→ Error 态，不崩溃
            .catch { emit(ArticleListUiState.Error(application.getString(R.string.article_error))) }
    }

    /** 失败态点"重试"：触发重建数据管道 */
    fun retry() {
        retryTrigger.value = retryTrigger.value + 1
    }

    companion object {
        /** 工厂：注入 Application/Repository/SavedStateHandle（导航参数） */
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                ArticleListViewModel(
                    application,
                    ArticleRepository.getInstance(application),
                    createSavedStateHandle()
                )
            }
        }
    }
}
