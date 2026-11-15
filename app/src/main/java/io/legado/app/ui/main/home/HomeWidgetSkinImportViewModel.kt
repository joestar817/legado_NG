package io.legado.app.ui.main.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.help.home.HomeWidgetSkinImport
import io.legado.app.help.home.HomeWidgetSkinStore
import io.legado.app.help.home.InstalledHomeWidgetSkin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class HomeWidgetSkinImportState(
    val draft: HomeWidgetSkinImport? = null,
    val busy: Boolean = false,
    val importedId: String? = null,
)

internal class HomeWidgetSkinImportViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(HomeWidgetSkinImportState())
    val state = mutableState.asStateFlow()
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    fun inspect(uri: Uri) = operate {
        if (mutableState.value.draft != null) return@operate
        var created: HomeWidgetSkinImport? = null
        try {
            val draft = withContext(Dispatchers.IO) {
                HomeWidgetSkinStore.inspect(uri).also { created = it }
            }
            if (draft.alreadyInstalled) {
                mutableState.value = mutableState.value.copy(importedId = draft.prepared.record.id)
                messages.send("此皮肤已安装")
            } else {
                mutableState.value = mutableState.value.copy(draft = draft)
                created = null
            }
        } finally {
            withContext(Dispatchers.IO + NonCancellable) { created?.discard() }
        }
    }

    fun confirm() = operate {
        val draft = mutableState.value.draft ?: return@operate
        mutableState.value = mutableState.value.copy(draft = null)
        try {
            withContext(Dispatchers.IO) { HomeWidgetSkinStore.install(draft.prepared) }
            mutableState.value = mutableState.value.copy(importedId = draft.prepared.record.id)
            messages.send("皮肤已导入")
        } finally {
            withContext(Dispatchers.IO + NonCancellable) { draft.discard() }
        }
    }

    fun dismiss() {
        val draft = mutableState.value.draft ?: return
        mutableState.value = mutableState.value.copy(draft = null)
        viewModelScope.launch(Dispatchers.IO + NonCancellable) { draft.discard() }
    }

    fun importedShown() { mutableState.value = mutableState.value.copy(importedId = null) }

    fun remove(skin: InstalledHomeWidgetSkin, onRemoved: (String) -> Unit) = operate {
        // Finish the explicit deletion and layout reset together even if the drawer is closed.
        withContext(NonCancellable) {
            withContext(Dispatchers.IO) { HomeWidgetSkinStore.remove(skin.id, skin.revision) }
            onRemoved(skin.renderer.styleId)
        }
        messages.send("皮肤已删除")
    }

    private fun operate(block: suspend () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true)
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                messages.send(error.message ?: "无法导入组件皮肤")
            } finally {
                mutableState.value = mutableState.value.copy(busy = false)
            }
        }
    }

    override fun onCleared() {
        mutableState.value.draft?.discard()
        super.onCleared()
    }
}
