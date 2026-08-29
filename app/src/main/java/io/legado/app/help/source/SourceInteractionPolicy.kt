package io.legado.app.help.source

import io.legado.app.exception.NoStackTraceException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Restricts foreground UI started by book-source scripts in a specific coroutine tree.
 */
class SourceInteractionPolicy(
    blockDialogs: Boolean,
    val blockMediaLaunches: Boolean = false,
    val throwOnBlocked: Boolean = false,
) : AbstractCoroutineContextElement(Key) {

    private val blockDialogsState = AtomicBoolean(blockDialogs)
    private val blockedRequestState = AtomicReference<SourceInteractionRequest?>(null)

    val blockDialogs: Boolean
        get() = blockDialogsState.get()

    val blockedRequest: SourceInteractionRequest?
        get() = blockedRequestState.get()

    fun updateBlockDialogs(blockDialogs: Boolean) {
        blockDialogsState.set(blockDialogs)
    }

    fun shouldBlock(kind: SourceInteractionKind): Boolean {
        return when (kind) {
            SourceInteractionKind.BROWSER,
            SourceInteractionKind.BROWSER_VERIFICATION -> {
                blockDialogs
            }
            SourceInteractionKind.VERIFICATION_CODE -> blockDialogs
            SourceInteractionKind.VIDEO_PLAYER -> blockMediaLaunches
        }
    }

    fun recordBlocked(request: SourceInteractionRequest) {
        blockedRequestState.compareAndSet(null, request)
    }

    companion object Key : CoroutineContext.Key<SourceInteractionPolicy>
}

enum class SourceInteractionKind {
    BROWSER,
    BROWSER_VERIFICATION,
    VERIFICATION_CODE,
    VIDEO_PLAYER,
}

data class SourceInteractionRequest(
    val kind: SourceInteractionKind,
    val url: String,
    val title: String = "",
    val html: String? = null,
    val saveResult: Boolean = false,
    val refetchAfterSuccess: Boolean = true,
    val isFloat: Boolean = false,
) {
    val actionName: String
        get() = when (kind) {
            SourceInteractionKind.BROWSER -> "网页"
            SourceInteractionKind.BROWSER_VERIFICATION -> "验证网页"
            SourceInteractionKind.VERIFICATION_CODE -> "验证码"
            SourceInteractionKind.VIDEO_PLAYER -> "视频播放"
        }
}

class SourceInteractionBlockedException(
    val request: SourceInteractionRequest,
) : NoStackTraceException("已禁止书源弹窗：${request.actionName}")
