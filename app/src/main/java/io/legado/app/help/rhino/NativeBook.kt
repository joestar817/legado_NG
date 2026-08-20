package io.legado.app.help.rhino

import com.script.rhino.JavaObjectWrapFactory
import io.legado.app.data.entities.Book
import org.mozilla.javascript.LambdaFunction
import org.mozilla.javascript.NativeJavaObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined

class NativeBook(scope: Scriptable?, javaObject: Any, staticType: Class<*>?) :
    NativeJavaObject(scope, javaObject, staticType) {

    private val sourceHint = (javaObject as? Book)?.let { book ->
        book.originName.takeIf { it.isNotBlank() }
            ?: book.origin.takeIf { it.isNotBlank() }
    }

    override fun has(name: String, start: Scriptable): Boolean {
        if (isBlockedMethod(name)) {
            return true
        }
        return super.has(name, start)
    }

    override fun get(name: String, start: Scriptable): Any? {
        if (isBlockedMethod(name)) {
            val functionName = name.substringBefore('(')
            return LambdaFunction(parentScope ?: start, functionName, 0) { _, _, _, _ ->
                BookSourceGuardLog.noOp("Book", name, sourceHint)
                Undefined.instance
            }
        }
        return super.get(name, start)
    }

    override fun put(name: String, start: Scriptable, value: Any?) {
        if (name in blockedProperties || isBlockedMethod(name)) {
            BookSourceGuardLog.ignoredWrite("Book", name, sourceHint)
            return
        }
        super.put(name, start, value)
    }

    companion object {
        private val blockedMethods = setOf(
            "setUseReplaceRule",
            "setReadConfig",
            "setGroup",
            "setOrder",
            "setCustomTag",
            "setCustomCoverUrl",
            "setCustomIntro",
            "upCustomIntro",
            "save",
            "delete"
        )

        private val blockedProperties = setOf(
            "useReplaceRule",
            "readConfig",
            "config",
            "group",
            "order",
            "customTag",
            "customCoverUrl",
            "customIntro"
        )

        private fun isBlockedMethod(name: String): Boolean {
            return blockedMethods.any { method ->
                name == method || name.startsWith("$method(")
            }
        }

        val factory = JavaObjectWrapFactory { scope, javaObject, staticType ->
            NativeBook(scope, javaObject, staticType)
        }
    }
}
