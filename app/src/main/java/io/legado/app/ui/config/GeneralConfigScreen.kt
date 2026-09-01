package io.legado.app.ui.config

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.components.compose.NgCompactSettingsDivider
import io.legado.app.ui.design.components.compose.NgExpandableSettingsItem
import io.legado.app.ui.design.components.compose.NgFormSwitchGroup
import io.legado.app.ui.design.components.compose.NgFormSwitchRow
import io.legado.app.ui.design.components.compose.NgFormSwitchRowVariant
import io.legado.app.ui.design.components.compose.NgSettingsGroup
import io.legado.app.ui.design.components.compose.NgSettingsItem
import io.legado.app.ui.design.components.compose.NgSettingsValueChip
import io.legado.app.ui.design.theme.NgTheme

@Immutable
internal data class GeneralConfigScreenState(
    val language: String = "auto",
    val replaceEnableDefault: Boolean = true,
    val showAddToShelfAlert: Boolean = true,
    val updateToVariant: String = "default_version",
    val autoUpdateVariant: Boolean = true,
    val showMangaUi: Boolean = true,
    val processText: Boolean = true,
    val videoAutoPlay: Boolean = true,
    val videoStartFull: Boolean = false,
    val videoFullBottomProgress: Boolean = true,
    val videoLongPressSpeed: Int = 30,
)

@Composable
internal fun GeneralConfigScreen(
    state: GeneralConfigScreenState,
    onLanguageClick: () -> Unit,
    onReplaceEnableDefaultChanged: (Boolean) -> Unit,
    onShowAddToShelfAlertChanged: (Boolean) -> Unit,
    onUpdateToVariantClick: () -> Unit,
    onAutoUpdateVariantChanged: (Boolean) -> Unit,
    onShowMangaUiChanged: (Boolean) -> Unit,
    onProcessTextChanged: (Boolean) -> Unit,
    onVideoAutoPlayChanged: (Boolean) -> Unit,
    onVideoStartFullChanged: (Boolean) -> Unit,
    onVideoFullBottomProgressChanged: (Boolean) -> Unit,
    onVideoLongPressSpeedClick: () -> Unit,
) {
    val languageOptions = stringArrayResource(R.array.language_value)
        .zip(stringArrayResource(R.array.language))
        .map { (value, label) -> GeneralChoiceOption(value, label) }
    val updateVariantOptions = stringArrayResource(R.array.default_app_variant_value)
        .zip(stringArrayResource(R.array.default_app_variant))
        .map { (value, label) -> GeneralChoiceOption(value, label) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        NgSettingsGroup {
            GeneralChoiceSettingItem(
                title = stringResource(R.string.language),
                selectedValue = state.language,
                options = languageOptions,
                onClick = onLanguageClick,
            )
            GeneralSwitchSettingItem(
                title = stringResource(R.string.replace_enable_default_t),
                summary = stringResource(R.string.replace_enable_default_s),
                checked = state.replaceEnableDefault,
                onCheckedChange = onReplaceEnableDefaultChanged,
            )
            GeneralSwitchSettingItem(
                title = stringResource(R.string.show_add_to_shelf_alert_title),
                summary = stringResource(R.string.show_add_to_shelf_alert_summary),
                checked = state.showAddToShelfAlert,
                onCheckedChange = onShowAddToShelfAlertChanged,
            )
            GeneralChoiceSettingItem(
                title = stringResource(R.string.update_to_variant_title),
                summary = stringResource(R.string.update_to_variant_summary),
                selectedValue = state.updateToVariant,
                options = updateVariantOptions,
                onClick = onUpdateToVariantClick,
            )
            GeneralSwitchSettingItem(
                title = stringResource(R.string.auto_update),
                summary = stringResource(R.string.auto_update_summary),
                checked = state.autoUpdateVariant,
                onCheckedChange = onAutoUpdateVariantChanged,
            )
            GeneralSwitchSettingItem(
                title = stringResource(R.string.show_manga_ui),
                checked = state.showMangaUi,
                onCheckedChange = onShowMangaUiChanged,
            )
            GeneralVideoSettingsItem(
                state = state,
                onAutoPlayChanged = onVideoAutoPlayChanged,
                onStartFullChanged = onVideoStartFullChanged,
                onFullBottomProgressChanged = onVideoFullBottomProgressChanged,
                onLongPressSpeedClick = onVideoLongPressSpeedClick,
            )
            GeneralSwitchSettingItem(
                title = stringResource(R.string.add_to_text_context_menu_t),
                summary = stringResource(R.string.add_to_text_context_menu_s),
                checked = state.processText,
                onCheckedChange = onProcessTextChanged,
            )
        }
    }
}

@Composable
private fun GeneralSwitchSettingItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
) {
    NgSettingsItem(
        title = title,
        summary = summary,
        trailing = NgSettingsTrailing.SWITCH,
        checked = checked,
        onCheckedChange = onCheckedChange,
        onClick = { onCheckedChange(!checked) },
        summaryMaxLines = 2,
    )
}

@Composable
private fun GeneralChoiceSettingItem(
    title: String,
    selectedValue: String,
    options: List<GeneralChoiceOption>,
    onClick: () -> Unit,
    summary: String? = null,
) {
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label.orEmpty()
    NgSettingsItem(
        title = title,
        summary = summary,
        trailing = NgSettingsTrailing.CUSTOM,
        customTrailing = { NgSettingsValueChip(selectedLabel) },
        onClick = onClick,
        summaryMaxLines = 2,
    )
}

@Composable
private fun GeneralVideoSettingsItem(
    state: GeneralConfigScreenState,
    onAutoPlayChanged: (Boolean) -> Unit,
    onStartFullChanged: (Boolean) -> Unit,
    onFullBottomProgressChanged: (Boolean) -> Unit,
    onLongPressSpeedClick: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    NgExpandableSettingsItem(
        title = stringResource(R.string.video_setting),
        summary = stringResource(R.string.video_setting_summary),
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        NgFormSwitchGroup {
            NgFormSwitchRow(
                title = stringResource(R.string.auto_play),
                checked = state.videoAutoPlay,
                onCheckedChange = onAutoPlayChanged,
                variant = NgFormSwitchRowVariant.GROUPED,
            )
            AnimatedVisibility(visible = state.videoAutoPlay) {
                Column {
                    NgCompactSettingsDivider()
                    NgFormSwitchRow(
                        title = stringResource(R.string.start_full),
                        checked = state.videoStartFull,
                        onCheckedChange = onStartFullChanged,
                        variant = NgFormSwitchRowVariant.GROUPED,
                    )
                }
            }
            NgCompactSettingsDivider()
            NgFormSwitchRow(
                title = stringResource(R.string.full_bottom_progress),
                checked = state.videoFullBottomProgress,
                onCheckedChange = onFullBottomProgressChanged,
                variant = NgFormSwitchRowVariant.GROUPED,
            )
            NgCompactSettingsDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onLongPressSpeedClick)
                    .heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.press_speed_summary,
                        state.videoLongPressSpeed / 10f,
                    ),
                    modifier = Modifier.weight(1f),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(NgTheme.colors.onSurfaceVariant),
                )
            }
        }
    }
}

@Immutable
private data class GeneralChoiceOption(
    val value: String,
    val label: String,
)
