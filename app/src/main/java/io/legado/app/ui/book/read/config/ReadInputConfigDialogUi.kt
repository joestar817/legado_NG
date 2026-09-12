package io.legado.app.ui.book.read.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.theme.NgTheme

internal data class ClickActionOption(
    val value: Int,
    val label: String,
)

@Composable
internal fun PageKeyDialogContent(
    prevKeys: String,
    nextKeys: String,
    onPrevKeysChanged: (String) -> Unit,
    onNextKeysChanged: (String) -> Unit,
    onPrevFocusChanged: (Boolean) -> Unit,
    onNextFocusChanged: (Boolean) -> Unit,
    onReset: () -> Unit,
    onConfirm: () -> Unit,
) {
    ReadConfigDialogSurface(
        contentPadding = PaddingValues(16.dp),
    ) {
        Text(
            text = stringResource(R.string.custom_page_key),
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
            color = Color(NgTheme.colors.onSurface),
            style = TextStyle(
                fontFamily = NgTheme.fontFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
        PageKeyInputField(
            label = stringResource(R.string.prev_page_key),
            value = prevKeys,
            onValueChange = onPrevKeysChanged,
            onFocusChanged = onPrevFocusChanged,
            modifier = Modifier.padding(5.dp),
        )
        PageKeyInputField(
            label = stringResource(R.string.next_page_key),
            value = nextKeys,
            onValueChange = onNextKeysChanged,
            onFocusChanged = onNextFocusChanged,
            modifier = Modifier.padding(5.dp),
        )
        Text(
            text = stringResource(R.string.page_key_set_help),
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            NgFormActionButton(
                text = stringResource(R.string.reset),
                onClick = onReset,
                modifier = Modifier.weight(1f),
            )
            NgFormActionButton(
                text = stringResource(R.string.ok),
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                variant = NgButtonVariant.PRIMARY,
            )
        }
    }
}

@Composable
private fun PageKeyInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NgTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
            color = Color(colors.onSurfaceVariant),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .onFocusChanged { onFocusChanged(it.isFocused) },
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = NgTheme.fontFamily,
                color = Color(colors.onSurface),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            cursorBrush = SolidColor(Color(colors.primary)),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clip(shape)
                        .background(Color(colors.inputContainer))
                        .border(1.dp, Color(colors.outline), shape)
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    innerTextField()
                }
            },
        )
    }
}

@Composable
internal fun ClickActionConfigScreen(
    actionLabels: List<String>,
    onCellClick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val labels = remember(actionLabels) {
        List(9) { index -> actionLabels.getOrElse(index) { "" } }
    }
    val isDark = NgTheme.snapshot.isDark
    val foregroundColor = if (isDark) {
        Color(0xFFF2F4F7)
    } else {
        Color(NgTheme.colors.onSurface)
    }
    val glassStyle = NgGlassDefaults.style(
        containerAlpha = if (isDark) 0.54f else 0.69f,
    ).copy(shadowElevation = 0.dp)
    val glassShape = RoundedCornerShape(6.dp)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (isDark) 0.07f else 0.10f))
            .padding(3.dp),
    ) {
        NgGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            shape = glassShape,
            style = glassStyle,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.click_regional_config),
                    modifier = Modifier.weight(1f),
                    color = foregroundColor,
                    fontSize = 15.sp,
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.close),
                            onClick = onClose,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.close),
                        modifier = Modifier.size(24.dp),
                        tint = foregroundColor,
                    )
                }
            }
        }
        repeat(3) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                repeat(3) { column ->
                    val index = row * 3 + column
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(3.dp),
                    ) {
                        NgGlassSurface(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    role = Role.Button,
                                    onClick = { onCellClick(index) },
                                ),
                            shape = glassShape,
                            style = glassStyle,
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = labels[index],
                                    modifier = Modifier.padding(12.dp),
                                    color = foregroundColor,
                                    fontSize = 14.sp,
                                    lineHeight = 19.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ClickActionSelectorDialog(
    title: String,
    options: List<ClickActionOption>,
    onSelected: (Int) -> Unit,
) {
    val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.72f).dp
    ReadConfigDialogSurface(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxListHeight),
        ) {
            items(
                items = options,
                key = { it.value },
            ) { option ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            role = Role.Button,
                            onClick = { onSelected(option.value) },
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = option.label,
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
        }
    }
}
