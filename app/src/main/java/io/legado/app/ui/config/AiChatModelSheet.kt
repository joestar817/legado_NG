package io.legado.app.ui.config

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import io.legado.app.R
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.ai.AiManager
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiChatModelSheet(onChanged: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sheetState by remember {
        mutableStateOf(
            AiModelSelectionSheetState(
                title = context.getString(R.string.ai_model_select),
                emptyText = context.getString(R.string.ai_assistant_model_empty),
                providers = emptyList(),
                selectedProviderId = AiConfig.assistantProviderId,
                selectedModelId = AiConfig.assistantModelId,
            )
        )
    }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var refreshJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        loadJob = scope.launch {
            try {
                sheetState = sheetState.copy(providers = withContext(Dispatchers.IO) {
                    assistantModelSelectionProviders()
                })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Toast.makeText(
                    context,
                    "加载模型失败：${e.localizedMessage ?: e.javaClass.simpleName}",
                    Toast.LENGTH_SHORT,
                ).show()
            } finally {
                if (!refreshing) loading = false
                loadJob = null
            }
        }
        onDispose {
            loadJob?.cancel()
            refreshJob?.cancel()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        containerColor = Color.Transparent,
        contentColor = Color(NgTheme.colors.onSurface),
        shape = RectangleShape,
    ) {
        NgBottomDrawerSurface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f),
            contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
        ) {
            AiModelSelectionSheet(
                state = sheetState,
                isLoading = loading,
                isRefreshing = refreshing,
                onRefresh = {
                    if (!refreshing) {
                        refreshing = true
                        loadJob?.cancel()
                        refreshJob = scope.launch {
                            try {
                                val result = AiManager.refreshEnabledProviderModels()
                                val providers = withContext(Dispatchers.IO) {
                                    assistantModelSelectionProviders()
                                }
                                sheetState = sheetState.copy(providers = providers)
                                loading = false
                                onChanged()
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.ai_refresh_models_result,
                                        result.refreshedProviderCount,
                                        result.refreshedModelCount,
                                        result.failedProviders.size,
                                        result.missingKeyProviders.size,
                                    ),
                                    Toast.LENGTH_LONG,
                                ).show()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                Toast.makeText(
                                    context,
                                    "刷新模型失败：${e.localizedMessage ?: e.javaClass.simpleName}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } finally {
                                loading = false
                                refreshing = false
                                refreshJob = null
                            }
                        }
                    }
                },
                onSelect = { providerId, modelId ->
                    AiConfig.saveAssistantModel(providerId, modelId)
                    onChanged()
                    onDismiss()
                },
            )
        }
    }
}
