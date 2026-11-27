package io.legado.app.ui.book.read.config

import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.book.character.readerCharacterHeaderAccentColor
import io.legado.app.ui.book.character.readerCharacterHeaderActionContainerColor
import io.legado.app.ui.config.NgThemeSheetActionButton
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.components.compose.NgLongDrawerHeaderVariant
import io.legado.app.ui.design.components.compose.NgSliderStepButton
import io.legado.app.ui.design.theme.NgSolidButtonPalette
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

internal data class AiPurifyRangeOptions(
    val currentChapter: Int,
    val chapterTitle: String?,
    val total: Int,
    val limit: Int,
)

internal data class AiPurifyRuleHit(val chapter: Int, val paragraph: Int, val count: Int)

internal data class AiPurifyRuleUi(
    val key: String,
    val type: String,
    val summary: String,
    val original: String,
    val cleaned: String,
    val hits: List<AiPurifyRuleHit>,
) {
    val hitCount = hits.sumOf { it.count }
}

internal data class AiPurifyParagraphUi(
    val original: String,
    val cleaned: String,
    val deletedCount: Int,
    val diff: List<AiPurifyDiffSpan>,
)

internal data class AiPurifyResultUi(
    val model: String?,
    val originalCount: Int,
    val cleanedCount: Int,
    val rules: List<AiPurifyRuleUi> = emptyList(),
    val paragraph: AiPurifyParagraphUi? = null,
    val autoApply: Boolean = false,
    val skippedChapters: Int = 0,
    val apply: suspend (Set<String>) -> Unit,
) {
    val ruleCount = if (paragraph == null) rules.size else if (paragraph.original != paragraph.cleaned) 1 else 0
    val ruleKeys = rules.mapTo(linkedSetOf()) { it.key }
}

internal data class AiPurifyRunUi(
    val title: String,
    val scope: String,
    val startedAtMs: Long,
    @StringRes val stage: Int,
    val configuredModel: String? = null,
    val elapsedMs: Long? = null,
    val result: AiPurifyResultUi? = null,
    val error: String? = null,
) {
    val running: Boolean get() = elapsedMs == null
}

@Composable
internal fun AiPurifyRangeDrawer(
    options: AiPurifyRangeOptions,
    onStart: (IntRange) -> Unit,
    onClose: () -> Unit,
) {
    var custom by rememberSaveable { mutableStateOf(false) }
    var startText by rememberSaveable { mutableStateOf(options.currentChapter.toString()) }
    var endText by rememberSaveable {
        mutableStateOf((options.currentChapter + options.limit - 1).coerceAtMost(options.total).toString())
    }
    val start = if (custom) startText.toIntOrNull() else options.currentChapter
    val end = if (custom) endText.toIntOrNull() else options.currentChapter
    val error = when {
        start == null || end == null || start > end -> stringResource(R.string.ai_purify_sample_range_invalid)
        start < 1 || end > options.total -> stringResource(R.string.ai_purify_sample_range_out_of_bounds, options.total)
        end - start + 1 > options.limit -> stringResource(R.string.ai_purify_sample_range_exceeded, options.limit)
        else -> null
    }
    val rangeMessage = when {
        !custom -> null
        error != null -> error
        start != null && end != null -> stringResource(
            R.string.ai_purify_drawer_range_count, end - start + 1, options.limit,
        )
        else -> null
    }
    val focus = LocalFocusManager.current
    AiPurifyDrawer(
        title = stringResource(R.string.ai_purify_chapter),
        leading = {
            AiPurifyRoundAction(R.drawable.ic_baseline_close, stringResource(R.string.cancel), onClose)
        },
        trailing = {
            AiPurifyHeaderPill(stringResource(R.string.start), enabled = error == null) {
                if (start != null && end != null && error == null) {
                    focus.clearFocus()
                    onStart((start - 1)..(end - 1))
                }
            }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            val current = stringResource(R.string.ai_purify_drawer_chapter, options.currentChapter)
            val description = options.chapterTitle?.takeIf { it.isNotBlank() }?.let {
                stringResource(R.string.ai_purify_drawer_current_chapter, current, it)
            } ?: current
            NgFormPanel {
                Column(Modifier.selectableGroup()) {
                    AiPurifyRangeOption(stringResource(R.string.ai_purify_sample_current_chapter), description, !custom) {
                        focus.clearFocus()
                        custom = false
                    }
                    NgFormGroupDivider()
                    AiPurifyRangeOption(
                        title = stringResource(R.string.ai_purify_drawer_custom_range),
                        subtitle = null,
                        selected = custom,
                        trailingText = rangeMessage,
                        trailingError = error != null,
                    ) {
                        custom = true
                    }
                }
                if (custom) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AiPurifyChapterRangeField(
                            label = stringResource(R.string.ai_purify_sample_range_start),
                            value = startText,
                            onValueChange = { startText = it },
                            chapterCount = options.total,
                            modifier = Modifier.weight(1f),
                        )
                        AiPurifyChapterRangeField(
                            label = stringResource(R.string.ai_purify_sample_range_end),
                            value = endText,
                            onValueChange = { endText = it },
                            chapterCount = options.total,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiPurifyRangeOption(
    title: String,
    subtitle: String?,
    selected: Boolean,
    trailingText: String? = null,
    trailingError: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.size(20.dp),
            colors = RadioButtonDefaults.colors(
                selectedColor = readerCharacterHeaderAccentColor(),
                unselectedColor = Color(NgTheme.colors.onSurfaceVariant),
            ),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Color(NgTheme.colors.onSurface), fontSize = 15.sp)
            subtitle?.let { AiPurifyCaption(it, maxLines = 2) }
        }
        if (trailingText != null) {
            Text(
                trailingText, Modifier.weight(1.4f),
                color = Color(if (trailingError) NgTheme.colors.error else NgTheme.colors.onSurfaceVariant),
                fontSize = 12.sp, lineHeight = 17.sp, textAlign = TextAlign.End, maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AiPurifyChapterRangeField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    chapterCount: Int,
    modifier: Modifier,
) {
    val chapter = value.toIntOrNull()?.takeIf { it in 1..chapterCount }
    val maxDigits = maxOf(5, chapterCount.toString().length)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = NgTheme.colors
    val accent = Color(colors.primary)
    val valueFill = accent.copy(alpha = if (NgTheme.snapshot.isDark) 0.24f else 0.12f)

    fun step(delta: Int) {
        val next = chapter?.plus(delta) ?: return
        if (next !in 1..chapterCount) return
        focus.clearFocus()
        keyboard?.hide()
        onValueChange(next.toString())
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color(colors.onSurface), fontSize = 14.sp, lineHeight = 20.sp)
        Row(
            Modifier.widthIn(max = 134.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NgSliderStepButton(
                iconRes = R.drawable.ic_chevron_left_20,
                contentDescription = "$label ${stringResource(R.string.reduce)}",
                enabled = chapter != null && chapter > 1,
                onClick = { step(-1) },
                modifier = Modifier.weight(1f),
                tint = accent,
            )
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.filter(Char::isDigit).take(maxDigits)) },
                modifier = Modifier.width(46.dp).height(44.dp).semantics { contentDescription = label },
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = NgTheme.fontFamily,
                    color = Color(colors.onSurface),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focus.clearFocus()
                    keyboard?.hide()
                }),
                cursorBrush = SolidColor(accent),
                decorationBox = { field ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(Modifier.fillMaxWidth().height(25.dp).background(valueFill, RoundedCornerShape(8.dp)))
                        field()
                    }
                },
            )
            NgSliderStepButton(
                iconRes = R.drawable.ic_chevron_right_20,
                contentDescription = "$label ${stringResource(R.string.plus)}",
                enabled = chapter != null && chapter < chapterCount,
                onClick = { step(1) },
                modifier = Modifier.weight(1f),
                tint = accent,
            )
        }
    }
}

@Composable
internal fun AiPurifyProgressDrawer(
    run: AiPurifyRunUi,
    onClose: () -> Unit,
    onDetails: () -> Unit,
    onRetry: () -> Unit,
) {
    AiPurifyDrawer(
        title = run.title,
        leading = {
            AiPurifyRoundAction(R.drawable.ic_baseline_close, stringResource(R.string.cancel), onClose)
        },
        trailing = {
            if (run.running) {
                AiPurifyHeaderPill(onClick = onDetails) {
                    AiPurifyElapsedTime(run, readerCharacterHeaderAccentColor())
                }
            } else {
                AiPurifyRoundAction(R.drawable.ic_refresh_black_24dp, stringResource(R.string.ai_purify_drawer_retry), onRetry)
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = 252.dp)
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (run.running) {
                    AiPurifyOrb()
                } else {
                    val accent = if (run.error != null) Color(NgTheme.colors.error)
                        else readerCharacterHeaderAccentColor()
                    Box(Modifier.size(144.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(106.dp).background(accent.copy(alpha = 0.08f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (run.error == null) {
                                Icon(painterResource(R.drawable.ic_check_circle_outline), null, Modifier.size(36.dp), accent)
                            } else {
                                Icon(Icons.Outlined.Warning, null, Modifier.size(36.dp), accent)
                            }
                        }
                    }
                }
                Text(
                    text = when {
                        run.running -> run.scope
                        run.error != null -> stringResource(R.string.ai_purify_drawer_failed)
                        else -> stringResource(R.string.ai_purify_drawer_no_changes)
                    },
                    modifier = Modifier.padding(top = 14.dp),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 26.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = when {
                        run.running -> stringResource(run.stage)
                        run.error != null -> run.error
                        else -> run.scope
                    },
                    modifier = Modifier.padding(top = 6.dp),
                    color = Color(if (run.error == null) NgTheme.colors.onSurfaceVariant else NgTheme.colors.error),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
            }
            NgFormGroupDivider(horizontalPadding = 0.dp)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable(role = Role.Button, onClick = onDetails)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AiPurifyIconTile(R.drawable.ic_ai_capability_text)
                Text(
                    stringResource(R.string.ai_purify_drawer_details),
                    modifier = Modifier.weight(1f), color = Color(NgTheme.colors.onSurface), fontSize = 15.sp,
                )
                AiPurifyCaption(stringResource(run.statusLabel()))
                AiPurifyChevron()
            }
        }
    }
}

@Composable
internal fun AiPurifyDetailsDrawer(
    run: AiPurifyRunUi,
    matchBookSource: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    AiPurifyDrawer(
        title = stringResource(R.string.ai_purify_drawer_details),
        onNavigate = onBack,
        back = true,
        trailing = if (run.running) null else {
            { AiPurifyRoundAction(R.drawable.ic_refresh_black_24dp, stringResource(R.string.ai_purify_drawer_retry), onRetry) }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            AiPurifySummaryCard(run, matchBookSource = matchBookSource)
        }
    }
}

@Composable
internal fun AiPurifySummaryCard(
    run: AiPurifyRunUi,
    matchBookSource: Boolean = false,
    reviewing: Boolean = false,
    selectedRuleCount: Int? = null,
) {
    val title = if (reviewing && run.result?.paragraph != null) {
        stringResource(R.string.ai_purify_drawer_selected_paragraph)
    } else run.scope
    NgFormPanel {
        Row(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title, color = Color(NgTheme.colors.onSurface), fontSize = 18.sp,
                    fontWeight = if (reviewing) FontWeight.SemiBold else FontWeight.Medium,
                    lineHeight = 22.sp,
                )
                if (!reviewing) AiPurifyCaption(run.title)
            }
            Text(stringResource(run.statusLabel()), color = readerCharacterHeaderAccentColor(), fontSize = 12.sp)
            AiPurifyElapsedTime(run)
        }
        NgFormGroupDivider()
        AiPurifyInfoRow(
            R.drawable.ic_ai_capability_tool, stringResource(R.string.ai_purify_model),
            run.result?.model ?: run.configuredModel ?: stringResource(R.string.ai_purify_model_unknown),
        )
        run.result?.let { result ->
            val numbers = NumberFormat.getIntegerInstance()
            AiPurifyInfoRow(
                R.drawable.ic_bottom_books_e, stringResource(R.string.ai_purify_drawer_length),
                stringResource(
                    R.string.ai_purify_drawer_length_change,
                    numbers.format(result.originalCount), numbers.format(result.cleanedCount),
                ),
            )
            result.paragraph?.let {
                AiPurifyInfoRow(
                    R.drawable.ic_ai_capability_text, stringResource(R.string.ai_purify_chapter_deleted_count),
                    stringResource(R.string.ai_purify_drawer_character_count, numbers.format(it.deletedCount)),
                )
            }
            val ruleCount = if (selectedRuleCount == null) {
                pluralStringResource(R.plurals.ai_purify_drawer_rule_count, result.ruleCount, result.ruleCount)
            } else {
                stringResource(R.string.ai_purify_drawer_selected_rules, selectedRuleCount, result.ruleCount)
            }
            AiPurifyInfoRow(
                R.drawable.ic_check_circle_outline, stringResource(R.string.ai_purify_drawer_rules), ruleCount,
            )
            if (!reviewing && result.ruleCount > 0) {
                AiPurifyInfoRow(
                    R.drawable.ic_ai_locate_selected, stringResource(R.string.ai_purify_drawer_scope),
                    stringResource(
                        if (matchBookSource) R.string.ai_purify_drawer_scope_source
                        else R.string.ai_purify_drawer_scope_book,
                    ),
                )
            }
            if (result.skippedChapters > 0) {
                AiPurifyCaption(
                    stringResource(R.string.ai_purify_drawer_skipped, result.skippedChapters),
                    Modifier.padding(13.dp),
                )
            }
        }
        run.error?.let {
            Text(
                it, Modifier.padding(13.dp), color = Color(NgTheme.colors.error),
                fontSize = 13.sp, lineHeight = 18.sp,
            )
        }
    }
}

@StringRes
private fun AiPurifyRunUi.statusLabel(): Int = when {
    running -> R.string.ai_purify_drawer_in_progress
    error != null -> R.string.ai_purify_drawer_incomplete
    else -> R.string.ai_purify_drawer_completed
}

@Composable
private fun AiPurifyElapsedTime(run: AiPurifyRunUi, foreground: Color = Color(NgTheme.colors.onSurface)) {
    val elapsed = run.elapsedMs ?: kotlin.run {
        val live by produceState(SystemClock.elapsedRealtime() - run.startedAtMs, run.startedAtMs) {
            while (true) {
                val current = (SystemClock.elapsedRealtime() - run.startedAtMs).coerceAtLeast(0)
                value = current
                delay(1_000 - current % 1_000)
            }
        }
        live
    }
    val seconds = elapsed / 1_000
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(R.drawable.ic_ai_character_card_clock_thin),
            stringResource(R.string.ai_purify_elapsed), Modifier.size(15.dp), foreground,
        )
        Text(
            String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60),
            color = foreground, fontSize = 13.sp, maxLines = 1, softWrap = false,
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
        )
    }
}

@Composable
internal fun AiPurifyDrawer(
    title: String,
    onNavigate: (() -> Unit)? = null,
    back: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.91f).dp
    NgBottomDrawerSurface(
        modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight),
        contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            NgLongDrawerHeader(
                title = title, centerTitle = true, titleFontWeight = FontWeight.Normal,
                variant = NgLongDrawerHeaderVariant.ROUND_ACTIONS_TRAILING_GROUP,
                centeredTrailingContent = trailing,
                navigationContent = leading ?: if (onNavigate == null) null else {
                    {
                        AiPurifyRoundAction(
                            if (back) R.drawable.ic_chevron_left_20 else R.drawable.ic_baseline_close,
                            stringResource(if (back) R.string.back else R.string.cancel),
                            onNavigate,
                            accented = false,
                        )
                    }
                },
            )
            Box(Modifier.weight(1f, fill = false).fillMaxWidth().padding(top = 5.dp)) { content() }
        }
    }
}

@Composable
internal fun AiPurifyRoundAction(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    accented: Boolean = true,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    val container = if (filled) {
        val theme = NgTheme.snapshot
        val primary = theme.colors.primary
        remember(primary, theme.isDark) {
            Color(NgSolidButtonPalette.resolveContainer(primary, Color.White.toArgb(), theme.isDark))
        }
    } else {
        readerCharacterHeaderActionContainerColor()
    }
    val tint = when {
        filled -> Color.White
        accented -> readerCharacterHeaderAccentColor()
        else -> Color(NgTheme.colors.onSurface)
    }
    NgThemeSheetActionButton(
        onClick, label, enabled = enabled, containerColor = container,
    ) {
        Icon(
            painterResource(icon), null, Modifier.size(19.dp),
            tint = if (enabled) tint else tint.copy(alpha = 0.65f),
        )
    }
}

@Composable
internal fun AiPurifyHeaderPill(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    AiPurifyHeaderPill(enabled, onClick) {
        Text(
            text, color = readerCharacterHeaderAccentColor().copy(alpha = if (enabled) 1f else 0.45f),
            fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AiPurifyHeaderPill(
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val color = readerCharacterHeaderActionContainerColor()
    Box(
        Modifier.heightIn(min = 44.dp).clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.heightIn(min = 34.dp)
                .background(color.copy(alpha = color.alpha * if (enabled) 1f else 0.45f), CircleShape)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Composable
internal fun AiPurifyCaption(text: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    Text(
        text = text, modifier = modifier, color = Color(NgTheme.colors.onSurfaceVariant),
        fontSize = 12.sp, lineHeight = 17.sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun AiPurifyChevron() {
    Icon(
        painterResource(R.drawable.ic_chevron_right_20), null, Modifier.size(16.dp),
        Color(NgTheme.colors.onSurfaceVariant),
    )
}

@Composable
private fun AiPurifyInfoRow(@DrawableRes icon: Int, title: String, value: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 42.dp).padding(horizontal = 13.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AiPurifyIconTile(icon)
        Text(title, color = Color(NgTheme.colors.onSurface), fontSize = 13.sp, maxLines = 2)
        Text(
            value, Modifier.weight(1f), color = Color(NgTheme.colors.onSurface),
            textAlign = TextAlign.End, fontSize = 13.sp, lineHeight = 17.sp,
        )
    }
}

@Composable
private fun AiPurifyIconTile(@DrawableRes icon: Int) {
    val accent = readerCharacterHeaderAccentColor()
    Box(
        Modifier.size(27.dp).background(accent.copy(alpha = 0.09f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, Modifier.size(16.dp), accent)
    }
}
