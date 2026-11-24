package io.legado.app.help.ai

import android.security.KeyPairGeneratorSpec
import android.util.AtomicFile
import com.google.gson.annotations.SerializedName
import io.legado.app.R
import io.legado.app.utils.GSON
import splitties.init.appCtx
import java.io.File
import java.io.FileNotFoundException
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Calendar
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal

internal class AiChatGptTokens(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String?,
    @SerializedName("expires_at") val expiresAt: Long,
    @SerializedName("scopes") val scopes: Set<String>,
) {
    val allowsPlan: Boolean get() = CHATGPT_PLAN_SCOPE in scopes
}

internal data class AiChatGptAccount(
    @SerializedName("client_id") val clientId: String,
    @SerializedName("subject") val subject: String,
    @SerializedName("email") val email: String?,
    @SerializedName("tokens") val tokens: AiChatGptTokens?,
    @SerializedName("reauthorization_required") val reauthorizationRequired: Boolean = false,
    @SerializedName("codex_workspace_id") val codexWorkspaceId: String? = null,
) {
    fun needsReauthorization(nowSeconds: Long = System.currentTimeMillis() / 1000): Boolean =
        reauthorizationRequired ||
            (tokens != null && tokens.refreshToken == null && tokens.expiresAt <= nowSeconds)

    fun afterAccessTokenRejected(accessToken: String): AiChatGptAccount? {
        val current = tokens?.takeIf { it.accessToken == accessToken } ?: return null
        if (reauthorizationRequired) return this
        val refresh = current.refreshToken ?: return copy(reauthorizationRequired = true)
        if (current.expiresAt == 0L) return this
        return copy(tokens = AiChatGptTokens(current.accessToken, refresh, 0L, current.scopes))
    }
}

/** Tokens are device-local. Only the selected registration ID is stored with provider settings. */
internal class AiOpenAiAccountStore(keyAlias: String, fileName: String) {
    private val file = AiEncryptedAccountFile(keyAlias, fileName, "Reading NG ChatGPT")

    private data class Accounts(
        @SerializedName("host_id") val hostId: String,
        @SerializedName("accounts") val accounts: List<AiChatGptAccount> = emptyList(),
    )

    private var cached: Accounts? = null

    @Synchronized
    fun accounts(): List<AiChatGptAccount> = snapshot().accounts

    @Synchronized
    fun hostId(): String {
        val value = snapshot()
        if (!file.exists()) write(value)
        return value.hostId
    }

    @Synchronized
    fun save(account: AiChatGptAccount) {
        val value = snapshot()
        val index = value.accounts.indexOfFirst { it.clientId == account.clientId }
        val updated = value.accounts.toMutableList()
        if (index < 0) updated.add(account) else updated[index] = account
        write(value.copy(accounts = updated))
    }

    private fun snapshot(): Accounts = cached ?: run {
        val loaded = try {
            file.read()?.let { GSON.fromJson(it, Accounts::class.java) }
        } catch (_: Exception) {
            null
        }
        (loaded ?: Accounts("urn:uuid:${UUID.randomUUID()}")).also { cached = it }
    }

    private fun write(value: Accounts) {
        try {
            file.write(GSON.toJson(value))
            cached = value
        } catch (_: Exception) {
            throw AiChatGptException(R.string.ai_chatgpt_storage_failed)
        }
    }
}

internal class AiEncryptedAccountFile(
    private val keyAlias: String,
    fileName: String,
    private val keySubject: String,
) {
    private val random = SecureRandom()
    private val file by lazy { AtomicFile(File(appCtx.noBackupFilesDir, fileName)) }

    fun exists(): Boolean = file.baseFile.exists()

    fun read(): String? {
        val envelope = try {
            file.readFully()
        } catch (_: FileNotFoundException) {
            return null
        }
        val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        rsa.init(Cipher.DECRYPT_MODE, keyStore().getKey(keyAlias, null))
        val keyLength = envelope.take(2).fold(0) { size, value -> (size shl 8) or (value.toInt() and 255) }
        val ivStart = 2 + keyLength
        val dataStart = ivStart + 12
        require(keyLength == 256 && envelope.size > dataStart)
        val key = rsa.doFinal(envelope, 2, keyLength)
        try {
            val iv = envelope.copyOfRange(ivStart, dataStart)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(keyAlias.toByteArray(Charsets.UTF_8))
            return cipher.doFinal(envelope, dataStart, envelope.size - dataStart).toString(Charsets.UTF_8)
        } finally {
            key.fill(0)
        }
    }

    fun write(value: String) {
        val key = ByteArray(32).also(random::nextBytes)
        try {
            val iv = ByteArray(12).also(random::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(keyAlias.toByteArray(Charsets.UTF_8))
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val store = keyStore()
            if (!store.containsAlias(keyAlias)) createKey()
            val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            rsa.init(Cipher.ENCRYPT_MODE, keyStore().getCertificate(keyAlias).publicKey)
            val wrappedKey = rsa.doFinal(key)
            val output = file.startWrite()
            try {
                output.write(byteArrayOf((wrappedKey.size shr 8).toByte(), wrappedKey.size.toByte()))
                output.write(wrappedKey)
                output.write(iv)
                output.write(encrypted)
                file.finishWrite(output)
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
        } finally {
            key.fill(0)
        }
    }

    @Suppress("DEPRECATION")
    private fun createKey() {
        val start = Calendar.getInstance()
        val end = (start.clone() as Calendar).apply { add(Calendar.YEAR, 30) }
        KeyPairGenerator.getInstance("RSA", "AndroidKeyStore").apply {
            initialize(KeyPairGeneratorSpec.Builder(appCtx)
                .setAlias(keyAlias).setKeySize(2048)
                .setSubject(X500Principal("CN=$keySubject"))
                .setSerialNumber(BigInteger.ONE).setStartDate(start.time).setEndDate(end.time).build())
            generateKeyPair()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}

internal val AiChatGptAccountStore = AiOpenAiAccountStore("legado.ai.chatgpt.accounts.v1", "chatgpt-accounts")
