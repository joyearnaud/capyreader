package com.jocmp.capy

import java.time.ZonedDateTime

data class ArticleSummaryRecord(
    val articleID: String,
    val providerKey: String,
    val promptHash: String,
    val content: String,
    val createdAt: ZonedDateTime,
)
