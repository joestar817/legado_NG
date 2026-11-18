package io.legado.app.help.config

import io.legado.app.help.http.okHttpClient
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream

internal object ParagraphCommentBubbleImport {
    fun read(input: InputStream): String {
        val bytes = ByteArray(ParagraphCommentIconSettings.MAX_TEMPLATE_BYTES + 1)
        var length = 0
        while (length < bytes.size) {
            val count = input.read(bytes, length, bytes.size - length)
            if (count < 0) break
            length += count
        }
        require(length <= ParagraphCommentIconSettings.MAX_TEMPLATE_BYTES) { "SVG 文件过大" }
        val bom = if (length >= 2) ((bytes[0].toInt() and 0xff) shl 8) or (bytes[1].toInt() and 0xff) else 0
        val charset = if (bom == 0xfeff || bom == 0xfffe) Charsets.UTF_16 else Charsets.UTF_8
        return String(bytes, 0, length, charset).removePrefix("\uFEFF")
    }

    suspend fun download(address: String): Pair<String, String> {
        val url = address.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("请输入 HTTP 或 HTTPS 地址")
        val call = okHttpClient.newCall(Request.Builder().url(url).build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            require(it.isSuccessful) { "下载失败（HTTP ${it.code}）" }
                            val body = it.body
                            body.byteStream().use(::read) to url.pathSegments.lastOrNull().orEmpty().ifBlank { "气泡.svg" }
                        }
                    }
                    if (continuation.isActive) continuation.resumeWith(result)
                }
            })
        }
    }
}
