package io.legado.app.ui.book.character

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import io.legado.app.data.appDb
import java.io.File

internal object BookCharacterPortraits {
    private const val MAX_IMAGE_BYTES = 32L * 1024 * 1024
    private const val MAX_IMAGE_PIXELS = 50_000_000L

    fun localUri(value: String?): Uri? {
        val uri = value?.takeIf(String::isNotBlank)?.let(Uri::parse) ?: return null
        return when {
            uri.scheme == "content" || uri.scheme == "file" -> uri
            uri.scheme == null && File(value).isAbsolute -> Uri.fromFile(File(value))
            else -> null
        }
    }

    fun copyFrom(context: Context, selection: String): File {
        val uri = requireNotNull(localUri(selection))
        val directory = directory(context)
        check(directory.mkdirs() || directory.isDirectory)
        val file = File.createTempFile("portrait-", ".image", directory)
        try {
            val input = checkNotNull(context.contentResolver.openInputStream(uri))
            input.use {
                file.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var remaining = MAX_IMAGE_BYTES
                    while (true) {
                        val count = input.read(buffer, 0, minOf(remaining + 1, buffer.size.toLong()).toInt())
                        if (count == -1) break
                        check(count <= remaining)
                        output.write(buffer, 0, count)
                        remaining -= count
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            check(bounds.outWidth > 0 && bounds.outHeight > 0 &&
                bounds.outWidth.toLong() * bounds.outHeight <= MAX_IMAGE_PIXELS)
            return file
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    fun discardIfUnreferenced(context: Context, value: String?) {
        val uri = localUri(value) ?: return
        val file = uri.path?.let(::File) ?: return
        if (uri.scheme != "file" || file.parentFile != directory(context) ||
            !file.name.startsWith("portrait-") || !file.name.endsWith(".image")) return
        val canonicalUri = Uri.fromFile(file).toString()
        if (value == canonicalUri && !appDb.bookCharacterDao.hasPortraitReference(canonicalUri)) {
            file.delete()
        }
    }

    private fun directory(context: Context) = File(context.filesDir, "character-portraits")
}
