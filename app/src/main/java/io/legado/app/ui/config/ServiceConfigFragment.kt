package io.legado.app.ui.config

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.prefs.SwitchPreference
import io.legado.app.lib.prefs.fragment.PreferenceFragment
import io.legado.app.lib.theme.primaryColor
import io.legado.app.service.McpService
import io.legado.app.service.WebService
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.observeEventSticky
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.openUrl

class ServiceConfigFragment : PreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        putPrefBoolean(PreferKey.webService, WebService.isRun)
        putPrefBoolean(PreferKey.mcpService, McpService.isRun)
        addPreferencesFromResource(R.xml.pref_config_service)
        updateWebPreference()
        updateMcpPreference()
        upPortSummary(PreferKey.webPort, AppConfig.webPort)
        upPortSummary(PreferKey.mcpPort, AppConfig.mcpPort)
        findPreference<SwitchPreference>(PreferKey.webService)?.onLongClick {
            if (!WebService.isRun) return@onLongClick false
            showAddressMenu(it.summary.toString())
            true
        }
        findPreference<SwitchPreference>(PreferKey.mcpService)?.onLongClick {
            if (!McpService.isRun) return@onLongClick false
            showAddressMenu(it.summary.toString())
            true
        }
        observeEventSticky<String>(EventBus.WEB_SERVICE) {
            updateWebPreference()
        }
        observeEventSticky<String>(EventBus.MCP_SERVICE) {
            updateMcpPreference()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.setTitle(R.string.service_manage)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        listView.setEdgeEffectColor(primaryColor)
    }

    override fun onDestroy() {
        super.onDestroy()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            PreferKey.webPort -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.web_port_title))
                .setMaxValue(60000)
                .setMinValue(1024)
                .setValue(AppConfig.webPort)
                .show {
                    AppConfig.webPort = it
                }

            PreferKey.mcpPort -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.mcp_port_title))
                .setMaxValue(60000)
                .setMinValue(1024)
                .setValue(AppConfig.mcpPort)
                .show {
                    AppConfig.mcpPort = it
                }
        }
        return super.onPreferenceTreeClick(preference)
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
                upPortSummary(PreferKey.webPort, AppConfig.webPort)
                if (WebService.isRun) {
                    WebService.stop(requireContext())
                    WebService.start(requireContext())
                }
            }

            PreferKey.mcpPort -> {
                upPortSummary(PreferKey.mcpPort, AppConfig.mcpPort)
                if (McpService.isRun) {
                    McpService.stop(requireContext())
                    McpService.start(requireContext())
                }
            }
        }
    }

    private fun updateWebPreference() {
        findPreference<SwitchPreference>(PreferKey.webService)?.let {
            it.isChecked = WebService.isRun
            it.summary = if (WebService.isRun) {
                WebService.hostAddress
            } else {
                getString(R.string.web_service_desc)
            }
        }
    }

    private fun updateMcpPreference() {
        findPreference<SwitchPreference>(PreferKey.mcpService)?.let {
            it.isChecked = McpService.isRun
            it.summary = if (McpService.isRun) {
                McpService.hostAddress
            } else {
                getString(R.string.mcp_service_desc)
            }
        }
    }

    private fun upPortSummary(key: String, port: Int) {
        findPreference<Preference>(key)?.summary = when (key) {
            PreferKey.mcpPort -> getString(R.string.mcp_port_summary, port.toString())
            else -> getString(R.string.web_port_summary, port.toString())
        }
    }

    private fun showAddressMenu(address: String) {
        context?.selector(arrayListOf("复制地址", "浏览器打开")) { _, i ->
            when (i) {
                0 -> context?.sendToClip(address)
                1 -> context?.openUrl(address)
            }
        }
    }
}
