package io.legado.app.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class JsLibraryBundleTest {
    private fun bundle(data: String = "';throw Error('data executed');\u0000汉😀"): String =
        JsonObject().apply {
            addProperty("format", "legado.js.library/1")
            add("scripts", JsonArray().apply { add("var value=7;") })
            add("resources", JsonObject().apply { addProperty("data", data) })
        }.toString()

    @Test fun legacyLibrariesAreNotBundles() {
        assertNull(JsLibraryBundle.parse("var value=7;"))
        assertNull(JsLibraryBundle.parse("{var value=7;}"))
        assertNull(JsLibraryBundle.parse("{\"format\":\"https://example.com/lib.js\"}"))
        assertNull(JsLibraryBundle.parse(null))
    }

    @Test fun resourceDataIsExactAndSeparateFromScripts() {
        val data = "';throw Error('data executed');\u0000汉😀"
        val parsed = JsLibraryBundle.parse(bundle(data))!!
        assertEquals(listOf("var value=7;"), parsed.scripts)
        assertEquals(data, parsed.resources["data"])
        assertNull(parsed.resources["missing"])
    }

    @Test fun malformedBundlesAndByteOverflowAreRejected() {
        for (value in listOf(
            bundle().replace("library/1", "library/2"),
            bundle().replace("[\"var value=7;\"]", "[7]"),
            bundle().replace("\"resources\"", "\"unknown\""),
            bundle().replace("\"scripts\":", "\"builtins\":[\"unknown\"],\"scripts\":"),
            bundle("汉".repeat(700_000)),
        )) {
            assertThrows(IllegalArgumentException::class.java) { JsLibraryBundle.parse(value) }
        }
    }

    @Test fun builtinsRequireExplicitOptOut() {
        assertTrue(JsLibraryBundle.parse(bundle())!!.usesCryptoJs)
        assertFalse(JsLibraryBundle.parse(bundle().replace("\"scripts\":", "\"builtins\":[],\"scripts\":"))!!.usesCryptoJs)
    }
}
