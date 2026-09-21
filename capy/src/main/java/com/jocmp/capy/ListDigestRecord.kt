package com.jocmp.capy

import java.time.ZonedDateTime

data class ListDigestRecord(
    val id: String,
    val scopeLabel: String,
    val articleCount: Long,
    val articleIds: List<String>,
    val content: String,
    val createdAt: ZonedDateTime,
)
