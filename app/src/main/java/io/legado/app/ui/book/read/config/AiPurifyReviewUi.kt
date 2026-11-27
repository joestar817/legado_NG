package io.legado.app.ui.book.read.config

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.book.character.readerCharacterHeaderAccentColor
import io.legado.app.ui.design.components.compose.NgCircularSelectionIndicator
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.components.compose.ngDrawerContentCardColor
import io.legado.app.ui.design.theme.NgTheme

private val RuleCheckboxWidth = 44.dp
private val RuleHitsWidth = 44.dp
private val RuleTypeWidth = 48.dp
private val RuleTablePadding = 10.dp

@Composable
internal fun AiPurifyChapterReviewDrawer(
    run: AiPurifyRunUi,
    selected: Set<String>,
    listState: LazyListState,
    onSelectionChange: (Set<String>) -> Unit,
    onRule: (AiPurifyRuleUi) -> Unit,
    onRetry: () -> Unit,
    onApply: () -> Unit,
) {
    val result = run.result ?: return
    val allSelected = selected.size == result.rules.size
    AiPurifyReviewDrawer(
        title = stringResource(R.string.ai_purify_chapter_confirm),
        onRetry = onRetry,
        onApply = onApply,
        applyEnabled = selected.isNotEmpty(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            state = listState,
        ) {
            item(key = "summary") {
                Column {
                    AiPurifySummaryCard(run, reviewing = true, selectedRuleCount = selected.size)
                    Spacer(Modifier.height(10.dp))
                }
            }
            item(key = "rule-header") {
                AiPurifyRuleTableHeader(
                    allSelected = allSelected,
                    onToggleAll = { onSelectionChange(if (allSelected) emptySet() else result.ruleKeys) },
                )
            }
            itemsIndexed(result.rules, key = { _, rule -> rule.key }) { index, rule ->
                AiPurifyRuleRow(
                    rule = rule,
                    checked = rule.key in selected,
                    last = index == result.rules.lastIndex,
                    onToggle = { checked ->
                        onSelectionChange(if (checked) selected + rule.key else selected - rule.key)
                    },
                    onClick = { onRule(rule) },
                )
            }
        }
    }
}

@Composable
private fun AiPurifyRuleTableHeader(allSelected: Boolean, onToggleAll: () -> Unit) {
    val radius = NgTheme.shapes.mediumDp.dp
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(topStart = radius, topEnd = radius))
            .background(ngDrawerContentCardColor())
            .padding(horizontal = RuleTablePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = RuleCheckboxWidth, height = 44.dp)
                .clickable(role = Role.Button, onClick = onToggleAll),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(if (allSelected) R.string.revert_selection else R.string.select_all),
                color = readerCharacterHeaderAccentColor(),
                fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            )
        }
        AiPurifyTableLabel(stringResource(R.string.ai_purify_rule_column_hit_count), Modifier.width(RuleHitsWidth), true)
        AiPurifyTableLabel(stringResource(R.string.ai_purify_rule_column_type), Modifier.width(RuleTypeWidth), true)
        AiPurifyTableLabel(
            stringResource(R.string.ai_purify_rule_column_content),
            Modifier.weight(1f).padding(start = 6.dp),
            true,
            TextAlign.Start,
        )
    }
}

@Composable
private fun AiPurifyRuleRow(
    rule: AiPurifyRuleUi,
    checked: Boolean,
    last: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.ai_purify_drawer_select_rule, rule.summary)
    val detailLabel = stringResource(R.string.ai_purify_rule_detail)
    val radius = NgTheme.shapes.mediumDp.dp
    val shape = if (last) RoundedCornerShape(bottomStart = radius, bottomEnd = radius) else RectangleShape
    Column(Modifier.fillMaxWidth().clip(shape).background(ngDrawerContentCardColor())) {
        NgFormGroupDivider(horizontalPadding = RuleTablePadding)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = RuleTablePadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NgCircularSelectionIndicator(
                state = if (checked) ToggleableState.On else ToggleableState.Off,
                modifier = Modifier.size(width = RuleCheckboxWidth, height = 50.dp)
                    .clip(CircleShape)
                    .toggleable(checked, role = Role.Checkbox, onValueChange = onToggle)
                    .semantics { contentDescription = label },
            )
            Row(
                Modifier.weight(1f).heightIn(min = 50.dp)
                    .clickable(role = Role.Button, onClickLabel = detailLabel, onClick = onClick),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AiPurifyTableLabel(
                    stringResource(R.string.ai_purify_rule_hit_count_value, rule.hitCount),
                    Modifier.width(RuleHitsWidth),
                    false,
                )
                AiPurifyTableLabel(rule.type, Modifier.width(RuleTypeWidth), false)
                AiPurifyTableLabel(
                    rule.summary,
                    Modifier.weight(1f).padding(start = 6.dp, end = 5.dp),
                    false,
                    TextAlign.Start,
                )
                AiPurifyChevron()
            }
        }
    }
}

@Composable
private fun AiPurifyTableLabel(
    text: String,
    modifier: Modifier,
    header: Boolean,
    align: TextAlign = TextAlign.Center,
) {
    Text(
        text, modifier, color = Color(NgTheme.colors.onSurface),
        fontSize = if (header) 12.sp else 13.sp, lineHeight = 18.sp,
        fontWeight = if (header) FontWeight.Medium else FontWeight.Normal,
        textAlign = align, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun AiPurifyParagraphReviewDrawer(
    run: AiPurifyRunUi,
    scrollState: ScrollState,
    onRetry: () -> Unit,
    onApply: () -> Unit,
) {
    val paragraph = run.result?.paragraph ?: return
    val theme = NgTheme.snapshot
    val removedColor = Color(theme.colors.error)
    val success = colorResource(R.color.ng_success)
    val addedColor = if (theme.isDark) lerp(success, Color.White, 0.52f) else success
    val omitted = SpanStyle(
        color = if (theme.isEInk) Color(theme.colors.onSurface) else removedColor,
        background = if (theme.isEInk) Color.Unspecified else removedColor.copy(alpha = 0.10f),
        textDecoration = TextDecoration.LineThrough,
    )
    val added = SpanStyle(
        color = if (theme.isEInk) Color(theme.colors.onSurface) else addedColor,
        background = if (theme.isEInk) Color.Unspecified else addedColor.copy(alpha = 0.12f),
        textDecoration = TextDecoration.Underline,
    )
    val original = remember(paragraph, omitted) {
        if (paragraph.diff.isEmpty()) AnnotatedString(paragraph.original)
        else annotateAiPurifyParagraph(paragraph.diff, omitted, AiPurifyDiffSpan::original)
    }
    val cleaned = remember(paragraph, added) {
        if (paragraph.diff.isEmpty()) AnnotatedString(paragraph.cleaned)
        else annotateAiPurifyParagraph(paragraph.diff, added, AiPurifyDiffSpan::cleaned)
    }
    val changes = remember(paragraph.diff) { paragraph.diff.filter(AiPurifyDiffSpan::changed) }
    val changeText = remember(changes, omitted, added) {
        buildAnnotatedString {
            changes.forEachIndexed { index, span ->
                if (index > 0) append(" · ")
                if (span.original.isNotEmpty()) {
                    withStyle(omitted) { append(span.original.visibleAiPurifyChange()) }
                }
                if (span.original.isNotEmpty() && span.cleaned.isNotEmpty()) append(" → ")
                if (span.cleaned.isNotEmpty()) {
                    withStyle(added) { append(span.cleaned.visibleAiPurifyChange()) }
                }
            }
        }
    }
    val counts = remember(changes) {
        listOf(
            changes.count { it.original.isNotEmpty() && it.cleaned.isNotEmpty() },
            changes.count { it.cleaned.isEmpty() },
            changes.count { it.original.isEmpty() },
        )
    }
    AiPurifyReviewDrawer(
        title = stringResource(R.string.ai_purify_drawer_compare),
        onRetry = onRetry,
        onApply = onApply,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AiPurifySummaryCard(run, reviewing = true)
            NgFormPanel {
                AiPurifyParagraphSection(stringResource(R.string.original_text), original)
                NgFormGroupDivider(horizontalPadding = 13.dp)
                AiPurifyParagraphSection(
                    stringResource(R.string.ai_purify_cleaned_text),
                    if (cleaned.text.isEmpty()) {
                        AnnotatedString(stringResource(R.string.ai_purify_rule_deleted_result))
                    } else cleaned,
                )
                if (changes.isNotEmpty()) {
                    val summary = listOf(
                        R.string.ai_purify_drawer_replacements,
                        R.string.ai_purify_drawer_removals,
                        R.string.ai_purify_drawer_additions,
                    ).mapIndexedNotNull { index, label ->
                        counts[index].takeIf { it > 0 }?.let { stringResource(label, it) }
                    }.joinToString(" · ")
                    NgFormGroupDivider(horizontalPadding = 13.dp)
                    AiPurifyParagraphSection(
                        stringResource(R.string.ai_purify_drawer_changes), changeText, summary,
                    )
                }
            }
        }
    }
}

private fun annotateAiPurifyParagraph(
    spans: List<AiPurifyDiffSpan>,
    style: SpanStyle,
    text: (AiPurifyDiffSpan) -> String,
): AnnotatedString = buildAnnotatedString {
    for (span in spans) {
        val value = text(span)
        if (span.changed) withStyle(style) { append(value) } else append(value)
    }
}

private fun String.visibleAiPurifyChange(): String {
    val escaped = replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t")
    return if (escaped.isBlank()) "“$escaped”" else escaped
}

@Composable
private fun AiPurifyParagraphSection(title: String, text: AnnotatedString, summary: String? = null) {
    val color = Color(NgTheme.colors.onSurface)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (summary != null) {
                Text(
                    summary, Modifier.weight(1f), color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 12.sp, textAlign = TextAlign.End,
                )
            }
        }
        SelectionContainer {
            Text(text, Modifier.fillMaxWidth(), color = color, fontSize = 15.sp, lineHeight = 23.sp)
        }
    }
}

@Composable
internal fun AiPurifyRuleDetailsDrawer(rule: AiPurifyRuleUi, onBack: () -> Unit) {
    val card = ngDrawerContentCardColor()
    AiPurifyDrawer(title = stringResource(R.string.ai_purify_rule_detail), onNavigate = onBack, back = true) {
        LazyColumn {
            item {
                NgFormPanel {
                    SelectionContainer {
                        Column {
                            AiPurifyRuleValue(stringResource(R.string.original_text), rule.original, removed = true)
                            NgFormGroupDivider()
                            AiPurifyRuleValue(
                                stringResource(R.string.ai_purify_cleaned_text),
                                rule.cleaned.ifEmpty { stringResource(R.string.ai_purify_rule_deleted_result) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(9.dp))
            }
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(card).padding(horizontal = 13.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.ai_purify_drawer_locations),
                        color = Color(NgTheme.colors.onSurface), fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    )
                    AiPurifyCaption(stringResource(R.string.ai_purify_drawer_hit_count, rule.hitCount))
                }
            }
            itemsIndexed(rule.hits, key = { _, hit -> "${hit.chapter}:${hit.paragraph}" }) { index, hit ->
                val modifier = if (index == rule.hits.lastIndex) {
                    Modifier.clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                } else Modifier
                Column(modifier.fillMaxWidth().background(card)) {
                    NgFormGroupDivider(horizontalPadding = 13.dp)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 43.dp).padding(horizontal = 13.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.ai_purify_drawer_hit_location, hit.chapter, hit.paragraph),
                            modifier = Modifier.weight(1f),
                            color = Color(NgTheme.colors.onSurface), fontSize = 14.sp,
                        )
                        if (hit.count > 1) {
                            AiPurifyCaption(stringResource(R.string.ai_purify_drawer_hit_count, hit.count))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiPurifyRuleValue(label: String, value: String, removed: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AiPurifyCaption(label)
        Text(
            value,
            color = Color(if (removed) NgTheme.colors.error else NgTheme.colors.onSurface),
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textDecoration = if (removed) TextDecoration.LineThrough else null,
        )
    }
}

@Composable
private fun AiPurifyReviewDrawer(
    title: String,
    onRetry: () -> Unit,
    onApply: () -> Unit,
    applyEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    AiPurifyDrawer(
        title = title,
        leading = {
            AiPurifyRoundAction(
                R.drawable.ic_refresh_black_24dp,
                stringResource(R.string.ai_purify_retry),
                onRetry,
            )
        },
        trailing = {
            AiPurifyRoundAction(
                R.drawable.ic_check,
                stringResource(R.string.ai_purify_apply),
                onApply,
                filled = true,
                enabled = applyEnabled,
            )
        },
        content = content,
    )
}
