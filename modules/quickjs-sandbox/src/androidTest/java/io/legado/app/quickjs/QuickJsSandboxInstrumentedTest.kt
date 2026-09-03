package io.legado.app.quickjs

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 23)
class QuickJsSandboxInstrumentedTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun evaluatesFixedScriptInARestrictedGlobalScope() = runBlocking {
        val script = "['fixture', 'key'].join('-')"
        val bridge = QuickJsSandboxBridge(context)

        val result = withContext(Dispatchers.IO) { bridge.evalString(script) }
        val globals = withContext(Dispatchers.IO) {
            bridge.evalString(
                """
                    JSON.stringify({
                        java: typeof java,
                        source: typeof source,
                        sourceApi: typeof sourceApi,
                        baseUrl: typeof baseUrl,
                        cookie: typeof cookie,
                        cache: typeof cache,
                        Packages: typeof Packages,
                        appCtx: typeof appCtx,
                        android: typeof android,
                        app: typeof app,
                        fetch: typeof fetch,
                        require: typeof require,
                        process: typeof process,
                        std: typeof std,
                        os: typeof os,
                        load: typeof load,
                        read: typeof read
                    })
                """.trimIndent()
            )
        }

        assertEquals("fixture-key", result)
        val globalTypes = JSONObject(globals)
        listOf(
            "java", "source", "sourceApi", "baseUrl", "cookie", "cache", "Packages",
            "appCtx", "android", "app", "fetch", "require", "process", "std", "os",
            "load", "read",
        ).forEach { name ->
            assertEquals(name, "undefined", globalTypes.getString(name))
        }
    }

    @Test
    fun exactInputAndOutputLimitsRemainUsable() = runBlocking {
        val bridge = QuickJsSandboxBridge(context)
        val prefix = "'max-input';"
        val maxInputScript = prefix + " ".repeat(
            QuickJsSandboxProtocol.MAX_INPUT_CHARS - prefix.length
        )

        val inputResult = withContext(Dispatchers.IO) {
            bridge.evalString(maxInputScript)
        }
        val outputResult = withContext(Dispatchers.IO) {
            bridge.evalString("'x'.repeat(${QuickJsSandboxProtocol.MAX_OUTPUT_CHARS})")
        }

        assertEquals("max-input", inputResult)
        assertEquals(QuickJsSandboxProtocol.MAX_OUTPUT_CHARS, outputResult.length)
    }

    @Test
    fun rejectsOversizedInputBeforeBinding() = runBlocking {
        val bridge = QuickJsSandboxBridge(context)

        val error = sandboxFailure {
            withContext(Dispatchers.IO) {
                bridge.evalString(" ".repeat(QuickJsSandboxProtocol.MAX_INPUT_CHARS + 1))
            }
        }

        assertTrue(error.message.orEmpty().contains(QuickJsSandboxProtocol.ERROR_INPUT_TOO_LARGE))
    }

    @Test
    fun rejectsOversizedOutput() = runBlocking {
        val bridge = QuickJsSandboxBridge(context)

        val error = sandboxFailure {
            withContext(Dispatchers.IO) {
                bridge.evalString("'x'.repeat(${QuickJsSandboxProtocol.MAX_OUTPUT_CHARS + 1})")
            }
        }

        assertTrue(error.message.orEmpty().contains(QuickJsSandboxProtocol.ERROR_OUTPUT_TOO_LARGE))
    }

    @Test
    fun infiniteLoopKillsOnlySandboxAndNextCallRecovers() = runBlocking {
        val bridge = QuickJsSandboxBridge(context)

        val error = sandboxFailure {
            withContext(Dispatchers.IO) { bridge.evalString("while (true) {}") }
        }
        assertTrue(
            error.message.orEmpty().contains(QuickJsSandboxProtocol.ERROR_EVALUATION_TIMEOUT)
        )

        val recovered = withContext(Dispatchers.IO) {
            bridge.evalString("'recovered'")
        }
        assertEquals("recovered", recovered)
    }

    @Test
    fun stalledInputPipeTimesOutAndNextCallRecovers() = runBlocking {
        val bound = bindSandbox()
        val pipe = ParcelFileDescriptor.createPipe()
        val output = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        output.write("partial".toByteArray(Charsets.UTF_8))
        output.flush()
        val startedAt = SystemClock.elapsedRealtime()

        val failure = try {
            runCatching {
                withContext(Dispatchers.IO) {
                    bound.service.evalString(pipe[0], 100)
                }
            }
        } finally {
            runCatching { output.close() }
            runCatching { pipe[0].close() }
            runCatching { context.unbindService(bound.connection) }
        }
        val elapsed = SystemClock.elapsedRealtime() - startedAt

        assertTrue(failure.isFailure)
        assertTrue(
            "stalled pipe returned too early: ${elapsed}ms",
            elapsed >= QuickJsSandboxProtocol.EVALUATION_TIMEOUT_MILLIS -
                QuickJsSandboxProtocol.TIMEOUT_DETECTION_TOLERANCE_MILLIS,
        )
        val recovered = withContext(Dispatchers.IO) {
            QuickJsSandboxBridge(context).evalString("'recovered-after-stalled-pipe'")
        }
        assertEquals("recovered-after-stalled-pipe", recovered)
    }

    @Test
    fun memoryPressureIsContainedAndNextCallRecovers() = runBlocking {
        val bridge = QuickJsSandboxBridge(context)

        val error = sandboxFailure {
            withContext(Dispatchers.IO) {
                bridge.evalString(
                    "var chunks=[]; while(true){chunks.push('x'.repeat(1048576));}"
                )
            }
        }
        assertTrue(
            error.message.orEmpty().contains(QuickJsSandboxProtocol.ERROR_EVALUATION_FAILED)
        )

        val recovered = withContext(Dispatchers.IO) {
            bridge.evalString("'recovered-after-memory-pressure'")
        }
        assertEquals("recovered-after-memory-pressure", recovered)
    }

    @Test
    fun recursiveStackOverflowIsContained() = runBlocking {
        val bridge = QuickJsSandboxBridge(context)

        val error = sandboxFailure {
            withContext(Dispatchers.IO) {
                bridge.evalString("function recurse(){return recurse();} recurse();")
            }
        }
        assertTrue(
            error.message.orEmpty().contains(QuickJsSandboxProtocol.ERROR_EVALUATION_FAILED)
        )

        val recovered = withContext(Dispatchers.IO) {
            bridge.evalString("'recovered-after-stack-overflow'")
        }
        assertEquals("recovered-after-stack-overflow", recovered)
    }

    private suspend fun sandboxFailure(block: suspend () -> Unit): IllegalStateException {
        return try {
            block()
            throw AssertionError("expected QuickJS sandbox failure")
        } catch (error: IllegalStateException) {
            error
        }
    }

    private suspend fun bindSandbox(): BoundSandbox = withContext(Dispatchers.IO) {
        val serviceRef = AtomicReference<IQuickJsSandbox?>()
        val latch = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                serviceRef.set(IQuickJsSandbox.Stub.asInterface(service))
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit

            override fun onNullBinding(name: ComponentName?) {
                latch.countDown()
            }

            override fun onBindingDied(name: ComponentName?) {
                latch.countDown()
            }
        }
        check(
            context.bindService(
                Intent(context, QuickJsSandboxService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
        ) { "failed to bind QuickJS sandbox" }
        check(
            latch.await(
                QuickJsSandboxProtocol.BIND_TIMEOUT_MILLIS,
                TimeUnit.MILLISECONDS,
            )
        ) { "timed out binding QuickJS sandbox" }
        BoundSandbox(
            connection = connection,
            service = checkNotNull(serviceRef.get()) { "QuickJS sandbox binder missing" },
        )
    }

    private data class BoundSandbox(
        val connection: ServiceConnection,
        val service: IQuickJsSandbox,
    )
}
