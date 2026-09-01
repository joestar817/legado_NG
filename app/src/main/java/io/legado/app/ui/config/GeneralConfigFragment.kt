package io.legado.app.ui.config

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.PreferKey
import io.legado.app.model.VideoPlay
import io.legado.app.receiver.SharedReceiverActivity
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import io.legado.app.utils.restart
import splitties.init.appCtx

class GeneralConfigFragment : BaseFragment(R.layout.fragment_general_config) {

    private val packageManager = appCtx.packageManager
    private val processTextComponent = ComponentName(
        appCtx,
        SharedReceiverActivity::class.java.name,
    )
    private var screenState by mutableStateOf(GeneralConfigScreenState())
    private var activeDialog by mutableStateOf<GeneralConfigDialog?>(null)

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        activity?.setTitle(R.string.general_setting)
        putPrefBoolean(PreferKey.processText, isProcessTextEnabled())
        refreshContent()
        (view as ComposeView).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            setContent {
                NgAppTheme {
                    GeneralConfigScreen(
                        state = screenState,
                        onLanguageClick = ::showLanguageDialog,
                        onReplaceEnableDefaultChanged = {
                            setBooleanPreference(PreferKey.replaceEnableDefault, it)
                        },
                        onShowAddToShelfAlertChanged = {
                            setBooleanPreference(PreferKey.showAddToShelfAlert, it)
                        },
                        onUpdateToVariantClick = ::showUpdateToVariantDialog,
                        onAutoUpdateVariantChanged = {
                            setBooleanPreference("autoUpdateVariant", it)
                        },
                        onShowMangaUiChanged = {
                            setBooleanPreference(PreferKey.showMangaUi, it)
                        },
                        onProcessTextChanged = ::setProcessTextEnabled,
                        onVideoAutoPlayChanged = ::setVideoAutoPlay,
                        onVideoStartFullChanged = ::setVideoStartFull,
                        onVideoFullBottomProgressChanged = ::setVideoFullBottomProgress,
                        onVideoLongPressSpeedClick = ::showVideoLongPressSpeedDialog,
                    )
                    when (activeDialog) {
                        GeneralConfigDialog.LANGUAGE -> ConfigChoiceDialog(
                            title = getString(R.string.language),
                            options = choiceOptions(R.array.language, R.array.language_value),
                            selectedValue = screenState.language,
                            onDismissRequest = { activeDialog = null },
                            onSelected = { value ->
                                activeDialog = null
                                setLanguage(value)
                            },
                        )
                        GeneralConfigDialog.UPDATE_VARIANT -> ConfigChoiceDialog(
                            title = getString(R.string.update_to_variant_title),
                            options = choiceOptions(
                                R.array.default_app_variant,
                                R.array.default_app_variant_value,
                            ),
                            selectedValue = screenState.updateToVariant,
                            onDismissRequest = { activeDialog = null },
                            onSelected = { value ->
                                activeDialog = null
                                setUpdateToVariant(value)
                            },
                        )
                        GeneralConfigDialog.VIDEO_SPEED -> ConfigNumberPickerDialog(
                            title = getString(R.string.press_speed),
                            minValue = 5,
                            maxValue = 60,
                            initialValue = screenState.videoLongPressSpeed,
                            decimalMode = true,
                            defaultValue = DEFAULT_VIDEO_LONG_PRESS_SPEED,
                            defaultText = getString(R.string.btn_default_s),
                            cancelText = getString(R.string.cancel),
                            confirmText = getString(R.string.ok),
                            onDismissRequest = { activeDialog = null },
                            onValueSelected = { value ->
                                activeDialog = null
                                setVideoLongPressSpeed(value)
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
        activity?.setTitle(R.string.general_setting)
        if (view != null) refreshContent()
    }

    private fun refreshContent() {
        screenState = GeneralConfigScreenState(
            language = getPrefString(PreferKey.language, "auto") ?: "auto",
            replaceEnableDefault = getPrefBoolean(PreferKey.replaceEnableDefault, true),
            showAddToShelfAlert = getPrefBoolean(PreferKey.showAddToShelfAlert, true),
            updateToVariant = getPrefString(
                PreferKey.updateToVariant,
                "default_version",
            ) ?: "default_version",
            autoUpdateVariant = getPrefBoolean("autoUpdateVariant", true),
            showMangaUi = getPrefBoolean(PreferKey.showMangaUi, true),
            processText = isProcessTextEnabled(),
            videoAutoPlay = VideoPlay.autoPlay,
            videoStartFull = VideoPlay.startFull,
            videoFullBottomProgress = VideoPlay.fullBottomProgressBar,
            videoLongPressSpeed = VideoPlay.longPressSpeed,
        )
    }

    private fun setLanguage(value: String) {
        if (value == screenState.language) return
        putPrefString(PreferKey.language, value)
        screenState = screenState.copy(language = value)
        view?.postDelayed({ appCtx.restart() }, LANGUAGE_RESTART_DELAY_MILLIS)
    }

    private fun showLanguageDialog() {
        activeDialog = GeneralConfigDialog.LANGUAGE
    }

    private fun showUpdateToVariantDialog() {
        activeDialog = GeneralConfigDialog.UPDATE_VARIANT
    }

    private fun setUpdateToVariant(value: String) {
        if (value == screenState.updateToVariant) return
        putPrefString(PreferKey.updateToVariant, value)
        screenState = screenState.copy(updateToVariant = value)
    }

    private fun setBooleanPreference(key: String, enabled: Boolean) {
        putPrefBoolean(key, enabled)
        screenState = when (key) {
            PreferKey.replaceEnableDefault -> screenState.copy(
                replaceEnableDefault = enabled,
            )
            PreferKey.showAddToShelfAlert -> screenState.copy(
                showAddToShelfAlert = enabled,
            )
            "autoUpdateVariant" -> screenState.copy(autoUpdateVariant = enabled)
            PreferKey.showMangaUi -> screenState.copy(showMangaUi = enabled)
            else -> screenState
        }
    }

    private fun setProcessTextEnabled(enabled: Boolean) {
        putPrefBoolean(PreferKey.processText, enabled)
        packageManager.setComponentEnabledSetting(
            processTextComponent,
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
        screenState = screenState.copy(processText = enabled)
    }

    private fun isProcessTextEnabled(): Boolean {
        return packageManager.getComponentEnabledSetting(processTextComponent) !=
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    private fun setVideoAutoPlay(enabled: Boolean) {
        VideoPlay.autoPlay = enabled
        screenState = screenState.copy(videoAutoPlay = enabled)
    }

    private fun setVideoStartFull(enabled: Boolean) {
        VideoPlay.startFull = enabled
        screenState = screenState.copy(videoStartFull = enabled)
    }

    private fun setVideoFullBottomProgress(enabled: Boolean) {
        VideoPlay.fullBottomProgressBar = enabled
        screenState = screenState.copy(videoFullBottomProgress = enabled)
    }

    private fun showVideoLongPressSpeedDialog() {
        activeDialog = GeneralConfigDialog.VIDEO_SPEED
    }

    private fun setVideoLongPressSpeed(value: Int) {
        VideoPlay.longPressSpeed = value
        screenState = screenState.copy(videoLongPressSpeed = value)
    }

    private companion object {
        const val DEFAULT_VIDEO_LONG_PRESS_SPEED = 30
        const val LANGUAGE_RESTART_DELAY_MILLIS = 1_000L
    }

    private enum class GeneralConfigDialog {
        LANGUAGE,
        UPDATE_VARIANT,
        VIDEO_SPEED,
    }

    private fun choiceOptions(entriesRes: Int, valuesRes: Int): List<ConfigChoiceOption> {
        val entries = resources.getStringArray(entriesRes)
        val values = resources.getStringArray(valuesRes)
        return entries.zip(values).map { (label, value) ->
            ConfigChoiceOption(label = label, value = value)
        }
    }
}
