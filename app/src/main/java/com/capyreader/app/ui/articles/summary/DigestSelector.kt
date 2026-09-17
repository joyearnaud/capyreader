package com.capyreader.app.ui.articles.summary

import java.time.ZoneId

/**
 * Day-backfill selection: whole calendar days, newest first, until the soft
 * cap; the oldest frontier is then trimmed to the hard cap (a single day
 * larger than the hard cap is the only day ever split).
 */
fun selectDigestDays(
    entries: List<DigestEntry>,
    softCap: Int = 50,
    hardCap: Int = 120,
): List<DigestEntry> {
    if (entries.isEmpty()) return entries

    val zone = ZoneId.systemDefault()
    val byDay = entries
        .asSequence()
        .sortedByDescending { it.publishedAt }
        .groupBy { it.publishedAt.withZoneSameInstant(zone).toLocalDate() }
        .toSortedMap(compareByDescending { it })

    val selected = mutableListOf<DigestEntry>()
    for ((_, dayEntries) in byDay) {
        selected += dayEntries
        if (selected.size >= softCap) break
    }

    return selected.take(hardCap)
}
