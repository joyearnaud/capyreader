package com.capyreader.app.ui.articles.summary

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.ui.articles.detail.toFontFamily
import com.capyreader.app.ui.collectChangesWithDefault
import com.jocmp.capy.articles.FontSize
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import org.koin.compose.koinInject

/**
 * The streamed summary body, shared by the article card and the list digest
 * sheet. While streaming: one plain, marker-stripped Text — nothing converts
 * mid-stream, so the layout can only grow. On completion: a crossfade to the
 * per-block Markdown render (one Markdown per block: the renderer flips to an
 * async Loading state — rendered as nothing — whenever its content changes,
 * so a single growing Markdown call flashes the card shut every tick).
 */
@Composable
fun SummaryContent(
    state: SummaryUiState,
    modifier: Modifier = Modifier,
    referenceTargets: List<String> = emptyList(),
) {
    val appPreferences: AppPreferences = koinInject()
    val readerFontFamily by appPreferences.readerOptions.fontFamily.collectChangesWithDefault()
    val readerFontSize by appPreferences.readerOptions.fontSize.collectChangesWithDefault()

    // Match the article reader: its font family and body size (CSS px ~ sp;
    // sp keeps system font-scale accessibility). Headings scale by the same
    // ratio so they never sink below the body.
    val family = readerFontFamily.toFontFamily() ?: FontFamily.Default
    val ratio = readerFontSize / FontSize.DEFAULT.toFloat()
    val body = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = family,
        fontSize = readerFontSize.sp,
        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * ratio,
    )
    val heading = MaterialTheme.typography.titleLarge.copy(
        fontFamily = family,
        fontSize = MaterialTheme.typography.titleLarge.fontSize * ratio,
        lineHeight = MaterialTheme.typography.titleLarge.lineHeight * ratio,
    )

    Column(modifier) {
        Crossfade(
            targetState = state.streamText != null,
            label = "summary-body",
        ) { streaming ->
            if (streaming) {
                Text(
                    text = stripStreamMarkers(state.streamText.orEmpty()),
                    style = body,
                )
            } else {
                state.text?.let {
                    val typography = markdownTypography(
                        text = body,
                        paragraph = body,
                        list = body,
                        h1 = heading,
                        h2 = heading,
                        h3 = body.copy(fontWeight = FontWeight.SemiBold),
                        h4 = body.copy(fontWeight = FontWeight.SemiBold),
                        h5 = body.copy(fontWeight = FontWeight.SemiBold),
                        h6 = body.copy(fontWeight = FontWeight.SemiBold),
                        quote = body.copy(fontStyle = FontStyle.Italic),
                        textLink = TextLinkStyles(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ),
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        linkifyReferences(it, referenceTargets)
                            .split(Regex("\n\n+"))
                            .filter { block -> block.isNotBlank() }
                            .forEach { block ->
                                Markdown(block, typography = typography)
                            }
                    }
                }
            }
        }

        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private val BARE_REFERENCE = Regex("""\((\d{1,3})\)""")
private val CITATION_MARKER = Regex("""\[\[?(\d{1,3})\]?\]\([^)]*\)""")
private val GENERIC_LINK = Regex("""\[([^\]]*)\]\([^)]*\)""")

/** Flatten the raw cumulative text for plain display: completed citation
 *  links [[1]](url) collapse to (1), other links to their label, and a
 *  half-streamed link at the tail is hidden entirely (it would otherwise
 *  show a raw URL that collapses when its ")" lands). */
fun stripStreamMarkers(text: String): String {
    val collapsed = text
        .replace(CITATION_MARKER) { "(${it.groupValues[1]})" }
        .replace(GENERIC_LINK) { it.groupValues[1] }

    val lastBracket = collapsed.lastIndexOf('[')
    val visible = if (lastBracket >= 0 && ')' !in collapsed.substring(lastBracket)) {
        collapsed.substring(0, lastBracket).trimEnd('[', ']')
    } else {
        collapsed
    }

    return visible
        .replace(Regex("[*`|]"), "")
        .replace(Regex("(?m)^[#>]+\\s*"), "")
}

/** Rewrite bare (n) reference markers into markdown links to
 *  capysummary://article/<id>; already-linked or unknown numbers stay as-is. */
fun linkifyReferences(text: String, targets: List<String>): String {
    if (targets.isEmpty()) return text

    return BARE_REFERENCE.replace(text) { match ->
        val index = match.groupValues[1].toInt()
        val id = targets.getOrNull(index - 1)

        if (id == null) match.value else "[${match.value}](capysummary://article/$id)"
    }
}
