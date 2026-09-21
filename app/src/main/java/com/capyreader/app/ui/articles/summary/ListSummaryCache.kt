package com.capyreader.app.ui.articles.summary

import com.jocmp.capy.ArticleFilter

/**
 * Stable identity of a digest for both cache layers (in-process LRU and the
 * persisted list_digests table). One derivation so they can never drift.
 */
fun digestCacheKey(scope: ArticleFilter, articleIds: List<String>, prompt: String): String =
    "${scope}|${articleIds.hashCode()}|${(prompt + articleIds.size).hashCode()}"

class ListSummaryCache {
    data class Entry(val text: String, val articleIds: List<String>)

    private val entries = object : LinkedHashMap<String, Entry>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > MAX_ENTRIES
    }

    fun get(scope: ArticleFilter, articleIds: List<String>, prompt: String): Entry? =
        entries[digestCacheKey(scope, articleIds, prompt)]

    fun put(scope: ArticleFilter, articleIds: List<String>, prompt: String, entry: Entry) {
        entries[digestCacheKey(scope, articleIds, prompt)] = entry
    }

    private companion object {
        const val MAX_ENTRIES = 8
    }
}
