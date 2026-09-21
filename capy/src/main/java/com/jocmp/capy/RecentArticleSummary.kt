package com.jocmp.capy

import java.time.ZonedDateTime

data class RecentArticleSummary(
    val articleID: String,
    val articleTitle: String,
    val content: String,
    val createdAt: ZonedDateTime,
)
