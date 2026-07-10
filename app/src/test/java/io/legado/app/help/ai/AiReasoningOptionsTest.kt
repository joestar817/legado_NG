package io.legado.app.help.ai

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AiReasoningOptionsTest {

    @Test
    fun disableThinkingPrefersThinkingParamOverEffortParam() {
        val body = JsonObject().apply {
            addReasoningParams(
                params = AiTextParams(disableThinking = true),
                options = AiModelReasoningOptions(
                    thinkingParam = "thinking",
                    effortParam = "reasoning_effort"
                )
            )
        }

        assertEquals("disabled", body.getAsJsonObject("thinking").get("type").asString)
        assertFalse(body.has("reasoning_effort"))
    }

    @Test
    fun disableThinkingUsesNoneForEffortOnlyProvider() {
        val body = JsonObject().apply {
            addReasoningParams(
                params = AiTextParams(disableThinking = true),
                options = AiModelReasoningOptions(
                    effortParam = "reasoning_effort",
                    disableEffortValue = "none"
                )
            )
        }

        assertEquals("none", body.get("reasoning_effort").asString)
        assertFalse(body.has("thinking"))
    }

    @Test
    fun disableThinkingDoesNotAssumeEffortOnlyProviderUsesNone() {
        val body = JsonObject().apply {
            addReasoningParams(
                params = AiTextParams(disableThinking = true),
                options = AiModelReasoningOptions(effortParam = "reasoning_effort")
            )
        }

        assertFalse(body.has("reasoning_effort"))
    }

    @Test
    fun autoReasoningAddsNoParameters() {
        val body = JsonObject().apply {
            addReasoningParams(
                params = AiTextParams(),
                options = AiModelReasoningOptions(
                    thinkingParam = "thinking",
                    effortParam = "reasoning_effort"
                )
            )
        }

        assertFalse(body.has("thinking"))
        assertFalse(body.has("reasoning_effort"))
    }
}
