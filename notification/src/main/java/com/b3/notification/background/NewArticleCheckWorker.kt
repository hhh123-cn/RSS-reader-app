package com.b3.notification.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.b3.notification.data.NotificationFeedRepository
import com.b3.notification.notify.NotificationHelper
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * 新文章检测 Worker —— P3 伴生模块的核心后台任务（必做功能）
 *
 * 完整链路（两个 App 协同）：
 *   主 App 的周期刷新任务把新文章入库并打上 is_new = 1 标记
 *     → 本 Worker 周期（15 分钟）跨进程查询 is_new = 1 的文章
 *     → 按"订阅源通知开关"过滤（设置页关闭的源不推送）
 *     → 逐条推送通知（点击跳详情 / 快捷标已读）
 *     → 清除 is_new 标记，防止下轮重复推送
 *
 * 说明：
 * - 检测的是本地 Provider 数据，不依赖网络，因此不加网络约束
 * - 通知权限被拒时直接跳过本轮（设置页已有降级提示），不崩溃
 * - 主 App 未安装时查询失败 → 静默跳过（下轮再试）
 */
class NewArticleCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 通知权限被拒：跳过本轮（无权限发了也没用）
        if (!NotificationHelper.hasNotificationPermission(applicationContext)) {
            return Result.success()
        }

        val repository = NotificationFeedRepository(applicationContext)

        // 1. 拉取订阅源，筛出"开启通知"的源（id → 源名 映射）
        val enabledFeedTitles = mutableMapOf<Long, String>()
        runCatching {
            repository.fetchFeeds().forEach { feed ->
                if (feed.notificationEnabled) {
                    enabledFeedTitles[feed.id] = feed.title
                }
            }
        }.getOrElse { return Result.success() } // 主 App 未安装：静默跳过

        // 没有任何开启通知的源：跳过
        if (enabledFeedTitles.isEmpty()) return Result.success()

        // 2. 查询所有"新增文章"（is_new = 1）
        val newArticles = runCatching { repository.fetchNewArticles() }
            .getOrElse { return Result.success() }

        // 3. 逐条处理
        for (article in newArticles) {
            // 该文章所属源已开启通知 → 推送
            enabledFeedTitles[article.feedId]?.let { feedTitle ->
                NotificationHelper.showNewArticleNotification(
                    context = applicationContext,
                    articleId = article.id,
                    feedTitle = feedTitle,
                    articleTitle = article.title
                )
            }
            // 本轮检测过的文章一律清掉"新增"标记：
            // 无论是否推送（源关闭的不推），都视为"已处理"，
            // 否则用户以后打开开关会被历史文章通知淹没
            runCatching { repository.markArticleNotNew(article.id) }
        }

        return Result.success()
    }

    companion object {
        /** 唯一任务名（周期任务） */
        private const val UNIQUE_WORK_NAME = "periodic_new_article_check"

        /** 一次性检测任务名（手动"立即检测"） */
        private const val UNIQUE_ONESHOT_NAME = "oneshot_new_article_check"

        /** 检测间隔：15 分钟（PeriodicWorkRequest 允许的最小间隔） */
        private const val CHECK_INTERVAL_MINUTES = 15L

        /** 注册周期检测任务（KEEP 幂等，Application.onCreate 调用） */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NewArticleCheckWorker>(
                CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /**
         * 立即执行一次检测（设置页"立即检测"按钮）。
         * 周期任务首次执行要等满一个周期（15 分钟），
         * 手动入口让用户/答辩演示可以随时触发，不用等。
         */
        fun enqueueOneShot(context: Context) {
            val request = OneTimeWorkRequestBuilder<NewArticleCheckWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONESHOT_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
