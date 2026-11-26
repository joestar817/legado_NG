package io.legado.app.help.http

import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.Sink
import okio.Timeout
import okio.buffer
import java.nio.charset.Charset

internal fun RequestBody.readNetworkLogPreview(): Pair<String, Boolean> {
    val head = Buffer()
    var truncated = false
    val bounded = object : Sink {
        override fun write(source: Buffer, byteCount: Long) {
            val accepted = minOf(byteCount, NetworkLog.BODY_PREVIEW_SIZE - head.size)
            head.write(source, accepted)
            source.skip(byteCount - accepted)
            truncated = truncated || accepted != byteCount
        }

        override fun flush() = Unit
        override fun close() = Unit
        override fun timeout() = Timeout.NONE
    }
    bounded.buffer().use { writeTo(it) }
    val charset = contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
    return head.readString(charset) to truncated
}

internal fun Response.captureNetworkLogBody(onFinished: (String) -> Unit): Response {
    val responseBody = body
    val contentType = responseBody.contentType()
    val charset = contentType?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
    val preview = NetworkLogBodyPreview(charset)
    // Observe bytes as the caller reads them so logging cannot wait for an SSE stream to finish.
    val source = object : ForwardingSource(responseBody.source()) {
        private var finished = false

        override fun read(sink: Buffer, byteCount: Long): Long {
            val start = sink.size
            val count = super.read(sink, byteCount)
            if (count == -1L) finish()
            if (count > 0) preview.append(sink, start, count)
            return count
        }

        override fun close() {
            try {
                super.close()
            } finally {
                finish()
            }
        }

        private fun finish() {
            if (finished) return
            finished = true
            onFinished(preview.text())
        }
    }
    return newBuilder()
        .body(source.buffer().asResponseBody(contentType, responseBody.contentLength()))
        .build()
}

private class NetworkLogBodyPreview(private val charset: Charset) {
    private val head = Buffer()
    private val tail = Buffer()
    private var omittedBytes = 0L

    fun append(source: Buffer, offset: Long, byteCount: Long) {
        val headCount = minOf(byteCount, NetworkLog.BODY_PREVIEW_SIZE - TAIL_BYTES - head.size)
        source.copyTo(head, offset, headCount)
        val remaining = byteCount - headCount
        if (remaining == 0L) return
        val tailCount = minOf(remaining, TAIL_BYTES)
        val discardedTail = (tail.size + tailCount - TAIL_BYTES).coerceAtLeast(0)
        tail.skip(discardedTail)
        omittedBytes += discardedTail + remaining - tailCount
        source.copyTo(tail, offset + byteCount - tailCount, tailCount)
    }

    fun text(): String {
        if (omittedBytes == 0L) {
            head.writeAll(tail)
            return head.readString(charset)
        }
        return head.readString(charset) +
            "\n[… ${omittedBytes} bytes omitted …]\n" +
            tail.readString(charset)
    }

    private companion object {
        const val TAIL_BYTES = 32L * 1024L
    }
}
