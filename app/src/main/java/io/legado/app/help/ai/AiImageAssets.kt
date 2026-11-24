package io.legado.app.help.ai

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.legado.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.buffer
import okio.sink
import okio.source
import splitties.init.appCtx
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.UUID

internal const val AI_IMAGE_ATTACHMENT_LIMIT = 4
internal const val AI_IMAGE_REFERENCE_BYTES = 8L * 1024 * 1024
private const val AI_IMAGE_BATCH_BYTES = 16L * 1024 * 1024
private const val IMAGE_IDS_KEY = "_ng_image_asset_ids"
private const val IMAGE_ATTACHMENT_MODE_KEY = "_ng_image_action"
private val assetName = Regex("[ig]-[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
private val pngHeader = "89504e470d0a1a0a".decodeHex()
private val jpegHeader = "ffd8ff".decodeHex()
private val riffHeader = "52494646".decodeHex()
private val webpHeader = "57454250".decodeHex()

internal object AiImageAssets {
    private val directory get() = File(appCtx.filesDir, "ai-image-assets")
    val works by lazy { AiImageWorksStore(directory) }

    fun generatedId(conversationId: String, receiptId: String): String =
        "g-" + UUID.nameUUIDFromBytes("$conversationId\u0000$receiptId".toByteArray(Charsets.UTF_8))

    fun file(id: String): File? = id.takeIf(assetName::matches)?.let { File(directory, "$it.image") }

    suspend fun importImage(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw AiChatGptException(R.string.ai_image_attachment_unavailable)
        stream.use { storeImport(it) }
    }

    suspend fun importExistingResult(file: File): String = withContext(Dispatchers.IO) {
        file.inputStream().use { storeImport(it) }
    }

    private fun storeImport(stream: InputStream): String {
        val id = "i-" + UUID.randomUUID()
        val target = requireNotNull(file(id))
        check(directory.mkdirs() || directory.isDirectory)
        try {
            stream.source().buffer().use { source ->
                target.sink().buffer().use { sink ->
                    val buffer = Buffer()
                    var remaining = AI_IMAGE_REFERENCE_BYTES
                    while (true) {
                        val count = source.read(buffer, minOf(remaining + 1, 8192))
                        if (count < 0) break
                        if (count > remaining) throw AiChatGptException(R.string.ai_image_attachment_format)
                        sink.write(buffer, count)
                        remaining -= count
                    }
                }
            }
            val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.path, size)
            if (target.imageMimeType() == null || size.outWidth <= 0 || size.outHeight <= 0 ||
                size.outWidth.toLong() * size.outHeight > 40_000_000
            ) {
                throw AiChatGptException(R.string.ai_image_attachment_format)
            }
        } catch (error: Exception) {
            target.delete()
            throw error
        }
        return id
    }

    fun saveGenerated(conversationId: String, receiptId: String, image: ByteString) {
        val target = requireNotNull(file(generatedId(conversationId, receiptId)))
        check(directory.mkdirs() || directory.isDirectory)
        val atomic = AtomicFile(target)
        val output = atomic.startWrite()
        try {
            image.write(output)
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
    }

    fun discardUnsent(id: String) {
        if (id.startsWith("i-")) file(id)?.delete()
    }

    fun loadInputs(ids: List<String>): List<AiImageInput> {
        if (ids.size > AI_IMAGE_ATTACHMENT_LIMIT) {
            throw AiChatGptException(R.string.ai_image_attachment_format)
        }
        var remaining = AI_IMAGE_BATCH_BYTES
        return ids.map { id ->
            val source = file(id)?.takeIf(File::isFile)
                ?: throw AiChatGptException(R.string.ai_image_attachment_unavailable)
            val size = source.length()
            if (size !in 1..minOf(remaining, AI_IMAGE_REFERENCE_BYTES)) {
                throw AiChatGptException(R.string.ai_image_attachment_format)
            }
            remaining -= size
            val bytes = source.source().buffer().use { input ->
                val buffer = Buffer()
                while (buffer.size <= size) {
                    if (input.read(buffer, minOf(size + 1 - buffer.size, 8192)) < 0) break
                    if (buffer.size > size) throw AiChatGptException(R.string.ai_image_attachment_format)
                }
                buffer.readByteString()
            }
            val mime = bytes.aiImageMimeType() ?: throw AiChatGptException(R.string.ai_image_attachment_format)
            AiImageInput(mime, bytes.base64())
        }
    }
}

internal data class AiImageWorksSnapshot(val visible: List<String>, val removed: List<String>)

internal class AiImageWorksStore(private val directory: File) {
    private val visibility = AtomicFile(File(directory, "removed-works.txt"))

    @Synchronized
    fun read(): AiImageWorksSnapshot = snapshot(listWorks(), readRemoved())

    @Synchronized
    fun setRemoved(ids: Set<String>, removed: Boolean): AiImageWorksSnapshot {
        val works = listWorks()
        val selected = ids.intersect(works.toSet())
        val previous = readRemoved()
        val updated = if (removed) previous + selected else previous - selected
        if (updated != previous) {
            val output = visibility.startWrite()
            try {
                output.write(updated.joinToString("\n").toByteArray(Charsets.UTF_8))
                visibility.finishWrite(output)
            } catch (error: Exception) {
                visibility.failWrite(output)
                throw error
            }
            check(readRemoved() == updated) { "Could not update the works list" }
        }
        return snapshot(works, updated)
    }

    private fun listWorks(): List<String> = directory.listFiles { file ->
        file.isFile && file.name.startsWith("g-") && file.extension == "image"
    }.orEmpty().sortedByDescending(File::lastModified).map { it.nameWithoutExtension }
        .filter(assetName::matches)

    private fun readRemoved(): Set<String> {
        val bytes = try {
            visibility.readFully()
        } catch (error: FileNotFoundException) {
            if (visibility.baseFile.exists()) throw error
            return emptySet()
        }
        return bytes.toString(Charsets.UTF_8).lineSequence()
            .filter { it.startsWith("g-") && assetName.matches(it) }
            .toSet()
    }

    private fun snapshot(works: List<String>, removed: Set<String>): AiImageWorksSnapshot {
        val (hidden, visible) = works.partition(removed::contains)
        return AiImageWorksSnapshot(visible, hidden)
    }
}

internal data class AiImageInput(val mime: String, val base64: String) {
    val dataUrl get() = "data:$mime;base64,$base64"
}

internal fun ByteString.aiImageMimeType(): String? = when {
    startsWith(pngHeader) -> "image/png"
    startsWith(jpegHeader) -> "image/jpeg"
    startsWith(riffHeader) && rangeEquals(8, webpHeader, 0, webpHeader.size) -> "image/webp"
    else -> null
}

internal fun File.imageMimeType(): String? = source().buffer().use { input ->
    input.readByteString(minOf(length(), 12)).aiImageMimeType()
}

internal fun imageFileExtension(mime: String) = if (mime == "image/jpeg") "jpg" else mime.substringAfter('/')

internal fun JsonObject.setChatImageAssets(ids: List<String>, direct: Boolean = false) {
    if (ids.isNotEmpty()) add(IMAGE_IDS_KEY, JsonArray().apply { ids.forEach { add(it) } })
    if (direct) addProperty(IMAGE_ATTACHMENT_MODE_KEY, true)
}

internal fun JsonObject.chatImageAssets(): List<String> = arrayOrNull(IMAGE_IDS_KEY)
    ?.mapNotNull { it.takeIf { element -> element.isJsonPrimitive && element.asJsonPrimitive.isString }?.asString }
    .orEmpty()

internal fun JsonObject.isDirectImageAction(): Boolean =
    get(IMAGE_ATTACHMENT_MODE_KEY)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean == true

internal fun JsonObject.stripChatImageAssets() {
    remove(IMAGE_IDS_KEY)
    remove(IMAGE_ATTACHMENT_MODE_KEY)
}

// The only binary expansion is on a copy at the provider boundary. Keep the latest
// image-bearing turn in the vision payload, including on follow-up text turns.
internal fun List<JsonObject>.prepareVisionMessages(supportsImages: Boolean): List<JsonObject> {
    val lastUser = indexOfLast { it.stringOrNull("role") == "user" }
    val latest = indexOfLast { it.stringOrNull("role") == "user" && it.chatImageAssets().isNotEmpty() }
    val inputs = if (supportsImages && latest >= 0) {
        try {
            AiImageAssets.loadInputs(this[latest].chatImageAssets())
        } catch (error: AiChatGptException) {
            if (latest == lastUser) throw error
            emptyList()
        }
    } else emptyList()
    return mapIndexed { index, message ->
        if (message.chatImageAssets().isEmpty() && !message.isDirectImageAction()) return@mapIndexed message
        message.deepCopy().apply {
            val hasImages = chatImageAssets().isNotEmpty()
            stripChatImageAssets()
            if (!hasImages) return@apply
            val text = get("content").contentText()
            if (index == latest && inputs.isNotEmpty()) {
                add("content", JsonArray().apply {
                    if (text.isNotBlank()) add(JsonObject().apply {
                        addProperty("type", "text")
                        addProperty("text", text)
                    })
                    inputs.forEach { image ->
                        add(JsonObject().apply {
                            addProperty("type", "image_url")
                            add("image_url", JsonObject().apply { addProperty("url", image.dataUrl) })
                        })
                    }
                })
            } else if (!supportsImages && index == lastUser) {
                addProperty("content", text + "\n\n[The user attached an image. The image pixels are not " +
                    "available to this model. Do not describe the image without seeing it.]")
            } else if (text.isBlank()) {
                addProperty("content", "[Image attachment]")
            }
        }
    }
}

internal fun JsonObject.chatVisionInputs(): List<AiImageInput> =
    arrayOrNull("content")?.mapNotNull { part ->
        val obj = part.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
        if (obj.stringOrNull("type") != "image_url") return@mapNotNull null
        val url = obj.objectOrNull("image_url")?.stringOrNull("url") ?: return@mapNotNull null
        val boundary = url.indexOf(";base64,")
        if (!url.startsWith("data:image/") || boundary < 0) return@mapNotNull null
        AiImageInput(url.substring(5, boundary), url.substring(boundary + 8))
    }.orEmpty()
