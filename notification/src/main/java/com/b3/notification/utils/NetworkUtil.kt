package com.b3.notification.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * 网络状态工具
 *
 * 说明：INTERNET 权限是"安装时权限"（normal 级别），
 * 在 manifest 声明即生效，系统不会弹运行时权限框，因此不存在"动态申请"一说。
 * 真正有意义的是【运行时检测网络是否可用】：
 * 无网络时后台检测/发送通知没有意义，UI 上给出降级提示。
 */
object NetworkUtil {

    /**
     * 当前是否有可用的互联网连接。
     * 用 NetworkCapabilities（API 23+）判断，比过时的 NetworkInfo 更可靠。
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
