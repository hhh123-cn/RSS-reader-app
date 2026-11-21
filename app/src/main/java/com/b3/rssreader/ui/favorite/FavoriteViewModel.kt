package com.b3.rssreader.ui.favorite

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.b3.rssreader.R
import com.b3.rssreader.data.local.dao.FavoriteFolderWithCount
import com.b3.rssreader.data.local.entity.Article
import com.b3.rssreader.data.local.entity.FavoriteFolder
import com.b3.rssreader.data.repository.ArticleRepository
import com.b3.rssreader.data.repository.FavoriteFolderRepository
import com.b3.rssreader.ui.article.ArticleListUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 收藏页 ViewModel —— 收藏列表 + 收藏夹分组管理（自选功能）
 *
 * 筛选模型：
 * - selectedFolderId = null  → 全部收藏
 * - selectedFolderId = -1    → 未分组收藏
 * - selectedFolderId = 其他  → 指定收藏夹内的收藏
 *
 * 数据自动化：切换筛选（flatMapLatest 重建查询流）、增删改收藏夹、
 * 移动文章分组，全部由 Room Flow 自动驱动列表与 chips 刷新。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteViewModel(
    private val application: Application,
    private val articleRepository: ArticleRepository,
    private val folderRepository: FavoriteFolderRepository
) : ViewModel() {

    /** 当前筛选（null = 全部） */
    private val selectedFolder = MutableStateFlow<Long?>(null)
    val selectedFolderId: StateFlow<Long?> = selectedFolder.asStateFlow()

    /** 收藏夹列表 + 各夹文章数（chips 展示），收藏夹变化自动刷新 */
    val folders: LiveData<List<FavoriteFolderWithCount>> =
        folderRepository.observeAllWithCount().asLiveData()

    /** 当前筛选下的收藏文章（三态） */
    val uiState: StateFlow<ArticleListUiState> = selectedFolder
        .flatMapLatest { folderId ->
            val source = when {
                folderId == null -> articleRepository.observeFavorites()
                folderId == UNGROUPED_FOLDER_ID -> articleRepository.observeUngroupedFavorites()
                else -> articleRepository.observeFavoritesInFolder(folderId)
            }
            source
                .map<List<Article>, ArticleListUiState> { ArticleListUiState.Success(it) }
                .onStart { emit(ArticleListUiState.Loading) }
                .catch { emit(ArticleListUiState.Error(application.getString(R.string.article_error))) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArticleListUiState.Loading)

    /** 切换筛选 */
    fun selectFolder(folderId: Long?) {
        selectedFolder.value = folderId
    }

    // ---------------- 文章操作 ----------------

    /** 取消收藏 */
    fun removeFavorite(article: Article) {
        viewModelScope.launch {
            articleRepository.markFavorite(article.id, isFavorite = false)
        }
    }

    /** 撤销取消收藏（恢复到原分组） */
    fun restoreFavorite(article: Article) {
        viewModelScope.launch {
            articleRepository.markFavorite(
                article.id,
                isFavorite = true,
                folderId = article.favoriteFolderId
            )
        }
    }

    /** 把文章移动到指定收藏夹（null = 移出分组） */
    fun moveToFolder(article: Article, folderId: Long?) {
        viewModelScope.launch {
            articleRepository.moveToFolder(article.id, folderId)
        }
    }

    // ---------------- 收藏夹管理 ----------------

    /** 新建收藏夹 */
    fun createFolder(name: String) {
        viewModelScope.launch { folderRepository.createFolder(name) }
    }

    /** 重命名收藏夹 */
    fun renameFolder(folderId: Long, newName: String) {
        viewModelScope.launch { folderRepository.renameFolder(folderId, newName) }
    }

    /** 删除收藏夹（夹内文章自动变未分组；若正在筛选该夹，回到"全部"） */
    fun deleteFolder(folder: FavoriteFolder) {
        viewModelScope.launch {
            folderRepository.deleteFolder(folder)
            if (selectedFolder.value == folder.id) {
                selectedFolder.value = null
            }
        }
    }

    companion object {
        /** 筛选值：未分组 */
        const val UNGROUPED_FOLDER_ID = -1L

        /** 工厂：注入 Application + 文章仓库 + 收藏夹仓库 */
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                FavoriteViewModel(
                    application,
                    ArticleRepository.getInstance(application),
                    FavoriteFolderRepository.getInstance(application)
                )
            }
        }
    }
}
