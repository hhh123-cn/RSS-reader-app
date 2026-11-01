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
 * 为什么用 ListAdapter 而不是普通 RecyclerView.Adapter？
 * 普通适配器数据变化时要手动调用 notifyDataSetChanged()，
 * 该调用会重绘整个列表：卡顿、无动画、容易闪屏。
 * ListAdapter + DiffUtil 会自动对比新旧两份列表：
 *   - 只有真正变化的行才刷新（高效）
 *   - 自动播放增删移动动画（顺滑）
 * 我们只需在数据变化时调用 submitList(新列表)。
 *
 * @param onItemClick 条目点击回调（弹"编辑/删除"操作菜单），
 *                    由 Fragment 注入；后续阶段"点击进入文章列表"也走这个回调扩展。
 */
class FeedAdapter(
    private val onItemClick: (feed: Feed, anchorView: View) -> Unit
) : ListAdapter<Feed, FeedAdapter.FeedViewHolder>(DIFF_CALLBACK) {

    /** 创建条目视图：inflate item_feed.xml 并包一层 ViewHolder */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        val binding = ItemFeedBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return FeedViewHolder(binding)
    }

    /** 把第 position 条数据渲染进 ViewHolder（getItem 是 ListAdapter 提供的安全取值） */
    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    /**
     * 条目 ViewHolder：持有 item 布局的 ViewBinding，
     * bind() 负责把一条 Feed 数据写到控件上、绑定点击事件。
     */
    class FeedViewHolder(
        private val binding: ItemFeedBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(feed: Feed, onItemClick: (Feed, View) -> Unit) {
            binding.tvTitle.text = feed.title   // 订阅源名称
            binding.tvUrl.text = feed.url       // RSS 地址

            // 点击条目 → 回调给 Fragment（itemView 作为菜单锚点）
            binding.root.setOnClickListener { onItemClick(feed, it) }
        }
    }

    companion object {
        /** DiffUtil 差异对比器，供 ListAdapter 计算最小刷新范围 */
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Feed>() {
            override fun areItemsTheSame(oldItem: Feed, newItem: Feed): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Feed, newItem: Feed): Boolean =
                oldItem == newItem
        }
    }
}
