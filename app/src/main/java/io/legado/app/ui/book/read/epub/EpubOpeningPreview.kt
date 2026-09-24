package io.legado.app.ui.book.read.epub

import android.content.Context
import io.legado.app.model.epub.EpubLayout
import io.legado.app.model.epub.EpubPublicationSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.io.Closeable

/** Activity-owned hidden document; borrows the preparation's snapshot until the reader adopts it. */
internal class EpubOpeningPreview(context: Context, owner: EpubOpeningPreparation) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var initial: EpubOpeningPreparation.InitialDocument? = null
    private var surface: EpubLayoutSurface? = null
    private var failed = false

    init {
        scope.launch {
            try {
                // initData posts its existing Main dispatcher work before entering IO.
                // Let that work start before Chromium occupies the UI thread.
                yield()
                val value = owner.initialDocument() ?: return@launch
                val publication = value.session.publication
                val item = publication.spine.firstOrNull {
                    publication.resourcesById[it.idref]?.location?.path == value.document.location.path
                } ?: return@launch
                if (publication.layoutFor(item) == EpubLayout.FIXED) return@launch
                val timing = EpubStartupTiming("prefetch")
                initial = value
                val next = EpubLayoutSurface(context, value.session, {}, { failed = true })
                surface = next
                timing.mark("surface-created")
                next.stage(value.document.location, value.document.html)
                timing.mark("document-started")
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Speculation may be discarded; the normal reader owns error reporting.
                failed = true
                surface?.close()
                surface = null
            }
        }
    }

    fun take(bookUrl: String?, session: EpubPublicationSession): EpubLayoutSurface? {
        scope.cancel()
        val value = surface
        surface = null
        val matches = !failed && initial?.bookUrl == bookUrl && initial?.session === session
        initial = null
        if (matches) return value
        value?.close()
        return null
    }

    fun discard(session: EpubPublicationSession) {
        if (initial?.session === session) close()
    }

    override fun close() {
        scope.cancel()
        surface?.close()
        surface = null
        initial = null
    }
}
