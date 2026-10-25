package io.legado.app.help.bgm.netease

import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.zip.GZIPInputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Protocol port of api-enhanced 4.41.1; see docs/BGM_PROTOCOL_SOURCES.md. */
internal object NeteaseCrypto {
    private val random = SecureRandom()
    private val iv = "0102030405060708".toByteArray()
    private val presetKey = "0CoJUm6Qyw8W8jud".toByteArray()
    private val eapiKey = "e82ckenh8dichen8".toByteArray()
    private const val PUBLIC_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB"
    private const val BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    fun weapi(data: JSONObject, secret: String = buildString {
        repeat(16) { append(BASE62[random.nextInt(BASE62.length)]) }
    }): Map<String, String> {
        require(secret.toByteArray().size == 16)
        val first = cipher(data.toString().toByteArray(), presetKey, true, iv).toByteString().base64()
        val second = cipher(first.toByteArray(), secret.toByteArray(), true, iv).toByteString().base64()
        val key = KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(requireNotNull(PUBLIC_KEY.decodeBase64()).toByteArray())
        ) as RSAPublicKey
        val rsa = BigInteger(1, secret.reversed().toByteArray()).modPow(key.publicExponent, key.modulus)
            .toString(16).padStart(256, '0')
        return mapOf("params" to second, "encSecKey" to rsa)
    }

    fun eapi(path: String, data: JSONObject): Map<String, String> {
        val json = data.toString()
        val digest = MessageDigest.getInstance("MD5")
            .digest("nobody${path}use${json}md5forencrypt".toByteArray()).toByteString().hex()
        val text = "$path-36cd479b6b5-$json-36cd479b6b5-$digest"
        return mapOf("params" to cipher(text.toByteArray(), eapiKey, true).toByteString().hex().uppercase())
    }

    fun decodeResponse(body: ByteArray): JSONObject {
        val decrypted = cipher(body, eapiKey, false)
        val plain = if (decrypted.size >= 2 && decrypted[0] == 0x1f.toByte() && decrypted[1] == 0x8b.toByte()) {
            GZIPInputStream(ByteArrayInputStream(decrypted)).use { it.readBytes() }
        } else decrypted
        return JSONObject(plain.toString(Charsets.UTF_8))
    }

    private fun cipher(data: ByteArray, key: ByteArray, encrypt: Boolean, iv: ByteArray? = null): ByteArray {
        val c = Cipher.getInstance(if (iv == null) "AES/ECB/PKCS5Padding" else "AES/CBC/PKCS5Padding")
        val mode = if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE
        val secret = SecretKeySpec(key, "AES")
        if (iv == null) c.init(mode, secret) else c.init(mode, secret, IvParameterSpec(iv))
        return c.doFinal(data)
    }

    fun bytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)
}
