package io.legado.app.quickjs

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Owns a lazy service binding for one synchronous host execution, never JS state. */
class QuickJsSandboxExecution(context: Context) : Closeable {
    private val appContext = context.applicationContext ?: context
    private val lock = Any()
    private var binding: Binding? = null
    private var closed = false

    val bridge = QuickJsSandboxBridge(appContext, this)

    /** Begin binding without running JS; owned and cancelled by this host execution. */
    fun prepare() = synchronized(lock) {
        check(!closed) { "QuickJS execution has ended" }
        if (binding == null) {
            val next = Binding()
            binding = next
            try {
                next.startBinding()
            } catch (error: Throwable) {
                next.close()
                binding = null
                throw error
            }
        }
    }

    internal fun <T> withService(block: (IQuickJsSandbox) -> T): T = synchronized(lock) {
        check(!closed) { "QuickJS execution has ended" }
        try {
            // Another execution may have hit the watchdog while this owner was idle.
            // Rebind before sending a new request, without retrying an evaluated script.
            binding?.let { current ->
                if (current.isReady && current.service.get()?.asBinder()?.isBinderAlive != true) {
                    current.close()
                    binding = null
                }
            }
            prepare()
            val current = requireNotNull(binding)
            current.awaitReady()
            block(current.service.get() ?: fail(QuickJsSandboxProtocol.ERROR_PROCESS_DIED))
        } catch (error: Throwable) {
            binding?.close()
            binding = null
            throw error
        }
    }

    override fun close() = synchronized(lock) {
        closed = true
        binding?.close()
        binding = null
    }

    private inner class Binding : ServiceConnection, Closeable {
        val service = AtomicReference<IQuickJsSandbox?>()
        private val ready = CountDownLatch(1)
        private var bound = false
        val isReady get() = ready.count == 0L

        fun startBinding() {
            bound = try {
                appContext.bindService(
                    Intent(appContext, QuickJsSandboxService::class.java),
                    this,
                    Context.BIND_AUTO_CREATE,
                )
            } catch (_: RuntimeException) {
                false
            }
            if (!bound) fail(QuickJsSandboxProtocol.ERROR_BIND_FAILED)
        }

        fun awaitReady() {
            if (!ready.await(QuickJsSandboxProtocol.BIND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                fail(QuickJsSandboxProtocol.ERROR_BIND_TIMEOUT)
            }
        }

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service.set(IQuickJsSandbox.Stub.asInterface(binder))
            ready.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service.set(null)
            ready.countDown()
        }

        override fun onNullBinding(name: ComponentName?) = onServiceDisconnected(name)

        override fun onBindingDied(name: ComponentName?) = onServiceDisconnected(name)

        override fun close() {
            if (bound) {
                bound = false
                runCatching { appContext.unbindService(this) }
            }
            service.set(null)
            ready.countDown()
        }
    }

    private fun fail(code: String): Nothing =
        throw IllegalStateException("QuickJS sandbox failed: $code")
}
