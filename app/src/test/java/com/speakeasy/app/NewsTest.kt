package com.speakeasy.app

import com.speakeasy.app.data.NewsParser
import com.speakeasy.app.data.NewsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser

class NewsTest {

    private val rss = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss xmlns:media="http://search.yahoo.com/mrss/" version="2.0">
          <channel>
            <title><![CDATA[BBC News]]></title>
            <link>https://www.bbc.co.uk/news</link>
            <item>
              <title><![CDATA[Storm hits the coast]]></title>
              <description><![CDATA[Thousands of homes are without <b>power</b> after the storm.]]></description>
              <link>https://www.bbc.com/news/articles/abc123?at_medium=RSS&amp;at_campaign=rss</link>
              <pubDate>Wed, 04 Nov 2026 10:15:30 GMT</pubDate>
              <media:thumbnail width="240" height="135" url="https://ichef.bbci.co.uk/a.jpg"/>
            </item>
            <item>
              <title>Markets rise &amp; fall</title>
              <description>Shares moved.</description>
              <link>https://www.bbc.com/news/articles/def456</link>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun parsesBbcRss() {
        val items = NewsParser.parseRss(rss, NewsSource.BBC_TOP, KXmlParser())
        assertEquals(2, items.size)
        val first = items[0]
        assertEquals("Storm hits the coast", first.title)
        assertEquals("Thousands of homes are without power after the storm.", first.summary)
        assertEquals("https://www.bbc.com/news/articles/abc123?at_medium=RSS&at_campaign=rss", first.link)
        assertEquals("https://ichef.bbci.co.uk/a.jpg", first.image)
        assertEquals(1793787330000L, first.published)
        assertEquals("Markets rise & fall", items[1].title)
        assertNull(items[1].published)
    }

    @Test
    fun parsesCnnLiteHeadlines() {
        val html = """
            <ul>
            <li class="card--lite">
                <a href="/2026/11/04/china/trade-talks-intl/index.html">
                    China and US resume trade talks
                </a>
            </li>
            <li class="card--lite">
                <a href="/2026/11/04/us/storm-weather/index.html">
                    Storm&#39;s path shifts &amp; widens
                </a>
            </li>
            </ul>
        """.trimIndent()
        val items = NewsParser.parseCnnHeadlines(html)
        assertEquals(2, items.size)
        assertEquals("China and US resume trade talks", items[0].title)
        assertEquals("https://lite.cnn.com/2026/11/04/china/trade-talks-intl/index.html", items[0].link)
        assertEquals("Storm's path shifts & widens", items[1].title)
        assertEquals(NewsSource.CNN, items[1].source)
    }

    @Test
    fun extractsCnnLiteParagraphs() {
        val html = """
            <h1 class="headline">China and US resume <em>trade</em> talks</h1>
            <p class="paragraph--lite">
                Officials met in <a href="/x">Geneva</a> on Tuesday.
            </p>
            <p class="paragraph--lite">They agreed to meet again &ldquo;soon&rdquo;.</p>
            <p class="footer">See Full Web Article</p>
        """.trimIndent()
        assertEquals(
            listOf("Officials met in Geneva on Tuesday.", "They agreed to meet again “soon”."),
            NewsParser.extractParagraphs(html),
        )
        assertEquals("China and US resume trade talks", NewsParser.extractTitle(html))
    }

    @Test
    fun extractsBbcTextBlocksThenGenericParagraphs() {
        val bbc = """
            <h1 id="main-heading">Storm hits the coast</h1>
            <p>Short promo</p>
            <div data-component="text-block" class="sc-1"><p class="sc-2"><b>Thousands</b> of homes lost power.</p></div>
            <div data-component="text-block"><p>Repairs could take days.</p></div>
            <script>var p = "<p>not text</p>";</script>
        """.trimIndent()
        assertEquals(listOf("Thousands of homes lost power.", "Repairs could take days."), NewsParser.extractParagraphs(bbc))

        val generic = "<p>Too short.</p><p>This paragraph is long enough to count as the body of a news article.</p>"
        assertEquals(
            listOf("This paragraph is long enough to count as the body of a news article."),
            NewsParser.extractParagraphs(generic),
        )
        assertTrue(NewsParser.extractParagraphs("<div>nothing</div>").isEmpty())
    }

    @Test
    fun decodesEntitiesOnce() {
        assertEquals(
            "Tom & Jerry's \"show\" — <now>",
            NewsParser.decodeEntities("Tom &amp; Jerry&#39;s &quot;show&quot; &mdash; &lt;now&gt;"),
        )
        assertEquals("&lt;", NewsParser.decodeEntities("&amp;lt;"))
        assertEquals("café", NewsParser.decodeEntities("caf&#xE9;"))
    }

    @Test
    fun relativeTimesAreVietnamese() {
        val now = 10_000_000_000L
        assertEquals("vừa xong", NewsParser.relativeTime(now - 20_000, now))
        assertEquals("15 phút trước", NewsParser.relativeTime(now - 15 * 60_000, now))
        assertEquals("3 giờ trước", NewsParser.relativeTime(now - 3 * 3_600_000, now))
        assertEquals("2 ngày trước", NewsParser.relativeTime(now - 2 * 86_400_000L, now))
        assertNotNull(NewsParser.parseDate("Wed, 04 Nov 2026 10:15:30 GMT"))
    }
}
