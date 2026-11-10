package io.legado.app.help.resource

import io.legado.app.constant.PreferKey
import io.legado.app.help.ai.AiChatEntryStyle
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.pet.PetImportCandidate
import io.legado.app.help.pet.PetImportResult
import io.legado.app.help.pet.PetLocalRecord
import io.legado.app.help.pet.PetPluginStore
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import splitties.init.appCtx

/** All sources share installation mutations; navigation itself never enables a pet. */
internal object PetManagementActions {
    private val forgotten = MutableStateFlow<Map<String, Long>>(emptyMap())
    val forgottenStates = forgotten.asStateFlow()

    suspend fun records(): List<PetLocalRecord> = ResourceRepository.localPetRecords()
    suspend fun readPreview(id: String): ByteArray? = ResourceRepository.localPetPreview(id)
    suspend fun fetchCatalogPreview(pet: PetLocalRecord) = ResourceRepository.localPetCatalogPreview(pet)
    suspend fun commit(candidate: PetImportCandidate, allowReplace: Boolean = false,
        allowDowngrade: Boolean = false): PetImportResult =
        ResourceRepository.commitLocalPet(candidate, allowReplace, allowDowngrade)

    suspend fun use(id: String) = withContext(Dispatchers.IO) {
        if (id != AiChatEntryStyle.BUTTON.id) {
            PetPluginStore.invalidateLoad(id)
            check(PetPluginStore.load(id) != null) { "宠物无法运行，请查看诊断或重新导入" }
        }
        withContext(Dispatchers.Main) { AiConfig.chatEntryStyle = AiChatEntryStyle.fromId(id) }
    }

    suspend fun reload(id: String) = withContext(Dispatchers.IO) {
        check(AiConfig.chatFabEnabled && appCtx.getPrefString(PreferKey.aiChatEntryStyle) == id) {
            "请先使用该宠物"
        }
        PetPluginStore.invalidateLoad(id)
        check(PetPluginStore.load(id) != null) { "宠物无法运行，请查看诊断或重新导入" }
    }

    suspend fun export(id: String, output: java.io.OutputStream) = withContext(Dispatchers.IO) {
        // Open while mutations are excluded; the descriptor remains valid if that revision is unlinked.
        val input = synchronized(PetPluginStore) { PetPluginStore.exportFile(id).inputStream() }
        input.use { it.copyTo(output) }
    }

    suspend fun uninstall(id: String) = withContext(Dispatchers.IO + NonCancellable) {
        try {
            ResourceRepository.removeLocalPet(id)
        } finally {
            // File cleanup may fail after the pointer was removed. Never retain an uninstalled selection.
            if (PetPluginStore.record(id) == null) {
                if (appCtx.getPrefString(PreferKey.aiChatEntryStyle) == id) {
                    appCtx.putPrefString(PreferKey.aiChatEntryStyle, AiChatEntryStyle.BUTTON.id)
                }
                forgotten.update { it + (id to System.nanoTime()) }
            }
        }
    }
}
