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
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.selector
import io.legado.app.service.McpService
import io.legado.app.service.WebService
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.observeEventSticky
import io.legado.app.utils.openUrl
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.sendToClip

class ServiceConfigFragment : BaseFragment(R.layout.fragment_service_config),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var screenState by mutableStateOf(ServiceConfigScreenState())
    private lateinit var sharedPreferences: SharedPreferences

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        activity?.setTitle(R.string.service_manage)
        putPrefBoolean(PreferKey.webService, WebService.isRun)
        putPrefBoolean(PreferKey.mcpService, McpService.isRun)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        refreshContent()
        (view as ComposeView).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            setContent {
                NgAppTheme {
                    ServiceConfigScreen(
                        state = screenState,
                        onWebServiceChanged = ::setWebService,
                        onWebServiceLongClick = ::showWebAddressMenu,
                        onWebPortClick = { showPortPicker(PreferKey.webPort) },
                        onWebServiceWakeLockChanged = ::setWebServiceWakeLock,
                        onMcpServiceChanged = ::setMcpService,
                        onMcpServiceLongClick = ::showMcpAddressMenu,
                        onMcpPortClick = { showPortPicker(PreferKey.mcpPort) },
                    )
                }
            }
        }
        observeEventSticky<String>(EventBus.WEB_SERVICE) {
            refreshContent()
        }
        observeEventSticky<String>(EventBus.MCP_SERVICE) {
            refreshContent()
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.service_manage)
        if (view != null) refreshContent()
    }

    override fun onDestroyView() {
        if (::sharedPreferences.isInitialized) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        }
        super.onDestroyView()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            PreferKey.webService -> {
                if (requireContext().getPrefBoolean(PreferKey.webService)) {
                    WebService.start(requireContext())
                } else {
                    WebService.stop(requireContext())
                }
            }

            PreferKey.mcpService -> {
                if (requireContext().getPrefBoolean(PreferKey.mcpService)) {
                    McpService.start(requireContext())
                } else {
                    McpService.stop(requireContext())
                }
            }

            PreferKey.webPort -> {
                updatePortSummaries()
                if (WebService.isRun) {
                    WebService.stop(requireContext())
                    WebService.start(requireContext())
                }
            }

            PreferKey.mcpPort -> {
                updatePortSummaries()
                if (McpService.isRun) {
                    McpService.stop(requireContext())
                    McpService.start(requireContext())
                }
            }

            PreferKey.webServiceWakeLock -> {
                screenState = screenState.copy(
                    webServiceWakeLock = getPrefBoolean(PreferKey.webServiceWakeLock),
                )
            }
        }
    }

    private fun refreshContent() {
        screenState = ServiceConfigScreenState(
            webServiceEnabled = WebService.isRun,
            webServiceSummary = if (WebService.isRun) {
                WebService.hostAddress
            } else {
                getString(R.string.web_service_desc)
            },
            webPortSummary = getString(
                R.string.web_port_summary,
                AppConfig.webPort.toString(),
            ),
            webServiceWakeLock = getPrefBoolean(PreferKey.webServiceWakeLock),
            mcpServiceEnabled = McpService.isRun,
            mcpServiceSummary = if (McpService.isRun) {
                McpService.hostAddress
            } else {
                getString(R.string.mcp_service_desc)
            },
            mcpPortSummary = getString(
                R.string.mcp_port_summary,
                AppConfig.mcpPort.toString(),
            ),
        )
    }

    private fun updatePortSummaries() {
        screenState = screenState.copy(
            webPortSummary = getString(
                R.string.web_port_summary,
                AppConfig.webPort.toString(),
            ),
            mcpPortSummary = getString(
                R.string.mcp_port_summary,
                AppConfig.mcpPort.toString(),
            ),
        )
    }

    private fun setWebService(enabled: Boolean) {
        screenState = screenState.copy(webServiceEnabled = enabled)
        putPrefBoolean(PreferKey.webService, enabled)
    }

    private fun setMcpService(enabled: Boolean) {
        screenState = screenState.copy(mcpServiceEnabled = enabled)
        putPrefBoolean(PreferKey.mcpService, enabled)
    }

    private fun setWebServiceWakeLock(enabled: Boolean) {
        screenState = screenState.copy(webServiceWakeLock = enabled)
        putPrefBoolean(PreferKey.webServiceWakeLock, enabled)
    }

    private fun showPortPicker(key: String) {
        val isMcp = key == PreferKey.mcpPort
        NumberPickerDialog(requireContext())
            .setTitle(
                getString(if (isMcp) R.string.mcp_port_title else R.string.web_port_title),
            )
            .setMaxValue(60000)
            .setMinValue(1024)
            .setValue(if (isMcp) AppConfig.mcpPort else AppConfig.webPort)
            .show { value ->
                if (isMcp) {
                    AppConfig.mcpPort = value
                } else {
                    AppConfig.webPort = value
                }
            }
    }

    private fun showWebAddressMenu() {
        if (WebService.isRun) showAddressMenu(WebService.hostAddress)
    }

    private fun showMcpAddressMenu() {
        if (McpService.isRun) showAddressMenu(McpService.hostAddress)
    }

    private fun showAddressMenu(address: String) {
        requireContext().selector(arrayListOf("复制地址", "浏览器打开")) { _, index ->
            when (index) {
                0 -> requireContext().sendToClip(address)
                1 -> requireContext().openUrl(address)
            }
        }
    }
}
