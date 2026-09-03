package io.legado.app.help.exoplayer

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheWriter
import com.google.gson.annotations.SerializedName
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.analyzeRule.AnalyzeUrl.Companion.getMediaItem
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.externalFiles
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.isJsonArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import splitties.init.appCtx
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

data class CachedAudioChapter(
    val content: String,
    val cacheKeys: List<String>,
)

/**
 * 用户主动缓存的有声书音频。
 *
 * Media3 的数据文件独立放在 externalFiles/audio_cache；每章清单跟随原 book_cache
 * 书籍目录保存，以便换源迁移和现有逐书／全局清理入口能共同管理。
 */
@OptIn(UnstableApi::class)
object AudioDownloadCache {

    private const val MANIFEST_VERSION = 1
    private const val AUDIO_MANIFEST_FOLDER = "audio"
    private const val AUDIO_CACHE_KEY_PREFIX = "audiobook:"
    private const val MAX_PARALLEL_DOWNLOADS = 2

    private val downloadSemaphore = Semaphore(MAX_PARALLEL_DOWNLOADS)
    private val globalGeneration = AtomicLong(0L)
    private val bookGenerations = ConcurrentHashMap<String, AtomicLong>()
    private val activeWriters = ConcurrentHashMap<String, MutableSet<CacheWriter>>()

    suspend fun cacheChapter(
        bookSource: BookSource,
        book: Book,
        chapter: BookChapter,
        content: String,
    ): CachedAudioChapter = downloadSemaphore.withPermit {
        getCachedChapter(bookSource, book, chapter)?.let { return@withPermit it }
        val audioUrls = parseAudioUrls(content)
        val bookPrefix = bookCachePrefix(book)
        val generation = generationSnapshot(bookPrefix)
        val parts = audioUrls.mapIndexed { index, audioUrl ->
            ensureSupportedAudioUrl(audioUrl)
            ensureGeneration(bookPrefix, generation)
            val cacheKey = chapterCacheKey(bookPrefix, book, chapter, index)
            val mediaItem = createMediaItem(
                bookSource = bookSource,
                book = book,
                chapter = chapter,
                audioUrl = audioUrl,
                cacheKey = cacheKey,
                coroutineContext = currentCoroutineContext(),
            )
            val writer = ExoPlayerHelper.createAudioCacheWriter(mediaItem)
            registerWriter(bookPrefix, writer)
            val completionHandle = currentCoroutineContext().job.invokeOnCompletion { cause ->
                if (cause is CancellationException) writer.cancel()
            }
            try {
                runInterruptible { writer.cache() }
                ensureGeneration(bookPrefix, generation)
                val length = ExoPlayerHelper.audioCacheContentLength(cacheKey)
                    .takeIf { it > 0L }
                    ?: ExoPlayerHelper.audioCacheContiguousLength(cacheKey)
                if (!ExoPlayerHelper.isAudioCacheComplete(cacheKey, length)) {
                    throw IOException("音频缓存不完整")
                }
                AudioCachePart(cacheKey = cacheKey, length = length)
            } finally {
                completionHandle.dispose()
                unregisterWriter(bookPrefix, writer)
                if (!generationMatches(bookPrefix, generation)) {
                    ExoPlayerHelper.removeAudioCache(listOf(cacheKey))
                }
            }
        }
        ensureGeneration(bookPrefix, generation)
        val manifest = AudioCacheManifest(
            version = MANIFEST_VERSION,
            sourceUrl = bookSource.bookSourceUrl,
            bookUrl = book.bookUrl,
            chapterUrl = chapter.url,
            chapterIndex = chapter.index,
            chapterFileName = chapter.getFileName(),
            content = content,
            parts = parts,
        )
        val manifestFile = manifestFile(book, chapter)
        readManifest(manifestFile)?.parts
            ?.map(AudioCachePart::cacheKey)
            ?.filterNot(parts.map(AudioCachePart::cacheKey).toSet()::contains)
            ?.let(ExoPlayerHelper::removeAudioCache)
        writeManifest(manifestFile, manifest)
        CachedAudioChapter(content, parts.map(AudioCachePart::cacheKey))
    }

    fun getCachedChapter(
        bookSource: BookSource,
        book: Book,
        chapter: BookChapter,
    ): CachedAudioChapter? {
        val manifest = readManifest(manifestFile(book, chapter)) ?: return null
        if (!manifest.matches(bookSource, book, chapter) || !manifest.isComplete()) return null
        return CachedAudioChapter(
            content = manifest.content,
            cacheKeys = manifest.parts.map(AudioCachePart::cacheKey),
        )
    }

    fun getCachedChapterFileNames(bookSource: BookSource, book: Book): Set<String> {
        return manifestFolder(book).listFiles()
            ?.asSequence()
            ?.filter(File::isFile)
            ?.mapNotNull(::readManifest)
            ?.filter { manifest ->
                manifest.version == MANIFEST_VERSION &&
                    manifest.sourceUrl == bookSource.bookSourceUrl &&
                    manifest.bookUrl == book.bookUrl &&
                    manifest.isComplete()
            }
            ?.map(AudioCacheManifest::chapterFileName)
            ?.toSet()
            .orEmpty()
    }

    fun createMediaItems(
        bookSource: BookSource?,
        book: Book?,
        chapter: BookChapter?,
        content: String,
        cacheKeys: List<String>,
        coroutineContext: CoroutineContext,
    ): List<MediaItem> {
        return parseAudioUrls(content).mapIndexed { index, audioUrl ->
            createMediaItem(
                bookSource = bookSource,
                book = book,
                chapter = chapter,
                audioUrl = audioUrl,
                cacheKey = cacheKeys.getOrNull(index),
                coroutineContext = coroutineContext,
            )
        }
    }

    fun clearBook(book: Book) {
        val bookPrefix = bookCachePrefix(book)
        bookGenerations.getOrPut(bookPrefix) { AtomicLong(0L) }.incrementAndGet()
        activeWriters[bookPrefix]?.toList()?.forEach(CacheWriter::cancel)
        manifestFolder(book).listFiles()
            ?.mapNotNull(::readManifest)
            ?.flatMap(AudioCacheManifest::parts)
            ?.map(AudioCachePart::cacheKey)
            ?.let(ExoPlayerHelper::removeAudioCache)
        ExoPlayerHelper.removeAudioCacheByPrefix(bookPrefix)
        FileUtils.delete(manifestFolder(book).absolutePath)
    }

    fun clearChapter(book: Book, chapter: BookChapter) {
        val bookPrefix = bookCachePrefix(book)
        bookGenerations.getOrPut(bookPrefix) { AtomicLong(0L) }.incrementAndGet()
        activeWriters[bookPrefix]?.toList()?.forEach(CacheWriter::cancel)
        val manifestFile = manifestFile(book, chapter)
        readManifest(manifestFile)?.parts
            ?.map(AudioCachePart::cacheKey)
            ?.let(ExoPlayerHelper::removeAudioCache)
        ExoPlayerHelper.removeAudioCacheByPrefix(chapterCachePrefix(bookPrefix, book, chapter))
        manifestFile.delete()
    }

    fun clearAll() {
        globalGeneration.incrementAndGet()
        activeWriters.values
            .flatMap { it.toList() }
            .forEach(CacheWriter::cancel)
        ExoPlayerHelper.clearAudioCache()
    }

    fun parseAudioUrls(content: String): List<String> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) throw NoStackTraceException("未获取到资源链接")
        val urls = if (trimmed.isJsonArray()) {
            GSON.fromJsonArray<String>(trimmed).getOrNull()
                ?: throw NoStackTraceException("音频地址格式错误")
        } else {
            listOf(trimmed)
        }
        return urls.map(String::trim).filter(String::isNotEmpty).ifEmpty {
            throw NoStackTraceException("未获取到资源链接")
        }
    }

    private fun createMediaItem(
        bookSource: BookSource?,
        book: Book?,
        chapter: BookChapter?,
        audioUrl: String,
        cacheKey: String?,
        coroutineContext: CoroutineContext,
    ): MediaItem {
        return AnalyzeUrl(
            audioUrl,
            source = bookSource,
            ruleData = book,
            chapter = chapter,
            coroutineContext = coroutineContext,
        ).getMediaItem(cacheKey)
    }

    private fun ensureSupportedAudioUrl(audioUrl: String) {
        val path = audioUrl.substringBefore(",{").substringBefore('?').lowercase()
        if (path.endsWith(".m3u8") || path.endsWith(".mpd")) {
            throw NoStackTraceException("暂不支持缓存 HLS/DASH 音频")
        }
    }

    private fun generationSnapshot(bookPrefix: String): GenerationSnapshot {
        return GenerationSnapshot(
            global = globalGeneration.get(),
            book = bookGenerations.getOrPut(bookPrefix) { AtomicLong(0L) }.get(),
        )
    }

    private fun generationMatches(bookPrefix: String, snapshot: GenerationSnapshot): Boolean {
        return globalGeneration.get() == snapshot.global &&
            bookGenerations.getOrPut(bookPrefix) { AtomicLong(0L) }.get() == snapshot.book
    }

    private fun ensureGeneration(bookPrefix: String, snapshot: GenerationSnapshot) {
        if (!generationMatches(bookPrefix, snapshot)) {
            throw CancellationException("音频缓存已清理")
        }
    }

    private fun registerWriter(bookPrefix: String, writer: CacheWriter) {
        activeWriters.getOrPut(bookPrefix) { ConcurrentHashMap.newKeySet() }.add(writer)
    }

    private fun unregisterWriter(bookPrefix: String, writer: CacheWriter) {
        activeWriters[bookPrefix]?.let { writers ->
            writers.remove(writer)
            if (writers.isEmpty()) activeWriters.remove(bookPrefix, writers)
        }
    }

    private fun bookCachePrefix(book: Book): String {
        return "$AUDIO_CACHE_KEY_PREFIX${MD5Utils.md5Encode(book.getFolderName())}:"
    }

    private fun chapterCacheKey(
        bookPrefix: String,
        book: Book,
        chapter: BookChapter,
        partIndex: Int,
    ): String {
        return "${chapterCachePrefix(bookPrefix, book, chapter)}$partIndex"
    }

    private fun chapterCachePrefix(
        bookPrefix: String,
        book: Book,
        chapter: BookChapter,
    ): String {
        val identity = listOf(
            MANIFEST_VERSION,
            book.origin,
            book.bookUrl,
            chapter.index,
            chapter.url,
        ).joinToString("\u0000")
        return "$bookPrefix${MD5Utils.md5Encode(identity)}:"
    }

    private fun manifestFile(book: Book, chapter: BookChapter): File {
        val identity = "${book.origin}\u0000${book.bookUrl}\u0000${chapter.index}\u0000${chapter.url}"
        return File(manifestFolder(book), "${MD5Utils.md5Encode(identity)}.json")
    }

    private fun manifestFolder(book: Book): File {
        return File(appCtx.externalFiles, "book_cache/${book.getFolderName()}/$AUDIO_MANIFEST_FOLDER")
    }

    private fun readManifest(file: File): AudioCacheManifest? {
        if (!file.isFile) return null
        return runCatching {
            GSON.fromJson(file.readText(), AudioCacheManifest::class.java)
        }.getOrNull()
    }

    private fun writeManifest(file: File, manifest: AudioCacheManifest) {
        file.parentFile?.mkdirs()
        val tempFile = File(file.parentFile, "${file.name}.part")
        tempFile.writeText(GSON.toJson(manifest))
        if (file.exists() && !file.delete()) {
            tempFile.delete()
            throw IOException("无法替换音频缓存清单")
        }
        if (!tempFile.renameTo(file)) {
            tempFile.delete()
            throw IOException("无法保存音频缓存清单")
        }
    }

    private data class GenerationSnapshot(
        val global: Long,
        val book: Long,
    )

    private data class AudioCacheManifest(
        @SerializedName("version")
        val version: Int = 0,
        @SerializedName("source_url")
        val sourceUrl: String = "",
        @SerializedName("book_url")
        val bookUrl: String = "",
        @SerializedName("chapter_url")
        val chapterUrl: String = "",
        @SerializedName("chapter_index")
        val chapterIndex: Int = -1,
        @SerializedName("chapter_file_name")
        val chapterFileName: String = "",
        @SerializedName("content")
        val content: String = "",
        @SerializedName("parts")
        val parts: List<AudioCachePart> = emptyList(),
    ) {
        fun matches(bookSource: BookSource, book: Book, chapter: BookChapter): Boolean {
            return version == MANIFEST_VERSION &&
                sourceUrl == bookSource.bookSourceUrl &&
                bookUrl == book.bookUrl &&
                chapterUrl == chapter.url &&
                chapterIndex == chapter.index &&
                chapterFileName == chapter.getFileName()
        }

        fun isComplete(): Boolean {
            val contentPartCount = runCatching {
                AudioDownloadCache.parseAudioUrls(content).size
            }.getOrNull()
            return contentPartCount == parts.size && parts.isNotEmpty() && parts.all {
                ExoPlayerHelper.isAudioCacheComplete(it.cacheKey, it.length)
            }
        }
    }

    private data class AudioCachePart(
        @SerializedName("cache_key")
        val cacheKey: String = "",
        @SerializedName("length")
        val length: Long = 0L,
    )
}
