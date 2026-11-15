package io.legado.app.ui.resource

import android.content.Intent

/** Carry the target book across a new or existing singleTask reader. */
internal data class ResourceReaderStyleRequest(val bookUrl: String) {
    fun writeTo(intent: Intent) {
        intent.putExtra(EXTRA_BOOK, bookUrl)
    }

    companion object {
        private const val EXTRA_BOOK = "io.legado.app.resource.READ_STYLE_BOOK"

        fun readFrom(intent: Intent): ResourceReaderStyleRequest? =
            intent.getStringExtra(EXTRA_BOOK)?.takeIf { it.isNotBlank() }?.let(::ResourceReaderStyleRequest)

        fun clear(intent: Intent) {
            intent.removeExtra(EXTRA_BOOK)
        }
    }
}
