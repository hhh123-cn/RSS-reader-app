package com.b3.rssreader.ui.article

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.b3.rssreader.databinding.FragmentArticleListBinding

/**
 * 文章列表页 —— 全部文章的聚合页，按发布时间倒序
 *
 * 当前为骨架占位；后续阶段实现：
 * - 展示所有订阅源的文章（时间倒序）
 * - 已读/未读视觉区分（未读加粗/高亮）
 * - 点击文章进入详情页
 * - 加载中 / 空数据 / 加载失败 三种界面状态
 */
class ArticleListFragment : Fragment() {

    private var _binding: FragmentArticleListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArticleListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
