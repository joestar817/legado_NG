package io.legado.app.help.bgm.qq

import okio.ByteString.Companion.toByteString
import okio.ByteString.Companion.decodeBase64
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Ported from QQMusicApi 0.8.2 (GPL-3.0), algorithms/sign.py and utils/qimei.py. */
internal object QqRequestCodec {
    private val random = SecureRandom()
    private val part1 = intArrayOf(23, 14, 6, 36, 16, 7, 19)
    private val part2 = intArrayOf(16, 1, 32, 12, 19, 27, 8, 5)
    private val scramble = intArrayOf(89, 39, 179, 150, 218, 82, 58, 252, 177, 52,
        186, 123, 120, 64, 242, 133, 143, 161, 121, 179)
    private const val PUBLIC_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDEIxgwoutfwoJxcGQeedgP7FG9qaIuS0qzfR8gWkrkTZKM2iWHn2ajQpBRZjMSoSf6+KJGvar2ORhBfpDXyVtZCKpqLQ+FLkpncClKVIrBwv6PHyUvuCb0rIarmgDnzkfQAqVufEtR64iazGDKatvJ9y6B9NMbHddGSAUmRTCrHQIDAQAB"

    fun sign(payload: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(payload.toByteArray(Charsets.UTF_8))
        val hex = digest.hex().uppercase(Locale.ROOT)
        val mixed = ByteArray(20) { (digest[it].toInt() xor scramble[it]).toByte() }
        val middle = mixed.toByteString().base64().filter { it !in "/+=\\" }
        return ("zzc" + part1.map { hex[it] }.joinToString("") + middle +
            part2.map { hex[it] }.joinToString("")).lowercase(Locale.ROOT)
    }

    fun hash33(value: String, seed: Int = 0): Int {
        var result = seed
        value.forEach { result = result * 33 + it.code }
        return result and Int.MAX_VALUE
    }

    fun md5(vararg values: String): String = MessageDigest.getInstance("MD5")
        .digest(values.joinToString("").toByteArray(Charsets.UTF_8)).hex()

    fun randomHex(size: Int): String = ByteArray((size + 1) / 2).also(random::nextBytes)
        .hex().take(size)

    fun randomInt(bound: Int): Int = random.nextInt(bound)

    fun aesBase64(key: String, value: String, iv: String = key): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(iv.toByteArray(Charsets.UTF_8)))
        return cipher.doFinal(value.toByteArray(Charsets.UTF_8)).toByteString().base64()
    }

    fun rsaBase64(value: String): String {
        val bytes = requireNotNull(PUBLIC_KEY.decodeBase64()).toByteArray()
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(value.toByteArray(Charsets.UTF_8)).toByteString().base64()
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
}
