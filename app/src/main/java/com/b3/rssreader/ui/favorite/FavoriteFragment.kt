package com.b3.rssreader.ui.favorite

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.b3.rssreader.R
import com.b3.rssreader.data.local.entity.Article
import com.b3.rssreader.databinding.FragmentFavoriteBinding
import com.b3.rssreader.ui.article.ArticleAdapter
import com.b3.rssreader.ui.article.ArticleListUiState
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * 收藏页 —— 收藏文章的展示与取消收藏（自选功能）
 *
 * - 复用 ArticleAdapter（与文章列表页同一套条目样式）
 * - 条目右侧金色星按钮 → 取消收藏，Snackbar 提示并提供"撤销"
 * - 点击条目 → 进入文章详情
 * - 三态界面：加载中 / 空（暂无收藏）/ 失败
 */
class FavoriteFragment : Fragment() {

    private var _binding: FragmentFavoriteBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FavoriteViewModel by viewModels { FavoriteViewModel.factory() }

    /** 适配器：条目点击进详情；星按钮取消收藏（由 Fragment 弹 Snackbar） */
    private val articleAdapter: ArticleAdapter by lazy {
        ArticleAdapter(
            onItemClick = { article -> openArticleDetail(article) },
            onFavoriteClick = { article -> removeFavoriteWithUndo(article) }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoriteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvFavorites.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFavorites.adapter = articleAdapter

        // 安全收集三态状态并渲染
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    /** 取消收藏 + Snackbar 提示（带"撤销"按钮，误点可恢复） */
    private fun removeFavoriteWithUndo(article: Article) {
        viewModel.removeFavorite(article)
        Snackbar.make(binding.root, R.string.msg_favorite_removed, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) { viewModel.restoreFavorite(article) }
            .show()
    }

    /** 点击条目 → 文章详情（只传 articleId，标题/链接由详情页自己查库） */
    private fun openArticleDetail(article: Article) {
        findNavController().navigate(
            R.id.nav_article_detail,
            bundleOf("articleId" to article.id)
        )
    }

    /** 三态渲染（与文章列表页同构） */
    private fun render(state: ArticleListUiState) {
        when (state) {
            is ArticleListUiState.Loading -> {
                binding.progressLoading.isVisible = true
                binding.rvFavorites.isVisible = false
                binding.layoutEmpty.isVisible = false
                binding.layoutError.isVisible = false
            }

            is ArticleListUiState.Success -> {
                binding.progressLoading.isVisible = false
                binding.layoutError.isVisible = false

                if (state.articles.isEmpty()) {
                    binding.rvFavorites.isVisible = false
                    binding.layoutEmpty.isVisible = true
                } else {
                    binding.layoutEmpty.isVisible = false
                    binding.rvFavorites.isVisible = true
                    articleAdapter.submitList(state.articles)
                }
            }

            is ArticleListUiState.Error -> {
                binding.progressLoading.isVisible = false
                binding.rvFavorites.isVisible = false
                binding.layoutEmpty.isVisible = false
                binding.layoutError.isVisible = true
                binding.tvError.text = state.message
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
