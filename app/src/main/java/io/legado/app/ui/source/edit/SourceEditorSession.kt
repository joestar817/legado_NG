package io.legado.app.ui.source.edit

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.AtomicFile
import androidx.activity.result.ActivityResult
import com.google.gson.annotations.SerializedName
import io.legado.app.constant.AppLog
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.code.CodeEditSessionStore
import io.legado.app.utils.GSON
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

internal data class SourceEditorField(
    @SerializedName("tab") val tab: Int,
    @SerializedName("key") val key: String,
    @SerializedName("start") val start: Int,
    @SerializedName("end") val end: Int,
)

internal data class SourceEditorRequest(
    @SerializedName("field") val field: SourceEditorField,
    @SerializedName("revision") val revision: Long,
    @SerializedName("codeSession") val codeSession: String?,
)

internal data class SourceEditorWindow(
    @SerializedName("entryUrl") val entryUrl: String?,
    @SerializedName("persistedUrl") val persistedUrl: String?,
    @SerializedName("tab") val tab: Int,
    @SerializedName("selection") val selection: SourceEditorField?,
    @SerializedName("revision") val revision: Long,
    @SerializedName("autoComplete") val autoComplete: Boolean,
    @SerializedName("request") val request: SourceEditorRequest?,
)

/** Owns disk checkpoints and child results for one instance of a source form. */
internal class SourceEditorSession<D : Any>(
    context: Context,
    name: String,
    private val draftType: Class<D>,
    private val scope: CoroutineScope,
) {
    private val directory = File(context.cacheDir, "source_editor_drafts/$name")
    private var id = UUID.randomUUID().toString()
    private var queuedWrite: Job? = null
    private var opening = false
    private var closed = false
    private var returned: ChildResult? = null
    private var requestLaunched = false
    private val completedSessions = linkedSetOf<String>()
    private val disk = Mutex()

    var pending: SourceEditorRequest? = null
        private set

    fun restoreInstanceState(state: Bundle?): Boolean {
        val restored = state?.getString(STATE_DRAFT_ID) ?: return false
        id = restored
        requestLaunched = state.getBoolean(STATE_CHILD_LAUNCHED)
        returned = state.getBundle(STATE_CHILD_RESULT)?.let(ChildResult::fromBundle) ?: returned
        return true
    }

    fun writeInstanceState(state: Bundle) {
        state.putString(STATE_DRAFT_ID, id)
        state.putBoolean(STATE_CHILD_LAUNCHED, requestLaunched)
        returned?.let { state.putBundle(STATE_CHILD_RESULT, it.toBundle()) }
    }

    fun restoreRequest(request: SourceEditorRequest?) {
        if (requestLaunched || returned != null) {
            pending = request
        } else {
            deleteCodeSession(request?.codeSession)
        }
    }

    suspend fun read(): D? = withContext(IO) {
        disk.withLock { file().openRead().bufferedReader().use { draftJson.fromJson(it, draftType) } }
    }

    fun scheduleCheckpoint(capture: () -> D) {
        queueCheckpoint(300) { capture() }
    }

    fun checkpoint(draft: D) {
        queueCheckpoint(0) { draft }
    }

    private fun queueCheckpoint(waitMillis: Long, capture: () -> D) {
        if (closed) return
        queuedWrite?.cancel()
        queuedWrite = scope.launch {
            delay(waitMillis)
            try {
                writeAndRelease(capture())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.put("保存源编辑草稿失败", error)
            }
        }
    }

    suspend fun openChild(
        field: SourceEditorField,
        revision: Long,
        text: String?,
        isCurrent: () -> Boolean,
        capture: () -> D,
        launch: (SourceEditorRequest) -> Unit,
    ) {
        if (opening || pending != null || closed || !isCurrent()) return
        opening = true
        var childId: String? = null
        var request: SourceEditorRequest? = null
        var launched = false
        try {
            if (text != null) withContext(IO) { childId = CodeEditSessionStore.app.create(text) }
            if (!isCurrent() || closed) return
            request = SourceEditorRequest(field, revision, childId)
            pending = request
            queuedWrite?.cancel()
            writeAndRelease(capture())
            if (!isCurrent() || closed) return
            requestLaunched = true
            launch(request)
            launched = true
        } finally {
            opening = false
            if (!launched) {
                deleteCodeSession(childId)
                if (request != null && pending === request) {
                    pending = null
                    requestLaunched = false
                    checkpoint(capture())
                }
            }
        }
    }

    fun receiveCode(result: ActivityResult) {
        val data = result.data
        val accepted = result.resultCode == Activity.RESULT_OK
        val id = data?.getStringExtra(CodeEditActivity.EXTRA_TEXT_SESSION_ID)
        if (returned != null || pending?.let { it.codeSession == null || accepted && it.codeSession != id } == true) return
        returned = ChildResult(
            code = true,
            accepted = accepted,
            sessionId = id,
            cursor = data?.getIntExtra("cursorPosition", -1) ?: -1,
            changed = data?.getBooleanExtra(CodeEditActivity.EXTRA_RESULT_TEXT_CHANGED, true) ?: true,
        )
    }

    fun receiveFile(uri: Uri?) {
        if (returned != null || pending?.codeSession != null) return
        returned = ChildResult(code = false, accepted = uri != null, path = uri?.toString())
    }

    suspend fun applyResult(
        isCurrent: (SourceEditorRequest) -> Boolean,
        applyCode: (SourceEditorField, String?, Int) -> Unit,
        applyFile: (SourceEditorField, Uri) -> Unit,
        capture: () -> D,
    ) {
        val result = returned ?: return
        val request = pending
        val childId = request?.codeSession
        if (request == null || (childId != null) != result.code ||
            result.code && result.accepted && childId != result.sessionId
        ) {
            returned = null
            return
        }
        var text: String? = null
        var failure: Exception? = null
        if (result.accepted && isCurrent(request) && childId != null && result.changed) {
            try {
                text = withContext(IO) { CodeEditSessionStore.app.readWorkingText(childId) }
                    ?: throw IOException("代码编辑会话已丢失")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failure = error
            }
        }
        if (returned !== result || pending !== request || closed) return
        pending = null
        requestLaunched = false
        returned = null
        try {
            failure?.let { throw it }
            if (result.accepted && isCurrent(request)) {
                if (result.code) {
                    applyCode(request.field, text, result.cursor.takeIf { it >= 0 } ?: request.field.end)
                } else {
                    result.path?.let { applyFile(request.field, Uri.parse(it)) }
                }
            }
        } finally {
            if (childId != null) completedSessions.add(childId)
            checkpoint(capture())
        }
    }

    fun close() {
        if (closed) return
        closed = true
        queuedWrite?.cancel()
        deleteCodeSession(pending?.codeSession)
        completedSessions.forEach(::deleteCodeSession)
        completedSessions.clear()
        pending = null
        returned = null
        Coroutine.async(context = IO) { disk.withLock { file().delete() } }
    }

    private suspend fun writeAndRelease(draft: D) {
        val completed = completedSessions.toList()
        write(draft)
        completed.forEach { sessionId ->
            if (completedSessions.remove(sessionId)) deleteCodeSession(sessionId)
        }
    }

    private suspend fun write(draft: D) = withContext(IO) {
        disk.withLock {
            val file = file()
            val output = file.startWrite()
            try {
                val writer = output.bufferedWriter()
                draftJson.toJson(draft, draftType, writer)
                writer.flush()
                file.finishWrite(output)
            } catch (error: Throwable) {
                file.failWrite(output)
                throw error
            }
        }
    }

    private fun file(): AtomicFile {
        require(UUID.fromString(id).toString() == id)
        return AtomicFile(File(directory, "$id.json"))
    }

    private fun deleteCodeSession(id: String?) {
        if (id != null) Coroutine.async(context = IO) { CodeEditSessionStore.app.delete(id) }
    }

    private data class ChildResult(
        val code: Boolean,
        val accepted: Boolean,
        val sessionId: String? = null,
        val cursor: Int = -1,
        val changed: Boolean = false,
        val path: String? = null,
    ) {
        fun toBundle() = Bundle().apply {
            putBoolean("code", code)
            putBoolean("accepted", accepted)
            putString("sessionId", sessionId)
            putInt("cursor", cursor)
            putBoolean("changed", changed)
            putString("uri", path)
        }

        companion object {
            fun fromBundle(state: Bundle) = ChildResult(
                state.getBoolean("code"), state.getBoolean("accepted"),
                state.getString("sessionId"), state.getInt("cursor", -1),
                state.getBoolean("changed"), state.getString("uri"),
            )
        }
    }

    companion object {
        const val STATE_DRAFT_ID = "sourceEditorDraftId"
        private const val STATE_CHILD_RESULT = "sourceEditorChildResult"
        private const val STATE_CHILD_LAUNCHED = "sourceEditorChildLaunched"
        private val draftJson by lazy { GSON.newBuilder().serializeNulls().create() }
    }
}

internal fun Intent.putSourceFieldSession(request: SourceEditorRequest) {
    putExtra(CodeEditActivity.EXTRA_TEXT_SESSION_ID, requireNotNull(request.codeSession))
    putExtra(CodeEditActivity.EXTRA_SOURCE_FIELD_SESSION, true)
    putExtra("cursorPosition", request.field.end)
}
