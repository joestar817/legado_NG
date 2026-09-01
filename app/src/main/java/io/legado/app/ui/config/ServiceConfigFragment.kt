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
import io.legado.app.service.McpService
import io.legado.app.service.WebService
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.observeEventSticky
import io.legado.app.utils.openUrl
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.sendToClip

class ServiceConfigFragment : BaseFragment(R.layout.fragment_service_config),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var screenState by mutableStateOf(ServiceConfigScreenState())
    private var activeDialog by mutableStateOf<ServiceConfigDialog?>(null)
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
                        onWebServiceLongClick = {
                            if (WebService.isRun) {
                                activeDialog = ServiceConfigDialog.WEB_ADDRESS
                            }
                        },
                        onWebPortChanged = ::setWebPortDraft,
                        onWebPortChangeFinished = ::saveWebPort,
                        onWebServiceWakeLockChanged = ::setWebServiceWakeLock,
                        onMcpServiceChanged = ::setMcpService,
                        onMcpServiceLongClick = {
                            if (McpService.isRun) {
                                activeDialog = ServiceConfigDialog.MCP_ADDRESS
                            }
                        },
                        onMcpPortChanged = ::setMcpPortDraft,
                        onMcpPortChangeFinished = ::saveMcpPort,
                    )
                    ServiceConfigDialogHost(
                        dialog = activeDialog,
                        webAddress = WebService.hostAddress,
                        mcpAddress = McpService.hostAddress,
                        onDismiss = { activeDialog = null },
                        onAddressAction = { address, action ->
                            activeDialog = null
                            when (action) {
                                ServiceAddressAction.COPY -> {
                                    requireContext().sendToClip(address)
                                }

                                ServiceAddressAction.OPEN -> {
                                    requireContext().openUrl(address)
                                }
                            }
                        },
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
        activeDialog = null
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
            webPort = AppConfig.webPort,
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
            mcpPort = AppConfig.mcpPort,
            mcpPortSummary = getString(
                R.string.mcp_port_summary,
                AppConfig.mcpPort.toString(),
            ),
        )
    }

    private fun updatePortSummaries() {
        screenState = screenState.copy(
            webPort = AppConfig.webPort,
            webPortSummary = getString(
                R.string.web_port_summary,
                AppConfig.webPort.toString(),
            ),
            mcpPort = AppConfig.mcpPort,
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

    private fun setWebPortDraft(value: Int) {
        screenState = screenState.copy(
            webPort = value,
            webPortSummary = getString(R.string.web_port_summary, value.toString()),
        )
    }

    private fun saveWebPort() {
        AppConfig.webPort = screenState.webPort
    }

    private fun setMcpPortDraft(value: Int) {
        screenState = screenState.copy(
            mcpPort = value,
            mcpPortSummary = getString(R.string.mcp_port_summary, value.toString()),
        )
    }

    private fun saveMcpPort() {
        AppConfig.mcpPort = screenState.mcpPort
    }

}
