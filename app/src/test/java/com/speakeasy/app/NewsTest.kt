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
    fun cleansReutersHeadlinesFromGoogleNews() {
        val feed = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel><title>site:reuters.com when:1d - Google News</title>
            <item><title>Oil falls for a fourth day - Reuters</title><link>https://news.google.com/rss/articles/CBMi123?oc=5</link><pubDate>Thu, 05 Nov 2026 02:20:00 GMT</pubDate><description>&lt;a href="https://news.google.com/rss/articles/CBMi123?oc=5"&gt;Oil falls for a fourth day&lt;/a&gt;&amp;nbsp;&amp;nbsp;&lt;font color="#6f6f6f"&gt;Reuters&lt;/font&gt;</description><source url="https://www.reuters.com">Reuters</source></item>
            <item><title>Markets brace for jobs data - Reuters</title><link>https://news.google.com/rss/articles/CBMi456?oc=5</link><pubDate>Thu, 05 Nov 2026 04:00:00 GMT</pubDate><description>Investors wait for Friday's report.</description><source url="https://www.reuters.com">Reuters</source></item>
            </channel></rss>
        """.trimIndent()
        val items = NewsParser.parseRss(feed, NewsSource.REUTERS, KXmlParser())
        assertEquals(2, items.size)
        // Newest first, whatever the feed order.
        assertEquals("Markets brace for jobs data", items[0].title)
        assertEquals("Investors wait for Friday's report.", items[0].summary)
        val oil = items[1]
        assertEquals("Oil falls for a fourth day", oil.title)
        assertEquals("", oil.summary)
        assertNull(oil.image)
        assertEquals("https://news.google.com/rss/articles/CBMi123?oc=5", oil.link)
        assertEquals(NewsSource.REUTERS, oil.source)
        assertTrue(NewsSource.REUTERS.headlinesOnly)
        assertEquals(
            "https://news.google.com/rss/search?q=site%3Areuters.com%2Fworld+when%3A1d&hl=en-US&gl=US&ceid=US:en",
            NewsSource.REUTERS_WORLD.url,
        )
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
    fun parsesConversationAtomWithFullContent() {
        val atom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xml:lang="en" xmlns="http://www.w3.org/2005/Atom">
              <id>tag:theconversation.com,2011:/global/articles</id>
              <link rel="alternate" type="text/html" href="https://theconversation.com/global"/>
              <title>The Conversation – Articles (Global)</title>
              <updated>2026-11-05T08:00:00Z</updated>
              <entry>
                <id>tag:theconversation.com,2011:article/251234</id>
                <published>2026-11-04T19:12:43Z</published>
                <updated>2026-11-05T07:30:00Z</updated>
                <link rel="alternate" type="text/html" href="https://theconversation.com/why-sleep-matters-251234"/>
                <title>Why sleep matters more than you think</title>
                <content type="html">&lt;figure&gt;&lt;img src="x.jpg"&gt;&lt;figcaption&gt;&lt;p&gt;A man asleep on a train.&lt;/p&gt;&lt;/figcaption&gt;&lt;/figure&gt;&lt;p&gt;Most adults need &lt;em&gt;seven&lt;/em&gt; hours of sleep a night.&lt;/p&gt;&lt;p&gt;Yet many get far less, and it shows.&lt;/p&gt;</content>
                <summary>Sleep researchers explain what happens when we cut corners.</summary>
                <author><name>Jane Doe</name></author>
              </entry>
            </feed>
        """.trimIndent()
        val items = NewsParser.parseRss(atom, NewsSource.CONVERSATION, KXmlParser())
        assertEquals(1, items.size)
        val entry = items[0]
        assertEquals("Why sleep matters more than you think", entry.title)
        assertEquals("https://theconversation.com/why-sleep-matters-251234", entry.link)
        assertEquals("Sleep researchers explain what happens when we cut corners.", entry.summary)
        // <published> wins over <updated>.
        assertEquals(1793819563000L, entry.published)
        assertEquals(
            listOf("Most adults need seven hours of sleep a night.", "Yet many get far less, and it shows."),
            NewsParser.contentParagraphs(entry.content),
        )
        assertEquals(1793819563000L, NewsParser.parseDate("2026-11-04T19:12:43Z"))
        assertEquals(1793819563000L, NewsParser.parseDate("2026-11-04T20:12:43+01:00"))
    }

    @Test
    fun cleansGuardianDescriptionAndUsesMediaContent() {
        val long = "Ministers promised new funding for coastal towns after weeks of flooding. ".repeat(6)
        val feed = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss xmlns:media="http://search.yahoo.com/mrss/" version="2.0"><channel><title>World news | The Guardian</title>
            <item><title>Floods hit coastal towns</title><link>https://www.theguardian.com/world/2026/nov/04/floods</link>
            <description>&lt;p&gt;Rescue teams worked through the night.&lt;/p&gt; &lt;p&gt;$long&lt;/p&gt; &lt;a href="https://www.theguardian.com/world/2026/nov/04/floods"&gt;Continue reading...&lt;/a&gt;</description>
            <pubDate>Wed, 04 Nov 2026 10:15:30 GMT</pubDate>
            <media:content width="140" url="https://i.guim.co.uk/small.jpg"/>
            <media:content width="460" url="https://i.guim.co.uk/large.jpg"/>
            </item>
            <item><title>Short one</title><link>https://www.theguardian.com/world/2026/nov/04/short</link>
            <description>&lt;p&gt;Just one line.&lt;/p&gt; &lt;a href="x"&gt;Continue reading...&lt;/a&gt;</description></item>
            </channel></rss>
        """.trimIndent()
        val items = NewsParser.parseRss(feed, NewsSource.GUARDIAN_WORLD, KXmlParser())
        val summary = items[0].summary
        assertTrue(summary.startsWith("Rescue teams worked through the night. Ministers promised"))
        assertTrue(summary.endsWith("…"))
        assertTrue(summary.length <= 301)
        assertTrue('<' !in summary && "Continue reading" !in summary)
        assertEquals("https://i.guim.co.uk/large.jpg", items[0].image)
        assertEquals("Just one line.", items[1].summary)
    }

    @Test
    fun dropsAlJazeeraVideoAndGalleryItems() {
        val feed = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel><title>Al Jazeera</title>
            <item><title>Talks resume in Doha</title><link>https://www.aljazeera.com/news/2026/11/4/talks-resume</link></item>
            <item><title>Watch: the storm</title><link>https://www.aljazeera.com/video/newsfeed/2026/11/4/storm</link></item>
            <item><title>In pictures: harvest</title><link>https://www.aljazeera.com/gallery/2026/11/4/harvest</link></item>
            </channel></rss>
        """.trimIndent()
        val items = NewsParser.parseRss(feed, NewsSource.ALJAZEERA, KXmlParser())
        assertEquals(listOf("Talks resume in Doha"), items.map { it.title })
    }

    @Test
    fun extractsNprStorytextAndAlJazeeraWysiwyg() {
        val npr = """
            <p>Sponsor message paragraph that is long enough to be picked by the generic fallback.</p>
            <div id="storytext" class="storytext storylocation linkLocation">
              <div class="bucketwrap image"><figure><img src="a.jpg"><figcaption><p>Voters line up in Ohio on Tuesday morning.</p></figcaption></figure></div>
              <p>Voters across the state went to the polls on Tuesday.</p>
              <div class="bucketwrap internallink"><p>Sign up for our politics newsletter</p></div>
              <p>Turnout was higher than expected.</p>
            </div>
            <p>Copyright 2026 NPR. To see more, visit https://www.npr.org.</p>
        """.trimIndent()
        assertEquals(
            listOf("Voters across the state went to the polls on Tuesday.", "Turnout was higher than expected."),
            NewsParser.extractParagraphs(npr),
        )

        val aj = """
            <header><p>Published On 4 Nov 2026</p></header>
            <div class="wysiwyg wysiwyg--all-content">
              <p>Negotiators met in Doha for a second round of talks.</p>
              <div class="advertisement"><p>Advertisement</p></div>
              <p>Both sides said progress had been made.</p>
            </div>
        """.trimIndent()
        assertEquals(
            listOf("Negotiators met in Doha for a second round of talks.", "Both sides said progress had been made."),
            NewsParser.extractParagraphs(aj),
        )
    }

    @Test
    fun skipsCaptionsBylinesAndPromosInArticles() {
        val guardian = """
            <p>Site-wide banner text that is long enough to be a paragraph outside the article.</p>
            <article class="dcr-1">
              <figure><img src="b.jpg"><figcaption><p class="dcr-cap">Flood water in the town centre, photographed on Monday.</p></figcaption></figure>
              <p class="dcr-byline">By Sam Jones in Leeds</p>
              <p class="dcr-s3ycb2">Flood water rose quickly across the north of England.</p>
              <p class="dcr-s3ycb2">Sign up to First Edition, our free daily newsletter</p>
              <p class="dcr-s3ycb2">Related stories</p>
              <p class="dcr-s3ycb2">Residents were told to leave their homes.</p>
            </article>
        """.trimIndent()
        assertEquals(
            listOf("Flood water rose quickly across the north of England.", "Residents were told to leave their homes."),
            NewsParser.extractParagraphs(guardian),
        )
        assertTrue(NewsParser.isStoryText("Millions subscribe to the service each year."))
        assertTrue(!NewsParser.isStoryText("Advertisement"))
    }

    @Test
    fun sourcesAreGroupedByBrand() {
        assertEquals(
            listOf("BBC", "CNN", "Reuters", "The Guardian", "NPR", "Al Jazeera", "ABC News", "The Conversation"),
            NewsSource.brands,
        )
        assertEquals(6, NewsSource.BBC_WORLD.sections.size)
        assertEquals("BBC · Thế giới", NewsSource.BBC_WORLD.tag)
        assertEquals("CNN", NewsSource.CNN.tag)
        assertEquals("BBC News", NewsSource.BBC_TOP.publisher)
        assertEquals("Reuters", NewsSource.REUTERS_MARKETS.publisher)
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
