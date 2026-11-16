package io.legado.app.ui.config

import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.help.tts.TtsVoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/** Only available voices become actionable cards; enabled but unavailable engines keep their tab. */
internal fun TtsEngineSetting.toVoiceDrawerGroup(
    systemVoiceName: String,
    isSelected: (TtsVoiceOption) -> Boolean,
): TtsVoiceDrawerGroup {
    val unavailable = runtimeUnavailableReason
    val voices = effectiveVoices()
    val enabledVoices = if (unavailable == null) enabledVoices() else emptyList()
    val options = when {
        unavailable != null -> emptyList()
        type == TtsEngineType.SYSTEM -> listOf(
            TtsVoiceOption(
                engine = this,
                voice = TtsVoice(id = TtsEngineStore.SYSTEM_DEFAULT_ID, name = systemVoiceName),
                systemDefault = true,
            ),
        )
        else -> enabledVoices.map { TtsVoiceOption(this, it, systemDefault = false) }
    }
    return TtsVoiceDrawerGroup(
        engineId = id,
        engineName = name,
        cards = options.map { it.toDrawerCard(selected = isSelected(it)) },
        needsFetch = unavailable == null && voices.isEmpty() && supportsVoiceFetch(),
        emptyMessage = unavailable ?: "没有已启用的发音人".takeIf {
            type != TtsEngineType.SYSTEM && voices.isNotEmpty() && enabledVoices.isEmpty()
        },
    )
}

internal fun TtsVoiceDrawerState.withVoiceGroups(
    groups: List<TtsVoiceDrawerGroup>,
): TtsVoiceDrawerState {
    val cards = groups.flatMap { it.cards }
    return copy(
        groups = groups,
        languageOptions = TtsVoiceFilterSupport.availableLanguageLabels(cards.map { it.option.voice }),
        genderOptions = listOf("男", "女").filter { label -> cards.any { it.genderLabel == label } },
    )
}

/** Fetch once when a user opens an empty engine tab; failures can be retried explicitly. */
internal class TtsVoiceDrawerCatalogLoader(
    private val scope: CoroutineScope,
    private val readState: () -> TtsVoiceDrawerState,
    private val writeState: (TtsVoiceDrawerState) -> Unit,
    private val fetch: suspend (String) -> Unit,
    private val loadGroup: suspend (String) -> TtsVoiceDrawerGroup?,
    private val onLoaded: () -> Unit = {},
) {
    private val attempted = mutableSetOf<String>()
    private val jobs = mutableMapOf<String, Job>()

    fun open(engineId: String) = request(engineId, retry = false)

    fun retry(engineId: String) = request(engineId, retry = true)

    private fun request(engineId: String, retry: Boolean) {
        val group = readState().groups.firstOrNull { it.engineId == engineId } ?: return
        if (!group.needsFetch || group.fetching || jobs[engineId]?.isActive == true) return
        if (!retry && engineId in attempted) return
        attempted += engineId
        updateGroup(engineId) { it.copy(fetching = true, fetchFailed = false) }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                fetch(engineId)
                val loaded = loadGroup(engineId)
                val current = readState()
                writeState(current.withVoiceGroups(current.groups.mapNotNull {
                    if (it.engineId == engineId) loaded else it
                }))
                onLoaded()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                updateGroup(engineId) { it.copy(fetching = false, fetchFailed = true) }
            } finally {
                if (jobs[engineId] === coroutineContext[Job]) jobs.remove(engineId)
            }
        }
        jobs[engineId] = job
        job.start()
    }

    suspend fun awaitPending() {
        jobs.values.toList().joinAll()
    }

    fun cancel() {
        jobs.values.toList().forEach { it.cancel() }
        jobs.clear()
        attempted.clear()
    }

    private fun updateGroup(engineId: String, transform: (TtsVoiceDrawerGroup) -> TtsVoiceDrawerGroup) {
        val current = readState()
        writeState(current.copy(groups = current.groups.map {
            if (it.engineId == engineId) transform(it) else it
        }))
    }
}
