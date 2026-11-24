package io.legado.app.help.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiImageFlowTest {
    @Test
    fun editingSerializesOrderedReferenceImages() {
        val png = AiImageInput("image/png", "aW1hZ2U=")
        val jpeg = AiImageInput("image/jpeg", "cmVm")
        val request = codexImageRequest(" Change the sky ", listOf(png, jpeg))

        assertEquals("gpt-image-2", request.get("model").asString)
        assertEquals("Change the sky", request.get("prompt").asString)
        assertEquals(listOf(png.dataUrl, jpeg.dataUrl), request.getAsJsonArray("images").map {
            it.asJsonObject.get("image_url").asString
        })
        assertFalse(codexImageRequest("Draw a fox", emptyList()).has("images"))
    }

    @Test
    fun automaticEditingUsesOnlyImagesBoundByTheHost() {
        val request = JsonObject().apply { addProperty("edit_previous", true) }

        assertEquals(listOf("attachment"), codexImageReferences(request, listOf("attachment"), "previous"))
        assertEquals(listOf("previous"), codexImageReferences(request, emptyList(), "previous"))
        assertTrue(codexImageReferences(JsonObject(), emptyList(), "previous").isEmpty())
    }

    @Test
    fun attachmentIdsAndDirectFlagStayInLocalHistory() {
        val message = JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", "Make this blue")
            setChatImageAssets(listOf("image"), direct = true)
        }
        val outbound = listOf(message).prepareVisionMessages(supportsImages = false).single()
        assertFalse(outbound.isDirectImageAction())
        assertTrue(outbound.chatImageAssets().isEmpty())
        assertTrue(outbound.get("content").asString.startsWith("Make this blue"))
        assertEquals(listOf("image"), message.chatImageAssets())
        assertTrue(message.isDirectImageAction())
    }

    @Test
    fun nativeChatCodecsSendTheImageAndTextTogether() {
        val message = JsonParser.parseString("""
            {"role":"user","content":[{"type":"text","text":"Describe this"},
            {"type":"image_url","image_url":{"url":"data:image/png;base64,aW1hZ2U="}}]}
        """.trimIndent()).asJsonObject
        val setting = AiProviderSetting(
            id = "image-fixture", type = AiProviderType.OPENAI, name = "Fixture",
            apiKey = "fixture-key", baseUrl = "https://fixture.invalid/v1",
        )
        val params = AiTextParams()
        val messages = listOf(message)
        val responses = AiOpenAiResponsesCodec(setting, "gpt-fixture", "/responses")
            .request(messages, emptyList(), params, false, null).json()
        val responseParts = responses.getAsJsonArray("input")[0].asJsonObject.getAsJsonArray("content")
        assertEquals("Describe this", responseParts[0].asJsonObject.get("text").asString)
        assertEquals("input_image", responseParts[1].asJsonObject.get("type").asString)
        assertEquals("data:image/png;base64,aW1hZ2U=", responseParts[1].asJsonObject.get("image_url").asString)

        val claude = AiClaudeMessagesCodec(setting.copy(type = AiProviderType.CLAUDE), "claude-fixture")
            .request(messages, emptyList(), params, false, null).json()
        val claudeParts = claude.getAsJsonArray("messages")[0].asJsonObject.getAsJsonArray("content")
        val claudeImage = claudeParts[1].asJsonObject.getAsJsonObject("source")
        assertEquals("image/png", claudeImage.get("media_type").asString)
        assertEquals("aW1hZ2U=", claudeImage.get("data").asString)

        val gemini = AiGeminiContentCodec(setting.copy(type = AiProviderType.GOOGLE), "gemini-fixture")
            .request(messages, emptyList(), params, false, null).json()
        val geminiParts = gemini.getAsJsonArray("contents")[0].asJsonObject.getAsJsonArray("parts")
        val geminiImage = geminiParts[1].asJsonObject.getAsJsonObject("inlineData")
        assertEquals("image/png", geminiImage.get("mimeType").asString)
        assertEquals("aW1hZ2U=", geminiImage.get("data").asString)
    }

    @Test
    fun historyCanReadMessagesFromBeforeImageAttachments() {
        val old = Gson().fromJson(
            """{"id":"old","role":"user","content":"hello"}""",
            AiChatMessageSnapshot::class.java
        )
        assertNull(old.imageAssetIds)
        assertFalse(old.directImageAction)
        val saved = AiChatMessageSnapshot(
            id = "new", role = "user", content = "", imageAssetIds = listOf("image"), directImageAction = true
        )
        val restored = Gson().fromJson(Gson().toJson(saved), AiChatMessageSnapshot::class.java)
        assertEquals(listOf("image"), restored.imageAssetIds)
        assertTrue(restored.directImageAction)
    }

    private fun Request.json(): JsonObject = Buffer().use { buffer ->
        requireNotNull(body).writeTo(buffer)
        JsonParser.parseString(buffer.readUtf8()).asJsonObject
    }
}
