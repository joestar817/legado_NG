package io.legado.app.ui.main.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.ngDrawerContentCardColor
import io.legado.app.ui.design.theme.NgTheme
import java.util.UUID

/** One drawer: a basic-glass type directory, then the selected type's sizes and skins. */
@Composable
internal fun HomeWidgetAddDrawer(
    usedWidgets: List<HomeWidgetInstance>,
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Boolean,
    onPreviewTypesChanged: (Set<String>) -> Unit = {},
    preview: @Composable (HomeWidgetInstance, HomeWidgetVariant) -> Unit,
) {
    var selectedTypeId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedVariants by rememberSaveable { mutableStateOf(mapOf<String, String>()) }
    val existingByType = remember(usedWidgets) { usedWidgets.distinctBy { it.typeId }.associateBy { it.typeId } }
    val type = HomeWidgetCatalog.type(selectedTypeId.orEmpty())
    val existing = type?.let { existingByType[it.id] }
    val selected = type?.let { entry ->
        val selectedId = selectedVariants[entry.id] ?: existing?.variantId
        entry.variants.firstOrNull { it.id == selectedId } ?: entry.variants.first()
    }
    val directoryScrollState = rememberScrollState()
    val pageStates = rememberSaveableStateHolder()
    val previewToken = rememberSaveable { UUID.randomUUID().toString() }
    var submitted by remember { mutableStateOf(false) }
    val previewTypes = remember(selectedTypeId) {
        type?.let { setOf(it.id) } ?: HomeWidgetCatalog.types.mapTo(mutableSetOf()) { it.id }
    }
    HomeWidgetPreviewTypes(previewTypes, onPreviewTypesChanged)
    HomeWidgetDrawerShell(
        title = stringResource(type?.let { HomeWidgetContents.find(it.id).titleRes }
            ?: R.string.home_add_widget),
        confirmTitle = type?.let { stringResource(if (existing != null) R.string.home_replace else R.string.home_add) },
        onDismiss = onDismiss,
        onNavigateBack = if (type != null) ({ selectedTypeId = null }) else null,
        onConfirm = {
            if (!submitted && type != null && selected != null) {
                submitted = onAdd(type.id, selected.id)
            }
        },
    ) {
        if (type == null || selected == null) {
            Spacer(Modifier.height(8.dp))
            HomeWidgetPreviewGrid(HomeWidgetCatalog.types, directoryScrollState, { it.id }) { entry ->
                val basic = entry.variants.first { it.styleId == "basic" }
                val current = existingByType[entry.id]
                val candidate = remember(previewToken, entry.id, current) {
                    current?.copy(variantId = basic.id)
                        ?: HomeWidgetInstance("add-preview:$previewToken:${entry.id}:${basic.id}", entry.id, basic.id)
                }
                HomeWidgetPreviewCard(
                    title = stringResource(HomeWidgetContents.find(entry.id).titleRes),
                    inUse = current != null,
                    onSelect = { selectedTypeId = entry.id },
                    preview = { preview(candidate, basic) },
                )
            }
        } else {
            pageStates.SaveableStateProvider(type.id) {
                HomeWidgetStyleChooser(type.id, selected, usedWidgets,
                    onSelect = { selectedVariants = selectedVariants + (type.id to it) },
                    preview = { variant ->
                        val candidate = remember(previewToken, type.id, variant.id, existing) {
                            existing?.copy(variantId = variant.id)
                                ?: HomeWidgetInstance("add-preview:$previewToken:${type.id}:${variant.id}", type.id, variant.id)
                        }
                        preview(candidate, variant)
                    })
            }
        }
    }
}

/** Shared thumbnail selector for adding a type and editing an existing instance. */
@Composable
internal fun ColumnScope.HomeWidgetStyleChooser(
    typeId: String,
    selected: HomeWidgetVariant,
    usedWidgets: List<HomeWidgetInstance>,
    onSelect: (String) -> Unit,
    preview: @Composable (HomeWidgetVariant) -> Unit,
) {
    val sizes = remember(typeId) { HomeWidgetCatalog.type(typeId)!!.variants.map { it.size }.distinct() }
    val variants = remember(typeId, selected.size) { HomeWidgetCatalog.variantsForSize(typeId, selected.size) }
    val usedIds = remember(usedWidgets, typeId) {
        usedWidgets.filter { it.typeId == typeId }.mapTo(mutableSetOf()) { it.variantId }
    }
    val smallScrollState = rememberScrollState()
    val largeScrollState = rememberScrollState()
    if (sizes.size > 1) {
        TabRow(selectedTabIndex = sizes.indexOf(selected.size),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
            containerColor = Color.Transparent, contentColor = Color(NgTheme.colors.primary)) {
            sizes.forEach { size ->
                Tab(selected = selected.size == size,
                    onClick = {
                        HomeWidgetCatalog.variantForSize(typeId, selected.id, size)?.let { onSelect(it.id) }
                    }, text = {
                        Text(stringResource(if (size == HomeWidgetSize.SMALL) R.string.home_size_small_label
                            else R.string.home_size_large_label), fontSize = 14.sp,
                            color = Color(if (selected.size == size) NgTheme.colors.primary else NgTheme.colors.onSurface))
                    })
            }
        }
    } else Spacer(Modifier.height(8.dp))
    HomeWidgetPreviewGrid(variants,
        if (selected.size == HomeWidgetSize.SMALL) smallScrollState else largeScrollState,
        { it.id }, selectable = true) { variant ->
        HomeWidgetPreviewCard(
            title = stringResource(variant.styleTitleRes),
            selected = selected.id == variant.id,
            inUse = variant.id in usedIds,
            onSelect = { onSelect(variant.id) },
            preview = { preview(variant) },
        )
    }
}

@Composable
internal fun HomeWidgetPreviewTypes(types: Set<String>, onChanged: (Set<String>) -> Unit) {
    val notifyTypes by rememberUpdatedState(onChanged)
    DisposableEffect(types) {
        notifyTypes(types)
        onDispose { notifyTypes(emptySet()) }
    }
}

/** Four types or three skins fit naturally; retain the small set of native thumbnails while scrolling. */
@Composable
private fun <T> ColumnScope.HomeWidgetPreviewGrid(
    items: List<T>,
    scrollState: ScrollState,
    itemKey: (T) -> String,
    selectable: Boolean = false,
    content: @Composable (T) -> Unit,
) {
    val minimumColumnWidth = 136.dp * LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.weight(1f, fill = false).fillMaxWidth()) {
        val columns = if ((maxWidth - 12.dp) / 2 >= minimumColumnWidth) 2 else 1
        Column(Modifier.fillMaxWidth().verticalScroll(scrollState)
            .then(if (selectable) Modifier.selectableGroup() else Modifier),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { item ->
                        key(itemKey(item)) { Box(Modifier.weight(1f)) { content(item) } }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun HomeWidgetPreviewCard(
    title: String,
    selected: Boolean? = null,
    inUse: Boolean? = null,
    onSelect: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val primary = Color(NgTheme.colors.primary)
    Column(Modifier.fillMaxWidth().clip(shape).background(ngDrawerContentCardColor())
        .border(1.dp, if (selected == true) primary else Color.Transparent, shape)
        .then(if (selected == null) Modifier.clickable(role = Role.Button, onClick = onSelect)
            else Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect))
        .padding(10.dp)) {
        Box(Modifier.fillMaxWidth().height(HomeWidgetAddPreviewHeight).clearAndSetSemantics {},
            contentAlignment = Alignment.Center) { preview() }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), color = Color(NgTheme.colors.onSurface),
                fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
            if (selected == true) Icon(painterResource(R.drawable.ic_check), null, tint = primary,
                modifier = Modifier.size(18.dp))
            else if (selected != null) Spacer(Modifier.size(18.dp))
        }
        if (inUse != null) {
            Text(stringResource(if (inUse) R.string.home_widget_in_use else R.string.home_widget_not_in_use),
                color = if (inUse) primary else Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}
