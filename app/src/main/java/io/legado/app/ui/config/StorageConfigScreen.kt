package io.legado.app.ui.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.components.compose.NgSettingsGroup
import io.legado.app.ui.design.components.compose.NgSettingsItem
import io.legado.app.ui.design.components.compose.NgSettingsValueChip

@Immutable
internal data class StorageConfigScreenState(
    val defaultBookTreeUri: String? = null,
    val defaultFilePicker: String = "system",
    val bitmapCacheSize: Int = 50,
    val imageRetainNum: Int = 0,
    val preDownloadNum: Int = 10,
    val autoClearExpired: Boolean = true,
)

@Composable
internal fun StorageConfigScreen(
    state: StorageConfigScreenState,
    onDefaultBookTreeClick: () -> Unit,
    onDefaultFilePickerClick: () -> Unit,
    onBitmapCacheSizeClick: () -> Unit,
    onImageRetainNumClick: () -> Unit,
    onPreDownloadNumClick: () -> Unit,
    onAutoClearExpiredChanged: (Boolean) -> Unit,
    onClearCacheClick: () -> Unit,
    onClearWebViewDataClick: () -> Unit,
    onShrinkDatabaseClick: () -> Unit,
) {
    val filePickerOptions = stringArrayResource(R.array.default_file_picker_value)
        .zip(stringArrayResource(R.array.default_file_picker))
    val filePickerLabel = filePickerOptions
        .firstOrNull { (value, _) -> value == state.defaultFilePicker }
        ?.second
        .orEmpty()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        NgSettingsGroup {
            StorageActionSettingItem(
                title = stringResource(R.string.book_tree_uri_t),
                summary = state.defaultBookTreeUri
                    ?: stringResource(R.string.book_tree_uri_s),
                onClick = onDefaultBookTreeClick,
                summaryMaxLines = 2,
            )
            NgSettingsItem(
                title = stringResource(R.string.default_file_picker),
                summary = filePickerLabel,
                trailing = NgSettingsTrailing.CUSTOM,
                customTrailing = { NgSettingsValueChip(filePickerLabel) },
                onClick = onDefaultFilePickerClick,
                summaryMaxLines = 2,
            )
            StorageActionSettingItem(
                title = stringResource(R.string.bitmap_cache_size),
                summary = stringResource(
                    R.string.bitmap_cache_size_summary,
                    state.bitmapCacheSize,
                ),
                onClick = onBitmapCacheSizeClick,
            )
            StorageActionSettingItem(
                title = stringResource(R.string.image_retain_number),
                summary = stringResource(
                    R.string.image_retain_number_summary,
                    state.imageRetainNum,
                ),
                onClick = onImageRetainNumClick,
            )
            StorageActionSettingItem(
                title = stringResource(R.string.pre_download),
                summary = stringResource(R.string.pre_download_s, state.preDownloadNum),
                onClick = onPreDownloadNumClick,
            )
            NgSettingsItem(
                title = stringResource(R.string.auto_clear_expired),
                summary = stringResource(R.string.auto_clear_expired_summary),
                trailing = NgSettingsTrailing.SWITCH,
                checked = state.autoClearExpired,
                onCheckedChange = onAutoClearExpiredChanged,
                onClick = { onAutoClearExpiredChanged(!state.autoClearExpired) },
                summaryMaxLines = 2,
            )
            StorageActionSettingItem(
                title = stringResource(R.string.clear_cache),
                summary = stringResource(R.string.clear_cache_summary),
                onClick = onClearCacheClick,
            )
            StorageActionSettingItem(
                title = stringResource(R.string.clear_webview_data),
                summary = stringResource(R.string.clear_webview_data_summary),
                onClick = onClearWebViewDataClick,
            )
            StorageActionSettingItem(
                title = stringResource(R.string.shrink_database),
                summary = stringResource(R.string.shrink_database_summary),
                onClick = onShrinkDatabaseClick,
            )
        }
    }
}

@Composable
private fun StorageActionSettingItem(
    title: String,
    summary: String,
    onClick: () -> Unit,
    summaryMaxLines: Int = 2,
) {
    NgSettingsItem(
        title = title,
        summary = summary,
        onClick = onClick,
        summaryMaxLines = summaryMaxLines,
    )
}
