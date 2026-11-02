package com.b3.notification

import android.app.Application
import com.b3.notification.background.NewArticleCheckWorker
import com.b3.notification.utils.CrashHandler

/**
 * P3 通知提醒中心 —— 应用入口
 *
 * 职责：
 * 1. 注册"新文章检测"周期任务（每 15 分钟检测主 App 的新文章并推送通知）
 * 2. 安装全局异常处理器（release 构建崩溃兜底：日志 + 重启）
 *
 * manifest 的 application 标签需指向本类（android:name=".P3Application"）。
 */
class P3Application : Application() {

    override fun onCreate() {
        super.onCreate()

        // 注册周期检测任务（KEEP 幂等，重启不会重复注册）
        NewArticleCheckWorker.schedule(this)

        // 全局异常捕获兜底（debug 构建下不拦截，保留原生崩溃信息）
        CrashHandler.install(this)
    }
}
