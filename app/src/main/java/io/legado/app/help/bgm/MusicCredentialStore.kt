package io.legado.app.help.bgm

import android.security.KeyPairGeneratorSpec
import android.util.Base64
import org.json.JSONObject
import splitties.init.appCtx
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Calendar
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal

/** Account material stays outside backup directories and the book-source cookie store. */
object MusicCredentialStore {
    private const val ALIAS = "legado.bgm.accounts.v1"
    private val directory get() = File(appCtx.noBackupFilesDir, "music-accounts").apply { mkdirs() }
    private val random = SecureRandom()

    @Synchronized
    fun read(platform: MusicPlatform): JSONObject? {
        val file = accountFile(platform)
        if (!file.isFile) return null
        return try {
            val envelope = JSONObject(file.readText())
            val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            val store = keyStore()
            rsa.init(Cipher.DECRYPT_MODE, store.getKey(ALIAS, null))
            val key = rsa.doFinal(decode(envelope.getString("key")))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"),
                GCMParameterSpec(128, decode(envelope.getString("iv"))))
            cipher.updateAAD(platform.name.toByteArray(Charsets.UTF_8))
            JSONObject(cipher.doFinal(decode(envelope.getString("data"))).toString(Charsets.UTF_8))
        } catch (_: Exception) {
            // Keystore reset or damaged ciphertext requires a fresh login, never plaintext fallback.
            file.delete()
            null
        }
    }

    @Synchronized
    @Suppress("DEPRECATION")
    fun write(platform: MusicPlatform, data: JSONObject) {
        val store = keyStore()
        if (!store.containsAlias(ALIAS)) {
            val start = Calendar.getInstance()
            val end = Calendar.getInstance().apply { add(Calendar.YEAR, 30) }
            val generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore")
            generator.initialize(KeyPairGeneratorSpec.Builder(appCtx)
                .setAlias(ALIAS).setKeySize(2048)
                .setSubject(X500Principal("CN=Reading NG music accounts"))
                .setSerialNumber(BigInteger.ONE).setStartDate(start.time).setEndDate(end.time).build())
            generator.generateKeyPair()
        }
        val key = ByteArray(32).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val aes = Cipher.getInstance("AES/GCM/NoPadding")
        aes.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        aes.updateAAD(platform.name.toByteArray(Charsets.UTF_8))
        val encrypted = aes.doFinal(data.toString().toByteArray(Charsets.UTF_8))
        val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        rsa.init(Cipher.ENCRYPT_MODE, keyStore().getCertificate(ALIAS).publicKey)
        val envelope = JSONObject().put("version", 1).put("key", encode(rsa.doFinal(key)))
            .put("iv", encode(iv)).put("data", encode(encrypted))
        val file = accountFile(platform)
        val temporary = File(file.parentFile, file.name + ".tmp")
        try {
            temporary.writeText(envelope.toString())
            check(temporary.renameTo(file)) { "无法保存音乐登录状态" }
        } finally {
            key.fill(0)
            temporary.delete()
        }
    }

    @Synchronized
    fun clear(platform: MusicPlatform) {
        accountFile(platform).delete()
    }

    private fun accountFile(platform: MusicPlatform) = File(directory, platform.name.lowercase() + ".json")
    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)
    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
}
