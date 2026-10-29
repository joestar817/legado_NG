package io.legado.app.help.book

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.exoplayer.AudioDownloadCache
import java.io.File
import java.io.IOException
import java.util.Properties
import java.util.UUID

internal data class ChapterCacheFileMove(
    val source: File,
    val target: File,
    val replacement: ByteArray? = null,
)

/** Caller must exclude chapter-cache writers until the database commit has finished. */
internal fun <T> withReorderedChapterCache(
    book: Book,
    oldChapters: List<BookChapter>,
    newChapters: List<BookChapter>,
    commit: () -> T,
): T {
    val oldByUrl = oldChapters.associateBy(BookChapter::url)
    val newByUrl = newChapters.associateBy(BookChapter::url)
    require(oldByUrl.size == oldChapters.size && newByUrl.size == newChapters.size)
    require(oldByUrl.keys == newByUrl.keys)
    require(oldChapters.all { it.bookUrl == book.bookUrl })
    require(newChapters.all { it.bookUrl == book.bookUrl })
    require(oldChapters.map(BookChapter::index).toSet().size == oldChapters.size)
    require(newChapters.map(BookChapter::index).toSet().size == newChapters.size)
    val directory = File(BookHelp.cachePath, book.getFolderName())
    val moves = buildList {
        oldChapters.forEach { old ->
            val new = newByUrl.getValue(old.url)
            val oldName = old.getFileName()
            val newName = new.getFileName()
            if (oldName != newName) {
                // AtomicFile's companion files belong to the same edited chapter.
                listOf("", ".epub-positions.gz", ".epub-positions.gz.bak", ".epub-positions.gz.new")
                    .forEach { suffix ->
                        add(ChapterCacheFileMove(File(directory, oldName + suffix), File(directory, newName + suffix)))
                    }
            }
        }
        addAll(AudioDownloadCache.chapterReorderMoves(book, oldChapters, newChapters))
    }
    return withReorderedCacheFiles(directory, moves, commit)
}

/**
 * Stages every original before publishing any target, including stale targets with no source.
 * A failed callback restores the exact original file set. This is not a process-crash transaction:
 * if restoring a file fails, backups and recovery.properties remain at the reported directory.
 */
internal fun <T> withReorderedCacheFiles(
    cacheDirectory: File,
    moves: List<ChapterCacheFileMove>,
    commit: () -> T,
): T {
    val root = cacheDirectory.canonicalFile
    fun checked(file: File): File {
        val canonical = file.canonicalFile
        require(generateSequence(canonical.parentFile) { it.parentFile }.any { it == root }) {
            "Chapter cache path is outside $root: $file"
        }
        require(!canonical.exists() || canonical.isFile) { "Not a cache file: $canonical" }
        return canonical
    }
    val plan = moves.map { it.copy(source = checked(it.source), target = checked(it.target)) }
        .filter { it.source != it.target || it.replacement != null }
    require(plan.map { it.source }.toSet().size == plan.size) { "Duplicate cache sources" }
    require(plan.map { it.target }.toSet().size == plan.size) { "Duplicate cache targets" }
    val paths = plan.flatMap { listOf(it.source, it.target) }.distinct()
    val originals = paths.filter(File::exists)
    if (originals.isEmpty()) return commit()

    val staging = File(root, ".chapter-cache-reorder-${UUID.randomUUID()}")
    if (!staging.mkdir()) throw IOException("无法暂存章节缓存：$staging")
    val ownedFiles = mutableListOf<File>()
    val backups = originals.mapIndexed { index, file ->
        file to File(staging, "$index.original").also(ownedFiles::add)
    }.toMap()
    val modifiedTimes = originals.associateWith(File::lastModified)
    val recovery = File(staging, "recovery.properties").also(ownedFiles::add)

    // Only this operation's exact temporary files may be cleaned. Never recursively delete.
    fun cleanup() {
        ownedFiles.forEach { runCatching { it.delete() } }
        runCatching { staging.delete() }
    }

    var published = false
    val result = try {
        Properties().apply {
            setProperty("root", root.path)
            paths.forEachIndexed { index, path ->
                setProperty("path.$index", path.relativeTo(root).path)
                setProperty("backup.$index", backups[path]?.name.orEmpty())
            }
        }.also { properties -> recovery.outputStream().use { properties.store(it, "Original chapter cache files") } }
        backups.forEach { (original, backup) -> original.copyTo(backup) }
        val outputs = plan.mapIndexedNotNull { index, move ->
            val backup = backups[move.source] ?: return@mapIndexedNotNull null
            val output = File(staging, "$index.output").also(ownedFiles::add)
            if (move.replacement == null) backup.copyTo(output) else output.writeBytes(move.replacement)
            output.setLastModified(modifiedTimes.getValue(move.source))
            move.target to output
        }
        published = true
        paths.forEach { file ->
            if (file.exists() && !file.delete()) throw IOException("无法替换章节缓存：$file")
        }
        outputs.forEach { (target, output) ->
            if (!output.renameTo(target)) throw IOException("无法写入章节缓存：$target")
        }
        commit()
    } catch (failure: Throwable) {
        if (published) {
            val rollbackFailures = mutableListOf<Throwable>()
            paths.forEach { original ->
                try {
                    val backup = backups[original]
                    if (backup != null) {
                        backup.copyTo(original, overwrite = true)
                        original.setLastModified(modifiedTimes.getValue(original))
                    } else if (original.exists() && !original.delete()) {
                        throw IOException("无法移除重排目标：$original")
                    }
                } catch (restoreFailure: Throwable) {
                    rollbackFailures.add(restoreFailure)
                }
            }
            if (rollbackFailures.isNotEmpty()) {
                throw IOException("章节缓存回滚未完成，原文件备份保留于：$staging", failure).apply {
                    rollbackFailures.forEach(::addSuppressed)
                }
            }
        }
        cleanup()
        throw failure
    }
    // The database has committed. Cleanup failure must not pretend the transaction rolled back.
    cleanup()
    return result
}
