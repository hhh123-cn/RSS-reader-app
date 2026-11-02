package com.b3.rssreader.utils

import android.content.Context

/**
 * 轻量键值存储工具（SharedPreferences 封装）
 *
 * 目前用于记录"最近一次周期刷新"的结果：
 * - 上次刷新时间戳
 * - 上次刷新发现的新文章数
 * 设置页/伴生模块对接时可读取展示（后续阶段接入）。
 *
 * 注意：SharedPreferences 是主 App 私有的，伴生模块读不到；
 * 跨 App 共享的数据（文章列表等）走 ContentProvider，不要往这里放。
 */
object PrefUtil {

    private const val FILE_NAME = "rss_reader_prefs"
    private const val KEY_LAST_REFRESH_TIME = "last_refresh_time"
    private const val KEY_LAST_NEW_COUNT = "last_new_count"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** 记录一次刷新结果 */
    fun saveLastRefresh(context: Context, timeMillis: Long, newCount: Int) {
        prefs(context).edit()
            .putLong(KEY_LAST_REFRESH_TIME, timeMillis)
            .putInt(KEY_LAST_NEW_COUNT, newCount)
            .apply() // apply：异步写盘，不阻塞调用线程
    }

    /** 上次刷新时间戳（毫秒），从未刷新过返回 0 */
    fun getLastRefreshTime(context: Context): Long =
        prefs(context).getLong(KEY_LAST_REFRESH_TIME, 0L)

    /** 上次刷新发现的新文章数，从未刷新过返回 0 */
    fun getLastNewCount(context: Context): Int =
        prefs(context).getInt(KEY_LAST_NEW_COUNT, 0)
}
