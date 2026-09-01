package io.legado.app.ui.config

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.activityViewModels
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.model.ImageProvider
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.SelectDirectoryContract
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean

class StorageConfigFragment : BaseFragment(R.layout.fragment_storage_config) {

    private val viewModel by activityViewModels<ConfigViewModel>()
    private var screenState by mutableStateOf(StorageConfigScreenState())
    private var activeDialog by mutableStateOf<StorageConfigDialog?>(null)
    private val localBookTreeSelect = registerForActivityResult(SelectDirectoryContract()) {
        it.uri?.let { treeUri ->
            AppConfig.defaultBookTreeUri = treeUri.toString()
            screenState = screenState.copy(defaultBookTreeUri = treeUri.toString())
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        activity?.setTitle(R.string.storage_cache_setting)
        refreshContent()
        (view as ComposeView).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            setContent {
                NgAppTheme {
                    StorageConfigScreen(
                        state = screenState,
                        onDefaultBookTreeClick = {
                            localBookTreeSelect.launch(null)
                        },
                        onDefaultFilePickerClick = ::showDefaultFilePickerDialog,
                        onBitmapCacheSizeClick = ::showBitmapCacheSizeDialog,
                        onImageRetainNumClick = ::showImageRetainNumDialog,
                        onPreDownloadNumClick = ::showPreDownloadNumDialog,
                        onAutoClearExpiredChanged = ::setAutoClearExpired,
                        onClearCacheClick = ::confirmClearCache,
                        onClearWebViewDataClick = ::confirmClearWebViewData,
                        onShrinkDatabaseClick = ::confirmShrinkDatabase,
                    )
                    when (activeDialog) {
                        StorageConfigDialog.FILE_PICKER -> ConfigChoiceDialog(
                            title = getString(R.string.default_file_picker),
                            options = choiceOptions(
                                R.array.default_file_picker,
                                R.array.default_file_picker_value,
                            ),
                            selectedValue = screenState.defaultFilePicker,
                            onDismissRequest = { activeDialog = null },
                            onSelected = { value ->
                                activeDialog = null
                                AppConfig.defaultFilePicker = value
                                screenState = screenState.copy(defaultFilePicker = value)
                            },
                        )
                        StorageConfigDialog.BITMAP_CACHE -> ConfigNumberPickerDialog(
                            title = getString(R.string.bitmap_cache_size),
                            minValue = 1,
                            maxValue = 1024,
                            initialValue = screenState.bitmapCacheSize,
                            cancelText = getString(R.string.cancel),
                            confirmText = getString(R.string.ok),
                            onDismissRequest = { activeDialog = null },
                            onValueSelected = ::setBitmapCacheSize,
                        )
                        StorageConfigDialog.IMAGE_RETAIN -> ConfigNumberPickerDialog(
                            title = getString(R.string.image_retain_number),
                            minValue = 0,
                            maxValue = 999,
                            initialValue = screenState.imageRetainNum,
                            cancelText = getString(R.string.cancel),
                            confirmText = getString(R.string.ok),
                            onDismissRequest = { activeDialog = null },
                            onValueSelected = ::setImageRetainNum,
                        )
                        StorageConfigDialog.PRE_DOWNLOAD -> ConfigNumberPickerDialog(
                            title = getString(R.string.pre_download),
                            minValue = 0,
                            maxValue = 9999,
                            initialValue = screenState.preDownloadNum,
                            cancelText = getString(R.string.cancel),
                            confirmText = getString(R.string.ok),
                            onDismissRequest = { activeDialog = null },
                            onValueSelected = ::setPreDownloadNum,
                        )
                        StorageConfigDialog.CLEAR_CACHE -> ConfigConfirmationDialog(
                            title = getString(R.string.clear_cache),
                            message = getString(R.string.sure_del),
                            cancelText = getString(R.string.no),
                            confirmText = getString(R.string.ok),
                            danger = true,
                            onDismissRequest = { activeDialog = null },
                            onConfirm = {
                                activeDialog = null
                                viewModel.clearCache()
                            },
                        )
                        StorageConfigDialog.CLEAR_WEBVIEW -> ConfigConfirmationDialog(
                            title = getString(R.string.clear_webview_data),
                            message = getString(R.string.sure_del),
                            cancelText = getString(R.string.no),
                            confirmText = getString(R.string.ok),
                            danger = true,
                            onDismissRequest = { activeDialog = null },
                            onConfirm = {
                                activeDialog = null
                                viewModel.clearWebViewData()
                            },
                        )
                        StorageConfigDialog.SHRINK_DATABASE -> ConfigConfirmationDialog(
                            title = getString(R.string.sure),
                            message = getString(R.string.shrink_database),
                            cancelText = getString(R.string.no),
                            confirmText = getString(R.string.ok),
                            onDismissRequest = { activeDialog = null },
                            onConfirm = {
                                activeDialog = null
                                viewModel.shrinkDatabase()
                            },
                        )
                        null -> Unit
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.storage_cache_setting)
        if (view != null) refreshContent()
    }

    private fun refreshContent() {
        screenState = StorageConfigScreenState(
            defaultBookTreeUri = AppConfig.defaultBookTreeUri,
            defaultFilePicker = AppConfig.defaultFilePicker,
            bitmapCacheSize = AppConfig.bitmapCacheSize,
            imageRetainNum = AppConfig.imageRetainNum,
            preDownloadNum = AppConfig.preDownloadNum,
            autoClearExpired = getPrefBoolean(PreferKey.autoClearExpired, true),
        )
    }

    private fun showDefaultFilePickerDialog() {
        activeDialog = StorageConfigDialog.FILE_PICKER
    }

    private fun showBitmapCacheSizeDialog() {
        activeDialog = StorageConfigDialog.BITMAP_CACHE
    }

    private fun showImageRetainNumDialog() {
        activeDialog = StorageConfigDialog.IMAGE_RETAIN
    }

    private fun showPreDownloadNumDialog() {
        activeDialog = StorageConfigDialog.PRE_DOWNLOAD
    }

    private fun setAutoClearExpired(enabled: Boolean) {
        putPrefBoolean(PreferKey.autoClearExpired, enabled)
        screenState = screenState.copy(autoClearExpired = enabled)
    }

    private fun confirmClearCache() {
        activeDialog = StorageConfigDialog.CLEAR_CACHE
    }

    private fun confirmClearWebViewData() {
        activeDialog = StorageConfigDialog.CLEAR_WEBVIEW
    }

    private fun confirmShrinkDatabase() {
        activeDialog = StorageConfigDialog.SHRINK_DATABASE
    }

    private fun setBitmapCacheSize(value: Int) {
        activeDialog = null
        AppConfig.bitmapCacheSize = value
        ImageProvider.bitmapLruCache.resize(ImageProvider.cacheSize)
        screenState = screenState.copy(bitmapCacheSize = value)
    }

    private fun setImageRetainNum(value: Int) {
        activeDialog = null
        AppConfig.imageRetainNum = value
        screenState = screenState.copy(imageRetainNum = value)
    }

    private fun setPreDownloadNum(value: Int) {
        activeDialog = null
        AppConfig.preDownloadNum = value
        screenState = screenState.copy(preDownloadNum = value)
    }

    private fun choiceOptions(entriesRes: Int, valuesRes: Int): List<ConfigChoiceOption> {
        val entries = resources.getStringArray(entriesRes)
        val values = resources.getStringArray(valuesRes)
        return entries.zip(values).map { (label, value) ->
            ConfigChoiceOption(label = label, value = value)
        }
    }

    private enum class StorageConfigDialog {
        FILE_PICKER,
        BITMAP_CACHE,
        IMAGE_RETAIN,
        PRE_DOWNLOAD,
        CLEAR_CACHE,
        CLEAR_WEBVIEW,
        SHRINK_DATABASE,
    }
}
