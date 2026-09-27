package io.legado.app.quickjs

import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Modifier

class V8SandboxBridgeSurfaceTest {
    @Test
    fun facadeHasTheSameStringOnlySurfaceAsQuickJs() {
        fun surface(type: Class<*>) = type.methods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
            .map { method ->
                "${method.name}(${method.parameterTypes.joinToString(",") { it.name }}):${method.returnType.name}"
            }.sorted()
        assertEquals(surface(QuickJsSandboxBridge::class.java), surface(V8SandboxBridge::class.java))
        assertEquals(emptyList<String>(), V8SandboxBridge::class.java.declaredFields
            .filter { Modifier.isPublic(it.modifiers) }.map { it.name })
    }
}
