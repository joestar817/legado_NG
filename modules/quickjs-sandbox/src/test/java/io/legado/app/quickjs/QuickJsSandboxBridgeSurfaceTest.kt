package io.legado.app.quickjs

import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Modifier

class QuickJsSandboxBridgeSurfaceTest {

    @Test
    fun rhinoBridgeKeepsASingleStringOnlyMethod() {
        val methods = QuickJsSandboxBridge::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
            .map { method ->
                val parameters = method.parameterTypes.joinToString(",") { it.name }
                "${method.name}($parameters):${method.returnType.name}"
            }
            .sorted()

        assertEquals(
            listOf("evalString(java.lang.String):java.lang.String"),
            methods,
        )
    }

    @Test
    fun rhinoBridgeDoesNotExposePublicState() {
        val publicFields = QuickJsSandboxBridge::class.java.declaredFields
            .filter { Modifier.isPublic(it.modifiers) }
            .map { it.name }

        assertEquals(emptyList<String>(), publicFields)
    }

    @Test
    fun inheritedPublicSurfaceIsLimitedToEvalStringAndObjectMethods() {
        val methods = QuickJsSandboxBridge::class.java.methods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
            .map { method ->
                val parameters = method.parameterTypes.joinToString(",") { it.name }
                "${method.name}($parameters):${method.returnType.name}"
            }
            .sorted()

        assertEquals(
            listOf(
                "equals(java.lang.Object):boolean",
                "evalString(java.lang.String):java.lang.String",
                "getClass():java.lang.Class",
                "hashCode():int",
                "notify():void",
                "notifyAll():void",
                "toString():java.lang.String",
                "wait():void",
                "wait(long):void",
                "wait(long,int):void",
            ),
            methods,
        )
    }
}
