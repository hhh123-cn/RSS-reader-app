package com.b3.rssreader.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.b3.rssreader.data.repository.FeedRepository
import com.b3.rssreader.utils.PrefUtil
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * 周期刷新 Worker —— 每小时自动刷新所有订阅源
 *
 * 与 RssRefreshWorker（单源、一次性）的区别：
 * - 本 Worker 是【周期任务】：WorkManager 保证"大约每 1 小时执行一次"，
 *   即使 App 进程被杀、设备重启也会按计划继续；
 * - 执行时遍历全部订阅源，逐个调用仓库的 refreshFeed（拉取→解析→入库）；
 * - 每个订阅源入库时通过 IGNORE 冲突自动"对比数据库识别新文章"
 *   （rowid != -1 即新增，且新文章带 is_new = true 标记）；
 * - 结果（时间 + 新增总数）写入 PrefUtil，供设置页展示、伴生模块参考。
 *
 * 周期任务注册时机：Application.onCreate（见 B3RssApplication.schedulePeriodicRefresh）。
 * KEEP 策略保证 App 每次重启不会重复注册任务。
 */
class PeriodicRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = FeedRepository.getInstance(applicationContext)

        // first()：取当前订阅源列表快照
        val feeds = repository.observeAll().first()

        // 没有任何订阅源：跳过本轮（下个周期再检查）
        if (feeds.isEmpty()) return Result.success()

        var totalNewCount = 0
        var hasRetryableError = false

        for (feed in feeds) {
            repository.refreshFeed(feed).fold(
                onSuccess = { newCount ->
                    totalNewCount += newCount
                },
                onFailure = {
                    // 单个源失败不中断整体：记下"有可重试错误"，
                    // 其他源继续刷新，最后统一决定重试
                    hasRetryableError = true
                }
            )
        }

        // 记录本轮结果（时间 + 新增文章数）
        PrefUtil.saveLastRefresh(applicationContext, System.currentTimeMillis(), totalNewCount)

        return if (hasRetryableError) {
            // 部分源失败（多为网络问题）→ 按退避策略整轮重试
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        /** 唯一任务名：App 生命周期内只存在这一个周期任务 */
        private const val UNIQUE_WORK_NAME = "periodic_refresh_all"

        /** 刷新间隔：1 小时 */
        private const val REFRESH_INTERVAL_HOURS = 1L

        /**
         * 注册周期刷新任务（幂等：KEEP 策略下重复调用不会重复注册）。
         * 注意：PeriodicWorkRequest 最短间隔 15 分钟，1 小时满足限制。
         *
         * 不设置网络约束：WorkManager 的 CONNECTED 要求网络被系统标记为 VALIDATED，
         * 部分环境（模拟器、受限网络）该状态长期为 false，任务将永不执行。
         * 改为由 OkHttp 请求自行失败并触发 retry 退避，行为更健壮。
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PeriodicRefreshWorker>(
                REFRESH_INTERVAL_HOURS, TimeUnit.HOURS
            )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
