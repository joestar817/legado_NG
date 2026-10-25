package io.legado.app.help.config

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import io.legado.app.utils.inputStream
import kotlinx.coroutines.ensureActive
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Shared header/footer image import; dimensions are known before reader layout. */
internal object ReadHeaderImageStore {
    private const val MAX_BYTES = 32L * 1024 * 1024
    private const val MAX_PIXELS = 100_000_000L

    data class Image(val path: String, val width: Int, val height: Int)

    suspend fun install(context: Context, uri: Uri): Image {
        val root = File(context.filesDir, "read_header_images").apply { mkdirs() }
        val temporary = File.createTempFile(".header-import-", ".image", context.cacheDir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            uri.inputStream(context).getOrThrow().use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_BYTES) { "图片不能超过32MB" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            val image = inspect(temporary)
            coroutineContext.ensureActive()
            val name = digest.digest().joinToString("") { "%02x".format(it) }
            val target = File(root, name)
            if (!target.isFile) check(temporary.renameTo(target)) { "无法保存图片" }
            return image.copy(path = target.absolutePath)
        } finally {
            temporary.delete()
        }
    }

    fun inspect(file: File): Image {
        require(file.isFile && file.length() in 1..MAX_BYTES) { "图片为空或超过32MB" }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        require(options.outMimeType in setOf("image/png", "image/webp", "image/jpeg")) {
            "请选择PNG、WebP或JPG图片"
        }
        var width = options.outWidth
        var height = options.outHeight
        require(width > 0 && height > 0 && width.toLong() * height <= MAX_PIXELS) {
            "图片尺寸无效或过大"
        }
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        if (orientation in ExifInterface.ORIENTATION_TRANSPOSE..ExifInterface.ORIENTATION_ROTATE_270) {
            val previousWidth = width
            width = height
            height = previousWidth
        }
        return Image(file.absolutePath, width, height)
    }
}
