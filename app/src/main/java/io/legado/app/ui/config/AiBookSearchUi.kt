package io.legado.app.ui.config

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.ai.AiBookSearchAvailability
import io.legado.app.help.ai.AiBookSearchCandidate
import io.legado.app.help.ai.AiBookSearchMedia
import io.legado.app.help.ai.AiBookSearchReply
import io.legado.app.help.ai.AiBookSourceHit
import io.legado.app.help.ai.AiBookSourceLookup
import io.legado.app.help.ai.AiBookSourceStatus
import io.legado.app.ui.book.search.SearchResultCard
import io.legado.app.ui.design.theme.NgTheme

internal sealed interface AiBookSearchAction {
    data class Lookup(val candidate: AiBookSearchCandidate, val keyword: String = candidate.title) : AiBookSearchAction
    data class Scope(val candidate: AiBookSearchCandidate) : AiBookSearchAction
    data class Reject(val keys: List<String>) : AiBookSearchAction
    data class Open(val hit: AiBookSourceHit) : AiBookSearchAction
    data object EnableMcp : AiBookSearchAction
}

@Composable
internal fun AiBookSearchGreetingText(
    playAnimation: Boolean,
    onAnimationFinished: () -> Unit,
) {
    val message = stringResource(R.string.ai_book_search_guide_greeting) + "\n" +
        stringResource(R.string.ai_book_search_guide_body)
    val reveal = remember(message) { Animatable(0, Int.VectorConverter) }
    val animate = playAnimation && !NgTheme.snapshot.isEInk
    LaunchedEffect(message, playAnimation, animate) {
        if (playAnimation) {
            if (animate) reveal.animateTo(message.length, tween(
                durationMillis = (message.length * 24).coerceAtMost(1800),
                easing = LinearEasing,
            ))
            onAnimationFinished()
        }
    }
    val visible = if (animate) reveal.value else message.length
    val text = remember(message, visible) {
        buildAnnotatedString {
            append(message)
            // Keep the full text in layout so its line breaks stay fixed while it appears.
            if (visible < message.length) addStyle(SpanStyle(color = Color.Transparent), visible, message.length)
        }
    }
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Normal,
    )
}

@Composable
internal fun AiBookSearchWebNotice(availability: AiBookSearchAvailability) {
    val message = when (availability) {
        AiBookSearchAvailability.READY -> return
        AiBookSearchAvailability.SKILL -> R.string.ai_book_search_skill_unavailable
        AiBookSearchAvailability.MODEL -> R.string.ai_book_search_missing_model
        AiBookSearchAvailability.ACCOUNT -> R.string.ai_book_search_account_required
        AiBookSearchAvailability.PROVIDER -> R.string.ai_book_search_key_required
        AiBookSearchAvailability.GENERATION -> R.string.ai_book_search_model_config_invalid
        AiBookSearchAvailability.OFF -> R.string.ai_book_search_web_off
        AiBookSearchAvailability.UNAVAILABLE -> R.string.ai_book_search_configure_web
        AiBookSearchAvailability.EXTERNAL_SETUP -> R.string.ai_book_search_external_setup
        AiBookSearchAvailability.EXTERNAL_TOOLS -> R.string.ai_book_search_external_tools
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_book_update_error),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun AiBookSearchReplyContent(
    reply: AiBookSearchReply,
    actionsEnabled: Boolean,
    mcpEnabled: Boolean,
    onAction: (AiBookSearchAction) -> Unit,
) {
    val candidates = reply.candidates.filterNot { it.key in reply.rejectedKeys }
    val lookup = reply.lookup
    val current = candidates.firstOrNull { it.key == lookup?.candidateKey }
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (lookup != null && current != null) {
            lookup.hits.forEach { hit ->
                val book = remember(hit) {
                    SearchBook(
                        bookUrl = hit.url, origin = hit.origin, originName = hit.originName,
                        name = hit.title, author = hit.author, coverUrl = hit.coverUrl,
                    )
                }
                SearchResultCard(
                    book = book,
                    inBookshelf = false,
                    originCount = hit.sourceCount.takeIf { it > 1 } ?: 0,
                    onClick = { onAction(AiBookSearchAction.Open(hit)) },
                    onLongClick = { onAction(AiBookSearchAction.Open(hit)) },
                    outerHorizontalPadding = 0.dp,
                    outerVerticalPadding = 0.dp,
                    cardCornerRadius = 14.dp,
                    minCardHeight = 78.dp,
                    coverWidth = 45.dp,
                    coverHeight = 64.dp,
                    cardStrokeColorRes = R.color.ng_search_result_card_stroke,
                    showIntro = false,
                    onAccent = if (NgTheme.snapshot.isDark) MaterialTheme.colorScheme.onPrimary else Color.White,
                )
            }
            AiBookSearchLookupStatus(lookup, current, mcpEnabled, actionsEnabled, onAction)
        }
        val choices = when {
            current == null || lookup == null -> candidates
            lookup.status != AiBookSourceStatus.RUNNING && lookup.hits.isEmpty() -> candidates.filter { it != current }
            else -> emptyList()
        }
        if (choices.isNotEmpty()) {
            AiBookSearchCandidates(choices, reply.verified, current == null, actionsEnabled, onAction)
        }
        if (reply.rejectedKeys.isNotEmpty() && candidates.isEmpty()) {
            AiBookSearchPanel {
                Text(stringResource(R.string.ai_book_search_more_clues), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiBookSearchCandidates(
    choices: List<AiBookSearchCandidate>,
    verified: Boolean,
    canReject: Boolean,
    actionsEnabled: Boolean,
    onAction: (AiBookSearchAction) -> Unit,
) {
    AiBookSearchPanel {
        choices.forEachIndexed { index, candidate ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.68f))
            }
            if (!verified) {
                Text(candidate.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Normal)
                val details = listOfNotNull(
                    candidate.author.takeIf { it.isNotBlank() },
                    candidate.media?.let { stringResource(it.labelRes) },
                ).joinToString(" · ")
                if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall)
            }
            val showReject = canReject && index == choices.lastIndex
            if (showReject || candidate.searchable) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                ) {
                    if (showReject) {
                        AiBookSearchTextAction(
                            if (choices.size > 1) R.string.ai_book_search_reject_all else R.string.ai_book_search_reject,
                            enabled = actionsEnabled,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        ) { onAction(AiBookSearchAction.Reject(choices.map { it.key })) }
                    }
                    if (candidate.searchable) {
                        InteractionChip(
                            text = stringResource(
                                if (verified) R.string.ai_book_search_search_sources else R.string.ai_book_search_select
                            ),
                            primary = true,
                            modifier = Modifier.align(Alignment.CenterVertically),
                            enabled = actionsEnabled,
                            compact = true,
                        ) { onAction(AiBookSearchAction.Lookup(candidate)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiBookSearchLookupStatus(
    lookup: AiBookSourceLookup,
    candidate: AiBookSearchCandidate,
    mcpEnabled: Boolean,
    actionsEnabled: Boolean,
    onAction: (AiBookSearchAction) -> Unit,
) {
    val statusText = when (lookup.status) {
        AiBookSourceStatus.RUNNING -> stringResource(R.string.ai_book_search_searching)
        AiBookSourceStatus.COMPLETE -> if (lookup.hits.isEmpty()) {
            stringResource(R.string.ai_book_search_no_match, candidate.title)
        } else null
        AiBookSourceStatus.INCOMPLETE -> stringResource(R.string.ai_book_search_incomplete)
        AiBookSourceStatus.FAILED -> stringResource(R.string.ai_book_search_failed)
        AiBookSourceStatus.DISABLED -> if (mcpEnabled) null else stringResource(R.string.ai_book_search_disabled)
        AiBookSourceStatus.INVALID_SCOPE -> stringResource(R.string.ai_book_search_scope_invalid)
        AiBookSourceStatus.NO_SOURCES -> stringResource(R.string.ai_book_search_no_sources)
        AiBookSourceStatus.AUDIO_SCOPE_REQUIRED -> stringResource(R.string.ai_book_search_audio_scope)
        AiBookSourceStatus.NAMESAKE -> stringResource(R.string.ai_book_search_namesake)
        AiBookSourceStatus.UNVERIFIED -> stringResource(R.string.ai_book_search_source_unverified)
        AiBookSourceStatus.INTERRUPTED -> stringResource(R.string.ai_book_search_interrupted)
        AiBookSourceStatus.OPEN_FAILED -> stringResource(R.string.ai_book_search_open_failed)
    }
    if (statusText == null && lookup.hits.isNotEmpty()) return
    AiBookSearchPanel {
        if (statusText != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (lookup.status == AiBookSourceStatus.RUNNING) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                }
                Text(statusText, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (lookup.status == AiBookSourceStatus.DISABLED && !mcpEnabled) {
            AiBookSearchTextAction(R.string.ai_book_search_enable_mcp, enabled = actionsEnabled) {
                onAction(AiBookSearchAction.EnableMcp)
            }
        } else if (lookup.status != AiBookSourceStatus.RUNNING) {
            val needsScope = lookup.status == AiBookSourceStatus.INVALID_SCOPE ||
                lookup.status == AiBookSourceStatus.NO_SOURCES || lookup.status == AiBookSourceStatus.AUDIO_SCOPE_REQUIRED
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!needsScope) {
                    AiBookSearchTextAction(R.string.ai_book_search_retry, enabled = actionsEnabled) {
                        onAction(AiBookSearchAction.Lookup(candidate, lookup.keyword))
                    }
                }
                AiBookSearchTextAction(R.string.ai_book_search_scope, enabled = actionsEnabled) {
                    onAction(AiBookSearchAction.Scope(candidate))
                }
                candidate.alias?.takeIf { !needsScope && it != lookup.keyword }?.let { alias ->
                    AiBookSearchTextAction(R.string.ai_book_search_alternate_title, enabled = actionsEnabled) {
                        onAction(AiBookSearchAction.Lookup(candidate, alias))
                    }
                }
            }
        }
    }
}

@Composable
private fun AiBookSearchTextAction(
    labelRes: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(stringResource(labelRes), fontWeight = FontWeight.Normal)
    }
}

@Composable
private fun AiBookSearchPanel(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = aiChatContentCardColor(),
        contentColor = colorResource(R.color.ng_on_surface),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.68f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            content()
        }
    }
}

private val AiBookSearchMedia.labelRes: Int
    get() = when (this) {
        AiBookSearchMedia.NOVEL -> R.string.ai_book_search_media_novel
        AiBookSearchMedia.MANGA -> R.string.ai_book_search_media_manga
        AiBookSearchMedia.AUDIOBOOK -> R.string.ai_book_search_media_audiobook
        AiBookSearchMedia.MUSIC -> R.string.ai_book_search_media_music
        AiBookSearchMedia.VIDEO -> R.string.ai_book_search_media_video
        AiBookSearchMedia.DOWNLOAD -> R.string.ai_book_search_media_download
    }
