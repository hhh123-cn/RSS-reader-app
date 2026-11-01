package com.b3.rssreader.ui.common

/**
 * 单次事件包装 —— 用于"一次性 UI 提示"（如"添加成功"）
 *
 * 为什么需要它？
 * LiveData 的特性：配置变化（如旋转屏幕）重建页面后，
 * 会把最后一个值重新推给新页面。如果直接把提示文案放进 LiveData<String>，
 * 旋转屏幕后提示会再次弹出——用户会莫名其妙。
 *
 * Event 解决思路：内容只能被"消费"一次。
 * getContentIfNotHandled() 第一次调用返回内容并标记已消费，
 * 之后（包括重建页面后的重新分发）一律返回 null。
 *
 * 用法（ViewModel 端）：
 *   private val _message = MutableLiveData<Event<String>>()
 *   _message.postValue(Event("添加成功"))
 * 用法（UI 端）：
 *   viewModel.message.observe(viewLifecycleOwner) { event ->
 *       event.getContentIfNotHandled()?.let { 弹提示 }
 *   }
 */
class Event<out T>(private val content: T) {

    /** 是否已被消费 */
    private var handled = false

    /** 获取内容；同一事件只返回一次，之后返回 null */
    fun getContentIfNotHandled(): T? {
        if (handled) return null
        handled = true
        return content
    }
}
