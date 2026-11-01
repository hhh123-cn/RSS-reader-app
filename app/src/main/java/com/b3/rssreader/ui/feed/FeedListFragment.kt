package com.b3.rssreader.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.b3.rssreader.databinding.FragmentFeedListBinding

/**
 * 订阅源页 —— 订阅源列表展示
 *
 * 当前阶段（第 6 次提交）只做"展示"：
 * - 通过 ViewModel 观察数据库里的订阅源列表
 * - 有数据 → 列表展示名称与地址；无数据 → 空状态视图
 * - 增删改逻辑在后续阶段实现（本任务暂不接任何点击事件）
 *
 * 后续阶段预留：添加订阅入口（Toolbar 菜单/悬浮按钮）、
 * 点击进入该源文章列表、长按删除、编辑等。
 */
class FeedListFragment : Fragment() {

    // ViewBinding 标准写法：可空持有，onDestroyView 置空防泄漏
    private var _binding: FragmentFeedListBinding? = null
    private val binding get() = _binding!!

    /**
     * 通过 viewModels 委托获取 ViewModel：
     * - 生命周期跟随 Fragment（Fragment 销毁 → ViewModel 自动清理）
     * - factory() 负责把 Repository 注入 ViewModel
     */
    private val viewModel: FeedListViewModel by viewModels { FeedListViewModel.factory() }

    /** 适配器懒加载：Fragment 生命周期内只创建一个实例 */
    private val feedAdapter: FeedAdapter by lazy { FeedAdapter() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 配置列表：LinearLayoutManager = 垂直单列滚动
        binding.rvFeeds.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFeeds.adapter = feedAdapter

        // 2. 观察订阅源数据（MVVM 单向数据流）：
        //    数据库任何变化 → LiveData 推送新列表 → 刷新 UI
        //    用 viewLifecycleOwner：页面视图销毁即停止观察，防止泄漏
        viewModel.feeds.observe(viewLifecycleOwner) { feeds ->
            feedAdapter.submitList(feeds)          // ListAdapter 自动做差异刷新
            updateEmptyState(feeds.isEmpty())      // 空/非空状态切换
        }
    }

    /**
     * 空状态切换：
     * 列表为空 → 隐藏 RecyclerView、显示"暂无订阅源"；
     * 否则反向。
     */
    private fun updateEmptyState(isEmpty: Boolean) {
        binding.layoutEmpty.isVisible = isEmpty
        binding.rvFeeds.isVisible = !isEmpty
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
