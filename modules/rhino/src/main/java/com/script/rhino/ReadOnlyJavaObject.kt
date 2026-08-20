package com.script.rhino

import org.mozilla.javascript.LambdaFunction
import org.mozilla.javascript.NativeJavaObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined

class ReadOnlyJavaObject(
    scope: Scriptable?,
    javaObject: Any,
    staticType: Class<*>?,
    private val blockedMethods: Set<String> = emptySet(),
    private val onBlockedMethod: ((String) -> Unit)? = null
) :
    NativeJavaObject(scope, javaObject, staticType) {

    private fun isBlockedMethod(name: String): Boolean {
        return blockedMethods.any { method ->
            name == method || name.startsWith("$method(")
        }
    }

    override fun has(name: String, start: Scriptable): Boolean {
        if (isBlockedMethod(name)) {
            return true
        }
        if (name.length > 3 && name.startsWith("set")) {
            val name = name.substring(3).replaceFirstChar { it.lowercase() }
            if (super.has(name, start)) {
                return false
            }
        }
        return super.has(name, start)
    }

    override fun get(name: String, start: Scriptable): Any? {
        if (isBlockedMethod(name)) {
            val functionName = name.substringBefore('(')
            return LambdaFunction(parentScope ?: start, functionName, 0) { _, _, _, _ ->
                onBlockedMethod?.invoke(name)
                Undefined.instance
            }
        }
        if (name.length > 3 && name.startsWith("set")) {
            val name = name.substring(3).replaceFirstChar { it.lowercase() }
            if (super.has(name, start)) {
                return NOT_FOUND
            }
        }
        return super.get(name, start)
    }

    override fun put(
        name: String?,
        start: Scriptable?,
        value: Any?
    ) {
        // do nothing
    }

    companion object {
        val factory = JavaObjectWrapFactory { scope, javaObject, staticType ->
            ReadOnlyJavaObject(scope, javaObject, staticType)
        }

        fun factory(
            blockedMethods: Set<String>,
            onBlockedMethod: ((String) -> Unit)? = null
        ) =
            JavaObjectWrapFactory { scope, javaObject, staticType ->
                ReadOnlyJavaObject(
                    scope,
                    javaObject,
                    staticType,
                    blockedMethods,
                    onBlockedMethod
                )
            }
    }

}
