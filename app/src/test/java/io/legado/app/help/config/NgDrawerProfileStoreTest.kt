package io.legado.app.help.config

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import io.legado.app.constant.PreferKey
import io.legado.app.utils.GSON
import io.legado.app.utils.defaultSharedPreferences
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import java.io.File
import java.util.Base64
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class NgDrawerProfileStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun resetCurrentPreferences() {
        context.defaultSharedPreferences.edit().remove(PreferKey.ngDrawerBackground).commit()
        NgDrawerProfileStore.reloadAfterRestore(context)
    }

    @Test
    fun `absent current profile ignores previous development settings`() {
        context.defaultSharedPreferences.edit()
            .putString("ngDrawerProfile.v1", "{\"light\":{\"mode\":\"color\",\"backgroundColor\":42}}")
            .putInt("ngDrawerTransparency", 99).commit()
        try {
            NgDrawerProfileStore.reloadAfterRestore(context)
            assertNull(NgDrawerProfileStore.current(context))
            assertEquals(NgThemeDrawerProfile(), NgDrawerProfileStore.snapshot(context))
        } finally {
            context.defaultSharedPreferences.edit().remove("ngDrawerProfile.v1").remove("ngDrawerTransparency").commit()
        }
    }

    @Test
    fun `settings persist independently while observers and theme snapshots see the current source`() {
        val saved = NgThemeLibraryStore.addOrReplace(context, customTheme())
        val activeBefore = NgThemeLibraryStore.current(context).activeThemeId
        try {
            NgDrawerProfileStore.update(context, NgThemeDrawerProfile(
                source = "custom_color",
                dark = NgThemeDrawerStyle(backgroundColor = 0xFF102030.toInt()),
            ))
            assertEquals(activeBefore, NgThemeLibraryStore.current(context).activeThemeId)
            assertEquals(saved.drawerProfile, NgThemeLibraryStore.current(context).savedThemes.single { it.id == saved.id }.drawerProfile)
            val observed = NgDrawerProfileStore.observe(context)
            context.defaultSharedPreferences.edit().putString(
                PreferKey.ngDrawerBackground, GSON.toJson(NgThemeDrawerProfile(source = "theme_image")),
            ).commit()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("theme_image", observed.value.profile?.source)
            val snapshot = NgThemeLibraryStore.snapshotCurrent(context, "current-${UUID.randomUUID()}")
            assertEquals(NgDrawerProfileStore.snapshot(context), snapshot.drawerProfile)
        } finally {
            NgThemeLibraryStore.remove(context, saved.id)
        }
    }

    @Test
    fun `a theme with no drawer profile preserves current settings`() {
        val current = NgDrawerProfileStore.update(context, NgThemeDrawerProfile(
            dark = NgThemeDrawerStyle(backgroundColor = 0xFF123456.toInt()),
            source = "custom_color",
        ))
        assertTrue(NgThemeLibraryStore.apply(context, NgBuiltInThemes.autumn.copy(drawerProfile = null)))
        assertEquals(current, NgDrawerProfileStore.current(context))
    }

    @Test
    fun `current images and theme snapshots have independent lifetimes`() {
        val source = sampleImage()
        val saved = NgThemeLibraryStore.addOrReplace(context, customTheme(source.path))
        val current = NgDrawerProfileStore.update(context, requireNotNull(saved.drawerProfile))
        val themeImage = File(requireNotNull(saved.drawerProfile?.light?.imagePath))
        val currentImage = File(requireNotNull(current.light.imagePath))
        var copiedTheme: NgManagedTheme? = null
        try {
            assertNotEquals(themeImage.path, currentImage.path)
            assertArrayEquals(themeImage.readBytes(), currentImage.readBytes())
            NgThemeLibraryStore.remove(context, saved.id)
            assertFalse(themeImage.exists())
            assertTrue(currentImage.isFile)
            val snapshot = NgThemeLibraryStore.saveCurrent(context, "copy-${UUID.randomUUID()}")
            copiedTheme = snapshot
            val copiedImage = File(requireNotNull(snapshot.drawerProfile?.light?.imagePath))
            assertNotEquals(currentImage.path, copiedImage.path)
            NgDrawerProfileStore.update(context, current.copy(
                light = current.light.copy(imagePath = null),
                dark = current.dark.copy(imagePath = null),
            ))
            assertFalse(currentImage.exists())
            assertTrue(copiedImage.isFile)
        } finally {
            NgThemeLibraryStore.remove(context, saved.id)
            copiedTheme?.let { NgThemeLibraryStore.remove(context, it.id) }
            source.delete()
        }
    }

    @Test
    fun `failed image preparation leaves current background unchanged`() {
        val before = NgDrawerProfileStore.update(context, NgThemeDrawerProfile(source = "theme_image"))
        val source = sampleImage()
        val invalid = File(context.cacheDir, "${UUID.randomUUID()}.png").apply { writeText("invalid") }
        val imageRoot = File(context.filesDir, "ng_current_drawer_images")
        val filesBefore = imageRoot.list().orEmpty().toSet()
        try {
            val result = runCatching {
                NgDrawerProfileStore.update(context, before.copy(
                    light = before.light.copy(imagePath = source.path),
                    dark = before.dark.copy(imagePath = invalid.path),
                    source = "custom_image",
                ))
            }
            assertTrue(result.isFailure)
            assertEquals(before, NgDrawerProfileStore.snapshot(context))
            assertEquals(filesBefore, imageRoot.list().orEmpty().toSet())
            assertTrue(source.isFile)
        } finally {
            source.delete()
            invalid.delete()
        }
    }

    @Test
    fun `theme image snapshots retain a dynamic source without capturing wallpaper paths`() {
        NgDrawerProfileStore.update(context, NgThemeDrawerProfile(source = "theme_image"))
        val snapshot = NgThemeLibraryStore.snapshotCurrent(context, "dynamic-${UUID.randomUUID()}")
        assertEquals("theme_image", snapshot.drawerProfile?.source)
        assertNull(snapshot.drawerProfile?.light?.imagePath)
        assertNull(snapshot.drawerProfile?.dark?.imagePath)
        assertEquals(snapshot.drawerProfile, NgThemeLibraryStore.editableDrawerProfile(context, null))
    }

    @Test
    fun `failed preference commit restores both present and absent values before image rollback`() {
        val prefs = context.defaultSharedPreferences
        listOf(false, true).forEach { existed ->
            prefs.edit().apply {
                if (existed) putString(PreferKey.ngDrawerBackground, GSON.toJson(NgThemeDrawerProfile(source = "custom_color")))
                else remove(PreferKey.ngDrawerBackground)
                putString("unrelated-setting", "keep")
            }.commit()
            val previous = prefs.all.toMap()
            val result = runCatching {
                commitDrawerPreferences(FailFirstCommitPreferences(prefs), NgThemeDrawerProfile(
                    source = "custom_image",
                    light = NgThemeDrawerStyle(imagePath = "/new/current/image.webp"),
                ))
            }
            assertTrue(result.isFailure)
            assertEquals(previous, prefs.all)
            assertEquals(existed, prefs.contains(PreferKey.ngDrawerBackground))
            NgDrawerProfileStore.reloadAfterRestore(context)
            assertNull(NgDrawerProfileStore.current(context)?.light?.imagePath)
        }
    }

    private fun customTheme(image: String? = null): NgManagedTheme = NgBuiltInThemes.storybook.copy(
        id = "local.${UUID.randomUUID()}",
        name = "drawer-settings-${UUID.randomUUID()}",
        drawerProfile = requireNotNull(NgBuiltInThemes.storybook.drawerProfile).let {
            it.copy(source = "custom_image", light = it.light.copy(imagePath = image))
        },
    )

    private fun sampleImage(): File = File(context.cacheDir, "${UUID.randomUUID()}.png").also {
        it.writeBytes(Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        ))
    }

    private class FailFirstCommitPreferences(
        private val delegate: SharedPreferences,
    ) : SharedPreferences by delegate {
        private var failed = false

        override fun edit(): SharedPreferences.Editor {
            val editor = delegate.edit()
            return object : SharedPreferences.Editor by editor {
                override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                    editor.putString(key, value)
                    return this
                }

                override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                    editor.putInt(key, value)
                    return this
                }

                override fun remove(key: String?): SharedPreferences.Editor {
                    editor.remove(key)
                    return this
                }

                override fun commit(): Boolean {
                    val success = editor.commit()
                    if (failed) return success
                    failed = true
                    return false
                }
            }
        }
    }
}
