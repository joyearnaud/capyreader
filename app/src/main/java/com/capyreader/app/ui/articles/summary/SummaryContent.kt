package com.capyreader.app.ui.articles.summary

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
 * The streamed summary body: per-block Markdown plus the plain stripped
 * tail, shared by the article card and the list digest sheet.
 * Reference markers like (1) become markdown links to
 * capysummary://article/<id> when [referenceTargets] maps them.
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

    // Match the article reader: its font family and its body size (CSS px ~
    // sp at unit scale; sp keeps system font-scale accessibility for app UI).
    // Headings scale by the same ratio so they never sink below the body.
    val family = readerFontFamily.toFontFamily() ?: FontFamily.Default
    val ratio = readerFontSize / FontSize.DEFAULT.toFloat()
    val body = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = family,
        fontSize = readerFontSize.sp,
        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * ratio,
    )
    val heading = MaterialTheme.typography.titleMedium.copy(
        fontFamily = family,
        fontSize = MaterialTheme.typography.titleMedium.fontSize * ratio,
        lineHeight = MaterialTheme.typography.titleMedium.lineHeight * ratio,
    )

    Column(modifier) {
        state.text?.let { raw ->
            // One Markdown per block: the renderer flips to an async Loading
            // state (rendered as nothing) whenever its content string changes,
            // so a single growing Markdown call flashes the card shut every
            // tick. A completed block's string never changes, hitting the
            // early-return cache instead; only the newest block parses.
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

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                linkifyReferences(raw, referenceTargets)
                    .split(Regex("\n\n+"))
                    .filter { block -> block.isNotBlank() }
                    .forEach { block ->
                        Markdown(block, typography = typography)
                    }
            }
        }

        state.streamTail?.takeIf { it.isNotBlank() }?.let { tail ->
            Text(
                text = stripStreamTailMarkers(tail),
                style = body,
            )
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

private val REFERENCE_MARKER = Regex("\\((\\d{1,3})\\)")

/** Rewrite (n) markers into markdown links to capysummary://article/<id>;
 *  unknown or out-of-range numbers stay literal. */
fun linkifyReferences(text: String, targets: List<String>): String {
    if (targets.isEmpty()) return text

    return REFERENCE_MARKER.replace(text) { match ->
        val index = match.groupValues[1].toInt()
        val id = targets.getOrNull(index - 1)

        if (id == null) match.value else "[${match.value}](capysummary://article/$id)"
    }
}
