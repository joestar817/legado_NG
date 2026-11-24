package io.legado.app.help.pet

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PetPackageInspectorTest {
    @Test fun supportedManifestAndCompiledPackageAreRequired() {
        for (api in listOf(2, 3, 4)) {
            val file = archive("classes.dex" to PetDexFixtures.minimal(), "assets/plugin.json" to manifest(api).toByteArray())
            try {
                val manifest = PetPackageInspector.inspect(file)
                assertEquals("test_pet", manifest.id)
                assertEquals(api, manifest.apiVersion)
            } finally { file.delete() }
        }
    }

    @Test fun missingCodeAndUnsupportedApiAreRejected() {
        for (entries in listOf(
            arrayOf("assets/plugin.json" to manifest().toByteArray()),
            arrayOf("classes.dex" to PetDexFixtures.minimal(), "assets/plugin.json" to manifest(5).toByteArray()),
        )) {
            val file = archive(*entries)
            try { assertTrue(runCatching { PetPackageInspector.inspect(file) }.isFailure) } finally { file.delete() }
        }
    }

    @Test fun invalidDexHeaderIsRejected() {
        val file = archive("classes.dex" to "not-dex".toByteArray(), "assets/plugin.json" to manifest().toByteArray())
        try { assertTrue(runCatching { PetPackageInspector.inspect(file) }.isFailure) } finally { file.delete() }
    }

    @Test fun traversalAndNativeLibrariesAreRejected() {
        assertFalse(PetPackageInspector.safePath("/absolute"))
        assertFalse(PetPackageInspector.safePath("ai_pets/../escape"))
        assertFalse(PetPackageInspector.safePath("ai_pets\\escape"))
        val file = archive("classes.dex" to PetDexFixtures.minimal(), "assets/plugin.json" to manifest().toByteArray(), "lib/arm64-v8a/evil.so" to byteArrayOf(1))
        try { assertTrue(runCatching { PetPackageInspector.inspect(file) }.isFailure) } finally { file.delete() }
    }

    private fun manifest(api: Int = 2) = """{"schemaVersion":2,"kind":"pet","apiVersion":$api,"id":"test_pet","name":"测试宠物","versionCode":1,"entryClass":"example.custom.PetEntry"}"""
    private fun archive(vararg entries: Pair<String, ByteArray>): File = File.createTempFile("pet-test-", ".zip").also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, bytes) in entries) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
        }
    }
}
