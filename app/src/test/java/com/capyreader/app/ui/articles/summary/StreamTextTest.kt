package com.capyreader.app.ui.articles.summary

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamTextTest {

    @Test
    fun `strips inline and line-start markers`() {
        val stripped = stripStreamMarkers("**Bold** and `code`\n# Head\n> q\n- item stays")

        assertEquals("Bold and code\nHead\nq\n- item stays", stripped)
    }

    @Test
    fun `strips partial table pipes`() {
        val stripped = stripStreamMarkers("| col")

        assertTrue(stripped, !stripped.contains("|"))
    }

    @Test
    fun `collapses citation links to their number`() {
        val stripped = stripStreamMarkers("Voir [[2]](capysummary://article/abc-123) et [[1]](https://x.y)")

        assertEquals("Voir (2) et (1)", stripped)
    }

    @Test
    fun `collapses generic links to their label`() {
        val stripped = stripStreamMarkers("Une [source](https://example.com) fiable")

        assertEquals("Une source fiable", stripped)
    }
}

class AdvanceDisplayedTest {

    @Test
    fun `catches up by a fifth of the backlog`() {
        assertEquals(6, advanceDisplayed(0, 30))
    }

    @Test
    fun `caps the per-frame step`() {
        assertEquals(6, advanceDisplayed(0, 1000))
    }

    @Test
    fun `always advances by at least one char and clamps at the end`() {
        assertEquals(300, advanceDisplayed(299, 300))
        assertEquals(5, advanceDisplayed(5, 5))
    }
}

class LinkifyReferencesTest {

    @Test
    fun `rewrites in-range markers into markdown links`() {
        val linked = linkifyReferences("Thème A voit (1) et (2). Puis (1) encore.", listOf("a", "b"))

        assertEquals(2, Regex("capysummary://article/a").findAll(linked).count())
        assertEquals(1, Regex("capysummary://article/b").findAll(linked).count())
        assertTrue(
            linked,
            linked.startsWith("Thème A voit [(1)](capysummary://article/a)"),
        )
    }

    @Test
    fun `out-of-range markers stay literal`() {
        val linked = linkifyReferences("L'année (2026) et le (9).", listOf("a"))

        assertTrue(linked, !linked.contains("capysummary://article/"))
        assertTrue(linked.contains("(2026)"))
        assertTrue(linked.contains("(9)"))
    }
}

class BuildListSummaryRequestTest {

    private fun entry(id: String, day: Int) = DigestEntry(
        id = id,
        feedName = "Le Feed",
        publishedAt = ZonedDateTime.of(LocalDateTime.of(2026, 9, day, 10, 0), ZoneId.systemDefault()),
        title = "Titre $id",
        excerpt = "extrait $id",
    )

    @Test
    fun `numbers blocks and passes the prompt through`() {
        val request = buildListSummaryRequest(
            scopeLabel = "News",
            entries = listOf(entry("b", 16), entry("a", 15)),
            systemPrompt = "MON PROMPT",
        )

        assertEquals("MON PROMPT", request.systemPrompt)
        assertEquals("News", request.title)
        assertTrue(request.thinkingDisabled)
        assertTrue(request.text, request.text.contains("(1) [2026-09-16] Le Feed — Titre b"))
        assertTrue(request.text, request.text.contains("(2) [2026-09-15] Le Feed — Titre a"))
        assertTrue(request.text, request.text.startsWith("2 articles from 2026-09-15 to 2026-09-16"))
    }
}

class PartialLinkTest {

    @Test
    fun `hides a half-streamed citation until it closes`() {
        assertEquals("Voir ", stripStreamMarkers("Voir [[2]](capysummary://article/ab"))
    }

    @Test
    fun `hides an unclosed open paren`() {
        assertEquals("Voir ", stripStreamMarkers("Voir [[1]]("))
    }

    @Test
    fun `hides an unclosed label bracket`() {
        assertEquals("une ", stripStreamMarkers("une [sou"))
    }

    @Test
    fun `complete citations still collapse`() {
        assertEquals("Voir (2) la", stripStreamMarkers("Voir [[2]](capysummary://article/x) la"))
    }
}
