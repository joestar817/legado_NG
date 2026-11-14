package io.legado.app.ui.resource

import android.content.Intent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.design.theme.NgAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = InterfaceFontTestApplication::class)
class ResourceFontUseTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun readerStyleRequestKeepsItsBookAndCanBeConsumedOnce() {
        val intent = Intent().putExtra("bookUrl", "book://one")
        val request = ResourceReaderStyleRequest("book://one")
        request.writeTo(intent)
        assertEquals(request, ResourceReaderStyleRequest.readFrom(intent))
        assertEquals("book://one", intent.getStringExtra("bookUrl"))
        ResourceReaderStyleRequest.clear(intent)
        assertNull(ResourceReaderStyleRequest.readFrom(intent))
        ResourceReaderStyleRequest(" ").writeTo(intent)
        assertNull(ResourceReaderStyleRequest.readFrom(intent))
    }

    @Test fun readerRequestsKeepTheirResourceBookAndDestination() {
        for (destination in listOf(ResourceFontDestination.BODY, ResourceFontDestination.TITLE)) {
            val intent = Intent()
            val request = ResourceReaderFontRequest("font.example", destination, "book://one")
            request.writeTo(intent)
            assertEquals(request, ResourceReaderFontRequest.readFrom(intent))
            ResourceReaderFontRequest.clear(intent)
            assertNull(ResourceReaderFontRequest.readFrom(intent))
        }
        val invalid = Intent()
        ResourceReaderFontRequest("font.example", ResourceFontDestination.INTERFACE, "book://one").writeTo(invalid)
        assertNull(ResourceReaderFontRequest.readFrom(invalid))
        ResourceReaderFontRequest("", ResourceFontDestination.BODY, "book://one").writeTo(invalid)
        assertNull(ResourceReaderFontRequest.readFrom(invalid))
        ResourceReaderFontRequest("font.example", ResourceFontDestination.TITLE, "").writeTo(invalid)
        assertNull(ResourceReaderFontRequest.readFrom(invalid))
    }

    @Test fun useDialogDispatchesOnlyTheSelectedDestinationAndCanBeCancelled() {
        var selected: ResourceFontDestination? = null
        var dismissed = false
        composeRule.setContent {
            NgAppTheme(updateSystemBars = false) {
                ResourceFontUseDialog(onDismiss = { dismissed = true }, onSelect = { selected = it })
            }
        }
        for (destination in ResourceFontDestination.entries) {
            composeRule.onNodeWithText(destination.label).performClick()
            composeRule.runOnIdle { assertEquals(destination, selected) }
        }
        composeRule.runOnIdle { selected = null }
        composeRule.onNodeWithText("取消").performClick()
        composeRule.runOnIdle {
            assertEquals(true, dismissed)
            assertNull(selected)
        }
    }
}
