package com.b3.notification.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.b3.notification.databinding.ViewStateLayoutBinding

/**
 * 通用三态视图组件（加载中 / 空数据 / 加载失败）—— 伴生模块版
 *
 * 与主 App 的 com.b3.rssreader.ui.common.StateLayout 功能一致：
 * 两个模块是独立 APK，无法共享 Java 类（也不该用库模块耦合），
 * 因此各自持有这份小组件；若以后抽出公共库模块可合并。
 *
 * 用法：
 *   stateLayout.showLoading()
 *   stateLayout.showEmpty("暂无订阅源", "提示说明")
 *   stateLayout.showError("加载失败")
 *   stateLayout.hideAll()
 */
class StateLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // merge 根布局：Binding 只有双参 inflate（自动挂到 parent）
    private val binding = ViewStateLayoutBinding.inflate(
        LayoutInflater.from(context), this
    )

    /** 加载中 */
    fun showLoading() {
        binding.viewLoading.isVisible = true
        binding.viewEmpty.isVisible = false
        binding.viewError.isVisible = false
    }

    /** 空数据态（title/hint 传 null 表示沿用上一次文案） */
    fun showEmpty(title: String? = null, hint: String? = null) {
        title?.let { binding.tvEmptyTitle.text = it }
        hint?.let { binding.tvEmptyHint.text = it }
        binding.viewLoading.isVisible = false
        binding.viewEmpty.isVisible = true
        binding.viewError.isVisible = false
    }

    /** 加载失败态（含重试按钮） */
    fun showError(message: String? = null) {
        message?.let { binding.tvErrorMsg.text = it }
        binding.viewLoading.isVisible = false
        binding.viewEmpty.isVisible = false
        binding.viewError.isVisible = true
    }

    /** 隐藏全部状态视图 */
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
