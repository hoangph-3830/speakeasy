package com.speakeasy.app.data

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * A section of a publisher's news. Sources are grouped by [brand] (the chip in the first row) and
 * named by a short section [label] (the chip in the second row); [publisher] heads the reader.
 * [headlinesOnly] sources give a headline list but no readable article page: the reader shows the
 * headline to study and sends the learner to the publisher's site for the full story.
 * The enum names are cache keys in SharedPreferences, so existing ones must not be renamed.
 */
enum class NewsSource(
    val brand: String,
    val label: String,
    val url: String,
    val publisher: String = brand,
    val headlinesOnly: Boolean = false,
) {
    BBC_TOP("BBC", "Nổi bật", "https://feeds.bbci.co.uk/news/rss.xml", "BBC News"),
    BBC_WORLD("BBC", "Thế giới", "https://feeds.bbci.co.uk/news/world/rss.xml", "BBC News"),
    BBC_BUSINESS("BBC", "Kinh doanh", "https://feeds.bbci.co.uk/news/business/rss.xml", "BBC News"),
    BBC_TECH("BBC", "Công nghệ", "https://feeds.bbci.co.uk/news/technology/rss.xml", "BBC News"),
    BBC_HEALTH("BBC", "Sức khỏe", "https://feeds.bbci.co.uk/news/health/rss.xml", "BBC News"),
    BBC_SCIENCE("BBC", "Khoa học", "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml", "BBC News"),
    CNN("CNN", "Nổi bật", "https://lite.cnn.com/"),
    // Reuters has no public RSS and blocks scripted visits, so its headlines come from a Google News search.
    REUTERS("Reuters", "Nổi bật", googleNews("site:reuters.com"), headlinesOnly = true),
    REUTERS_WORLD("Reuters", "Thế giới", googleNews("site:reuters.com/world"), headlinesOnly = true),
    REUTERS_BUSINESS("Reuters", "Kinh doanh", googleNews("site:reuters.com/business"), headlinesOnly = true),
    REUTERS_MARKETS("Reuters", "Thị trường", googleNews("site:reuters.com/markets"), headlinesOnly = true),
    GUARDIAN_WORLD("The Guardian", "Thế giới", "https://www.theguardian.com/world/rss"),
    GUARDIAN_BUSINESS("The Guardian", "Kinh doanh", "https://www.theguardian.com/uk/business/rss"),
    GUARDIAN_TECH("The Guardian", "Công nghệ", "https://www.theguardian.com/uk/technology/rss"),
    GUARDIAN_ENVIRONMENT("The Guardian", "Môi trường", "https://www.theguardian.com/uk/environment/rss"),
    GUARDIAN_SCIENCE("The Guardian", "Khoa học", "https://www.theguardian.com/science/rss"),
    NPR_TOP("NPR", "Nổi bật", "https://feeds.npr.org/1001/rss.xml"),
    NPR_WORLD("NPR", "Thế giới", "https://feeds.npr.org/1004/rss.xml"),
    NPR_BUSINESS("NPR", "Kinh doanh", "https://feeds.npr.org/1006/rss.xml"),
    NPR_TECH("NPR", "Công nghệ", "https://feeds.npr.org/1019/rss.xml"),
    NPR_HEALTH("NPR", "Sức khỏe", "https://feeds.npr.org/1128/rss.xml"),
    ALJAZEERA("Al Jazeera", "Nổi bật", "https://www.aljazeera.com/xml/rss/all.xml"),
    ABC_TOP("ABC News", "Nổi bật", "https://www.abc.net.au/news/feed/45910/rss.xml"),
    ABC_LATEST("ABC News", "Mới nhất", "https://www.abc.net.au/news/feed/51120/rss.xml"),
    ABC_BUSINESS("ABC News", "Kinh doanh", "https://www.abc.net.au/news/feed/51892/rss.xml"),
    // An Atom feed whose entries carry the whole article, so its stories open without fetching the page.
    CONVERSATION("The Conversation", "Nổi bật", "https://theconversation.com/global/articles.atom"),
    ;

    /** The brand's sections, in display order. */
    val sections: List<NewsSource> get() = entries.filter { it.brand == brand }

    /** "BBC · Thế giới", or just the brand when it has a single section. */
    val tag: String get() = if (sections.size > 1) "$brand · $label" else brand

    companion object {
        /** Brands in display order. */
        val brands: List<String> = entries.map { it.brand }.distinct()
    }
}

/** Google News RSS for the last 3 hours of [query]; over a whole day it ranks by relevance, not time. */
private fun googleNews(query: String) =
    "https://news.google.com/rss/search?q=${URLEncoder.encode("$query when:3h", "UTF-8")}&hl=en-US&gl=US&ceid=US:en"

/**
 * A headline; CNN Lite gives no [summary] or [published] time. [content] is the full article HTML
 * some feeds (Atom `<content>`) carry; it is never written to disk.
 */
@Serializable
data class NewsItem(
    val title: String,
    val link: String,
    val source: NewsSource,
    val summary: String = "",
    val published: Long? = null,
    val image: String? = null,
    @Transient val content: String = "",
)

@Serializable
data class NewsCache(val items: List<NewsItem>, val fetchedAt: Long)

/** Paragraphs pulled out of an article page; empty when the page layout was not recognised. */
data class Article(val title: String, val paragraphs: List<String>)

/** Pure parsing helpers, kept free of Android calls so they run in unit tests. */
object NewsParser {

    /**
     * Reads RSS `<item>`s and Atom `<entry>`s; [parser] is injectable because android.util.Xml is
     * not available on the JVM.
     */
    fun parseRss(xml: String, source: NewsSource, parser: XmlPullParser = Xml.newPullParser()): List<NewsItem> {
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))
        val items = mutableListOf<NewsItem>()
        var fields: MutableMap<String, String>? = null
        var imageWidth = -1
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (val name = parser.name) {
                    "item", "entry" -> { fields = mutableMapOf(); imageWidth = -1 }
                    "media:thumbnail", "thumbnail" -> fields?.let { f ->
                        parser.getAttributeValue(null, "url")?.let { f.putIfAbsent("image", it) }
                    }
                    // The Guardian lists several sizes of one picture and no thumbnail: keep the widest.
                    "media:content" -> fields?.let { f ->
                        val url = parser.getAttributeValue(null, "url")
                        val width = parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 0
                        if (url != null && width > imageWidth) { f["mediaContent"] = url; imageWidth = width }
                    }
                    // Atom: <link rel="alternate" href="…"/>; RSS: <link>…</link>.
                    "link" -> fields?.let { f ->
                        val href = parser.getAttributeValue(null, "href")
                        val rel = parser.getAttributeValue(null, "rel")
                        if (href == null) f["link"] = parser.nextText().trim()
                        else if (rel == null || rel == "alternate") f.putIfAbsent("link", href)
                    }
                    "summary" -> fields?.put("description", parser.nextText().trim())
                    "content" -> fields?.let { f ->
                        if (parser.getAttributeValue(null, "type") != "xhtml") f["content"] = parser.nextText().trim()
                    }
                    "published", "updated" -> fields?.let { f ->
                        val date = parser.nextText().trim()
                        if (name == "published") f["pubDate"] = date else f.putIfAbsent("pubDate", date)
                    }
                    "title", "description", "pubDate" -> fields?.put(name, parser.nextText().trim())
                }
                XmlPullParser.END_TAG -> if (parser.name == "item" || parser.name == "entry") {
                    val f = fields ?: continue
                    val title = clean(f["title"].orEmpty())
                    val link = decodeEntities(f["link"].orEmpty()).trim()
                    if (title.isNotEmpty() && link.isNotEmpty() && !isMediaOnly(link)) items += NewsItem(
                        title = title,
                        link = link,
                        source = source,
                        summary = summarize(f["description"].orEmpty()),
                        published = f["pubDate"]?.let(::parseDate),
                        image = f["image"] ?: f["mediaContent"],
                        content = f["content"].orEmpty(),
                    )
                    fields = null
                }
            }
        }
        return if (source.headlinesOnly) items.map(::headlineOnly).sortedByDescending { it.published ?: 0L } else items
    }

    /** Video and photo-gallery pages (Al Jazeera) have no article text to read. */
    private fun isMediaOnly(link: String) = "/video/" in link || "/gallery/" in link

    private const val SUMMARY_CHARS = 300
    private val paragraphBreak = Regex("</p>|<br\\s*/?>", RegexOption.IGNORE_CASE)

    /**
     * Plain text of a feed description. The Guardian's is several HTML paragraphs ending in a
     * "Continue reading…" link, so it is cut at a word boundary to about [SUMMARY_CHARS] characters.
     */
    fun summarize(description: String): String {
        val text = clean(paragraphBreak.replace(description, " "))
            .removeSuffix("Continue reading...").removeSuffix("Continue reading…").trim()
        if (text.length <= SUMMARY_CHARS) return text
        val cut = text.lastIndexOf(' ', SUMMARY_CHARS).takeIf { it > SUMMARY_CHARS / 2 } ?: SUMMARY_CHARS
        return text.take(cut).trimEnd(' ', ',', ';', ':') + "…"
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

    /** RSS dates (RFC 1123) and Atom dates (ISO-8601). */
    fun parseDate(text: String): Long? {
        val t = text.trim()
        return runCatching { ZonedDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(t).toInstant().toEpochMilli() }.getOrNull()
    }

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
    private val abcParagraph = Regex("""<p[^>]*class="[^"]*paragraph_paragraph[^"]*"[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
    private val heading = Regex("""<h1[^>]*>(.*?)</h1>""", RegexOption.DOT_MATCHES_ALL)
    private val scripts = Regex("""<(script|style|noscript)[^>]*>.*?</\1>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    // Photo captions and asides are not part of the story.
    private val captions = Regex("""<(figure|figcaption|aside)\b[^>]*>.*?</\1>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val nprStory = Regex("""<div[^>]*\bid="storytext"[^>]*>""")
    private val wysiwyg = Regex("""<div[^>]*\bclass="[^"]*\bwysiwyg\b[^"]*"[^>]*>""")
    private val articleTag = Regex("""<article\b[^>]*>""", RegexOption.IGNORE_CASE)

    /**
     * Article paragraphs, from the first layout that matches: CNN Lite's markup, BBC's text blocks,
     * NPR's `#storytext`, Al Jazeera's `.wysiwyg`, ABC's `paragraph_paragraph` classes, the page's
     * `<article>` (The Guardian), then any long-enough `<p>`. Captions, bylines and promos are skipped.
     */
    fun extractParagraphs(html: String): List<String> {
        val page = captions.replace(scripts.replace(html, " "), " ")
        fun texts(matches: Sequence<MatchResult>) = matches.map { clean(it.groupValues[1]) }.filter(::isStoryText).toList()
        fun within(open: Regex, tag: String) = inner(page, open, tag)?.let { texts(paragraph.findAll(it)) }.orEmpty()
        return texts(cnnParagraph.findAll(page))
            .ifEmpty { texts(bbcBlock.findAll(page).flatMap { paragraph.findAll(it.groupValues[1]) }) }
            .ifEmpty { within(nprStory, "div") }
            .ifEmpty { within(wysiwyg, "div") }
            .ifEmpty { texts(abcParagraph.findAll(page)) }
            .ifEmpty { within(articleTag, "article") }
            .ifEmpty { texts(paragraph.findAll(page)).filter { it.length > 40 } }
    }

    /** The HTML inside the first element opened by [open], up to its matching `</tag>`. */
    private fun inner(html: String, open: Regex, tag: String): String? {
        val start = open.find(html) ?: return null
        val from = start.range.last + 1
        var depth = 1
        for (m in Regex("<(/?)$tag\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html, from)) {
            depth += if (m.groupValues[1].isEmpty()) 1 else -1
            if (depth == 0) return html.substring(from, m.range.first)
        }
        return html.substring(from)
    }

    private val byline = Regex("""^(By|Written by|Reporting by|Photograph:|Photo:)\s""", RegexOption.IGNORE_CASE)
    private val promo = Regex("""^(Sign up|Subscribe)\b|\bnewsletters?\b""", RegexOption.IGNORE_CASE)
    private val sentenceEnd = Regex("""[.!?…]["”’')]*$""")

    /** Donation asks at the end of reader-funded newsrooms (The Guardian, NPR), however long. */
    private val donation = Regex(
        """fending off legal risks|reader[- ]funded|(support|fund|power)(s|ing)? (our|independent|this) journalism|make a (contribution|donation)|become a (supporter|member)""",
        RegexOption.IGNORE_CASE,
    )

    /** False for bylines, newsletter promos, donation asks, "Advertisement" and short fragments that are not sentences. */
    fun isStoryText(text: String): Boolean = when {
        text.isEmpty() -> false
        text.equals("Advertisement", ignoreCase = true) -> false
        donation.containsMatchIn(text) -> false
        byline.containsMatchIn(text) && text.length < 120 -> false
        promo.containsMatchIn(text) && text.length < 200 -> false
        text.length < 40 && !sentenceEnd.containsMatchIn(text) -> false
        else -> true
    }

    /** Paragraphs of the article HTML a feed carries in full (The Conversation's Atom `<content>`). */
    fun contentParagraphs(html: String): List<String> =
        paragraph.findAll(captions.replace(scripts.replace(html, " "), " "))
            .map { clean(it.groupValues[1]) }.filter(::isStoryText).toList()

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

/** Headlines from every [NewsSource], cached per source so the last list shows instantly offline. */
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

    /** The source the news tab showed last. */
    var lastSource: NewsSource
        get() = prefs.getString(LAST_SOURCE, null)?.let { n -> NewsSource.entries.firstOrNull { it.name == n } } ?: NewsSource.BBC_TOP
        set(value) = prefs.edit().putString(LAST_SOURCE, value.name).apply()

    fun cached(source: NewsSource): NewsCache? =
        prefs.getString(source.name, null)
            ?.let { runCatching { json.decodeFromString(NewsCache.serializer(), it) }.getOrNull() }
            ?.also { c -> c.items.forEach { known[it.link] = it } }

    suspend fun fetch(source: NewsSource): NewsCache = withContext(Dispatchers.IO) {
        val body = get(source.url)
        val items = if (source == NewsSource.CNN) NewsParser.parseCnnHeadlines(body) else NewsParser.parseRss(body, source)
        if (items.isEmpty()) throw IOException("No headlines in ${source.url}")
        val cache = NewsCache(items, System.currentTimeMillis())
        items.forEach { item ->
            known[item.link] = item
            // A feed that carries the whole story fills the in-memory reader cache up front.
            if (item.content.isNotBlank()) {
                val paragraphs = NewsParser.contentParagraphs(item.content)
                if (paragraphs.isNotEmpty()) articles[item.link] = Article(item.title, paragraphs)
            }
        }
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
        const val LAST_SOURCE = "last_source"
    }
}
