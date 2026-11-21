package com.b3.rssreader.ui.article

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.b3.rssreader.data.local.entity.FavoriteFolder
import com.b3.rssreader.data.repository.ArticleRepository
import com.b3.rssreader.data.repository.FavoriteFolderRepository
import com.b3.rssreader.ui.common.Event
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 文章详情 ViewModel
 *
 * 职责：
 * 1. 从导航参数/深链接（SavedStateHandle）取文章 id
 * 2. 查库拿标题与原文链接（深链接只带 id，标题/链接必须自己查）
 * 3. 进入详情页时【自动把文章标记为已读】
 * 4. 收藏/取消收藏（详情页工具栏星形按钮），收藏时可选择归入哪个收藏夹
 */
class ArticleDetailViewModel(
    private val repository: ArticleRepository,
    private val folderRepository: FavoriteFolderRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** 文章 id（导航参数或深链接 rssreader://article/{articleId} 注入） */
    val articleId: Long = savedStateHandle["articleId"] ?: -1L

    /**
     * 文章信息（标题 + 原文链接）单次事件：
     * - 用 Event 包装：LiveData 重发（旋转屏幕）不会重复触发 WebView 重新加载
     * - 链接为空串表示"文章不存在（可能已被删除）"，UI 显示错误态
     */
    private val _articleInfo = MutableLiveData<Event<Pair<String, String>>>()
    val articleInfo: LiveData<Event<Pair<String, String>>> = _articleInfo

    /**
     * 当前收藏状态（响应式）：
     * observeById 是 Room Flow，收藏一变自动推送，
     * 详情页星形按钮图标随之实时更新。
     */
    val isFavorite: LiveData<Boolean> = repository.observeById(articleId)
        .map { it?.isFavorite == true }
        .asLiveData()

    /**
     * 全部收藏夹（收藏弹窗的选择列表）。
     * 用 StateFlow + SharingStarted.Eagerly：
     * 弹窗读取 folders.value 时必须是"已加载的最新数据"，
     * 而普通 LiveData 在无人 observe 时永远不会加载（会导致弹窗里看不到已有收藏夹）。
     */
    val folders: StateFlow<List<FavoriteFolder>> =
        folderRepository.observeAllWithCount()
            .map { list -> list.map { it.folder } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            // 1. 进入详情即自动标记已读
            if (articleId != -1L) {
                repository.markRead(articleId, isRead = true)
            }

            // 2. 查库补全标题与链接（深链接场景没有携带这些信息）
            val article = repository.getById(articleId)
            if (article == null) {
                // 文章不存在：空链接 → UI 侧显示加载失败态
                _articleInfo.postValue(Event("" to ""))
            } else {
                _articleInfo.postValue(Event(article.title to article.link))
            }
        }
    }

    /**
     * 收藏 / 取消收藏。
     * - 已收藏状态调用 → 取消收藏（同时清空分组）
     * - 未收藏状态调用 → 收藏并归入指定收藏夹（folderId = null 表示未分组）
     */
    fun toggleFavorite(folderId: Long? = null) {
        viewModelScope.launch {
            val current = repository.getById(articleId)?.isFavorite ?: false
            if (current) {
                repository.markFavorite(articleId, isFavorite = false)
            } else {
                repository.markFavorite(articleId, isFavorite = true, folderId = folderId)
            }
        }
    }

    companion object {
        /** 工厂：注入仓库 + SavedStateHandle（导航参数/深链接参数） */
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                ArticleDetailViewModel(
                    ArticleRepository.getInstance(application),
                    FavoriteFolderRepository.getInstance(application),
                    createSavedStateHandle()
                )
            }
        }
    }
}
