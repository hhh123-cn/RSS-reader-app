package com.b3.rssreader.ui.article

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
import com.b3.rssreader.databinding.FragmentArticleListBinding
import kotlinx.coroutines.launch

/**
 * 文章列表页 —— 按订阅源展示文章 / 全部文章聚合（三态界面）
 *
 * 两种模式（由导航参数决定，见 ArticleListViewModel 注释）：
 * - feedId = -1：底部"文章列表"tab → 全部文章
 * - feedId ≥ 0 ：订阅源列表点击进入 → 该订阅源的文章
 *
 * 三态渲染（本页面的核心规范）：
 * - Loading → 进度圈
 * - Success(空) → 空态提示；Success(非空) → 列表
 * - Error → 错误提示 + 重试按钮
 */
class ArticleListFragment : Fragment() {

    private var _binding: FragmentArticleListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ArticleListViewModel by viewModels { ArticleListViewModel.factory() }

    private val articleAdapter: ArticleAdapter by lazy {
        ArticleAdapter(onItemClick = { article -> openArticleDetail(article) })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArticleListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 标题：单源模式显示订阅源名，聚合模式显示"文章列表"
        binding.toolbar.title =
            viewModel.feedTitle.ifBlank { getString(R.string.tab_article) }

        // 列表初始化
        binding.rvArticles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvArticles.adapter = articleAdapter

        // 失败态重试
        binding.stateLayout.setOnRetryListener { viewModel.retry() }

        // 收集 UI 状态并渲染三态
        // repeatOnLifecycle(STARTED)：页面回到前台才开始收集、退到后台自动停止，
        // 是官方推荐的"安全收集 StateFlow"写法
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    /**
     * 点击文章条目 → 导航到详情页（只传 articleId，
     * 详情页 ViewModel 会自己查库补全标题/链接——深链接场景共用同一逻辑）。
     */
    private fun openArticleDetail(article: com.b3.rssreader.data.local.entity.Article) {
        findNavController().navigate(
            R.id.nav_article_detail,
            bundleOf("articleId" to article.id)
        )
    }

    /** 根据 UiState 渲染三种界面状态（StateLayout 统一三态样式） */
    private fun render(state: ArticleListUiState) {
        when (state) {
            is ArticleListUiState.Loading -> {
                binding.stateLayout.showLoading()
                binding.rvArticles.isVisible = false
            }

            is ArticleListUiState.Success -> {
                if (state.articles.isEmpty()) {
                    binding.rvArticles.isVisible = false
                    binding.stateLayout.showEmpty(
                        getString(R.string.article_empty_title),
                        getString(R.string.article_empty_hint)
                    )
                } else {
                    binding.stateLayout.hideAll()
                    binding.rvArticles.isVisible = true
                    articleAdapter.submitList(state.articles)
                }
            }

            is ArticleListUiState.Error -> {
                binding.rvArticles.isVisible = false
                binding.stateLayout.showError(state.message)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
