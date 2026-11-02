package com.b3.rssreader.data.remote

import android.util.Xml
import com.b3.rssreader.data.local.entity.Article
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.StringReader
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * RSS 解析结果：
 * - channel 信息：站点名 / 站点描述 / 站点主页（添加订阅源后可用它回填标题）
 * - articles：已映射好的 Article 列表（feedId 已填入，可直接入库）
 */
data class RssParseResult(
    val channelTitle: String?,
    val channelDescription: String?,
    val channelLink: String?,
    val articles: List<Article>
)

/**
 * RSS 2.0 解析工具（XmlPullParser 实现）
 *
 * 为什么用 XmlPullParser？
 * - 它是 Android SDK 内置的"拉模式"解析器（DOM/SAX 之外的选择）：
 *   不用一次性把整棵文档树读进内存（DOM 会），
 *   不用回调满天飞（SAX 会），而是"事件循环 + 状态机"自己掌控节奏，
 *   内存占用小、逻辑直观，是 Android 上解析 XML 的推荐方式。
 *
 * 支持解析的 RSS 2.0 标签：
 *   channel：title / description / link
 *   item   ：title / link / description / guid / pubDate /
 *            author（含 dc:creator）/ content:encoded（全文，可选）
 *
 * 容错策略：
 * - guid 缺失 → 用 link 兜底（保证去重字段恒有值，见 Article 实体注释）
 * - link / title 缺失 → 该条文章跳过（无链接无法阅读）
 * - pubDate 解析失败 → 存 null（列表排序时排最后，不崩溃）
 * - 文档不是 RSS（没有 <rss> 根标签）→ 整体失败
 *
 * 注：本工具只面向 RSS 2.0；Atom 格式（<feed> 根）不在课程设计范围内。
 */
object RssParser {

    /**
     * 解析入口。
     *
     * @param xml    RssHttpClient.fetchRssXml 拿到的原始 XML 字符串
     * @param feedId 所属订阅源 id（Article 外键，解析时直接填入）
     * @return Result<RssParseResult>：失败时携带 RssParseException（中文提示）
     */
    fun parse(xml: String, feedId: Long): Result<RssParseResult> =
        try {
            Result.success(doParse(xml, feedId))
        } catch (e: XmlPullParserException) {
            // XML 格式错误（标签不闭合、非法字符等）
            Result.failure(RssParseException("XML 解析失败：${e.message}", e))
        } catch (e: IOException) {
            // 读取流异常（StringReader 场景几乎不会发生，防御性兜底）
            Result.failure(RssParseException("读取 XML 失败", e))
        }

    /** 实际解析逻辑（异常向上抛，由 parse() 统一翻译） */
    private fun doParse(xml: String, feedId: Long): RssParseResult {
        // Android 提供的 XmlPullParser 工厂（android.util.Xml）
        val parser = Xml.newPullParser()
        // 关闭命名空间处理：标签名直接是 "pubDate"/"content:encoded" 这种原始写法，
        // 我们用 lowercase 统一匹配，兼容大小写混写
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        // ---------- channel 层字段 ----------
        var channelTitle: String? = null
        var channelDescription: String? = null
        var channelLink: String? = null

        // ---------- 当前 item 的字段（遇到 </item> 时组装成 Article）----------
        var itemTitle: String? = null
        var itemLink: String? = null
        var itemDescription: String? = null
        var itemAuthor: String? = null
        var itemGuid: String? = null
        var itemPubDate: String? = null
        var itemContent: String? = null

        var insideChannel = false
        var insideItem = false
        var sawRssRoot = false   // 是否遇到 <rss> 根标签（合法性校验）
        val articles = mutableListOf<Article>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase()) {
                        "rss" -> sawRssRoot = true

                        "channel" -> insideChannel = true

                        "item" -> {
                            insideItem = true
                            // 每个 item 开始前重置所有字段，防止脏数据串到下一条
                            itemTitle = null
                            itemLink = null
                            itemDescription = null
                            itemAuthor = null
                            itemGuid = null
                            itemPubDate = null
                            itemContent = null
                        }

                        // 标题：item 内优先；channel 内只取第一个（RSS 里 channel.title 是站点名）
                        "title" -> when {
                            insideItem -> itemTitle = readTextUntilEnd(parser)
                            insideChannel && channelTitle == null -> channelTitle = readTextUntilEnd(parser)
                        }

                        "link" -> when {
                            insideItem -> itemLink = readTextUntilEnd(parser)
                            insideChannel && channelLink == null -> channelLink = readTextUntilEnd(parser)
                        }

                        // 描述：item 内是文章摘要（常含 HTML，readTextUntilEnd 会剥标签取文本）；
                        // channel 内是站点描述
                        "description" -> when {
                            insideItem -> itemDescription = readTextUntilEnd(parser)
                            insideChannel -> channelDescription = readTextUntilEnd(parser)
                        }

                        // 全文标签（部分源提供），命名空间关闭后原始名即 "content:encoded"
                        "content:encoded" ->
                            if (insideItem) itemContent = readTextUntilEnd(parser)

                        "guid" ->
                            if (insideItem) itemGuid = readTextUntilEnd(parser)

                        "pubdate" ->
                            if (insideItem) itemPubDate = readTextUntilEnd(parser)

                        // 作者：支持 author 与 Dublin Core 的 dc:creator 两种写法
                        "author", "dc:creator" ->
                            if (insideItem) itemAuthor = readTextUntilEnd(parser)
                    }
                }

                XmlPullParser.END_TAG -> {
                    when (parser.name.lowercase()) {
                        // 一条 item 结束 → 组装 Article
                        "item" -> {
                            insideItem = false
                            buildArticle(
                                feedId = feedId,
                                title = itemTitle,
                                link = itemLink,
                                description = itemDescription,
                                author = itemAuthor,
                                guid = itemGuid,
                                pubDate = itemPubDate,
                                content = itemContent
                            )?.let { articles += it }
                        }

                        "channel" -> insideChannel = false
                    }
                }
            }
            event = parser.next()
        }

        // 合法性校验：没有 <rss> 根标签的文档（如 HTML 错误页）直接判失败
        if (!sawRssRoot) {
            throw RssParseException("不是有效的 RSS 文档")
        }

        return RssParseResult(
            channelTitle = channelTitle,
            channelDescription = channelDescription,
            channelLink = channelLink,
            articles = articles
        )
    }

    /**
     * 读取从当前 START_TAG 到其匹配 END_TAG 之间的文本。
     *
     * 为什么不用 parser.nextText()？
     * nextText() 遇到嵌套子标签会直接抛异常——而 RSS 的 description 字段
     * 经常含 HTML（<p>、<b>…），一抛就崩。
     * 这里用"深度计数"手动累积：
     *   - 嵌套 START_TAG → 深度 +1，不采集标签本身
     *   - 匹配 END_TAG → 深度 -1，归零即结束
     *   - TEXT / CDSECT → 追加文本（CDATA 里的 HTML 原样保留）
     * 结果：普通嵌套标签被剥成纯文本；CDATA 内的内容原样保留。
     */
    private fun readTextUntilEnd(parser: XmlPullParser): String {
        val builder = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> builder.append(parser.text)
            }
        }
        return builder.toString().trim()
    }

    /**
     * 组装一条 Article。
     * 返回 null 表示该条无效（title 或 link 缺失），调用方跳过。
     */
    private fun buildArticle(
        feedId: Long,
        title: String?,
        link: String?,
        description: String?,
        author: String?,
        guid: String?,
        pubDate: String?,
        content: String?
    ): Article? {
        // 标题与链接是文章的底线，缺失则整条丢弃
        if (title.isNullOrBlank() || link.isNullOrBlank()) return null

        return Article(
            feedId = feedId,
            guid = guid?.takeIf { it.isNotBlank() } ?: link, // guid 缺失用 link 兜底
            title = title,
            link = link,
            author = author,
            description = description,
            content = content,
            pubDate = parseDate(pubDate)
        )
    }

    /**
     * 把 RSS 的日期文本解析成时间戳（毫秒）。
     * 尝试多种常见格式（RFC 822 为主，兼容 ISO 8601 等），全失败返回 null。
     *
     * 注意：SimpleDateFormat 不是线程安全的，所以这里每次调用都新建实例
     * （解析频率低，性能可接受；关键是避免多线程共用一个实例导致日期错乱）。
     */
    private fun parseDate(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val trimmed = text.trim()
        for (pattern in DATE_PATTERNS) {
            try {
                return SimpleDateFormat(pattern, Locale.US).parse(trimmed)?.time
            } catch (_: ParseException) {
                // 该格式不匹配，尝试下一个
            }
        }
        return null
    }

    /** 支持的日期格式（按常见程度排序） */
    private val DATE_PATTERNS = listOf(
        "EEE, dd MMM yyyy HH:mm:ss zzz", // RFC 822：Wed, 02 Oct 2002 13:00:00 GMT
        "EEE, dd MMM yyyy HH:mm:ss Z",   // RFC 822 变体（+0800 形式时区）
        "dd MMM yyyy HH:mm:ss zzz",      // 无星期的 RFC 822
        "yyyy-MM-dd'T'HH:mm:ssXXX",      // ISO 8601（Atom 风格）
        "yyyy-MM-dd HH:mm:ss"            // 常见数据库格式
    )
}
