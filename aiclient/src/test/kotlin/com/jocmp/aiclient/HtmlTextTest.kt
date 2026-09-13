package com.jocmp.aiclient

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HtmlTextTest {
    @Test
    fun `returns empty string for blank input`() {
        assertEquals("", htmlToText(""))
        assertEquals("", htmlToText("   "))
    }

    @Test
    fun `strips tags and decodes entities`() {
        assertEquals("Tom & Jerry", htmlToText("<p>Tom &amp; Jerry</p>"))
    }

    @Test
    fun `separates paragraphs with a blank line`() {
        assertEquals("One\n\nTwo", htmlToText("<p>One</p><p>Two</p>"))
    }

    @Test
    fun `separates list items`() {
        val text = htmlToText("<ul><li>First</li><li>Second</li></ul>")
        assertTrue(text.contains("First"), text)
        assertTrue(text.contains("Second"), text)
        assertFalse(text.contains("FirstSecond"), text)
    }

    @Test
    fun `drops script and style content`() {
        val text = htmlToText("<script>var secret = 1;</script><style>p{color:red}</style><p>Body</p>")
        assertEquals("Body", text)
        assertFalse(text.contains("secret"), text)
    }

    @Test
    fun `collapses runs of whitespace and blank lines`() {
        assertEquals("A B\n\nC", htmlToText("<p>A    B</p>\n\n\n<p>C</p>"))
    }
}
