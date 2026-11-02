package com.b3.rssreader.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.b3.rssreader.data.remote.NetworkException
import com.b3.rssreader.data.repository.FeedRepository

/**
 * RSS 刷新 Worker —— 后台拉取单个订阅源的最新文章并入库
 *
 * 为什么用 WorkManager 而不是开个线程/服务？
 * - WorkManager 是官方推荐的后台任务方案：任务入库持久化，
 *   即使 App 进程被杀、设备重启，任务也不会丢，系统会在合适时机补跑；
 * - 自带约束（如"必须有网络"）、重试（指数退避）、去重（唯一任务名）能力；
 * - CoroutineWorker 直接提供 suspend doWork()，与全项目协程风格无缝衔接。
 *
 * 结果语义（WorkManager 协议）：
 * - Result.success()：任务完成
 * - Result.retry()  ：可恢复失败（如断网），WorkManager 按退避策略自动重试
 * - Result.failure()：不可恢复失败（如 XML 根本不是 RSS），放弃重试
 */
class RssRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 从输入数据取订阅源 id（无则任务无效，直接失败）
        val feedId = inputData.getLong(KEY_FEED_ID, -1L)
        if (feedId == -1L) return Result.failure()

        val repository = FeedRepository.getInstance(applicationContext)

        // 订阅源可能已被用户删除（数据库里查不到）→ 任务无意义，直接完成
        val feed = repository.getById(feedId) ?: return Result.success()

        // 拉取 → 解析 → 入库（新增文章数在成功分支里，这里只需要知道成败）
        return repository.refreshFeed(feed).fold(
            onSuccess = { Result.success() },
            onFailure = { e ->
                when (e) {
                    // 网络类异常（断网/超时/5xx）是暂时的 → 重试
                    is NetworkException -> Result.retry()
                    // 解析失败说明数据源内容有问题，重试无意义 → 失败
                    else -> Result.failure()
                }
            }
        )
    }

    companion object {
        /** 输入数据 key：要刷新的订阅源 id */
        const val KEY_FEED_ID = "feed_id"

        /** 唯一任务名前缀：同一订阅源的任务串起来去重 */
        private const val UNIQUE_WORK_PREFIX = "refresh_feed_"

        /**
         * 提交一次单源刷新任务（手动刷新与后续周期任务共用入口）。
         *
         * enqueueUniqueWork + REPLACE：用户连点两次刷新，
         * 旧的未完成任务会被替换，不会排队重复执行。
         *
         * 网络约束：无网络时任务自动延后，等网络恢复再跑，
         * 避免"明知必失败还去请求"的浪费。
         */
        fun enqueueOneShot(context: Context, feedId: Long) {
            val request = OneTimeWorkRequestBuilder<RssRefreshWorker>()
                .setInputData(workDataOf(KEY_FEED_ID to feedId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                // 指数退避重试：失败后 10 秒、20 秒、40 秒……逐步拉长间隔
                // （WorkManager 2.10 起退避时间用 Duration 表达；java.time 在 API 26+ 可用）
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    java.time.Duration.ofSeconds(10)
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_PREFIX + feedId,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
