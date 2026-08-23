package io.legado.app.ui.config

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgSettingsGroup
import io.legado.app.ui.design.components.compose.NgSettingsIcon
import io.legado.app.ui.design.components.compose.NgSettingsItem

@Composable
internal fun SettingsMenuScreen(onOpenPage: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        NgSettingsGroup {
            SettingsMenuEntry(
                title = stringResource(R.string.appearance_setting),
                summary = stringResource(R.string.appearance_setting_summary),
                iconRes = R.drawable.ic_cfg_theme,
                onClick = { onOpenPage(ConfigTag.APPEARANCE_CONFIG) }
            )
            SettingsMenuEntry(
                title = stringResource(R.string.interface_layout_setting),
                summary = stringResource(R.string.interface_layout_setting_summary),
                iconRes = R.drawable.ic_interface_setting,
                onClick = { onOpenPage(ConfigTag.INTERFACE_CONFIG) }
            )
            SettingsMenuEntry(
                title = stringResource(R.string.cover_config),
                summary = stringResource(R.string.cover_config_summary),
                iconRes = R.drawable.ic_image,
                onClick = { onOpenPage(ConfigTag.COVER_CONFIG) }
            )
            SettingsMenuEntry(
                title = stringResource(R.string.general_setting),
                summary = stringResource(R.string.general_setting_summary),
                iconRes = R.drawable.ic_settings,
                onClick = { onOpenPage(ConfigTag.GENERAL_CONFIG) }
            )
            SettingsMenuEntry(
                title = stringResource(R.string.storage_cache_setting),
                summary = stringResource(R.string.storage_cache_setting_summary),
                iconRes = R.drawable.ic_storage_black_24dp,
                onClick = { onOpenPage(ConfigTag.STORAGE_CONFIG) }
            )
            SettingsMenuEntry(
                title = stringResource(R.string.advanced_setting),
                summary = stringResource(R.string.advanced_setting_summary),
                iconRes = R.drawable.ic_cfg_other,
                onClick = { onOpenPage(ConfigTag.ADVANCED_CONFIG) }
            )
        }
    }
}

@Composable
private fun SettingsMenuEntry(
    title: String,
    summary: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit
) {
    NgSettingsItem(
        title = title,
        summary = summary,
        onClick = onClick,
        leading = {
            NgSettingsIcon(
                painter = painterResource(iconRes),
                contentDescription = null
            )
        }
    )
}
