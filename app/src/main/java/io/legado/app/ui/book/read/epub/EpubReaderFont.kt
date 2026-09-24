package io.legado.app.ui.book.read.epub

import android.content.Context
import androidx.core.net.toUri
import java.io.ByteArrayOutputStream
import java.io.File

/** Reads only the user's existing reading-font choice, never an EPUB-supplied path. */
internal fun readEpubReaderFont(context: Context, path: String): ByteArray? {
    if (path.isBlank()) return null
    val input = when {
        path.startsWith("assets://") -> context.assets.open(path.removePrefix("assets://"))
        path.startsWith("content://") -> context.contentResolver.openInputStream(path.toUri())
        else -> File(path).inputStream()
    } ?: return null
    return input.use {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = it.read(buffer)
            if (count < 0) break
            require(result.size() + count <= 64 * 1024 * 1024) { "阅读字体过大" }
            result.write(buffer, 0, count)
        }
        EpubFontData.forWebView(result.toByteArray())
    }
}
