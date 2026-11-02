package com.b3.rssreader.ui.article

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.b3.rssreader.data.repository.ArticleRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 文章详情 ViewModel
 *
 * 职责：
 * 1. 从导航参数（SavedStateHandle）取文章 id / 标题 / 原文链接
 * 2. 进入详情页时【自动把文章标记为已读】
 * 3. 收藏/取消收藏（详情页工具栏星形按钮）
 *
 * 自动标记已读的联动效果：
 * markRead 落库后，文章列表页的 Room Flow 会自动发射新数据，
 * 对应条目立即变灰、未读圆点消失——无需任何手动刷新，
 * 这就是 MVVM + Room 响应式架构的典型表现。
 */
class ArticleDetailViewModel(
    private val repository: ArticleRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** 文章 id（导航必传参数） */
    val articleId: Long = savedStateHandle["articleId"] ?: -1L

    /** 文章标题（用于页面标题栏，导航传入，避免再查一次数据库） */
    val articleTitle: String = savedStateHandle["articleTitle"] ?: ""

    /** 原文链接（WebView 加载目标） */
    val articleLink: String = savedStateHandle["articleLink"] ?: ""

    /**
     * 当前收藏状态（响应式）：
     * observeById 是 Room Flow，收藏一变自动推送，
     * 详情页星形按钮图标随之实时更新。
     */
    val isFavorite: LiveData<Boolean> = repository.observeById(articleId)
        .map { it?.isFavorite == true }
        .asLiveData()

    init {
        // 进入详情即自动标记已读（viewModelScope：页面销毁时协程自动取消）
        viewModelScope.launch {
            if (articleId != -1L) {
                repository.markRead(articleId, isRead = true)
            }
        }
    }

    /** 收藏 / 取消收藏（取反当前状态） */
    fun toggleFavorite() {
        viewModelScope.launch {
            val current = repository.getById(articleId)?.isFavorite ?: false
            repository.markFavorite(articleId, isFavorite = !current)
        }
    }

    companion object {
        /** 工厂：注入 Repository + SavedStateHandle（导航参数） */
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                ArticleDetailViewModel(
                    ArticleRepository.getInstance(application),
                    createSavedStateHandle()
                )
            }
        }
    }
}
