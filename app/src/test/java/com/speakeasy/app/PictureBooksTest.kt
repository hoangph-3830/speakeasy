package com.speakeasy.app

import com.speakeasy.app.data.Levels
import com.speakeasy.app.data.PictureBookContent
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PictureBooksTest {

    private val content: PictureBookContent = Json { ignoreUnknownKeys = true }
        .decodeFromString(PictureBookContent.serializer(), File("src/main/assets/picturebooks.json").readText())

    @Test
    fun libraryParsesWithUniqueIds() {
        assertTrue(content.books.isNotEmpty())
        assertEquals(content.books.size, content.books.map { it.id }.toSet().size)
    }

    @Test
    fun everyPageHasEnglishAndVietnamese() {
        content.books.forEach { b ->
            assertTrue("${b.id} pages", b.pages.isNotEmpty())
            b.pages.forEachIndexed { i, p ->
                assertTrue("${b.id} page $i en", p.en.isNotBlank())
                assertTrue("${b.id} page $i vi", p.vi.isNotBlank())
            }
            assertTrue("${b.id} titles", b.title.isNotBlank() && b.titleVi.isNotBlank())
        }
    }

    @Test
    fun levelsAreValid() {
        content.books.forEach { assertTrue("${it.id} level ${it.level}", it.level in Levels.all) }
    }

    /** The CC licences require the attribution the reader shows on its last page. */
    @Test
    fun creditsArePresent() {
        content.books.forEach { b ->
            assertTrue("${b.id} author", b.text.isNotBlank())
            assertTrue("${b.id} illustrator", b.illustration.isNotBlank())
            assertTrue("${b.id} translator", b.translation.isNotBlank())
            assertTrue("${b.id} licence ${b.license}", b.license == "CC BY" || b.license == "CC BY-NC")
        }
    }

    /** Covers and page pictures may be missing, but every one that exists is loaded over https. */
    @Test
    fun imageUrlsAreHttps() {
        content.books.forEach { b ->
            (listOfNotNull(b.cover) + b.pages.mapNotNull { it.img }).forEach { url ->
                assertTrue("${b.id} $url", url.startsWith("https://"))
            }
        }
    }
}
