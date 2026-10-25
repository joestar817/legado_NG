package io.legado.app.help.book

import java.io.File
import java.io.IOException

/** 仅计量派生缓存目录；只读，不跟随目录内链接。调用方在 IO 线程执行。 */
internal fun bookCacheDirectoryBytes(folder: File, checkActive: () -> Unit = {}): Long {
    checkActive()
    if (!folder.exists()) return 0L
    if (!folder.isDirectory) throw IOException("Cannot read cache directory: ${folder.name}")
    val root = folder.canonicalFile
    fun size(file: File): Long {
        checkActive()
        if (file.canonicalFile != file.absoluteFile) return 0L
        if (file.isFile) return file.length()
        return (file.listFiles() ?: throw IOException("Cannot read cache directory: ${file.name}"))
            .sumOf(::size)
    }
    return size(root)
}
