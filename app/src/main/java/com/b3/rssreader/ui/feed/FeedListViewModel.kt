package com.b3.rssreader.ui.feed

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.b3.rssreader.R
import com.b3.rssreader.background.RssRefreshWorker
import com.b3.rssreader.data.local.entity.Feed
import com.b3.rssreader.data.repository.FeedRepository
import com.b3.rssreader.ui.common.Event
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 订阅源列表 ViewModel —— MVVM 中的数据持有者 + 业务逻辑入口
 *
 * 数据流向（单向数据流）：
 *   Room 数据库 → DAO(Flow) → Repository(Flow) → ViewModel(asLiveData) → UI(observe)
 *   用户操作 → ViewModel 方法 → Repository(suspend) → DAO → Room
 *   UI 永远不直接访问数据库。
 */
class FeedListViewModel(
    private val application: Application,
    private val repository: FeedRepository
) : ViewModel() {

    /**
     * 订阅源列表。Room 里数据一变（增删改），
     * 这个 LiveData 自动把新列表推给界面，无需手动刷新。
     */
    val feeds: LiveData<List<Feed>> = repository.observeAll().asLiveData()

    // ------------------------------------------------------------
    // 一次性提示消息（添加成功 / 已存在 / 已删除等）
    // 用 Event 包装：旋转屏幕重建页面后不会重复弹提示
    // ------------------------------------------------------------
    private val _message = MutableLiveData<Event<String>>()
    val message: LiveData<Event<String>> = _message

    /**
     * 添加订阅源。
     *
     * viewModelScope.launch：ViewModel 的专属协程作用域，
     * 页面关闭时自动取消未完成的协程，不会泄漏。
     * 线程切换由 Repository/DAO 内部完成（withContext(IO)/Room 自带线程池），
     * 这里只需 suspend 调用，主线程绝不会被阻塞。
     */
    fun addFeed(title: String, url: String) {
        viewModelScope.launch {
            repository.addFeed(title = title.ifBlank { url }, url = url)
                .onSuccess { _message.postValue(Event(application.getString(R.string.msg_feed_added))) }
                .onFailure { e ->
                    // DuplicateFeedException 携带友好文案（"该订阅源已存在: xxx"）
                    _message.postValue(Event(e.message ?: application.getString(R.string.msg_feed_error)))
                }
        }
    }

    /** 编辑保存：把修改后的 Feed 写回数据库 */
    fun updateFeed(feed: Feed) {
        viewModelScope.launch {
            repository.updateFeed(feed)
            _message.postValue(Event(application.getString(R.string.msg_feed_updated)))
        }
    }

    /**
     * 删除订阅源。
     * 数据库外键 CASCADE 会自动级联删除其下所有文章（见 AppDatabase 注释），
     * 这里无需手动清理文章。
     */
    fun deleteFeed(feed: Feed) {
        viewModelScope.launch {
            repository.deleteFeed(feed)
            _message.postValue(Event(application.getString(R.string.msg_feed_deleted)))
        }
    }

    /**
     * 刷新全部订阅源（工具栏刷新按钮）：
     * 逐个提交后台刷新任务，由 WorkManager 调度执行。
     * 文章入库后，文章列表页的 Room Flow 会自动刷新（响应式联动），
     * 无需等待任务完成回调。
     */
    fun refreshAll() {
        viewModelScope.launch {
            // first()：只取当前列表快照一次（observeAll 是 Flow）
            val feeds = repository.observeAll().first()
            if (feeds.isEmpty()) {
                _message.postValue(Event(application.getString(R.string.msg_refresh_no_feeds)))
                return@launch
            }
            feeds.forEach { feed ->
                RssRefreshWorker.enqueueOneShot(application, feed.id)
            }
            _message.postValue(
                Event(application.getString(R.string.msg_refresh_submitted, feeds.size))
            )
        }
    }

    /** 刷新单个订阅源（条目 ⋮ 菜单） */
    fun refreshFeed(feed: Feed) {
        RssRefreshWorker.enqueueOneShot(application, feed.id)
        _message.postValue(
            Event(application.getString(R.string.msg_refresh_one_submitted, feed.title))
        )
    }

    companion object {

        /**
         * ViewModel 工厂：
         * FeedListViewModel 构造函数需要参数（Application + Repository），
         * 系统无法自动创建，必须提供 Factory。
         *
         * viewModelFactory { initializer { ... } } 是 Lifecycle 2.5+ 官方 DSL：
         * 通过 APPLICATION_KEY 取出 Application，再去拿仓库单例完成构造。
         */
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                FeedListViewModel(application, FeedRepository.getInstance(application))
            }
        }
    }
}
