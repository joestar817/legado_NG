package io.legado.app.help.http

import io.legado.app.help.config.AppConfig
import io.legado.app.model.ReadBook
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

object NetworkLog {

    const val MAX_LOG_SIZE = 500
    const val BODY_PREVIEW_SIZE = 512L * 1024L
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val nextId = AtomicLong(0)
    private val items = arrayListOf<Entry>()

    val isEnabled: Boolean
        get() = AppConfig.recordNetworkLog

    val logs: List<Entry>
        @Synchronized get() = items.toList()

    @Synchronized
    fun find(id: Long): Entry? {
        return items.firstOrNull { it.id == id }
    }

    @Synchronized
    fun clear() {
        items.clear()
    }

    fun recordOkHttp(
        request: Request,
        response: Response?,
        tookMs: Long,
        error: Throwable? = null
    ) {
        if (!isEnabled) return
        add(
            Entry(
                id = nextId.incrementAndGet(),
                time = System.currentTimeMillis(),
                source = currentSourceLabel(),
                type = "OkHttp",
                method = request.method,
                url = request.url.toString(),
                statusCode = response?.code,
                tookMs = tookMs,
                requestHeaders = request.headers.format(),
                requestBody = requestBodyText(request),
                responseHeaders = response?.headers?.format(),
                responseBody = response?.previewBodyText(),
                error = error?.stackTraceToString()
            )
        )
    }

    fun recordEvent(
        type: String,
        method: String,
        url: String,
        requestHeaders: String? = null,
        requestBody: String? = null,
        statusCode: Int? = null,
        tookMs: Long? = null,
        responseHeaders: String? = null,
        responseBody: String? = null,
        error: Throwable? = null,
        source: String? = null
    ) {
        if (!isEnabled) return
        add(
            Entry(
                id = nextId.incrementAndGet(),
                time = System.currentTimeMillis(),
                source = source ?: currentSourceLabel(),
                type = type,
                method = method,
                url = url,
                statusCode = statusCode,
                tookMs = tookMs,
                requestHeaders = requestHeaders,
                requestBody = requestBody?.limitPreview(),
                responseHeaders = responseHeaders,
                responseBody = responseBody?.limitPreview(),
                error = error?.stackTraceToString()
            )
        )
    }

    @Synchronized
    private fun add(entry: Entry) {
        items.add(0, entry)
        while (items.size > MAX_LOG_SIZE) {
            items.removeLastOrNull()
        }
    }

    private fun currentSourceLabel(): String {
        val book = ReadBook.book
        val source = ReadBook.bookSource?.getTag()
        val chapter = ReadBook.curTextChapter?.chapter?.title
        return buildString {
            source?.takeIf { it.isNotBlank() }?.let { append(it) }
            book?.name?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append(" / ")
                append(it)
            }
            chapter?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append(" / ")
                append(it)
            }
        }.ifBlank { "全局" }
    }

    private fun Headers.format(): String = buildString {
        for (index in 0 until size) {
            append(name(index)).append(": ").append(value(index)).append('\n')
        }
    }.trimEnd()

    private fun requestBodyText(request: Request): String? {
        val body = request.body ?: return null
        return runCatching {
            val buffer = okio.Buffer()
            body.writeTo(buffer)
            buffer.readUtf8().limitPreview()
        }.getOrElse {
            "[request body unavailable: ${it.localizedMessage}]"
        }
    }

    private fun Response.previewBodyText(): String {
        return runCatching {
            val preview = peekBody(BODY_PREVIEW_SIZE)
            val text = preview.string()
            if (body.contentLength() > BODY_PREVIEW_SIZE) {
                "$text\n[truncated at ${BODY_PREVIEW_SIZE} bytes]"
            } else {
                text
            }
        }.getOrElse {
            "[response body unavailable: ${it.localizedMessage}]"
        }
    }

    private fun String.limitPreview(): String {
        val maxChars = BODY_PREVIEW_SIZE.toInt()
        return if (length > maxChars) {
            take(maxChars) + "\n[truncated at $BODY_PREVIEW_SIZE chars]"
        } else {
            this
        }
    }

    data class Entry(
        val id: Long,
        val time: Long,
        val source: String,
        val type: String,
        val method: String,
        val url: String,
        val statusCode: Int?,
        val tookMs: Long?,
        val requestHeaders: String?,
        val requestBody: String?,
        val responseHeaders: String?,
        val responseBody: String?,
        val error: String?
    ) {
        val summary: String
            get() = buildString {
                append('[').append(timeFormat.format(Date(time))).append("] ")
                append(type).append(' ')
                append(method).append(' ')
                statusCode?.let { append(it).append(' ') }
                tookMs?.let { append(it).append("ms ") }
                append(url)
                if (error != null) append("\nERROR: ").append(error.lineSequence().firstOrNull())
                append("\n").append(source)
            }

        val detail: String
            get() = buildString {
                append(summary)
                appendSection("Request headers", requestHeaders)
                appendSection("Request body", requestBody)
                appendSection("Response headers", responseHeaders)
                appendSection("Response body", responseBody)
                appendSection("Error", error)
            }

        private fun StringBuilder.appendSection(title: String, value: String?) {
            if (value.isNullOrEmpty()) return
            append("\n\n== ").append(title).append(" ==\n")
            append(value)
        }
    }
}
