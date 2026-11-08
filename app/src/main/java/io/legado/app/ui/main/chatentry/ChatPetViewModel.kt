package io.legado.app.ui.main.chatentry

import io.legado.app.help.pet.rethrowFatalPetError
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.ViewModel
import io.legado.app.help.pet.PetPluginHandle
import io.legado.app.help.pet.PetPluginStore
import io.legado.pet.api.PetSession

private const val MAX_STATE_BYTES = 4096
private const val MAX_SAVED_PETS = 16
private const val STATE_KEY = "petSessionsV2"

/** Holds only the selected live instance; opaque state for inactive IDs holds no plugin objects. */
class ChatPetViewModel : ViewModel() {
    private val saved = linkedMapOf<String, ByteArray>()
    private var active: PetBinding? = null
    private val forgottenTokens = mutableMapOf<String, Long>()

    internal fun forget(id: String, token: Long) {
        if (forgottenTokens[id] == token) return
        forgottenTokens[id] = token
        saved.remove(id)
        active?.takeIf { it.id == id }?.let {
            it.retire()
            active = null
        }
    }

    internal fun bindingFor(handle: PetPluginHandle?): PetBinding? {
        active?.let { previous ->
            if (handle === previous.plugin) return previous.takeUnless { it.failed }
            remember(previous.id, previous.snapshot())
            previous.retire()
            active = null
        }
        if (handle == null) return null
        return PetBinding(handle.id, handle, saved.remove(handle.id)).also { active = it }
    }

    private fun remember(id: String, state: ByteArray?) {
        saved.remove(id)
        if (state != null) saved[id] = state
        while (saved.size > MAX_SAVED_PETS) saved.remove(saved.keys.first())
    }

    fun restorePlacements(bundle: Bundle?) {
        if (active != null || saved.isNotEmpty()) return
        val states = bundle?.getBundle(STATE_KEY) ?: return
        for (id in states.keySet().take(MAX_SAVED_PETS)) {
            states.getByteArray(id)?.takeIf { it.size <= MAX_STATE_BYTES }?.let { remember(id, it.copyOf()) }
        }
    }

    fun savePlacements(): Bundle {
        active?.let { remember(it.id, it.snapshot()) }
        return Bundle().apply {
            putBundle(STATE_KEY, Bundle().apply { saved.forEach { (id, bytes) -> putByteArray(id, bytes) } })
        }
    }

    override fun onCleared() {
        active?.retire()
        active = null
        super.onCleared()
    }
}

internal class PetBinding(
    val id: String,
    val plugin: PetPluginHandle,
    private var lastState: ByteArray?,
) {
    var session: PetSession? = null
        private set
    var failed = false
        private set
    var retired = false
        private set
    var onFailure: (() -> Unit)? = null

    /** The open ZIP remains available until the last callback even if an update unlinks its path. */
    fun openSession(): PetSession {
        val assets = plugin.openAssets()
        try {
            val instance = plugin.provider.createSession(assets)
            return object : PetSession by instance {
                override fun close() {
                    try { instance.close() } finally { assets.close() }
                }
            }
        } catch (error: Throwable) {
            assets.close()
            throw error
        }
    }

    fun attach(value: PetSession) {
        check(!retired && !failed && session == null)
        session = value
        lastState?.let { bytes -> invoke("restoreState", Unit) { it.restoreState(bytes.copyOf()) } }
    }

    fun snapshot(): ByteArray? {
        if (session != null) {
            invoke("saveState", Unit) {
                val bytes = it.saveState()
                require(bytes == null || bytes.size <= MAX_STATE_BYTES) { "Plugin state exceeds 4096 bytes" }
                lastState = bytes?.copyOf()
            }
        }
        return lastState
    }

    /** Plugin callback failures disable this instance. Fatal VM errors and thread termination propagate. */
    inline fun <T> invoke(stage: String, fallback: T, action: (PetSession) -> T): T {
        val current = session ?: return fallback
        return try {
            action(current)
        } catch (failure: Throwable) {
            failure.rethrowFatalPetError()
            fail(stage, failure)
            fallback
        }
    }

    fun fail(stage: String, cause: Throwable) {
        if (retired || failed) return
        failed = true
        PetPluginStore.reportFailure(id, stage, cause)
        Log.e("PetPlugin", id + "@" + plugin.sha256.take(12) + " failed in " + stage, cause)
        val previous = session
        session = null
        if (previous != null) closeSession(previous)
        onFailure?.invoke()
    }

    fun retire() {
        retired = true
        onFailure = null
        val previous = session
        session = null
        if (previous != null) closeSession(previous)
    }

    fun closeSession(value: PetSession) {
        try {
            value.close()
        } catch (error: Throwable) {
            error.rethrowFatalPetError()
            Log.w("PetPlugin", id + " failed in close", error)
        }
    }
}
