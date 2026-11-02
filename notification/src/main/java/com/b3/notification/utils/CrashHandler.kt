package com.b3.notification.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import com.b3.notification.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局未捕获异常处理器（伴生模块版）
 *
 * 与主 App CrashHandler 逻辑一致：
 * - debug 构建：不拦截，保留原生崩溃信息便于开发
 * - release 构建：写崩溃日志文件 + 重启 App 兜底
 * 正式项目应接入崩溃上报 SDK，而不是静默重启掩盖问题。
 */
class CrashHandler private constructor(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            logCrash(thread, throwable)

            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            defaultHandler?.uncaughtException(thread, throwable)
            return
        } finally {
            Process.killProcess(Process.myPid())
            System.exit(1)
        }
    }

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
        private const val TAG = "P3Notification"

        /** 安装全局异常处理器（Application.onCreate 调用，只需一次） */
        fun install(context: Context) {
            if (BuildConfig.DEBUG) return
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(
                CrashHandler(context.applicationContext, defaultHandler)
            )
        }
    }
}
