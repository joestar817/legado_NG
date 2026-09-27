package io.legado.app.quickjs

import android.content.Context
import android.os.Build
import android.os.Looper
import androidx.javascriptengine.IsolateStartupParameters
import androidx.javascriptengine.JavaScriptSandbox
import org.json.JSONObject
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Bounded, string-only system-JS facade; each evaluation gets a fresh isolate. */
class V8SandboxBridge internal constructor(
    context: Context,
    private val execution: V8SandboxExecution?,
) {
    constructor(context: Context) : this(context, null)
    private val appContext = context.applicationContext ?: context

    fun evalString(script: String): String = evaluate(script, null)

    fun evalStringWithData(script: String, data: String): String = evaluate(script, data)

    private fun evaluate(script: String, data: String?): String = synchronized(evaluationLock) {
        if (Build.VERSION.SDK_INT < 26) fail("UNSUPPORTED_API")
        if (Looper.getMainLooper().thread === Thread.currentThread()) fail("MAIN_THREAD")
        if (script.length > QuickJsSandboxProtocol.MAX_INPUT_CHARS ||
            script.toByteArray(Charsets.UTF_8).size > QuickJsSandboxProtocol.MAX_INPUT_BYTES ||
            data != null && (data.length > QuickJsSandboxProtocol.MAX_DATA_CHARS ||
                data.toByteArray(Charsets.UTF_8).size > QuickJsSandboxProtocol.MAX_DATA_BYTES)
        ) fail("INPUT_TOO_LARGE")
        if (execution == null) {
            return@synchronized V8SandboxExecution(appContext).use { it.bridge.evaluate(script, data) }
        }
        execution.withSandbox { sandbox ->
            val required = listOf(
                JavaScriptSandbox.JS_FEATURE_ISOLATE_TERMINATION,
                JavaScriptSandbox.JS_FEATURE_PROMISE_RETURN,
                JavaScriptSandbox.JS_FEATURE_PROVIDE_CONSUME_ARRAY_BUFFER,
                JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE,
                JavaScriptSandbox.JS_FEATURE_EVALUATE_WITHOUT_TRANSACTION_LIMIT,
            )
            if (required.any { !sandbox.isFeatureSupported(it) }) fail("UNSUPPORTED_FEATURE")
            val parameters = IsolateStartupParameters().apply {
                maxHeapSizeBytes = QuickJsSandboxProtocol.MEMORY_LIMIT_BYTES
                maxEvaluationReturnSizeBytes = QuickJsSandboxProtocol.MAX_OUTPUT_CHARS * 4
            }
            sandbox.createIsolate(parameters).use { isolate ->
                try {
                    val body = "var value=(0,eval)(" + JSONObject.quote(script) + ");" +
                        "if(typeof value!=='string')throw Error('STRING_RESULT_REQUIRED');" +
                        "if(value.length>" + QuickJsSandboxProtocol.MAX_OUTPUT_CHARS +
                        ")throw Error('OUTPUT_TOO_LARGE');return value;"
                    val code = if (data == null) "(function(){delete globalThis.android;$body})()" else {
                        // Named data never becomes executable source. UTF-16 also avoids a
                        // multi-megabyte JS UTF-8 decoding pass and preserves NUL/astral text.
                        isolate.provideNamedData("input", data.toByteArray(Charsets.UTF_16LE))
                        "(function(){return android.consumeNamedDataAsArrayBuffer('input').then(function(buffer){" +
                            "var words=new Uint16Array(buffer),parts=[];" +
                            "if(new Uint8Array(new Uint16Array([1]).buffer)[0]!==1){" +
                            "var view=new DataView(buffer);for(var j=0;j<words.length;j++)words[j]=view.getUint16(j*2,true);}" +
                            "for(var i=0;i<words.length;i+=4096)parts.push(String.fromCharCode.apply(null,words.subarray(i,i+4096)));" +
                            "Object.defineProperty(globalThis,'sandboxData',{value:parts.join(''),writable:false,configurable:false});" +
                            "buffer=null;words=null;parts=null;delete globalThis.android;" + body + "});})()"
                    }
                    val result = isolate.evaluateJavaScriptAsync(code).get(
                        QuickJsSandboxProtocol.EVALUATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS,
                    )
                    if (result.length > QuickJsSandboxProtocol.MAX_OUTPUT_CHARS) fail("OUTPUT_TOO_LARGE")
                    result
                } catch (error: TimeoutException) {
                    throw IllegalStateException("V8 sandbox failed: EVALUATION_TIMEOUT", error)
                } catch (error: ExecutionException) {
                    val reason = if (error.cause?.message.orEmpty().contains("OUTPUT_TOO_LARGE")) {
                        "OUTPUT_TOO_LARGE"
                    } else "EVALUATION_FAILED"
                    throw IllegalStateException("V8 sandbox failed: $reason", error)
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IllegalStateException("V8 sandbox failed: INTERRUPTED", error)
                }
            }
        }
    }

    private fun fail(code: String): Nothing = throw IllegalStateException("V8 sandbox failed: $code")

    private companion object {
        // Serialise evaluations, including scripts from different sources.
        val evaluationLock = Any()
    }
}
