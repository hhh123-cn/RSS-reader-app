package com.b3.rssreader.ui.feed

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.b3.rssreader.data.local.entity.Feed
import com.b3.rssreader.databinding.ItemFeedBinding

/**
 * 订阅源列表适配器 —— 展示订阅源的名称与地址
 *
 * ListAdapter + DiffUtil：自动差异刷新与增删动画（详见 FeedAdapter 旧版注释）。
 *
 * 两个交互回调（由 Fragment 注入）：
 * @param onItemClick 点击条目 → 进入该订阅源的文章列表
 * @param onMoreClick 点击右侧 ⋮ 按钮 → 弹出编辑/删除菜单（anchorView = ⋮ 按钮）
 */
class FeedAdapter(
    private val onItemClick: (feed: Feed) -> Unit,
    private val onMoreClick: (feed: Feed, anchorView: View) -> Unit
) : ListAdapter<Feed, FeedAdapter.FeedViewHolder>(DIFF_CALLBACK) {

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
            feed: Feed,
            onItemClick: (Feed) -> Unit,
            onMoreClick: (Feed, View) -> Unit
        ) {
            binding.tvTitle.text = feed.title
            binding.tvUrl.text = feed.url

            // 条目点击 → 进入文章列表
            binding.root.setOnClickListener { onItemClick(feed) }
            // ⋮ 按钮点击 → 编辑/删除菜单
            binding.btnMore.setOnClickListener { onMoreClick(feed, it) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Feed>() {
            override fun areItemsTheSame(oldItem: Feed, newItem: Feed): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Feed, newItem: Feed): Boolean =
                oldItem == newItem
        }
    }
}
