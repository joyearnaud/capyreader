package com.capyreader.app.ui.articles.summary

import com.capyreader.app.preferences.AppPreferences
import com.jocmp.aiclient.SummaryRequest
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun buildListSummaryRequest(
    scopeLabel: String,
    entries: List<DigestEntry>,
    systemPrompt: String,
): SummaryRequest {
    val zone = ZoneId.systemDefault()
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    val text = buildString {
        if (entries.isNotEmpty()) {
            val oldest = entries.last().publishedAt.withZoneSameInstant(zone).toLocalDate()
            val newest = entries.first().publishedAt.withZoneSameInstant(zone).toLocalDate()
            append("${entries.size} articles from $oldest to $newest, newest first.")
        }
        entries.forEachIndexed { index, entry ->
            appendLine()
            appendLine()
            val day = entry.publishedAt.withZoneSameInstant(zone).toLocalDate().format(formatter)
            append("(${index + 1}) [$day] ${entry.feedName} — ${entry.title}")
            if (entry.excerpt.isNotBlank()) {
                appendLine()
                append(entry.excerpt)
            }
        }
    }

    return SummaryRequest(
        systemPrompt = systemPrompt,
        title = scopeLabel,
        text = text,
        thinkingDisabled = true,
    )
}
