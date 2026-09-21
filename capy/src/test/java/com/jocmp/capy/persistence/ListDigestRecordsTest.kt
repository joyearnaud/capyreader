package com.jocmp.capy.persistence

import com.jocmp.capy.InMemoryDatabaseProvider
import com.jocmp.capy.db.Database
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import java.time.ZonedDateTime

class ListDigestRecordsTest {
    private lateinit var database: Database
    private lateinit var records: ListDigestRecords

    private val now: ZonedDateTime = ZonedDateTime.now()

    @BeforeTest
    fun setup() {
        database = InMemoryDatabaseProvider.build("777")
        records = ListDigestRecords(database)
    }

    @Test
    fun storesAndReadsBackADigest() = runTest {
        records.upsert(
            id = "Today|1|2",
            scopeLabel = "Today",
            articleCount = 3,
            articleIds = listOf("a", "b", "c"),
            content = "Digest text",
            now = now,
        )

        val digest = records.findByKey("Today|1|2", cutoff = now.minusDays(1))

        assertEquals("Digest text", digest?.content)
        assertEquals(listOf("a", "b", "c"), digest?.articleIds)
        assertEquals(3, digest?.articleCount)
    }

    @Test
    fun overwritesTheSameKey() = runTest {
        records.upsert("k", "Today", 1, listOf("a"), "old", now)
        records.upsert("k", "Today", 1, listOf("a"), "new", now)

        assertEquals("new", records.findByKey("k", cutoff = now.minusMinutes(1))?.content)
    }

    @Test
    fun missesWhenOlderThanCutoff() = runTest {
        records.upsert("k", "Today", 1, listOf("a"), "text", now)

        assertNull(records.findByKey("k", cutoff = now.plusSeconds(1)))
    }

    @Test
    fun recentReturnsNewestFirst() = runTest {
        records.upsert("a", "Today", 1, listOf("x"), "first", now)
        records.upsert("b", "Everything", 2, listOf("y"), "second", now.plusSeconds(10))

        val recent = records.recent(cutoff = now.minusSeconds(1))

        assertEquals(listOf("b", "a"), recent.map { it.id })
    }

    @Test
    fun purgeRemovesOnlyOldRows() = runTest {
        records.upsert("old", "Today", 1, listOf("x"), "old", now.minusDays(4))
        records.upsert("fresh", "Today", 1, listOf("y"), "fresh", now)

        records.deleteOlderThan(cutoff = now.minusDays(3))

        assertNull(records.findByKey("old", cutoff = now.minusDays(5)))
        assertEquals("fresh", records.findByKey("fresh", cutoff = now.minusDays(1))?.content)
    }
}
