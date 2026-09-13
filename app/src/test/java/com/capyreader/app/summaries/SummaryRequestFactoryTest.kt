package com.capyreader.app.summaries

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryRequestFactoryTest {
    @Test
    fun `converts html to text and keeps the title`() {
        val request = buildSummaryRequest(
            systemPrompt = "Summarize.",
            title = "Title",
            contentHTML = "<p>Body text</p>",
        )

        assertEquals("Summarize.", request.systemPrompt)
        assertEquals("Title", request.title)
        assertEquals("Body text", request.text)
    }

    @Test
    fun `truncates content longer than the budget`() {
        val request = buildSummaryRequest(
            systemPrompt = "Summarize.",
            title = "Title",
            contentHTML = "<p>${"a".repeat(500)}</p>",
            maxCharacters = 100,
        )

        assertEquals(100, request.text.length)
        assertTrue(isTruncated("<p>${"a".repeat(500)}</p>", maxCharacters = 100))
    }

    @Test
    fun `keeps content shorter than the budget`() {
        val request = buildSummaryRequest(
            systemPrompt = "Summarize.",
            title = "Title",
            contentHTML = "<p>short</p>",
            maxCharacters = 100,
        )

        assertTrue(request.text.startsWith("short"))
        assertFalse(isTruncated("<p>short</p>", maxCharacters = 100))
    }

    @Test
    fun `returns empty text for an empty article body`() {
        val request = buildSummaryRequest(
            systemPrompt = "Summarize.",
            title = "Title",
            contentHTML = "",
        )

        assertEquals("", request.text)
    }
}
