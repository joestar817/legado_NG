package io.legado.app.help.font

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.NgInterfaceFontStore
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.utils.defaultSharedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = InterfaceFontTestApplication::class)
class FontDirectoryCatalogTest {
    private fun withDirectories(action: (Context, File, File) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.defaultSharedPreferences
        val keys = listOf(NgInterfaceFontStore.DIRECTORY, PreferKey.fontFolder, "ngInterfaceFont.choice")
        val previous = keys.associateWith { preferences.getString(it, null) }
        val root = File(context.cacheDir, "font-directories-${UUID.randomUUID()}").apply { mkdirs() }
        val first = File(root, "interface").apply { mkdirs() }
        val second = File(root, "reader").apply { mkdirs() }
        try {
            preferences.edit().putString(NgInterfaceFontStore.DIRECTORY, first.toURI().toString())
                .putString(PreferKey.fontFolder, second.path).putString("ngInterfaceFont.choice", "system").commit()
            action(context, first, second)
        } finally {
            preferences.edit().apply { previous.forEach { (key, value) -> putString(key, value) } }.commit()
            root.deleteRecursively()
        }
    }

    @Test fun listsBothConfiguredDirectoriesWithoutCopyingOrMergingDifferentFiles() = withDirectories { _, first, second ->
        val left = File(first, "same.ttf").apply { writeText("font bytes") }
        val right = File(second, "same.ttf").apply { writeText("font bytes") }
        val before = FontLibraryStore.list().flatMap { it.paths }.toSet()
        val entries = FontDirectoryCatalog.scan(refresh = true).entries
            .filter { it.directoryUri != null }
        assertEquals(setOf(left.canonicalPath, right.canonicalPath), entries.map { it.id }.toSet())
        assertTrue(entries.all { it.managed == null })
        assertEquals(1, entries.map { it.sha256 }.distinct().size)
        assertEquals(before, FontLibraryStore.list().flatMap { it.paths }.toSet())
    }

    @Test fun aMissingDirectoryDoesNotHideFontsFromTheOtherDirectory() = withDirectories { _, first, second ->
        val file = File(second, "font.otf").apply { writeText("font bytes") }
        first.delete()
        val snapshot = FontDirectoryCatalog.scan(refresh = true)
        assertTrue(snapshot.errors.isNotEmpty())
        assertTrue(snapshot.entries.any { it.id == file.canonicalPath })
    }

    @Test fun cachedScanStillEnumeratesAddedRemovedAndChangedFiles() = withDirectories { _, first, _ ->
        val removed = File(first, "removed.ttf").apply { writeText("removed bytes") }
        val changed = File(first, "changed.ttf").apply { writeText("before") }
        val initial = FontDirectoryCatalog.scan(refresh = true)
        val originalHash = initial.entries.first { it.id == changed.canonicalPath }.sha256
        assertTrue(removed.delete())
        changed.writeText("a different size")
        val added = File(first, "added.otf").apply { writeText("added bytes") }

        val current = FontDirectoryCatalog.scan().entries.filter { it.directoryUri != null }

        assertEquals(setOf(changed.canonicalPath, added.canonicalPath), current.map { it.id }.toSet())
        val currentChanged = current.first { it.id == changed.canonicalPath }
        assertEquals(changed.length(), currentChanged.size)
        assertNotEquals(originalHash, currentChanged.sha256)
    }

    @Test fun requestedFontMovesFirstWithoutChangingOrDuplicatingOtherEntries() = withDirectories { _, first, _ ->
        val alpha = File(first, "alpha.ttf").apply { writeText("alpha") }
        val beta = File(first, "beta.ttf").apply { writeText("beta") }
        val entries = FontDirectoryCatalog.list(refresh = true).filter { it.directoryUri != null }

        val preferred = FontDirectoryCatalog.prioritize(entries, beta.toURI().toString())

        assertEquals(beta.canonicalPath, preferred.first().id)
        assertEquals(setOf(alpha.canonicalPath, beta.canonicalPath), preferred.map { it.id }.toSet())
        assertEquals(entries.size, preferred.size)
        assertEquals(entries, FontDirectoryCatalog.prioritize(entries, File(first, "missing.ttf").path))
        assertEquals(entries, FontDirectoryCatalog.prioritize(entries, null))
    }

    @Test fun changedFileCannotBeDeletedUsingAnOldConfirmation() = withDirectories { _, first, _ ->
        val file = File(first, "font.ttf").apply { writeText("before") }
        val entry = FontDirectoryCatalog.list(refresh = true).first { it.id == file.canonicalPath }
        val stamp = file.lastModified()
        file.writeText("after!")
        assertTrue(file.setLastModified(stamp))
        // Display scans may reuse a matching stat cache; deletion must still read the target bytes.
        val cached = FontDirectoryCatalog.list().first { it.id == file.canonicalPath }
        assertEquals(entry.sha256, cached.sha256)
        val failure = assertThrows(IllegalStateException::class.java) { runBlocking { FontDirectoryCatalog.delete(cached) } }
        assertTrue(failure.message.orEmpty().contains("已变化"))
        assertEquals("after!", file.readText())
    }

    @Test fun replacingTheDirectoryRevokesAnOldDeletionTarget() = withDirectories { context, first, second ->
        val file = File(first, "font.ttf").apply { writeText("font bytes") }
        val entry = FontDirectoryCatalog.list(refresh = true).first { it.id == file.canonicalPath }
        context.defaultSharedPreferences.edit().putString(NgInterfaceFontStore.DIRECTORY, second.toURI().toString()).commit()
        assertThrows(IllegalStateException::class.java) { runBlocking { FontDirectoryCatalog.delete(entry) } }
        assertTrue(file.isFile)
    }
}
