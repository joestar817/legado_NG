package io.legado.app.ui.main.bookshelf

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.BookshelfCardAppearance
import io.legado.app.help.config.BookshelfCardMaterial
import io.legado.app.help.config.BookshelfCardStyle
import io.legado.app.ui.design.components.compose.*
import io.legado.app.ui.design.theme.NgTheme

internal val bookshelfCardAppearanceSaver = listSaver<BookshelfCardAppearance, Int>(
    save = { listOf(it.day.material.value, it.day.transparentPercent, it.day.liquidPercent,
        it.night.material.value, it.night.transparentPercent, it.night.liquidPercent) },
    restore = { BookshelfCardAppearance(
        BookshelfCardStyle(BookshelfCardMaterial.fromValue(it[0]), it[1], it[2]),
        BookshelfCardStyle(BookshelfCardMaterial.fromValue(it[3]), it[4], it[5]),
    ) },
)

@Composable
internal fun bookshelfCardMaterialTitle(material: BookshelfCardMaterial): String = stringResource(
    when (material) {
        BookshelfCardMaterial.SOLID -> R.string.bookshelf_card_solid
        BookshelfCardMaterial.TRANSPARENT -> R.string.ng_visual_system_transparent_glass
        BookshelfCardMaterial.LIQUID -> R.string.ng_visual_system_liquid_glass
    },
)

@Composable
internal fun BookshelfCardAppearanceFields(
    appearance: BookshelfCardAppearance,
    onChange: (BookshelfCardAppearance) -> Unit,
) {
    val currentNight = NgTheme.snapshot.isDark
    var editingNight by rememberSaveable { mutableStateOf(currentNight) }
    val style = appearance.forNight(editingNight)
    Column(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NgFloatingTabBar(
            items = listOf(
                NgFloatingTabSpec(text = stringResource(R.string.theme_mode_day_short)),
                NgFloatingTabSpec(text = stringResource(R.string.theme_mode_night_short)),
            ),
            selectedIndex = if (editingNight) 1 else 0,
            onTabSelected = { editingNight = it == 1 },
            modifier = Modifier.fillMaxWidth(),
        )
        NgFlatActionRail(
            items = BookshelfCardMaterial.entries.map {
                NgFlatActionRailItem(label = bookshelfCardMaterialTitle(it), emphasized = style.material == it)
            },
            onItemClick = {
                onChange(appearance.updated(editingNight, style.copy(material = BookshelfCardMaterial.entries[it])))
            },
            variant = NgFlatActionRailVariant.TEXT_MODE_PICKER,
        )
        if (style.material != BookshelfCardMaterial.SOLID) {
            SettingsSlider(stringResource(R.string.bookshelf_card_transparency), style.transparency, 0..100, "%") {
                onChange(appearance.updated(editingNight, style.withTransparency(it)))
            }
        }
    }
}
