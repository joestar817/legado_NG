package io.legado.app.ui.config

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.preference.PreferenceManager
import com.jeremyliao.liveeventbus.LiveEventBus
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.PreferKey
import io.legado.app.help.AppFreezeMonitor
import io.legado.app.help.DispatchersMonitor
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.about.NetworkLogDialog
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.LogUtils
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import io.legado.app.utils.removePref
import io.legado.app.utils.showDialogFragment
import splitties.init.appCtx

class AdvancedConfigFragment : BaseFragment(R.layout.fragment_advanced_config),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var sharedPreferences: SharedPreferences
    private var screenState by mutableStateOf(AdvancedConfigScreenState())
    private var activeDialog by mutableStateOf<AdvancedConfigDialog?>(null)

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        activity?.setTitle(R.string.advanced_setting)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        refreshContent()
        (view as ComposeView).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            setContent {
                NgAppTheme {
                    AdvancedConfigScreen(
                        state = screenState,
                        onUserAgentClick = ::showUserAgentDialog,
                        onCustomHostsClick = ::showCustomHostsDialog,
                        onUploadRuleClick = {
                            showDialogFragment<DirectLinkUploadConfig>()
                        },
                        onCronetChanged = {
                            setBooleanPreference(PreferKey.cronet, it)
                        },
                        onAntiAliasChanged = {
                            setBooleanPreference(PreferKey.antiAlias, it)
                        },
                        onThreadCountChanged = ::setThreadCountDraft,
                        onThreadCountChangeFinished = ::saveThreadCount,
                        onRecordLogChanged = {
                            setBooleanPreference(PreferKey.recordLog, it)
                        },
                        onRecordNetworkLogChanged = {
                            setBooleanPreference(PreferKey.recordNetworkLog, it)
                        },
                        onNetworkRequestLogClick = {
                            showDialogFragment<NetworkLogDialog>()
                        },
                        onRecordHeapDumpChanged = {
                            setBooleanPreference(PreferKey.recordHeapDump, it)
                        },
                        onReadingPaceEnabledChanged = {
                            setBooleanPreference(PreferKey.readingPaceEnabled, it)
                        },
                        onReadingPaceMinIntervalChanged = ::setReadingPaceMinIntervalDraft,
                        onReadingPaceMaxIntervalChanged = ::setReadingPaceMaxIntervalDraft,
                        onReadingPaceIntervalChangeFinished = ::saveReadingPaceIntervals,
                        onReadingPaceLongRestChanged = {
                            setBooleanPreference(PreferKey.readingPaceLongRest, it)
                        },
                        onReadingPaceQuietHoursChanged = {
                            setBooleanPreference(PreferKey.readingPaceQuietHours, it)
                        },
                    )
                    when (activeDialog) {
                        AdvancedConfigDialog.USER_AGENT -> ConfigTextEditorDialog(
                            title = getString(R.string.user_agent),
                            initialValue = AppConfig.userAgent,
                            cancelText = getString(R.string.cancel),
                            confirmText = getString(R.string.ok),
                            onDismissRequest = { activeDialog = null },
                            onConfirm = ::saveUserAgent,
                        )
                        AdvancedConfigDialog.CUSTOM_HOSTS -> ConfigJsonEditorDialog(
                            title = getString(R.string.custom_hosts),
                            label = getString(R.string.json_format),
                            initialValue = AppConfig.customHosts.orEmpty(),
                            cancelText = getString(R.string.cancel),
                            confirmText = getString(R.string.ok),
                            onDismissRequest = { activeDialog = null },
                            onConfirm = ::saveCustomHosts,
                        )
                        null -> Unit
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.advanced_setting)
        if (view != null) refreshContent()
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String?,
    ) {
        when (key) {
            PreferKey.threadCount -> postEvent(PreferKey.threadCount, "")
            PreferKey.recordLog -> {
                AppConfig.recordLog = appCtx.getPrefBoolean(PreferKey.recordLog)
                LogUtils.upLevel()
                LogUtils.logDeviceInfo()
                LiveEventBus.config().enableLogger(AppConfig.recordLog)
                AppFreezeMonitor.init(appCtx)
                DispatchersMonitor.init()
            }
            PreferKey.recordNetworkLog -> {
                AppConfig.recordNetworkLog =
                    appCtx.getPrefBoolean(PreferKey.recordNetworkLog)
            }
            PreferKey.readingPaceEnabled,
            PreferKey.readingPaceMinInterval,
            PreferKey.readingPaceMaxInterval,
            PreferKey.readingPaceLongRest,
            PreferKey.readingPaceQuietHours -> Unit
        }
        view?.post(::refreshContent)
    }

    private fun refreshContent() {
        screenState = AdvancedConfigScreenState(
            userAgent = AppConfig.userAgent,
            cronet = getPrefBoolean(PreferKey.cronet, false),
            antiAlias = getPrefBoolean(PreferKey.antiAlias, false),
            threadCount = AppConfig.threadCount,
            recordLog = getPrefBoolean(PreferKey.recordLog, false),
            recordNetworkLog = getPrefBoolean(PreferKey.recordNetworkLog, false),
            recordHeapDump = getPrefBoolean(PreferKey.recordHeapDump, false),
            readingPaceEnabled = AppConfig.readingPaceEnabled,
            readingPaceMinInterval = AppConfig.readingPaceMinInterval,
            readingPaceMaxInterval = AppConfig.readingPaceMaxInterval,
            readingPaceLongRest = AppConfig.readingPaceLongRest,
            readingPaceQuietHours = AppConfig.readingPaceQuietHours,
        )
    }

    private fun setBooleanPreference(key: String, enabled: Boolean) {
        putPrefBoolean(key, enabled)
        screenState = when (key) {
            PreferKey.cronet -> screenState.copy(cronet = enabled)
            PreferKey.antiAlias -> screenState.copy(antiAlias = enabled)
            PreferKey.recordLog -> screenState.copy(recordLog = enabled)
            PreferKey.recordNetworkLog -> screenState.copy(recordNetworkLog = enabled)
            PreferKey.recordHeapDump -> screenState.copy(recordHeapDump = enabled)
            PreferKey.readingPaceEnabled -> screenState.copy(readingPaceEnabled = enabled)
            PreferKey.readingPaceLongRest -> screenState.copy(readingPaceLongRest = enabled)
            PreferKey.readingPaceQuietHours -> screenState.copy(readingPaceQuietHours = enabled)
            else -> screenState
        }
    }

    private fun setReadingPaceMinIntervalDraft(value: Int) {
        screenState = screenState.copy(readingPaceMinInterval = value)
    }

    private fun setReadingPaceMaxIntervalDraft(value: Int) {
        screenState = screenState.copy(readingPaceMaxInterval = value)
    }

    private fun saveReadingPaceIntervals() {
        // 保证最小间隔不大于最大间隔,否则取反
        var min = screenState.readingPaceMinInterval
        var max = screenState.readingPaceMaxInterval
        if (min > max) {
            val swap = min
            min = max
            max = swap
            screenState = screenState.copy(
                readingPaceMinInterval = min,
                readingPaceMaxInterval = max,
            )
        }
        AppConfig.readingPaceMinInterval = min
        AppConfig.readingPaceMaxInterval = max
        // 在这里手动调过间隔即视为手动模式,否则缓存管理页的开关会让这两个值静默失效。
        AppConfig.cachePaceManualEnabled = true
    }

    private fun showUserAgentDialog() {
        activeDialog = AdvancedConfigDialog.USER_AGENT
    }

    private fun showCustomHostsDialog() {
        activeDialog = AdvancedConfigDialog.CUSTOM_HOSTS
    }

    private fun setThreadCountDraft(value: Int) {
        screenState = screenState.copy(threadCount = value)
    }

    private fun saveThreadCount() {
        AppConfig.threadCount = screenState.threadCount
    }

    private fun saveUserAgent(value: String) {
        activeDialog = null
        if (value.isBlank()) {
            removePref(PreferKey.userAgent)
        } else {
            putPrefString(PreferKey.userAgent, value)
        }
    }

    private fun saveCustomHosts(value: String) {
        activeDialog = null
        if (value.isJsonObject()) {
            putPrefString(PreferKey.customHosts, value)
        } else {
            removePref(PreferKey.customHosts)
        }
    }

    override fun onDestroyView() {
        if (::sharedPreferences.isInitialized) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        }
        super.onDestroyView()
    }

    private enum class AdvancedConfigDialog {
        USER_AGENT,
        CUSTOM_HOSTS,
    }
}
