package com.b3.rssreader.ui.favorite

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
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
import com.b3.rssreader.data.local.dao.FavoriteFolderWithCount
import com.b3.rssreader.data.local.entity.Article
import com.b3.rssreader.data.local.entity.FavoriteFolder
import com.b3.rssreader.databinding.FragmentFavoriteBinding
import com.b3.rssreader.ui.article.ArticleAdapter
import com.b3.rssreader.ui.article.ArticleListUiState
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * 收藏页 —— 收藏文章 + 收藏夹分组管理（自选功能）
 *
 * 交互一览：
 * - 顶部 chips        → 筛选：全部 / 未分组 / 各收藏夹
 * - 工具栏 ⋮ 菜单     → 新建收藏夹 / 管理收藏夹（重命名、删除）
 * - 点击条目          → 进入文章详情
 * - 点击金色星按钮    → 取消收藏（Snackbar 可撤销）
 * - 长按条目          → 移动到其他收藏夹
 */
class FavoriteFragment : Fragment() {

    private var _binding: FragmentFavoriteBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FavoriteViewModel by viewModels { FavoriteViewModel.factory() }

    /** 适配器：点击进详情；星按钮取消收藏；长按移动分组 */
    private val articleAdapter: ArticleAdapter by lazy {
        ArticleAdapter(
            onItemClick = { article -> openArticleDetail(article) },
            onFavoriteClick = { article -> removeFavoriteWithUndo(article) },
            onItemLongClick = { article -> showMoveDialog(article) }
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

        setupToolbar()
        binding.rvFavorites.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFavorites.adapter = articleAdapter

        // 收藏夹列表变化 → 重建筛选 chips（含选中态）
        viewModel.folders.observe(viewLifecycleOwner) { folders ->
            rebuildChips(folders)
        }

        // 三态渲染
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    /** 工具栏菜单：新建 / 管理收藏夹 */
    private fun setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.favorite_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_new_folder -> {
                    showNameInputDialog(getString(R.string.folder_new), null) { name ->
                        viewModel.createFolder(name)
                    }
                    true
                }

                R.id.action_manage_folders -> {
                    showManageDialog()
                    true
                }

                else -> false
            }
        }
    }

    // ---------------- 筛选 chips ----------------

    /** 重建筛选条：全部 / 未分组 / 每个收藏夹（带文章数） */
    private fun rebuildChips(folders: List<FavoriteFolderWithCount>) {
        val group = binding.chipGroupFolders
        group.removeAllViews()

        val current = viewModel.selectedFolderId.value
        addChip(getString(R.string.folder_all), null, current == null)
        addChip(
            getString(R.string.folder_ungrouped),
            FavoriteViewModel.UNGROUPED_FOLDER_ID,
            current == FavoriteViewModel.UNGROUPED_FOLDER_ID
        )
        folders.forEach { item ->
            addChip(
                "${item.folder.name} (${item.articleCount})",
                item.folder.id,
                current == item.folder.id
            )
        }
    }

    private fun addChip(label: String, folderId: Long?, checked: Boolean) {
        val chip = Chip(requireContext()).apply {
            text = label
            isCheckable = true
            isChecked = checked
            // 点击切换筛选（ChipGroup singleSelection 自动取消其它选中）
            setOnClickListener { viewModel.selectFolder(folderId) }
        }
        binding.chipGroupFolders.addView(chip)
    }

    // ---------------- 文章操作 ----------------

    /** 取消收藏 + 撤销提示 */
    private fun removeFavoriteWithUndo(article: Article) {
        viewModel.removeFavorite(article)
        Snackbar.make(binding.root, R.string.msg_favorite_removed, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) { viewModel.restoreFavorite(article) }
            .show()
    }

    /** 长按 → 移动文章到其他收藏夹 */
    private fun showMoveDialog(article: Article) {
        val folders = viewModel.folders.value.orEmpty().map { it.folder }
        // 选项：第 0 项"未分组"，其余为各收藏夹
        val names = mutableListOf(getString(R.string.folder_ungrouped))
        names += folders.map { it.name }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.folder_move)
            .setItems(names.toTypedArray()) { _, which ->
                val folderId = if (which == 0) null else folders[which - 1].id
                viewModel.moveToFolder(article, folderId)
            }
            .show()
    }

    /** 点击条目 → 文章详情 */
    private fun openArticleDetail(article: Article) {
        findNavController().navigate(
            R.id.nav_article_detail,
            bundleOf("articleId" to article.id)
        )
    }

    // ---------------- 收藏夹管理对话框 ----------------

    /** 管理收藏夹：列表 → 点击某项 → 重命名/删除 */
    private fun showManageDialog() {
        val folders = viewModel.folders.value.orEmpty().map { it.folder }
        if (folders.isEmpty()) {
            Snackbar.make(binding.root, R.string.folder_manage_empty, Snackbar.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.folder_manage)
            .setItems(folders.map { it.name }.toTypedArray()) { _, which ->
                val folder = folders[which]
                showFolderOperationDialog(folder)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** 针对单个收藏夹的操作：重命名 / 删除 */
    private fun showFolderOperationDialog(folder: FavoriteFolder) {
        val operations = arrayOf(
            getString(R.string.folder_rename),
            getString(R.string.folder_delete)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(folder.name)
            .setItems(operations) { _, which ->
                when (which) {
                    0 -> showNameInputDialog(
                        getString(R.string.folder_rename_title),
                        folder.name
                    ) { newName -> viewModel.renameFolder(folder.id, newName) }

                    1 -> showDeleteFolderConfirm(folder)
                }
            }
            .show()
    }

    /** 删除收藏夹确认（夹内文章会变未分组，不丢数据） */
    private fun showDeleteFolderConfirm(folder: FavoriteFolder) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.folder_delete_title)
            .setMessage(getString(R.string.folder_delete_message, folder.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteFolder(folder) }
            .show()
    }

    /**
     * 通用"输入名称"对话框（新建/重命名共用）。
     * @param title   对话框标题
     * @param initial 初始文本（重命名时预填），null = 新建
     */
    private fun showNameInputDialog(title: String, initial: String?, onConfirm: (String) -> Unit) {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.folder_name_hint)
            setText(initial)
            setSelection(text.length)
            isSingleLine = true
        }
        // 用带边距的容器包住 EditText，避免贴边
        val margin = (getResources().displayMetrics.density * 24).toInt()
        val container = FrameLayout(requireContext()).apply {
            setPadding(margin, 0, margin, 0)
            addView(input)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) onConfirm(name)
            }
            .show()
    }

    // ---------------- 三态渲染 ----------------

    private fun render(state: ArticleListUiState) {
        when (state) {
            is ArticleListUiState.Loading -> {
                binding.stateLayout.showLoading()
                binding.rvFavorites.isVisible = false
            }

            is ArticleListUiState.Success -> {
                if (state.articles.isEmpty()) {
                    binding.rvFavorites.isVisible = false
                    binding.stateLayout.showEmpty(
                        getString(R.string.favorite_empty_title),
                        getString(R.string.favorite_empty_hint)
                    )
                } else {
                    binding.stateLayout.hideAll()
                    binding.rvFavorites.isVisible = true
                    articleAdapter.submitList(state.articles)
                }
            }

            is ArticleListUiState.Error -> {
                binding.rvFavorites.isVisible = false
                binding.stateLayout.showError(state.message)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
