package com.capyreader.app.ui.articles.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DigestSelectorTest {

    private fun at(day: Int, hour: Int = 10): ZonedDateTime =
        ZonedDateTime.of(LocalDateTime.of(2026, 9, day, hour, 0), ZoneId.systemDefault())

    private fun entriesFor(vararg days: Pair<Int, Int>): List<DigestEntry> =
        buildList {
            days.forEach { (day, count) ->
                repeat(count) { i -> add(entry("d${day}-$i", at(day, 8 + i % 10))) }
            }
        }

    private fun entry(id: String, at: ZonedDateTime) =
        DigestEntry(id = id, feedName = "Feed", publishedAt = at, title = "Title $id", excerpt = "excerpt")

    @Test
    fun `accumulates whole days until the soft cap`() {
        // 30 + 30 = 60 >= 50 -> the third day is never opened
        val selected = selectDigestDays(entriesFor(10 to 30, 9 to 30, 8 to 30))

        assertEquals(60, selected.size)
        assertTrue(selected.all { it.publishedAt.dayOfMonth >= 9 })
    }

    @Test
    fun `first day is kept whole even beyond the soft cap`() {
        val selected = selectDigestDays(entriesFor(10 to 70))

        assertEquals(70, selected.size)
    }

    @Test
    fun `trims the oldest entries to the hard cap`() {
        val selected = selectDigestDays(entriesFor(10 to 200))

        assertEquals(120, selected.size)
        assertTrue(selected.all { it.publishedAt.dayOfMonth == 10 })
        // newest kept, oldest trimmed
        assertTrue(selected.none { it.id == "d10-0" })
        assertTrue(selected.any { it.id == "d10-199" })
    }

    @Test
    fun `empty list selects nothing`() {
        assertEquals(0, selectDigestDays(emptyList()).size)
    }

    @Test
    fun `days are accumulated newest first`() {
        val selected = selectDigestDays(entriesFor(8 to 10, 10 to 10, 9 to 10))

        // 10 + 10 + 10 = 30 < 50 -> every day is opened, oldest last
        assertEquals(30, selected.size)
        assertTrue(selected.first().publishedAt.dayOfMonth == 10)
        assertTrue(selected.last().publishedAt.dayOfMonth == 8)
    }
}
