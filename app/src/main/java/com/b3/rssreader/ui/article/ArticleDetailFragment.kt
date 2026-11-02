package com.b3.rssreader.ui.article

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.b3.rssreader.R
import com.b3.rssreader.databinding.FragmentArticleDetailBinding
import com.google.android.material.color.MaterialColors

/**
 * 文章详情页 —— WebView 加载原文 + 进入自动标记已读
 *
 * 工作流程：
 * 1. 进入页面 → ArticleDetailViewModel 自动把文章标为已读
 *    （返回列表时对应条目已自动变灰——Room Flow 联动，无需手动刷新）
 * 2. WebView 加载文章原文链接
 * 3. 加载失败 → 错误态：可重试，或"在浏览器中打开"（降级方案，不崩溃）
 *
 * 返回键处理：WebView 内有历史记录时先回退网页，没有则返回列表页。
 */
class ArticleDetailFragment : Fragment() {

    companion object {
        /** 已收藏星星的金色（Material Amber 500） */
        private const val FAVORITE_COLOR = 0xFFFFC107.toInt()
    }

    private var _binding: FragmentArticleDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ArticleDetailViewModel by viewModels { ArticleDetailViewModel.factory() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArticleDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupWebView()
        setupButtons()
        setupBackHandling()

        // 开始加载原文（链接为空视为加载失败）
        if (viewModel.articleLink.isBlank()) {
            showError()
        } else {
            binding.webView.loadUrl(viewModel.articleLink)
        }
    }

    /** 标题栏：文章标题 + 返回箭头 + 收藏按钮 */
    private fun setupToolbar() {
        binding.toolbar.title = viewModel.articleTitle
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // 挂收藏菜单并监听点击：切换收藏状态
        binding.toolbar.inflateMenu(R.menu.article_detail_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_favorite -> {
                    viewModel.toggleFavorite()
                    true
                }

                else -> false
            }
        }

        // 观察收藏状态，实时更新星形图标（实心金 = 已收藏 / 描边灰 = 未收藏）
        viewModel.isFavorite.observe(viewLifecycleOwner) { favorite ->
            updateFavoriteIcon(favorite)
        }
    }

    /**
     * 更新收藏按钮图标与颜色。
     * 直接用 setTint 指定颜色：Toolbar 会默认给菜单图标统一着色
     * （colorControlNormal），必须显式覆盖才能显示金色实心星。
     */
    private fun updateFavoriteIcon(favorite: Boolean) {
        val item = binding.toolbar.menu.findItem(R.id.action_favorite) ?: return
        val iconRes = if (favorite) R.drawable.ic_star_filled else R.drawable.ic_star_border
        val color = if (favorite) FAVORITE_COLOR else MaterialColors.getColor(
            binding.toolbar,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        val drawable = ContextCompat.getDrawable(requireContext(), iconRes)!!
        drawable.setTint(color)
        item.icon = drawable
    }

    /**
     * WebView 配置与加载回调。
     * 需要关闭 Android 硬编码警告（WebView 类 lint 提示），这里已做完整配置。
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.settings.apply {
            javaScriptEnabled = true          // 现代站点几乎都依赖 JS，必须开启
            domStorageEnabled = true          // 部分站点需要 localStorage 才能正常渲染
            // 混合内容：允许 HTTPS 页面加载 HTTP 资源（部分老站点会用到）
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        binding.webView.webViewClient = object : WebViewClient() {

            // 页面开始加载：显示进度条
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.progressLoading.isVisible = true
                binding.layoutError.isVisible = false
            }

            // 页面加载完成：隐藏进度条
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.progressLoading.isVisible = false
            }

            // 加载失败（断网、404、DNS 失败等）：切到错误态，不崩溃
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                // 只处理"主页面"错误，忽略子资源（图片等）失败
                if (request?.isForMainFrame == true) {
                    showError()
                }
            }

            // HTTPS 证书错误：默认会白屏，这里取消加载并切到错误态
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handler?.cancel()
                showError()
            }
        }
    }

    /** 错误态按钮：重试 / 在浏览器中打开 */
    private fun setupButtons() {
        binding.btnRetry.setOnClickListener {
            binding.layoutError.isVisible = false
            binding.webView.loadUrl(viewModel.articleLink)
        }
        binding.btnOpenBrowser.setOnClickListener {
            // 降级方案：交给系统浏览器打开原文
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(viewModel.articleLink)))
            }
        }
    }

    /**
     * 返回键：WebView 有历史 → 网页回退；否则 → 返回列表页。
     * 用 OnBackPressedCallback 拦截（推荐做法，替代过时的 onBackPressed 覆写）
     */
    private fun setupBackHandling() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    isEnabled = false                     // 放行，让系统走默认返回
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    /** 显示错误态：隐藏 WebView，展示错误提示与按钮 */
    private fun showError() {
        binding.progressLoading.isVisible = false
        binding.webView.isVisible = false
        binding.layoutError.isVisible = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 销毁 WebView，防止内存泄漏（WebView 持有 Activity 引用）
        binding.webView.destroy()
        _binding = null
    }
}
