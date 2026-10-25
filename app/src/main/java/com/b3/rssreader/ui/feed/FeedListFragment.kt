package com.b3.rssreader.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.b3.rssreader.databinding.FragmentFeedListBinding

/**
 * 订阅源页 —— 订阅源的增删改查列表
 *
 * 当前为骨架占位；后续阶段实现：
 * - 列表展示所有订阅源（含每源未读计数）
 * - 添加订阅（悬浮按钮/菜单入口）
 * - 长按/侧滑删除、点击进入编辑
 * - 点击订阅源进入该源的文章列表
 * - 加载中 / 空数据 / 加载失败 三种界面状态
 */
class FeedListFragment : Fragment() {

    // ViewBinding 在 Fragment 中的标准写法：
    // 用可空变量持有 binding，onDestroyView 时置空，
    // 防止 Fragment 视图销毁后仍然持有 View 引用造成内存泄漏
    private var _binding: FragmentFeedListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 第三个参数传 false：不立即挂到 container，
        // 返回 binding.root 由系统自行挂载（Navigation 要求）
        _binding = FragmentFeedListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
