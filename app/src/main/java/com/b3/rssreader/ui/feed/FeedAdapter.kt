package com.b3.rssreader.ui.feed

import android.view.LayoutInflater
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
 * @property DiffUtil.ItemCallback 两个方法：
 *   areItemsTheSame    —— 判断"是不是同一条数据"（用主键 id，O(1)）
 *   areContentsTheSame —— 判断"同一条数据的内容有没有变"（用 equals，data class 自动实现）
 */
class FeedAdapter : ListAdapter<Feed, FeedAdapter.FeedViewHolder>(DIFF_CALLBACK) {

    /** 创建条目视图：inflate item_feed.xml 并包一层 ViewHolder */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        val binding = ItemFeedBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return FeedViewHolder(binding)
    }

    /** 把第 position 条数据渲染进 ViewHolder（getItem 是 ListAdapter 提供的安全取值） */
    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * 条目 ViewHolder：持有 item 布局的 ViewBinding，
     * bind() 负责把一条 Feed 数据写到控件上。
     */
    class FeedViewHolder(
        private val binding: ItemFeedBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(feed: Feed) {
            binding.tvTitle.text = feed.title   // 订阅源名称
            binding.tvUrl.text = feed.url       // RSS 地址
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
