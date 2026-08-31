package io.legado.app.ui.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.components.compose.NgCompactSettingsDivider
import io.legado.app.ui.design.components.compose.NgCompactSettingsGroup
import io.legado.app.ui.design.components.compose.NgCompactSettingsItem
import io.legado.app.ui.design.components.compose.NgSettingsSectionLabel

internal data class BackupConfigScreenState(
    val webDavUrlSummary: String = "",
    val webDavAccountSummary: String = "",
    val webDavPasswordSummary: String = "",
    val webDavDirSummary: String = "legado",
    val webDavDeviceNameSummary: String = "",
    val syncBookProgress: Boolean = true,
    val syncBookProgressPlus: Boolean = false,
    val backupPathSummary: String? = null,
    val onlyLatestBackup: Boolean = true,
    val autoCheckNewBackup: Boolean = true,
)

@Composable
internal fun BackupConfigScreen(
    state: BackupConfigScreenState,
    onWebDavUrlClick: () -> Unit,
    onWebDavAccountClick: () -> Unit,
    onWebDavPasswordClick: () -> Unit,
    onWebDavDirClick: () -> Unit,
    onWebDavDeviceNameClick: () -> Unit,
    onSyncBookProgressChange: (Boolean) -> Unit,
    onSyncBookProgressPlusChange: (Boolean) -> Unit,
    onLocalPasswordClick: () -> Unit,
    onBackupPathClick: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onRestoreLongClick: () -> Unit,
    onRestoreIgnoreClick: () -> Unit,
    onImportOldClick: () -> Unit,
    onOnlyLatestBackupChange: (Boolean) -> Unit,
    onAutoCheckNewBackupChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
    ) {
        NgSettingsSectionLabel(text = stringResource(R.string.web_dav_set))
        NgCompactSettingsGroup {
            NgCompactSettingsItem(
                title = stringResource(R.string.web_dav_url),
                summary = state.webDavUrlSummary,
                onClick = onWebDavUrlClick,
            )
            NgCompactSettingsDivider()
            NgCompactSettingsItem(
                title = stringResource(R.string.web_dav_account),
                summary = state.webDavAccountSummary,
                onClick = onWebDavAccountClick,
            )
            NgCompactSettingsDivider()
            NgCompactSettingsItem(
                title = stringResource(R.string.web_dav_pw),
                summary = state.webDavPasswordSummary,
                onClick = onWebDavPasswordClick,
            )
            NgCompactSettingsDivider()
            NgCompactSettingsItem(
                title = stringResource(R.string.sub_dir),
                summary = state.webDavDirSummary,
                onClick = onWebDavDirClick,
            )
            NgCompactSettingsDivider()
            NgCompactSettingsItem(
                title = stringResource(R.string.webdav_device_name),
                summary = state.webDavDeviceNameSummary,
                onClick = onWebDavDeviceNameClick,
            )
            NgCompactSettingsDivider()
            NgCompactSettingsItem(
                title = stringResource(R.string.sync_book_progress_t),
                summary = stringResource(R.string.sync_book_progress_s),
                trailing = NgSettingsTrailing.SWITCH,
                checked = state.syncBookProgress,
                onCheckedChange = onSyncBookProgressChange,
                onClick = { onSyncBookProgressChange(!state.syncBookProgress) },
            )
            NgCompactSettingsDivider()
            NgCompactSettingsItem(
                title = stringResource(R.string.sync_book_progress_plus_t),
                summary = stringResource(R.string.sync_book_progress_plus_s),
                enabled = state.syncBookProgress,
                trailing = NgSettingsTrailing.SWITCH,
                checked = state.syncBookProgressPlus,
                onCheckedChange = onSyncBookProgressPlusChange,
                onClick = {
                    onSyncBookProgressPlusChange(!state.syncBookProgressPlus)
                },
                summaryMaxLines = 2,
            )
        }

        Spacer(Modifier.height(20.dp))
        NgSettingsSectionLabel(text = stringResource(R.string.backup_restore))
        NgCompactSettingsGroup {
            NgCompactSettingsItem(
                title = stringResource(R.string.set_local_password),
                summary = stringResource(R.string.set_local_password_summary),
                onClick = onLocalPasswordClick,
                summaryMaxLines = 2,
            )
            NgCompactSettingsDivider()
            NgCompactSettingsItem(
                title = stringResource(R.string.backup_path),
                summary = state.backupPathSummary,
                onClick = onBackupPathClick,
            )
            NgCompactSettingsDivider()
            NgCompactSettingsItem(
                title = stringResource(R.string.backup),
                summary = stringResource(R.string.backup_summary),
                onClick = onBackupClick,
            )
            NgCompactSettingsDivider()
            NgCompactSettingsItem(
                title = stringResource(R.string.restore),
                summary = stringResource(R.string.restore_summary),
                onClick = onRestoreClick,
                onLongClick = onRestoreLongClick,
            )
            NgCompactSettingsDivider()
            NgCompactSettingsItem(
                title = stringResource(R.string.restore_ignore),
                summary = stringResource(R.string.restore_ignore_summary),
                onClick = onRestoreIgnoreClick,
            )
            NgCompactSettingsDivider()
            NgCompactSettingsItem(
                title = stringResource(R.string.menu_import_old_version),
                summary = stringResource(R.string.import_old_summary),
                onClick = onImportOldClick,
            )
            NgCompactSettingsDivider()
            NgCompactSettingsItem(
                title = stringResource(R.string.only_latest_backup_t),
                summary = stringResource(R.string.only_latest_backup_s),
                trailing = NgSettingsTrailing.SWITCH,
                checked = state.onlyLatestBackup,
                onCheckedChange = onOnlyLatestBackupChange,
                onClick = { onOnlyLatestBackupChange(!state.onlyLatestBackup) },
            )
            NgCompactSettingsDivider()
            NgCompactSettingsItem(
                title = stringResource(R.string.auto_check_new_backup_t),
                summary = stringResource(R.string.auto_check_new_backup_s),
                trailing = NgSettingsTrailing.SWITCH,
                checked = state.autoCheckNewBackup,
                onCheckedChange = onAutoCheckNewBackupChange,
                onClick = { onAutoCheckNewBackupChange(!state.autoCheckNewBackup) },
            )
        }
    }
}
