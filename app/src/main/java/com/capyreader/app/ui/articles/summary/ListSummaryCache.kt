package com.capyreader.app.ui.articles.summary

import com.jocmp.capy.ArticleFilter

/**
 * In-process LRU cache for list digests, keyed by scope + the exact selection
 * + prompt. Survives navigation (process lifetime); a fresh article changes
 * the selection hash and misses on purpose. Entries keep the article id order
 * so (n) reference markers keep resolving after a cache hit.
 */
class ListSummaryCache {
    data class Entry(val text: String, val articleIds: List<String>)

    private data class Key(val scope: String, val selectionHash: Int, val promptHash: Int)

    private val entries = object : LinkedHashMap<Key, Entry>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Entry>): Boolean =
            size > MAX_ENTRIES
    }

    fun get(scope: ArticleFilter, articleIds: List<String>, prompt: String): Entry? =
        entries[keyOf(scope, articleIds, prompt)]

    fun put(scope: ArticleFilter, articleIds: List<String>, prompt: String, entry: Entry) {
        entries[keyOf(scope, articleIds, prompt)] = entry
    }

    private fun keyOf(scope: ArticleFilter, articleIds: List<String>, prompt: String) = Key(
        scope = scope.toString(),
        selectionHash = articleIds.hashCode(),
        promptHash = (prompt + articleIds.size).hashCode(),
    )

    private companion object {
        const val MAX_ENTRIES = 8
    }
}
