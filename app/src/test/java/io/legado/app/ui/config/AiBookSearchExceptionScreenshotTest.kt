package io.legado.app.ui.config

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import io.legado.app.help.ai.AiBookSearchAvailability
import io.legado.app.help.ai.AiBookSearchCandidate
import io.legado.app.help.ai.AiBookSearchMedia
import io.legado.app.help.ai.AiBookSearchReply
import io.legado.app.help.ai.AiBookSourceHit
import io.legado.app.help.ai.AiBookSourceLookup
import io.legado.app.help.ai.AiBookSourceStatus
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
import java.io.File
import kotlin.math.ceil

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h780dp-xhdpi", application = InterfaceFontTestApplication::class)
class AiBookSearchExceptionScreenshotTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var previewView: View

    private val theme = NgThemeResolver.resolve(NgLegacyThemeInput(
        primaryColor = 0xFFE3EBF8.toInt(), accentColor = 0xFF375B84.toInt(),
        backgroundColor = 0xFFE9EFF8.toInt(), bottomBackground = 0xFFE3EBF8.toInt(),
        errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
    ))
    private val candidate = AiBookSearchCandidate("问道红尘", "姬叉", AiBookSearchMedia.NOVEL, alias = "仙子请自重")
    private val cachedHit = AiBookSourceHit(
        candidate.title, candidate.author, "https://fixture.example/book/42", "https://fixture.example/",
        "测试书源", null, 1,
    )

    @Test
    fun blockedInputReasonsShowTheirMessagesWithoutExtraActions() {
        val cases = listOf(
            GateCase("01-mimo-unavailable", AiBookSearchAvailability.UNAVAILABLE, R.string.ai_book_search_configure_web),
            GateCase("02-web-off", AiBookSearchAvailability.OFF, R.string.ai_book_search_web_off),
            GateCase("03-external-not-configured", AiBookSearchAvailability.EXTERNAL_SETUP, R.string.ai_book_search_external_setup),
            GateCase("04-external-tools-unavailable", AiBookSearchAvailability.EXTERNAL_TOOLS, R.string.ai_book_search_external_tools),
            GateCase("05-no-model", AiBookSearchAvailability.MODEL, R.string.ai_book_search_missing_model),
            GateCase("06-chatgpt-account", AiBookSearchAvailability.ACCOUNT, R.string.ai_book_search_account_required),
            GateCase("07-provider-key", AiBookSearchAvailability.PROVIDER, R.string.ai_book_search_key_required),
            GateCase("08-model-interface", AiBookSearchAvailability.GENERATION, R.string.ai_book_search_model_config_invalid),
            GateCase("09-skill-unavailable", AiBookSearchAvailability.SKILL, R.string.ai_book_search_skill_unavailable),
        )
        assertEquals(
            AiBookSearchAvailability.entries.filterNot { it == AiBookSearchAvailability.READY }.toSet(),
            cases.map { it.availability }.toSet(),
        )
        val state = mutableStateOf(cases.first().availability)
        composeRule.setContent {
            PreviewFrame {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        AiBookSearchWebNotice(state.value)
                    }
                }
            }
        }
        for (case in cases) {
            composeRule.runOnIdle { state.value = case.availability }
            assertVisible(case.message)
            composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
            save(case.file)
        }
        composeRule.runOnIdle { state.value = AiBookSearchAvailability.READY }
        cases.forEach { composeRule.onNodeWithText(string(it.message)).assertDoesNotExist() }
    }

    @Test
    fun lookupStatesUseTheCurrentCardsAndRecoveryControls() {
        val retry = R.string.ai_book_search_retry
        val scope = R.string.ai_book_search_scope
        val alias = R.string.ai_book_search_alternate_title
        val enableMcp = R.string.ai_book_search_enable_mcp
        val cases = listOf(
            ResultCase("20-searching", AiBookSourceStatus.RUNNING, R.string.ai_book_search_searching),
            ResultCase("21-no-match", AiBookSourceStatus.COMPLETE, R.string.ai_book_search_no_match, listOf(retry, scope, alias)),
            ResultCase("22-partial-no-hit", AiBookSourceStatus.INCOMPLETE, R.string.ai_book_search_incomplete, listOf(retry, scope, alias)),
            ResultCase("23-partial-with-hit", AiBookSourceStatus.INCOMPLETE, R.string.ai_book_search_incomplete, listOf(retry, scope, alias), hit = true),
            ResultCase("24-all-failed", AiBookSourceStatus.FAILED, R.string.ai_book_search_failed, listOf(retry, scope, alias)),
            ResultCase("25-mcp-disabled", AiBookSourceStatus.DISABLED, R.string.ai_book_search_disabled, listOf(enableMcp), mcp = false),
            ResultCase("26-mcp-reenabled", AiBookSourceStatus.DISABLED, null, listOf(retry, scope, alias)),
            ResultCase("27-invalid-scope", AiBookSourceStatus.INVALID_SCOPE, R.string.ai_book_search_scope_invalid, listOf(scope)),
            ResultCase("28-no-sources-of-type", AiBookSourceStatus.NO_SOURCES, R.string.ai_book_search_no_sources, listOf(scope)),
            ResultCase("29-audio-scope-required", AiBookSourceStatus.AUDIO_SCOPE_REQUIRED, R.string.ai_book_search_audio_scope, listOf(scope)),
            ResultCase("30-same-title-other-author", AiBookSourceStatus.NAMESAKE, R.string.ai_book_search_namesake, listOf(retry, scope, alias)),
            ResultCase("31-result-needs-verification", AiBookSourceStatus.UNVERIFIED, R.string.ai_book_search_source_unverified, listOf(retry, scope, alias)),
            ResultCase("32-interrupted", AiBookSourceStatus.INTERRUPTED, R.string.ai_book_search_interrupted, listOf(retry, scope, alias)),
            ResultCase("33-interrupted-with-hit", AiBookSourceStatus.INTERRUPTED, R.string.ai_book_search_interrupted, listOf(retry, scope, alias), hit = true),
            ResultCase("34-details-unavailable", AiBookSourceStatus.OPEN_FAILED, R.string.ai_book_search_open_failed, listOf(retry, scope, alias), hit = true),
            ResultCase("35-missing-cover", AiBookSourceStatus.COMPLETE, null, hit = true),
        )
        val state = mutableStateOf(cases.first())
        val callbacks = mutableListOf<AiBookSearchAction>()
        composeRule.setContent {
            PreviewFrame {
                val case = state.value
                AiBookSearchReplyContent(AiBookSearchReply(listOf(candidate), verified = true, lookup = AiBookSourceLookup(
                    candidate.key, candidate.title, case.status, if (case.hit) listOf(cachedHit) else emptyList(),
                )), actionsEnabled = true, mcpEnabled = case.mcp, onAction = callbacks::add)
            }
        }
        for (case in cases) {
            composeRule.runOnIdle {
                state.value = case
                callbacks.clear()
            }
            case.message?.let { message ->
                val label = if (message == R.string.ai_book_search_no_match) string(message, candidate.title) else string(message)
                composeRule.onNodeWithText(label).assertIsDisplayed()
            }
            for (action in listOf(retry, scope, alias, enableMcp)) {
                val node = composeRule.onNodeWithText(string(action))
                if (action in case.actions) node.assertIsDisplayed() else node.assertDoesNotExist()
            }
            if (case.hit) {
                composeRule.onNodeWithContentDescription(
                    string(R.string.origin_show, cachedHit.originName), useUnmergedTree = true,
                ).assertIsDisplayed()
            }
            save(case.file)
            case.actions.forEach { composeRule.onNodeWithText(string(it)).performClick() }
            composeRule.runOnIdle {
                assertEquals(case.actions.map { action ->
                    when (action) {
                        retry -> AiBookSearchAction.Lookup(candidate)
                        scope -> AiBookSearchAction.Scope(candidate)
                        alias -> AiBookSearchAction.Lookup(candidate, requireNotNull(candidate.alias))
                        else -> AiBookSearchAction.EnableMcp
                    }
                }, callbacks)
            }
        }
    }

    @Test
    fun inexactCandidatesKeepTheCurrentConfirmationAndFollowUpUi() {
        val pending = AiBookSearchReply(listOf(candidate))
        val namesake = candidate.copy(author = "见秋")
        val cases = listOf(
            "10-confirmation-required" to pending,
            "11-multiple-candidates" to pending.copy(candidates = listOf(candidate, namesake)),
            "12-unknown-version" to pending.copy(candidates = listOf(candidate.copy(media = null))),
            "13-rejected-candidates" to pending.copy(rejectedKeys = listOf(candidate.key)),
            "14-title-identification-only" to pending.copy(verified = true),
        )
        val state = mutableStateOf(cases.first().second)
        val callbacks = mutableListOf<AiBookSearchAction>()
        composeRule.setContent {
            PreviewFrame {
                AiBookSearchReplyContent(state.value, actionsEnabled = true, mcpEnabled = true, onAction = callbacks::add)
            }
        }
        for ((name, reply) in cases) {
            composeRule.runOnIdle { state.value = reply }
            val action = when (name.substringBefore('-')) {
                "10", "11" -> R.string.ai_book_search_select
                "12" -> R.string.ai_book_search_reject
                "13" -> R.string.ai_book_search_more_clues
                else -> R.string.ai_book_search_search_sources
            }
            if (name.startsWith("11")) assertVisible(R.string.ai_book_search_reject_all) else assertVisible(action)
            if (name.startsWith("12")) composeRule.onNodeWithText(string(R.string.ai_book_search_select)).assertDoesNotExist()
            save(name)
        }
        composeRule.onNodeWithText(string(R.string.ai_book_search_search_sources)).performClick()
        composeRule.runOnIdle { assertEquals(listOf(AiBookSearchAction.Lookup(candidate)), callbacks) }
    }

    @Test
    fun incompleteRecognitionUsesTheCurrentNoticeAndPreservesTheQuestion() {
        val state = mutableStateOf(false)
        composeRule.setContent {
            PreviewFrame {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                    Text(
                        text = if (state.value) "你记得要找的是小说、漫画还是有声书吗？" else string(R.string.ai_book_search_unverified),
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        assertVisible(R.string.ai_book_search_unverified)
        save("15-web-verification-incomplete")
        composeRule.runOnIdle { state.value = true }
        composeRule.onNodeWithText("你记得要找的是小说、漫画还是有声书吗？").assertIsDisplayed()
        save("16-ask-for-version")
    }

    @Composable
    private fun PreviewFrame(content: @Composable () -> Unit) {
        val view = LocalView.current
        SideEffect { previewView = view }
        NgAppTheme(snapshot = theme, updateSystemBars = false) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxWidth().testTag(CAPTURE).padding(8.dp)) { content() }
            }
        }
    }

    private fun save(name: String) {
        val output = System.getenv("LEGADO_AI_BOOK_SEARCH_SCREENSHOTS") ?: return
        val folder = File(output)
        check(folder.isDirectory || folder.mkdirs())
        val bounds = composeRule.onNodeWithTag(CAPTURE).fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle {
            val image = Bitmap.createBitmap(
                ceil(bounds.width).toInt(), ceil(bounds.height).toInt(), Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(image)
            canvas.translate(-bounds.left, -bounds.top)
            previewView.draw(canvas)
            File(folder, "$name.png").outputStream().use {
                assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
    }

    private fun assertVisible(@StringRes id: Int) = composeRule.onNodeWithText(string(id)).assertIsDisplayed()

    private fun string(@StringRes id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private class GateCase(
        val file: String,
        val availability: AiBookSearchAvailability,
        @param:StringRes val message: Int,
    )

    private data class ResultCase(
        val file: String,
        val status: AiBookSourceStatus,
        @param:StringRes val message: Int?,
        val actions: List<Int> = emptyList(),
        val hit: Boolean = false,
        val mcp: Boolean = true,
    )

    private companion object { const val CAPTURE = "book-search-exception-preview" }
}
