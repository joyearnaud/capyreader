package com.capyreader.app.summaries

import com.jocmp.aiclient.SummaryRequest
import com.jocmp.aiclient.htmlToText

const val DEFAULT_MAX_SUMMARY_CHARACTERS = 24_000

/**
 * Builds the provider request from the article as displayed. The text is truncated because every
 * character is billed, and a long article must not blow up the request.
 */
fun buildSummaryRequest(
    systemPrompt: String,
    title: String,
    contentHTML: String,
    maxCharacters: Int = DEFAULT_MAX_SUMMARY_CHARACTERS,
): SummaryRequest = SummaryRequest(
    systemPrompt = systemPrompt,
    title = title,
    text = htmlToText(contentHTML).take(maxCharacters),
)

fun isTruncated(
    contentHTML: String,
    maxCharacters: Int = DEFAULT_MAX_SUMMARY_CHARACTERS,
): Boolean = htmlToText(contentHTML).length > maxCharacters
