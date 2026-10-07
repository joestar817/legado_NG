package io.legado.app.ui.main.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.ngDrawerContentCardColor
import io.legado.app.ui.design.theme.NgTheme

/** Only the local choice changes until Apply commits the whole surrounding layout. */
@Composable
internal fun HomeWidgetSuiteDrawer(
    usedWidgets: List<HomeWidgetInstance>,
    onDismiss: () -> Unit,
    onSave: (String) -> Boolean,
    onPreviewTypesChanged: (Set<String>) -> Unit,
    preview: @Composable (String) -> Unit,
) {
    val currentStyle = remember(usedWidgets) { homeWidgetCommonStyle(usedWidgets) }
    val styles = remember {
        HomeWidgetCatalog.variantsForSize("reading", HomeWidgetSize.SMALL)
    }
    var selectedId by rememberSaveable(usedWidgets) { mutableStateOf(currentStyle) }
    var submitted by remember { mutableStateOf(false) }
    val previewTypes = remember { HomeWidgetCatalog.types.mapTo(mutableSetOf()) { it.id } }
    HomeWidgetPreviewTypes(previewTypes, onPreviewTypesChanged)
    HomeWidgetDrawerShell(
        title = stringResource(R.string.home_suite_title),
        confirmTitle = stringResource(R.string.home_suite_apply),
        onDismiss = onDismiss,
        onConfirm = {
            val styleId = selectedId
            if (!submitted && styleId != null) submitted = onSave(styleId)
        },
        confirmEnabled = selectedId != null && !submitted && usedWidgets.isNotEmpty(),
    ) {
        Text(
            stringResource(if (currentStyle == null) R.string.home_suite_mixed else R.string.home_suite_keep_layout),
            color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        val scrollState = rememberScrollState()
        val fontScale = LocalDensity.current.fontScale
        BoxWithConstraints(Modifier.weight(1f, fill = false).fillMaxWidth()) {
            val previewHeight = (maxHeight - 124.dp).coerceIn(220.dp, 360.dp)
            val columns = if (maxWidth / 3 >= 92.dp * fontScale) 3 else 1
            Column(Modifier.fillMaxWidth().verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().height(previewHeight).clearAndSetSemantics {},
                    contentAlignment = Alignment.Center) {
                    selectedId?.let { preview(it) } ?: Text(
                        stringResource(R.string.home_suite_choose),
                        color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 14.sp,
                    )
                }
                Column(Modifier.fillMaxWidth().selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    styles.chunked(columns).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { style ->
                                HomeWidgetSuiteChoice(style, selectedId == style.styleId,
                                    currentStyle == style.styleId, { selectedId = style.styleId },
                                    Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeWidgetSuiteChoice(
    style: HomeWidgetVariant,
    selected: Boolean,
    inUse: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier,
) {
    val primary = Color(NgTheme.colors.primary)
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.clip(shape).background(ngDrawerContentCardColor())
        .border(1.dp, if (selected) primary else Color.Transparent, shape)
        .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
        .padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(8.dp))
            .clearAndSetSemantics {}) {
            HomeWidgetSuiteSwatch(style.styleId)
            if (selected) Icon(painterResource(R.drawable.ic_check), null, tint = primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(18.dp)
                    .background(ngDrawerContentCardColor(), RoundedCornerShape(9.dp)))
        }
        Text(stringResource(style.styleTitleRes), color = Color(NgTheme.colors.onSurface),
            fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
        Text(if (inUse) stringResource(R.string.home_widget_in_use) else "",
            color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 10.sp, lineHeight = 16.sp)
    }
}

/** Reuse existing skin artwork; the three choices never create three live widget trees. */
@Composable
private fun HomeWidgetSuiteSwatch(styleId: String) {
    if (styleId == "basic") {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(
            Color(0xFFD9F8F5), Color(0xFFF7FCFA), Color(0xFFC4E8D7),
        ))))
    } else Box(Modifier.fillMaxSize()) {
        HomeWidgetSkinBackdrop(styleId, modifier = Modifier.fillMaxSize(), small = styleId == "storybook")
        Image(homeWidgetArtworkPainter(if (styleId == "storybook")
            R.drawable.ng_home_calendar_story_cat else R.drawable.ng_home_calendar_night_lamp),
            contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.BottomEnd).size(36.dp))
    }
}
