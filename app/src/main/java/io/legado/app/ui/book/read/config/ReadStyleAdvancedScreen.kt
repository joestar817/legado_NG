package io.legado.app.ui.book.read.config

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import io.legado.app.ui.book.read.page.provider.ReadNineSliceGeometry
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.ReadHighlightRule
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.book.read.page.provider.ReadCharStyle
import io.legado.app.ui.book.read.page.provider.ReadHighlightImageRenderer
import io.legado.app.ui.config.NgInlineColorPicker
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.components.compose.NgSwitchControl
import io.legado.app.ui.design.theme.NgTheme
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private val AdvancedPageHeight = 500.dp

@Composable
internal fun FullLineUnderlinePage(
    state: ReadStyleUiState,
    contentColor: Color,
    accentColor: Color,
    actions: ReadStyleActions,
) {
    val underline = state.fullLineUnderline
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(AdvancedPageHeight),
    ) {
        AdvancedEditorHeader(
            title = stringResource(R.string.read_style_full_underline_title),
            contentColor = contentColor,
            onBack = actions.onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 12.dp,
            ),
        ) {
            item {
                AdvancedSwitchRow(
                    title = stringResource(R.string.enable),
                    checked = underline.enabled,
                    contentColor = contentColor,
                    onCheckedChange = actions.onFullLineUnderlineEnabledChanged,
                )
                if (underline.enabled) {
                    AdvancedDivider(contentColor)
                    AdvancedSectionLabel(
                        stringResource(R.string.text_underline),
                        accentColor,
                    )
                    AdvancedDock(
                        labels = listOf(
                            stringResource(R.string.read_style_underline_solid),
                            stringResource(R.string.read_style_underline_dashed),
                        ),
                        selectedIndex = if (underline.dashed) 1 else 0,
                        contentColor = contentColor,
                        accentColor = accentColor,
                        onSelected = { actions.onFullLineUnderlineDashedChanged(it == 1) },
                    )
                    Spacer(Modifier.height(10.dp))
                    AdvancedColorRow(
                        title = stringResource(R.string.read_style_full_underline_color),
                        color = Color(underline.color),
                        contentColor = contentColor,
                        onClick = {
                            actions.onPageSelected(ReadStylePage.EDIT_UNDERLINE_COLOR)
                        },
                    )
                    AdvancedSliderRow(
                        title = stringResource(R.string.read_style_underline_width),
                        valueText = "${underline.width} dp",
                        value = underline.width.toFloat(),
                        range = 1f..20f,
                        steps = 18,
                        contentColor = contentColor,
                        onValueChanged = {
                            actions.onFullLineUnderlineWidthChanged(it.roundToInt())
                        },
                    )
                    AdvancedSliderRow(
                        title = stringResource(R.string.read_style_underline_offset),
                        valueText = "${underline.offset} dp",
                        value = underline.offset.toFloat().coerceIn(0f, 20f),
                        range = 0f..20f,
                        steps = 19,
                        contentColor = contentColor,
                        onValueChanged = {
                            actions.onFullLineUnderlineOffsetChanged(it.roundToInt())
                        },
                    )
                    AdvancedSwitchRow(
                        title = stringResource(R.string.read_style_underline_extend),
                        checked = underline.extend,
                        contentColor = contentColor,
                        onCheckedChange = actions.onFullLineUnderlineExtendChanged,
                    )
                    if (underline.dashed) {
                        AdvancedSliderRow(
                            title = stringResource(R.string.read_style_underline_dash_length),
                            valueText = "${underline.dashLength.roundToInt()} dp",
                            value = underline.dashLength.coerceIn(1f, 20f),
                            range = 1f..20f,
                            steps = 18,
                            contentColor = contentColor,
                            onValueChanged = actions.onFullLineUnderlineDashLengthChanged,
                        )
                        AdvancedSliderRow(
                            title = stringResource(R.string.read_style_underline_gap_length),
                            valueText = "${underline.gapLength.roundToInt()} dp",
                            value = underline.gapLength.coerceIn(1f, 20f),
                            range = 1f..20f,
                            steps = 18,
                            contentColor = contentColor,
                            onValueChanged = actions.onFullLineUnderlineGapLengthChanged,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HighlightRuleEditorPage(
    state: ReadStyleUiState,
    contentColor: Color,
    accentColor: Color,
    actions: ReadStyleActions,
) {
    val draft = state.highlightDraft ?: return
    val editorHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(editorHeight),
    ) {
        AdvancedEditorHeader(
            title = stringResource(
                if (state.editingHighlightIndex == null) {
                    R.string.highlight_rule_create_title
                } else {
                    R.string.highlight_rule_edit_title
                }
            ),
            contentColor = contentColor,
            onBack = actions.onBack,
            actionLabel = stringResource(R.string.save),
            onAction = actions.onSaveHighlight,
            actionColor = accentColor,
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 16.dp,
            ),
        ) {
            item {
                AdvancedSectionLabel(
                    stringResource(R.string.highlight_rule_section_match),
                    accentColor,
                )
                AdvancedTextField(
                    label = stringResource(R.string.highlight_rule_name),
                    value = draft.name,
                    contentColor = contentColor,
                    accentColor = accentColor,
                    onValueChanged = { actions.onHighlightDraftChanged(draft.copy(name = it)) },
                )
                Spacer(Modifier.height(8.dp))
                AdvancedTextField(
                    label = stringResource(R.string.highlight_rule_pattern),
                    value = draft.pattern,
                    contentColor = contentColor,
                    accentColor = accentColor,
                    onValueChanged = { actions.onHighlightDraftChanged(draft.copy(pattern = it)) },
                )
                Spacer(Modifier.height(8.dp))
                AdvancedTextField(
                    label = stringResource(R.string.highlight_rule_sample),
                    value = draft.sampleText,
                    contentColor = contentColor,
                    accentColor = accentColor,
                    singleLine = false,
                    onValueChanged = {
                        actions.onHighlightDraftChanged(draft.copy(sampleText = it))
                    },
                )
                HighlightRulePreview(
                    rule = draft,
                    contentColor = contentColor,
                    accentColor = accentColor,
                )
                Text(
                    text = stringResource(R.string.highlight_rule_scope),
                    modifier = Modifier.padding(top = 12.dp, bottom = 7.dp),
                    color = contentColor,
                    fontSize = 14.sp,
                )
                AdvancedDock(
                    labels = listOf(
                        stringResource(R.string.all),
                        stringResource(R.string.title),
                        stringResource(R.string.highlight_rule_scope_body),
                    ),
                    selectedIndex = draft.targetScope,
                    contentColor = contentColor,
                    accentColor = accentColor,
                    onSelected = {
                        actions.onHighlightDraftChanged(draft.copy(targetScope = it))
                    },
                )
                AdvancedSwitchRow(
                    title = stringResource(R.string.enable),
                    checked = draft.enabled,
                    contentColor = contentColor,
                    onCheckedChange = {
                        actions.onHighlightDraftChanged(draft.copy(enabled = it))
                    },
                )
                AdvancedDivider(contentColor)
                AdvancedSectionLabel(
                    stringResource(R.string.highlight_rule_section_style),
                    accentColor,
                )
                OptionalColorRow(
                    title = stringResource(R.string.highlight_rule_use_text_color),
                    color = draft.textColor,
                    contentColor = contentColor,
                    onEnabledChanged = { enabled ->
                        actions.onHighlightDraftChanged(
                            draft.copy(
                                textColor = if (enabled) state.editorTextAccentColor else null
                            )
                        )
                    },
                    onClick = {
                        actions.onPageSelected(ReadStylePage.HIGHLIGHT_TEXT_COLOR)
                    },
                )
                OptionalColorRow(
                    title = stringResource(R.string.highlight_rule_use_background_color),
                    color = draft.bgColor,
                    contentColor = contentColor,
                    onEnabledChanged = { enabled ->
                        val fallback = (state.editorTextAccentColor and 0x00FFFFFF) or 0x33000000
                        actions.onHighlightDraftChanged(
                            draft.copy(bgColor = if (enabled) fallback else null)
                        )
                    },
                    onClick = {
                        actions.onPageSelected(ReadStylePage.HIGHLIGHT_BACKGROUND_COLOR)
                    },
                )
                AdvancedFileRow(
                    title = stringResource(R.string.highlight_rule_background_image),
                    path = draft.bgImage,
                    contentColor = contentColor,
                    accentColor = accentColor,
                    onSelect = actions.onSelectHighlightBackground,
                    onClear = actions.onClearHighlightBackground,
                )
                if (!draft.bgImage.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.highlight_rule_image_fit),
                        modifier = Modifier.padding(top = 8.dp, bottom = 7.dp),
                        color = contentColor,
                        fontSize = 14.sp,
                    )
                    AdvancedDock(
                        labels = listOf(
                            stringResource(R.string.highlight_rule_image_fit_tile),
                            stringResource(R.string.highlight_rule_image_fit_stretch),
                            stringResource(R.string.highlight_rule_image_fit_cover),
                            stringResource(R.string.highlight_rule_image_fit_nine),
                        ),
                        selectedIndex = draft.bgImageFit,
                        contentColor = contentColor,
                        accentColor = accentColor,
                        onSelected = {
                            actions.onHighlightDraftChanged(draft.copy(bgImageFit = it))
                            if (it == 3) actions.onPageSelected(ReadStylePage.HIGHLIGHT_NINE_SLICE)
                        },
                    )
                    if (draft.bgImageFit == 3) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                                .clickable(role = Role.Button) {
                                    actions.onPageSelected(ReadStylePage.HIGHLIGHT_NINE_SLICE)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.highlight_nine_slice_editor),
                                modifier = Modifier.weight(1f), color = contentColor, fontSize = 14.sp,
                            )
                            Icon(
                                painterResource(R.drawable.ic_chevron_right_20), contentDescription = null,
                                modifier = Modifier.size(20.dp), tint = contentColor,
                            )
                        }
                    }
                    AdvancedSliderRow(
                        title = stringResource(R.string.highlight_rule_image_scale),
                        valueText = String.format(Locale.ROOT, "%.1f×", draft.bgImageScale),
                        value = draft.bgImageScale.coerceIn(0.1f, 5f),
                        range = 0.1f..5f,
                        steps = 48,
                        contentColor = contentColor,
                        onValueChanged = {
                            actions.onHighlightDraftChanged(
                                draft.copy(bgImageScale = (it * 10).roundToInt() / 10f)
                            )
                        },
                    )
                }
                AdvancedDivider(contentColor)
                AdvancedSectionLabel(
                    stringResource(R.string.highlight_rule_section_underline),
                    accentColor,
                )
                UnderlineStyleGrid(
                    selectedMode = draft.underlineMode,
                    contentColor = contentColor,
                    accentColor = accentColor,
                    onSelected = {
                        actions.onHighlightDraftChanged(draft.copy(underlineMode = it))
                    },
                )
                if (draft.underlineMode != 0) {
                    OptionalColorRow(
                        title = stringResource(R.string.highlight_rule_use_underline_color),
                        color = draft.underlineColor,
                        contentColor = contentColor,
                        onEnabledChanged = { enabled ->
                            actions.onHighlightDraftChanged(
                                draft.copy(
                                    underlineColor = if (enabled) {
                                        draft.textColor ?: state.editorTextAccentColor
                                    } else {
                                        null
                                    }
                                )
                            )
                        },
                        onClick = {
                            actions.onPageSelected(ReadStylePage.HIGHLIGHT_UNDERLINE_COLOR)
                        },
                    )
                    AdvancedSliderRow(
                        title = stringResource(R.string.read_style_underline_width),
                        valueText = String.format(Locale.ROOT, "%.1f dp", draft.underlineWidth),
                        value = draft.underlineWidth.coerceIn(0.1f, 10f),
                        range = 0.1f..10f,
                        steps = 98,
                        contentColor = contentColor,
                        onValueChanged = {
                            actions.onHighlightDraftChanged(
                                draft.copy(underlineWidth = (it * 10).roundToInt() / 10f)
                            )
                        },
                    )
                    AdvancedSliderRow(
                        title = stringResource(R.string.read_style_underline_offset),
                        valueText = String.format(Locale.ROOT, "%.1f dp", draft.underlineOffset),
                        value = draft.underlineOffset.coerceIn(0f, 20f),
                        range = 0f..20f,
                        steps = 199,
                        contentColor = contentColor,
                        onValueChanged = {
                            actions.onHighlightDraftChanged(
                                draft.copy(underlineOffset = (it * 10).roundToInt() / 10f)
                            )
                        },
                    )
                    if (draft.underlineMode == 5) {
                        AdvancedTextField(
                            label = stringResource(R.string.highlight_rule_svg_path),
                            value = draft.underlineSvgPath.orEmpty(),
                            contentColor = contentColor,
                            accentColor = accentColor,
                            singleLine = false,
                            onValueChanged = {
                                actions.onHighlightDraftChanged(
                                    draft.copy(underlineSvgPath = it.ifBlank { null })
                                )
                            },
                        )
                    }
                }
                AdvancedDivider(contentColor)
                AdvancedSectionLabel(
                    stringResource(R.string.highlight_rule_section_font),
                    accentColor,
                )
                AdvancedFileRow(
                    title = stringResource(R.string.highlight_rule_font_file),
                    path = draft.fontPath,
                    contentColor = contentColor,
                    accentColor = accentColor,
                    onSelect = actions.onSelectHighlightFont,
                    onClear = actions.onClearHighlightFont,
                )
                AdvancedSliderRow(
                    title = stringResource(R.string.font_weight),
                    valueText = draft.fontWeight.toString(),
                    value = draft.fontWeight.toFloat().coerceIn(100f, 900f),
                    range = 100f..900f,
                    steps = 7,
                    contentColor = contentColor,
                    onValueChanged = {
                        val weight = ((it / 100f).roundToInt() * 100).coerceIn(100, 900)
                        actions.onHighlightDraftChanged(draft.copy(fontWeight = weight))
                    },
                )
                AdvancedSwitchRow(
                    title = stringResource(R.string.highlight_rule_italic),
                    checked = draft.isItalic,
                    contentColor = contentColor,
                    onCheckedChange = {
                        actions.onHighlightDraftChanged(draft.copy(isItalic = it))
                    },
                )
                if (state.editingHighlightIndex != null) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accentColor.copy(alpha = 0.10f))
                            .clickable(role = Role.Button, onClick = actions.onDeleteHighlight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.delete),
                            color = accentColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightRulePreview(
    rule: ReadHighlightRule,
    contentColor: Color,
    accentColor: Color,
) {
    val text = rule.sampleText.ifBlank { stringResource(R.string.highlight_rule_sample) }
    val textColor = rule.textColor?.let(::Color) ?: contentColor
    val underlineColor = rule.underlineColor?.let(::Color) ?: textColor
    val imagePath = rule.bgImage.orEmpty()
    val loadedImage by produceState<Pair<String, Bitmap?>?>(null, imagePath) {
        value = null
        if (imagePath.isNotBlank()) {
            value = imagePath to withContext(Dispatchers.IO) {
                ReadHighlightImageRenderer.loadBitmap(imagePath)
            }
        }
    }
    // 路径改变后的首帧也不能继续显示上一张图片。
    val backgroundImage = loadedImage?.takeIf { it.first == imagePath }?.second
    val imageStyle = remember(
        imagePath, rule.bgImageFit, rule.bgImageScale,
        rule.npLeft, rule.npRight, rule.npTop, rule.npBottom,
    ) {
        ReadCharStyle(
            bgImage = imagePath,
            bgImageFit = rule.bgImageFit,
            bgImageScale = rule.bgImageScale,
            npLeft = rule.npLeft,
            npRight = rule.npRight,
            npTop = rule.npTop,
            npBottom = rule.npBottom,
        )
    }
    val bottomPadding = if (rule.underlineMode == 0) {
        0.dp
    } else {
        (rule.underlineOffset.coerceAtLeast(0f) + 7f).dp
    }
    val previewCuts = remember(backgroundImage, imageStyle) {
        backgroundImage?.takeIf { imageStyle.bgImageFit == 3 }?.let {
            ReadNineSliceGeometry.from(it.width, it.height, imageStyle)
        }
    }
    val density = LocalDensity.current
    val previewFrame = with(density) {
        previewCuts?.forLine(24.sp.toPx(), ChapterProvider.lineSpacingExtra)
    }
    val frameLeft = with(density) { (previewFrame?.let { it.leftWidth + 3.dp.toPx() } ?: 0f).toDp() }
    val frameRight = with(density) { (previewFrame?.let { it.rightWidth + 3.dp.toPx() } ?: 0f).toDp() }
    val frameVertical = with(density) {
        previewCuts?.verticalInsets(24.sp.toPx(), ChapterProvider.lineSpacingExtra)
            ?.let { maxOf(it.first, it.second).toDp() } ?: 0.dp
    }
    var textLayout by remember(text, rule.fontWeight, rule.isItalic) {
        mutableStateOf<TextLayoutResult?>(null)
    }
    val shape = RoundedCornerShape(12.dp)

    AdvancedSectionLabel(
        label = stringResource(R.string.highlight_rule_preview),
        accentColor = accentColor,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(NgTheme.colors.surface).copy(alpha = 0.22f))
            .border(0.7.dp, contentColor.copy(alpha = 0.12f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .wrapContentSize()
                .padding(start = frameLeft, end = frameRight, top = frameVertical, bottom = frameVertical)
                .background(rule.bgColor?.let(::Color) ?: Color.Transparent),
        ) {
            Text(
                text = text,
                modifier = Modifier
                    .padding(bottom = bottomPadding)
                    .drawWithContent {
                        val bitmap = backgroundImage
                        val layout = textLayout
                        fun drawImages(frame: Boolean) {
                            if (bitmap == null || layout == null) return
                            val inset = if (imageStyle.bgImageFit == 3) 0f else 1.dp.toPx()
                            repeat(layout.lineCount) { line ->
                                val destination = RectF(
                                    layout.getLineLeft(line) - (if (imageStyle.bgImageFit == 3) 3.dp.toPx() else 0f),
                                    layout.getLineTop(line) + inset,
                                    layout.getLineRight(line) + (if (imageStyle.bgImageFit == 3) 3.dp.toPx() else 0f),
                                    layout.getLineBottom(line) - inset,
                                )
                                if (destination.width() > 0f && destination.height() > 0f) {
                                    if (frame) {
                                        ReadHighlightImageRenderer.drawNineSliceFrame(
                                            drawContext.canvas.nativeCanvas, bitmap, destination,
                                            imageStyle, ChapterProvider.lineSpacingExtra,
                                        )
                                    } else {
                                        ReadHighlightImageRenderer.draw(
                                            drawContext.canvas.nativeCanvas, bitmap, destination, imageStyle,
                                        )
                                    }
                                }
                            }
                        }
                        drawImages(false)
                        drawContent()
                        if (imageStyle.bgImageFit == 3) drawImages(true)
                    },
                color = textColor,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight(rule.fontWeight.coerceIn(100, 900)),
                fontStyle = if (rule.isItalic) FontStyle.Italic else FontStyle.Normal,
                onTextLayout = { textLayout = it },
            )
            if (rule.underlineMode != 0) {
                Canvas(Modifier.matchParentSize()) {
                    val layout = textLayout ?: return@Canvas
                    val strokeWidth = rule.underlineWidth.coerceIn(0.1f, 10f).dp.toPx()
                    val offset = rule.underlineOffset.coerceIn(0f, 20f).dp.toPx()
                    repeat(layout.lineCount) { line ->
                        val start = layout.getLineLeft(line)
                        val end = layout.getLineRight(line)
                        val y = layout.getLineBottom(line) + offset
                        when (rule.underlineMode) {
                            1 -> drawLine(
                                color = underlineColor,
                                start = androidx.compose.ui.geometry.Offset(start, y),
                                end = androidx.compose.ui.geometry.Offset(end, y),
                                strokeWidth = strokeWidth,
                            )

                            2 -> drawLine(
                                color = underlineColor,
                                start = androidx.compose.ui.geometry.Offset(start, y),
                                end = androidx.compose.ui.geometry.Offset(end, y),
                                strokeWidth = strokeWidth,
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(8.dp.toPx(), 5.dp.toPx())
                                ),
                            )

                            3 -> {
                                val amplitude = 3.dp.toPx()
                                val wavelength = 12.dp.toPx()
                                val step = 1.dp.toPx()
                                var previous = androidx.compose.ui.geometry.Offset(start, y)
                                var x = start
                                while (x < end) {
                                    val next = (x + step).coerceAtMost(end)
                                    val phase = ((next - start) / wavelength) * 2f * PI.toFloat()
                                    val nextPoint = androidx.compose.ui.geometry.Offset(
                                        next,
                                        y + sin(phase.toDouble()).toFloat() * amplitude,
                                    )
                                    drawLine(
                                        color = underlineColor,
                                        start = previous,
                                        end = nextPoint,
                                        strokeWidth = strokeWidth,
                                    )
                                    previous = nextPoint
                                    x = next
                                }
                            }

                            4 -> {
                                drawLine(
                                    color = underlineColor,
                                    start = androidx.compose.ui.geometry.Offset(start, y),
                                    end = androidx.compose.ui.geometry.Offset(end, y),
                                    strokeWidth = strokeWidth,
                                )
                                drawLine(
                                    color = underlineColor,
                                    start = androidx.compose.ui.geometry.Offset(start, y + 3.dp.toPx()),
                                    end = androidx.compose.ui.geometry.Offset(end, y + 3.dp.toPx()),
                                    strokeWidth = strokeWidth,
                                )
                            }

                            else -> drawLine(
                                color = underlineColor,
                                start = androidx.compose.ui.geometry.Offset(start, y),
                                end = androidx.compose.ui.geometry.Offset(end, y),
                                strokeWidth = strokeWidth,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HighlightRuleColorPage(
    page: ReadStylePage,
    state: ReadStyleUiState,
    actions: ReadStyleActions,
) {
    val draft = state.highlightDraft ?: return
    val title: String
    val color: Int
    when (page) {
        ReadStylePage.HIGHLIGHT_BACKGROUND_COLOR -> {
            title = stringResource(R.string.bg_color)
            color = draft.bgColor ?: state.editorTextAccentColor
        }

        ReadStylePage.HIGHLIGHT_UNDERLINE_COLOR -> {
            title = stringResource(R.string.read_style_full_underline_color)
            color = draft.underlineColor
                ?: draft.textColor
                ?: state.editorTextAccentColor
        }

        else -> {
            title = stringResource(R.string.text_color)
            color = draft.textColor ?: state.editorTextAccentColor
        }
    }
    AdvancedColorPage(
        title = title,
        initialColor = state.editorInitialColor ?: color,
        onBack = actions.onBack,
        onColorChanged = { selected ->
            actions.onHighlightDraftChanged(
                when (page) {
                    ReadStylePage.HIGHLIGHT_BACKGROUND_COLOR -> draft.copy(bgColor = selected)
                    ReadStylePage.HIGHLIGHT_UNDERLINE_COLOR ->
                        draft.copy(underlineColor = selected)
                    else -> draft.copy(textColor = selected)
                }
            )
        },
        onReset = actions.onResetEditorColor,
    )
}

@Composable
internal fun AdvancedColorPage(
    title: String,
    initialColor: Int,
    onBack: () -> Unit,
    onColorChanged: (Int) -> Unit,
    onReset: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(AdvancedPageHeight)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        NgInlineColorPicker(
            title = title,
            initialColor = initialColor,
            onBack = onBack,
            onColorChanged = onColorChanged,
            onReset = onReset,
        )
    }
}

@Composable
internal fun AdvancedEditorHeader(
    title: String,
    contentColor: Color,
    onBack: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionColor: Color = contentColor,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.back),
                modifier = Modifier.size(24.dp),
                tint = contentColor,
            )
        }
        Text(
            text = title,
            modifier = Modifier.padding(start = 2.dp).weight(1f),
            color = contentColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (actionLabel != null && onAction != null) {
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = actionLabel,
                    color = actionColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun AdvancedTextField(
    label: String,
    value: String,
    contentColor: Color,
    accentColor: Color,
    singleLine: Boolean = true,
    onValueChanged: (String) -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(NgTheme.colors.surface).copy(alpha = 0.22f))
            .border(0.7.dp, contentColor.copy(alpha = 0.12f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = contentColor.copy(alpha = 0.62f),
            fontSize = 11.sp,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChanged,
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 2,
            maxLines = if (singleLine) 1 else 3,
            textStyle = TextStyle(color = contentColor, fontSize = 14.sp),
            keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
            cursorBrush = SolidColor(accentColor),
        )
    }
}

@Composable
private fun AdvancedSectionLabel(label: String, accentColor: Color) {
    Text(
        text = label,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        color = accentColor,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun AdvancedSwitchRow(
    title: String,
    checked: Boolean,
    contentColor: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, modifier = Modifier.weight(1f), color = contentColor, fontSize = 14.sp)
        NgSwitchControl(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(width = 52.dp, height = 36.dp),
        )
    }
}

@Composable
private fun AdvancedColorRow(
    title: String,
    color: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, modifier = Modifier.weight(1f), color = contentColor, fontSize = 14.sp)
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(color)
                .border(0.7.dp, contentColor.copy(alpha = 0.18f), CircleShape),
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right_20),
            contentDescription = null,
            modifier = Modifier.padding(start = 6.dp).size(18.dp),
            tint = contentColor.copy(alpha = 0.68f),
        )
    }
}

@Composable
private fun OptionalColorRow(
    title: String,
    color: Int?,
    contentColor: Color,
    onEnabledChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, modifier = Modifier.weight(1f), color = contentColor, fontSize = 14.sp)
        if (color != null) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(0.7.dp, contentColor.copy(alpha = 0.18f), CircleShape)
                    .clickable(role = Role.Button, onClick = onClick),
            )
        }
        NgSwitchControl(
            checked = color != null,
            onCheckedChange = onEnabledChanged,
            modifier = Modifier.size(width = 52.dp, height = 36.dp),
        )
    }
}

@Composable
private fun AdvancedFileRow(
    title: String,
    path: String?,
    contentColor: Color,
    accentColor: Color,
    onSelect: () -> Unit,
    onClear: () -> Unit,
) {
    val fileName = path?.substringAfterLast('/')?.substringAfterLast(':')
        ?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.highlight_rule_no_file)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = contentColor, fontSize = 14.sp)
            Text(
                text = fileName,
                color = contentColor.copy(alpha = 0.60f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!path.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onClear)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.clear),
                    color = contentColor.copy(alpha = 0.70f),
                    fontSize = 13.sp,
                )
            }
        }
        Box(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onSelect)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.highlight_rule_choose_file),
                color = accentColor,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun AdvancedSliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    contentColor: Color,
    onValueChanged: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, modifier = Modifier.weight(1f), color = contentColor, fontSize = 14.sp)
            Text(text = valueText, color = contentColor.copy(alpha = 0.68f), fontSize = 12.sp)
        }
        NgSlider(
            value = value,
            onValueChange = onValueChanged,
            valueRange = range,
            steps = steps,
            variant = NgSliderVariant.COMPACT,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun UnderlineStyleGrid(
    selectedMode: Int,
    contentColor: Color,
    accentColor: Color,
    onSelected: (Int) -> Unit,
) {
    val labels = listOf(
        stringResource(R.string.highlight_rule_underline_none),
        stringResource(R.string.read_style_underline_solid),
        stringResource(R.string.read_style_underline_dashed),
        stringResource(R.string.highlight_rule_underline_wavy),
        stringResource(R.string.highlight_rule_underline_bar),
        stringResource(R.string.highlight_rule_underline_svg),
    )
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        labels.chunked(3).forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                row.forEachIndexed { columnIndex, label ->
                    val mode = rowIndex * 3 + columnIndex
                    val selected = mode == selectedMode
                    val shape = RoundedCornerShape(11.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(shape)
                            .background(
                                if (selected) accentColor
                                else Color(NgTheme.colors.surface).copy(alpha = 0.24f)
                            )
                            .border(0.7.dp, contentColor.copy(alpha = 0.12f), shape)
                            .clickable(role = Role.RadioButton) { onSelected(mode) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = if (selected) {
                                if (accentColor.luminance() > 0.5f) Color.Black else Color.White
                            } else {
                                contentColor
                            },
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvancedDock(
    labels: List<String>,
    selectedIndex: Int,
    contentColor: Color,
    accentColor: Color,
    onSelected: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val dockSurfaceColor = ReadDrawerStyle.dockSurfaceColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(shape)
            .background(dockSurfaceColor)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex.coerceIn(labels.indices)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) accentColor else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelected(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) {
                        if (accentColor.luminance() > 0.5f) Color.Black else Color.White
                    } else {
                        contentColor
                    },
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AdvancedDivider(contentColor: Color) {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.8.dp)
            .background(contentColor.copy(alpha = 0.12f)),
    )
}
