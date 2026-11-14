package io.legado.app.ui.book.read.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
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
import io.legado.app.help.config.PageKeyBindings
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.theme.NgTheme

internal data class ClickActionOption(
    val value: Int,
    val label: String,
)

@Composable
internal fun PageKeyDialogContent(
    bindings: PageKeyBindings,
    capture: PageKeyCaptureController,
    onBindingsChanged: (PageKeyBindings) -> Unit,
    onClose: () -> Unit,
) {
    ReadConfigDialogSurface(contentPadding = PaddingValues(16.dp)) {
        Text(
            text = stringResource(R.string.custom_page_key),
            modifier = Modifier.fillMaxWidth().padding(5.dp),
            color = Color(NgTheme.colors.onSurface),
            style = TextStyle(
                fontFamily = NgTheme.fontFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
        PageKeyBindingControls(bindings, capture, onBindingsChanged)
        NgFormActionButton(
            text = stringResource(R.string.close),
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().padding(5.dp),
            variant = NgButtonVariant.PRIMARY,
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
    val translucent = colorResource(R.color.translucent)
    val foregroundColor = Color.White
    val shape = RoundedCornerShape(3.dp)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(translucent)
            .padding(3.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp)
                .clip(shape)
                .background(translucent),
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
                            .padding(3.dp)
                            .clip(shape)
                            .background(translucent)
                            .clickable(
                                role = Role.Button,
                                onClick = { onCellClick(index) },
                            ),
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
