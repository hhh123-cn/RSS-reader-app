package com.b3.rssreader.ui.article

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.b3.rssreader.data.local.entity.Article
import com.b3.rssreader.databinding.ItemArticleBinding
import com.google.android.material.color.MaterialColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文章列表适配器 —— 按时间倒序展示，已读/未读样式区分
 *
 * 已读/未读的视觉区分：
 * - 未读：标题加粗深色 + 左侧橙色圆点
 * - 已读：标题常规灰色，圆点隐藏
 * 排序：数据源（Room DAO）已按 pub_date DESC 倒序，适配器不做二次排序。
 *
 * ListAdapter + DiffUtil：isRead 变化属于"内容变化"（data class equals 覆盖全字段），
 * 用户标记已读后对应行会自动重绘样式，无需手动 notify。
 *
 * 两个回调：
 * @param onItemClick 点击条目 → 进入文章详情（列表页与收藏页共用）
 * @param onFavoriteClick 点击星形按钮 → 取消收藏；传 null 时星形按钮隐藏
 *                       （文章列表页不需要收藏入口，收藏页传入即可复用本适配器）
 */
class ArticleAdapter(
    private val onItemClick: (article: Article) -> Unit,
    private val onFavoriteClick: ((article: Article) -> Unit)? = null
) : ListAdapter<Article, ArticleAdapter.ArticleViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleViewHolder {
        val binding = ItemArticleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ArticleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ArticleViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick, onFavoriteClick)
    }

    class ArticleViewHolder(
        private val binding: ItemArticleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            article: Article,
            onItemClick: (Article) -> Unit,
            onFavoriteClick: ((Article) -> Unit)?
        ) {
            // 标题文字
            binding.tvTitle.text = article.title
            // 已读/未读样式：
            // setTypeface(null, style)：null 表示保留字体族，只改字形（常规/加粗）
            binding.tvTitle.setTypeface(
                null, if (article.isRead) Typeface.NORMAL else Typeface.BOLD
            )
            // 颜色：未读用主题主文字色，已读用次要文字色。
            // 用 MaterialColors 解析主题属性，亮色/暗色模式下都正确
            // （不要用 android.R.color.primary_text_dark 之类固定色，暗色模式下会看不清）
            binding.tvTitle.setTextColor(
                MaterialColors.getColor(
                    binding.root,
                    if (article.isRead) com.google.android.material.R.attr.colorOnSurfaceVariant
                    else com.google.android.material.R.attr.colorOnSurface
                )
            )

            // 未读圆点：已读隐藏
            binding.dotUnread.isVisible = !article.isRead

            // 摘要：空则隐藏该行
            binding.tvDesc.text = article.description.orEmpty()
            binding.tvDesc.isVisible = !article.description.isNullOrBlank()

            // 时间：相对时间（"3 小时前"），无发布时间显示空
            binding.tvTime.text = formatRelativeTime(article.pubDate)

            // 点击条目 → 进入详情
            binding.root.setOnClickListener { onItemClick(article) }

            // 收藏星按钮：收藏页传入回调时显示，点击 → 取消收藏
            binding.btnFavorite.isVisible = onFavoriteClick != null
            binding.btnFavorite.setOnClickListener { onFavoriteClick?.invoke(article) }
        }
    }

    companion object {

        /** DiffUtil：id 判同条，equals 判内容（isRead 变化也会触发刷新） */
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Article>() {
            override fun areItemsTheSame(oldItem: Article, newItem: Article): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Article, newItem: Article): Boolean =
                oldItem == newItem
        }

        /**
         * 相对时间格式化：刚刚 / x 分钟前 / x 小时前 / x 天前 / 具体日期。
         * 与常见资讯类 App 的列表时间展示一致，阅读体验更好。
         * 时间戳为空（解析失败）返回空串。
         */
        fun formatRelativeTime(timestamp: Long?): String {
            if (timestamp == null) return ""
            val diff = System.currentTimeMillis() - timestamp
            return when {
                diff < 60_000L -> "刚刚"
                diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
                diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
                diff < 7L * 86_400_000L -> "${diff / 86_400_000L} 天前"
                else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }
}
