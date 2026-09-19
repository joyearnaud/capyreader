package com.capyreader.app.ui.articles.summary

import com.jocmp.capy.Article
import java.time.ZoneId

/**
 * Day-backfill selection: whole calendar days, newest first, until the soft
 * cap; the oldest frontier is then trimmed to the hard cap (a single day
 * larger than the hard cap is the only day ever split). Runs on lightweight
 * article data — excerpt parsing happens only for the selected ones.
 */
fun selectDigestArticles(
    articles: List<Article>,
    softCap: Int = 50,
    hardCap: Int = 120,
): List<Article> {
    if (articles.isEmpty()) return articles

    val zone = ZoneId.systemDefault()
    val byDay = articles
        .asSequence()
        .sortedByDescending { it.publishedAt }
        .groupBy { it.publishedAt.withZoneSameInstant(zone).toLocalDate() }
        .toSortedMap(compareByDescending { it })

    val selected = mutableListOf<Article>()
    for ((_, dayArticles) in byDay) {
        selected += dayArticles
        if (selected.size >= softCap) break
    }

    return selected.take(hardCap)
}
