package io.legado.app.quickjs

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.DeadObjectException
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.SystemClock
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class QuickJsSandboxBridge(context: Context) {

    private val appContext = context.applicationContext ?: context

    fun evalString(script: String): String = synchronized(evaluationLock) {
        requireSupportedCall(script)

        val serviceRef = AtomicReference<IQuickJsSandbox?>()
        val connectionLatch = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                serviceRef.set(IQuickJsSandbox.Stub.asInterface(service))
                connectionLatch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                serviceRef.set(null)
                connectionLatch.countDown()
            }

            override fun onNullBinding(name: ComponentName?) {
                connectionLatch.countDown()
            }

            override fun onBindingDied(name: ComponentName?) {
                serviceRef.set(null)
                connectionLatch.countDown()
            }
        }

        val bound = try {
            appContext.bindService(
                Intent(appContext, QuickJsSandboxService::class.java),
                connection,
                Context.BIND_AUTO_CREATE
            )
        } catch (_: RuntimeException) {
            false
        }
        if (!bound) fail(QuickJsSandboxProtocol.ERROR_BIND_FAILED)

        try {
            if (!connectionLatch.await(
                    QuickJsSandboxProtocol.BIND_TIMEOUT_MILLIS,
                    TimeUnit.MILLISECONDS
                )
            ) {
                fail(QuickJsSandboxProtocol.ERROR_BIND_TIMEOUT)
            }
            val service = serviceRef.get()
                ?: fail(QuickJsSandboxProtocol.ERROR_PROCESS_DIED)
            val response = callService(service, script)
            if (!response.containsKey(QuickJsSandboxProtocol.KEY_SUCCESS)) {
                fail(QuickJsSandboxProtocol.ERROR_INVALID_RESPONSE)
            }
            if (!response.getBoolean(QuickJsSandboxProtocol.KEY_SUCCESS)) {
                fail(
                    response.getString(QuickJsSandboxProtocol.KEY_ERROR)
                        ?: QuickJsSandboxProtocol.ERROR_INVALID_RESPONSE
                )
            }
            response.getString(QuickJsSandboxProtocol.KEY_VALUE)
                ?: fail(QuickJsSandboxProtocol.ERROR_INVALID_RESPONSE)
        } finally {
            runCatching { appContext.unbindService(connection) }
        }
    }

    private fun callService(service: IQuickJsSandbox, script: String): Bundle {
        val pipe = try {
            ParcelFileDescriptor.createPipe()
        } catch (_: IOException) {
            fail(QuickJsSandboxProtocol.ERROR_INPUT_PIPE_FAILED)
        }
        val readSide = pipe[0]
        val writeSide = pipe[1]
        val writer = Thread({
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(writeSide)
                    .bufferedWriter(Charsets.UTF_8)
                    .use { it.write(script) }
            } catch (_: IOException) {
                // The service may close the read side after rejecting input or being killed.
            } finally {
                runCatching { writeSide.close() }
            }
        }, "quickjs-sandbox-input").apply {
            isDaemon = true
        }

        try {
            try {
                writer.start()
            } catch (_: RuntimeException) {
                fail(QuickJsSandboxProtocol.ERROR_INPUT_PIPE_FAILED)
            }
            val evaluationStartedAt = SystemClock.elapsedRealtime()
            return try {
                readSide.use { descriptor ->
                    service.evalString(descriptor, script.length)
                }
            } catch (_: DeadObjectException) {
                fail(processFailureCode(evaluationStartedAt))
            } catch (_: RemoteException) {
                fail(processFailureCode(evaluationStartedAt))
            } ?: fail(QuickJsSandboxProtocol.ERROR_INVALID_RESPONSE)
        } finally {
            runCatching { readSide.close() }
            runCatching { writeSide.close() }
            if (writer.isAlive) {
                writer.interrupt()
                runCatching { writer.join(1_000L) }
            }
        }
    }

    private fun requireSupportedCall(script: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            fail(QuickJsSandboxProtocol.ERROR_UNSUPPORTED_API)
        }
        if (Looper.getMainLooper().thread === Thread.currentThread()) {
            fail(QuickJsSandboxProtocol.ERROR_MAIN_THREAD)
        }
        if (script.length > QuickJsSandboxProtocol.MAX_INPUT_CHARS) {
            fail(QuickJsSandboxProtocol.ERROR_INPUT_TOO_LARGE)
        }
    }

    private fun fail(code: String): Nothing {
        throw IllegalStateException("QuickJS sandbox failed: $code")
    }

    private fun processFailureCode(evaluationStartedAt: Long): String {
        val elapsed = SystemClock.elapsedRealtime() - evaluationStartedAt
        val timeoutThreshold = QuickJsSandboxProtocol.EVALUATION_TIMEOUT_MILLIS -
            QuickJsSandboxProtocol.TIMEOUT_DETECTION_TOLERANCE_MILLIS
        return if (elapsed >= timeoutThreshold) {
            QuickJsSandboxProtocol.ERROR_EVALUATION_TIMEOUT
        } else {
            QuickJsSandboxProtocol.ERROR_PROCESS_DIED
        }
    }

    private companion object {
        val evaluationLock = Any()
    }
}
