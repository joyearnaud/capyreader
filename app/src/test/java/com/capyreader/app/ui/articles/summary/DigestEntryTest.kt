package com.capyreader.app.ui.articles.summary

import com.jocmp.capy.Article
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DigestEntryTest {

    private fun article(summary: String, url: String? = "https://example.com/a") = Article(
        id = "1",
        feedID = "f1",
        title = "A title",
        author = null,
        contentHTML = "",
        url = url?.let { java.net.URL(it) },
        summary = summary,
        imageURL = null,
        updatedAt = ZonedDateTime.of(LocalDateTime.of(2026, 9, 16, 9, 0), ZoneId.systemDefault()),
        publishedAt = ZonedDateTime.of(LocalDateTime.of(2026, 9, 16, 10, 0), ZoneId.systemDefault()),
        read = false,
        starred = false,
        feedName = "Feed Name",
    )

    @Test
    fun `strips html from the summary`() {
        val entry = buildDigestEntry(article(summary = "<p>Hello <b>world</b></p>"))

        assertEquals("Hello world", entry.excerpt)
        assertEquals("Feed Name", entry.feedName)
    }

    @Test
    fun `truncates long excerpts at a word boundary`() {
        val long = "word ".repeat(80)
        val entry = buildDigestEntry(article(summary = long))

        assertTrue(entry.excerpt.length <= 220)
        assertTrue(entry.excerpt.takeLast(30), entry.excerpt.endsWith("word"))
    }

    @Test
    fun `url placeholder summary yields a title-only entry`() {
        val entry = buildDigestEntry(article(summary = "https://example.com/a"))

        assertEquals("", entry.excerpt)
        assertEquals("A title", entry.title)
    }

    @Test
    fun `blank summary yields a title-only entry`() {
        val entry = buildDigestEntry(article(summary = " ", url = null))

        assertEquals("", entry.excerpt)
    }
}
