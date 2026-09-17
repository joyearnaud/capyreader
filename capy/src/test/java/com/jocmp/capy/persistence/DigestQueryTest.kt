package com.jocmp.capy.persistence

import com.jocmp.capy.ArticleFilter
import com.jocmp.capy.ArticleStatus
import com.jocmp.capy.InMemoryDatabaseProvider
import com.jocmp.capy.common.TimeHelpers
import com.jocmp.capy.db.Database
import com.jocmp.capy.fixtures.ArticleFixture
import com.jocmp.capy.fixtures.FeedFixture
import kotlinx.coroutines.test.runTest
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals

class DigestQueryTest {
    private lateinit var database: Database
    private lateinit var articleFixture: ArticleFixture
    private lateinit var feedFixture: FeedFixture
    private lateinit var records: ArticleRecords

    @Before
    fun setup() {
        database = InMemoryDatabaseProvider.build("787")
        feedFixture = FeedFixture(database)
        articleFixture = ArticleFixture(database)
        records = ArticleRecords(database)
    }

    @Test
    fun `includes both read and unread articles`() = runTest {
        articleFixture.create(id = "read", read = true)
        articleFixture.create(id = "unread", read = false)

        val result = records.findRecentForDigest(ArticleFilter.Articles(ArticleStatus.ALL))

        assertEquals(setOf("read", "unread"), result.map { it.id }.toSet())
    }

    @Test
    fun `scopes to a single feed`() = runTest {
        val feedA = feedFixture.create(feedURL = "https://example.com/a")
        val feedB = feedFixture.create(feedURL = "https://example.com/b")
        articleFixture.create(id = "in-feed", feed = feedA)
        articleFixture.create(id = "other", feed = feedB)

        val result = records.findRecentForDigest(
            ArticleFilter.Feeds(feedID = feedA.id, folderTitle = null, feedStatus = ArticleStatus.ALL)
        )

        assertEquals(listOf("in-feed"), result.map { it.id })
    }

    @Test
    fun `respects the limit, newest first`() = runTest {
        val now = TimeHelpers.nowUTC().toEpochSecond()
        articleFixture.create(id = "old", publishedAt = now - 7200)
        articleFixture.create(id = "middle", publishedAt = now - 3600)
        articleFixture.create(id = "new", publishedAt = now)

        val result = records.findRecentForDigest(
            ArticleFilter.Articles(ArticleStatus.ALL),
            limit = 2,
        )

        assertEquals(listOf("new", "middle"), result.map { it.id })
    }
}
