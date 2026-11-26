package io.legado.app.ui.config

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.help.ai.AiChatClient
import io.legado.app.help.ai.AiModel
import io.legado.app.help.ai.AiModelGenerationIssue
import io.legado.app.help.ai.AiModelGenerationTestFailure
import io.legado.app.help.ai.AiModelGenerationTestProtocol
import io.legado.app.help.ai.AiModelGenerationTestResult
import io.legado.app.help.ai.AiProviderSetting
import io.legado.app.help.ai.isOfficialOpenCodeGo
import io.legado.app.help.ai.modelGenerationTestProtocols
import io.legado.app.help.ai.resolveModelGenerationRoute
import io.legado.app.help.ai.usesAccountLogin
import io.legado.app.ui.design.components.compose.LocalNgDialogColorMode
import io.legado.app.ui.design.components.compose.LocalNgPaneBounds
import io.legado.app.ui.design.components.compose.NgDialogColorMode
import io.legado.app.ui.design.components.compose.NgDialogSurface
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuVariant
import io.legado.app.ui.design.components.compose.NgMenuSelectionStyle
import io.legado.app.ui.design.components.compose.NgPaneDialog
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun AiModelGenerationTestDialog(
    provider: AiProviderSetting,
    model: AiModel,
    onDismissRequest: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val accountProvider = provider.usesAccountLogin()
    val modelRoute = remember(provider, model) { provider.resolveModelGenerationRoute(model) }
    val protocols = remember(provider, modelRoute.protocol) {
        (provider.modelGenerationTestProtocols() + modelRoute.protocol).distinct()
    }
    var protocol by remember(provider, model) { mutableStateOf(modelRoute.protocol) }
    var streaming by remember(provider, model) { mutableStateOf(provider.streamResponseEnabled || accountProvider) }
    var result by remember(provider, model) { mutableStateOf<AiModelGenerationTestResult?>(null) }
    var job by remember(provider, model) { mutableStateOf<Job?>(null) }
    val issue = remember(provider, model, protocol, streaming) {
        provider.resolveModelGenerationRoute(model, protocolOverride = protocol, stream = streaming).issue
    }
    val running = job != null
    val margin = if (LocalNgPaneBounds.current == null) 24.dp else 0.dp
    val dismiss = {
        job?.cancel()
        onDismissRequest()
    }

    NgPaneDialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(LocalNgDialogColorMode provides NgDialogColorMode.STANDARD) {
            NgDialogSurface(
                modifier = Modifier.padding(horizontal = margin).widthIn(max = 420.dp),
            ) {
                Column(Modifier.height(320.dp).padding(horizontal = 24.dp, vertical = 21.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Text(
                            text = stringResource(R.string.ai_model_test_generation),
                            color = Color(NgTheme.colors.onSurface),
                            fontSize = 24.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Normal,
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                painter = painterResource(model.iconRes(provider.iconRes())),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                            )
                            Text(
                                text = model.id,
                                modifier = Modifier.weight(1f, fill = false),
                                color = Color(NgTheme.colors.primary),
                                fontSize = 14.sp,
                                lineHeight = 19.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        GenerationTestRow(label = stringResource(R.string.ai_model_test_protocol)) {
                            if (protocols.size > 1) {
                                GenerationTestSelector(
                                    current = protocol,
                                    options = protocols.map { it to it.displayName() },
                                    enabled = !running,
                                    fontSize = 13.sp,
                                ) { value ->
                                    if (value != protocol) {
                                        protocol = value
                                        result = null
                                    }
                                }
                            } else {
                                Text(
                                    text = protocol.displayName(),
                                    color = Color(NgTheme.colors.onSurfaceVariant),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                )
                            }
                        }
                        GenerationTestRow(label = stringResource(R.string.ai_model_test_response_mode)) {
                            if (accountProvider) {
                                GenerationTestText(stringResource(R.string.ai_model_test_streaming))
                            } else {
                                GenerationTestSelector(
                                    current = streaming,
                                    options = listOf(
                                        true to stringResource(R.string.ai_model_test_streaming),
                                        false to stringResource(R.string.ai_model_test_non_streaming),
                                    ),
                                    enabled = !running,
                                ) { value ->
                                    if (value != streaming) {
                                        streaming = value
                                        result = null
                                    }
                                }
                            }
                        }
                        GenerationTestRow(
                            label = stringResource(R.string.ai_model_test_result),
                            topAligned = true,
                        ) {
                            GenerationTestResult(running, result, issue, provider.isOfficialOpenCodeGo())
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    ) {
                        NgDialogTextActionButton(
                            text = stringResource(if (result == null) R.string.cancel else R.string.close),
                            onClick = dismiss,
                        )
                        NgDialogTextActionButton(
                            text = stringResource(if (result is AiModelGenerationTestResult.Failure) R.string.retry else R.string.test),
                            enabled = !running && issue == null,
                            onClick = {
                                if (job == null && issue == null) {
                                    result = null
                                    val stream = streaming
                                    val selectedProtocol = protocol
                                    // Start after assigning the Job so validation can finish synchronously.
                                    job = scope.launch(start = CoroutineStart.LAZY) {
                                        try {
                                            result = AiChatClient().testModelGeneration(
                                                provider, model, stream, selectedProtocol,
                                            )
                                        } catch (error: CancellationException) {
                                            throw error
                                        } catch (_: Exception) {
                                            result = AiModelGenerationTestResult.Failure(
                                                AiModelGenerationTestFailure.INVALID_RESPONSE,
                                            )
                                        } finally {
                                            job = null
                                        }
                                    }
                                    job?.start()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenerationTestRow(
    label: String,
    topAligned: Boolean = false,
    value: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
        verticalAlignment = if (topAligned) Alignment.Top else Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.38f).padding(end = 8.dp),
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 15.sp,
            lineHeight = 21.sp,
        )
        Column(Modifier.weight(0.62f)) { value() }
    }
}

@Composable
private fun GenerationTestText(text: String, color: Color = Color(NgTheme.colors.onSurfaceVariant)) {
    Text(text = text, color = color, fontSize = 15.sp, lineHeight = 21.sp)
}

@Composable
private fun <T> GenerationTestSelector(
    current: T,
    options: List<Pair<T, String>>,
    enabled: Boolean,
    fontSize: TextUnit = 15.sp,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.first == current }?.second.orEmpty()
    val color = Color(NgTheme.colors.primary).copy(alpha = if (enabled) 1f else 0.65f)
    Box {
        Row(
            modifier = Modifier.heightIn(min = 48.dp).clickable(enabled = enabled, role = Role.Button) {
                expanded = true
            },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currentLabel,
                modifier = Modifier.weight(1f, fill = false),
                color = color,
                fontSize = fontSize,
                lineHeight = (fontSize.value + 6f).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painter = painterResource(R.drawable.ic_ng_spinner_arrow_down),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
        }
        NgExpandableActionMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            items = options.mapIndexed { index, (value, label) ->
                NgExpandableActionMenuItem(
                    itemId = index,
                    titleRes = 0,
                    title = label,
                    iconRes = 0,
                    checked = current == value,
                    selectionStyle = NgMenuSelectionStyle.COMPACT_CHECK,
                )
            },
            onItemClick = {
                expanded = false
                onSelect(options[it.itemId].first)
            },
            variant = NgExpandableActionMenuVariant.END_ANCHORED_DROPDOWN,
            properties = PopupProperties(focusable = true),
        )
    }
}

@Composable
private fun GenerationTestResult(
    running: Boolean,
    result: AiModelGenerationTestResult?,
    issue: AiModelGenerationIssue?,
    officialOpenCodeGo: Boolean,
) {
    when {
        running -> LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            color = Color(NgTheme.colors.primary),
            trackColor = Color(NgTheme.colors.outlineVariant),
        )
        result is AiModelGenerationTestResult.Success -> {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.success),
                modifier = Modifier.size(20.dp),
                tint = colorResource(R.color.success),
            )
        }
        result is AiModelGenerationTestResult.Failure -> {
            GenerationTestText(result.message(officialOpenCodeGo), Color(NgTheme.colors.error))
            if (result.reply.isNotBlank()) {
                GenerationTestText(stringResource(R.string.ai_model_test_partial_reply, result.reply))
            }
        }
        issue != null -> GenerationTestText(
            text = stringResource(when (issue) {
                AiModelGenerationIssue.UNSUPPORTED_PROTOCOL -> R.string.ai_model_interface_unsupported
                AiModelGenerationIssue.STREAMING_REQUIRED -> R.string.ai_model_streaming_required
            }),
            color = Color(NgTheme.colors.error),
        )
        else -> GenerationTestText("—")
    }
}

internal fun AiModelGenerationTestProtocol.displayName(): String = when (this) {
    AiModelGenerationTestProtocol.CHAT_COMPLETIONS -> "Chat Completions"
    AiModelGenerationTestProtocol.RESPONSES -> "Responses"
    AiModelGenerationTestProtocol.CLAUDE_MESSAGES -> "Claude Messages"
    AiModelGenerationTestProtocol.GEMINI_GENERATE_CONTENT -> "Gemini GenerateContent"
}

@Composable
private fun AiModelGenerationTestResult.Failure.message(officialOpenCodeGo: Boolean): String = when (reason) {
    AiModelGenerationTestFailure.MISSING_API_KEY -> stringResource(R.string.ai_api_key_required)
    AiModelGenerationTestFailure.INVALID_ENDPOINT -> stringResource(R.string.ai_model_test_invalid_endpoint)
    AiModelGenerationTestFailure.UNSUPPORTED_PROTOCOL -> stringResource(R.string.ai_model_interface_unsupported)
    AiModelGenerationTestFailure.STREAMING_REQUIRED -> stringResource(R.string.ai_model_streaming_required)
    AiModelGenerationTestFailure.HTTP -> httpStatus?.let { status ->
        when {
            officialOpenCodeGo && status == 401 -> stringResource(R.string.ai_opencode_invalid_key)
            officialOpenCodeGo && status == 403 -> stringResource(R.string.ai_opencode_go_model_denied)
            else -> stringResource(R.string.ai_model_test_http_error, status)
        }
    } ?: stringResource(R.string.ai_model_test_invalid_response)
    AiModelGenerationTestFailure.NETWORK -> stringResource(R.string.ai_model_test_network_error)
    AiModelGenerationTestFailure.TIMEOUT -> stringResource(R.string.ai_model_test_timeout)
    AiModelGenerationTestFailure.INTERRUPTED -> stringResource(R.string.ai_model_test_interrupted)
    AiModelGenerationTestFailure.TRUNCATED -> stringResource(R.string.ai_model_test_truncated)
    AiModelGenerationTestFailure.REFUSED -> stringResource(R.string.ai_model_test_refused)
    AiModelGenerationTestFailure.TOOL_CALL -> stringResource(R.string.ai_model_test_tool_call)
    AiModelGenerationTestFailure.EMPTY_REPLY -> stringResource(R.string.ai_model_test_empty_reply)
    AiModelGenerationTestFailure.INVALID_RESPONSE -> stringResource(R.string.ai_model_test_invalid_response)
}
