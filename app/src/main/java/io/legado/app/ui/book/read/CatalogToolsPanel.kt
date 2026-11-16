package io.legado.app.ui.book.read

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.ngDrawerContentCardColor
import io.legado.app.ui.design.theme.NgTheme

internal enum class CatalogToolsPanelVariant { STANDARD, BOOKMARK_INTEGRATED, MANGA_CAPSULE }

/** Shared inline search shell for chapter tools and bookmark filters. */
@Composable
internal fun CatalogToolsPanel(
    query: String,
    hint: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    variant: CatalogToolsPanelVariant = CatalogToolsPanelVariant.STANDARD,
    content: @Composable ColumnScope.() -> Unit,
) {
    val primary = Color(NgTheme.colors.primary)
    val muted = Color(NgTheme.colors.onSurfaceVariant)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    val integrated = variant == CatalogToolsPanelVariant.BOOKMARK_INTEGRATED
    val fieldShape = when (variant) {
        CatalogToolsPanelVariant.STANDARD -> RoundedCornerShape(13.dp)
        CatalogToolsPanelVariant.BOOKMARK_INTEGRATED -> RoundedCornerShape(8.dp)
        CatalogToolsPanelVariant.MANGA_CAPSULE -> CircleShape
    }
    val textSize = if (integrated) 13.sp else 14.sp
    Column(modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp)
        .then(if (integrated) Modifier.clip(RoundedCornerShape(14.dp))
            .background(ngDrawerContentCardColor()).padding(horizontal = 8.dp, vertical = 6.dp)
        else Modifier)) {
        Row(Modifier.fillMaxWidth().height(if (integrated) 40.dp else 44.dp).clip(fieldShape)
            .background(if (integrated) Color.Transparent else ngDrawerContentCardColor())
            .then(when {
                focused -> Modifier.border(1.dp, primary.copy(alpha = 0.5f), fieldShape)
                integrated -> Modifier.border(0.5.dp, muted.copy(alpha = 0.15f), fieldShape)
                else -> Modifier
            })
            .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_search), null, Modifier.size(18.dp), tint = primary)
            Spacer(Modifier.width(8.dp))
            BasicTextField(value = query, onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
                singleLine = true,
                textStyle = TextStyle(fontFamily = NgTheme.fontFamily,
                    color = Color(NgTheme.colors.onSurface), fontSize = textSize),
                cursorBrush = SolidColor(primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus(); keyboard?.hide() }),
                decorationBox = { inner ->
                    if (query.isEmpty() && !focused) Text(hint, color = muted.copy(alpha = 0.72f), fontSize = textSize)
                    inner()
                })
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.clear),
                        Modifier.size(18.dp), tint = muted)
                }
            } else Spacer(Modifier.width(8.dp))
        }
        content()
    }
}

@Composable
internal fun CatalogToolSwitch(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    indicatorShape: Shape = if (compact) RoundedCornerShape(4.dp) else CircleShape,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val primary = Color(NgTheme.colors.primary)
    val muted = Color(NgTheme.colors.onSurfaceVariant)
    val background = if (compact) Color.Transparent else ngDrawerContentCardColor()
        .copy(alpha = if (checked) 0.78f else 0.4f)
    Row(modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
        .background(background)
        .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
        .padding(horizontal = if (compact) 4.dp else 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        val indicator = if (enabled) primary else primary.copy(alpha = 0.45f)
        Box(Modifier.size(16.dp).clip(indicatorShape)
            .background(if (checked) indicator else Color.Transparent)
            .border(1.dp, if (checked) indicator else muted.copy(alpha = 0.45f), indicatorShape),
            contentAlignment = Alignment.Center) {
            if (checked) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(11.dp),
                tint = Color(NgTheme.colors.onPrimary))
        }
        Text(label, color = if (checked) indicator else muted, fontSize = if (compact) 11.sp else 12.sp)
    }
}

@Composable
internal fun CatalogToolAction(label: String, icon: Int, busy: Boolean = false,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.heightIn(min = 44.dp).widthIn(min = 96.dp).clip(RoundedCornerShape(10.dp))
        .background(ngDrawerContentCardColor().copy(alpha = 0.4f))
        .clickable(enabled = !busy, onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        val tint = Color(NgTheme.colors.primary)
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = tint, strokeWidth = 2.dp)
        else Icon(painterResource(icon), null, Modifier.size(16.dp), tint = tint)
        Text(label, color = tint, fontSize = 12.sp)
    }
}

@Composable
internal fun catalogFilterSelectedColor(): Color = Color(NgTheme.colors.primary)
    .copy(alpha = if (NgTheme.snapshot.isDark) 0.18f else 0.08f)
    .compositeOver(ngDrawerContentCardColor())
