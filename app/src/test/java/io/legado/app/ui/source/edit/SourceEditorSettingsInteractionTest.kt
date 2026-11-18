package io.legado.app.ui.source.edit

import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.view.inspector.WindowInspector
import androidx.compose.ui.platform.LocalView
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import io.legado.app.data.entities.RssSource
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.rss.source.edit.RssSourceEditAction
import io.legado.app.ui.rss.source.edit.RssSourceEditScreen
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", application = InterfaceFontTestApplication::class)
class SourceEditorSettingsInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun wallpaperKeepsItsPixelsInBothThemesAndBehindSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = android.graphics.Color.rgb(48, 160, 96)
        val file = File(context.cacheDir, "editor-color.png")
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(original)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        runBlocking { SourceEditorAppearanceStore.setImage(context, Uri.fromFile(file)) }
        var open by mutableStateOf(false)
        lateinit var editorView: View
        compose.setContent {
            SourceEditorHost {
                editorView = LocalView.current
                Box(Modifier.fillMaxSize())
                if (open) SourceEditorSettingsSheet(false, {}, { open = false })
            }
        }
        fun sample(view: View): Int {
            val target = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(target)
            canvas.translate(-view.width / 2f, -view.height / 10f)
            view.draw(canvas)
            return target.getPixel(0, 0)
        }
        for (theme in listOf(SourceEditorTheme.WHITE, SourceEditorTheme.ONE_DARK)) {
            compose.runOnIdle { SourceEditorAppearanceStore.setTheme(context, theme) }
            compose.waitUntil(timeoutMillis = 15_000) {
                compose.runOnIdle { sample(editorView) == original }
            }
            compose.runOnIdle { open = true }
            val toggle = compose.onNodeWithText(context.getString(R.string.source_editor_show_image))
            toggle.assertExists()
            compose.runOnIdle {
                val dialog = WindowInspector.getGlobalWindowViews().last()
                assertTrue(dialog !== editorView.rootView)
                val outside = sample(dialog)
                assertTrue("Sheet tinted the background: ${outside.toUInt().toString(16)}",
                    android.graphics.Color.alpha(outside) == 0 || outside == original)
            }
            toggle.performClick()
            compose.runOnIdle { assertEquals(null, SourceEditorAppearanceStore.read(context).visibleImage) }
            toggle.performClick()
            compose.runOnIdle { assertTrue(SourceEditorAppearanceStore.read(context).visibleImage!!.isFile) }
            compose.onNodeWithText(context.getString(R.string.complete)).performClick()
        }
    }

    @Test
    fun rssEditorUsesTheSharedThemeAndKeepsTheDefaultSelectionKey() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = RssSource()
        val events = mutableListOf<RssSourceEditAction>()
        SourceEditorAppearanceStore.setTheme(context, SourceEditorTheme.ONE_DARK)
        compose.setContent {
            SourceEditorHost {
                RssSourceEditScreen(
                    source = source, selectedTab = 0, autoComplete = false,
                    sourceRevision = 0, fieldValueRevision = 0, focusedField = null,
                    draftTextValues = emptyMap(), editEntityMaxLine = Int.MAX_VALUE,
                    keyboardAssists = emptyList(), keyboardRowCount = 1, onAction = { events += it },
                )
            }
        }
        fun more() = compose.onNodeWithContentDescription(context.getString(R.string.more)).performClick()
        fun click(label: Int) = compose.onNodeWithText(context.getString(label)).performClick()
        more()
        click(R.string.copy_source)
        more()
        click(R.string.setting)
        compose.onNodeWithText(context.getString(R.string.source_editor_one_dark)).assertExists()
        click(R.string.source_editor_theme)
        click(R.string.text_default)
        click(R.string.complete)
        compose.runOnIdle {
            val theme = SourceEditorAppearanceStore.read(context).theme
            assertEquals("white", theme.key)
            assertEquals(R.string.text_default, theme.label)
            assertEquals(listOf(RssSourceEditAction.Copy), events)
        }
    }

    @Test
    fun groupedQuickActionsAndSettingsUseSeparateCallbacks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val events = mutableListOf<Int>()
        compose.setContent {
            var complete by remember { mutableStateOf(false) }
            SourceEditorHost {
                SourceEditorTopBar(R.string.edit_book_source, true, { true }, complete, {}) { id ->
                    events += id
                    if (id == R.id.menu_auto_complete) complete = !complete
                }
            }
        }
        fun click(res: Int) = compose.onNodeWithText(context.getString(res)).performClick()
        fun more() = compose.onNodeWithContentDescription(context.getString(R.string.more)).performClick()
        more()
        click(R.string.copy_source)
        more()
        click(R.string.source_editor_import_share)
        click(R.string.import_by_qr_code)
        more()
        click(R.string.book_info_menu_data_and_diagnostics)
        click(R.string.network_request_log)
        more()
        click(R.string.setting)
        compose.onNodeWithText(context.getString(R.string.source_editor_settings)).assertExists()
        click(R.string.auto_complete)
        click(R.string.source_editor_theme)
        for (theme in SourceEditorTheme.entries) {
            compose.onNode(hasClickAction() and hasText(context.getString(theme.label)) and
                androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected)).assertExists()
        }
        val darkOption = compose.onNode(hasClickAction() and hasText(context.getString(R.string.source_editor_vscode_dark_plus)))
        val bounds = darkOption.getUnclippedBoundsInRoot()
        val width = bounds.right - bounds.left
        assertTrue("Unexpected theme menu width: $width", width in 48.dp..140.dp)
        darkOption.performClick()
        click(R.string.complete)
        compose.runOnIdle {
            assertEquals(listOf(R.id.menu_copy_source, R.id.menu_qr_code_camera,
                R.id.menu_network_log, R.id.menu_auto_complete), events)
            assertEquals(SourceEditorTheme.VS_CODE_DARK_PLUS, SourceEditorAppearanceStore.read(context).theme)
        }
    }
}
