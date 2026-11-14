package io.legado.app.ui.book.read.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgSurface
import io.legado.app.ui.design.theme.NgTheme

internal data class ReadTipChoiceUi(
    val value: Int,
    val name: String,
    val icon: Int,
    val selected: Boolean,
    val usage: String?,
)

@Composable
internal fun ReadTipSettingsTabs(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    accessibilityLabel: String,
) {
    ReadConfigDock(
        labels = listOf(
            stringResource(R.string.information),
            stringResource(R.string.read_tip_tab_layout),
            stringResource(R.string.read_tip_tab_image),
        ),
        selectedIndex = selectedIndex,
        onSelected = onSelected,
        accessibilityLabel = accessibilityLabel,
    )
}

@Composable
internal fun ReadTipInformationPanel(
    enabledTitle: String,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    headerBackButton: Boolean?,
    onHeaderBackButtonChanged: (Boolean) -> Unit,
    position: Int,
    onPositionChanged: (Int) -> Unit,
    positionSummaries: List<String>,
    choices: List<ReadTipChoiceUi>,
    onChoiceSelected: (Int) -> Unit,
    areaLabel: String,
) {
    NgSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
    ) {
        ReadConfigSwitchRow(
            title = enabledTitle,
            checked = enabled,
            onCheckedChange = onEnabledChanged,
            variant = ReadConfigSwitchRowVariant.READ_INFO_COMPACT,
        )
        if (enabled) {
            if (headerBackButton != null) {
                NgFormGroupDivider(horizontalPadding = 0.dp)
                ReadConfigSwitchRow(
                    title = stringResource(R.string.read_header_back_button),
                    checked = headerBackButton,
                    onCheckedChange = onHeaderBackButtonChanged,
                    variant = ReadConfigSwitchRowVariant.READ_INFO_COMPACT,
                )
            }
            NgFormGroupDivider(horizontalPadding = 0.dp)
            ReadTipPositionSelector(position, onPositionChanged, positionSummaries, areaLabel)
            Spacer(Modifier.height(4.dp))
            Column(
                Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                choices.chunked(2).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        rowItems.forEach { item ->
                            ReadTipOption(
                                name = item.name,
                                icon = item.icon,
                                selected = item.selected,
                                usage = item.usage,
                                onClick = { onChoiceSelected(item.value) },
                                modifier = Modifier.weight(1f),
                                variant = ReadTipOptionVariant.HEADER_CHOICE,
                            )
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ReadTipPositionSelector(
    position: Int,
    onPositionChanged: (Int) -> Unit,
    summaries: List<String>,
    areaLabel: String,
) {
    val colors = NgTheme.colors
    val labels = listOf(
        stringResource(R.string.left),
        stringResource(R.string.middle),
        stringResource(R.string.right),
    )
    Row(
        Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = areaLabel },
    ) {
        labels.forEachIndexed { index, label ->
            val selected = position == index
            Column(
                Modifier.weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(selected = selected, role = Role.Tab) { onPositionChanged(index) }
                    .padding(top = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = label,
                    color = Color(if (selected) colors.primary else colors.onSurface),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    text = summaries.getOrElse(index) { "" },
                    color = Color(if (selected) colors.primary else colors.onSurfaceVariant),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.width(64.dp).height(2.dp)
                        .background(if (selected) Color(colors.primary) else Color.Transparent),
                )
            }
        }
    }
}
