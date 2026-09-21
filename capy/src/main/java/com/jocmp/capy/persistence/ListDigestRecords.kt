package com.jocmp.capy.persistence

import com.jocmp.capy.ListDigestRecord
import com.jocmp.capy.common.TimeHelpers.nowUTC
import com.jocmp.capy.common.toDateTimeFromSeconds
import com.jocmp.capy.common.transactionWithErrorHandling
import com.jocmp.capy.db.Database
import java.time.ZonedDateTime

class ListDigestRecords(private val database: Database) {
    fun upsert(
        id: String,
        scopeLabel: String,
        articleCount: Int,
        articleIds: List<String>,
        content: String,
        now: ZonedDateTime = nowUTC(),
    ) {
        database.transactionWithErrorHandling {
            database.list_digestsQueries.upsert(
                id = id,
                scopeLabel = scopeLabel,
                articleCount = articleCount.toLong(),
                // IDs are UUIDs: a newline separator is unambiguous and needs no JSON.
                articleIds = articleIds.joinToString("\n"),
                content = content,
                createdAt = now.toEpochSecond(),
            )
        }
    }

    fun findByKey(id: String, cutoff: ZonedDateTime): ListDigestRecord? =
        database.list_digestsQueries.findByKey(
            id = id,
            cutoff = cutoff.toEpochSecond(),
        ).executeAsOneOrNull()?.toRecord()

    fun recent(cutoff: ZonedDateTime): List<ListDigestRecord> =
        database.list_digestsQueries.recent(
            cutoff = cutoff.toEpochSecond(),
        ).executeAsList().map { it.toRecord() }

    fun deleteOlderThan(cutoff: ZonedDateTime) {
        database.transactionWithErrorHandling {
            database.list_digestsQueries.deleteOlderThan(cutoff = cutoff.toEpochSecond())
        }
    }

    private fun com.jocmp.capy.db.List_digests.toRecord() = ListDigestRecord(
        id = id,
        scopeLabel = scope_label,
        articleCount = article_count,
        articleIds = if (article_ids.isEmpty()) emptyList() else article_ids.split("\n"),
        content = content,
        createdAt = created_at.toDateTimeFromSeconds,
    )
}
