package com.capyreader.app.ui.articles.summary

import com.jocmp.capy.Article
import com.jocmp.aiclient.htmlToText
import java.time.ZonedDateTime

data class DigestEntry(
    val id: String,
    val feedName: String,
    val publishedAt: ZonedDateTime,
    val title: String,
    val excerpt: String,
    val url: String? = null,
)

private const val MAX_EXCERPT_CHARS = 220

/**
 * List queries carry the RSS summary (frequently HTML) and never the content
 * body; when the summary is blank the mapper substitutes the article URL, so
 * a blank or URL-shaped strip yields a title-only entry.
 */
fun buildDigestEntry(article: Article): DigestEntry {
    val stripped = htmlToText(article.summary)
        .replace(Regex("\\s+"), " ")
        .trim()
    val isUrlPlaceholder = article.url != null && stripped == article.url.toString()

    val excerpt = when {
        stripped.isBlank() || isUrlPlaceholder -> ""
        stripped.length <= MAX_EXCERPT_CHARS -> stripped
        else -> stripped.take(MAX_EXCERPT_CHARS).substringBeforeLast(" ", stripped.take(MAX_EXCERPT_CHARS))
    }

    return DigestEntry(
        id = article.id,
        feedName = article.feedName,
        publishedAt = article.publishedAt,
        title = article.title,
        excerpt = excerpt,
        url = article.url?.toString(),
    )
}
