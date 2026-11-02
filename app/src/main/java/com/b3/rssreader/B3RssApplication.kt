package com.b3.rssreader

import android.app.Application
import com.b3.rssreader.background.PeriodicRefreshWorker
import com.b3.rssreader.utils.CrashHandler

/**
 * 应用入口（Application）
 *
 * 职责：全局一次性初始化。
 * 1. 注册"每小时周期刷新"任务（KEEP 幂等，重启不会重复注册）
 * 2. 安装全局异常处理器（release 构建崩溃兜底：日志 + 重启）
 *
 * manifest 的 application 标签需指向本类（android:name=".B3RssApplication"）。
 */
class B3RssApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 注册周期后台刷新：每 1 小时自动拉取所有订阅源的新文章
        PeriodicRefreshWorker.schedule(this)

        // 全局异常捕获兜底（debug 构建下不拦截，保留原生崩溃信息）
        CrashHandler.install(this)
    }
}
