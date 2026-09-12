package io.legado.app.ui.book.read.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.theme.NgColorMath
import io.legado.app.ui.design.theme.NgTheme

internal data class AiPurifyPreviewUi(
    val deletedCount: String,
    val ruleCount: String,
    val elapsed: String,
    val model: String,
    val original: String,
    val cleaned: String,
    val deleted: String,
)

internal data class AiPurifyChapterSummaryUi(
    val originalCount: String,
    val cleanedCount: String,
    val elapsed: String,
    val model: String,
)

internal data class AiPurifyRuleUi(
    val hitCount: String,
    val type: String,
    val summary: String,
    val original: String,
    val cleaned: String,
    val deleted: String,
)

@Composable
internal fun AiPurifyProgressDialogContent(
    title: String,
    cancelLabel: String,
    onCancel: () -> Unit,
) {
    ReadConfigDialogSurface(
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
    ) {
        ReadConfigDialogTitle(title)
        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 24.dp)
                .size(42.dp),
            color = Color(NgTheme.colors.primary),
            trackColor = Color(NgTheme.colors.outline).copy(alpha = 0.42f),
        )
        NgFormActionButton(
            text = cancelLabel,
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp),
        )
    }
}

@Composable
internal fun AiPurifyPreviewDialogContent(
    title: String,
    preview: AiPurifyPreviewUi,
    originalLabel: String,
    cleanedLabel: String,
    deletedLabel: String,
    deletedCountLabel: String,
    ruleCountLabel: String,
    elapsedLabel: String,
    modelLabel: String,
    retryLabel: String,
    cancelLabel: String,
    applyLabel: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    ReadConfigDialogSurface(
        contentPadding = PaddingValues(
            start = 18.dp,
            top = 18.dp,
            end = 18.dp,
            bottom = 14.dp,
        ),
    ) {
        ReadConfigDialogTitle(title)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.66f).dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AiPurifyStat(deletedCountLabel, preview.deletedCount, Modifier.weight(1f))
                    AiPurifyStat(ruleCountLabel, preview.ruleCount, Modifier.weight(1f))
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AiPurifyStat(elapsedLabel, preview.elapsed, Modifier.weight(1f))
                    AiPurifyStat(modelLabel, preview.model, Modifier.weight(1f))
                }
            }
            item { AiPurifyTextPanel(originalLabel, preview.original) }
            item {
                AiPurifyTextPanel(
                    cleanedLabel,
                    preview.cleaned,
                    accent = colorResource(R.color.ng_success),
                )
            }
            item { AiPurifyTextPanel(deletedLabel, preview.deleted, accent = Color(NgTheme.colors.error)) }
        }
        AiPurifyActions(
            retryLabel = retryLabel,
            cancelLabel = cancelLabel,
            applyLabel = applyLabel,
            onRetry = onRetry,
            onCancel = onCancel,
            onApply = onApply,
        )
    }
}

@Composable
internal fun AiPurifyRangeDialogContent(
    title: String,
    currentChapterLabel: String,
    customRangeLabel: String,
    customSelected: Boolean,
    currentChapterHint: String,
    hint: String,
    startLabel: String,
    endLabel: String,
    start: String,
    end: String,
    cancelLabel: String,
    confirmLabel: String,
    onModeSelected: (Boolean) -> Unit,
    onStartChanged: (String) -> Unit,
    onEndChanged: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    ReadConfigDialogSurface(
        contentPadding = PaddingValues(
            start = 18.dp,
            top = 18.dp,
            end = 18.dp,
            bottom = 16.dp,
        ),
    ) {
        ReadConfigDialogTitle(title)
        ReadConfigDock(
            labels = listOf(currentChapterLabel, customRangeLabel),
            selectedIndex = if (customSelected) 1 else 0,
            onSelected = { onModeSelected(it == 1) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            height = 40.dp,
        )
        Text(
            text = if (customSelected) hint else currentChapterHint,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = 4.dp, end = 4.dp),
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        if (customSelected) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ReadDialogTextField(
                    value = start,
                    onValueChange = { onStartChanged(it.filter(Char::isDigit).take(5)) },
                    modifier = Modifier.weight(1f),
                    label = startLabel,
                    keyboardType = KeyboardType.Number,
                )
                ReadDialogTextField(
                    value = end,
                    onValueChange = { onEndChanged(it.filter(Char::isDigit).take(5)) },
                    modifier = Modifier.weight(1f),
                    label = endLabel,
                    keyboardType = KeyboardType.Number,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NgFormActionButton(
                text = cancelLabel,
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            NgFormActionButton(
                text = confirmLabel,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                variant = NgButtonVariant.PRIMARY,
            )
        }
    }
}

@Composable
internal fun AiPurifyChapterConfirmDialogContent(
    title: String,
    summary: AiPurifyChapterSummaryUi,
    rules: List<AiPurifyRuleUi>,
    originalCountLabel: String,
    cleanedCountLabel: String,
    ruleCountLabel: String,
    elapsedLabel: String,
    modelLabel: String,
    hitCountColumnLabel: String,
    typeColumnLabel: String,
    contentColumnLabel: String,
    selectAllLabel: String,
    clearSelectionLabel: String,
    retryLabel: String,
    cancelLabel: String,
    applyLabel: String,
    onRuleClick: (Int) -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onApply: (Set<Int>) -> Unit,
) {
    var selectedIndexes by remember(rules) {
        mutableStateOf(rules.indices.toSet())
    }
    AiPurifyResultDialogSurface(
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 18.dp,
            end = 16.dp,
            bottom = 14.dp,
        ),
    ) {
        ReadConfigDialogTitle(title)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.67f).dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AiPurifyStat(originalCountLabel, summary.originalCount, Modifier.weight(1f))
                    AiPurifyStat(cleanedCountLabel, summary.cleanedCount, Modifier.weight(1f))
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AiPurifyStat(
                        ruleCountLabel,
                        "${selectedIndexes.size}/${rules.size}",
                        Modifier.weight(1f),
                    )
                    AiPurifyStat(elapsedLabel, summary.elapsed, Modifier.weight(1f))
                }
            }
            item { AiPurifyStat(modelLabel, summary.model, Modifier.fillMaxWidth()) }
            item {
                AiPurifyRuleHeader(
                    selectionLabel = if (selectedIndexes.size == rules.size) {
                        clearSelectionLabel
                    } else {
                        selectAllLabel
                    },
                    hitCountColumnLabel = hitCountColumnLabel,
                    typeColumnLabel = typeColumnLabel,
                    contentColumnLabel = contentColumnLabel,
                    onToggleAll = {
                        selectedIndexes = if (selectedIndexes.size == rules.size) {
                            emptySet()
                        } else {
                            rules.indices.toSet()
                        }
                    },
                )
            }
            itemsIndexed(rules) { index, rule ->
                AiPurifyRuleRow(
                    rule = rule,
                    checked = index in selectedIndexes,
                    onCheckedChanged = { checked ->
                        selectedIndexes = selectedIndexes.toMutableSet().apply {
                            if (checked) add(index) else remove(index)
                        }
                    },
                    onClick = { onRuleClick(index) },
                )
            }
        }
        AiPurifyActions(
            retryLabel = retryLabel,
            cancelLabel = cancelLabel,
            applyLabel = applyLabel,
            onRetry = onRetry,
            onCancel = onCancel,
            onApply = { onApply(selectedIndexes) },
        )
    }
}

@Composable
internal fun AiPurifyRuleDetailDialogContent(
    title: String,
    originalLabel: String,
    cleanedLabel: String,
    deletedLabel: String,
    rule: AiPurifyRuleUi,
    closeLabel: String,
    onClose: () -> Unit,
) {
    AiPurifyResultDialogSurface(
        contentPadding = PaddingValues(20.dp),
    ) {
        ReadConfigDialogTitle(title)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.62f).dp),
            contentPadding = PaddingValues(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { AiPurifyTextPanel(originalLabel, rule.original) }
            item {
                AiPurifyTextPanel(
                    cleanedLabel,
                    rule.cleaned,
                    accent = colorResource(R.color.ng_success),
                )
            }
            item {
                AiPurifyTextPanel(
                    deletedLabel,
                    rule.deleted,
                    accent = Color(NgTheme.colors.error),
                )
            }
        }
        TextButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp)
                .height(40.dp),
            colors = ButtonDefaults.textButtonColors(
                contentColor = Color(NgTheme.colors.secondary),
            ),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) {
            Text(
                text = closeLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun AiPurifyResultDialogSurface(
    contentPadding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    val surface = Color(NgTheme.colors.surface)
    NgGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        style = NgGlassDefaults.style(containerAlpha = 1f).copy(
            containerTop = surface,
            containerBottom = surface,
            accentGlow = Color.Transparent,
            surfaceGloss = Color.Transparent,
        ),
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
private fun AiPurifyStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(NgTheme.colors.inputContainer))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            modifier = Modifier.padding(top = 4.dp),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AiPurifyTextPanel(
    label: String,
    text: String,
    accent: Color = Color(NgTheme.colors.onSurfaceVariant),
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(NgTheme.colors.inputContainer))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Color(NgTheme.colors.outline).copy(alpha = 0.62f))
            )
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 10.dp),
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Color(NgTheme.colors.outline).copy(alpha = 0.62f))
            )
        }
        SelectionContainer {
            Text(
                text = text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = Color(NgTheme.colors.onSurface),
                style = TextStyle(
                    fontFamily = NgTheme.fontFamily,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
            )
        }
    }
}

@Composable
private fun AiPurifyRuleHeader(
    selectionLabel: String,
    hitCountColumnLabel: String,
    typeColumnLabel: String,
    contentColumnLabel: String,
    onToggleAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(Color(NgTheme.colors.inputContainer))
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 28.dp)
                .clickable(role = Role.Button, onClick = onToggleAll)
                .padding(horizontal = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = selectionLabel,
                color = Color(NgTheme.colors.secondary),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        AiPurifyTableLabel(hitCountColumnLabel, Modifier.size(width = 48.dp, height = 28.dp))
        AiPurifyTableLabel(typeColumnLabel, Modifier.size(width = 52.dp, height = 28.dp))
        AiPurifyTableLabel(contentColumnLabel, Modifier.weight(1f))
    }
}

@Composable
private fun AiPurifyRuleRow(
    rule: AiPurifyRuleUi,
    checked: Boolean,
    onCheckedChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(NgTheme.colors.inputContainer))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChanged,
            modifier = Modifier.size(40.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = Color(NgTheme.colors.primary),
                uncheckedColor = Color(NgTheme.colors.onSurfaceVariant),
                checkmarkColor = Color(NgTheme.colors.onPrimary),
            ),
        )
        AiPurifyTableLabel(rule.hitCount, Modifier.size(width = 48.dp, height = 32.dp), false)
        AiPurifyTableLabel(rule.type, Modifier.size(width = 52.dp, height = 32.dp), false)
        AiPurifyTableLabel(rule.summary, Modifier.weight(1f), false, TextAlign.Start)
    }
}

@Composable
private fun AiPurifyTableLabel(
    text: String,
    modifier: Modifier,
    header: Boolean = true,
    textAlign: TextAlign = TextAlign.Center,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            color = Color(
                if (header) NgTheme.colors.onSurfaceVariant else NgTheme.colors.onSurface
            ),
            fontSize = if (header) 11.sp else 12.sp,
            fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AiPurifyActions(
    retryLabel: String,
    cancelLabel: String,
    applyLabel: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NgFormActionButton(
            text = retryLabel,
            onClick = onRetry,
            modifier = Modifier.weight(1f),
        )
        NgFormActionButton(
            text = cancelLabel,
            onClick = onCancel,
            modifier = Modifier.weight(1f),
        )
        NgFormActionButton(
            text = applyLabel,
            onClick = onApply,
            modifier = Modifier.weight(1f),
            variant = NgButtonVariant.PRIMARY,
        )
    }
}
