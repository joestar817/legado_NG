package io.legado.app.ui.book.read

import android.net.Uri
import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.BookCharacter
import io.legado.app.help.ai.AiCharacterCardProgress
import io.legado.app.help.ai.AiCharacterCardTool
import io.legado.app.help.ai.runtime.ToolExecutionStatus
import io.legado.app.ui.book.character.BookCharacterDrawer
import io.legado.app.ui.book.character.BookCharacterDrawerVariant
import io.legado.app.ui.book.character.BookCharacterPortraitImage
import io.legado.app.ui.book.character.BookCharacterSilhouetteStyle
import io.legado.app.ui.book.character.forRole
import io.legado.app.ui.book.character.readerCharacterHeaderAccentColor
import io.legado.app.ui.book.character.readerCharacterHeaderActionContainerColor
import io.legado.app.ui.book.character.rememberBookCharacterSilhouetteStyle
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun ReaderCharacterCardGeneration(
    name: String,
    progress: AiCharacterCardProgress,
    error: String?,
    timing: ReaderCharacterCardTiming?,
    onDetails: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    automaticSilhouetteStyle: BookCharacterSilhouetteStyle,
    stageLabel: String? = null,
    discoverCandidates: Boolean = false,
    batchStep: Pair<Int, Int>? = null,
    portraitName: String = name,
    portraitCharacter: BookCharacter? = null,
) {
    val runningTool = progress.runningTool
    val stage = when {
        error != null -> error
        stageLabel != null -> stageLabel
        runningTool != null && readerCharacterToolType(runningTool) == ReaderCharacterToolType.READ ->
            stringResource(R.string.ai_character_card_stage_reading)
        runningTool != null -> stringResource(R.string.ai_character_card_stage_tool, toolLabel(runningTool))
        progress.tools.isEmpty() -> stringResource(R.string.ai_character_card_stage_locating)
        else -> stringResource(R.string.ai_character_card_stage_waiting)
    }
    val targetHeight = (LocalConfiguration.current.screenHeightDp * 0.46f).dp.coerceAtMost(400.dp)
    val stageHeight = (targetHeight - 126.dp).coerceAtLeast(144.dp)
    BookCharacterDrawer(
        title = stringResource(if (error == null) R.string.ai_character_card_reading else R.string.ai_character_card_failed),
        onClose = onCancel,
        onRetry = onRetry.takeIf { error != null },
        variant = BookCharacterDrawerVariant.READER_AI,
        navigationAccented = true,
        headerTrailingContent = if (error == null && timing != null) {
            {
                CharacterCardElapsedTime(
                    timing,
                    modifier = Modifier.padding(end = 4.dp)
                        .heightIn(min = 30.dp)
                        .background(readerCharacterHeaderActionContainerColor(), CircleShape)
                        .padding(horizontal = 8.dp),
                    foreground = readerCharacterHeaderAccentColor(),
                )
            }
        } else null,
    ) {
        val colors = NgTheme.colors
        Column(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = stageHeight)
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CharacterCardGenerationPortrait(
                    running = error == null,
                    discoverCandidates = discoverCandidates,
                    name = portraitName,
                    character = portraitCharacter,
                    automaticSilhouetteStyle = automaticSilhouetteStyle,
                )
                Text(
                    text = name,
                    modifier = Modifier.padding(top = 10.dp),
                    color = Color(colors.onSurface),
                    fontSize = 21.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
                AnimatedContent(
                    targetState = stage,
                    modifier = Modifier.padding(top = 4.dp),
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(110)) },
                    label = "character card generation stage",
                ) { message ->
                    Text(
                        text = message,
                        color = Color(if (error != null) colors.error else colors.onSurfaceVariant),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                if (batchStep != null) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        repeat(batchStep.second) { index ->
                            Box(Modifier.width(12.dp).height(2.dp).background(
                                Color(colors.primary).copy(alpha = if (index < batchStep.first) 1f else 0.25f),
                                CircleShape,
                            ))
                        }
                    }
                }
            }
            NgFormGroupDivider(horizontalPadding = 0.dp)
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .clickable(role = Role.Button, onClick = onDetails)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CharacterCardIconTile(R.drawable.ic_ai_capability_tool, size = 32.dp)
                Text(
                    stringResource(R.string.ai_character_card_tools),
                    modifier = Modifier.weight(1f),
                    color = Color(colors.onSurface),
                    fontSize = 15.sp,
                )
                Text(
                    stringResource(R.string.ai_character_card_tool_count, progress.tools.size),
                    color = Color(colors.onSurfaceVariant),
                    fontSize = 13.sp,
                )
                Icon(
                    painterResource(R.drawable.ic_chevron_right_20),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color(colors.onSurfaceVariant),
                )
            }
        }
    }
}

@Composable
private fun CharacterCardElapsedTime(
    timing: ReaderCharacterCardTiming,
    modifier: Modifier = Modifier,
    @DrawableRes clockIcon: Int = R.drawable.ic_mingcute_time_line,
    fontWeight: FontWeight = FontWeight.Medium,
    foreground: Color = Color(NgTheme.colors.onSurfaceVariant),
) {
    val elapsed = characterCardElapsedTimeText(timing)
    val elapsedDescription = stringResource(R.string.ai_character_card_elapsed_time, elapsed)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = elapsedDescription },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(clockIcon),
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = foreground,
        )
        Text(
            text = elapsed,
            color = foreground,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = fontWeight,
            maxLines = 1,
            softWrap = false,
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
        )
    }
}

@Composable
private fun characterCardElapsedTimeText(timing: ReaderCharacterCardTiming): String {
    val elapsedMs = timing.totalElapsedMs ?: run {
        val liveElapsedMs by produceState(timing.elapsedMillis(SystemClock.elapsedRealtime()), timing) {
            while (true) {
                val elapsed = timing.elapsedMillis(SystemClock.elapsedRealtime())
                value = elapsed
                delay(1_000 - elapsed % 1_000)
            }
        }
        liveElapsedMs
    }
    val elapsedSeconds = elapsedMs / 1_000
    return "%02d:%02d".format(Locale.ROOT, elapsedSeconds / 60, elapsedSeconds % 60)
}

@Composable
private fun CharacterCardGenerationPortrait(
    running: Boolean,
    discoverCandidates: Boolean,
    name: String,
    character: BookCharacter?,
    automaticSilhouetteStyle: BookCharacterSilhouetteStyle,
) {
    val accent = Color(NgTheme.colors.primary)
    val transition = if (running && !NgTheme.snapshot.isEInk) {
        rememberInfiniteTransition(label = "character card generation portrait")
    } else null
    val rotation = transition?.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4_500, easing = LinearEasing)),
        label = "character portrait orbit",
    )
    val avatarModifier = Modifier.size(106.dp).clip(CircleShape)
    Box(Modifier.size(146.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f - 2.dp.toPx()
            drawCircle(accent.copy(alpha = 0.14f), radius, style = Stroke(1.dp.toPx()))
            drawCircle(accent.copy(alpha = 0.12f), radius - 11.dp.toPx(), style = Stroke(1.dp.toPx()))
            if (running) {
                drawArc(
                    color = accent.copy(alpha = 0.62f),
                    startAngle = (rotation?.value ?: 0f) - 90f,
                    sweepAngle = 64f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        if (discoverCandidates) {
            Box(
                modifier = avatarModifier.background(accent.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_search), null, Modifier.size(35.dp), accent)
            }
        } else {
            val portrait = character?.portraitUri?.takeIf(String::isNotBlank)?.let(Uri::parse)
            val roleTag = character?.roleTag ?: BookCharacter.RoleTag.UNKNOWN
            if (portrait != null || roleTag != BookCharacter.RoleTag.UNKNOWN) {
                val style = rememberBookCharacterSilhouetteStyle().resolve(automaticSilhouetteStyle)
                BookCharacterPortraitImage(
                    portrait = portrait,
                    silhouette = style.forRole(roleTag, hasPortrait = portrait != null),
                    contentDescription = null,
                    modifier = avatarModifier.border(1.dp, Color(NgTheme.colors.surface).copy(alpha = 0.8f), CircleShape),
                )
            } else {
                val initial = name.trim().takeIf(String::isNotEmpty)
                    ?.let { it.substring(0, it.offsetByCodePoints(0, 1)) }.orEmpty()
                Box(
                    modifier = avatarModifier.background(accent.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initial,
                        modifier = Modifier.clearAndSetSemantics {},
                        color = accent,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ReaderCharacterCardDetails(
    name: String,
    chapterIndex: Int,
    chapterTitle: String?,
    modelName: String,
    providerName: String,
    progress: AiCharacterCardProgress,
    timing: ReaderCharacterCardTiming?,
    complete: Boolean,
    interrupted: Boolean,
    onBack: () -> Unit,
    onRetry: (() -> Unit)?,
    scannedChapter: Int? = null,
) {
    val tools = progress.tools
    val finished = complete || interrupted
    val groups = remember(tools, finished) { summarizeReaderCharacterCardTools(tools, finished) }
    val separator = stringResource(R.string.ai_character_card_chapter_separator)
    val ranges = remember(tools, separator, scannedChapter) {
        chapterRanges(listOfNotNull(scannedChapter) + tools.flatMap { it.chapters }, separator)
    }
    val currentChapter = chapterTitle?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.ai_character_card_chapter_ranges, (chapterIndex + 1).toString())
    val overallStatus = when {
        complete -> ReaderCharacterTraceStatus.DONE
        interrupted -> ReaderCharacterTraceStatus.INTERRUPTED
        else -> ReaderCharacterTraceStatus.RUNNING
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    BookCharacterDrawer(
        title = stringResource(R.string.ai_character_card_details),
        onBack = onBack,
        onRetry = onRetry,
        variant = BookCharacterDrawerVariant.READER_AI,
        maxHeightFraction = 0.84f,
    ) {
        val colors = NgTheme.colors
        NgFormPanel {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    name,
                    modifier = Modifier.weight(1f),
                    color = Color(colors.onSurface),
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CharacterCardStatus(overallStatus, summary = true)
                    if (timing != null) {
                        CharacterCardElapsedTime(
                            timing = timing,
                            clockIcon = R.drawable.ic_ai_character_card_clock_thin,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                }
            }
            NgFormGroupDivider(horizontalPadding = 14.dp)
            Column(Modifier.padding(horizontal = 14.dp, vertical = 5.dp)) {
                CharacterCardSummaryRow(
                    icon = R.drawable.ic_ai_locate_selected,
                    label = stringResource(R.string.ai_character_card_current_chapter),
                    value = currentChapter,
                )
                CharacterCardSummaryRow(
                    icon = R.drawable.ic_ai_tab_models,
                    label = stringResource(R.string.ai_purify_model),
                    value = modelName,
                    secondary = providerName,
                )
                CharacterCardSummaryRow(
                    icon = R.drawable.ic_bookshelf_dock_novel,
                    label = stringResource(R.string.ai_character_card_read_chapters),
                    value = ranges.ifEmpty { stringResource(R.string.ai_character_card_no_read_chapters) },
                )
                CharacterCardTokenSummary(progress)
            }
        }
        NgFormPanel {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 49.dp)
                    .padding(start = 14.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.ai_character_card_trace),
                        modifier = Modifier.weight(1f, fill = false),
                        color = Color(colors.onSurface),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        stringResource(R.string.ai_character_card_tool_count, tools.size),
                        color = Color(colors.onSurfaceVariant),
                        fontSize = 12.sp,
                    )
                }
                if (tools.isNotEmpty()) {
                    NgFormActionButton(
                        text = stringResource(
                            if (expanded) R.string.ai_character_card_trace_collapse
                            else R.string.ai_character_card_trace_expand,
                        ),
                        onClick = { expanded = !expanded },
                        appearance = NgFormActionButtonAppearance.TEXT,
                        minimumWidth = 0.dp,
                        buttonHeight = 44.dp,
                        textSize = 13.sp,
                        trailingIcon = {
                            Icon(
                                painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                        },
                    )
                }
            }
            when {
                tools.isEmpty() -> {
                    NgFormGroupDivider(horizontalPadding = 14.dp)
                    Text(
                        stringResource(R.string.ai_character_card_no_tools),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 15.dp),
                        color = Color(colors.onSurfaceVariant),
                        fontSize = 13.sp,
                    )
                }
                expanded -> tools.forEach { tool ->
                    val type = readerCharacterToolType(tool.name)
                    val chapters = chapterRanges(tool.chapters, separator)
                    CharacterCardTraceRow(
                        title = toolLabel(tool.name),
                        icon = type.icon,
                        subtitle = if (chapters.isEmpty()) null
                            else stringResource(R.string.ai_character_card_chapter_ranges, chapters),
                        status = traceStatus(tool.status, finished),
                    )
                }
                else -> groups.forEach { group ->
                    CharacterCardTraceRow(
                        title = stringResource(group.type.title),
                        icon = group.type.icon,
                        status = group.status,
                        count = group.count,
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterCardTokenSummary(progress: AiCharacterCardProgress) {
    val formatter = NumberFormat.getIntegerInstance()
    CharacterCardSummaryRow(
        icon = R.drawable.ic_solar_chart_bold,
        label = stringResource(R.string.ai_character_card_token_usage),
        value = stringResource(
            R.string.ai_character_card_token_usage_values,
            formatter.tokenCount(progress.usage?.promptTokens, progress.missingPromptTokens),
            formatter.tokenCount(progress.usage?.completionTokens, progress.missingCompletionTokens),
        ),
        valueStyle = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
    )
}

private fun NumberFormat.tokenCount(count: Int?, incomplete: Boolean): String {
    val value = count?.let { format(it.toLong()) } ?: return "—"
    return if (incomplete) "≥$value" else value
}

@Composable
private fun CharacterCardSummaryRow(
    @DrawableRes icon: Int,
    label: String,
    value: String,
    secondary: String? = null,
    valueStyle: TextStyle = LocalTextStyle.current,
) {
    val colors = NgTheme.colors
    val valueText = remember(value, secondary, colors.onSurfaceVariant) {
        buildAnnotatedString {
            append(value.replace('\r', ' ').replace('\n', ' '))
            if (!secondary.isNullOrBlank()) {
                withStyle(SpanStyle(color = Color(colors.onSurfaceVariant), fontSize = 11.sp)) {
                    append(" · ")
                    append(secondary.replace('\r', ' ').replace('\n', ' '))
                }
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 37.dp).padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CharacterCardIconTile(icon, size = 26.dp, iconSize = 16.dp)
        Text(label, color = Color(colors.onSurfaceVariant), fontSize = 12.sp, lineHeight = 17.sp)
        Box(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                valueText,
                color = Color(colors.onSurface),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.sp,
                maxLines = 1,
                softWrap = false,
                style = valueStyle,
            )
        }
    }
}

@Composable
private fun CharacterCardTraceRow(
    title: String,
    @DrawableRes icon: Int,
    status: ReaderCharacterTraceStatus,
    count: Int? = null,
    subtitle: String? = null,
) {
    val colors = NgTheme.colors
    NgFormGroupDivider(horizontalPadding = 14.dp)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = if (count == null) 46.dp else 58.dp)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CharacterCardIconTile(icon, size = 29.dp, filled = count != null)
        Column(Modifier.weight(1f)) {
            Text(title, color = Color(colors.onSurface), fontSize = 14.sp, lineHeight = 20.sp)
            if (subtitle != null) {
                Text(subtitle, color = Color(colors.onSurfaceVariant), fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        if (count != null && count > 1) {
            Text(
                stringResource(R.string.ai_character_card_tool_count, count),
                color = Color(colors.onSurfaceVariant),
                fontSize = 12.sp,
            )
        }
        CharacterCardStatus(status)
    }
}

@Composable
private fun CharacterCardIconTile(
    @DrawableRes icon: Int,
    size: Dp,
    iconSize: Dp = 18.dp,
    filled: Boolean = true,
) {
    val accent = Color(NgTheme.colors.primary)
    val shape = RoundedCornerShape(NgTheme.shapes.smallDp.dp)
    Box(
        modifier = Modifier.size(size)
            .background(if (filled) accent.copy(alpha = 0.09f) else Color.Transparent, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(iconSize), tint = accent)
    }
}

@Composable
private fun CharacterCardStatus(
    status: ReaderCharacterTraceStatus,
    modifier: Modifier = Modifier,
    summary: Boolean = false,
) {
    val label = stringResource(status.label)
    val colors = NgTheme.colors
    val tint = Color(when (status) {
        ReaderCharacterTraceStatus.DONE, ReaderCharacterTraceStatus.RUNNING -> colors.primary
        ReaderCharacterTraceStatus.FAILED -> colors.error
        ReaderCharacterTraceStatus.INTERRUPTED -> colors.onSurfaceVariant
    })
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconSize = Modifier.size(16.dp)
        when (status) {
            ReaderCharacterTraceStatus.DONE -> Icon(
                painterResource(if (summary) R.drawable.ic_check_circle_outline else R.drawable.ic_check),
                contentDescription = if (summary) null else label,
                modifier = iconSize,
                tint = tint,
            )
            ReaderCharacterTraceStatus.RUNNING ->
                CircularProgressIndicator(Modifier.size(14.dp), color = tint, strokeWidth = 1.6.dp)
            ReaderCharacterTraceStatus.FAILED ->
                Icon(Icons.Outlined.Warning, contentDescription = null, modifier = iconSize, tint = tint)
            ReaderCharacterTraceStatus.INTERRUPTED ->
                Icon(painterResource(R.drawable.ic_baseline_close), contentDescription = null, modifier = iconSize, tint = tint)
        }
        if (summary || status != ReaderCharacterTraceStatus.DONE) {
            Text(label, color = tint, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

internal enum class ReaderCharacterTraceGroup(
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
) {
    PREPARATION(R.string.ai_character_card_group_preparation, R.drawable.ic_ai_skill_puzzle),
    BOOK(R.string.ai_character_card_tool_book, R.drawable.ic_bookshelf_dock_novel),
    CHARACTERS(R.string.ai_character_card_group_characters, R.drawable.ic_read_menu_characters),
    SEARCH(R.string.ai_character_card_tool_find, R.drawable.ic_search),
    CHAPTERS(R.string.ai_character_card_tool_read, R.drawable.ic_bookshelf_dock_novel),
    OTHER(R.string.ai_character_card_group_other, R.drawable.ic_ai_capability_tool),
}

private enum class ReaderCharacterToolType(
    val group: ReaderCharacterTraceGroup,
    @StringRes val title: Int?,
    @DrawableRes val icon: Int = group.icon,
) {
    SKILL(ReaderCharacterTraceGroup.PREPARATION, R.string.ai_character_card_tool_skill),
    CACHE(ReaderCharacterTraceGroup.PREPARATION, R.string.ai_character_card_tool_cache, R.drawable.ic_bookshelf_manage_cache),
    BOOK(ReaderCharacterTraceGroup.BOOK, R.string.ai_character_card_tool_book),
    CARDS(ReaderCharacterTraceGroup.CHARACTERS, R.string.ai_character_card_tool_existing),
    MEMORY(ReaderCharacterTraceGroup.CHARACTERS, R.string.ai_character_card_tool_memory, R.drawable.ic_history),
    SEARCH(ReaderCharacterTraceGroup.SEARCH, R.string.ai_character_card_tool_find),
    READ(ReaderCharacterTraceGroup.CHAPTERS, R.string.ai_character_card_tool_read),
    OTHER(ReaderCharacterTraceGroup.OTHER, null),
}

private fun readerCharacterToolType(tool: String): ReaderCharacterToolType = when (tool) {
    "use_skill" -> ReaderCharacterToolType.SKILL
    "bookshelf_text_search" -> ReaderCharacterToolType.SEARCH
    "bookshelf_text_window_get", "bookshelf_chapter_content_get", "bookshelf_chapter_snippets_get" -> ReaderCharacterToolType.READ
    "bookshelf_cache_status", "bookshelf_cache_status_get" -> ReaderCharacterToolType.CACHE
    "bookshelf_character_list" -> ReaderCharacterToolType.CARDS
    "bookshelf_book_get", "bookshelf_current_book_get", "bookshelf_chapter_list" -> ReaderCharacterToolType.BOOK
    else -> if (tool.startsWith("agent_memory_")) ReaderCharacterToolType.MEMORY else ReaderCharacterToolType.OTHER
}

@Composable
private fun toolLabel(tool: String): String =
    readerCharacterToolType(tool).title?.let { stringResource(it) } ?: tool

internal enum class ReaderCharacterTraceStatus(@StringRes val label: Int) {
    DONE(R.string.ai_character_card_status_done),
    RUNNING(R.string.ai_character_card_status_running),
    INTERRUPTED(R.string.ai_character_card_status_interrupted),
    FAILED(R.string.ai_character_card_status_failed),
}

private fun traceStatus(status: ToolExecutionStatus?, finished: Boolean) = when (status) {
    ToolExecutionStatus.SUCCEEDED -> ReaderCharacterTraceStatus.DONE
    ToolExecutionStatus.FAILED -> ReaderCharacterTraceStatus.FAILED
    ToolExecutionStatus.CANCELED -> ReaderCharacterTraceStatus.INTERRUPTED
    null -> if (finished) ReaderCharacterTraceStatus.INTERRUPTED else ReaderCharacterTraceStatus.RUNNING
}

internal data class ReaderCharacterToolSummary(
    val type: ReaderCharacterTraceGroup,
    val count: Int,
    val status: ReaderCharacterTraceStatus,
)

internal fun summarizeReaderCharacterCardTools(
    tools: List<AiCharacterCardTool>,
    finished: Boolean,
): List<ReaderCharacterToolSummary> {
    val toolsByGroup = tools.groupBy { readerCharacterToolType(it.name).group }
    return ReaderCharacterTraceGroup.entries.mapNotNull { group ->
        val members = toolsByGroup[group] ?: return@mapNotNull null
        // A failed call stays visible even if a later call in the same group succeeds.
        ReaderCharacterToolSummary(group, members.size, members.maxOf { traceStatus(it.status, finished) })
    }
}

private fun chapterRanges(chapters: Iterable<Int>, separator: String): String {
    val ranges = mutableListOf<IntRange>()
    for (number in chapters.toSortedSet()) {
        val previous = ranges.lastOrNull()
        if (previous != null && previous.last + 1 == number) {
            ranges[ranges.lastIndex] = previous.first..number
        } else {
            ranges += number..number
        }
    }
    return ranges.joinToString(separator) { if (it.first == it.last) it.first.toString() else "${it.first}–${it.last}" }
}
