package com.b3.rssreader.ui.feed

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.b3.rssreader.data.local.dao.FeedWithStats
import com.b3.rssreader.databinding.ItemFeedBinding

/**
 * 订阅源列表适配器 —— 名称 + 地址 + 未读数徽标
 *
 * 数据类用 FeedWithStats（订阅源 + 文章数/未读数聚合查询结果），
 * 未读数 > 0 时在名称右侧显示橙色徽标（99+ 封顶）。
 *
 * ListAdapter + DiffUtil：自动差异刷新与增删动画。
 *
 * 两个交互回调（由 Fragment 注入）：
 * @param onItemClick 点击条目 → 进入该订阅源的文章列表
 * @param onMoreClick 点击右侧 ⋮ 按钮 → 弹出刷新/编辑/删除菜单
 */
class FeedAdapter(
    private val onItemClick: (item: FeedWithStats) -> Unit,
    private val onMoreClick: (item: FeedWithStats, anchorView: View) -> Unit
) : ListAdapter<FeedWithStats, FeedAdapter.FeedViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        val binding = ItemFeedBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return FeedViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick, onMoreClick)
    }

    class FeedViewHolder(
        private val binding: ItemFeedBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: FeedWithStats,
            onItemClick: (FeedWithStats) -> Unit,
            onMoreClick: (FeedWithStats, View) -> Unit
        ) {
            binding.tvTitle.text = item.feed.title
            binding.tvUrl.text = item.feed.url

            // 未读徽标：0 隐藏；超过 99 显示"99+"
            if (item.unreadCount > 0) {
                binding.badgeUnread.isVisible = true
                binding.badgeUnread.text =
                    if (item.unreadCount > 99) "99+" else item.unreadCount.toString()
            } else {
                binding.badgeUnread.isVisible = false
            }

            // 条目点击 → 进入文章列表
            binding.root.setOnClickListener { onItemClick(item) }
            // ⋮ 按钮点击 → 刷新/编辑/删除菜单
            binding.btnMore.setOnClickListener { onMoreClick(item, it) }
        }
    }

    companion object {
        /**
         * DiffUtil：id 判同条；内容比较同时覆盖 feed 与未读数
         * （未读数变化要能触发徽标刷新）
         */
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<FeedWithStats>() {
            override fun areItemsTheSame(oldItem: FeedWithStats, newItem: FeedWithStats): Boolean =
                oldItem.feed.id == newItem.feed.id

            override fun areContentsTheSame(oldItem: FeedWithStats, newItem: FeedWithStats): Boolean =
                oldItem == newItem
        }
    }
}
