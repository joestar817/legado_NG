package io.legado.app.ui.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.ai.AiExternalSearchDebugResult
import io.legado.app.help.ai.AiExternalSearchHit
import io.legado.app.help.ai.AiExternalSearchService
import io.legado.app.help.ai.AiExternalSearchTool
import io.legado.app.ui.design.components.NgStatusTagVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgFloatingTitleToolbar
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.components.compose.NgFormControlGroup
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.components.compose.NgPaneDialog
import io.legado.app.ui.design.components.compose.NgStatusTag
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.openUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
internal fun AiSearchServiceDebugScreen(
    service: AiExternalSearchService,
    tool: AiExternalSearchTool,
    model: AiSearchServiceDebugModel,
    onBack: () -> Unit,
    onShowResponse: (AiExternalSearchDebugResult<*>) -> Unit,
) {
    val listState = rememberLazyListState()
    val article = model.article
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        if (article != null) {
            SearchDebugArticleScreen(article, onBack, onShowResponse)
        } else {
            val fetching = tool == AiExternalSearchTool.READ_LINK
            val searchResult = model.searchResult
            val searching = model.activeTool == AiExternalSearchTool.SEARCH
            NgFloatingTitleToolbar(
                title = stringResource(if (fetching) R.string.ai_search_debug_fetch_results else R.string.ai_search_debug_search_results),
                onBack = onBack,
                modifier = Modifier.padding(horizontal = 16.dp),
                titleLeadingContent = { SearchServiceLogo(service.provider) },
                trailingContent = {
                    if (fetching) {
                        Text(
                            service.displayName,
                            modifier = Modifier.widthIn(max = 96.dp).padding(end = 8.dp),
                            color = colorResource(R.color.ng_search_content),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else if (searchResult != null && !searching) {
                        NgFormActionButton(
                            text = stringResource(R.string.ai_search_debug_view_response),
                            onClick = { onShowResponse(searchResult) },
                            appearance = NgFormActionButtonAppearance.TEXT,
                            buttonHeight = 34.dp,
                            textSize = 13.sp,
                        )
                    }
                },
            )
            when (tool) {
                AiExternalSearchTool.READ_LINK -> {
                    FetchHistory(
                        records = model.fetches,
                        loading = model.activeTool == AiExternalSearchTool.READ_LINK,
                        listState = listState,
                        onOpen = { record ->
                            val result = record.result
                            if (result?.error != null) onShowResponse(result) else model.openArticle(record)
                        },
                        onShowResponse = onShowResponse,
                    )
                }
                AiExternalSearchTool.SEARCH -> {
                    DebugSearchResults(
                        result = searchResult,
                        searching = searching,
                        canFetch = model.activeTool == null,
                        listState = listState,
                        onFetch = { hit -> hit.url?.let { model.fetch(service, it, hit) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun DebugSearchResults(
    result: AiExternalSearchDebugResult<List<AiExternalSearchHit>>?,
    searching: Boolean,
    canFetch: Boolean,
    listState: LazyListState,
    onFetch: (AiExternalSearchHit) -> Unit,
) {
    val context = LocalContext.current
    val hits = result?.data.orEmpty()
    val keyedHits = remember(hits) {
        val occurrences = mutableMapOf<String, Int>()
        hits.map { hit ->
            val identity = hit.address.ifBlank { hit.title }
            val occurrence = occurrences.getOrDefault(identity, 0)
            occurrences[identity] = occurrence + 1
            "${identity.length}:$identity:$occurrence" to hit
        }
    }
    var summaryKey by rememberSaveable { mutableStateOf<String?>(null) }
    val viewSummaryLabel = stringResource(R.string.ai_search_debug_view_summary)
    val label = when {
        searching -> stringResource(R.string.ai_chat_web_searching)
        result?.error != null -> result.error.displayMessage(context)
        result == null -> stringResource(R.string.ai_search_debug_search_empty)
        hits.isEmpty() -> stringResource(R.string.ai_search_test_empty)
        else -> pluralStringResource(R.plurals.ai_search_debug_result_count, hits.size, hits.size)
    }
    val summary = if (result != null) {
        stringResource(R.string.ai_search_debug_result_time, label, searchDebugDuration(result.elapsedMs))
    } else label
    if (searching || hits.isEmpty() || result?.error != null) {
        SearchDebugSummary(summary, result, onShowResponse = null, loading = searching, isSearch = true)
        return
    }
    val firstResultKey = keyedHits.first().first
    LazyColumn(
        modifier = Modifier.padding(top = 12.dp),
        state = listState,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(keyedHits, key = { it.first }) { (key, hit) ->
            NgFormPanel {
                if (key == firstResultKey) {
                    Text(
                        text = summary,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Normal,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.35f),
                    )
                }
                Column(
                    Modifier.fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = viewSummaryLabel) { summaryKey = key }
                        .padding(horizontal = 14.dp),
                ) {
                    val link = hit.url
                    val date = hit.publishedDate
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.weight(1f)) {
                            SearchDebugSource(hit.siteName, hit.address, emphasizeSite = true)
                        }
                        if (link != null) {
                            TextButton(
                                enabled = canFetch,
                                onClick = { onFetch(hit) },
                                contentPadding = PaddingValues(horizontal = 6.dp),
                            ) {
                                Text(stringResource(R.string.ai_search_debug_article), fontSize = 13.sp, maxLines = 1)
                                Icon(
                                    painterResource(R.drawable.ic_chevron_right_20), null,
                                    Modifier.padding(start = 4.dp).size(15.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = searchDebugHitTitle(hit),
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        color = Color(NgTheme.colors.primary),
                        fontWeight = FontWeight(650),
                        fontSize = 16.5.sp,
                        lineHeight = 22.sp,
                    )
                    if (hit.snippet.isNotBlank()) {
                        Text(
                            text = hit.snippet,
                            modifier = Modifier.padding(top = 5.dp),
                            color = Color(NgTheme.colors.onSurface),
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (date != null || link != null) {
                        HorizontalDivider(color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.35f))
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 43.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.weight(1f)) {
                                if (date != null) {
                                    Text(
                                        text = stringResource(R.string.ai_search_debug_published_date, date.toString()),
                                        color = Color(NgTheme.colors.onSurfaceVariant),
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                            if (link != null) {
                                TextButton(
                                    onClick = { context.openUrl(link) },
                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.ai_search_debug_original_link),
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.padding(start = 4.dp).size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    keyedHits.firstOrNull { it.first == summaryKey }?.second?.let { hit ->
        SearchDebugHitSummaryDialog(hit, onDismiss = { summaryKey = null })
    }
}

@Composable
private fun searchDebugHitTitle(hit: AiExternalSearchHit): String =
    hit.title.ifBlank { hit.address.ifBlank { stringResource(R.string.ai_search_debug_untitled) } }

@Composable
private fun SearchDebugHitSummaryDialog(hit: AiExternalSearchHit, onDismiss: () -> Unit) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.8f).dp
    NgPaneDialog(onDismissRequest = onDismiss) {
        NgDialog(
            title = searchDebugHitTitle(hit),
            modifier = Modifier.heightIn(max = maxHeight),
            titleFontSize = 18.sp,
            actions = {
                NgDialogTextActionButton(text = stringResource(R.string.close), onClick = onDismiss)
            },
        ) {
            Text(
                text = hit.summary.ifBlank { hit.snippet.ifBlank { stringResource(R.string.ai_search_debug_no_summary) } },
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 15.sp,
                lineHeight = 23.sp,
            )
        }
    }
}

@Composable
private fun FetchHistory(
    records: List<AiSearchFetchRecord>,
    loading: Boolean,
    listState: LazyListState,
    onOpen: (AiSearchFetchRecord) -> Unit,
    onShowResponse: (AiExternalSearchDebugResult<*>) -> Unit,
) {
    val label = when {
        loading -> stringResource(R.string.ai_chat_web_reading)
        records.isEmpty() -> stringResource(R.string.ai_search_debug_fetch_empty)
        else -> pluralStringResource(R.plurals.ai_search_debug_fetch_count, records.size, records.size)
    }
    SearchDebugSummary(label, records.firstOrNull()?.result, onShowResponse, loading)
    if (loading) return
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(records, key = { it.address }) { record ->
            val result = record.result
            val page = result?.data
            val failure = result?.error?.displayMessage(LocalContext.current)
            NgFormPanel {
                Column(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(record) }.padding(13.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = page?.source?.title?.takeUnless { it == record.address }
                                ?: record.titleHint.ifBlank {
                                    stringResource(when {
                                        failure != null -> R.string.ai_search_debug_failed
                                        page != null -> R.string.ai_search_debug_untitled
                                        else -> R.string.ai_chat_web_reading
                                    })
                                },
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            color = Color(NgTheme.colors.onSurface),
                            fontSize = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        SearchDebugFetchTags(result)
                        Icon(painterResource(R.drawable.ic_chevron_right_20), null, Modifier.size(16.dp))
                    }
                    Text(
                        record.address, color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val preview = failure ?: page?.text?.take(380)?.trimStart()
                    if (!preview.isNullOrBlank()) {
                        Text(
                            preview,
                            color = Color(if (failure == null) NgTheme.colors.onSurfaceVariant else NgTheme.colors.error),
                            fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchDebugSummary(
    label: String,
    result: AiExternalSearchDebugResult<*>?,
    onShowResponse: ((AiExternalSearchDebugResult<*>) -> Unit)?,
    loading: Boolean = false,
    isSearch: Boolean = false,
) {
    val textSize = 13.sp
    NgFormControlGroup(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(
                text = label,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                color = Color(if (result?.error != null && isSearch) NgTheme.colors.error else NgTheme.colors.onSurface),
                fontSize = textSize,
                fontWeight = if (!isSearch && result?.data != null) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (onShowResponse != null && result != null && !loading) {
                TextButton(
                    onClick = { onShowResponse(result) },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Text(stringResource(R.string.ai_search_debug_view_response), fontSize = textSize)
                }
            }
        }
    }
}

@Composable
internal fun SearchDebugSource(siteName: String, address: String, emphasizeSite: Boolean = false) {
    val host = address.toHttpUrlOrNull()?.host ?: if (emphasizeSite) "" else address
    val site = siteName.takeUnless { it.equals(host, ignoreCase = true) }.orEmpty()
    if (site.isBlank() && host.isBlank()) return
    val primary = site.ifBlank { host }
    val siteColor = Color(NgTheme.colors.onSurface)
    val label = buildAnnotatedString {
        if (emphasizeSite) {
            withStyle(SpanStyle(color = siteColor, fontWeight = FontWeight.SemiBold)) { append(primary) }
        } else {
            append(primary)
        }
        if (site.isNotBlank() && host.isNotBlank()) append(" · $host")
    }
    Text(label, color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun SearchDebugFetchTags(result: AiExternalSearchDebugResult<*>?) {
    if (result == null) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
    } else {
        val success = result.error == null
        NgStatusTag(searchDebugDuration(result.elapsedMs), variant = NgStatusTagVariant.NEUTRAL)
        NgStatusTag(
            text = stringResource(if (success) R.string.ai_search_test_success else R.string.ai_search_debug_failed),
            variant = if (success) NgStatusTagVariant.SUCCESS else NgStatusTagVariant.ERROR,
        )
    }
}

@Composable
private fun searchDebugDuration(ms: Long): String = if (ms < 1000) {
    stringResource(R.string.ai_search_debug_duration_ms, ms)
} else {
    stringResource(R.string.ai_search_debug_duration_seconds, ms / 1000.0)
}
