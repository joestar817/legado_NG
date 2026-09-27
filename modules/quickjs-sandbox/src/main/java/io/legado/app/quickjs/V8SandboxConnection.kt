package io.legado.app.quickjs

import android.content.Context
import android.os.Build
import androidx.javascriptengine.JavaScriptSandbox
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** One system connection, shared only while host executions own leases. Never shares JS state. */
internal object V8SandboxConnection {
    private val lock = Any()
    private var current: Lease? = null

    internal class Lease(val future: ListenableFuture<JavaScriptSandbox>) {
        var owners = 0
        var retired = false
    }

    fun acquire(context: Context): Lease = synchronized(lock) {
        check(Build.VERSION.SDK_INT >= 26 && JavaScriptSandbox.isSupported()) {
            "V8 sandbox failed: UNSUPPORTED_API"
        }
        var lease = current
        if (lease?.retired == true) error("V8 sandbox failed: CONNECTION_CLOSING")
        if (lease == null) {
            lease = Lease(JavaScriptSandbox.createConnectedInstanceAsync(context)).also { it.owners = 1 }
            current = lease
            val created = lease
            // Do not cancel a pending AndroidX bind: close the resulting connection,
            // including when its owner finishes before the asynchronous bind completes.
            created.future.addListener({ synchronized(lock) { closeIfUnused(created) } }, Executor { it.run() })
        } else lease.owners++
        lease
    }

    fun isRetired(lease: Lease): Boolean = synchronized(lock) { lease.retired }

    fun connected(lease: Lease): JavaScriptSandbox {
        check(!isRetired(lease)) { "V8 sandbox failed: PROCESS_DIED" }
        return try {
            lease.future.get(QuickJsSandboxProtocol.BIND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            discard(lease)
            throw IllegalStateException("V8 sandbox failed: BIND_TIMEOUT", error)
        }
    }

    fun release(lease: Lease) = synchronized(lock) {
        check(lease.owners > 0)
        lease.owners--
        // An unused pending bind can be adopted by the next host execution. No JS
        // has run yet; otherwise the completion listener closes it with no owner.
        closeIfUnused(lease)
    }

    fun discard(lease: Lease) = synchronized(lock) {
        lease.retired = true
        closeIfUnused(lease)
    }

    private fun closeIfUnused(lease: Lease) {
        if ((!lease.retired && lease.owners > 0) || !lease.future.isDone) return
        runCatching { lease.future.get().close() }
        if (current === lease) current = null
    }
}
