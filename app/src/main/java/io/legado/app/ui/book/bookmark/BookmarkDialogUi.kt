package io.legado.app.ui.book.bookmark

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.book.read.readFloatingGlassStyle
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun BookmarkDialogContent(
    chapterName: String,
    initialBookmarkText: String,
    initialNote: String,
    showDelete: Boolean,
    useReadPreset: Boolean,
    onCancel: () -> Unit,
    onConfirm: (bookText: String, content: String) -> Unit,
    onDelete: () -> Unit,
) {
    var bookmarkText by rememberSaveable { mutableStateOf(initialBookmarkText) }
    var note by rememberSaveable { mutableStateOf(initialNote) }
    val glassStyle = if (useReadPreset) {
        readFloatingGlassStyle()
    } else {
        NgGlassDefaults.style(
            containerAlpha = NgTheme.effects.dialogAlpha,
        ).copy(depthEdge = Color.Transparent)
    }

    NgGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        style = glassStyle,
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 14.dp,
            end = 20.dp,
            bottom = 14.dp,
        ),
    ) {
        Text(
            text = stringResource(R.string.bookmark),
            modifier = Modifier.fillMaxWidth(),
            color = Color(NgTheme.colors.onSurface),
            style = TextStyle(
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            textAlign = TextAlign.Center,
        )
        Text(
            text = chapterName,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BookmarkFlatTextField(
            value = bookmarkText,
            onValueChange = { bookmarkText = it },
            label = stringResource(R.string.bookmark_content),
            fieldHeight = 160.dp,
            maxLines = 7,
            modifier = Modifier.padding(top = 8.dp),
        )
        BookmarkFlatTextField(
            value = note,
            onValueChange = { note = it },
            label = stringResource(R.string.bookmark_note_optional),
            placeholder = stringResource(R.string.bookmark_note_hint),
            fieldHeight = 52.dp,
            maxLines = 2,
            modifier = Modifier.padding(top = 10.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showDelete) {
                BookmarkActionButton(
                    text = stringResource(R.string.delete),
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    variant = NgButtonVariant.DANGER,
                )
            }
            BookmarkActionButton(
                text = stringResource(R.string.cancel),
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            BookmarkActionButton(
                text = stringResource(R.string.ok),
                onClick = { onConfirm(bookmarkText, note) },
                modifier = Modifier.weight(1f),
                variant = NgButtonVariant.PRIMARY,
            )
        }
    }
}

@Composable
private fun BookmarkFlatTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    fieldHeight: Dp,
    maxLines: Int,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    val colors = NgTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val dividerColor = if (focused) {
        Color(colors.primary)
    } else {
        Color(colors.outline).copy(alpha = 0.34f)
    }
    val fieldColor = if (NgTheme.snapshot.isEInk) {
        Color(colors.inputContainer)
    } else {
        ReadDrawerStyle.dockSurfaceColor(alpha = 0.16f)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(start = 6.dp),
            color = Color(colors.primary),
            fontSize = 13.sp,
            lineHeight = 16.sp,
            maxLines = 1,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(fieldHeight),
            textStyle = TextStyle(
                color = Color(colors.onSurface),
                fontSize = 15.sp,
                lineHeight = 23.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            cursorBrush = SolidColor(Color(colors.primary)),
            interactionSource = interactionSource,
            singleLine = false,
            maxLines = maxLines,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(fieldHeight)
                        .background(fieldColor)
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            color = Color(colors.onSurfaceVariant).copy(alpha = 0.72f),
                            fontSize = 15.sp,
                            lineHeight = 23.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
        HorizontalDivider(
            thickness = if (focused) 1.25.dp else 0.8.dp,
            color = dividerColor,
        )
    }
}

@Composable
private fun BookmarkActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: NgButtonVariant = NgButtonVariant.OUTLINE,
) {
    val colors = NgTheme.colors
    val primary = Color(colors.primary)
    val accent = if (variant == NgButtonVariant.DANGER) {
        Color(colors.error)
    } else {
        primary
    }
    val isPrimary = variant == NgButtonVariant.PRIMARY
    val containerColor = if (isPrimary) {
        primary
    } else {
        Color.Transparent
    }
    val contentColor = if (isPrimary) Color(colors.onPrimary) else accent

    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        border = if (isPrimary) null else BorderStroke(1.dp, accent),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
        ),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
