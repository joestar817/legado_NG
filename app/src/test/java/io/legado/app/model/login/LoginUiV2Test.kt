package io.legado.app.model.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginUiV2Test {

    @Test
    fun parsesDynamicRowsAndActions() {
        val rows = LoginUiV2.parseRender(
            """{"rows":[
                {"key":"phone","name":"手机号","type":"text","hint":"11位"},
                {"name":"说明","type":"label"},
                {"key":"line","name":"线路","type":"select","options":["主","备"]},
                {"key":"remember","name":"记住","type":"toggle","value":"true"},
                {"name":"发码","type":"button","action":"send","countdown":60}
            ]}"""
        )
        assertEquals(5, rows!!.size)
        assertEquals("11位", rows[0].hint)
        assertEquals(listOf("主", "备"), rows[2].options)

        val command = LoginUiV2.parseActionResult(
            """{"state":{"step":"code"},"error":{"phone":"格式错误"},
                "login":{"token":"t"},"close":true,"unknown":1}"""
        )
        assertEquals("""{"step":"code"}""", command.stateJson)
        assertEquals("格式错误", command.error!!["phone"])
        assertEquals("""{"token":"t"}""", command.loginJson)
        assertTrue(command.close)
        assertEquals(listOf("unknown"), command.unknownKeys)
    }

    @Test
    fun rejectsAmbiguousRowsAndMalformedCommands() {
        assertTrue(LoginUiV2.isV2(LoginUiV2.MARKER))
        assertFalse(LoginUiV2.isV2("[]"))
        assertNull(
            LoginUiV2.parseRender(
                """{"rows":[{"key":"x","name":"甲","type":"text"},
                    {"key":"x","name":"乙","type":"toggle"}]}"""
            )
        )
        assertTrue(LoginUiV2.parseActionResult("not json").malformed)
        assertTrue(LoginUiV2.parseActionResult("""{"close":"false"}""").malformed)
    }
}
