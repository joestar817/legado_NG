package io.legado.app.quickjs

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
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

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 23)
class QuickJsSandboxExecutionTest {
    private class CountingContext : ContextWrapper(ApplicationProvider.getApplicationContext()) {
        var binds = 0
        var unbinds = 0
        override fun getApplicationContext(): Context = this
        override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int): Boolean {
            binds++
            return super.bindService(intent, connection, flags)
        }
        override fun unbindService(connection: ServiceConnection) {
            unbinds++
            super.unbindService(connection)
        }
    }

    @Test
    fun bindingIsLazyAndReusedButJavascriptGlobalsStayIsolated() = runBlocking {
        val context = CountingContext()
        withContext(Dispatchers.IO) {
            QuickJsSandboxExecution(context).use { execution ->
                assertEquals(0, context.binds)
                assertEquals("set", execution.bridge.evalString("globalThis.privateValue=42; 'set'"))
                assertEquals("undefined", execution.bridge.evalString("typeof privateValue"))
                assertEquals(1, context.binds)
                assertEquals(0, context.unbinds)
            }
        }
        assertEquals(1, context.unbinds)
    }

    @Test
    fun preparationStartsOnlyAConnectionAndClosingUnusedPreparationReleasesIt() = runBlocking {
        val context = CountingContext()
        withContext(Dispatchers.IO) {
            QuickJsSandboxExecution(context).use { execution ->
                execution.prepare()
                execution.prepare()
                assertEquals(1, context.binds)
                assertEquals(0, context.unbinds)
            }
            QuickJsSandboxExecution(context).use { execution ->
                execution.prepare()
                assertEquals("undefined", execution.bridge.evalString("typeof sandboxData"))
                assertEquals(2, context.binds)
            }
        }
        assertEquals(2, context.unbinds)
    }

    @Test
    fun failuresDropBindingAndNextCallCanRecover() = runBlocking {
        val context = CountingContext()
        withContext(Dispatchers.IO) {
            QuickJsSandboxExecution(context).use { execution ->
                assertTrue(runCatching { execution.bridge.evalString("throw Error('fixture')") }.isFailure)
                assertEquals(1, context.unbinds)
                assertEquals("ok", execution.bridge.evalString("'ok'"))
                assertEquals(2, context.binds)
            }
        }
        assertEquals(2, context.unbinds)
    }

    @Test
    fun anotherExecutionTimeoutDoesNotPoisonAnIdleBinding() = runBlocking {
        val context = CountingContext()
        withContext(Dispatchers.IO) {
            QuickJsSandboxExecution(context).use { idle ->
                assertEquals("ready", idle.bridge.evalString("'ready'"))
                QuickJsSandboxExecution(context).use { failing ->
                    assertTrue(runCatching { failing.bridge.evalString("while (true) {}") }.isFailure)
                }
                assertEquals("rebound", idle.bridge.evalString("'rebound'"))
            }
        }
        assertEquals(context.binds, context.unbinds)
    }

    @Test
    fun hostFailureAndRepeatedCloseCannotLeakOrReopenExecution() = runBlocking {
        val context = CountingContext()
        val execution = QuickJsSandboxExecution(context)
        withContext(Dispatchers.IO) {
            assertTrue(runCatching {
                execution.use { it.bridge.evalString("'ok'"); error("host failure") }
            }.isFailure)
            execution.close()
            assertTrue(runCatching { execution.bridge.evalString("'unexpected'") }.isFailure)
        }
        assertEquals(1, context.binds)
        assertEquals(1, context.unbinds)
    }

    @Test
    fun separateDataIsInertReadOnlyAndScopedToOneEvaluation() = runBlocking {
        val context = CountingContext()
        withContext(Dispatchers.IO) {
            QuickJsSandboxExecution(context).use { execution ->
                val data = "';globalThis.injected=true;//\u0000汉字\uD83D\uDE00"
                assertEquals(data, execution.bridge.evalStringWithData("sandboxData", data))
                assertEquals(
                    "undefined|undefined|undefined|false|false",
                    execution.bridge.evalStringWithData(
                        "[typeof injected,typeof java,typeof __sandboxReadData," +
                            "Object.getOwnPropertyDescriptor(globalThis,'sandboxData').writable," +
                            "Object.getOwnPropertyDescriptor(globalThis,'sandboxData').configurable].join('|')",
                        data,
                    ),
                )
                assertEquals("undefined", execution.bridge.evalString("typeof sandboxData"))
                assertEquals("", execution.bridge.evalStringWithData("sandboxData", ""))
                assertEquals("safe", execution.bridge.evalStringWithData(
                    "JSON.stringify=function(){throw Error('replaced')};sandboxData", "safe",
                ))
                assertEquals(1, context.binds)
            }
        }
        assertEquals(1, context.unbinds)
    }

    @Test
    fun dataQuotasDoNotChangeScriptOrOutputQuotas() = runBlocking {
        val context = CountingContext()
        withContext(Dispatchers.IO) {
            QuickJsSandboxExecution(context).use { execution ->
                val bridge = execution.bridge
                assertEquals("3000000", bridge.evalStringWithData("String(sandboxData.length)", "a".repeat(3_000_000)))
                for (data in listOf("a".repeat(3_000_001), "汉".repeat(3_000_000))) {
                    assertTrue(runCatching { bridge.evalStringWithData("'bad'", data) }
                        .exceptionOrNull()?.message.orEmpty().contains("INPUT_TOO_LARGE"))
                }
                assertTrue(runCatching { bridge.evalStringWithData(" ".repeat(384_001), "") }
                    .exceptionOrNull()?.message.orEmpty().contains("INPUT_TOO_LARGE"))
                assertTrue(runCatching { bridge.evalStringWithData("sandboxData", "a".repeat(65_537)) }
                    .exceptionOrNull()?.message.orEmpty().contains("OUTPUT_TOO_LARGE"))
                assertEquals("recovered", bridge.evalStringWithData("sandboxData", "recovered"))
            }
        }
        assertEquals(context.binds, context.unbinds)
    }
}
