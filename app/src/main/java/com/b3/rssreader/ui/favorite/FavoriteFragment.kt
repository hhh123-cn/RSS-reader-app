package com.b3.rssreader.ui.favorite

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.b3.rssreader.databinding.FragmentFavoriteBinding

/**
 * 收藏页 —— 收藏的文章列表（自选功能）
 *
 * 当前为骨架占位；后续阶段实现：
 * - 展示所有收藏文章（is_favorite = 1）
 * - 取消收藏操作
 * - 收藏夹分组管理（自选加分项）
 * - 加载中 / 空数据 / 加载失败 三种界面状态
 */
class FavoriteFragment : Fragment() {

    private var _binding: FragmentFavoriteBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoriteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
