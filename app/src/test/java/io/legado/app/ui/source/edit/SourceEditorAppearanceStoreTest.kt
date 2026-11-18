package io.legado.app.ui.source.edit

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = InterfaceFontTestApplication::class)
class SourceEditorAppearanceStoreTest {
    @Test
    fun replacingInvalidAndHidingBackgroundKeepUserImageAndTheme() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SourceEditorAppearanceStore
        val picture = File(context.cacheDir, "chosen.png").apply {
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val invalid = File(context.cacheDir, "invalid.png").apply { writeText("not an image") }
        store.setTheme(context, SourceEditorTheme.ONE_DARK)
        store.setImage(context, Uri.fromFile(picture))
        val selected = store.read(context)
        val first = requireNotNull(selected.image)
        assertArrayEquals(picture.readBytes(), first.readBytes())
        assertTrue(runCatching { store.setImage(context, Uri.fromFile(invalid)) }.isFailure)
        assertEquals(selected, store.read(context))
        store.setImage(context, Uri.fromFile(picture))
        val second = requireNotNull(store.read(context).image)
        assertFalse(first.exists())
        assertTrue(second.isFile)
        store.setImageVisible(context, false)
        assertEquals(SourceEditorAppearance(SourceEditorTheme.ONE_DARK, second, false), store.read(context))
        assertEquals(null, store.read(context).visibleImage)
        assertArrayEquals(picture.readBytes(), second.readBytes())
        store.setImageVisible(context, true)
        assertEquals(second, store.read(context).visibleImage)
        assertTrue(picture.isFile)
    }
}
