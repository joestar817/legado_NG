package io.legado.app.ui.config

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonObject
import io.legado.app.help.ai.AgentModeEntryContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentModeEntryTemplateInstrumentedTest {

    @Test
    fun rendersEntryContextWithAndroidRegexEngine() {
        val payload = JsonObject().apply {
            addProperty("book_name", "白蛇")
        }

        assertEquals(
            "本次扫描《白蛇》",
            renderModeEntryConfirmationTemplate(
                template = "本次扫描《{{context.payload.book_name}}》",
                context = AgentModeEntryContext(
                    contextId = "book_scan",
                    title = "AI 扫书：白蛇",
                    payload = payload
                )
            )
        )
    }
}
