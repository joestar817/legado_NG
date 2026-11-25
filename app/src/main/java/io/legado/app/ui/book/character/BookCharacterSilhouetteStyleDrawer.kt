package io.legado.app.ui.book.character

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.BookCharacter
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.components.compose.ngDrawerContentCardColor
import io.legado.app.ui.design.components.compose.ngDrawerPrimaryContentColor
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun BookCharacterSilhouetteStyleDrawer(
    onSave: (BookCharacterSilhouetteStyle?) -> Unit,
) {
    val storedStyle = rememberBookCharacterSilhouetteStyle()
    var draftKey by rememberSaveable { mutableStateOf<String?>(null) }
    val draftStyle = draftKey?.let(BookCharacterSilhouetteStyle::fromKey)
    BookCharacterDrawer(
        title = stringResource(R.string.character_silhouette_style),
        confirmText = stringResource(R.string.save),
        onSave = { onSave(draftStyle) },
    ) {
        NgFormPanel {
            BookCharacterSilhouetteOptions(
                selected = draftStyle ?: storedStyle,
                enabled = true,
                onSelect = { style -> draftKey = style.takeUnless { it == storedStyle }?.key },
                modifier = Modifier.padding(14.dp),
            )
        }
    }
}

@Composable
internal fun BookCharacterDrawer(
    title: String,
    confirmText: String,
    onSave: () -> Unit,
    saveEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.91f).dp
    val focusManager = LocalFocusManager.current
    NgBottomDrawerSurface(
        modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight),
        contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            NgLongDrawerHeader(title = title, centerTitle = true)
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
            NgFormActionButton(
                text = confirmText,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                enabled = saveEnabled,
                variant = NgButtonVariant.PRIMARY,
                buttonHeight = 44.dp,
                textSize = 15.sp,
                textLineHeight = 19.sp,
                onClick = {
                    focusManager.clearFocus()
                    onSave()
                },
            )
        }
    }
}

@Composable
internal fun BookCharacterSilhouetteOptions(
    selected: BookCharacterSilhouetteStyle,
    enabled: Boolean,
    onSelect: (BookCharacterSilhouetteStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NgTheme.colors
    val auto = BookCharacterSilhouetteStyle.AUTO
    val autoSelected = selected == auto
    Column(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.character_silhouette_scope),
            color = Color(colors.onSurfaceVariant),
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        Surface(
            modifier = Modifier.fillMaxWidth().selectable(
                selected = autoSelected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = { onSelect(auto) },
            ),
            color = if (autoSelected) Color(colors.selectedContainer) else ngDrawerContentCardColor(),
            shape = RoundedCornerShape(NgTheme.shapes.smallDp.dp),
            border = BorderStroke(
                1.dp,
                if (autoSelected) Color(colors.primary) else Color(colors.outlineVariant).copy(alpha = 0.55f),
            ),
        ) {
            Row(
                Modifier.heightIn(min = 44.dp).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(auto.labelRes),
                    color = Color(if (autoSelected) colors.primary else colors.onSurface),
                    fontSize = 14.sp,
                )
                Text(
                    text = stringResource(R.string.character_silhouette_auto_hint),
                    modifier = Modifier.weight(1f),
                    color = Color(colors.onSurfaceVariant),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
                if (autoSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(colors.primary),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            for (style in BookCharacterSilhouetteStyle.entries) {
                if (style == auto) continue
                BookCharacterSilhouetteTile(
                    style = style,
                    selected = selected == style,
                    enabled = enabled,
                    onClick = { onSelect(style) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BookCharacterSilhouetteTile(
    style: BookCharacterSilhouetteStyle,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NgTheme.colors
    val accent = Color(colors.primary)
    Surface(
        modifier = modifier.selectable(selected, enabled, role = Role.RadioButton, onClick = onClick),
        color = if (selected) Color(colors.selectedContainer) else ngDrawerContentCardColor(),
        shape = RoundedCornerShape(NgTheme.shapes.smallDp.dp),
        border = BorderStroke(
            1.dp,
            if (selected) accent else Color(colors.outlineVariant).copy(alpha = 0.55f),
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(70.dp)) {
                BookCharacterPortraitImage(
                    portrait = null,
                    silhouette = style.forRole(BookCharacter.RoleTag.MALE_LEAD),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    alignment = BiasAlignment(0f, -0.4f),
                )
                if (selected) {
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp)
                            .clip(CircleShape).background(accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = ngDrawerPrimaryContentColor(),
                        )
                    }
                }
            }
            Text(
                text = stringResource(style.labelRes),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
                color = Color(if (selected) colors.primary else colors.onSurface),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
            )
        }
    }
}

@get:StringRes
internal val BookCharacterSilhouetteStyle.labelRes: Int
    get() = when (this) {
        BookCharacterSilhouetteStyle.AUTO -> R.string.character_silhouette_auto
        BookCharacterSilhouetteStyle.GENERIC -> R.string.character_silhouette_generic
        BookCharacterSilhouetteStyle.ANCIENT -> R.string.character_silhouette_ancient
        BookCharacterSilhouetteStyle.MODERN -> R.string.character_silhouette_modern
        BookCharacterSilhouetteStyle.FANTASY -> R.string.character_silhouette_fantasy
    }
