package io.legado.app.help.resource

import io.legado.app.utils.GSON
import io.legado.app.help.home.HomeWidgetSkinPackage
import io.legado.app.help.home.HomeWidgetSkinRenderer
import io.legado.app.help.home.HomeWidgetSkinRecord
import io.legado.app.help.home.HomeWidgetSkinLedger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class HomeWidgetSkinPackageTest {
    @Test
    fun localSkinLedgerRoundTripsStableFields() {
        val record = HomeWidgetSkinRecord("ng.home_skin.night_desk", "夜灯书桌", "a".repeat(64), 400L)
        val ledger = HomeWidgetSkinLedger(skins = listOf(record))
        val restored = GSON.fromJson(GSON.toJson(ledger), HomeWidgetSkinLedger::class.java)
        restored.skins.single().validate()
        assertEquals(ledger, restored)
        assertFalse(restored.skins.single().fromResource)
        assertThrows(IllegalArgumentException::class.java) {
            record.copy(id = "../night").validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            record.copy(sha256 = "").validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            record.copy(size = HomeWidgetSkinPackage.MAX_PACKAGE_BYTES + 1).validate()
        }
    }

    @Test
    fun readsTheFourNightDeskArtworkRoles() = withArchive(nightDesk()) { archive ->
        assertEquals(HomeWidgetSkinRenderer.NIGHT_DESK, HomeWidgetSkinPackage.read(archive) { 100 to 100 })
    }

    @Test
    fun blueFishIsASeparatePackageAndRequiresAllOfItsArtwork() {
        val renderer = HomeWidgetSkinRenderer.BLUE_FISH
        val entries = mapOf("manifest.json" to """{"schemaVersion":1,"renderer":"blue_fish"}""".toByteArray()) +
            renderer.artwork.associate { HomeWidgetSkinPackage.artworkPath(it) to encodedWebp }
        withArchive(entries) { archive ->
            assertEquals(renderer, HomeWidgetSkinPackage.read(archive) { 100 to 100 })
            assertEquals("ng.home_skin.blue_fish", renderer.resourceId)
        }
        withArchive(entries + ("artwork/calendar.webp" to encodedWebp)) { archive ->
            assertEquals(renderer, HomeWidgetSkinPackage.read(archive) { 100 to 100 })
        }
        withArchive(entries + ("artwork/calendar.webp" to "not-webp".toByteArray())) { archive ->
            assertTrue(runCatching { HomeWidgetSkinPackage.read(archive) { 100 to 100 } }.isFailure)
        }
        withArchive(entries - "artwork/reading.webp") { archive ->
            assertTrue(runCatching { HomeWidgetSkinPackage.read(archive) { 100 to 100 } }.isFailure)
        }
    }

    @Test
    fun validatesTheOptionalPreviewAtItsFixedDimensions() {
        val png = ByteBuffer.allocate(33)
            .put(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
            .putInt(13).putInt(0x49484452).putInt(768).putInt(768).array()
        withArchive(nightDesk() + ("preview.png" to png)) { archive ->
            assertEquals(HomeWidgetSkinRenderer.NIGHT_DESK, HomeWidgetSkinPackage.read(archive) { 100 to 100 })
        }
        ByteBuffer.wrap(png).putInt(16, 769)
        withArchive(nightDesk() + ("preview.png" to png)) { archive ->
            assertTrue(runCatching { HomeWidgetSkinPackage.read(archive) { 100 to 100 } }.isFailure)
        }
    }

    @Test
    fun rejectsExtraFilesMissingArtworkAndOversizedImages() {
        val valid = nightDesk()
        for ((entries, size) in listOf(
            (valid + ("classes.dex" to byteArrayOf(1))) to (100 to 100),
            (valid + ("../lamp.webp" to encodedWebp)) to (100 to 100),
            (valid - "artwork/lamp.webp") to (100 to 100),
            valid to (1537 to 100),
        )) {
            withArchive(entries) { archive ->
                assertTrue(runCatching { HomeWidgetSkinPackage.read(archive) { size } }.isFailure)
            }
        }
    }

    private fun nightDesk() =
        mapOf("manifest.json" to """{"schemaVersion":1,"renderer":"night"}""".toByteArray()) +
            HomeWidgetSkinRenderer.NIGHT_DESK.artwork.associate { HomeWidgetSkinPackage.artworkPath(it) to encodedWebp }

    private fun withArchive(entries: Map<String, ByteArray>, check: (File) -> Unit) {
        val file = File.createTempFile("widget-skin-test", ".zip")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            check(file)
        } finally {
            file.delete()
        }
    }

    // The reader test substitutes bounds decoding to exercise the exact ZIP contract on the JVM.
    private val encodedWebp = "RIFF0000WEBP0000".toByteArray()
}
