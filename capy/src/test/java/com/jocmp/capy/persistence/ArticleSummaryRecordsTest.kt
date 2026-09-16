package com.jocmp.capy.persistence

import com.jocmp.capy.InMemoryDatabaseProvider
import com.jocmp.capy.db.Database
import com.jocmp.capy.fixtures.ArticleFixture
import kotlinx.coroutines.test.runTest
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArticleSummaryRecordsTest {
    private lateinit var database: Database
    private lateinit var articleFixture: ArticleFixture
    private lateinit var records: ArticleRecords

    @Before
    fun setup() {
        database = InMemoryDatabaseProvider.build("777")
        articleFixture = ArticleFixture(database)
        records = ArticleRecords(database)
    }

    @Test
    fun storesAndReadsBackASummary() = runTest {
        val article = articleFixture.create()

        records.upsertSummary(
            articleID = article.id,
            providerKey = "https://api.example.com/v1/deepseek-chat",
            promptHash = "abc",
            content = "Résumé de l'article",
        )

        val summary = records.findSummary(
            articleID = article.id,
            providerKey = "https://api.example.com/v1/deepseek-chat",
            promptHash = "abc",
        )

        assertEquals("Résumé de l'article", summary?.content)
    }

    @Test
    fun overwritesWhenTheSameKeyIsInsertedTwice() = runTest {
        val article = articleFixture.create()

        records.upsertSummary(article.id, "k1", "h1", "old")
        records.upsertSummary(article.id, "k1", "h1", "new")

        assertEquals("new", records.findSummary(article.id, "k1", "h1")?.content)
    }

    @Test
    fun keepsSeparateRowsPerProviderAndPrompt() = runTest {
        val article = articleFixture.create()

        records.upsertSummary(article.id, "provider-a", "h", "from A")
        records.upsertSummary(article.id, "provider-b", "h", "from B")

        assertEquals("from A", records.findSummary(article.id, "provider-a", "h")?.content)
        assertEquals("from B", records.findSummary(article.id, "provider-b", "h")?.content)
    }

    @Test
    fun deleteOrphanedRemovesSummariesWhoseArticleIsGone() = runTest {
        val article = articleFixture.create()

        records.upsertSummary(article.id, "k", "h", "text")
        database.articlesQueries.deleteByID(articleID = article.id)
        records.deleteOrphanedSummaries()

        assertNull(records.findSummary(article.id, "k", "h"))
    }

    @Test
    fun findByArticleMissesOnDifferentPromptHash() = runTest {
        val article = articleFixture.create()

        records.upsertSummary(article.id, "k", "hash-1", "text")

        assertNull(records.findSummary(article.id, "k", "hash-2"))
    }
}
