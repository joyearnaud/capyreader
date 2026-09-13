package com.jocmp.aiclient

import org.jsoup.Jsoup

private const val BLOCK_ELEMENTS =
    "p, div, section, article, li, h1, h2, h3, h4, h5, h6, blockquote, pre, tr"

private val WHITESPACE = Regex("[ \t\u00a0]+")
private val BLANK_LINES = Regex("\n{3,}")

/**
 * Converts article HTML to plain text, keeping block boundaries as blank lines.
 * Article text is what we pay for by the token, so markup must not be sent.
 */
fun htmlToText(html: String): String {
    if (html.isBlank()) return ""

    val document = Jsoup.parse(html)
    document.select("script, style, noscript, template, svg, iframe").remove()
    document.select("br").forEach { it.appendText("\n") }
    document.select(BLOCK_ELEMENTS).forEach { it.appendText("\n\n") }

    return document.body()
        .wholeText()
        .lineSequence()
        .map { it.replace(WHITESPACE, " ").trim() }
        .joinToString("\n")
        .replace(BLANK_LINES, "\n\n")
        .trim()
}
