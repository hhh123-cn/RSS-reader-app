package com.b3.notification

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.b3.notification.data.NotificationFeedRepository.FeedInfo
import com.b3.notification.databinding.ItemFeedSwitchBinding

/**
 * 订阅源通知开关列表适配器
 *
 * 与主 App 的 ListAdapter 不同：这里的数据是 ContentProvider 快照
 * （无 DiffUtil 的响应式增量），用普通 Adapter + 整体替换即可，
 * 列表规模小，性能无压力。
 */
class FeedSwitchAdapter(
    private val onSwitchChanged: (feed: FeedInfo, enabled: Boolean) -> Unit
) : RecyclerView.Adapter<FeedSwitchAdapter.FeedSwitchViewHolder>() {

    private val items = mutableListOf<FeedInfo>()

    /** 整体替换列表（快照式数据源） */
    fun submitList(newItems: List<FeedInfo>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedSwitchViewHolder {
        val binding = ItemFeedSwitchBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return FeedSwitchViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: FeedSwitchViewHolder, position: Int) {
        holder.bind(items[position], onSwitchChanged)
    }

    class FeedSwitchViewHolder(
        private val binding: ItemFeedSwitchBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            feed: FeedInfo,
            onSwitchChanged: (FeedInfo, Boolean) -> Unit
        ) {
            binding.tvTitle.text = feed.title
            binding.tvUrl.text = feed.url

            // 先移除旧监听再 setChecked：
            // 否则 setChecked 会触发 onCheckedChanged 造成"设置开关 → 又触发写回"的死循环
            binding.switchEnabled.setOnCheckedChangeListener(null)
            binding.switchEnabled.isChecked = feed.notificationEnabled
            binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                onSwitchChanged(feed, isChecked)
            }
        }
    }
}
