package io.legado.app.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import io.legado.app.R
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.components.compose.NgExpandableSettingsItem
import io.legado.app.ui.design.components.compose.NgSettingsGroup
import io.legado.app.ui.design.components.compose.NgSettingsItem
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal data class ServiceConfigScreenState(
    val webServiceEnabled: Boolean = false,
    val webServiceSummary: String = "",
    val webPort: Int = SERVICE_PORT_MIN,
    val webPortSummary: String = "",
    val webServiceWakeLock: Boolean = false,
    val mcpServiceEnabled: Boolean = false,
    val mcpServiceSummary: String = "",
    val mcpPort: Int = SERVICE_PORT_MIN,
    val mcpPortSummary: String = "",
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
    var expandedPort by rememberSaveable { mutableStateOf<String?>(null) }
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
            NgExpandableSettingsItem(
                title = stringResource(R.string.web_port_title),
                summary = state.webPortSummary,
                expanded = expandedPort == WEB_PORT_KEY,
                onExpandedChange = { expanded ->
                    expandedPort = if (expanded) WEB_PORT_KEY else null
                },
            ) {
                ServicePortEditor(
                    value = state.webPort,
                    onValueCommitted = { value ->
                        onWebPortChanged(value)
                        onWebPortChangeFinished()
                    },
                )
            }
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
            NgExpandableSettingsItem(
                title = stringResource(R.string.mcp_port_title),
                summary = state.mcpPortSummary,
                expanded = expandedPort == MCP_PORT_KEY,
                onExpandedChange = { expanded ->
                    expandedPort = if (expanded) MCP_PORT_KEY else null
                },
            ) {
                ServicePortEditor(
                    value = state.mcpPort,
                    onValueCommitted = { value ->
                        onMcpPortChanged(value)
                        onMcpPortChangeFinished()
                    },
                )
            }
        }
    }
}

@Composable
private fun ServicePortEditor(
    value: Int,
    onValueCommitted: (Int) -> Unit,
) {
    var text by rememberSaveable(value) { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    var submittedValue by remember(value) { mutableIntStateOf(value) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val parsedValue = text.toIntOrNull()
    val valid = parsedValue != null && parsedValue in SERVICE_PORT_MIN..SERVICE_PORT_MAX
    val effectiveValue = parsedValue?.takeIf {
        it in SERVICE_PORT_MIN..SERVICE_PORT_MAX
    } ?: submittedValue
    val inputShape = RoundedCornerShape(16.dp)
    val surfaceColor = colorResource(R.color.ng_surface_card)
    val outlineColor = when {
        !valid -> Color(NgTheme.colors.error)
        focused -> Color(NgTheme.colors.primary)
        else -> Color(NgTheme.colors.outline).copy(alpha = 0.58f)
    }

    LaunchedEffect(value) {
        submittedValue = value
        if (!focused) text = value.toString()
    }

    fun submit(candidate: Int) {
        val normalized = candidate.coerceIn(SERVICE_PORT_MIN, SERVICE_PORT_MAX)
        text = normalized.toString()
        if (normalized != submittedValue) {
            submittedValue = normalized
            onValueCommitted(normalized)
        }
    }

    fun finishInput(revertInvalid: Boolean) {
        val candidate = text.toIntOrNull()
        if (candidate != null && candidate in SERVICE_PORT_MIN..SERVICE_PORT_MAX) {
            submit(candidate)
        } else if (revertInvalid) {
            text = submittedValue.toString()
        }
    }

    fun stepValue(delta: Int, commit: Boolean): Boolean {
        val base = text.toIntOrNull()?.takeIf {
            it in SERVICE_PORT_MIN..SERVICE_PORT_MAX
        } ?: submittedValue
        val next = (base + delta).coerceIn(SERVICE_PORT_MIN, SERVICE_PORT_MAX)
        if (next == base) return false
        text = next.toString()
        if (commit) submit(next)
        return true
    }

    fun commitRepeatedValue() {
        text.toIntOrNull()?.takeIf {
            it in SERVICE_PORT_MIN..SERVICE_PORT_MAX
        }?.let(::submit)
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ServicePortStepButton(
                icon = Icons.Rounded.Remove,
                contentDescription = stringResource(R.string.service_port_decrease),
                enabled = effectiveValue > SERVICE_PORT_MIN,
                onClick = {
                    stepValue(delta = -1, commit = true)
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
                onLongPressStep = {
                    stepValue(delta = -1, commit = false)
                },
                onLongPressFinished = ::commitRepeatedValue,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .background(surfaceColor, inputShape)
                    .border(
                        width = if (focused || !valid) 1.5.dp else 1.dp,
                        color = outlineColor,
                        shape = inputShape,
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { candidate ->
                        if (
                            candidate.length <= SERVICE_PORT_MAX.toString().length &&
                            candidate.all { it.isDigit() }
                        ) {
                            text = candidate
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            val lostFocus = focused && !focusState.isFocused
                            focused = focusState.isFocused
                            if (lostFocus) finishInput(revertInvalid = true)
                        },
                    textStyle = TextStyle(
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 22.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            finishInput(revertInvalid = false)
                            if (valid) {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        },
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(Color(NgTheme.colors.primary)),
                )
            }
            ServicePortStepButton(
                icon = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.service_port_increase),
                enabled = effectiveValue < SERVICE_PORT_MAX,
                onClick = {
                    stepValue(delta = 1, commit = true)
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
                onLongPressStep = {
                    stepValue(delta = 1, commit = false)
                },
                onLongPressFinished = ::commitRepeatedValue,
            )
        }
        Text(
            text = stringResource(
                R.string.service_port_range_step,
                SERVICE_PORT_MIN,
                SERVICE_PORT_MAX,
            ),
            modifier = Modifier.fillMaxWidth(),
            color = Color(
                if (valid) NgTheme.colors.onSurfaceVariant else NgTheme.colors.error,
            ),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ServicePortStepButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongPressStep: () -> Boolean,
    onLongPressFinished: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val tint = Color(NgTheme.colors.primary).copy(alpha = if (enabled) 1f else 0.35f)
    val hapticFeedback = LocalHapticFeedback.current
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongPressStep by rememberUpdatedState(onLongPressStep)
    val currentOnLongPressFinished by rememberUpdatedState(onLongPressFinished)
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(colorResource(R.color.ng_surface_card), shape)
            .border(
                width = 1.dp,
                color = Color(NgTheme.colors.outline).copy(alpha = 0.42f),
                shape = shape,
            )
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                if (!enabled) disabled()
                onClick {
                    if (enabled) {
                        currentOnClick()
                        true
                    } else {
                        false
                    }
                }
            }
            .pointerInput(Unit) {
                var longPressHandled = false
                detectTapGestures(
                    onPress = press@{
                        if (!currentEnabled) return@press
                        longPressHandled = false
                        coroutineScope {
                            val repeatJob = launch {
                                delay(PORT_STEP_LONG_PRESS_DELAY_MS)
                                if (!currentEnabled) return@launch
                                longPressHandled = true
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                while (isActive && currentOnLongPressStep()) {
                                    delay(PORT_STEP_REPEAT_INTERVAL_MS)
                                }
                            }
                            tryAwaitRelease()
                            repeatJob.cancelAndJoin()
                        }
                        if (longPressHandled) currentOnLongPressFinished()
                    },
                    onTap = {
                        if (currentEnabled && !longPressHandled) currentOnClick()
                        longPressHandled = false
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = tint,
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
private const val WEB_PORT_KEY = "web_port"
private const val MCP_PORT_KEY = "mcp_port"
private const val PORT_STEP_LONG_PRESS_DELAY_MS = 400L
private const val PORT_STEP_REPEAT_INTERVAL_MS = 90L
