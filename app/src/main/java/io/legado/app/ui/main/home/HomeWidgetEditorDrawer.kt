package io.legado.app.ui.main.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.ui.design.components.NgButtonShapeVariant
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgButton
import io.legado.app.ui.design.components.compose.NgDismissibleDrawer
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgDrawerDragHandle
import io.legado.app.ui.design.components.compose.NgDrawerDragHandleVariant
import io.legado.app.ui.design.components.compose.NgIconButton
import io.legado.app.ui.design.theme.NgTheme

/** Save commits the surrounding edit; dismissing only discards the drawer's local selection. */
@Composable
internal fun HomeWidgetEditorDrawer(
    widget: HomeWidgetInstance,
    usedWidgets: List<HomeWidgetInstance>,
    confirmTitle: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Boolean,
    onPreviewTypesChanged: (Set<String>) -> Unit = {},
    preview: @Composable (HomeWidgetVariant) -> Unit,
) {
    val variants = HomeWidgetCatalog.type(widget.typeId)?.variants ?: return
    var selectedId by rememberSaveable(widget.id, widget.variantId) { mutableStateOf(widget.variantId) }
    val selected = variants.firstOrNull { it.id == selectedId } ?: return
    var submitted by remember(widget.id) { mutableStateOf(false) }
    val previewTypes = remember(widget.typeId) { setOf(widget.typeId) }
    HomeWidgetPreviewTypes(previewTypes, onPreviewTypesChanged)
    HomeWidgetDrawerShell(
        title = stringResource(HomeWidgetContents.find(widget.typeId).titleRes),
        confirmTitle = confirmTitle,
        onDismiss = onDismiss,
        onConfirm = { if (!submitted) submitted = onSave(selected.id) },
    ) {
        HomeWidgetStyleChooser(widget.typeId, selected, usedWidgets,
            onSelect = { selectedId = it }, preview = preview)
    }
}

/** One window for both add pages; explicit close gestures dismiss, system Back navigates first. */
@Composable
internal fun HomeWidgetDrawerShell(
    title: String,
    confirmTitle: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.80f).dp
    Dialog(
        onDismissRequest = onNavigateBack ?: onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            )
            Box(Modifier.align(Alignment.BottomCenter)) {
                NgDismissibleDrawer(onDismiss = onDismiss) {
                    NgBottomDrawerSurface(
                        modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight),
                        contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
                    ) {
                        Column(
                            Modifier.fillMaxWidth().navigationBarsPadding()
                                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 12.dp)
                                .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                        ) {
                            NgDrawerDragHandle(variant = NgDrawerDragHandleVariant.COMPACT)
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (onNavigateBack != null) {
                                    NgIconButton(onClick = onNavigateBack, modifier = Modifier.size(48.dp)) {
                                        Icon(
                                            painterResource(R.drawable.ic_arrow_back),
                                            contentDescription = stringResource(R.string.back),
                                            tint = Color(NgTheme.colors.onSurface),
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                                Text(
                                    title,
                                    Modifier.weight(1f),
                                    color = Color(NgTheme.colors.onSurface),
                                    fontSize = 18.sp,
                                    lineHeight = 24.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                NgIconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                                    Icon(
                                        painterResource(R.drawable.ic_baseline_close),
                                        contentDescription = stringResource(R.string.close),
                                        tint = Color(NgTheme.colors.onSurface),
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                            content()
                            if (confirmTitle != null) {
                                NgButton(
                                    onClick = onConfirm,
                                    enabled = confirmEnabled,
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                                        .heightIn(min = 48.dp),
                                    shapeVariant = NgButtonShapeVariant.ROUNDED,
                                ) {
                                    Text(confirmTitle, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
