package io.legado.app.model.jsSource

import androidx.collection.LruCache
import com.script.CompiledScript
import com.script.ScriptBindings
import com.script.buildScriptBindings
import com.script.rhino.RhinoContext
import com.script.rhino.RhinoScriptEngine
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.JsExtensions
import io.legado.app.help.http.BookSourceCookieStore
import io.legado.app.help.source.getShareScope
import io.legado.app.help.source.scriptCacheObject
import io.legado.app.help.source.withBookSourceClassPolicy
import io.legado.app.model.SharedJsScope
import io.legado.app.quickjs.QuickJsSandboxBridge
import io.legado.app.utils.GSON
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.htmlunit.corejs.javascript.Context
import org.htmlunit.corejs.javascript.Function
import org.htmlunit.corejs.javascript.NativeJSON
import org.htmlunit.corejs.javascript.Scriptable
import org.htmlunit.corejs.javascript.ScriptableObject
import org.htmlunit.corejs.javascript.Undefined
import org.htmlunit.corejs.javascript.Wrapper
import splitties.init.appCtx
import kotlin.coroutines.CoroutineContext

/**
 * 纯 JavaScript 单文件书源执行器。
 *
 * 每次业务调用创建独立局部作用域，复用当前主线按书源隔离的 Cookie、缓存、共享 jsLib
 * 与 Rhino 类访问策略。仅此 java 宿主对象额外提供字符串输入输出的 QuickJS 窄门面；
 * 声明式书源继续走原有 AnalyzeRule 链，两种书源只在 WebBook 入口分流。
 */
class JsSourceEngine(
    private val source: BookSource,
    private val coroutineContext: CoroutineContext? = null,
) : JsExtensions {

    private val quickJsSandboxBridge by lazy { QuickJsSandboxBridge(appCtx) }

    override fun getSource(): BaseSource = source

    override fun getTag(): String = source.getTag()

    /** 仅单文件 JS 运行时可达；QuickJS 本体不暴露给 Rhino。 */
    fun getQuickJsSandbox(): QuickJsSandboxBridge = quickJsSandboxBridge

    fun callFunction(name: String, args: List<Pair<String, Any?>>): String? {
        return source.withBookSourceClassPolicy {
            val scope = buildScope(args)
            if (ScriptableObject.getProperty(scope, name) !is Function) {
                throw NoStackTraceException("JS源缺少函数 $name")
            }
            val expression = "$name(${args.joinToString(", ") { it.first }})"
            normalizeJsResult(compile(expression).eval(scope, coroutineContext), coroutineContext)
        }
    }

    fun callFunctionIfExists(name: String, args: List<Pair<String, Any?>>): String? {
        return callOptionalFunction(name, args).value
    }

    internal fun callOptionalFunction(
        name: String,
        args: List<Pair<String, Any?>>,
    ): OptionalCallResult {
        return source.withBookSourceClassPolicy {
            val scope = buildScope(args)
            if (ScriptableObject.getProperty(scope, name) !is Function) {
                return@withBookSourceClassPolicy OptionalCallResult(false, null)
            }
            val expression = "$name(${args.joinToString(", ") { it.first }})"
            OptionalCallResult(
                exists = true,
                value = normalizeJsResult(
                    compile(expression).eval(scope, coroutineContext),
                    coroutineContext,
                ),
            )
        }
    }

    internal data class OptionalCallResult(
        val exists: Boolean,
        val value: String?,
    )

    private fun buildScope(args: List<Pair<String, Any?>>): ScriptBindings {
        val script = source.mainJs?.takeIf { it.isNotBlank() }
            ?: throw NoStackTraceException("mainJs 为空，不是 JS 书源")
        val bindings = buildScriptBindings { values ->
            values["java"] = this
            values["source"] = source
            values["sourceApi"] = source
            values["baseUrl"] = source.getKey()
            values["cookie"] = BookSourceCookieStore.forSource(source)
            values["cache"] = source.scriptCacheObject()
            args.forEach { (key, value) -> values[key] = value }
        }
        val sharedScope = source.getShareScope(coroutineContext)
            ?: SharedJsScope.getCryptoScope(
                scopeNamespace = source.getKey(),
                coroutineContext = coroutineContext,
                bookSourceClassPolicy = true,
                bookSourceLabel = source.getTag(),
            )
        val scope = if (sharedScope == null) {
            RhinoScriptEngine.getRuntimeScope(bindings)
        } else {
            bindings.apply { chainTo(sharedScope) }
        }
        compile(script).eval(scope, coroutineContext)
        return scope
    }

    companion object {

        private val scriptCache = LruCache<String, CompiledScript>(64)

        private fun compile(script: String): CompiledScript {
            scriptCache[script]?.let { return it }
            return RhinoScriptEngine.compile(script).also { scriptCache.put(script, it) }
        }

        fun normalizeJsResult(
            result: Any?,
            coroutineContext: CoroutineContext? = null,
        ): String? {
            var value = result
            if (value is Wrapper) value = value.unwrap()
            return when {
                value == null || value is Undefined -> null
                value is String -> value
                value is CharSequence -> value.toString()
                value is Scriptable -> stringifyScriptable(value, coroutineContext)
                    ?: GSON.toJson(value)
                else -> GSON.toJson(value)
            }
        }

        private fun stringifyScriptable(
            value: Scriptable,
            coroutineContext: CoroutineContext?,
        ): String? {
            val topScope = value.parentScope?.let(ScriptableObject::getTopLevelScope)
                ?: return null
            val context = Context.enter() as RhinoContext
            val previousCoroutineContext = context.coroutineContext
            val previousAllowScriptRun = context.allowScriptRun
            if (coroutineContext != null && coroutineContext[Job] != null) {
                context.coroutineContext = coroutineContext
            }
            context.allowScriptRun = true
            context.recursiveCount++
            try {
                context.checkRecursive()
                val raw = try {
                    NativeJSON.stringify(context, topScope, value, null, null)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    throw NoStackTraceException(
                        "JS返回值 JSON.stringify 失败: ${error.message}"
                    )
                }
                return RhinoScriptEngine.unwrapReturnValue(raw) as? String
            } finally {
                context.recursiveCount--
                context.allowScriptRun = previousAllowScriptRun
                context.coroutineContext = previousCoroutineContext
                Context.exit()
            }
        }
    }
}
