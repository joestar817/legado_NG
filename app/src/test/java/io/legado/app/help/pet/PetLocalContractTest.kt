package io.legado.app.help.pet

import io.legado.app.utils.GSON
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PetLocalContractTest {
    private val manifest = PetPluginManifest(2, "pet", 2, "test_pet", "测试", 2, "example.Pet")
    private fun previous(version: Long = 2, hash: String = "a".repeat(64), status: PetLocalStatus = PetLocalStatus.READY) =
        PetLocalRecord("test_pet", manifest.copy(versionCode = version), null, hash, 10, null, null, status)

    @Test fun identityVersionAndHashDecideActions() {
        val hash = "b".repeat(64)
        assertEquals(PetImportAction.INSTALL, PetImportPolicy.action(null, manifest, hash))
        assertEquals(PetImportAction.NO_OP, PetImportPolicy.action(previous(hash = hash), manifest, hash))
        assertEquals(PetImportAction.UPDATE, PetImportPolicy.action(previous(version = 1), manifest, hash))
        assertEquals(PetImportAction.DOWNGRADE, PetImportPolicy.action(previous(version = 3), manifest, hash))
        assertEquals(PetImportAction.REPLACE, PetImportPolicy.action(previous(), manifest, hash))
        assertEquals(PetImportAction.REPLACE, PetImportPolicy.action(previous(hash = hash, status = PetLocalStatus.MISSING), manifest, hash))
    }

    @Test fun replacementAndDowngradeNeedSeparatePermissions() {
        assertTrue(runCatching { PetImportPolicy.requirePermission(PetImportAction.REPLACE, false, true) }.isFailure)
        assertTrue(runCatching { PetImportPolicy.requirePermission(PetImportAction.DOWNGRADE, true, false) }.isFailure)
        PetImportPolicy.requirePermission(PetImportAction.REPLACE, true, false)
        PetImportPolicy.requirePermission(PetImportAction.DOWNGRADE, false, true)
    }

    @Test fun oldPointerNaturallyReadsAbsentOptionalFields() {
        val json = """{"manifest":${GSON.toJson(manifest)},"sha256":"${"a".repeat(64)}"}"""
        val record = GSON.fromJson(json, InstalledPetPlugin::class.java)
        assertEquals(manifest, record.manifest)
        assertNull(record.info)
        assertNull(record.origin)
        assertNull(record.installedAt)
    }

    @Test fun resourceReceiptMustBindTheExactPayload() {
        val hash = "a".repeat(64)
        val origin = PetInstallOrigin("resource", "resource-id", "owner/repo", "b".repeat(40), "c".repeat(40), hash)
        origin.validate(hash)
        assertTrue(runCatching { origin.validate("d".repeat(64)) }.isFailure)
        assertTrue(runCatching { origin.copy(sourceCommit = null).validate(hash) }.isFailure)
        assertTrue(runCatching { origin.copy(channel = "local").validate(hash) }.isFailure)
    }

    @Test fun boundedCopyNeverWritesBeyondLimit() {
        val output = ByteArrayOutputStream()
        assertTrue(runCatching { PetPackageDisplay.copyBounded(ByteArrayInputStream(ByteArray(20)), output, 10) }.isFailure)
        assertTrue(output.size() <= 10)
    }

    @Test fun optionalInfoDoesNotRequireOrExecutePluginCode() {
        val file = archive("assets/pet-info.json" to """{"schemaVersion":1,"versionName":"2.1","author":"作者"}""")
        try { assertEquals("2.1", PetPackageDisplay.info(file)?.versionName) } finally { file.delete() }
        val plain = archive("assets/other.txt" to "x")
        try { assertNull(PetPackageDisplay.info(plain)) } finally { plain.delete() }
    }

    @Test fun badSchemaPathsTypesAndControlsAreRejected() {
        val cases = listOf(
            """{"schemaVersion":2}""",
            """{"schemaVersion":1,"versionName":7}""",
            """{"schemaVersion":1,"author":""}""",
            """{"schemaVersion":1,"author":"a\nb"}""",
            """{"schemaVersion":1,"preview":"../outside.png"}""",
            """{"schemaVersion":1,"preview":"https://example.test/a.png"}""",
            """{"schemaVersion":1,"unknown":"x"}""",
        )
        for (json in cases) {
            val file = archive("assets/pet-info.json" to json)
            try { assertTrue(json, runCatching { PetPackageDisplay.info(file) }.isFailure) } finally { file.delete() }
        }
    }

    private fun archive(vararg entries: Pair<String, String>): File = File.createTempFile("pet-info-", ".zip").also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, value) -> zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray()); zip.closeEntry() }
        }
    }
}
