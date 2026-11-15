package io.legado.app.help.resource

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.time.ZoneId

class ResourceProtocolTest {
    private val commit = "a".repeat(40)
    private val category = ResourceCategory("fonts", "字体", "font", "resources/fonts/index.json", 1)
    private val item = ResourceItem(
        id = "font.example", name = "示例", author = "作者", license = "OFL-1.1", versionCode = 1,
        payload = ResourcePayload("resources/fonts/example/font.ttf", "ttf", 32, "b".repeat(64)),
    )
    private fun entry(value: ResourceItem = item) = ResourceEntry(category, commit, value)
    private fun snapshot(values: List<ResourceEntry> = listOf(entry())) =
        ResourceSnapshot(catalogCommit = "c".repeat(40), catalog = ResourceCatalog(1, commit, listOf(category)), entries = values)
    private fun preview() = ResourceFontPreview(
        thumbnail = ResourcePreviewImage("previews/${"d".repeat(64)}.png", 256, "d".repeat(64), 256, 256),
        detail = ResourcePreviewImage("previews/${"e".repeat(64)}.png", 512, "e".repeat(64), 960, 420),
    )
    private fun previewEntry(value: ResourceFontPreview = preview()) =
        entry(item.copy(preview = value)).copy(catalogCommit = "c".repeat(40))

    @Test fun rejectsTraversalAndEncodedPathSeparators() {
        listOf("../x", "/x", "resources/../x", "resources//x", "resources/%2e%2e/x",
            "resources/a?x=1", "resources/a#x", "C:/x", "resources/\\x").forEach {
            assertFalse(it, ResourceProtocol.safePath(it))
        }
        assertTrue(ResourceProtocol.safePath("resources/fonts/示例/字体.ttf"))
    }

    private fun packet(line: String): String = "%04x".format(line.toByteArray().size + 4) + line

    @Test fun readsExactCatalogBranchFromSmartHttpAdvertisement() {
        val advertisement = packet("# service=git-upload-pack\n") + "0000" +
            packet("${"d".repeat(40)} HEAD\u0000multi_ack\n") +
            packet("$commit refs/heads/catalog\n") + "0000"
        assertEquals(commit, ResourceProtocol.catalogCommit(advertisement.toByteArray()))
    }

    @Test fun rejectsTruncatedOrDuplicateGitRefsInsteadOfGuessingHead() {
        listOf("0003", "zzzz", packet("# service=git-upload-pack\n") + "000fabc",
            packet("# service=git-upload-pack\n") + packet("$commit refs/heads/main\n"),
            packet("# service=git-upload-pack\n") + packet("$commit refs/heads/catalog\n").repeat(2),
        ).forEach { broken ->
            assertThrows(Exception::class.java) { ResourceProtocol.catalogCommit(broken.toByteArray()) }
        }
    }

    @Test fun rejectsMixedCommitSnapshots() {
        assertThrows(IllegalArgumentException::class.java) {
            ResourceProtocol.validateSnapshot(snapshot(listOf(entry().copy(sourceCommit = "d".repeat(40)))))
        }
    }

    @Test fun rejectsIncompleteCategoryIndex() {
        assertThrows(IllegalArgumentException::class.java) { ResourceProtocol.validateSnapshot(snapshot(emptyList())) }
    }

    @Test fun acceptsCompletePinnedSnapshotAndStableSerializedFields() {
        val original = snapshot()
        val encoded = Gson().toJson(original)
        assertTrue(encoded.contains("\"sourceCommit\""))
        assertTrue(encoded.contains("\"versionCode\""))
        assertEquals(original, Gson().fromJson(encoded, ResourceSnapshot::class.java))
        ResourceProtocol.validateSnapshot(original)
    }

    @Test fun acceptsMetadataWithoutOptionalLicenseAndDates() {
        val gson = Gson()
        val json = gson.toJsonTree(item).asJsonObject.apply { remove("license") }
        val decoded = gson.fromJson(json, ResourceItem::class.java)
        assertNull(decoded.license)
        assertNull(decoded.publishedAt)
        assertNull(decoded.updatedAt)
        ResourceProtocol.validateSnapshot(snapshot(listOf(entry(decoded))))
    }

    @Test fun resourceDatesRoundTripWithStableSerializedNames() {
        val dated = item.copy(publishedAt = "2026-10-26T12:00:00Z", updatedAt = "2026-10-27T03:00:00Z")
        val gson = Gson()
        val json = gson.toJsonTree(dated).asJsonObject
        assertEquals(dated.publishedAt, json.get("publishedAt").asString)
        assertEquals(dated.updatedAt, json.get("updatedAt").asString)
        assertEquals("OFL-1.1", json.get("license").asString)
        assertEquals(dated, gson.fromJson(json, ResourceItem::class.java))
        ResourceProtocol.validateSnapshot(snapshot(listOf(entry(dated))))
    }

    @Test fun resourceDatesUseTheSelectedTimezoneAndKeepMinutePrecision() {
        val timestamp = "2026-10-26T23:30:45.123Z"
        assertEquals("2026-10-26 23:30", ResourceDateTime.format(timestamp, ZoneId.of("UTC")))
        assertEquals("2026-10-27 07:30", ResourceDateTime.format(timestamp, ZoneId.of("Asia/Shanghai")))
        assertEquals("2026-07-01 08:30", ResourceDateTime.format("2026-07-01T12:30:00Z", ZoneId.of("America/New_York")))
        assertEquals("2026-01-01 07:30", ResourceDateTime.format("2026-01-01T12:30:00Z", ZoneId.of("America/New_York")))
    }

    @Test fun missingOrInvalidDatesNeverInventAResourceTimestamp() {
        listOf(null, "", "  ", "not-a-date", "2026-10-26", "2026-02-30T12:00:00Z", "2026-10-26T12:00:00").forEach {
            assertEquals("—", ResourceDateTime.format(it, ZoneId.of("UTC")))
        }
    }

    @Test fun metadataAndPathChangesDoNotTriggerReinstall() {
        val local = ResourceInstallation(entry = entry(), target = "local", baseline = item.payload.sha256)
        val renamed = item.copy(name = "改名", versionName = "新的展示版本", description = "新介绍", license = null,
            publishedAt = "2026-10-26T12:00:00Z", updatedAt = "2026-10-27T03:00:00Z",
            payload = item.payload.copy(path = "resources/fonts/moved/font.ttf"))
        assertEquals("已下载", ResourceProtocol.versionState(entry(renamed), local))
    }

    @Test fun previewFieldsAndPinnedCatalogSurviveInstallationSerialization() {
        val original = ResourceInstallation(entry = previewEntry(), target = "font", baseline = item.payload.sha256)
        val gson = Gson()
        val encoded = gson.toJsonTree(original).asJsonObject
        val savedEntry = encoded.getAsJsonObject("entry")
        assertEquals("c".repeat(40), savedEntry.get("catalogCommit").asString)
        val image = savedEntry.getAsJsonObject("item").getAsJsonObject("preview").getAsJsonObject("thumbnail")
        listOf("path", "size", "sha256", "width", "height").forEach { assertTrue(image.has(it)) }
        assertEquals(original, gson.fromJson(encoded, ResourceInstallation::class.java))
        ResourceProtocol.validateSnapshot(snapshot(listOf(original.entry)))
    }

    @Test fun previewsRequireBothSizesAndTheFontResourceType() {
        listOf(preview().copy(thumbnail = null), preview().copy(detail = null)).forEach {
            assertThrows(IllegalArgumentException::class.java) { ResourceProtocol.validateSnapshot(snapshot(listOf(previewEntry(it)))) }
        }
        listOf("background", "theme", "tts").forEach { type ->
            assertThrows(IllegalArgumentException::class.java) { ResourceProtocol.validatePreview(preview(), type) }
        }
    }

    @Test fun petPreviewsUseSquareDetailAndPreserveInstallIdentity() {
        val pet = preview().copy(detail = preview().detail!!.copy(width = 768, height = 768))
        ResourceProtocol.validatePreview(pet, "pet")
        assertThrows(IllegalArgumentException::class.java) { ResourceProtocol.validatePreview(preview(), "pet") }
        assertThrows(IllegalArgumentException::class.java) { ResourceProtocol.validatePreview(pet, "font") }
        val before = entry().copy(category = category.copy(type = "pet"), item = item.copy(
            payload = item.payload.copy(format = "zip", pluginId = "example_pet"),
        ))
        val local = ResourceInstallation(entry = before, target = "example_pet", baseline = before.item.payload.sha256)
        val after = before.copy(item = before.item.copy(preview = pet), catalogCommit = "c".repeat(40))
        ResourceProtocol.validatePreviewSource(after)
        assertEquals("已下载", ResourceProtocol.versionState(after, local))
        assertFalse(ResourceProtocol.isUpdate(after, local))
    }

    @Test fun readingPresetUsesZipAndSquareCatalogImages() {
        val presetCategory = ResourceCategory("read-presets", "阅读预设", "read_preset", "resources/read-presets/index.json", 1)
        val presetItem = item.copy(payload = ResourcePayload("resources/read-presets/sample/preset.zip", "zip", 32, "b".repeat(64)),
            preview = preview().copy(detail = preview().detail!!.copy(width = 768, height = 768)))
        val preset = ResourceEntry(presetCategory, commit, presetItem, "c".repeat(40))
        ResourceProtocol.validateSnapshot(ResourceSnapshot(catalogCommit = preset.catalogCommit,
            catalog = ResourceCatalog(1, commit, listOf(presetCategory)), entries = listOf(preset)))
        assertNull(ResourceProtocol.incompatibility(preset, Long.MAX_VALUE))
        assertTrue(ResourceProtocol.isBrowsableType("read_preset"))
        assertFalse(ResourceProtocol.isBrowsableType("font"))
        assertFalse(ResourceProtocol.isBrowsableType("background"))
    }

    @Test fun previewFilesCannotEscapeTheirHashAddressedPngPaths() {
        val asset = preview().thumbnail!!
        listOf("../${asset.sha256}.png", "previews/../${asset.sha256}.png", "previews/%2e%2e/x.png",
            "https://example.com/font.png", "resources/fonts/font.png", "previews/${asset.sha256}.jpg",
            "previews/${asset.sha256.uppercase()}.png", "previews/${"f".repeat(64)}.png").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) {
                ResourceProtocol.validatePreview(preview().copy(thumbnail = asset.copy(path = path)), "font")
            }
        }
    }

    @Test fun previewMetadataHasBoundedExactDimensionsAndSize() {
        val asset = preview().thumbnail!!
        listOf(asset.copy(size = 0), asset.copy(size = ResourceProtocol.MAX_PREVIEW_BYTES + 1),
            asset.copy(width = 0), asset.copy(width = Int.MAX_VALUE), asset.copy(height = 255),
            asset.copy(sha256 = "invalid")).forEach {
            assertThrows(IllegalArgumentException::class.java) {
                ResourceProtocol.validatePreview(preview().copy(thumbnail = it), "font")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResourceProtocol.validatePreview(preview().copy(detail = preview().detail!!.copy(width = 420, height = 960)), "font")
        }
    }

    @Test fun previewOriginCannotBeBorrowedFromAnotherCatalogSnapshot() {
        val old = previewEntry()
        val newCatalog = "f".repeat(40)
        val refreshed = old.copy(catalogCommit = newCatalog)
        ResourceProtocol.validateSnapshot(snapshot(listOf(refreshed)).copy(catalogCommit = newCatalog))
        assertThrows(IllegalArgumentException::class.java) {
            ResourceProtocol.validateSnapshot(snapshot(listOf(old)).copy(catalogCommit = newCatalog))
        }
        listOf("", "catalog", "0".repeat(40), commit).forEach {
            assertThrows(IllegalArgumentException::class.java) {
                ResourceProtocol.validateSnapshot(snapshot(listOf(old.copy(catalogCommit = it))))
            }
        }
        assertEquals("c".repeat(40), old.catalogCommit)
    }

    @Test fun previewChangesDoNotReinstallFontsOrMarkUndownloadedFontsInstalled() {
        val old = previewEntry()
        val local = ResourceInstallation(entry = old, target = "font", baseline = item.payload.sha256)
        val image = preview().detail!!.copy(path = "previews/${"f".repeat(64)}.png", sha256 = "f".repeat(64))
        val changed = old.copy(catalogCommit = "f".repeat(40), item = old.item.copy(preview = preview().copy(detail = image)))
        assertEquals("已下载", ResourceProtocol.versionState(changed, local))
        assertFalse(ResourceProtocol.isUpdate(changed, local))
        assertEquals("未下载", ResourceProtocol.versionState(changed, null))
        assertTrue(ResourceCenterState(snapshot = snapshot(listOf(old))).downloadedInstallations().isEmpty())
    }

    @Test fun oldEntriesWithoutPreviewsDoNotInventCatalogOrigins() {
        val gson = Gson()
        val encoded = gson.toJsonTree(entry()).asJsonObject.apply { remove("catalogCommit") }
        val decoded = gson.fromJson(encoded, ResourceEntry::class.java)
        assertNull(decoded.item.preview)
        assertEquals("", decoded.catalogCommit)
        ResourceProtocol.validateSnapshot(snapshot(listOf(decoded)))
    }

    @Test fun previewPngRejectsContentAndHeaderMismatchesBeforeDecode() {
        val header = ByteBuffer.allocate(33)
            .put(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
            .putInt(13).putInt(0x49484452).putInt(256).putInt(256).array()
        val asset = preview().thumbnail!!.copy(size = header.size.toLong(), sha256 = ResourceProtocol.hash(header))
        assertThrows(IllegalArgumentException::class.java) { ResourceProtocol.validatePreviewPng(header, asset.copy(size = 1)) }
        assertThrows(IllegalArgumentException::class.java) { ResourceProtocol.validatePreviewPng(header, asset.copy(sha256 = "f".repeat(64))) }
        assertThrows(IllegalArgumentException::class.java) { ResourceProtocol.validatePreviewPng(header, asset.copy(width = 960, height = 420)) }
        val wrongSignature = header.copyOf().apply { this[0] = 0 }
        assertThrows(IllegalArgumentException::class.java) {
            ResourceProtocol.validatePreviewPng(wrongSignature, asset.copy(sha256 = ResourceProtocol.hash(wrongSignature)))
        }
        val wrongHeader = header.copyOf().also { ByteBuffer.wrap(it).putInt(16, Int.MAX_VALUE) }
        assertThrows(IllegalArgumentException::class.java) {
            ResourceProtocol.validatePreviewPng(wrongHeader, asset.copy(sha256 = ResourceProtocol.hash(wrongHeader)))
        }
    }

    @Test fun contentVersionMovesForwardAndNeverSilentlyDowngrades() {
        val local = ResourceInstallation(entry = entry(), target = "local", baseline = item.payload.sha256)
        assertEquals("可更新", ResourceProtocol.versionState(entry(item.copy(versionCode = 2)), local))
        val newer = local.copy(entry = entry(item.copy(versionCode = 3)))
        assertEquals("本地版本较新", ResourceProtocol.versionState(entry(), newer))
        assertEquals("来源版本异常", ResourceProtocol.versionState(entry(item.copy(payload = item.payload.copy(sha256 = "e".repeat(64)))), local))
    }

    @Test fun nativeIdentityCannotChangeForExistingResource() {
        val tts = entry().copy(category = category.copy(type = "tts"), item = item.copy(payload = item.payload.copy(engineId = "first")))
        val local = ResourceInstallation(entry = tts, target = "first")
        assertThrows(IllegalArgumentException::class.java) {
            ResourceProtocol.versionState(tts.copy(item = tts.item.copy(payload = tts.item.payload.copy(engineId = "second"))), local)
        }
    }

    @Test fun compatibilityIsCheckedEvenWhenPayloadVersionIsUnchanged() {
        assertNull(ResourceProtocol.incompatibility(entry(), 100))
        assertNotNull(ResourceProtocol.incompatibility(entry(item.copy(requirements = ResourceRequirements(101))), 100))
        assertNotNull(ResourceProtocol.incompatibility(entry(item.copy(requirements = ResourceRequirements(capabilities = listOf("unknown")))), 100))
        assertNotNull(ResourceProtocol.incompatibility(entry(item.copy(payload = item.payload.copy(size = ResourceProtocol.MAX_PAYLOAD_BYTES + 1))), 100))
    }

    @Test fun centerCropThemeCapabilityDoesNotAcceptUnknownCapabilities() {
        val themeCategory = ResourceCategory("themes", "主题包", "theme", "resources/themes/index.json", 1)
        fun theme(vararg capabilities: String) = ResourceEntry(themeCategory, commit, item.copy(
            id = "ng.theme.claude_and_bluefish",
            payload = ResourcePayload("resources/themes/example/theme.zip", "zip", 32, "b".repeat(64)),
            requirements = ResourceRequirements(capabilities = capabilities.toList()),
        ))
        assertNull(ResourceProtocol.incompatibility(theme(), Long.MAX_VALUE))
        assertNull(ResourceProtocol.incompatibility(theme("theme.effect.1"), Long.MAX_VALUE))
        assertNull(ResourceProtocol.incompatibility(theme(THEME_CENTER_CROP_CAPABILITY), Long.MAX_VALUE))
        assertNull(ResourceProtocol.incompatibility(theme("theme.effect.1", THEME_CENTER_CROP_CAPABILITY), Long.MAX_VALUE))
        assertNotNull(ResourceProtocol.incompatibility(theme(THEME_CENTER_CROP_CAPABILITY, "unknown"), Long.MAX_VALUE))
    }

    @Test fun blueFishRequiresItsRendererCapabilityWithoutChangingExistingSkins() {
        val category = ResourceCategory("widget-skins", "组件皮肤", HOME_WIDGET_SKIN_TYPE,
            "resources/widget-skins/index.json", 1)
        fun skin(id: String, capabilities: List<String>) = ResourceEntry(category, commit, item.copy(
            id = id,
            payload = ResourcePayload("resources/widget-skins/example/skin.zip", "zip", 32, "b".repeat(64)),
            requirements = ResourceRequirements(capabilities = capabilities),
        ))
        val common = HOME_WIDGET_SKIN_CAPABILITY
        val blue = HOME_WIDGET_BLUE_FISH_CAPABILITY
        val blueFish = "ng.home_skin.blue_fish"
        assertNull(ResourceProtocol.incompatibility(skin(blueFish, listOf(common, blue)), Long.MAX_VALUE))
        assertNotNull(ResourceProtocol.incompatibility(skin(blueFish, listOf(common)), Long.MAX_VALUE))
        assertNotNull(ResourceProtocol.incompatibility(skin(blueFish, listOf(blue)), Long.MAX_VALUE))
        assertNotNull(ResourceProtocol.incompatibility(skin(blueFish, listOf(common, blue, "unknown")), Long.MAX_VALUE))
        assertNull(ResourceProtocol.incompatibility(skin("ng.home_skin.storybook", listOf(common)), Long.MAX_VALUE))
        assertNotNull(ResourceProtocol.incompatibility(skin("ng.home_skin.storybook", listOf(common, blue)), Long.MAX_VALUE))
        // The earlier client accepted only the one-element common capability set.
        assertNotEquals(setOf(common), setOf(common, blue))
    }

    @Test fun zeroCommitAndPayloadOutsideCategoryAreRejected() {
        assertFalse(ResourceProtocol.validCommit("0".repeat(40)))
        assertThrows(IllegalArgumentException::class.java) {
            ResourceProtocol.validateIndex(snapshot().catalog, category, ResourceIndex(1, commit, "font",
                listOf(item.copy(payload = item.payload.copy(path = "resources/tts/font.ttf")))))
        }
    }

    @Test fun duplicateCaseInsensitiveIdsAreRejected() {
        val two = category.copy(count = 2)
        assertThrows(IllegalArgumentException::class.java) {
            ResourceProtocol.validateSnapshot(snapshot().copy(
                catalog = ResourceCatalog(1, commit, listOf(two)),
                entries = listOf(entry().copy(category = two), entry(item.copy(id = "FONT.EXAMPLE")).copy(category = two)),
            ))
        }
    }
}

