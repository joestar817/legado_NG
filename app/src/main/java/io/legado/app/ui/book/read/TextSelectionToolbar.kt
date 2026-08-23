@file:OptIn(ExperimentalFoundationApi::class)

package io.legado.app.ui.book.read

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.Bookmark
import io.legado.app.ui.design.components.compose.NgGlassStyle
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.theme.NgTheme

internal const val TEXT_SELECTION_TOOLBAR_HEIGHT_DP = 52
internal const val TEXT_HIGHLIGHT_EDITOR_HEIGHT_DP = 40
internal const val TEXT_HIGHLIGHT_EDITOR_GAP_DP = 4
internal const val TEXT_HIGHLIGHT_NOTE_EDITOR_HEIGHT_DP = 56
internal const val TEXT_HIGHLIGHT_NOTE_EDITOR_GAP_DP = 4
internal const val TEXT_SELECTION_FIRST_PAGE_ACTION_COUNT = 5
private const val TEXT_SELECTION_TOOLBAR_BASE_WIDTH_DP = 336
private const val TEXT_SELECTION_PAGE_BUTTON_WIDTH_DP = 36
private const val SECONDARY_PAGE_ACTION_COUNT = 3
internal const val TEXT_SELECTION_MORE_ACTION_HEIGHT_DP = 44
internal const val TEXT_SELECTION_MORE_PANEL_WIDTH_DP = 156
private const val MORE_PANEL_VERTICAL_PADDING_DP = 4
private const val MORE_PANEL_MAX_VISIBLE_ROWS = 10
internal const val TEXT_SELECTION_MORE_PANEL_GAP_DP = 4
internal const val TEXT_SELECTION_MORE_PANEL_SCREEN_MARGIN_DP = 8

internal data class TextSelectionAction(
    val title: String,
    @param:DrawableRes val iconRes: Int,
    val iconBitmap: ImageBitmap? = null,
    val onClick: () -> Unit,
)

internal fun textSelectionToolbarWidthDp(primaryActionCount: Int): Int {
    return TEXT_SELECTION_TOOLBAR_BASE_WIDTH_DP +
        if (primaryActionCount > TEXT_SELECTION_FIRST_PAGE_ACTION_COUNT) {
            TEXT_SELECTION_PAGE_BUTTON_WIDTH_DP
        } else {
            0
        }
}

internal fun textSelectionMoreMenuHeightDp(actionCount: Int): Int {
    if (actionCount <= 0) return 0
    return actionCount.coerceAtMost(MORE_PANEL_MAX_VISIBLE_ROWS) *
        TEXT_SELECTION_MORE_ACTION_HEIGHT_DP + MORE_PANEL_VERTICAL_PADDING_DP * 2
}

internal fun textSelectionToolbarHeightDp(
    showHighlightEditor: Boolean,
    showNoteEditor: Boolean = false,
): Int {
    return TEXT_SELECTION_TOOLBAR_HEIGHT_DP +
        if (showHighlightEditor) {
            TEXT_HIGHLIGHT_EDITOR_GAP_DP + TEXT_HIGHLIGHT_EDITOR_HEIGHT_DP
        } else {
            0
        } +
        if (showHighlightEditor && showNoteEditor) {
            TEXT_HIGHLIGHT_NOTE_EDITOR_GAP_DP + TEXT_HIGHLIGHT_NOTE_EDITOR_HEIGHT_DP
        } else {
            0
        }
}

@Composable
internal fun TextHighlightNotePreview(
    note: String,
    onSettings: () -> Unit,
) {
    val settingsDescription = stringResource(R.string.highlight_settings)
    val noteScrollState = rememberScrollState()
    val settingsContainerColor = if (NgTheme.snapshot.isEInk) {
        Color.Transparent
    } else {
        Color(NgTheme.colors.primary).copy(alpha = 0.10f)
    }
    val previewShape = RoundedCornerShape(12.dp)
    NgGlassSurface(
        modifier = Modifier.fillMaxSize(),
        shape = previewShape,
        style = readFloatingGlassStyle(),
    ) {
        Box(Modifier.fillMaxSize()) {
            Text(
                text = note.trim(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, top = 14.dp, end = 58.dp, bottom = 14.dp)
                    .verticalScroll(noteScrollState),
                color = Color(NgTheme.colors.onSurface),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 2.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = settingsDescription,
                        onClick = onSettings,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(settingsContainerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = settingsDescription,
                        modifier = Modifier.size(18.dp),
                        tint = Color(NgTheme.colors.primary),
                    )
                }
            }
        }
    }
}

@Composable
internal fun TextSelectionToolbar(
    primaryActions: List<TextSelectionAction>,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    moreMenuVisible: Boolean,
    onMoreMenuVisibleChange: (Boolean) -> Unit,
    onLongClick: () -> Unit,
    textHighlight: Bookmark? = null,
    onHighlightStyleChange: (style: Int, color: Int) -> Unit = { _, _ -> },
    noteEditorVisible: Boolean = false,
    noteDraft: String = "",
    onNoteEditorVisibleChange: (Boolean) -> Unit = {},
    onNoteDraftChange: (String) -> Unit = {},
    onNoteDone: () -> Unit = {},
    dragEnabled: Boolean = true,
    onDragStart: () -> Unit = {},
    onDrag: (deltaX: Float, deltaY: Float) -> Unit = { _, _ -> },
) {
    val pages = buildList {
        add(primaryActions.take(TEXT_SELECTION_FIRST_PAGE_ACTION_COUNT))
        primaryActions
            .drop(TEXT_SELECTION_FIRST_PAGE_ACTION_COUNT)
            .chunked(SECONDARY_PAGE_ACTION_COUNT)
            .forEach(::add)
    }
    val page = currentPage.coerceIn(0, pages.lastIndex)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dragModifier = if (dragEnabled) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { currentOnDragStart() },
                onDrag = { change, dragAmount ->
                    change.consume()
                    currentOnDrag(dragAmount.x, dragAmount.y)
                },
            )
        }
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(dragModifier),
    ) {
        TextSelectionSubtleGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(TEXT_SELECTION_TOOLBAR_HEIGHT_DP.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (page == 0) {
                    pages[page].forEach { action ->
                        TextSelectionActionItem(
                            action = action,
                            onLongClick = onLongClick,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(TEXT_SELECTION_FIRST_PAGE_ACTION_COUNT - pages[page].size) {
                        Spacer(Modifier.weight(1f))
                    }
                    MoreButton(
                        expanded = moreMenuVisible,
                        onExpandedChange = { visible ->
                            if (visible && noteEditorVisible) {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                            onMoreMenuVisibleChange(visible)
                        },
                    )
                    if (pages.size > 1) {
                        PageButton(
                            iconRes = R.drawable.ic_chevron_right_20,
                            contentDescription = stringResource(R.string.more_menu),
                            onClick = { onPageChange(1) },
                        )
                    }
                } else {
                    PageButton(
                        iconRes = R.drawable.ic_chevron_left_20,
                        contentDescription = stringResource(R.string.back),
                        onClick = { onPageChange(page - 1) },
                    )
                    pages[page].forEach { action ->
                        TextSelectionActionItem(
                            action = action,
                            onLongClick = onLongClick,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(SECONDARY_PAGE_ACTION_COUNT - pages[page].size) {
                        Spacer(Modifier.weight(1f))
                    }
                    if (page == pages.lastIndex) {
                        Spacer(Modifier.width(TEXT_SELECTION_PAGE_BUTTON_WIDTH_DP.dp))
                    }
                    MoreButton(
                        expanded = moreMenuVisible,
                        onExpandedChange = { visible ->
                            if (visible && noteEditorVisible) {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                            onMoreMenuVisibleChange(visible)
                        },
                    )
                    if (page < pages.lastIndex) {
                        PageButton(
                            iconRes = R.drawable.ic_chevron_right_20,
                            contentDescription = stringResource(R.string.more_menu),
                            onClick = { onPageChange(page + 1) },
                        )
                    }
                }
            }
        }
        textHighlight?.let { highlight ->
            Spacer(Modifier.height(TEXT_HIGHLIGHT_EDITOR_GAP_DP.dp))
            TextHighlightStyleBar(
                highlight = highlight,
                noteEditorVisible = noteEditorVisible,
                noteDraft = noteDraft,
                onNoteEditorVisibleChange = { visible ->
                    if (!visible) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                    onNoteEditorVisibleChange(visible)
                },
                onStyleChange = onHighlightStyleChange,
            )
            if (noteEditorVisible) {
                Spacer(Modifier.height(TEXT_HIGHLIGHT_NOTE_EDITOR_GAP_DP.dp))
                TextHighlightNoteEditor(
                    value = noteDraft,
                    onValueChange = onNoteDraftChange,
                    onDone = onNoteDone,
                )
            }
        }
    }
}

@Composable
private fun TextHighlightStyleBar(
    highlight: Bookmark,
    noteEditorVisible: Boolean,
    noteDraft: String,
    onNoteEditorVisibleChange: (Boolean) -> Unit,
    onStyleChange: (style: Int, color: Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TEXT_HIGHLIGHT_EDITOR_HEIGHT_DP.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HighlightNoteButton(
            expanded = noteEditorVisible,
            hasNote = noteDraft.isNotBlank(),
            onClick = { onNoteEditorVisibleChange(!noteEditorVisible) },
        )
        HighlightStyleButton(
            style = Bookmark.STYLE_BACKGROUND,
            selected = highlight.highlightStyle == Bookmark.STYLE_BACKGROUND,
            color = Color(highlight.highlightColor),
            contentDescription = stringResource(R.string.highlight_style_background),
            onClick = { onStyleChange(Bookmark.STYLE_BACKGROUND, highlight.highlightColor) },
        )
        HighlightStyleButton(
            style = Bookmark.STYLE_UNDERLINE,
            selected = highlight.highlightStyle == Bookmark.STYLE_UNDERLINE,
            color = Color(highlight.highlightColor),
            contentDescription = stringResource(R.string.highlight_style_underline),
            onClick = { onStyleChange(Bookmark.STYLE_UNDERLINE, highlight.highlightColor) },
        )
        HighlightStyleButton(
            style = Bookmark.STYLE_WAVY_UNDERLINE,
            selected = highlight.highlightStyle == Bookmark.STYLE_WAVY_UNDERLINE,
            color = Color(highlight.highlightColor),
            contentDescription = stringResource(R.string.highlight_style_wavy),
            onClick = {
                onStyleChange(Bookmark.STYLE_WAVY_UNDERLINE, highlight.highlightColor)
            },
        )
        Spacer(Modifier.width(4.dp))
        TextSelectionSubtleGlassSurface(
            modifier = Modifier
                .weight(1f)
                .height(36.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Bookmark.HIGHLIGHT_COLORS.forEachIndexed { index, color ->
                    HighlightColorButton(
                        color = Color(color),
                        selected = highlight.highlightColor == color,
                        contentDescription = stringResource(
                            R.string.highlight_color_option,
                            index + 1,
                        ),
                        onClick = { onStyleChange(highlight.highlightStyle, color) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HighlightNoteButton(
    expanded: Boolean,
    hasNote: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = textSelectionBaseContainerColor()
    val selectedContainerColor = lerp(
        containerColor,
        Color(NgTheme.colors.primary).copy(alpha = containerColor.alpha),
        0.18f,
    )
    val contentDescription = stringResource(R.string.bookmark_note)
    TextSelectionSubtleGlassSurface(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
        shape = CircleShape,
        containerColor = selectedContainerColor.takeIf { expanded },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ai_chat_suggestion),
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = if (hasNote || expanded) {
                    Color(NgTheme.colors.primary)
                } else {
                    Color(NgTheme.colors.onSurface)
                },
            )
        }
    }
}

@Composable
private fun TextHighlightNoteEditor(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val finishEditing = {
        focusManager.clearFocus()
        keyboardController?.hide()
        onDone()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val noteStyle = textSelectionSubtleGlassStyle(prominentContent = true)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TEXT_HIGHLIGHT_NOTE_EDITOR_HEIGHT_DP.dp),
    ) {
        NgGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .align(Alignment.BottomStart),
            shape = RoundedCornerShape(12.dp),
            style = noteStyle,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .focusRequester(focusRequester)
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    textStyle = TextStyle(
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    cursorBrush = SolidColor(Color(NgTheme.colors.primary)),
                    singleLine = false,
                    maxLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { finishEditing() }),
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(NgTheme.colors.primary).copy(alpha = 0.14f))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.ok),
                            onClick = finishEditing,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = stringResource(R.string.ok),
                        modifier = Modifier.size(20.dp),
                        tint = Color(NgTheme.colors.primary),
                    )
                }
            }
        }
        Canvas(
            modifier = Modifier
                .padding(start = 13.dp)
                .width(14.dp)
                .height(8.dp)
                .align(Alignment.TopStart),
        ) {
            val apex = androidx.compose.ui.geometry.Offset(size.width / 2f, 0f)
            val left = androidx.compose.ui.geometry.Offset(0f, size.height)
            val right = androidx.compose.ui.geometry.Offset(size.width, size.height)
            val pointer = androidx.compose.ui.graphics.Path().apply {
                moveTo(left.x, left.y)
                lineTo(apex.x, apex.y)
                lineTo(right.x, right.y)
                close()
            }
            drawPath(pointer, color = noteStyle.containerTop)
            val strokeWidth = 0.6.dp.toPx()
            drawLine(noteStyle.borderColor, left, apex, strokeWidth)
            drawLine(noteStyle.borderColor, apex, right, strokeWidth)
        }
    }
}

@Composable
private fun HighlightStyleButton(
    style: Int,
    selected: Boolean,
    color: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val containerColor = textSelectionBaseContainerColor()
    val selectedContainerColor = lerp(
        containerColor,
        Color(NgTheme.colors.primary).copy(alpha = containerColor.alpha),
        0.18f,
    )
    TextSelectionSubtleGlassSurface(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
        shape = CircleShape,
        containerColor = selectedContainerColor.takeIf { selected },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(23.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (style == Bookmark.STYLE_BACKGROUND) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(4.dp))
                            .background(color.copy(alpha = 0.42f)),
                    )
                }
                Text(
                    text = "A",
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 19.sp,
                    lineHeight = 21.sp,
                )
                if (style != Bookmark.STYLE_BACKGROUND) {
                    Canvas(Modifier.fillMaxSize()) {
                        val y = size.height - 1.5.dp.toPx()
                        if (style == Bookmark.STYLE_UNDERLINE) {
                            drawLine(
                                color = color,
                                start = androidx.compose.ui.geometry.Offset(1.dp.toPx(), y),
                                end = androidx.compose.ui.geometry.Offset(size.width - 1.dp.toPx(), y),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        } else {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(1.dp.toPx(), y)
                                var x = 1.dp.toPx()
                                var upwards = true
                                while (x < size.width - 1.dp.toPx()) {
                                    val next = (x + 5.dp.toPx()).coerceAtMost(
                                        size.width - 1.dp.toPx()
                                    )
                                    quadraticTo(
                                        (x + next) / 2,
                                        y + if (upwards) -1.5.dp.toPx() else 1.5.dp.toPx(),
                                        next,
                                        y,
                                    )
                                    upwards = !upwards
                                    x = next
                                }
                            }
                            drawPath(
                                path,
                                color,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 1.8.dp.toPx(),
                                    cap = StrokeCap.Round,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightColorButton(
    color: Color,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .clickable(
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (selected) 24.dp else 21.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = Color(0xFF263238),
                )
            }
        }
    }
}

@Composable
private fun TextSelectionActionItem(
    action: TextSelectionAction,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(9.dp))
            .combinedClickable(
                role = Role.Button,
                onClick = action.onClick,
                onLongClickLabel = stringResource(R.string.switch_selection_read_mode),
                onLongClick = onLongClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ActionIcon(
            action = action,
            sizeDp = 20,
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = action.title,
            color = Color(NgTheme.colors.onSurface),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MoreButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .width(TEXT_SELECTION_PAGE_BUTTON_WIDTH_DP.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(9.dp))
            .combinedClickable(
                role = Role.Button,
                onClick = { onExpandedChange(!expanded) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_more_vert),
            contentDescription = stringResource(R.string.more_menu),
            modifier = Modifier.size(20.dp),
            tint = Color(NgTheme.colors.onSurface),
        )
    }
}

@Composable
private fun PageButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(TEXT_SELECTION_PAGE_BUTTON_WIDTH_DP.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(9.dp))
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = Color(NgTheme.colors.onSurface),
        )
    }
}

@Composable
internal fun TextSelectionMoreMenu(
    actions: List<TextSelectionAction>,
    onLongClick: () -> Unit,
) {
    TextSelectionSubtleGlassSurface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(MORE_PANEL_VERTICAL_PADDING_DP.dp))
            actions.forEach { action ->
                MoreActionItem(
                    action = action,
                    onLongClick = onLongClick,
                )
            }
            Spacer(Modifier.height(MORE_PANEL_VERTICAL_PADDING_DP.dp))
        }
    }
}

@Composable
private fun TextSelectionSubtleGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    containerColor: Color? = null,
    content: @Composable () -> Unit,
) {
    NgGlassSurface(
        modifier = modifier,
        shape = shape,
        style = textSelectionSubtleGlassStyle(containerColor),
    ) {
        content()
    }
}

@Composable
private fun textSelectionBaseContainerColor(): Color {
    val floatingStyle = readFloatingGlassStyle()
    return lerp(floatingStyle.containerTop, floatingStyle.containerBottom, 0.5f)
}

@Composable
private fun textSelectionSubtleGlassStyle(
    containerColor: Color? = null,
    prominentContent: Boolean = false,
): NgGlassStyle {
    val floatingStyle = readFloatingGlassStyle()
    val isEInk = NgTheme.snapshot.isEInk
    fun prominent(color: Color): Color {
        if (!prominentContent || isEInk) return color
        return color.copy(alpha = (color.alpha + 0.08f).coerceAtMost(0.94f))
    }
    val containerTop = prominent(containerColor ?: floatingStyle.containerTop)
    val containerBottom = prominent(containerColor ?: floatingStyle.containerBottom)
    return floatingStyle.copy(
        containerTop = containerTop,
        containerBottom = containerBottom,
        accentGlow = if (containerColor == null) {
            floatingStyle.accentGlow
        } else {
            Color.Transparent
        },
        borderColor = if (isEInk) {
            floatingStyle.borderColor
        } else {
            floatingStyle.borderColor.copy(
                alpha = floatingStyle.borderColor.alpha * 0.65f
            )
        },
        edgeHighlight = floatingStyle.edgeHighlight.copy(
            alpha = floatingStyle.edgeHighlight.alpha * 0.65f
        ),
        surfaceGloss = floatingStyle.surfaceGloss.copy(
            alpha = floatingStyle.surfaceGloss.alpha * 0.45f
        ),
        depthEdge = floatingStyle.depthEdge.copy(
            alpha = floatingStyle.depthEdge.alpha * 0.35f
        ),
        blurRadius = 0.dp,
        shadowElevation = 0.dp,
    )
}

@Composable
private fun MoreActionItem(
    action: TextSelectionAction,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TEXT_SELECTION_MORE_ACTION_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                role = Role.Button,
                onClick = action.onClick,
                onLongClickLabel = stringResource(R.string.switch_selection_read_mode),
                onLongClick = onLongClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(10.dp))
        ActionIcon(
            action = action,
            sizeDp = 20,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = action.title,
            modifier = Modifier.weight(1f),
            color = Color(NgTheme.colors.onSurface),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 18.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(10.dp))
    }
}

@Composable
private fun ActionIcon(
    action: TextSelectionAction,
    sizeDp: Int,
    contentDescription: String? = null,
) {
    val iconBitmap = action.iconBitmap
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
            contentDescription = contentDescription,
            modifier = Modifier.size(sizeDp.dp),
        )
    } else {
        Icon(
            painter = painterResource(action.iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(sizeDp.dp),
            tint = Color(NgTheme.colors.onSurface),
        )
    }
}
