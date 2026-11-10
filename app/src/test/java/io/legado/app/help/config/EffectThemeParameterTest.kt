package io.legado.app.help.config

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class EffectThemeParameterTest {
    @Test fun rangeRejectsNonFiniteAndOutOfBoundsValues() {
        val parameter = NgEffectThemeParameter("intensity", "强度", "range", "40", 0f, 100f, 1f)
        assertTrue(parameter.accepts("0"))
        assertTrue(parameter.accepts("100"))
        listOf("NaN", "Infinity", "-1", "101", "bad").forEach { assertFalse(parameter.accepts(it)) }
    }

    @Test fun choiceAndBooleanValuesRemainDeclared() {
        val choice = NgEffectThemeParameter("style", "样式", "choice", "a",
            options = listOf(NgEffectThemeOption("a", "甲"), NgEffectThemeOption("b", "乙")))
        assertTrue(choice.accepts("b"))
        assertFalse(choice.accepts("future"))
        val boolean = NgEffectThemeParameter("enabled", "启用", "boolean", "true")
        assertTrue(boolean.accepts("false"))
        assertFalse(boolean.accepts("1"))
    }

    @Test fun colorsAcceptOnlyPortableHexValues() {
        val color = NgEffectThemeParameter("color", "颜色", "color", "#FF008877")
        assertTrue(color.accepts("#008877"))
        assertTrue(color.accepts("#FF008877"))
        assertFalse(color.accepts("green"))
        assertFalse(color.accepts("#888"))
    }

    @Test fun genericViewThemesMayDeclareNoGlRequirement() {
        NgEffectThemeDescriptor(entryClass = "example.Theme", targets = listOf("app"),
            minGlesVersion = 0).validate()
        assertThrows(IllegalArgumentException::class.java) {
            NgEffectThemeDescriptor(entryClass = "example.Theme", targets = listOf("unknown")).validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            NgEffectThemeDescriptor(entryClass = "example.Theme", targets = listOf("app"),
                parameterSchema = listOf(NgEffectThemeParameter("x", "X", "range", "8", 0f, 1f))).validate()
        }
    }

    @Test fun descriptorRoundTripKeepsStableWireNames() {
        val descriptor = NgEffectThemeDescriptor(entryClass = "example.Theme", targets = listOf("player"),
            playerPalette = "theme", preview = "assets/preview.png")
        val gson = Gson()
        val json = gson.toJson(descriptor)
        assertTrue(json.contains("\"entryClass\""))
        assertTrue(json.contains("\"playerPalette\""))
        assertTrue(json.contains("\"parameterSchema\""))
        assertEquals(descriptor, gson.fromJson(json, NgEffectThemeDescriptor::class.java))
    }
}
