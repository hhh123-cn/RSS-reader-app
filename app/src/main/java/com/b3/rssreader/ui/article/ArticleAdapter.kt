package com.b3.rssreader.ui.article

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.b3.rssreader.data.local.entity.Article
import com.b3.rssreader.databinding.ItemArticleBinding
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
 */
class ArticleAdapter : ListAdapter<Article, ArticleAdapter.ArticleViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleViewHolder {
        val binding = ItemArticleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ArticleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ArticleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ArticleViewHolder(
        private val binding: ItemArticleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(article: Article) {
            // 标题文字
            binding.tvTitle.text = article.title
            // 已读/未读样式：
            // setTypeface(null, style)：null 表示保留字体族，只改字形（常规/加粗）
            binding.tvTitle.setTypeface(
                null, if (article.isRead) Typeface.NORMAL else Typeface.BOLD
            )
            // 颜色：未读用深色主文字色，已读用浅灰
            binding.tvTitle.setTextColor(
                ContextCompat.getColor(
                    binding.tvTitle.context,
                    if (article.isRead) android.R.color.darker_gray
                    else android.R.color.primary_text_dark
                )
            )

            // 未读圆点：已读隐藏
            binding.dotUnread.isVisible = !article.isRead

            // 摘要：空则隐藏该行
            binding.tvDesc.text = article.description.orEmpty()
            binding.tvDesc.isVisible = !article.description.isNullOrBlank()

            // 时间：相对时间（"3 小时前"），无发布时间显示空
            binding.tvTime.text = formatRelativeTime(article.pubDate)
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
