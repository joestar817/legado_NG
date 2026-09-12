package io.legado.app.ui.design.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.help.config.NgInterfaceFontStore
import io.legado.app.help.config.NgThemeRuntimeAssets
import io.legado.app.R
import io.legado.app.ui.config.ThemeInterfaceFontEditorSheet
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.utils.defaultSharedPreferences
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = InterfaceFontTestApplication::class)
class NgInterfaceFontComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val preferences
        get() = ApplicationProvider.getApplicationContext<Context>().defaultSharedPreferences
    private var previousChoice: String? = null

    @Before
    fun selectDistinctInterfaceFont() {
        previousChoice = preferences.getString("ngInterfaceFont.choice", null)
        preferences.edit().putString("ngInterfaceFont.choice", "monospace").commit()
    }

    @After
    fun restoreFontChoice() {
        preferences.edit().putString("ngInterfaceFont.choice", previousChoice).commit()
    }

    @Test
    fun inputValuesUseTheSameFontAsOrdinaryTextWhilePreviewKeepsItsFont() {
        composeRule.setContent {
            NgAppTheme(updateSystemBars = false) {
                Column {
                    Text("ordinary")
                    NgFormField(label = "Field", value = "field-value", onValueChange = {})
                    NgSearchBar(query = "search-value", onQueryChange = {}, hint = "Search")
                    Text("font-preview", fontFamily = FontFamily.Serif)
                }
            }
        }

        val interfaceFont = layout("ordinary").layoutInput.style.fontFamily
        assertTrue(interfaceFont != null)
        assertEquals(interfaceFont, layout("field-value").layoutInput.style.fontFamily)
        assertEquals(interfaceFont, layout("search-value").layoutInput.style.fontFamily)
        assertEquals(FontFamily.Serif, layout("font-preview").layoutInput.style.fontFamily)
        assertNotEquals(interfaceFont, layout("font-preview").layoutInput.style.fontFamily)
    }

    @Test
    fun nativeResolverPreservesWeightAndItalicForEverySystemChoiceAndPreloadedFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolver = createFontFamilyResolver(context)
        val typefaces = listOf("system", "serif", "monospace").map { choice ->
            preferences.edit().putString("ngInterfaceFont.choice", choice).commit()
            choice to checkNotNull(NgThemeRuntimeAssets.appTypeface(context))
        } + ("preloaded-file" to Typeface.createFromAsset(context.assets, "font/number.ttf"))

        typefaces.forEach { (label, base) ->
            val family = interfaceFontFamily(base)
            fun resolve(
                weight: FontWeight = FontWeight.Normal,
                style: FontStyle = FontStyle.Normal,
                synthesis: FontSynthesis = FontSynthesis.All,
            ): Typeface = resolver.resolve(family, weight, style, synthesis).value as Typeface

            if (label == "preloaded-file") {
                assertSame(label, base, resolve())
                // A single file cannot synthesize a genuine medium/light face.
                assertSame(label, base, resolve(FontWeight.Medium))
                assertSame(label, base, resolve(FontWeight.Bold, FontStyle.Italic, FontSynthesis.None))
            } else {
                val expectedFamily = when (label) {
                    "serif" -> FontFamily.Serif
                    "monospace" -> FontFamily.Monospace
                    else -> FontFamily.Default
                }
                assertEquals(label, expectedFamily, family)
                assertEquals(label, 400, resolve().weight)
                assertEquals(label, 500, resolve(FontWeight.Medium).weight)
                assertEquals(label, 300, resolve(FontWeight.Light).weight)
                // Platform family selection still works with synthetic styling disabled.
                assertEquals(label, 500, resolve(FontWeight.Medium, synthesis = FontSynthesis.None).weight)
            }
            val bold = resolve(FontWeight.Bold)
            assertTrue(label, bold.isBold)
            assertEquals(label, 700, bold.weight)
            assertFalse(label, bold.isItalic)
            assertEquals(label, 600, resolve(FontWeight.SemiBold).weight)
            assertTrue(label, resolve(style = FontStyle.Italic).isItalic)
            val boldItalic = resolve(FontWeight.Bold, FontStyle.Italic)
            assertTrue(label, boldItalic.isBold)
            assertTrue(label, boldItalic.isItalic)
            assertSame(label, bold, resolve(FontWeight.Bold))
        }
    }

    @Test
    fun clickingSelectedCustomFontReloadsItsSourceDisablesSaveAndUsesTheSamePreviewForLabelAndSample() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FontReloadFixture(context, useContentUri = true).use { fixture ->
            runBlocking {
                val initial = NgInterfaceFontStore.load(context, fixture.choice, reloadFromSource = true)
                NgInterfaceFontStore.save(context, fixture.choice, "selected-reload-font", initial.second)
            }
            val savedBefore = fixture.savedPreferences()
            val visible = mutableStateOf(true)
            val dismissed = AtomicBoolean(false)
            composeRule.setContent {
                NgAppTheme(updateSystemBars = false) {
                    if (visible.value) ThemeInterfaceFontEditorSheet {
                        dismissed.set(true)
                        visible.value = false
                    }
                }
            }
            waitForSaveEnabled(context)
            fixture.replaceSource()
            fixture.provider.entered = CountDownLatch(1)
            fixture.provider.returned = CountDownLatch(1)
            fixture.provider.gate = CountDownLatch(1)
            try {
                // Same URI/row: this is deliberately not a selection change.
                composeRule.onNodeWithText("selected-reload-font").performClick()
                composeRule.waitUntil(5_000) { fixture.provider.entered.count == 0L }
                composeRule.onNodeWithContentDescription(context.getString(R.string.save)).assertIsNotEnabled()
                assertEquals(savedBefore, fixture.savedPreferences())
                fixture.provider.gate!!.countDown()
                waitForSaveEnabled(context)
                val titleFont = layout("selected-reload-font").layoutInput.style.fontFamily
                val sampleResults = mutableListOf<TextLayoutResult>()
                composeRule.onAllNodesWithText("Aa", useUnmergedTree = true)[0]
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                        assertTrue(action(sampleResults))
                    }
                assertSame(nativeTypeface(titleFont),
                    nativeTypeface(sampleResults.single().layoutInput.style.fontFamily))
                composeRule.onNodeWithContentDescription(context.getString(R.string.save)).performClick()
                composeRule.waitUntil(5_000) { dismissed.get() }
                assertArrayEquals(fixture.updatedBytes,
                    File(checkNotNull(preferences.getString("ngInterfaceFont.file", null))).readBytes())
            } finally {
                fixture.provider.gate?.countDown()
                composeRule.runOnIdle { visible.value = false }
                composeRule.waitForIdle()
            }
        }
    }

    @Test
    fun newerSelectionWinsOverBlockedCustomReloadAndDismissWithoutSaveKeepsInstalledFont() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FontReloadFixture(context, useContentUri = true).use { fixture ->
            runBlocking {
                val initial = NgInterfaceFontStore.load(context, fixture.choice, reloadFromSource = true)
                NgInterfaceFontStore.save(context, fixture.choice, "selected-reload-font", initial.second)
            }
            val savedBefore = fixture.savedPreferences()
            val visible = mutableStateOf(true)
            composeRule.setContent {
                NgAppTheme(updateSystemBars = false) {
                    if (visible.value) ThemeInterfaceFontEditorSheet { visible.value = false }
                }
            }
            waitForSaveEnabled(context)
            fixture.replaceSource()
            fixture.provider.entered = CountDownLatch(1)
            fixture.provider.returned = CountDownLatch(1)
            fixture.provider.gate = CountDownLatch(1)
            try {
                composeRule.onNodeWithText("selected-reload-font").performClick()
                composeRule.waitUntil(5_000) { fixture.provider.entered.count == 0L }
                composeRule.onNodeWithContentDescription(context.getString(R.string.save)).assertIsNotEnabled()
                composeRule.onNodeWithText(context.getString(R.string.font_mode_system)).performClick()
                val serifLabel = context.resources.getStringArray(R.array.system_typefaces)[1]
                composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
                composeRule.onNodeWithText(serifLabel).performClick()
                waitForSaveEnabled(context)
                val latestPreview = layout(serifLabel).layoutInput.style.fontFamily
                assertEquals(Typeface.SERIF, nativeTypeface(latestPreview))
                fixture.provider.gate!!.countDown()
                composeRule.waitUntil(5_000) { fixture.provider.returned.count == 0L }
                composeRule.waitForIdle()
                assertEquals(nativeTypeface(latestPreview),
                    nativeTypeface(layout(serifLabel).layoutInput.style.fontFamily))
                assertEquals(savedBefore, fixture.savedPreferences())
                // Cancel a fully loaded preview without persisting it.
                composeRule.runOnIdle { visible.value = false }
                composeRule.waitForIdle()
                assertEquals(savedBefore, fixture.savedPreferences())
                assertArrayEquals(fixture.originalBytes,
                    File(checkNotNull(preferences.getString("ngInterfaceFont.file", null))).readBytes())
            } finally {
                fixture.provider.gate?.countDown()
                composeRule.runOnIdle { visible.value = false }
                composeRule.waitForIdle()
            }
        }
    }

    private fun nativeTypeface(family: FontFamily?): Typeface =
        createFontFamilyResolver(ApplicationProvider.getApplicationContext<Context>())
            .resolve(family).value as Typeface

    private fun waitForSaveEnabled(context: Context) {
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithContentDescription(context.getString(R.string.save)).assertIsEnabled()
                true
            }.getOrDefault(false)
        }
    }
    private fun layout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                assertTrue(action(results))
            }
        return results.single()
    }
}
