package io.legado.app.model

import androidx.collection.LruCache
import com.google.gson.reflect.TypeToken
import com.script.ScriptBindings
import com.script.rhino.RhinoClassShutter
import com.script.rhino.RhinoScriptEngine
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.ACache
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonObject
import kotlinx.coroutines.runBlocking
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import splitties.init.appCtx
import java.io.File
import java.lang.ref.WeakReference
import kotlin.coroutines.CoroutineContext

object SharedJsScope {

    private val aCache by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ACache.get(File(appCtx.cacheDir, "shareJs"))
    }

    private val scopeMap = LruCache<String, WeakReference<Scriptable>>(16)

    fun getScope(
        jsLib: String?,
        coroutineContext: CoroutineContext?,
        bookSourceClassPolicy: Boolean = false,
        bookSourceLabel: String? = null,
        scopeNamespace: String? = null
    ): Scriptable? {
        if (jsLib.isNullOrBlank()) {
            return null
        }
        return RhinoClassShutter.withBookSourceClassPolicy(
            enabled = bookSourceClassPolicy,
            sourceLabel = bookSourceLabel
        ) {
            val key = scopeKey(jsLib, bookSourceClassPolicy, scopeNamespace)
            var scope = scopeMap[key]?.get()
            if (scope == null) {
                scope = RhinoScriptEngine.run {
                    getRuntimeScope(ScriptBindings())
                }
                if (jsLib.isJsonObject()) {
                    val jsMap: Map<String, String> = GSON.fromJson(
                        jsLib,
                        TypeToken.getParameterized(
                            Map::class.java,
                            String::class.java,
                            String::class.java
                        ).type
                    )
                    jsMap.values.forEach { value ->
                        if (value.isAbsUrl()) {
                            val fileName = MD5Utils.md5Encode(value)
                            var js = aCache.getAsString(fileName)
                            if (js == null) {
                                js = runBlocking {
                                    okHttpClient.newCallStrResponse {
                                        url(value)
                                    }.body
                                }
                                if (js != null) {
                                    aCache.put(fileName, js)
                                } else {
                                    throw NoStackTraceException("下载jsLib-${value}失败")
                                }
                            }
                            RhinoScriptEngine.eval(js, scope, coroutineContext)
                        }
                    }
                } else {
                    RhinoScriptEngine.eval(jsLib, scope, coroutineContext)
                }
                if (scope is ScriptableObject) {
                    /**
                     * 阻止新全局增加（即函数内未用var的隐性全局变量创建）,会直接隐性创建失败,提示变量未定义
                     */
                    scope.preventExtensions()
                }
                scopeMap.put(key, WeakReference(scope))
            }
            scope
        }
    }

    fun remove(jsLib: String?) {
        if (jsLib.isNullOrBlank()) {
            return
        }
        if (jsLib.isJsonObject()) {
            val jsMap: Map<String, String> = GSON.fromJson(
                jsLib,
                TypeToken.getParameterized(
                    Map::class.java,
                    String::class.java,
                    String::class.java
                ).type
            )
            jsMap.values.forEach { value ->
                if (value.isAbsUrl()) {
                    val fileName = MD5Utils.md5Encode(value)
                    aCache.remove(fileName)
                }
            }
        }
        val jsLibHash = MD5Utils.md5Encode(jsLib)
        scopeMap.snapshot().keys.forEach { key ->
            if (key.endsWith(":$jsLibHash")) {
                scopeMap.remove(key)
            }
        }
    }

    private fun scopeKey(
        jsLib: String,
        bookSourceClassPolicy: Boolean,
        scopeNamespace: String?
    ): String {
        val policyPrefix = if (bookSourceClassPolicy) {
            require(!scopeNamespace.isNullOrBlank()) {
                "BookSource shared JS scope requires a source namespace"
            }
            "bookSource:$scopeNamespace"
        } else {
            "default"
        }
        return "$policyPrefix:${MD5Utils.md5Encode(jsLib)}"
    }

}
