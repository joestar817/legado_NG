package io.legado.app.ui.book.read.config

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.Window
import androidx.activity.ComponentDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.NgThemeSnapshot
import io.legado.app.ui.widget.dialog.applyNgWindow

internal fun showReadComposeDialog(
    context: Context,
    marginDp: Int = 20,
    dimAmount: Float = 0.14f,
    cancelOnTouchOutside: Boolean = true,
    onDismiss: () -> Unit = {},
    themeSnapshot: NgThemeSnapshot = ReadDrawerStyle.themeSnapshot(context),
    content: @Composable (dismiss: () -> Unit) -> Unit,
): ComponentDialog {
    val dialog = ComponentDialog(context)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    dialog.setCanceledOnTouchOutside(cancelOnTouchOutside)
    dialog.setOnDismissListener { onDismiss() }
    dialog.setContentView(
        ComposeView(context).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NgAppTheme(
                    snapshot = themeSnapshot,
                    updateSystemBars = false,
                ) {
                    content(dialog::dismiss)
                }
            }
        }
    )
    dialog.show()
    dialog.applyNgWindow(marginDp = marginDp, dimAmount = dimAmount)
    return dialog
}

internal fun showReadConfirmDialog(
    context: Context,
    title: String,
    message: String? = null,
    confirmLabel: String,
    cancelLabel: String? = null,
    onConfirm: () -> Unit,
    onCancel: (() -> Unit)? = null,
    onOutsideDismiss: (() -> Unit)? = null,
    themeSnapshot: NgThemeSnapshot? = null,
): ComponentDialog {
    var actionTaken = false
    return showReadComposeDialog(
        context = context,
        onDismiss = {
            if (!actionTaken) onOutsideDismiss?.invoke()
        },
        themeSnapshot = themeSnapshot ?: ReadDrawerStyle.themeSnapshot(context),
    ) { dismiss ->
        ReadConfirmDialogContent(
            title = title,
            message = message,
            confirmLabel = confirmLabel,
            cancelLabel = cancelLabel,
            onConfirm = {
                actionTaken = true
                dismiss()
                onConfirm()
            },
            onCancel = cancelLabel?.let {
                {
                    actionTaken = true
                    dismiss()
                    onCancel?.invoke()
                }
            },
        )
    }
}

@Composable
internal fun ReadConfirmDialogContent(
    title: String,
    message: String? = null,
    confirmLabel: String,
    cancelLabel: String? = null,
    onConfirm: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    ReadConfigDialogSurface(
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 20.dp,
            end = 20.dp,
            bottom = 16.dp,
        ),
    ) {
        ReadConfigDialogTitle(title)
        message?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 4.dp),
                color = Color(NgTheme.colors.onSurfaceVariant),
                style = TextStyle(
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
                textAlign = TextAlign.Start,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (message.isNullOrBlank()) 18.dp else 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            cancelLabel?.let {
                NgFormActionButton(
                    text = it,
                    onClick = { onCancel?.invoke() },
                    modifier = Modifier.weight(1f),
                )
            }
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
internal fun ReadDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    hint: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = NgTheme.colors
    val shape = RoundedCornerShape(10.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val outlineColor = if (focused) {
        Color(colors.primary)
    } else {
        Color(colors.outline).copy(alpha = 0.68f)
    }
    Column(modifier = modifier) {
        label?.let {
            Text(
                text = it,
                modifier = Modifier.padding(start = 8.dp, bottom = 5.dp),
                color = Color(colors.onSurfaceVariant),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            readOnly = readOnly,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            interactionSource = interactionSource,
            textStyle = TextStyle(
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
                        .height(36.dp)
                        .clip(shape)
                        .background(Color(colors.inputContainer).copy(alpha = 0.76f))
                        .border(if (focused) 1.dp else 0.75.dp, outlineColor, shape)
                        .then(
                            if (onClick != null) {
                                Modifier.clickable(role = Role.Button, onClick = onClick)
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && hint.isNotEmpty()) {
                        Text(
                            text = hint,
                            color = Color(colors.onSurfaceVariant).copy(alpha = 0.72f),
                            fontSize = 14.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
internal fun ReadDialogSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        color = Color(NgTheme.colors.onSurfaceVariant),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
