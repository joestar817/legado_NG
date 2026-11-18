package io.legado.app.ui.book.read.config

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.view.allViews
import androidx.core.view.drawToBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import io.legado.app.help.config.ParagraphCommentBubble
import io.legado.app.help.config.ParagraphCommentBubbleSettings
import io.legado.app.help.config.ParagraphCommentIconConfig
import io.legado.app.help.config.ParagraphCommentIconSettings
import io.legado.app.help.config.ParagraphCommentIconSource
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgLegacyThemeInput
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.utils.defaultSharedPreferences
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", application = InterfaceFontTestApplication::class)
class ParagraphBubbleDrawerTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun openDrawer() {
        val theme = NgThemeResolver.resolve(NgLegacyThemeInput(
            primaryColor = 0xFFFFF1E8.toInt(), accentColor = 0xFF008577.toInt(),
            backgroundColor = 0xFFFFF9F5.toInt(), bottomBackground = 0xFFFFF1E8.toInt(),
            errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
        ))
        compose.setContent {
            NgAppTheme(snapshot = theme, updateSystemBars = false) { ReadParagraphCommentIconSettings {} }
        }
    }

    @Test
    @OptIn(InternalComposeUiApi::class)
    fun bubblePreviewsRenderWith99() {
        // Encode the count as a pixel color so the test reads the real preview without OCR.
        val template = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 54 39"><rect width="54" height="39" fill="#0000{n}"/></svg>"""
        val config = ParagraphCommentIconConfig(source = ParagraphCommentIconSource.CUSTOM, customSvg = template)
        compose.setContent {
            ParagraphIconPicture(config, android.graphics.Color.WHITE, Modifier.size(54.dp, 39.dp).testTag("preview"))
        }
        val node = compose.onNodeWithTag("preview").fetchSemanticsNode()
        compose.waitUntil(5000) {
            compose.runOnIdle {
                val center = node.boundsInRoot.center
                val bitmap = (node.root as ViewRootForTest).view.drawToBitmap()
                bitmap.getPixel(center.x.toInt(), center.y.toInt()) == 0xff000099.toInt()
            }
        }
    }

    @Test
    @OptIn(InternalComposeUiApi::class)
    @Config(qualifiers = "zh-rCN-w411dp-h891dp-xxhdpi")
    fun ticklessSizeSliderKeepsFivePercentSteps() {
        openDrawer()
        compose.assertDrawerTextStyle(context.getString(R.string.paragraph_icon_title), 17)
        compose.assertDrawerTextStyle("开启图标替换", 14)
        compose.assertDrawerTextStyle(context.getString(R.string.paragraph_icon_color), 15)
        val slider = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        slider.assertRangeInfoEquals(ProgressBarRangeInfo(100f, 70f..150f, 15))
        val node = slider.fetchSemanticsNode()
        compose.runOnIdle {
            val image = (node.root as ViewRootForTest).view.drawToBitmap()
            val bounds = node.boundsInRoot
            val x = bounds.center.x.toInt()
            val y = bounds.center.y.toInt()
            val width = bounds.width.toInt()
            val filled = image.getPixel(x - width / 4, y)
            val unfilled = image.getPixel(x, y)
            assertNotEquals(filled, unfilled)
            assertTrue((x - width / 3..x - width / 4).all { image.getPixel(it, y) == filled })
            assertTrue((x..x + width / 3).all { image.getPixel(it, y) == unfilled })
        }
        for ((target, expected) in listOf(70f to 70, 108f to 110, 150f to 150)) {
            slider.performSemanticsAction(SemanticsActions.SetProgress) { it(target) }
            compose.onNodeWithText("$expected%").assertIsDisplayed()
            assertEquals(expected, ParagraphCommentBubbleSettings.load().appearance.size)
        }
    }

    @Test fun clipboardImportsAnEmbeddedWebpWithCommunityCountTokens() {
        val webp = "UklGRh4AAABXRUJQVlA4TBEAAAAvAUAAAAdQyAIVrP+BiOh/AAA="
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 16">
            <image width="20" height="16" href="data:image/webp;base64,$webp"/>
            <text x="2" y="12" fill="{t}">{n}</text></svg>"""
        val before = ParagraphCommentBubbleSettings.load()
        openDrawer()
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("bubble", svg))
        compose.onNodeWithContentDescription(context.getString(R.string.more)).performClick()
        compose.onNodeWithText(context.getString(R.string.paragraph_icon_clipboard)).performClick()
        compose.waitUntil(5_000) { ParagraphCommentBubbleSettings.load().bubbles.size == before.bubbles.size + 1 }
        val after = ParagraphCommentBubbleSettings.load()
        assertEquals(svg, after.bubbles.single { it.id == after.selectedId }.svg)
        assertEquals(before.enabled, after.enabled)
    }

    @Test fun longLibraryScrollsIndependentlyAndCanBeManagedWhileOff() {
        val initial = ParagraphCommentBubbleSettings.load()
        val extras = (1..20).map { ParagraphCommentBubble("extra-$it", "$it.svg", initial.bubbles.first().svg) }
        ParagraphCommentBubbleSettings.save(initial, initial.copy(bubbles = initial.bubbles + extras))
        openDrawer()
        val toggle = compose.onNodeWithText(context.getString(R.string.paragraph_icon_enabled))
        val footer = compose.onNodeWithText(context.getString(R.string.paragraph_icon_color))
        val toggleBounds = toggle.assertIsDisplayed().getUnclippedBoundsInRoot()
        val footerBounds = footer.assertIsDisplayed().getUnclippedBoundsInRoot()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(22)
        assertEquals(toggleBounds, toggle.getUnclippedBoundsInRoot())
        assertEquals(footerBounds, footer.getUnclippedBoundsInRoot())
        compose.onNodeWithContentDescription(context.getString(R.string.paragraph_icon_edit_named, "20.svg")).assertIsEnabled()
        compose.onNodeWithText("20.svg").performClick()
        assertEquals("extra-20", ParagraphCommentBubbleSettings.load().selectedId)
        compose.onNodeWithContentDescription(context.getString(R.string.paragraph_icon_delete_named, "20.svg")).performClick()
        compose.onNode(hasText(context.getString(R.string.delete)) and hasClickAction()).performClick()
        compose.runOnIdle {
            assertFalse(ParagraphCommentBubbleSettings.load().enabled)
            assertFalse(ParagraphCommentBubbleSettings.load().bubbles.any { it.id == "extra-20" })
            assertEquals(ParagraphCommentIconSource.ORIGINAL, ParagraphCommentIconSettings.load().source)
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w700dp-h360dp-land-xhdpi")
    fun shortWindowKeepsTheLastBubbleAndAppearanceReachable() {
        val initial = ParagraphCommentBubbleSettings.load()
        val extras = (1..15).map { ParagraphCommentBubble("extra-$it", "$it.svg", initial.bubbles.first().svg) }
        ParagraphCommentBubbleSettings.save(initial, initial.copy(bubbles = initial.bubbles + extras))
        openDrawer()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(17)
        compose.onNodeWithText("15.svg").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.paragraph_icon_color)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.paragraph_icon_title)).assertIsDisplayed()
    }

    @OptIn(InternalComposeUiApi::class, ExperimentalComposeUiApi::class)
    @Test fun invalidNetworkAddressStaysEditableAndCancelLeavesLibraryUntouched() {
        val before = ParagraphCommentBubbleSettings.load()
        openDrawer()
        compose.onNodeWithContentDescription(context.getString(R.string.more)).performClick()
        val network = compose.onNodeWithText(context.getString(R.string.paragraph_icon_network)).assertIsDisplayed()
        // Robolectric pumps the Activity layout; the focused field belongs to a separate Dialog window.
        fun <T> withDialogNode(matcher: SemanticsMatcher, action: (SemanticsNode) -> T): T {
            repeat(3) {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
                compose.mainClock.advanceTimeBy(100)
            }
            return compose.runOnUiThread {
                val root = ShadowDialog.getLatestDialog().window!!.decorView.allViews.filterIsInstance<ViewRootForTest>().single()
                root.measureAndLayoutForTest()
                val nodes = root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
                val node = checkNotNull(nodes.singleOrNull(matcher::matches)) { "Expected ${matcher.description}, found ${nodes.map { it.config }}" }
                action(node)
            }
        }
        compose.mainClock.autoAdvance = false
        network.performClick()
        withDialogNode(hasSetTextAction()) { it.config[SemanticsActions.SetText].action!!(AnnotatedString("invalid")) }
        withDialogNode(hasText(context.getString(R.string.paragraph_icon_import_action)) and hasClickAction()) {
            it.config[SemanticsActions.OnClick].action!!()
        }
        withDialogNode(hasText("HTTP", substring = true)) { assertTrue(it.size.height > 0) }
        withDialogNode(hasSetTextAction() and hasText("invalid")) {
            it.config[SemanticsActions.SetText].action!!(AnnotatedString("https://example.org/bubble.svg"))
        }
        withDialogNode(hasSetTextAction() and hasText("https://example.org/bubble.svg")) { assertTrue(it.size.width > 0) }
        withDialogNode(hasText(context.getString(R.string.cancel)) and hasClickAction()) {
            it.config[SemanticsActions.OnClick].action!!()
        }
        compose.mainClock.advanceTimeBy(300)
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(before, ParagraphCommentBubbleSettings.load()) }
    }

    @Test fun restoreConfirmsBeforePurgingAndPersistsOnlyTheBuiltins() {
        val initial = ParagraphCommentBubbleSettings.load()
        val original = initial.bubbles.first()
        val custom = original.copy(id = "imported")
        val appearance = initial.appearance.copy(size = 135, followTextColor = true)
        val before = initial.copy(bubbles = listOf(original, custom), selectedId = custom.id, enabled = true, appearance = appearance)
        ParagraphCommentBubbleSettings.save(initial, before)
        openDrawer()
        fun showRestore() {
            compose.onNodeWithContentDescription(context.getString(R.string.more)).performClick()
            compose.onNodeWithText(context.getString(R.string.restore_default)).performClick()
            compose.onNodeWithText(context.getString(R.string.paragraph_icon_restore_confirm)).assertIsDisplayed()
        }
        showRestore()
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        compose.runOnIdle { assertEquals(before, ParagraphCommentBubbleSettings.load()) }
        showRestore()
        compose.onNode(hasText(context.getString(R.string.restore_default)) and hasClickAction()).performClick()
        compose.runOnIdle {
            val restored = ParagraphCommentBubbleSettings.load()
            assertEquals(initial.bubbles, restored.bubbles)
            assertTrue(restored.enabled)
            assertEquals(original.id, restored.selectedId)
            assertEquals(appearance, restored.appearance)
            assertEquals(original.svg, ParagraphCommentIconSettings.load().customSvg)
            assertFalse(context.defaultSharedPreferences.all.keys.any { it.startsWith("paragraphCommentBubbles.imported.") })
        }
    }
}
