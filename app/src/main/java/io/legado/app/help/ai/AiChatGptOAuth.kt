package io.legado.app.help.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.R
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import splitties.init.appCtx
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.math.BigInteger
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.concurrent.TimeUnit

internal const val CHATGPT_API = "https://api.openai.com/v1"
internal const val CHATGPT_USAGE_URL = "https://chatgpt.com/settings/usage"
internal const val CHATGPT_PLAN_SCOPE = "chatgpt.tokens.use.direct"
internal const val CHATGPT_ISSUER = "https://auth.openai.com"
internal const val CHATGPT_DYNAMIC_CLIENT = "dynamic_agent_client"
private const val CALLBACK_PATH = "/auth/callback"

internal data class AiChatGptAuthorization(val state: String, val nonce: String, val verifier: String) {
    fun challenge(): String = MessageDigest.getInstance("SHA-256")
        .digest(verifier.toByteArray(Charsets.US_ASCII)).base64Url()

    fun url(
        hostId: String,
        redirect: String,
        clientId: String?,
        email: String?,
        requestPlanConsent: Boolean = false,
    ): HttpUrl {
        return "$CHATGPT_ISSUER/api/accounts/authorize".toHttpUrl().newBuilder()
            .addQueryParameter("client_id", clientId ?: CHATGPT_DYNAMIC_CLIENT)
            .apply {
                if (clientId == null) addQueryParameter("agent_name_hint", "Reading NG")
                else if (!email.isNullOrBlank()) addQueryParameter("login_hint", email)
                if (requestPlanConsent) addQueryParameter("prompt", "consent")
            }
            .addQueryParameter("ext_agent_host_id", hostId)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("redirect_uri", redirect)
            .addQueryParameter("scope", "openid profile email offline_access resource.invoke $CHATGPT_PLAN_SCOPE")
            .addQueryParameter("resource", CHATGPT_API)
            .addQueryParameter("state", state)
            .addQueryParameter("nonce", nonce)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("code_challenge", challenge())
            .build()
    }

    companion object {
        private val random = SecureRandom()

        fun create() = AiChatGptAuthorization(randomValue(), randomValue(), randomValue())

        private fun randomValue() = ByteArray(32).also(random::nextBytes).base64Url()
    }
}

internal sealed interface AiChatGptCallback {
    data class Code(val value: String, val clientId: String?) : AiChatGptCallback
    data class Rejected(val denied: Boolean) : AiChatGptCallback
}

internal fun readChatGptCallback(url: HttpUrl, state: String): AiChatGptCallback? {
    if (url.encodedPath != CALLBACK_PATH || url.fragment != null) return null
    if (url.queryParameterValues("state").singleOrNull() != state) return null
    if (listOf("code", "client_id", "error").any { url.queryParameterValues(it).size > 1 }) return null
    if ("error" in url.queryParameterNames) {
        return AiChatGptCallback.Rejected(url.queryParameter("error") == "access_denied")
    }
    val code = url.queryParameter("code")?.takeIf(String::isNotBlank) ?: return null
    return AiChatGptCallback.Code(code, url.queryParameter("client_id"))
}

/** The browser sends the authorization result only to this process's IPv4 loopback listener. */
internal class AiChatGptLoopback(
    port: Int = 0,
    redirectHost: String = "127.0.0.1",
    private val browserText: (Int) -> String = { appCtx.getString(it) },
) : Closeable {
    private val server = ServerSocket(port, 8, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 500 }
    private val origin = "http://127.0.0.1:${server.localPort}"
    val redirectUri = "http://$redirectHost:${server.localPort}$CALLBACK_PATH"

    suspend fun await(state: String): AiChatGptCallback = withTimeoutOrNull(TimeUnit.MINUTES.toMillis(5)) {
        var callback: AiChatGptCallback? = null
        while (callback == null) {
            currentCoroutineContext().ensureActive()
            val connection = try {
                server.accept()
            } catch (_: SocketTimeoutException) {
                continue
            }
            connection.use { socket ->
                callback = socket.handle(state)
            }
        }
        callback
    } ?: throw AiChatGptException(R.string.ai_chatgpt_sign_in_timeout)

    private fun Socket.handle(state: String): AiChatGptCallback? {
        val firstLine = try {
            requestLine()
        } catch (_: IOException) {
            return null
        }
        val target = firstLine?.split(' ', limit = 3)?.takeIf { it.size == 3 && it[0] == "GET" }?.get(1)
        val callback = target?.takeIf { it.startsWith("$CALLBACK_PATH?") }
            ?.let { runCatching { (origin + it).toHttpUrl() }.getOrNull() }
            ?.let { readChatGptCallback(it, state) }
        val message = when (callback) {
            is AiChatGptCallback.Code -> R.string.ai_chatgpt_browser_callback
            is AiChatGptCallback.Rejected -> R.string.ai_chatgpt_browser_denied
            null -> R.string.ai_chatgpt_browser_invalid
        }
        val content = browserText(message).toByteArray(Charsets.UTF_8)
        val status = if (callback == null) "400 Bad Request" else "200 OK"
        val headers = "HTTP/1.1 $status\r\nContent-Type: text/plain; charset=utf-8\r\n" +
            "Content-Length: ${content.size}\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\n" +
            "X-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n"
        try {
            getOutputStream().apply {
                write(headers.toByteArray(Charsets.US_ASCII))
                write(content)
                flush()
            }
        } catch (_: IOException) {
            // The browser may close the tab after delivering a valid callback.
        }
        return callback
    }

    private fun Socket.requestLine(): String? {
        soTimeout = 1000
        val input = BufferedInputStream(getInputStream())
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        val line = ByteArrayOutputStream()
        var firstLine = true
        var lineLength = 0
        repeat(16384) {
            if (System.nanoTime() - deadline >= 0) return null
            when (val next = input.read()) {
                -1 -> return null
                '\n'.code -> {
                    if (!firstLine && lineLength == 0) return line.toString("US-ASCII")
                    firstLine = false
                    lineLength = 0
                }
                '\r'.code -> Unit
                else -> {
                    lineLength++
                    if (firstLine) line.write(next)
                }
            }
        }
        return null
    }

    override fun close() = server.close()
}

internal data class AiChatGptIdentity(val subject: String, val email: String?)

internal fun verifyChatGptIdentity(
    token: String,
    keys: JsonObject,
    clientId: String,
    nonce: String?,
    nowSeconds: Long = System.currentTimeMillis() / 1000,
): AiChatGptIdentity = try {
    val parts = token.split('.', limit = 4)
    require(parts.size == 3 && token.length <= 32768)
    val header = decodeJwtObject(parts[0])
    val kid = requireNotNull(header.stringOrNull("kid"))
    require(header.stringOrNull("alg") == "RS256" && !header.has("crit"))
    val key = keys.arrayOrNull("keys").objectParts().single {
        it.stringOrNull("kid") == kid && it.stringOrNull("kty") == "RSA" &&
            it.stringOrNull("use") in listOf(null, "sig") && it.stringOrNull("alg") in listOf(null, "RS256")
    }
    val modulus = BigInteger(1, requireNotNull(key.stringOrNull("n")?.decodeBase64()).toByteArray())
    val exponent = BigInteger(1, requireNotNull(key.stringOrNull("e")?.decodeBase64()).toByteArray())
    require(modulus.bitLength() in 2048..8192)
    val publicKey = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
    val signature = Signature.getInstance("SHA256withRSA").apply {
        initVerify(publicKey)
        update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
    }
    require(signature.verify(requireNotNull(parts[2].decodeBase64()).toByteArray()))
    val claims = decodeJwtObject(parts[1])
    val audience = claims.get("aud")
    val audiences = when {
        audience?.isJsonArray == true -> audience.asJsonArray.map { it.asString }
        audience?.isJsonPrimitive == true && audience.asJsonPrimitive.isString -> listOf(audience.asString)
        else -> emptyList()
    }
    val authorizedParty = claims.stringOrNull("azp")
    require(claims.stringOrNull("iss") == CHATGPT_ISSUER && clientId in audiences)
    require(if (audiences.size > 1 || authorizedParty != null) authorizedParty == clientId else true)
    require(claims.get("exp").asLong > nowSeconds)
    require(claims.get("nbf")?.asLong?.let { it <= nowSeconds + 30 } != false)
    require(nonce == null || claims.stringOrNull("nonce") == nonce)
    val subject = requireNotNull(claims.stringOrNull("sub")?.takeIf(String::isNotBlank))
    val email = claims.stringOrNull("email")?.trim()?.take(254)?.takeIf(String::isNotBlank)
    AiChatGptIdentity(subject, email)
} catch (_: Exception) {
    throw AiChatGptException(R.string.ai_chatgpt_invalid_identity)
}

internal fun decodeJwtObject(part: String): JsonObject =
    JsonParser.parseString(requireNotNull(part.decodeBase64()).utf8()).asJsonObject

private fun ByteArray.base64Url() = toByteString().base64Url().trimEnd('=')
