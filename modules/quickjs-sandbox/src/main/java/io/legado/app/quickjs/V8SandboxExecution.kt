package io.legado.app.quickjs

import android.content.Context
import androidx.javascriptengine.JavaScriptSandbox
import java.io.Closeable

/** Host-owned lifetime. This type is not visible to book-source Rhino code. */
class V8SandboxExecution(context: Context) : Closeable {
    private val appContext = context.applicationContext ?: context
    private val lock = Any()
    private var lease: V8SandboxConnection.Lease? = null
    private var closed = false
    val bridge = V8SandboxBridge(appContext, this)

    fun prepare() = synchronized(lock) {
        check(!closed) { "V8 execution has ended" }
        lease?.let {
            if (V8SandboxConnection.isRetired(it)) {
                V8SandboxConnection.release(it)
                lease = null
            }
        }
        if (lease == null) lease = V8SandboxConnection.acquire(appContext)
    }

    internal fun <T> withSandbox(block: (JavaScriptSandbox) -> T): T = synchronized(lock) {
        prepare()
        val active = requireNotNull(lease)
        try {
            block(V8SandboxConnection.connected(active))
        } catch (error: Throwable) {
            V8SandboxConnection.discard(active)
            V8SandboxConnection.release(active)
            lease = null
            throw error
        }
    }

    override fun close() = synchronized(lock) {
        if (!closed) {
            closed = true
            lease?.let(V8SandboxConnection::release)
            lease = null
        }
    }
}
