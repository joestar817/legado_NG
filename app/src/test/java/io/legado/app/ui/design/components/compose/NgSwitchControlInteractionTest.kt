package io.legado.app.ui.design.components.compose

import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgLegacyThemeInput
import io.legado.app.ui.design.theme.NgThemeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
    application = InterfaceFontTestApplication::class,
)
class NgSwitchControlInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun touchingChildSwitchDoesNotTriggerDifferentParentAction() {
        val changes = mutableListOf<Pair<NgSwitchControlVariant, Boolean>>()
        val parentClicks = mutableListOf<NgSwitchControlVariant>()
        var checked by mutableStateOf(emptySet<NgSwitchControlVariant>())
        composeRule.setContent {
            SwitchTestTheme {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NgSwitchControlVariant.entries.forEach { variant ->
                        Box(
                            modifier = Modifier
                                .width(200.dp)
                                .height(56.dp)
                                .testTag("parent-$variant")
                                .clickable { parentClicks += variant },
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            NgSwitchControl(
                                checked = variant in checked,
                                onCheckedChange = { value ->
                                    changes += variant to value
                                    checked = if (value) checked + variant else checked - variant
                                },
                                modifier = switchModifier(variant),
                                variant = variant,
                            )
                        }
                    }
                }
            }
        }

        NgSwitchControlVariant.entries.forEach { variant ->
            switch(variant).performTouchInput { click() }
            switch(variant).assertIsOn()
            composeRule.runOnIdle {
                assertEquals(listOf(variant to true), changes)
                assertTrue(parentClicks.isEmpty())
                changes.clear()
            }
            composeRule.onNodeWithTag("parent-$variant", useUnmergedTree = true)
                .performTouchInput { click(Offset(8f, center.y)) }
            composeRule.runOnIdle {
                assertEquals(listOf(variant), parentClicks)
                assertTrue(changes.isEmpty())
                parentClicks.clear()
            }
        }
    }

    @Test
    fun disabledSwitchIgnoresTouchAndDrag() {
        val changes = mutableListOf<Boolean>()
        composeRule.setContent {
            SwitchTestTheme {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    NgSwitchControlVariant.entries.forEach { variant ->
                        NgSwitchControl(
                            checked = false,
                            onCheckedChange = { changes += it },
                            modifier = switchModifier(variant),
                            enabled = false,
                            variant = variant,
                        )
                    }
                }
            }
        }

        NgSwitchControlVariant.entries.forEach { variant ->
            switch(variant).assertIsNotEnabled()
            switch(variant).performTouchInput { click() }
            dragSwitch(switch(variant), LayoutDirection.Ltr)
            switch(variant).assertIsOff()
        }
        composeRule.runOnIdle { assertTrue(changes.isEmpty()) }
    }

    @Test
    fun regularAndCompactSettingsKeepSwitchAndRowActionsSeparate() {
        val changes = mutableListOf<Pair<String, Boolean>>()
        val rowClicks = mutableListOf<String>()
        var checked by mutableStateOf(emptySet<String>())
        fun change(id: String, value: Boolean) {
            changes += id to value
            checked = if (value) checked + id else checked - id
        }
        composeRule.setContent {
            SwitchTestTheme {
                Column {
                    NgSettingsItem(
                        title = "Settings",
                        modifier = Modifier.testTag("settings"),
                        trailing = NgSettingsTrailing.SWITCH,
                        checked = "settings" in checked,
                        onCheckedChange = { change("settings", it) },
                        onClick = { rowClicks += "settings" },
                    )
                    NgCompactSettingsItem(
                        title = "Compact settings",
                        modifier = Modifier.testTag("compact-settings"),
                        trailing = NgSettingsTrailing.SWITCH,
                        checked = "compact-settings" in checked,
                        onCheckedChange = { change("compact-settings", it) },
                        onClick = { rowClicks += "compact-settings" },
                    )
                }
            }
        }

        listOf("settings", "compact-settings").forEach { id ->
            val control = composeRule.onNode(
                isToggleable() and hasAnyAncestor(hasTestTag(id)),
                useUnmergedTree = true,
            )
            control.performTouchInput { click() }
            control.assertIsOn()
            composeRule.runOnIdle {
                assertEquals(listOf(id to true), changes)
                assertTrue(rowClicks.isEmpty())
                changes.clear()
            }
        }
    }

    @Test
    fun externalCheckedAndThemeUpdatesDoNotNotifyBusinessCallback() {
        var checked by mutableStateOf(false)
        var dark by mutableStateOf(false)
        val changes = mutableListOf<Boolean>()
        composeRule.setContent {
            SwitchTestTheme(dark = dark) {
                NgSwitchControl(
                    checked = checked,
                    onCheckedChange = { changes += it },
                    modifier = Modifier.testTag("switch"),
                )
            }
        }

        composeRule.onNodeWithTag("switch").assertIsOff()
        composeRule.runOnIdle { checked = true }
        composeRule.onNodeWithTag("switch").assertIsOn()
        composeRule.runOnIdle { dark = true }
        composeRule.onNodeWithTag("switch").assertIsOn()
        composeRule.runOnIdle { checked = false }
        composeRule.onNodeWithTag("switch").assertIsOff()
        composeRule.runOnIdle { assertTrue(changes.isEmpty()) }
    }

    @Test
    fun nullCallbackRetainsLocalToggleBehavior() {
        composeRule.setContent {
            SwitchTestTheme {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    NgSwitchControlVariant.entries.forEach { variant ->
                        NgSwitchControl(
                            checked = false,
                            onCheckedChange = null,
                            modifier = switchModifier(variant),
                            variant = variant,
                        )
                    }
                }
            }
        }

        NgSwitchControlVariant.entries.forEach { variant ->
            switch(variant).performTouchInput { click() }
            switch(variant).assertIsOn()
            switch(variant).performTouchInput { click() }
            switch(variant).assertIsOff()
        }
    }

    @Test
    fun slowThumbDragChangesValueOnceInBothLayoutDirections() {
        val changes = mutableListOf<Pair<String, Boolean>>()
        var checked by mutableStateOf(emptySet<String>())
        composeRule.setContent {
            SwitchTestTheme {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    LayoutDirection.entries.forEach { direction ->
                        CompositionLocalProvider(LocalLayoutDirection provides direction) {
                            NgSwitchControlVariant.entries.forEach { variant ->
                                val id = "$direction-$variant"
                                NgSwitchControl(
                                    checked = id in checked,
                                    onCheckedChange = { value ->
                                        changes += id to value
                                        checked = if (value) checked + id else checked - id
                                    },
                                    modifier = switchModifier(variant, id),
                                    variant = variant,
                                )
                            }
                        }
                    }
                }
            }
        }

        LayoutDirection.entries.forEach { direction ->
            NgSwitchControlVariant.entries.forEach { variant ->
                val id = "$direction-$variant"
                val node = composeRule.onNodeWithTag(id)
                dragSwitch(node, direction)
                node.assertIsOn()
                composeRule.runOnIdle {
                    assertEquals(listOf(id to true), changes)
                    changes.clear()
                }
                dragSwitch(node, direction, initiallyChecked = true)
                node.assertIsOff()
                composeRule.runOnIdle {
                    assertEquals(listOf(id to false), changes)
                    changes.clear()
                }
            }
        }
    }

    @Test
    fun cancellingThumbDragDoesNotChangeValueOrInvokeCallback() {
        val changes = mutableListOf<Boolean>()
        composeRule.setContent {
            SwitchTestTheme {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    NgSwitchControlVariant.entries.forEach { variant ->
                        NgSwitchControl(
                            checked = false,
                            onCheckedChange = { changes += it },
                            modifier = switchModifier(variant),
                            variant = variant,
                        )
                    }
                }
            }
        }

        NgSwitchControlVariant.entries.forEach { variant ->
            dragSwitch(switch(variant), LayoutDirection.Ltr, cancelGesture = true)
            switch(variant).assertIsOff()
        }
        composeRule.runOnIdle { assertTrue(changes.isEmpty()) }
    }

    @Test
    fun draggingThumbBackToOriginalSideDoesNotBecomeATap() {
        val changes = mutableListOf<Boolean>()
        composeRule.setContent {
            SwitchTestTheme {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    NgSwitchControlVariant.entries.forEach { variant ->
                        NgSwitchControl(
                            checked = false,
                            onCheckedChange = { changes += it },
                            modifier = switchModifier(variant),
                            variant = variant,
                        )
                    }
                }
            }
        }

        NgSwitchControlVariant.entries.forEach { variant ->
            dragSwitch(switch(variant), LayoutDirection.Ltr, returnToStart = true)
            switch(variant).assertIsOff()
        }
        composeRule.runOnIdle { assertTrue(changes.isEmpty()) }
    }

    @Test
    fun replacedCallbackReceivesNextGesture() {
        var owner by mutableStateOf("previous")
        var checked by mutableStateOf(false)
        val changes = mutableListOf<Pair<String, Boolean>>()
        composeRule.setContent {
            val currentOwner = owner
            SwitchTestTheme {
                NgSwitchControl(
                    checked = checked,
                    onCheckedChange = { value ->
                        changes += currentOwner to value
                        checked = value
                    },
                    modifier = Modifier.testTag("switch"),
                )
            }
        }

        composeRule.runOnIdle { owner = "current" }
        composeRule.onNodeWithTag("switch").performTouchInput { click() }
        composeRule.onNodeWithTag("switch").assertIsOn()
        composeRule.runOnIdle { assertEquals(listOf("current" to true), changes) }
    }

    @Test
    fun reorderedItemsKeepTheirOwnStateAndCallback() {
        var ids by mutableStateOf(listOf("first", "second"))
        var checked by mutableStateOf(setOf("first"))
        val changes = mutableListOf<Pair<String, Boolean>>()
        composeRule.setContent {
            SwitchTestTheme {
                LazyColumn {
                    items(ids, key = { it }) { id ->
                        NgSwitchControl(
                            checked = id in checked,
                            onCheckedChange = { value ->
                                changes += id to value
                                checked = if (value) checked + id else checked - id
                            },
                            modifier = Modifier.testTag(id),
                            variant = NgSwitchControlVariant.COMPACT,
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("first").assertIsOn()
        composeRule.onNodeWithTag("second").assertIsOff()
        composeRule.runOnIdle { ids = ids.reversed() }
        composeRule.onNodeWithTag("first").assertIsOn()
        composeRule.onNodeWithTag("second").assertIsOff()
        composeRule.onNodeWithTag("second").performTouchInput { click() }
        composeRule.onNodeWithTag("second").assertIsOn()
        composeRule.onNodeWithTag("first").assertIsOn()
        composeRule.runOnIdle { assertEquals(listOf("second" to true), changes) }
    }

    private fun switch(variant: NgSwitchControlVariant) =
        composeRule.onNodeWithTag(variant.name, useUnmergedTree = true)

    private fun dragSwitch(
        node: SemanticsNodeInteraction,
        direction: LayoutDirection,
        initiallyChecked: Boolean = false,
        cancelGesture: Boolean = false,
        returnToStart: Boolean = false,
    ) {
        node.performTouchInput {
            val startsOnRight = (direction == LayoutDirection.Rtl) != initiallyChecked
            val start = Offset(width * (if (startsOnRight) 0.75f else 0.25f), center.y)
            val end = Offset(width * (if (startsOnRight) 0.05f else 0.95f), center.y)
            down(start)
            repeat(10) { index ->
                val fraction = (index + 1) / 10f
                moveTo(start + (end - start) * fraction, delayMillis = 40L)
            }
            if (returnToStart) {
                repeat(10) { index ->
                    val fraction = (index + 1) / 10f
                    moveTo(end + (start - end) * fraction, delayMillis = 40L)
                }
            }
            if (cancelGesture) cancel() else up()
        }
    }
}

private fun switchModifier(
    variant: NgSwitchControlVariant,
    tag: String = variant.name,
): Modifier = Modifier.testTag(tag).then(
    if (variant == NgSwitchControlVariant.REGULAR) {
        Modifier.size(width = 52.dp, height = 36.dp)
    } else {
        Modifier
    }
)

@Composable
private fun SwitchTestTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val themedContext = remember(baseContext) {
        ContextThemeWrapper(baseContext, R.style.AppTheme_Light)
    }
    val snapshot = NgThemeResolver.resolve(
        NgLegacyThemeInput(
            primaryColor = if (dark) 0xFF202020.toInt() else 0xFFFFF1E8.toInt(),
            accentColor = 0xFFF78E66.toInt(),
            backgroundColor = if (dark) 0xFF121212.toInt() else 0xFFFFF9F5.toInt(),
            bottomBackground = if (dark) 0xFF202020.toInt() else 0xFFFFF1E8.toInt(),
            errorColor = 0xFFB3261E.toInt(),
            isDark = dark,
            isEInk = false,
        )
    )
    CompositionLocalProvider(LocalContext provides themedContext) {
        NgAppTheme(snapshot = snapshot, updateSystemBars = false, content = content)
    }
}
