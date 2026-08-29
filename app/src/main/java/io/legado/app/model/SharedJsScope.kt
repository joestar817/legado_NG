package io.legado.app.model

import androidx.collection.LruCache
import com.google.gson.reflect.TypeToken
import com.script.CompiledScript
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
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import splitties.init.appCtx
import java.io.File
import java.lang.ref.WeakReference
import java.security.SecureRandom
import kotlin.coroutines.CoroutineContext

object SharedJsScope {

    private const val CRYPTO_JS_ASSET = "scripts/cryptojs.min.js"
    private const val SECURE_RANDOM_BINDING = "__legadoSecureRandomInt"

    private val aCache by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ACache.get(File(appCtx.cacheDir, "shareJs"))
    }

    private val scopeMap = LruCache<String, WeakReference<Scriptable>>(16)
    private val cryptoScopeMap = LruCache<String, WeakReference<Scriptable>>(16)
    private val cryptoScopeLock = Any()
    @Volatile
    private var cryptoJsScript: CompiledScript? = null
    private val cryptoCompileLock = Any()
    private val secureRandom by lazy { SecureRandom() }

    internal fun installCryptoJs(
        scope: Scriptable,
        coroutineContext: CoroutineContext?,
    ): Boolean {
        val cryptoJs = compiledCryptoJs() ?: return false
        cryptoJs.eval(scope, coroutineContext)
        val secureRandomFunction = object : BaseFunction() {
            override fun call(
                cx: Context,
                callScope: Scriptable,
                thisObj: Scriptable,
                args: Array<Any>,
            ): Any = secureRandom.nextInt().toDouble()
        }.apply {
            parentScope = scope
            prototype = ScriptableObject.getFunctionPrototype(scope)
        }
        ScriptableObject.putProperty(scope, SECURE_RANDOM_BINDING, secureRandomFunction)
        try {
            RhinoScriptEngine.eval(SECURE_RANDOM_PATCH, scope, coroutineContext)
        } finally {
            scope.delete(SECURE_RANDOM_BINDING)
        }
        return true
    }

    fun getCryptoScope(
        scopeNamespace: String,
        coroutineContext: CoroutineContext?,
        bookSourceClassPolicy: Boolean = false,
        bookSourceLabel: String? = null,
    ): Scriptable? {
        return RhinoClassShutter.withBookSourceClassPolicy(
            enabled = bookSourceClassPolicy,
            sourceLabel = bookSourceLabel,
        ) {
            val key = "${if (bookSourceClassPolicy) "bookSource" else "default"}:" +
                "$scopeNamespace:${Thread.currentThread().id}"
            synchronized(cryptoScopeLock) {
                cryptoScopeMap[key]?.get()?.let { return@withBookSourceClassPolicy it }
                val scope = RhinoScriptEngine.getRuntimeScope(ScriptBindings())
                if (!installCryptoJs(scope, coroutineContext)) {
                    return@withBookSourceClassPolicy null
                }
                if (scope is ScriptableObject) scope.preventExtensions()
                cryptoScopeMap.put(key, WeakReference(scope))
                scope
            }
        }
    }

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
                installCryptoJs(scope, coroutineContext)
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

    private fun compiledCryptoJs(): CompiledScript? {
        cryptoJsScript?.let { return it }
        synchronized(cryptoCompileLock) {
            cryptoJsScript?.let { return it }
            val script = runCatching {
                appCtx.assets.open(CRYPTO_JS_ASSET).bufferedReader().use { it.readText() }
            }.getOrNull() ?: return null
            return RhinoScriptEngine.compile(script).also { cryptoJsScript = it }
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

    private const val SECURE_RANDOM_PATCH = """
        CryptoJS.lib.WordArray.random = (function(nextInt) {
            return function(nBytes) {
                var words = [];
                for (var i = 0; i < nBytes; i += 4) {
                    words.push(nextInt());
                }
                return CryptoJS.lib.WordArray.create(words, nBytes);
            };
        })(__legadoSecureRandomInt);
    """

}
