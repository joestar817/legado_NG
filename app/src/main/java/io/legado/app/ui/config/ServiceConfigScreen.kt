package io.legado.app.ui.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.components.compose.NgFormNumberSettingRow
import io.legado.app.ui.design.components.compose.NgFormNumberSettingRowVariant
import io.legado.app.ui.design.components.compose.NgSettingsCardSurface
import io.legado.app.ui.design.components.compose.NgSettingsGroup
import io.legado.app.ui.design.components.compose.NgSettingsItem

internal data class ServiceConfigScreenState(
    val webServiceEnabled: Boolean = false,
    val webServiceSummary: String = "",
    val webPort: Int = SERVICE_PORT_MIN,
    val webServiceWakeLock: Boolean = false,
    val mcpServiceEnabled: Boolean = false,
    val mcpServiceSummary: String = "",
    val mcpPort: Int = SERVICE_PORT_MIN,
)

internal enum class ServiceConfigDialog {
    WEB_ADDRESS,
    MCP_ADDRESS,
}

internal enum class ServiceAddressAction {
    COPY,
    OPEN,
}

@Composable
internal fun ServiceConfigScreen(
    state: ServiceConfigScreenState,
    onWebServiceChanged: (Boolean) -> Unit,
    onWebServiceLongClick: () -> Unit,
    onWebPortChanged: (Int) -> Unit,
    onWebPortChangeFinished: () -> Unit,
    onWebServiceWakeLockChanged: (Boolean) -> Unit,
    onMcpServiceChanged: (Boolean) -> Unit,
    onMcpServiceLongClick: () -> Unit,
    onMcpPortChanged: (Int) -> Unit,
    onMcpPortChangeFinished: () -> Unit,
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
            ServicePortEditor(
                title = stringResource(R.string.web_port_title),
                value = state.webPort,
                onValueCommitted = { value ->
                    onWebPortChanged(value)
                    onWebPortChangeFinished()
                },
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
            ServicePortEditor(
                title = stringResource(R.string.mcp_port_title),
                value = state.mcpPort,
                onValueCommitted = { value ->
                    onMcpPortChanged(value)
                    onMcpPortChangeFinished()
                },
            )
        }
    }
}

@Composable
private fun ServicePortEditor(
    title: String,
    value: Int,
    onValueCommitted: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    var submittedValue by remember(value) { mutableIntStateOf(value) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val invalid = text.toIntOrNull()?.let { it !in servicePortRange } ?: true

    fun finishInput(revertInvalid: Boolean): Boolean {
        val candidate = text.toIntOrNull()?.takeIf { it in servicePortRange }
        if (candidate == null) {
            if (revertInvalid) text = submittedValue.toString()
            return false
        }
        text = candidate.toString()
        if (candidate != submittedValue) {
            submittedValue = candidate
            onValueCommitted(candidate)
        }
        return true
    }

    fun submitAndCloseKeyboard() {
        if (finishInput(revertInvalid = false)) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    NgSettingsCardSurface(modifier = Modifier.fillMaxWidth()) {
        NgFormNumberSettingRow(
            title = title,
            summary = if (invalid) {
                stringResource(R.string.service_port_range, SERVICE_PORT_MIN, SERVICE_PORT_MAX)
            } else null,
            value = text,
            onValueChange = { candidate ->
                if (candidate.length <= SERVICE_PORT_MAX.toString().length && candidate.all(Char::isDigit)) {
                    text = candidate
                }
            },
            modifier = Modifier.padding(start = 2.dp, end = 10.dp),
            valueWidth = 56.dp * LocalDensity.current.fontScale.coerceAtLeast(1f),
            keyboardActions = KeyboardActions(onDone = { submitAndCloseKeyboard() }),
            onFocusLost = { finishInput(revertInvalid = true) },
            valueRange = servicePortRange,
            onStepValueChange = { text = it.toString() },
            onStepValueChangeFinished = ::submitAndCloseKeyboard,
            variant = NgFormNumberSettingRowVariant.COMPACT_CHEVRONS,
            isError = invalid,
            stepFallbackValue = submittedValue,
            commitStepsOnRelease = true,
        )
    }
}

@Composable
internal fun ServiceConfigDialogHost(
    dialog: ServiceConfigDialog?,
    webAddress: String,
    mcpAddress: String,
    onDismiss: () -> Unit,
    onAddressAction: (String, ServiceAddressAction) -> Unit,
) {
    when (dialog) {
        ServiceConfigDialog.WEB_ADDRESS,
        ServiceConfigDialog.MCP_ADDRESS -> {
            val address = if (dialog == ServiceConfigDialog.MCP_ADDRESS) {
                mcpAddress
            } else {
                webAddress
            }
            ConfigChoiceDialog(
                title = null,
                options = listOf(
                    ConfigChoiceOption("复制地址", ServiceAddressAction.COPY.name),
                    ConfigChoiceOption("浏览器打开", ServiceAddressAction.OPEN.name),
                ),
                onDismissRequest = onDismiss,
                onSelected = { value ->
                    onAddressAction(address, ServiceAddressAction.valueOf(value))
                },
            )
        }

        null -> Unit
    }
}

private const val SERVICE_PORT_MIN = 1024
private const val SERVICE_PORT_MAX = 60000
private val servicePortRange = SERVICE_PORT_MIN..SERVICE_PORT_MAX
