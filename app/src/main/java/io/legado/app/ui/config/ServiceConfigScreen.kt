package io.legado.app.ui.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.components.compose.NgSettingsGroup
import io.legado.app.ui.design.components.compose.NgSettingsItem

internal data class ServiceConfigScreenState(
    val webServiceEnabled: Boolean = false,
    val webServiceSummary: String = "",
    val webPortSummary: String = "",
    val webServiceWakeLock: Boolean = false,
    val mcpServiceEnabled: Boolean = false,
    val mcpServiceSummary: String = "",
    val mcpPortSummary: String = "",
)

@Composable
internal fun ServiceConfigScreen(
    state: ServiceConfigScreenState,
    onWebServiceChanged: (Boolean) -> Unit,
    onWebServiceLongClick: () -> Unit,
    onWebPortClick: () -> Unit,
    onWebServiceWakeLockChanged: (Boolean) -> Unit,
    onMcpServiceChanged: (Boolean) -> Unit,
    onMcpServiceLongClick: () -> Unit,
    onMcpPortClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        NgSettingsGroup {
            NgSettingsItem(
                title = stringResource(R.string.web_service),
                summary = state.webServiceSummary,
                trailing = NgSettingsTrailing.SWITCH,
                checked = state.webServiceEnabled,
                onCheckedChange = onWebServiceChanged,
                onClick = { onWebServiceChanged(!state.webServiceEnabled) },
                onLongClick = onWebServiceLongClick,
            )
            NgSettingsItem(
                title = stringResource(R.string.web_port_title),
                summary = state.webPortSummary,
                onClick = onWebPortClick,
            )
            NgSettingsItem(
                title = stringResource(R.string.web_service_wake_lock),
                summary = stringResource(R.string.web_service_wake_lock_summary),
                summaryMaxLines = 2,
                trailing = NgSettingsTrailing.SWITCH,
                checked = state.webServiceWakeLock,
                onCheckedChange = onWebServiceWakeLockChanged,
                onClick = {
                    onWebServiceWakeLockChanged(!state.webServiceWakeLock)
                },
            )
            NgSettingsItem(
                title = stringResource(R.string.mcp_service),
                summary = state.mcpServiceSummary,
                trailing = NgSettingsTrailing.SWITCH,
                checked = state.mcpServiceEnabled,
                onCheckedChange = onMcpServiceChanged,
                onClick = { onMcpServiceChanged(!state.mcpServiceEnabled) },
                onLongClick = onMcpServiceLongClick,
            )
            NgSettingsItem(
                title = stringResource(R.string.mcp_port_title),
                summary = state.mcpPortSummary,
                onClick = onMcpPortClick,
            )
        }
    }
}
