package io.legado.app.quickjs

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
@SdkSuppress(minSdkVersion = 26)
class V8SandboxExecutionTest {
    @Test
    fun leasesShareOnlyConnectionAndIsolatesRemainFresh() = runBlocking {
        withContext(Dispatchers.IO) {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            V8SandboxExecution(context).use { first ->
                V8SandboxExecution(context).use { second ->
                    first.prepare()
                    second.prepare()
                    assertEquals("set", first.bridge.evalString("globalThis.privateValue=42;'set'"))
                    assertEquals("undefined", first.bridge.evalString("typeof privateValue"))
                    assertEquals("undefined", second.bridge.evalString("typeof privateValue"))
                    val text = "a\u0000汉字\uD83D\uDE00"
                    assertEquals(text, second.bridge.evalStringWithData("sandboxData", text))
                }
                assertEquals("ok", first.bridge.evalString("'ok'"))
                assertTrue(runCatching { first.bridge.evalString("while(true){}") }.isFailure)
                assertEquals("recovered", first.bridge.evalString("'recovered'"))
            }
        }
    }

    @Test
    fun unusedPreparationIsEventuallyReleasedAndClosedOwnerCannotReopen() = runBlocking {
        withContext(Dispatchers.IO) {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val execution = V8SandboxExecution(context)
            execution.prepare()
            execution.close()
            execution.close()
            assertTrue(runCatching { execution.bridge.evalString("'bad'") }.isFailure)
            // A caller arriving before asynchronous cleanup must not fail just because
            // the previous owner prepared the connection without evaluating anything.
            V8SandboxExecution(context).use { next ->
                assertEquals("fresh", next.bridge.evalString("'fresh'"))
            }
        }
    }
}
