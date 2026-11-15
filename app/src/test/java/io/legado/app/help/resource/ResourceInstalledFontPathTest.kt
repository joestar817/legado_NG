package io.legado.app.help.resource

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.help.font.FontLibraryStore
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = InterfaceFontTestApplication::class)
class ResourceInstalledFontPathTest {
    @Test fun useResolvesOnlyTheCurrentUnmodifiedInstalledFont() = runBlocking {
        val suffix = UUID.randomUUID().toString().replace("-", "")
        val id = "font.test$suffix"
        val bytes = "font-$suffix".toByteArray()
        val hash = ResourceProtocol.hash(bytes)
        val target = File(FontLibraryStore.downloadRoot, "$suffix.ttf")
        val oldRecords = ResourceInstallStore.load()
        check(ResourceInstallStore.pendingFontDeletion() == null)
        try {
            target.parentFile!!.mkdirs()
            target.writeBytes(bytes)
            val entry = ResourceEntry(
                category = ResourceCategory("fonts", "字体", "font", "resources/fonts/index.json", 1),
                sourceCommit = "a".repeat(40),
                item = ResourceItem(
                    id = id, name = "测试", author = "测试", versionCode = 1,
                    payload = ResourcePayload("resources/fonts/test/font.ttf", "ttf", bytes.size.toLong(), hash),
                ),
            )
            ResourceInstallStore.save(oldRecords + ResourceInstallation(entry = entry, target = target.path, baseline = hash))
            assertEquals(target.canonicalPath, ResourceRepository.installedFontPath(id))
            assertNull(ResourceRepository.installedFontPath("font.not-installed"))

            ResourceInstallStore.prepareFontDeletion(ResourceFontDeletion(
                id = hash, name = "测试", path = target.path, paths = listOf(target.path), size = target.length(),
            ))
            assertNull(ResourceRepository.installedFontPath(id))
            ResourceInstallStore.clearFontDeletion()

            target.writeText("changed")
            assertNull(ResourceRepository.installedFontPath(id))
            target.delete()
            assertNull(ResourceRepository.installedFontPath(id))
        } finally {
            ResourceInstallStore.clearFontDeletion()
            ResourceInstallStore.save(oldRecords)
            target.delete()
        }
    }
}
