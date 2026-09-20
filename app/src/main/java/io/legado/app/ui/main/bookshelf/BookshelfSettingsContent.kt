package io.legado.app.ui.main.bookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.config.*
import io.legado.app.constant.PreferKey
import io.legado.app.ui.design.components.compose.*
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.putPrefBoolean
import splitties.init.appCtx

/** Ephemeral editor state, never serialized into a backup or database. */
internal data class BookshelfSettingsDraft(
    val topStyle: Int = AppConfig.bookshelfTopBarStyle.value,
    val searchPosition: Int = AppConfig.bookshelfFloatingDockSearchPosition.value,
    val topDistance: Int = AppConfig.bookshelfFloatingDockTopDistancePx,
    val topTransparency: Int = AppConfig.bookshelfFloatingDockTransparency,
    val floatingBottom: Boolean = AppConfig.useFloatingBottomBar,
    val bottomDistance: Int = AppConfig.floatingBottomBarBottomDistancePx,
    val bottomTransparency: Int = AppConfig.floatingBottomBarTransparency,
    val swipeMode: Int = BookshelfGestureConfig.mode.value,
    val aiSwipe: Boolean = AiConfig.bookshelfSwipeEnabled,
) {
    fun save(): Boolean {
        val old = BookshelfSettingsDraft()
        AppConfig.bookshelfTopBarStyle = BookshelfTopBarStyle.fromValue(topStyle)
        AppConfig.bookshelfFloatingDockSearchPosition = BookshelfFloatingDockSearchPosition.fromValue(searchPosition)
        AppConfig.bookshelfFloatingDockTopDistancePx = topDistance
        AppConfig.bookshelfFloatingDockTransparency = topTransparency
        appCtx.putPrefBoolean(PreferKey.useFloatingBottomBar, floatingBottom)
        AppConfig.floatingBottomBarBottomDistancePx = bottomDistance
        AppConfig.floatingBottomBarTransparency = bottomTransparency
        BookshelfGestureConfig.mode = BookshelfSwipeMode.fromValue(swipeMode)
        AiConfig.bookshelfSwipeEnabled = aiSwipe
        return old.copy(swipeMode = swipeMode, aiSwipe = aiSwipe) != this
    }
}

@Composable
internal fun BookshelfSettingsFields(
    tab: Int,
    draft: BookshelfSettingsDraft,
    onChange: (BookshelfSettingsDraft) -> Unit,
) {
    val metrics = androidx.compose.ui.platform.LocalContext.current.resources.displayMetrics
    when (tab) {
        1 -> {
            NgFormGroup(stringResource(R.string.bookshelf_top_bar_style)) {
                SettingsChoiceRail(
                    listOf(stringResource(R.string.bookshelf_top_bar_compact_toolbar), stringResource(R.string.bookshelf_top_bar_group_navigation)),
                    draft.topStyle,
                ) { onChange(draft.copy(topStyle = it)) }
            }
            NgFormPanel {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.bookshelf_floating_dock_search_position),
                        modifier = Modifier.weight(1f), fontSize = 14.sp, color = Color(NgTheme.colors.onSurface))
                    NgFloatingTabBar(
                        items = listOf(NgFloatingTabSpec(text = stringResource(R.string.left)), NgFloatingTabSpec(text = stringResource(R.string.right))),
                        selectedIndex = draft.searchPosition,
                        onTabSelected = { onChange(draft.copy(searchPosition = it)) },
                        modifier = Modifier.width(132.dp),
                    )
                }
                NgFormGroupDivider()
                SettingsSlider(stringResource(R.string.bookshelf_floating_dock_top_distance),
                    BookshelfFloatingDockConfig.resolveTopDistancePx(draft.topDistance, metrics.widthPixels, metrics.density, 0),
                    0..500, "px") { onChange(draft.copy(topDistance = BookshelfFloatingDockConfig.normalizeTopDistancePx(it))) }
                NgFormGroupDivider()
                SettingsSlider(stringResource(R.string.bookshelf_floating_dock_transparency), draft.topTransparency, 0..100, "%") {
                    onChange(draft.copy(topTransparency = it))
                }
            }
        }
        2 -> {
            NgFormGroup(stringResource(R.string.main_bottom_bar_style)) {
                SettingsChoiceRail(listOf(stringResource(R.string.traditional_bottom_bar), stringResource(R.string.floating_bottom_bar)),
                    if (draft.floatingBottom) 1 else 0) { onChange(draft.copy(floatingBottom = it == 1)) }
            }
            if (draft.floatingBottom) {
                NgFormPanel {
                    SettingsSlider(stringResource(R.string.floating_bottom_bar_bottom_distance),
                        FloatingBottomBarConfig.resolveBottomDistancePx(draft.bottomDistance, metrics.density), 0..100, "px") {
                        onChange(draft.copy(bottomDistance = FloatingBottomBarConfig.normalizeBottomDistancePx(it)))
                    }
                    NgFormGroupDivider()
                    SettingsSlider(stringResource(R.string.floating_bottom_bar_transparency), draft.bottomTransparency, 0..100, "%") {
                        onChange(draft.copy(bottomTransparency = it))
                    }
                }
            }
        }
        3 -> {
            NgFormGroup(stringResource(R.string.bookshelf_swipe_pages)) {
                Column(Modifier.selectableGroup()) {
                    listOf(R.string.bookshelf_swipe_main, R.string.bookshelf_swipe_groups, R.string.close).forEachIndexed { index, title ->
                        Row(
                            Modifier.fillMaxWidth().selectable(draft.swipeMode == index, role = Role.RadioButton,
                                onClick = { onChange(draft.copy(swipeMode = index)) }).padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = draft.swipeMode == index, onClick = null)
                            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                Text(stringResource(title), fontSize = 14.sp, color = Color(NgTheme.colors.onSurface))
                                if (index == 1) SettingsNote(stringResource(R.string.bookshelf_swipe_groups_summary))
                            }
                        }
                        if (index != 2) NgFormGroupDivider()
                    }
                }
            }
            NgFormGroup(stringResource(R.string.bookshelf_shortcuts)) {
                NgFormSwitchSettingRow(
                    title = stringResource(R.string.bookshelf_swipe_ai),
                    summary = stringResource(R.string.bookshelf_swipe_ai_summary),
                    checked = draft.aiSwipe,
                    onCheckedChange = { onChange(draft.copy(aiSwipe = it)) },
                )
            }
        }
    }
}

@Composable
private fun SettingsChoiceRail(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(4.dp).selectableGroup()) {
        titles.forEachIndexed { index, title ->
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp)
                    .background(if (selected == index) Color(NgTheme.colors.primary).copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(8.dp))
                    .selectable(selected == index, role = Role.Tab, onClick = { onSelect(index) }).padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(title, fontSize = 14.sp, color = Color(if (selected == index) NgTheme.colors.primary else NgTheme.colors.onSurface))
            }
        }
    }
}

@Composable
private fun SettingsSlider(title: String, value: Int, range: IntRange, unit: String, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 14.sp, color = Color(NgTheme.colors.onSurface))
        NgSlider(value = value.toFloat(), onValueChange = { onChange(it.toInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(), variant = NgSliderVariant.COMPACT,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
        Text(if (unit == "%") "$value%" else "$value $unit", fontSize = 13.sp,
            color = Color(NgTheme.colors.onSurfaceVariant), modifier = Modifier.widthIn(min = 42.dp))
    }
}

@Composable
private fun SettingsNote(text: String) {
    Text(text, color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 12.sp, lineHeight = 17.sp,
        modifier = Modifier.padding(top = 4.dp))
}
