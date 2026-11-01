package com.b3.rssreader.ui.feed

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.b3.rssreader.data.local.entity.Feed
import com.b3.rssreader.data.repository.FeedRepository

/**
 * 订阅源列表 ViewModel —— MVVM 中的数据持有者
 *
 * 数据流向（单向数据流）：
 *   Room 数据库 → DAO(Flow) → Repository(Flow) → ViewModel(asLiveData) → UI(observe)
 *   UI 永远不直接访问数据库，只能通过 ViewModel 拿数据、发指令。
 *
 * 为什么把 Flow 转成 LiveData？
 * - Flow 冷流需要手动 collect 和取消；LiveData 感知生命周期，
 *   页面销毁自动停止推送，不怕内存泄漏；
 * - LiveData 在"旋转屏幕"等重建场景会把最新数据立刻交给新页面，无空窗。
 */
class FeedListViewModel(private val repository: FeedRepository) : ViewModel() {

    /**
     * 订阅源列表。Room 里数据一变（增删改），
     * 这个 LiveData 自动把新列表推给界面，无需手动刷新。
     */
    val feeds: LiveData<List<Feed>> = repository.observeAll().asLiveData()

    companion object {

        /**
         * ViewModel 工厂：
         * FeedListViewModel 的构造函数需要参数（Repository），
         * 系统无法自动创建它，必须提供 Factory。
         *
         * viewModelFactory { initializer { ... } } 是 Lifecycle 2.5+ 的官方 DSL：
         * initializer 块里通过 APPLICATION_KEY 取出 Application 上下文，
         * 再去拿仓库单例，构造 ViewModel。
         */
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                FeedListViewModel(FeedRepository.getInstance(application))
            }
        }
    }
}
