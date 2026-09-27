package io.legado.app.quickjs

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.FunctionBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.IOException

class QuickJsSandboxService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val evaluationLock = Any()

    private val binder = object : IQuickJsSandbox.Stub() {
        override fun evalString(
            script: ParcelFileDescriptor,
            expectedChars: Int,
        ): Bundle = synchronized(evaluationLock) {
            withWatchdog {
                val readResult = readScript(script, expectedChars)
                if (readResult.error != null) {
                    failure(readResult.error)
                } else {
                    evaluateInFreshRuntime(requireNotNull(readResult.script))
                }
            }
        }

        override fun evalStringWithData(
            script: ParcelFileDescriptor,
            expectedChars: Int,
            data: ParcelFileDescriptor,
            expectedDataChars: Int,
        ): Bundle = synchronized(evaluationLock) {
            // Close both descriptors even when the other stream was rejected.
            script.use {
                data.use {
                    withWatchdog {
                        val source = readScript(script, expectedChars)
                        if (source.error != null) {
                            failure(source.error)
                        } else {
                            val input = readScript(
                                data, expectedDataChars,
                                QuickJsSandboxProtocol.MAX_DATA_CHARS,
                                QuickJsSandboxProtocol.MAX_DATA_BYTES,
                            )
                            if (input.error != null) failure(input.error)
                            else evaluateInFreshRuntime(requireNotNull(source.script), input.script)
                        }
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun readScript(
        descriptor: ParcelFileDescriptor,
        expectedChars: Int,
        maxChars: Int = QuickJsSandboxProtocol.MAX_INPUT_CHARS,
        maxBytes: Int = QuickJsSandboxProtocol.MAX_INPUT_BYTES,
    ): ScriptReadResult {
        if (expectedChars !in 0..maxChars) {
            runCatching { descriptor.close() }
            return ScriptReadResult(error = QuickJsSandboxProtocol.ERROR_INPUT_TOO_LARGE)
        }
        return try {
            val output = ByteArrayOutputStream(minOf(expectedChars, 64 * 1024))
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > maxBytes) {
                        return ScriptReadResult(
                            error = QuickJsSandboxProtocol.ERROR_INPUT_TOO_LARGE
                        )
                    }
                    output.write(buffer, 0, count)
                }
            }
            val script = output.toString(Charsets.UTF_8.name())
            if (script.length != expectedChars) {
                ScriptReadResult(error = QuickJsSandboxProtocol.ERROR_INPUT_READ_FAILED)
            } else {
                ScriptReadResult(script = script)
            }
        } catch (_: IOException) {
            ScriptReadResult(error = QuickJsSandboxProtocol.ERROR_INPUT_READ_FAILED)
        }
    }

    private inline fun withWatchdog(block: () -> Bundle): Bundle {
        val watchdog = Runnable {
            Process.killProcess(Process.myPid())
        }
        mainHandler.postDelayed(watchdog, QuickJsSandboxProtocol.EVALUATION_TIMEOUT_MILLIS)
        return try {
            block()
        } finally {
            mainHandler.removeCallbacks(watchdog)
        }
    }

    private fun evaluateInFreshRuntime(script: String, data: String? = null): Bundle {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return failure(QuickJsSandboxProtocol.ERROR_UNSUPPORTED_API)
        }
        if (script.length > QuickJsSandboxProtocol.MAX_INPUT_CHARS) {
            return failure(QuickJsSandboxProtocol.ERROR_INPUT_TOO_LARGE)
        }

        var quickJs: QuickJs? = null
        return try {
            val runtime = QuickJs.create(Dispatchers.Default).apply {
                memoryLimit = QuickJsSandboxProtocol.MEMORY_LIMIT_BYTES
                maxStackSize = QuickJsSandboxProtocol.MAX_STACK_SIZE_BYTES
            }
            quickJs = runtime
            val value = runBlocking {
                if (data != null) {
                    // A primitive string callback is the only binding. No reflection,
                    // paths, host objects, or source-specific behavior enter the sandbox.
                    // JNI's modified UTF-8 string mapping does not preserve literal NUL.
                    // JSON transports control characters losslessly, without evaluating data.
                    val needsEscaping = '\u0000' in data
                    val encodedData = if (needsEscaping) JSONObject.quote(data) else data
                    runtime.defineBinding("__sandboxReadData", object : FunctionBinding<String> {
                        override fun invoke(args: Array<Any?>): String = encodedData
                    })
                    runtime.evaluate<String>(
                        "Object.defineProperty(globalThis,'sandboxData'," +
                            "{value:" + (if (needsEscaping) "JSON.parse(__sandboxReadData())" else "__sandboxReadData()") +
                            ",writable:false,configurable:false});" +
                            "delete globalThis.__sandboxReadData; 'ready'",
                        filename = "sandbox-input.js",
                    )
                    // The library's String result also stops at literal NUL. Keep the
                    // new data API lossless in both directions; retain legacy eval behavior.
                    // Check type/length inside JS before encoding or allocating on the host.
                    val encoded = runtime.evaluate<String>(
                        "(function(){var encode=JSON.stringify;var value=(0,eval)(" + JSONObject.quote(script) + ");" +
                            "if(typeof value!=='string')throw Error('String result required');" +
                            "return value.length>" + QuickJsSandboxProtocol.MAX_OUTPUT_CHARS +
                            "?'!':'='+encode(value);})()",
                        filename = "sandbox.js",
                    )
                    if (encoded == "!") return@runBlocking null
                    check(encoded.startsWith('='))
                    JSONTokener(encoded.substring(1)).nextValue() as String
                } else {
                    runtime.evaluate<String>(script, filename = "sandbox.js")
                }
            }
            if (value == null || value.length > QuickJsSandboxProtocol.MAX_OUTPUT_CHARS) {
                failure(QuickJsSandboxProtocol.ERROR_OUTPUT_TOO_LARGE)
            } else {
                success(value)
            }
        } catch (_: Exception) {
            failure(QuickJsSandboxProtocol.ERROR_EVALUATION_FAILED)
        } finally {
            runCatching { quickJs?.close() }
        }
    }

    private fun success(value: String) = Bundle().apply {
        putBoolean(QuickJsSandboxProtocol.KEY_SUCCESS, true)
        putString(QuickJsSandboxProtocol.KEY_VALUE, value)
    }

    private fun failure(error: String) = Bundle().apply {
        putBoolean(QuickJsSandboxProtocol.KEY_SUCCESS, false)
        putString(QuickJsSandboxProtocol.KEY_ERROR, error)
    }

    private data class ScriptReadResult(
        val script: String? = null,
        val error: String? = null,
    )
}
