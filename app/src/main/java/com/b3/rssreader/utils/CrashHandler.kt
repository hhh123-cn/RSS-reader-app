package com.b3.rssreader.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import com.b3.rssreader.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局未捕获异常处理器 —— 崩溃兜底
 *
 * 原理：Thread.setDefaultUncaughtExceptionHandler 拦截所有
 * 没有 try-catch 的异常（主线程与后台线程都算），做最后兜底：
 *
 * 1. debug 构建：不拦截，交给系统默认处理——开发期需要看到原生崩溃信息
 * 2. release 构建：
 *    - 把异常堆栈写入应用私有目录的日志文件（课后可 pull 出来排查）
 *    - 重启 App（简单兜底：让用户回到可用状态，而不是停在崩溃白屏）
 *
 * 说明：课程设计演示"全局异常捕获"的落地方式；
 * 正式项目应接入崩溃上报 SDK（Firebase Crashlytics / Bugly），
 * 而不是静默重启掩盖问题。
 */
class CrashHandler private constructor(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // 1. 写崩溃日志（Logcat + 文件双份）
            logCrash(thread, throwable)

            // 2. 重启 App：直接拉起桌面入口 Activity
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // 兜底逻辑自身失败：交给系统默认处理（正常崩溃流程）
            defaultHandler?.uncaughtException(thread, throwable)
            return
        } finally {
            // 无论如何都要杀掉当前进程，否则界面会停留在崩溃状态
            Process.killProcess(Process.myPid())
            System.exit(1)
        }
    }

    /** 记录崩溃日志：Logcat + 应用私有目录文件 */
    private fun logCrash(thread: Thread, throwable: Throwable) {
        val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val logText = buildString {
            appendLine("========== 崩溃时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())} ==========")
            appendLine("线程：${thread.name}")
            appendLine("系统：Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine(stackTrace)
        }

        Log.e(TAG, logText)

        runCatching {
            val dir = File(context.filesDir, "logs")
            dir.mkdirs()
            File(dir, "crash.log").appendText(logText)
        }
    }

    companion object {
        private const val TAG = "B3RssReader"

        /** 安装全局异常处理器（Application.onCreate 调用，只需一次） */
        fun install(context: Context) {
            if (BuildConfig.DEBUG) {
                // debug 构建保留系统默认崩溃行为，方便开发调试
                return
            }
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(
                CrashHandler(context.applicationContext, defaultHandler)
            )
        }
    }
}
