package com.b3.rssreader.data.remote

import java.io.IOException

/**
 * 网络请求业务异常
 *
 * 与系统 IO 异常的区别：message 是"可以直接展示给用户的中文提示"，
 * 例如"请求超时，请稍后重试"。上层（Repository/ViewModel）捕获后
 * 直接把 message 透传到界面提示，不需要再翻译。
 */
class NetworkException(message: String, cause: Throwable? = null) : IOException(message, cause)
