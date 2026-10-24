package io.legado.app.help.book

import android.graphics.BitmapFactory
import io.legado.app.data.entities.Book
import io.legado.app.utils.SvgUtils
import java.io.File
import java.io.IOException

/** 已下载并解密的书籍图片。仅在 IO/图片加载线程调用；检查不删除或改写缓存。 */
internal fun getCachedBookImage(book: Book, src: String): File? {
    return getCachedBookImage(BookHelp.getImage(book, src))
}

internal fun getCachedBookImage(file: File): File? {
    if (!file.isFile || file.length() == 0L) return null
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        file.takeIf {
            (bounds.outWidth > 0 && bounds.outHeight > 0) || SvgUtils.getSize(file.path) != null
        }
    } catch (_: IOException) {
        null
    }
}
