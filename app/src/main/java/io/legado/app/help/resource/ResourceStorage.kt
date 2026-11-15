package io.legado.app.help.resource

import android.util.AtomicFile
import io.legado.app.utils.GSON
import io.legado.app.utils.externalFiles
import splitties.init.appCtx
import java.io.File
import java.security.MessageDigest

internal object ResourceFiles {
    val root: File get() = File(appCtx.noBackupFilesDir, "resource-center").apply { mkdirs() }
    val backgrounds: File get() = File(appCtx.externalFiles, "bg/resource-center")
    val fonts: File get() = File(appCtx.externalFiles, "font/resource-center")
    fun backgroundFiles(): List<File> = backgrounds.listFiles()?.filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }.orEmpty()
    fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun writeJson(file: File, value: Any) = writeBytes(file, GSON.toJson(value).toByteArray(Charsets.UTF_8))

    fun writeBytes(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            output.write(bytes)
            atomic.finishWrite(output)
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        }
    }

    fun <T> readJson(file: File, type: Class<T>): T? {
        val atomic = AtomicFile(file)
        if (!file.exists() && !File(file.path + ".bak").exists()) return null
        return atomic.openRead().use { input ->
            GSON.fromJson(input.reader(Charsets.UTF_8), type) ?: error("资源记录为空")
        }
    }
}

/** 单项写前日志：进程在业务入库后退出，可按真实内容指纹补齐版本记录。 */
internal object ResourceInstallStore {
    private val ledgerFile get() = File(ResourceFiles.root, "installed.json")
    private val journalFile get() = File(ResourceFiles.root, "pending.json")
    private val fontDeletionFile get() = File(ResourceFiles.root, "pending-font-deletion.json")
    private val fontCleanupFile get() = File(ResourceFiles.root, "font-cache-cleanup.json")

    fun load(): List<ResourceInstallation> {
        val ledger = ResourceFiles.readJson(ledgerFile, ResourceInstallLedger::class.java) ?: return emptyList()
        require(ledger.schemaVersion == 1) { "不支持的资源安装记录" }
        require(ledger.installations.map { it.entry.item.id }.distinct().size == ledger.installations.size) { "资源安装记录重复" }
        ledger.installations.forEach {
            require(it.repository == ResourceProtocol.REPOSITORY && it.target.isNotBlank() && it.baseline.matches(Regex("[a-f0-9]{64}"))) { "资源安装记录损坏" }
            ResourceProtocol.validateCatalog(ResourceCatalog(1, it.entry.sourceCommit, listOf(it.entry.category)))
            ResourceProtocol.validateIndex(
                ResourceCatalog(1, it.entry.sourceCommit),
                it.entry.category.copy(count = 1),
                ResourceIndex(1, it.entry.sourceCommit, it.entry.category.type, listOf(it.entry.item)),
            )
            ResourceProtocol.validatePreviewSource(it.entry)
        }
        return ledger.installations
    }

    fun pending(): ResourceInstallation? = ResourceFiles.readJson(journalFile, ResourceInstallation::class.java)
    fun prepare(record: ResourceInstallation) = ResourceFiles.writeJson(journalFile, record)
    fun clearPending() = AtomicFile(journalFile).delete()
    fun save(records: List<ResourceInstallation>) = ResourceFiles.writeJson(ledgerFile, ResourceInstallLedger(installations = records))

    fun pendingFontDeletion(): ResourceFontDeletion? =
        ResourceFiles.readJson(fontDeletionFile, ResourceFontDeletion::class.java)?.also { it.validate() }

    fun prepareFontDeletion(deletion: ResourceFontDeletion) {
        deletion.validate()
        ResourceFiles.writeJson(fontDeletionFile, deletion)
    }

    fun clearFontDeletion() = AtomicFile(fontDeletionFile).delete()

    fun fontCacheCleanup(): Set<String> {
        val hashes = ResourceFiles.readJson(fontCleanupFile, ResourceFontCacheCleanup::class.java)?.hashes.orEmpty()
        require(hashes.all(ResourceFontDeletionPolicy::validHash)) { "字体缓存清理记录损坏" }
        return hashes.toSet()
    }

    fun saveFontCacheCleanup(hashes: Collection<String>) {
        require(hashes.all(ResourceFontDeletionPolicy::validHash)) { "字体缓存校验信息无效" }
        if (hashes.isEmpty()) AtomicFile(fontCleanupFile).delete()
        else ResourceFiles.writeJson(fontCleanupFile, ResourceFontCacheCleanup(hashes.distinct()))
    }
}

