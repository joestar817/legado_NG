package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import io.legado.app.R
import io.legado.app.help.ai.runtime.ToolExecutionReceipt
import io.legado.app.help.ai.runtime.ToolExecutionStatus
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import okio.ForwardingSource
import okio.buffer
import splitties.init.appCtx
import java.io.File
import java.io.IOException
import java.util.UUID

internal const val CODEX_IMAGE_TOOL = "generate_image"
internal const val CODEX_IMAGE_MODEL = "gpt-image-2"
private const val MAX_IMAGE_BYTES = 32 * 1024 * 1024
private const val MAX_RESPONSE_BYTES = ((MAX_IMAGE_BYTES + 2L) / 3) * 4 + 65536

internal fun AiModelSelection.isCodexChatSelection(): Boolean =
    providerId == AiDefaultProviders.CODEX_ID && modelId.isNotBlank()

internal fun AiProviderSetting?.availableCodexImageProvider(): AiProviderSetting? =
    this?.takeIf { it.isCodexSubscription() && it.enabled && !it.chatGptAccountId.isNullOrBlank() }

internal fun AiModelSelection.requireCodexImageProvider(): AiProviderSetting {
    if (!isCodexChatSelection()) throw AiChatGptException(R.string.ai_image_codex_model_required)
    return AiProviderStore.provider(providerId).availableCodexImageProvider()
        ?: throw AiChatGptException(R.string.ai_image_codex_required)
}

internal fun codexImageReferences(
    arguments: JsonObject,
    attachedImages: List<String>,
    previousImageId: String?,
): List<String> {
    if (attachedImages.isNotEmpty()) return attachedImages
    val editPrevious = arguments.get("edit_previous")?.takeIf {
        it.isJsonPrimitive && it.asJsonPrimitive.isBoolean
    }?.asBoolean == true
    if (!editPrevious) return emptyList()
    return listOf(previousImageId ?: throw AiChatGptException(R.string.ai_image_attachment_unavailable))
}

internal fun codexImageToolDefinition(hasAttachedImages: Boolean = false, hasPreviousImage: Boolean = false) =
    JsonObject().apply {
    addProperty("type", "function")
    add("function", JsonObject().apply {
        addProperty("name", CODEX_IMAGE_TOOL)
        addProperty("description", buildString {
            append("Generate or edit one image only when the user explicitly requests it. ")
            append("Write a self-contained prompt. The result appears in the reply. ")
            append("Do not invent a link or claim success without a successful result. ")
            if (hasAttachedImages) append("The user's attached images are supplied to this tool. ")
            if (hasPreviousImage) {
                append("An earlier generated image is available. Set edit_previous only when the user ")
                append("unambiguously asks to change that latest image. Ask which image when unclear.")
            }
        })
        add("parameters", JsonObject().apply {
            addProperty("type", "object")
            add("properties", JsonObject().apply {
                add("prompt", JsonObject().apply {
                    addProperty("type", "string")
                    addProperty("description", "The complete description of the desired image.")
                })
                if (hasPreviousImage && !hasAttachedImages) add("edit_previous", JsonObject().apply {
                    addProperty("type", "boolean")
                    addProperty("description", "Apply the requested change to the latest generated image.")
                })
            })
            add("required", JsonArray().apply { add(JsonPrimitive("prompt")) })
            addProperty("additionalProperties", false)
        })
    })
}

internal suspend fun AiProviderSetting.generateCodexImage(
    arguments: JsonObject,
    conversationId: String,
    turnId: String,
    receiptId: String,
    references: List<String> = emptyList(),
): JsonObject {
    val prompt = arguments.stringOrNull("prompt")?.trim()?.takeIf { it.length in 1..32000 }
        ?: return codexImageError(appCtx.getString(R.string.ai_codex_image_prompt_invalid))
    generateCodexImage(prompt, conversationId, turnId, receiptId, references)
    return codexImageSavedResult()
}

internal suspend fun AiProviderSetting.generateCodexImage(
    prompt: String,
    conversationId: String,
    turnId: String,
    receiptId: String,
    references: List<String>,
): String {
    require(isCodexSubscription())
    if (prompt.trim().length !in 1..32000) throw AiChatGptException(R.string.ai_codex_image_prompt_invalid)
    val images = AiImageAssets.loadInputs(references)
    val token = AiCodexSessions.accessToken(this, forInference = true)
    val directory = imageDirectory(conversationId)
    val imageRequestId = storageName(turnId)
    check(directory.mkdirs() || directory.isDirectory)
    // A missing response can still have consumed quota. Only a new turn may send a new image request.
    if (!File(directory, "$imageRequestId.attempt").createNewFile()) {
        throw AiChatGptException(R.string.ai_codex_image_turn_used)
    }
    val endpoint = if (images.isEmpty()) "generations" else "edits"
    val request = Request.Builder().url("$CODEX_API/images/$endpoint")
        .header("Authorization", "Bearer $token")
        .addCodexWorkspace(token)
        .header("x-codex-image-turn-id", imageRequestId)
        .post(jsonBody(codexImageRequest(prompt, images)))
        .build()
    val client = chatGptHttpClient(maxOf(timeoutSeconds, 300)).newBuilder().retryOnConnectionFailure(false).build()
    val image = try {
        client.newCall(request).readCancellableStream { response ->
            response.use {
                if (!it.isSuccessful) {
                    val error = chatGptRequestError(it.code, it.chatGptJson())
                    throw AiCodexSessions.resolveRequestError(chatGptAccountId, token, error)
                }
                readCodexImage(it.body)
            }
        }
    } catch (_: IOException) {
        currentCoroutineContext().ensureActive()
        throw AiChatGptException(R.string.ai_image_unconfirmed)
    }
    currentCoroutineContext().ensureActive()
    AiImageAssets.saveGenerated(conversationId, receiptId, image)
    return AiImageAssets.generatedId(conversationId, receiptId)
}

internal fun codexImageRequest(prompt: String, images: List<AiImageInput>): JsonObject = JsonObject().apply {
    addProperty("model", CODEX_IMAGE_MODEL)
    addProperty("prompt", prompt.trim())
    addProperty("n", 1)
    addProperty("background", "opaque")
    if (images.isNotEmpty()) add("images", JsonArray().apply {
        images.forEach { image -> add(JsonObject().apply { addProperty("image_url", image.dataUrl) }) }
    })
}

internal fun readCodexImage(body: ResponseBody): ByteString {
    if (body.contentLength() > MAX_RESPONSE_BYTES) invalidImage()
    val limited = object : ForwardingSource(body.source()) {
        private var remaining = MAX_RESPONSE_BYTES

        override fun read(sink: Buffer, byteCount: Long): Long {
            val count = super.read(sink, minOf(byteCount, remaining + 1))
            if (count > remaining) invalidImage()
            if (count >= 0) remaining -= count
            return count
        }
    }.buffer()
    val data = limited.inputStream().reader(Charsets.UTF_8).use {
        JsonParser.parseReader(it).takeIf { json -> json.isJsonObject }?.asJsonObject
    } ?: invalidImage()
    val encoded = data.arrayOrNull("data")?.firstOrNull()?.takeIf { it.isJsonObject }
        ?.asJsonObject?.stringOrNull("b64_json") ?: invalidImage()
    val image = encoded.decodeBase64() ?: invalidImage()
    if (image.size > MAX_IMAGE_BYTES || image.aiImageMimeType() == null) invalidImage()
    return image
}

private fun invalidImage(): Nothing = throw AiChatGptException(R.string.ai_codex_image_invalid)

internal fun codexImageError(message: String) = JsonObject().apply {
    addProperty("ok", false)
    addProperty("error", message)
}

internal fun codexImageSavedResult() = JsonObject().apply {
    addProperty("ok", true)
    addProperty("message", "The generated image is saved and will be displayed with this reply.")
}

internal fun ToolExecutionReceipt.isCodexImage(): Boolean =
    toolName == CODEX_IMAGE_TOOL && status == ToolExecutionStatus.SUCCEEDED

internal fun codexImageFile(conversationId: String, receiptId: String): File {
    val shared = AiImageAssets.file(AiImageAssets.generatedId(conversationId, receiptId))
    if (shared?.isFile == true) return shared
    return File(imageDirectory(conversationId), "${storageName(receiptId)}.image")
}

internal fun deleteUnusedCodexImages(conversationId: String, isReferenced: () -> Boolean) {
    val directory = imageDirectory(conversationId)
    if (directory.isDirectory && !isReferenced()) directory.deleteRecursively()
}

private fun imageDirectory(conversationId: String) =
    File(File(appCtx.filesDir, "ai-chat-images"), storageName(conversationId))

private fun storageName(value: String): String =
    UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString()
