package io.legado.app.ui.book.read.config

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.help.config.ReadHighlightRule
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgLegacyThemeInput
import io.legado.app.ui.design.theme.NgThemeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", application = InterfaceFontTestApplication::class)
class ReadHighlightOverviewUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun currentPresetRowOpensAssociationSettings() {
        var opened = 0
        composeRule.setContent {
            NgAppTheme(snapshot = testTheme(), updateSystemBars = false) {
                HighlightCurrentPresetRow(
                    presetName = "Preset A", modeLabel = "Use global", contentColor = Color.Black,
                    onClick = { opened++ },
                )
            }
        }
        composeRule.onNodeWithText("Preset A").assertExists()
        composeRule.onNodeWithText("Current preset").performClick()
        composeRule.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun globalSwitchAndRuleEditorRemainSeparateForLinkedPreset() {
        var globallyEnabled by mutableStateOf(false)
        var onlyThisBook by mutableStateOf(false)
        var editCount = 0
        val rule = ReadHighlightRule(id = "linked", name = "Linked", pattern = "linked", enabled = false)
        composeRule.setContent {
            NgAppTheme(snapshot = testTheme(), updateSystemBars = false) {
                HighlightOverviewRuleRow(
                    rule = rule.copy(enabled = globallyEnabled), usesLinkedRules = true,
                    usedByCurrent = true, onlyThisBook = onlyThisBook,
                    contentColor = Color.Black, accentColor = Color(0xFF008577),
                    onEdit = { editCount++ }, onEnabledChanged = { globallyEnabled = it },
                )
            }
        }
        composeRule.onNodeWithText("Used by this preset").assertExists()
        composeRule.onNodeWithContentDescription("Linked global default").assertIsOff().performClick()
        composeRule.runOnIdle {
            assertTrue(globallyEnabled)
            assertEquals(0, editCount)
        }
        composeRule.onNodeWithText("Linked").performClick()
        composeRule.runOnIdle {
            assertEquals(1, editCount)
            onlyThisBook = true
        }
        composeRule.onNodeWithText("Used by this book").assertExists()
    }

    @Test
    fun moreButtonOpensOnlyActionsForTheChosenRule() {
        var canRestore by mutableStateOf(true)
        var globallyEnabled by mutableStateOf(false)
        var editCount = 0
        val actions = mutableListOf<HighlightRuleMenuAction>()
        val rule = ReadHighlightRule(id = "rule", name = "Rule", pattern = "rule", enabled = false)
        composeRule.setContent {
            NgAppTheme(snapshot = testTheme(), updateSystemBars = false) {
                HighlightManagedRuleRow(
                    rule = rule.copy(enabled = globallyEnabled), usesLinkedRules = false, usedByCurrent = false,
                    onlyThisBook = false, canRestore = canRestore,
                    contentColor = Color.Black, accentColor = Color(0xFF008577),
                    onEdit = { editCount++ }, onEnabledChanged = { globallyEnabled = it },
                    onAction = { actions.add(it) },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Rule global default").assertIsOff().performClick()
        composeRule.onNodeWithText("Rule").performClick()
        composeRule.runOnIdle {
            assertTrue(globallyEnabled)
            assertEquals(1, editCount)
            assertTrue(actions.isEmpty())
        }
        composeRule.onNodeWithContentDescription("Rule more options").performClick()
        composeRule.onNodeWithText("Restore default").assertExists()
        composeRule.onNodeWithText("Sort").assertDoesNotExist()
        composeRule.onNodeWithText("Batch export").assertDoesNotExist()
        composeRule.onNodeWithText("Copy").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(HighlightRuleMenuAction.COPY), actions)
            canRestore = false
        }
        composeRule.onNodeWithContentDescription("Rule more options").performClick()
        composeRule.onNodeWithText("Restore default").assertDoesNotExist()
        composeRule.onNodeWithText("Export").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(HighlightRuleMenuAction.COPY, HighlightRuleMenuAction.EXPORT), actions)
        }
    }

    @Test
    fun headerAndBatchActionsOperateInTheSameSelectionState() {
        var selecting by mutableStateOf(false)
        var sorting by mutableStateOf(false)
        var selectedCount by mutableStateOf(0)
        var exported = 0
        var deleted = 0
        composeRule.setContent {
            NgAppTheme(snapshot = testTheme(), updateSystemBars = false) {
                Column {
                    HighlightLibraryHeader(
                        selecting = selecting, sorting = sorting, selectedCount = selectedCount,
                        hasRules = true, canSort = true, contentColor = Color.Black, accentColor = Color(0xFF008577),
                        onSelect = { selecting = true }, onCancelSelection = { selecting = false; selectedCount = 0 },
                        onSort = { sorting = true }, onFinishSorting = { sorting = false },
                    )
                    if (selecting) {
                        HighlightBatchActions(
                            selectedCount = selectedCount, allSelected = selectedCount == 2,
                            accentColor = Color(0xFF008577), onToggleAll = { selectedCount = 2 },
                            onExport = { exported++ }, onDelete = { deleted++ },
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText("Sort").performClick()
        composeRule.onNodeWithText("Finished").performClick()
        composeRule.onNodeWithText("Select").performClick()
        composeRule.onNodeWithText("Export").assertIsNotEnabled()
        composeRule.onNodeWithText("Delete").assertIsNotEnabled()
        composeRule.onNodeWithText("Select all").performClick()
        composeRule.onNodeWithText("Export").performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.runOnIdle {
            assertEquals(2, selectedCount)
            assertEquals(1, exported)
            assertEquals(1, deleted)
        }
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Select").assertExists()
    }

    @Test
    fun presetMenuOpensRulesAndRestoresSavedLinks() {
        val rules = listOf(ReadHighlightRule(id = "linked", name = "Linked", pattern = "linked", enabled = false))
        var ids by mutableStateOf(emptyList<String>())
        var usesLinked by mutableStateOf(false)
        var opened = 0
        var activated = 0
        composeRule.setContent {
            NgAppTheme(snapshot = testTheme(), updateSystemBars = false) {
                PresetHighlightModeSetting(
                    overview = readHighlightOverview(rules, ids, usesLinked), contentColor = Color.Black,
                    onFollowGlobal = { usesLinked = false },
                    onActivateLinked = { usesLinked = true; activated++ },
                    onOpenRules = { opened++ },
                )
            }
        }
        fun option(label: String) = composeRule.onNode(
            hasText(label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected),
        )
        composeRule.onNodeWithText("Highlight rules").performClick()
        option("Use global").assertIsSelected()
        option("Linked rules").performClick()
        composeRule.runOnIdle {
            assertFalse(usesLinked)
            assertEquals(1, opened)
            assertEquals(0, activated)
            ids = listOf("linked")
        }
        composeRule.onNodeWithText("Highlight rules").performClick()
        option("Linked rules").performClick()
        composeRule.runOnIdle {
            assertTrue(usesLinked)
            assertEquals(listOf("linked"), ids)
            assertEquals(2, opened)
            assertEquals(1, activated)
        }
        composeRule.onNodeWithText("1 linked").assertExists()
        composeRule.onNodeWithText("Highlight rules").performClick()
        option("Linked rules").assertIsSelected()
        option("Use global").performClick()
        composeRule.runOnIdle {
            assertFalse(usesLinked)
            assertEquals(listOf("linked"), ids)
            assertEquals(2, opened)
        }
    }

    @Test
    fun associationPageShowsRulesImmediatelyAndKeepsListAfterLastRemoval() {
        val rules = listOf(ReadHighlightRule(id = "linked", name = "Linked", pattern = "linked", enabled = false))
        var ids by mutableStateOf(emptyList<String>())
        var usesLinked by mutableStateOf(false)
        var backCount = 0
        composeRule.setContent {
            NgAppTheme(snapshot = testTheme(), updateSystemBars = false) {
                HighlightAssociationPage(
                    overview = readHighlightOverview(rules, ids, usesLinked), rules = rules,
                    presetName = "Preset A", contentColor = Color.Black,
                    onBack = { backCount++ },
                    onRuleChanged = { id, checked ->
                        ids = if (checked) (ids + id).distinct() else ids - id
                        usesLinked = ids.isNotEmpty()
                    },
                )
            }
        }
        composeRule.onNodeWithText("Preset A").assertExists()
        composeRule.onNodeWithText("Select a rule to start linking; the global default applies until then").assertDoesNotExist()
        composeRule.onNodeWithText("Linked").assertExists().performClick()
        composeRule.runOnIdle {
            assertTrue(usesLinked)
            assertEquals(listOf("linked"), ids)
        }
        composeRule.onNodeWithText("1 linked").assertExists()
        composeRule.onNodeWithText("Linked").performClick()
        composeRule.runOnIdle {
            assertFalse(usesLinked)
            assertTrue(ids.isEmpty())
        }
        composeRule.onNodeWithText("Linked").assertExists()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.runOnIdle { assertEquals(1, backCount) }
    }

    private fun testTheme() = NgThemeResolver.resolve(NgLegacyThemeInput(
        primaryColor = 0xFFFFF1E8.toInt(), accentColor = 0xFF008577.toInt(),
        backgroundColor = 0xFFFFF9F5.toInt(), bottomBackground = 0xFFFFF1E8.toInt(),
        errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
    ))
}
