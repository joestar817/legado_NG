package io.legado.app.quickjs

import android.content.Context
import android.os.Build
import android.os.DeadObjectException
import android.os.Bundle
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.SystemClock
import java.io.Closeable
import java.io.IOException

class QuickJsSandboxBridge internal constructor(
    context: Context,
    private val execution: QuickJsSandboxExecution?,
) {
    constructor(context: Context) : this(context, null)

    private val appContext = context.applicationContext ?: context

    fun evalString(script: String): String = evaluate(script, null)

    /** The data is visible only as the string `sandboxData` in this fresh runtime. */
    fun evalStringWithData(script: String, data: String): String = evaluate(script, data)

    private fun evaluate(script: String, data: String?): String = synchronized(evaluationLock) {
        requireSupportedCall(script)
        if (data != null && (data.length > QuickJsSandboxProtocol.MAX_DATA_CHARS ||
                data.toByteArray(Charsets.UTF_8).size > QuickJsSandboxProtocol.MAX_DATA_BYTES)) {
            fail(QuickJsSandboxProtocol.ERROR_INPUT_TOO_LARGE)
        }

        if (execution == null) {
            return@synchronized QuickJsSandboxExecution(appContext).use {
                it.bridge.evaluate(script, data)
            }
        }
        execution.withService { service ->
            val response = callService(service, script, data)
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
        }
    }

    private inner class InputPipe(text: String) : Closeable {
        private val pipe = try {
            ParcelFileDescriptor.createPipe()
        } catch (_: IOException) {
            fail(QuickJsSandboxProtocol.ERROR_INPUT_PIPE_FAILED)
        }
        val readSide = pipe[0]
        private val writeSide = pipe[1]
        private val writer = Thread({
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(writeSide)
                    .bufferedWriter(Charsets.UTF_8)
                    .use { it.write(text) }
            } catch (_: IOException) {
                // The service may close the read side after rejecting input or being killed.
            } finally {
                runCatching { writeSide.close() }
            }
        }, "quickjs-sandbox-input").apply {
            isDaemon = true
        }

        fun start() {
            try {
                writer.start()
            } catch (_: RuntimeException) {
                fail(QuickJsSandboxProtocol.ERROR_INPUT_PIPE_FAILED)
            }
        }

        override fun close() {
            runCatching { readSide.close() }
            runCatching { writeSide.close() }
            if (writer.isAlive) {
                writer.interrupt()
                runCatching { writer.join(1_000L) }
            }
        }
    }

    private fun callService(service: IQuickJsSandbox, script: String, data: String?): Bundle =
        InputPipe(script).use { scriptPipe ->
            // Starting both bounded streams before the Binder call avoids pipe deadlocks.
            // use also closes the first stream if allocation of the second one fails.
            val dataPipe = data?.let { InputPipe(it) }
            dataPipe.use {
                scriptPipe.start()
                dataPipe?.start()
                val evaluationStartedAt = SystemClock.elapsedRealtime()
                try {
                    if (dataPipe == null) {
                        service.evalString(scriptPipe.readSide, script.length)
                    } else {
                        service.evalStringWithData(
                            scriptPipe.readSide, script.length,
                            dataPipe.readSide, requireNotNull(data).length,
                        )
                    }
                } catch (_: DeadObjectException) {
                    fail(processFailureCode(evaluationStartedAt))
                } catch (_: RemoteException) {
                    fail(processFailureCode(evaluationStartedAt))
                } ?: fail(QuickJsSandboxProtocol.ERROR_INVALID_RESPONSE)
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
