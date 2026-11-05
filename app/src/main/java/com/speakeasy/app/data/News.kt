package com.speakeasy.app.data

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.net.URLEncoder
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * [headlinesOnly] sources give a headline list but no readable article page: the reader shows the
 * headline to study and sends the learner to the publisher's site for the full story.
 */
enum class NewsSource(val label: String, val url: String, val publisher: String, val headlinesOnly: Boolean = false) {
    BBC_TOP("BBC Top", "https://feeds.bbci.co.uk/news/rss.xml", "BBC News"),
    BBC_WORLD("BBC World", "https://feeds.bbci.co.uk/news/world/rss.xml", "BBC News"),
    BBC_BUSINESS("BBC Business", "https://feeds.bbci.co.uk/news/business/rss.xml", "BBC News"),
    BBC_TECH("BBC Tech", "https://feeds.bbci.co.uk/news/technology/rss.xml", "BBC News"),
    BBC_HEALTH("BBC Health", "https://feeds.bbci.co.uk/news/health/rss.xml", "BBC News"),
    BBC_SCIENCE("BBC Science", "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml", "BBC News"),
    CNN("CNN", "https://lite.cnn.com/", "CNN"),
    // Reuters has no public RSS and blocks scripted visits, so its headlines come from a Google News search.
    REUTERS("Reuters", googleNews("site:reuters.com"), "Reuters", headlinesOnly = true),
    REUTERS_WORLD("Reuters World", googleNews("site:reuters.com/world"), "Reuters", headlinesOnly = true),
    REUTERS_BUSINESS("Reuters Business", googleNews("site:reuters.com/business"), "Reuters", headlinesOnly = true),
    REUTERS_MARKETS("Reuters Markets", googleNews("site:reuters.com/markets"), "Reuters", headlinesOnly = true),
}

/** Google News RSS for the last day's results of [query]. */
private fun googleNews(query: String) =
    "https://news.google.com/rss/search?q=${URLEncoder.encode("$query when:1d", "UTF-8")}&hl=en-US&gl=US&ceid=US:en"

/** A headline; CNN Lite gives no [summary] or [published] time. */
@Serializable
data class NewsItem(
    val title: String,
    val link: String,
    val source: NewsSource,
    val summary: String = "",
    val published: Long? = null,
    val image: String? = null,
)

@Serializable
data class NewsCache(val items: List<NewsItem>, val fetchedAt: Long)

/** Paragraphs pulled out of an article page; empty when the page layout was not recognised. */
data class Article(val title: String, val paragraphs: List<String>)

/** Pure parsing helpers, kept free of Android calls so they run in unit tests. */
object NewsParser {

    /** Reads RSS `<item>`s; [parser] is injectable because android.util.Xml is not available on the JVM. */
    fun parseRss(xml: String, source: NewsSource, parser: XmlPullParser = Xml.newPullParser()): List<NewsItem> {
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))
        val items = mutableListOf<NewsItem>()
        var fields: MutableMap<String, String>? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (val name = parser.name) {
                    "item" -> fields = mutableMapOf()
                    "media:thumbnail", "thumbnail" -> fields?.let { f ->
                        parser.getAttributeValue(null, "url")?.let { f.putIfAbsent("image", it) }
                    }
                    "title", "description", "link", "pubDate" -> fields?.put(name, parser.nextText().trim())
                }
                XmlPullParser.END_TAG -> if (parser.name == "item") {
                    val f = fields ?: continue
                    val title = clean(f["title"].orEmpty())
                    val link = decodeEntities(f["link"].orEmpty()).trim()
                    if (title.isNotEmpty() && link.isNotEmpty()) items += NewsItem(
                        title = title,
                        link = link,
                        source = source,
                        summary = clean(f["description"].orEmpty()),
                        published = f["pubDate"]?.let(::parseDate),
                        image = f["image"],
                    )
                    fields = null
                }
            }
        }
        return if (source.headlinesOnly) items.map(::headlineOnly).sortedByDescending { it.published ?: 0L } else items
    }

    /**
     * Google News titles end in " - Reuters" and its description only repeats the title (plus the
     * publisher's name), so that summary is dropped; there is no picture either.
     */
    private fun headlineOnly(item: NewsItem): NewsItem {
        val title = item.title.removeSuffix(" - ${item.source.publisher}").trim()
        val summary = item.summary.takeUnless { it.startsWith(title) || it.startsWith(item.title) }.orEmpty()
        return item.copy(title = title, summary = summary, image = null)
    }

    fun parseDate(text: String): Long? =
        runCatching { ZonedDateTime.parse(text.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()

    private val cnnCard = Regex("""<li[^>]*class="card--lite"[^>]*>\s*<a[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)

    /** CNN Lite's front page is a plain list of `<li class="card--lite"><a href="/2026/...">Headline</a>`. */
    fun parseCnnHeadlines(html: String): List<NewsItem> =
        cnnCard.findAll(html).mapNotNull { m ->
            val href = decodeEntities(m.groupValues[1])
            val title = clean(m.groupValues[2])
            if (title.isEmpty()) null
            else NewsItem(title, if (href.startsWith("http")) href else "https://lite.cnn.com$href", NewsSource.CNN)
        }.distinctBy { it.link }.toList()

    private val cnnParagraph = Regex("""<p[^>]*class="[^"]*paragraph--lite[^"]*"[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
    private val bbcBlock = Regex("""<div[^>]*data-component="text-block"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
    private val paragraph = Regex("""<p(?:\s[^>]*)?>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
    private val heading = Regex("""<h1[^>]*>(.*?)</h1>""", RegexOption.DOT_MATCHES_ALL)
    private val scripts = Regex("""<(script|style|noscript)[^>]*>.*?</\1>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    /** Article paragraphs: CNN Lite's markup, then BBC's text blocks, then any long-enough `<p>`. */
    fun extractParagraphs(html: String): List<String> {
        val page = scripts.replace(html, " ")
        val cnn = cnnParagraph.findAll(page).map { clean(it.groupValues[1]) }.filter { it.isNotEmpty() }.toList()
        if (cnn.isNotEmpty()) return cnn
        val bbc = bbcBlock.findAll(page)
            .flatMap { block -> paragraph.findAll(block.groupValues[1]).map { clean(it.groupValues[1]) } }
            .filter { it.isNotEmpty() }.toList()
        if (bbc.isNotEmpty()) return bbc
        return paragraph.findAll(page).map { clean(it.groupValues[1]) }.filter { it.length > 40 }.toList()
    }

    fun extractTitle(html: String): String? = heading.find(html)?.let { clean(it.groupValues[1]) }?.takeIf { it.isNotEmpty() }

    private val tag = Regex("<[^>]+>")
    private val space = Regex("\\s+")

    /** Tags removed, entities decoded, whitespace collapsed. */
    fun clean(html: String): String = decodeEntities(tag.replace(html, " ")).replace(space, " ").trim()
        .replace(" ,", ",").replace(" .", ".")

    private val entity = Regex("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")
    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“", "mdash" to "—", "ndash" to "–",
        "hellip" to "…", "pound" to "£", "euro" to "€", "copy" to "©",
    )

    /** One pass, so "&amp;lt;" becomes "&lt;" rather than "<". */
    fun decodeEntities(text: String): String = entity.replace(text) { m ->
        val code = m.groupValues[1]
        when {
            code.startsWith("#x") || code.startsWith("#X") -> code.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
            code.startsWith("#") -> code.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
            else -> named[code]
        } ?: m.value
    }

    /** "vừa xong", "15 phút trước", "3 giờ trước", "2 ngày trước". */
    fun relativeTime(time: Long, now: Long = System.currentTimeMillis()): String {
        val minutes = ((now - time) / 60_000).coerceAtLeast(0)
        return when {
            minutes < 1 -> "vừa xong"
            minutes < 60 -> "$minutes phút trước"
            minutes < 24 * 60 -> "${minutes / 60} giờ trước"
            else -> "${minutes / (24 * 60)} ngày trước"
        }
    }
}

/** Headlines from BBC, CNN and Reuters, cached per source so the last list shows instantly offline. */
class NewsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("news", Context.MODE_PRIVATE)

    private val http = OkHttpClient.Builder()
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    // Every headline seen this session, so the reader can find its title and summary by link.
    private val known = ConcurrentHashMap<String, NewsItem>()

    // Article text is only kept in memory, never written to disk.
    private val articles = ConcurrentHashMap<String, Article>()

    fun item(link: String): NewsItem? = known[link]

    fun cached(source: NewsSource): NewsCache? =
        prefs.getString(source.name, null)
            ?.let { runCatching { json.decodeFromString(NewsCache.serializer(), it) }.getOrNull() }
            ?.also { c -> c.items.forEach { known[it.link] = it } }

    suspend fun fetch(source: NewsSource): NewsCache = withContext(Dispatchers.IO) {
        val body = get(source.url)
        val items = if (source == NewsSource.CNN) NewsParser.parseCnnHeadlines(body) else NewsParser.parseRss(body, source)
        if (items.isEmpty()) throw IOException("No headlines in ${source.url}")
        val cache = NewsCache(items, System.currentTimeMillis())
        items.forEach { known[it.link] = it }
        prefs.edit().putString(source.name, json.encodeToString(NewsCache.serializer(), cache)).apply()
        cache
    }

    suspend fun article(link: String, fallbackTitle: String): Article = articles[link] ?: withContext(Dispatchers.IO) {
        val html = get(link)
        Article(NewsParser.extractTitle(html) ?: fallbackTitle, NewsParser.extractParagraphs(html))
            .also { if (it.paragraphs.isNotEmpty()) articles[link] = it }
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
        return http.newCall(request).execute().use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code} for $url")
            it.body?.string().orEmpty()
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
