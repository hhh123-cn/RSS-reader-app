package com.b3.rssreader.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * RSS 网络请求工具（OkHttp 封装）
 *
 * 职责（本阶段只做网络层，不解析）：
 * - GET 请求一个 RSS 地址
 * - 返回服务器响应的**原始 XML 字符串**
 * - 把各种网络异常翻译成携带中文提示的 Result.failure，绝不向上抛裸异常
 *
 * 设计说明：
 * 1. object 单例 + lazy：OkHttpClient 内部有连接池和线程池，
 *    创建成本高，整个 App 只建一个（官方强烈推荐复用）。
 * 2. Result<String> 返回值：成功携带 XML 文本，失败携带 NetworkException。
 *    调用方（阶段 3 的 Repository）用 onSuccess/onFailure 分支处理，
 *    不需要 try-catch，代码干净。
 * 3. withContext(Dispatchers.IO)：execute() 是同步阻塞调用，
 *    必须切到 IO 线程池执行，否则阻塞主线程导致 ANR。
 */
object RssHttpClient {

    /** 全局唯一 OkHttp 客户端（懒加载单例） */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // 连接超时：建立 TCP 连接的最长等待时间
            .connectTimeout(10, TimeUnit.SECONDS)
            // 读取超时：发完请求后，等服务器回数据的最长等待时间
            .readTimeout(15, TimeUnit.SECONDS)
            // 写入超时：向服务器写数据的最长等待时间（GET 无请求体，保险起见也设）
            .writeTimeout(15, TimeUnit.SECONDS)
            // 日志拦截器：打印每个请求的 URL / 状态码 / 耗时到 Logcat，
            // 方便课程设计调试网络问题
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    /**
     * 拉取 RSS 源的原始 XML 文本。
     *
     * @param url RSS 地址（如 https://sspai.com/feed）
     * @return Result<String>：成功 = XML 字符串；失败 = NetworkException（中文提示）
     */
    suspend fun fetchRssXml(url: String): Result<String> = withContext(Dispatchers.IO) {
        // 构造 GET 请求。部分服务器会拒绝没有 User-Agent 的请求，
        // 这里带上自定义 UA 模拟正常客户端
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "B3RssReader/1.0 (Android)")
            .get()
            .build()

        try {
            // execute() 同步执行；use{} 确保 response 用完自动关闭（释放连接回池）
            client.newCall(request).execute().use { response ->
                // 非 2xx 状态码（404、500 等）：不是有效的 RSS 响应
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        NetworkException("服务器返回错误：HTTP ${response.code}")
                    )
                }
                // 读取响应体为字符串（body 可能为 null，防御性判断）
                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    return@withContext Result.failure(NetworkException("服务器返回内容为空"))
                }
                Result.success(body)
            }
        } catch (e: UnknownHostException) {
            // 域名解析失败：通常是没网 / 域名打错
            Result.failure(NetworkException("网络连接失败，请检查网络或地址是否正确", e))
        } catch (e: SocketTimeoutException) {
            // 超时：网络慢或服务器无响应
            Result.failure(NetworkException("请求超时，请稍后重试", e))
        } catch (e: IOException) {
            // 其余 IO 异常兜底（注意 catch 顺序：子类必须在前）
            Result.failure(NetworkException("网络请求失败：${e.message}", e))
        }
    }
}
