package io.legado.app.ui.pet

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.PreferKey
import io.legado.app.help.ai.AiChatEntryStyle
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.pet.*
import io.legado.app.help.resource.PetManagementActions
import io.legado.app.utils.getPrefString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class PetManagerState(
    val records: List<PetLocalRecord> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val selectedId: String = AiChatEntryStyle.BUTTON.id,
    val enabled: Boolean = false,
    val candidate: PetImportCandidate? = null,
    val importFileName: String? = null,
    val error: String? = null,
    val revision: Long = 0,
    val importedId: String? = null,
    val successfulUseRequestId: String? = null,
) {
    fun canUseFromCard(status: PetLocalStatus) = enabled && canUseFromDetail(status)

    fun canUseFromDetail(status: PetLocalStatus) =
        !loading && !busy && (status == PetLocalStatus.READY || status == PetLocalStatus.LOAD_ERROR)
}

internal class PetManagerViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(PetManagerState())
    val state = mutableState.asStateFlow()
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    var updateId: String?
        get() = savedState["updateId"]
        set(value) { savedState["updateId"] = value }
    var exportId: String?
        get() = savedState["exportId"]
        set(value) { savedState["exportId"] = value }

    init { viewModelScope.launch { PetPluginStore.revision.collect { refreshNow() } } }
    fun refresh() { viewModelScope.launch { refreshNow() } }
    private suspend fun refreshNow() {
        try {
            val records = withContext(Dispatchers.IO) { PetManagementActions.records() }
            mutableState.value = mutableState.value.copy(
                records = records, loading = false, error = null,
                selectedId = getApplication<Application>().getPrefString(
                    PreferKey.aiChatEntryStyle, AiChatEntryStyle.BUTTON.id
                ) ?: AiChatEntryStyle.BUTTON.id,
                enabled = AiConfig.chatFabEnabled,
                revision = PetPluginStore.revision.value,
            )
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(loading = false, error = "无法读取本地宠物")
        }
    }
    private fun operation(block: suspend () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true)
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                refreshNow()
                messages.send(error.message ?: "操作失败，请重试")
            }
            finally { mutableState.value = mutableState.value.copy(busy = false) }
        }
    }
    fun inspect(uri: Uri) {
        val expectedId = updateId
        updateId = null
        operation {
            var created: PetImportCandidate? = null
            var handedToUi = false
            try {
                val (candidate, name) = withContext(Dispatchers.IO) {
                    val name = runCatching {
                        getApplication<Application>().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        }
                    }.getOrNull()
                    // Retain the created copy before withContext's cancellable dispatch back to Main.
                    PetLocalManager.inspect(uri, expectedId).also { created = it } to name
                }
                if (candidate.action == PetImportAction.NO_OP) {
                    messages.send("已安装此版本")
                } else {
                    mutableState.value = mutableState.value.copy(candidate = candidate, importFileName = name)
                    handedToUi = true
                }
            } finally {
                if (!handedToUi) created?.let { candidate ->
                    withContext(Dispatchers.IO + NonCancellable) { candidate.discard() }
                }
            }
        }
    }
    fun dismissImport() {
        if (mutableState.value.busy) return
        val candidate = mutableState.value.candidate ?: return
        mutableState.value = mutableState.value.copy(candidate = null)
        viewModelScope.launch(Dispatchers.IO + NonCancellable) { candidate.discard() }
    }
    fun install() = operation {
        val candidate = mutableState.value.candidate ?: return@operation
        // Ownership moves to this operation: onCleared must not delete an in-flight commit's input.
        mutableState.value = mutableState.value.copy(candidate = null, importedId = null)
        try {
            withContext(Dispatchers.IO) {
                PetManagementActions.commit(candidate,
                    allowReplace = candidate.action == PetImportAction.REPLACE,
                    allowDowngrade = candidate.action == PetImportAction.DOWNGRADE)
            }
            refreshNow()
            mutableState.value = mutableState.value.copy(importedId = candidate.manifest.id)
            messages.send("导入完成")
        } finally {
            withContext(Dispatchers.IO + NonCancellable) { candidate.discard() }
        }
    }
    fun use(id: String) {
        val current = mutableState.value
        val status = current.records.firstOrNull { it.id == id }?.status ?: return
        if (current.canUseFromCard(status)) applyPet(id, requestId = null)
    }
    fun useFromDetail(id: String, requestId: String) {
        val current = mutableState.value
        val status = current.records.firstOrNull { it.id == id }?.status ?: return
        if (current.canUseFromDetail(status)) applyPet(id, requestId)
    }
    private fun applyPet(id: String, requestId: String?) = operation {
        withContext(Dispatchers.IO) { PetManagementActions.use(id) }
        refreshNow()
        if (requestId != null) {
            mutableState.value = mutableState.value.copy(successfulUseRequestId = requestId)
        }
    }
    fun setEntryEnabled(enabled: Boolean) {
        val current = mutableState.value
        if (current.loading || current.busy || current.enabled == enabled) return
        operation {
            if (enabled) {
                // Use the saved selection, not chatEntryStyle's NONE value while disabled.
                // A failed pet load leaves the entry off and preserves the chosen ID.
                withContext(Dispatchers.IO) { PetManagementActions.use(current.selectedId) }
            } else {
                AiConfig.chatFabEnabled = false
            }
            refreshNow()
        }
    }
    fun useButton() {
        val current = mutableState.value
        if (current.loading || current.busy || !current.enabled) return
        AiConfig.chatEntryStyle = AiChatEntryStyle.BUTTON
        refresh()
    }
    fun uninstall(id: String) = operation {
        withContext(Dispatchers.IO) { PetManagementActions.uninstall(id) }
        refreshNow()
        messages.send("已卸载")
    }
    fun reload(id: String) = operation {
        withContext(Dispatchers.IO) { PetManagementActions.reload(id) }
        refreshNow()
        messages.send("已重新加载")
    }
    fun export(uri: Uri) {
        val id = exportId ?: return
        exportId = null
        operation {
            withContext(Dispatchers.IO) {
                val resolver = getApplication<Application>().contentResolver
                checkNotNull(resolver.openOutputStream(uri, "wt")) { "无法写入文件" }.use { output ->
                    PetManagementActions.export(id, output)
                }
            }
            messages.send("已导出 ZIP")
        }
    }
    override fun onCleared() {
        val candidate = mutableState.value.candidate
        if (candidate != null) java.util.concurrent.Executors.newSingleThreadExecutor().let { executor ->
            executor.execute { try { candidate.discard() } finally { executor.shutdown() } }
        }
    }
}
