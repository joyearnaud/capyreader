package com.capyreader.app.ui.articles.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamTextTest {

    @Test
    fun `splits at the last blank line`() {
        val (stable, tail) = splitStreamText("First block.\n\nSecond block.\n\nThird is being writ")

        assertEquals("First block.\n\nSecond block.", stable)
        assertEquals("Third is being writ", tail)
    }

    @Test
    fun `keeps everything in the tail before any blank line`() {
        val (stable, tail) = splitStreamText("An opening line still being writ")

        assertEquals("", stable)
        assertEquals("An opening line still being writ", tail)
    }

    @Test
    fun `tail is empty when text ends on a blank line`() {
        val (stable, tail) = splitStreamText("First block.\n\n")

        assertEquals("First block.", stable)
        assertEquals("", tail)
    }

    @Test
    fun `normalizes CRLF before splitting`() {
        val (stable, tail) = splitStreamText("First block.\r\n\r\nSecond tail")

        assertEquals("First block.", stable)
        assertEquals("Second tail", tail)
    }

    @Test
    fun `strips inline and line-start markers from the tail`() {
        val stripped = stripStreamTailMarkers("**Bold** and `code`\n# Head\n> q\n- item stays")

        assertEquals("Bold and code\nHead\nq\n- item stays", stripped)
    }

    @Test
    fun `strips partial table pipes`() {
        val stripped = stripStreamTailMarkers("| col")

        assertTrue(!stripped.contains("|"))
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
