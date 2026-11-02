package com.b3.rssreader.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.b3.rssreader.databinding.ViewStateLayoutBinding

/**
 * 通用三态视图组件（加载中 / 空数据 / 加载失败）
 *
 * 背景：项目要求所有页面处理三态。此前每个页面各自写一遍
 * ProgressBar + 空态布局 + 失败布局，重复且样式容易跑偏。
 * 本组件把三态收敛到一个自定义 View 里，页面只需：
 *   stateLayout.showLoading()
 *   stateLayout.showEmpty("暂无文章", "提示说明")
 *   stateLayout.showError("加载失败")
 *   stateLayout.hideAll()
 * 样式统一、行为统一（失败态自带重试按钮回调）。
 *
 * 布局结构见 res/layout/view_state_layout.xml（merge 根 + 三组视图），
 * 同一时刻只有一组可见，由本类方法切换。
 */
class StateLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // 注意：根布局是 <merge>，生成的 Binding 只有双参 inflate（自动挂到 parent），
    // 没有普通布局那种三参（attachToRoot）重载
    private val binding = ViewStateLayoutBinding.inflate(
        LayoutInflater.from(context), this
    )

    /** 加载中：只显示进度圈 */
    fun showLoading() {
        binding.viewLoading.isVisible = true
        binding.viewEmpty.isVisible = false
        binding.viewError.isVisible = false
    }

    /**
     * 空数据态。
     * @param title 主提示（如"暂无文章"），null 保持上一次文案
     * @param hint  辅助说明，null 保持上一次文案
     */
    fun showEmpty(title: String? = null, hint: String? = null) {
        title?.let { binding.tvEmptyTitle.text = it }
        hint?.let { binding.tvEmptyHint.text = it }
        binding.viewLoading.isVisible = false
        binding.viewEmpty.isVisible = true
        binding.viewError.isVisible = false
    }

    /** 加载失败态（含重试按钮，回调由页面注入） */
    fun showError(message: String? = null) {
        message?.let { binding.tvErrorMsg.text = it }
        binding.viewLoading.isVisible = false
        binding.viewEmpty.isVisible = false
        binding.viewError.isVisible = true
    }

    /** 隐藏全部状态视图（数据正常展示时调用） */
    fun hideAll() {
        binding.viewLoading.isVisible = false
        binding.viewEmpty.isVisible = false
        binding.viewError.isVisible = false
    }

    /** 失败态"重试"按钮回调 */
    fun setOnRetryListener(listener: () -> Unit) {
        binding.btnRetry.setOnClickListener { listener() }
    }
}
