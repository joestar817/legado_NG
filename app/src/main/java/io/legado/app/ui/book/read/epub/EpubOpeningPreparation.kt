package io.legado.app.ui.book.read.epub

import android.content.Context
import android.os.ParcelFileDescriptor
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isEpub
import io.legado.app.model.epub.EpubSnapshotSession
import io.legado.app.model.epub.EpubSnapshotStore
import io.legado.app.model.epub.EpubPublicationSession
import io.legado.app.model.epub.EpubSingleDocumentSource
import io.legado.app.model.epub.EpubSourceChapter
import io.legado.app.ui.book.read.page.entities.TextChapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.File
import java.io.IOException

/** One reading Activity's pending resource owner. It neither changes ReadBook nor opens a renderer. */
internal class EpubOpeningPreparation(context: Context, private val requestedUrl: String?, previousChapter: TextChapter? = null) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var closed = false
    private var sourceUrl: String? = null
    private var ready: EpubSnapshotSession? = null
    private data class SourceKey(val bookUrl: String, val chapterUrl: String, val index: Int,
                                 val nextUrl: String, val start: String?, val end: String?,
                                 val removeHeadings: Boolean, val removeRuby: Boolean)
    private data class Source(val key: SourceKey, val session: EpubPublicationSession, val content: EpubSourceChapter)
    private var source: Source? = null
    private data class Stored(val key: SourceKey, val session: EpubPublicationSession, val record: EpubOpeningMappingFile.Record)
    private var stored: Stored? = null
    data class InitialDocument(val bookUrl: String, val session: EpubPublicationSession,
                               val document: io.legado.app.model.epub.EpubSourceDocument)
    private var initial: InitialDocument? = null

    private fun sourceKey(book: Book, chapter: BookChapter) = SourceKey(book.bookUrl, chapter.url, chapter.index,
        chapter.getVariable("nextUrl"), chapter.startFragmentId, chapter.endFragmentId,
        book.getDelTag(Book.hTag), book.getDelTag(Book.rubyTag))
    private val task = scope.async {
        val book = if (requestedUrl.isNullOrEmpty()) appDb.bookDao.lastReadBook else appDb.bookDao.getBook(requestedUrl)
        if (closed || book?.isEpub != true) return@async
        val timing = EpubStartupTiming("preopen")
        timing.mark("begin")
        var opened: EpubSnapshotSession? = null
        try {
            opened = EpubSnapshotStore(File(context.cacheDir, "epub-layout"), onStage = timing::mark).open(
                book.bookUrl,
                openInput = {
                    val descriptor = BookHelp.getBookPFD(book) ?: throw IOException("无法打开 EPUB")
                    ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                },
                checkCancelled = { if (closed) throw IOException("EPUB 打开已取消") },
            )
            val session = opened.publicationSession
            val revision = opened.identity.contentRevision
            timing.mark("snapshot-ready")
            val mapped = previousChapter?.let { chapter ->
                EpubOpeningMappingCache.find(book, chapter, revision) { source ->
                    BookHelp.epubContentPositions(book, chapter.chapter, source, chapter.contentPositionMap!!.source)
                }
            }
            val chapter = if (mapped == null) appDb.bookChapterDao.getChapter(book.bookUrl, book.durChapterIndex) else null
            val disk = chapter?.let {
                EpubOpeningMappingFile.read(File(context.cacheDir, "epub-layout/opening-mapping.bin"),
                    EpubOpeningMappingFile.request(book, it, revision))?.let { record -> Stored(sourceKey(book, it), session, record) }
            }
            val initialSource = runCatching {
                if (closed || mapped != null || disk != null || chapter == null) return@runCatching null
                val key = sourceKey(book, chapter)
                val path = key.chapterUrl.substringBeforeLast('#')
                EpubSingleDocumentSource.read(session.publication, path, key.nextUrl.substringBeforeLast('#'),
                    key.start, key.end, key.removeHeadings, key.removeRuby) {
                    session.openResource(path).use { it.data.readBytes().toString(java.nio.charset.Charset.defaultCharset()) }
                }?.let { Source(key, session, it) }
            }.getOrNull()
            timing.mark(if (disk != null) "disk-source-ready" else if (mapped != null) "mapped-source-reused" else if (initialSource != null) "source-ready" else "source-not-prepared")
            synchronized(this@EpubOpeningPreparation) {
                if (!closed) {
                    sourceUrl = book.bookUrl
                    ready = opened
                    source = initialSource
                    stored = disk
                    initial = (mapped?.documents?.singleOrNull()?.source
                        ?: disk?.record?.mapped?.documents?.singleOrNull()?.source
                        ?: initialSource?.content?.documents?.singleOrNull())?.let {
                        InitialDocument(book.bookUrl, session, it)
                    }
                    opened = null
                }
            }
            timing.mark("ready")
        } finally { opened?.close() }
    }

    /** Borrowed only while this Activity still owns the pending snapshot. */
    suspend fun initialDocument(): InitialDocument? {
        task.await()
        return synchronized(this) { initial?.takeIf { !closed && ready?.publicationSession === it.session } }
    }

    @Synchronized
    fun cachedOpeningDocument(book: Book, session: EpubPublicationSession) = stored?.takeIf {
        !closed && it.session === session && it.key.bookUrl == book.bookUrl && it.key.index == book.durChapterIndex &&
            it.key.removeHeadings == book.getDelTag(Book.hTag) && it.key.removeRuby == book.getDelTag(Book.rubyTag)
    }?.record?.mapped?.documents?.singleOrNull()?.source

    @Synchronized
    fun takeRecord(book: Book, chapter: BookChapter, session: EpubPublicationSession): EpubOpeningMappingFile.Record? {
        val value = stored ?: return null
        if (closed || value.session !== session || value.key != sourceKey(book, chapter)) return null
        stored = null
        return value.record
    }

    @Synchronized
    fun openingDocument(book: Book, session: EpubPublicationSession) = source?.takeIf {
        !closed && it.session === session && it.key.bookUrl == book.bookUrl && it.key.index == book.durChapterIndex &&
            it.key.removeHeadings == book.getDelTag(Book.hTag) && it.key.removeRuby == book.getDelTag(Book.rubyTag)
    }?.content?.documents?.singleOrNull() ?: cachedOpeningDocument(book, session)

    @Synchronized
    fun takeSource(book: Book, chapter: BookChapter, session: EpubPublicationSession): EpubSourceChapter? {
        val value = source ?: return null
        if (closed || value.session !== session || value.key != sourceKey(book, chapter)) return null
        source = null
        return value.content
    }

    suspend fun take(bookUrl: String): EpubSnapshotSession? {
        if (closed || !requestedUrl.isNullOrEmpty() && requestedUrl != bookUrl) return null
        try {
            task.await()
        } catch (error: CancellationException) {
            currentCoroutineContext().ensureActive()
            if (closed) return null
            throw error
        }
        return synchronized(this) {
            if (closed || sourceUrl != bookUrl) null else ready.also { ready = null }
        }
    }

    override fun close() {
        val unused = synchronized(this) {
            closed = true
            source = null
            stored = null
            initial = null
            ready.also { ready = null }
        }
        scope.cancel()
        if (unused != null) scope.launch(NonCancellable) { unused.close() }
    }
}
