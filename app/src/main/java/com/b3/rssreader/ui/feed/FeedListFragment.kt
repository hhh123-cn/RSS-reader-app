package com.b3.rssreader.ui.feed

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.b3.rssreader.R
import com.b3.rssreader.data.local.entity.Feed
import com.b3.rssreader.databinding.DialogAddFeedBinding
import com.b3.rssreader.databinding.FragmentFeedListBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/**
 * 订阅源页 —— 订阅源的增删改查
 *
 * 交互一览：
 * - 工具栏 + 按钮         → 弹出"添加订阅源"对话框
 * - 点击订阅源条目        → 进入该订阅源的文章列表（导航，带 feedId/feedTitle）
 * - 点击条目右侧 ⋮        → 弹出操作菜单（编辑 / 删除）
 * - 编辑                 → 预填数据的对话框，保存写回数据库
 * - 删除                 → 二次确认对话框（提示会级联删除文章）
 * - 所有操作结果          → Snackbar 提示（成功 / 重复 / 失败）
 */
class FeedListFragment : Fragment() {

    // ViewBinding 标准写法：可空持有，onDestroyView 置空防泄漏
    private var _binding: FragmentFeedListBinding? = null
    private val binding get() = _binding!!

    /** ViewModel：数据 + 业务操作（工厂注入 Repository） */
    private val viewModel: FeedListViewModel by viewModels { FeedListViewModel.factory() }

    /** 适配器：条目点击进入文章列表；⋮ 点击弹出编辑/删除菜单 */
    private val feedAdapter: FeedAdapter by lazy {
        FeedAdapter(
            onItemClick = { feed -> openArticleList(feed) },
            onMoreClick = { feed, anchorView -> showItemMenu(feed, anchorView) }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupList()
        observeFeeds()
        observeMessages()
    }

    /** 顶部工具栏：挂"添加订阅"按钮 */
    private fun setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.feed_list_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_add_feed -> {
                    showAddOrEditDialog(null) // null = 添加模式
                    true
                }

                else -> false
            }
        }
    }

    /** 列表初始化 */
    private fun setupList() {
        binding.rvFeeds.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFeeds.adapter = feedAdapter
    }

    /** 观察订阅源列表：数据变化自动刷新 + 空状态切换 */
    private fun observeFeeds() {
        viewModel.feeds.observe(viewLifecycleOwner) { feeds ->
            feedAdapter.submitList(feeds)
            updateEmptyState(feeds.isEmpty())
        }
    }

    /** 观察一次性提示消息（Event 保证只消费一次） */
    private fun observeMessages() {
        viewModel.message.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { msg ->
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 添加 / 编辑共用的对话框。
     * @param feed 传 null 为添加模式；传已有订阅源为编辑模式（预填数据）
     */
    private fun showAddOrEditDialog(feed: Feed?) {
        val isEdit = feed != null
        // inflate 对话框内容布局（独立于页面布局的 ViewBinding）
        val dialogBinding = DialogAddFeedBinding.inflate(layoutInflater)

        // 编辑模式：预填现有数据
        if (isEdit) {
            dialogBinding.etTitle.setText(feed.title)
            dialogBinding.etUrl.setText(feed.url)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (isEdit) R.string.feed_dialog_edit_title else R.string.feed_dialog_add_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .create()

        // 注意：不能用 setPositiveButton + return 来阻止关闭——
        // AlertDialog 的按钮默认点击即 dismiss。
        // 这里用 dialog.setButton 手动控制：
        // 校验失败 → 不关闭（错误提示留在输入框下方）；校验通过 → 提交并关闭。
        dialog.setButton(
            DialogInterface.BUTTON_POSITIVE,
            getString(if (isEdit) R.string.feed_dialog_save else R.string.feed_dialog_add_confirm)
        ) { _, _ ->
            val title = dialogBinding.etTitle.text?.toString()?.trim().orEmpty()
            val url = dialogBinding.etUrl.text?.toString()?.trim().orEmpty()

            // 校验 RSS 地址：必填 + http/https 开头
            if (!isValidUrl(url)) {
                dialogBinding.tilUrl.error = getString(R.string.feed_error_invalid_url)
                return@setButton // 校验失败：不关对话框
            }

            if (isEdit) {
                // 编辑：copy 出修改后的对象写回数据库
                viewModel.updateFeed(feed.copy(title = title.ifBlank { url }, url = url))
            } else {
                viewModel.addFeed(title, url)
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * 点击订阅源条目 → 导航到文章列表页。
     * 携带 feedId（指定只看这个源）和 feedTitle（页面标题显示订阅源名）。
     * 文章列表页通过 SavedStateHandle 接收这两个参数（见 ArticleListViewModel）。
     */
    private fun openArticleList(feed: Feed) {
        findNavController().navigate(
            R.id.nav_article_list,
            bundleOf("feedId" to feed.id, "feedTitle" to feed.title)
        )
    }

    /** 条目点击 → 弹出"编辑 / 删除"操作菜单（anchorView 作为弹出锚点） */
    private fun showItemMenu(feed: Feed, anchorView: View) {
        val popup = PopupMenu(requireContext(), anchorView)
        popup.menuInflater.inflate(R.menu.feed_item_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit_feed -> showAddOrEditDialog(feed)   // 编辑
                R.id.action_delete_feed -> showDeleteConfirm(feed)    // 删除
            }
            true
        }
        popup.show()
    }

    /** 删除二次确认（提示会级联删除文章） */
    private fun showDeleteConfirm(feed: Feed) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.feed_delete_title)
            .setMessage(getString(R.string.feed_delete_message, feed.title))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteFeed(feed)
            }
            .show()
    }

    /** 空状态切换 */
    private fun updateEmptyState(isEmpty: Boolean) {
        binding.layoutEmpty.isVisible = isEmpty
        binding.rvFeeds.isVisible = !isEmpty
    }

    /** 简单 URL 校验：必须 http/https 开头 */
    private fun isValidUrl(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://")

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
