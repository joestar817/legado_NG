package io.legado.app.help.http

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

object NetworkLogInterceptor : Interceptor {

    internal val aiRequests = Interceptor { chain -> intercept(chain, aiRequest = true) }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response =
        intercept(chain, aiRequest = false)

    private fun intercept(chain: Interceptor.Chain, aiRequest: Boolean): Response {
        val request = chain.request()
        if (!NetworkLog.isEnabled) {
            return chain.proceed(request)
        }
        val start = System.nanoTime()
        val response = try {
            chain.proceed(request)
        } catch (e: Throwable) {
            if (aiRequest) {
                NetworkLog.recordAiHttp(request, null, elapsedMs(start), e)
            } else {
                NetworkLog.recordOkHttp(request, null, elapsedMs(start), e)
            }
            throw e
        }
        val tookMs = elapsedMs(start)
        if (aiRequest) return NetworkLog.recordAiHttp(request, response, tookMs) ?: response
        NetworkLog.recordOkHttp(request, response, tookMs)
        return response
    }

    private fun elapsedMs(start: Long): Long {
        return (System.nanoTime() - start) / 1_000_000L
    }
}
