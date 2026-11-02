package com.b3.rssreader.data.remote

/**
 * RSS 解析业务异常
 *
 * 与 NetworkException 同理：message 是可直接展示给用户的中文提示
 * （如"不是有效的 RSS 文档"），上层捕获后直接透传到界面。
 */
class RssParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
