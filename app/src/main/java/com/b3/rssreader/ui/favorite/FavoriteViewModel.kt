package com.b3.rssreader.ui.favorite

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.b3.rssreader.R
import com.b3.rssreader.data.local.entity.Article
import com.b3.rssreader.data.repository.ArticleRepository
import com.b3.rssreader.ui.article.ArticleListUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 收藏页 ViewModel —— 收藏文章的展示与取消收藏
 *
 * 复用 ArticleListUiState 三态模型（Loading / Success / Error）：
 * Success 且列表为空 → 空态（"暂无收藏"）；非空 → 列表。
 */
class FavoriteViewModel(
    private val application: Application,
    private val repository: ArticleRepository
) : ViewModel() {

    /** 收藏列表（Room Flow → 三态 StateFlow），收藏状态变化自动刷新 */
    val uiState = repository.observeFavorites()
        .map<List<Article>, ArticleListUiState> { ArticleListUiState.Success(it) }
        .onStart { emit(ArticleListUiState.Loading) }
        .catch { emit(ArticleListUiState.Error(application.getString(R.string.article_error))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArticleListUiState.Loading)

    /** 取消收藏（收藏页星形按钮点击） */
    fun removeFavorite(article: Article) {
        viewModelScope.launch {
            repository.markFavorite(article.id, isFavorite = false)
        }
    }

    /** 恢复收藏（Snackbar"撤销"按钮） */
    fun restoreFavorite(article: Article) {
        viewModelScope.launch {
            repository.markFavorite(article.id, isFavorite = true)
        }
    }

    companion object {
        /** 工厂：注入 Application + Repository */
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                FavoriteViewModel(application, ArticleRepository.getInstance(application))
            }
        }
    }
}
