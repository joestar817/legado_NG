package io.legado.app.help.tts.packages

import android.content.Context
import android.net.Uri
import android.os.Build
import android.system.Os
import android.system.OsConstants
import androidx.annotation.RequiresApi
import io.legado.app.help.tts.TtsVoice
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import splitties.init.appCtx
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.Semaphore

/** An imported package is not a playable TTS engine until the offline runtime is connected. */
internal data class MicrosoftTtsBundleSummary(
    val voices: List<String>,
    val engineVersion: String,
    val publisherName: String,
    val signer: String,
    val hasBundledLicense: Boolean,
)

internal data class MicrosoftTtsCatalog(
    val revision: String,
    val voices: List<TtsVoice>,
    val hasLicense: Boolean,
)

/** The same serialized package store is shared by local import and later resource installation. */
@RequiresApi(26)
internal object MicrosoftTtsBundleManager {
    private const val MAX_IMPORT_BYTES = 2L * 1024 * 1024 * 1024
    private val gate = Semaphore(1)
    private fun root(context: Context) = File(context.noBackupFilesDir, "tts-offline-microsoft")
    private fun lockFile(context: Context) = File(context.noBackupFilesDir, "tts-offline-microsoft.lock")

    private class Lease(private val file: RandomAccessFile, private val lock: FileLock) : AutoCloseable {
        override fun close() {
            try { lock.release() } finally {
                try { file.close() } finally { gate.release() }
            }
        }
    }

    private fun acquire(context: Context): Lease {
        check(gate.tryAcquire()) { "离线语音包正在处理，请稍后重试" }
        var file: RandomAccessFile? = null
        try {
            val lockPath = lockFile(context)
            lockPath.parentFile?.mkdirs()
            file = RandomAccessFile(lockPath, "rw")
            val lock = file.channel.tryLock() ?: error("离线语音包正在处理，请稍后重试")
            return Lease(file, lock)
        } catch (error: Throwable) {
            try { file?.close() } finally { gate.release() }
            throw error
        }
    }

    private fun device(abi: String): MicrosoftTtsPackageStore.Device {
        val pageSize = (Os.sysconf(OsConstants._SC_PAGESIZE) / 1024L).toInt()
        return MicrosoftTtsPackageStore.Device(abi, Build.VERSION.SDK_INT, pageSize)
    }

    internal fun processDevice(): MicrosoftTtsPackageStore.Device {
        MicrosoftOfflineSupport.currentUnavailableReason()?.let { error(it) }
        val abi = when (System.getProperty("os.arch")?.lowercase()) {
            "aarch64", "arm64" -> "arm64-v8a"
            else -> error("当前进程架构不支持微软离线语音")
        }
        return device(abi)
    }

    internal fun <T> useStore(context: Context, action: (MicrosoftTtsPackageStore) -> T): T {
        return acquire(context).use { action(openStore(context)) }
    }

    internal fun licenseFile(context: Context) = File(root(context), ".model-license")

    fun hasLicense(): Boolean = licenseFile(appCtx).let { it.isFile && it.length() in 1..65_536L }

    fun importLicense(uri: Uri) {
        val context = appCtx
        useStore(context) {
            val temporary = File(root(context), ".license-${UUID.randomUUID()}.tmp")
            try {
                requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取许可文件" }.use { input ->
                    val buffer = ByteArray(8192)
                    val collected = ByteArrayOutputStream()
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(collected.size() + count <= 65_536) { "许可文件为空或过大" }
                        collected.write(buffer, 0, count)
                    }
                    val bytes = collected.toByteArray()
                    try {
                        require(bytes.isNotEmpty()) { "许可文件为空或过大" }
                        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes))
                        require(text.isNotBlank() && !text.contains('\u0000')) { "许可文件格式无效" }
                        FileOutputStream(temporary).use { output ->
                            output.write(bytes)
                            output.fd.sync()
                        }
                    } finally {
                        bytes.fill(0)
                        buffer.fill(0)
                    }
                }
                Files.move(temporary.toPath(), licenseFile(context).toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temporary.delete()
            }
        }
    }

    fun catalog(): MicrosoftTtsCatalog = useStore(appCtx) { store ->
        val graph = store.latestGraph(processDevice())
        val voicePackages = graph.entries.mapNotNull { (id, digest) ->
            val item = store.readInstalled(digest)
            if (item.manifest.contentRole == "voices") id to item else null
        }
        val voices = store.catalogVoices(graph).map {
            TtsVoice(id = it.voiceId, name = it.name, language = it.locale, gender = it.gender)
        }
        require(voices.isNotEmpty() && voices.map { it.id }.distinct().size == voices.size) {
            "离线语音包的发音人为空或存在重复 ID"
        }
        MicrosoftTtsCatalog(
            revision = encodeMicrosoftOfflineGraph(graph),
            voices = voices,
            hasLicense = hasLicense() || voicePackages.all { (id, _) ->
                store.credential(graph, id, "model_license") != null
            },
        )
    }

    /** A committed registry may need its Room/preference projection rebuilt after interruption. */
    fun hasInstalledCatalog(): Boolean = File(root(appCtx), "registry.json").isFile

    fun installedFingerprint(): String? = if (!hasInstalledCatalog()) null else
        fingerprint(MicrosoftTtsPackageStore.readOnly(root(appCtx)))

    private fun fingerprint(store: MicrosoftTtsPackageStore): String? {
        if (store.snapshot().installed.isEmpty()) return null
        val graph = store.catalogPackages().associate { it.manifest.packageId to it.digest }
        return microsoftOfflineCatalogFingerprint(graph, store.catalogVoices(graph).map { it.voiceId })
    }

    /** Read only the pinned engine manifest; do not recover or mutate the package store here. */
    fun revisionUnavailableReason(revision: String?): String? {
        MicrosoftOfflineSupport.currentEngineUnavailableReason(revision)?.let { return it }
        return runCatching<String?> {
            val graph = decodeMicrosoftOfflineGraph(revision.orEmpty())
            val engine = MicrosoftTtsPackageStore.runtimeObject(
                root(appCtx), graph.getValue(MicrosoftTtsPackageStore.ENGINE)
            )
            val device = processDevice()
            check(MicrosoftOfflineSupport.runtimeCompatible(
                engine.manifest.android.abi, device.abi, device.sdk,
                engine.manifest.android.minSdk, device.pageSizeKb, engine.manifest.android.pageSizes,
            ))
            null
        }.getOrElse { "离线语音包不适配当前运行环境" }
    }

    class Candidate internal constructor(
        private val store: MicrosoftTtsPackageStore,
        private val plan: MicrosoftTtsPackageStore.Plan,
        private val lease: AutoCloseable,
        val summary: MicrosoftTtsBundleSummary,
        val name: String,
        val versionName: String,
        val packageId: String,
        val fingerprint: String,
        val previousFingerprint: String?,
        val newComponents: Int,
        val reusedComponents: Int,
        val trustRequired: Map<String, String>,
        val processCompatible: Boolean,
    ) : AutoCloseable {
        private var finished = false

        @Synchronized
        fun commit(): MicrosoftTtsBundleSummary {
            check(!finished) { "导入已结束，请重新选择文件" }
            var committed = false
            try {
                // Confirmation in the import preview is the trust decision for this exact plan.
                store.install(plan, plan.trustRequired.values.toSet())
                committed = true
                return summary
            } finally {
                finished = true
                try {
                    if (committed) runCatching { plan.close() } else plan.close()
                } finally { lease.close() }
            }
        }

        @Synchronized
        override fun close() {
            if (finished) return
            finished = true
            try { plan.close() } finally { lease.close() }
        }
    }

    suspend fun inspect(uri: Uri): Candidate {
        val context = appCtx
        val lease = acquire(context)
        var plan: MicrosoftTtsPackageStore.Plan? = null
        var handedOff = false
        var incoming: File? = null
        try {
            val store = openStore(context)
            val incomingFile = File(root(context), ".incoming-${UUID.randomUUID()}.zip")
            incoming = incomingFile
            val coroutine = currentCoroutineContext()
            requireNotNull(appCtx.contentResolver.openInputStream(uri)) { "无法读取所选文件" }.use { input ->
                FileOutputStream(incomingFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        coroutine.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        require(copied <= MAX_IMPORT_BYTES) { "离线语音包超过 2 GiB 限额" }
                        output.write(buffer, 0, count)
                    }
                    require(copied > 0) { "离线语音包为空" }
                    output.fd.sync()
                }
            }
            coroutine.ensureActive()
            val inspected = store.inspect(incomingFile, device("*")) { coroutine.ensureActive() }
            plan = inspected
            val engine = inspected.packages.single { it.manifest.kind == "engine" }
            require(MicrosoftOfflineSupport.supportsPackageAbi(engine.manifest.android.abi)) {
                "微软离线语音包仅支持 ARM64 架构"
            }
            require(engine.manifest.android.abi in Build.SUPPORTED_ABIS) { "此设备不支持该整合包的处理器架构" }
            coroutine.ensureActive()
            val process = runCatching { processDevice() }.getOrNull()
            val processCompatible = process != null && MicrosoftOfflineSupport.runtimeCompatible(
                engine.manifest.android.abi, process.abi, process.sdk,
                engine.manifest.android.minSdk, process.pageSizeKb, engine.manifest.android.pageSizes,
            )
            val result = Candidate(store, inspected, lease, summary(inspected.packages, inspected.voices),
                inspected.bundleName, inspected.bundleVersionName,
                inspected.bundlePackageId,
                microsoftOfflineCatalogFingerprint(inspected.catalog.components, inspected.catalog.voiceIds),
                fingerprint(store),
                inspected.packages.size - inspected.reusable, inspected.reusable,
                inspected.trustRequired.toMap(), processCompatible)
            handedOff = true
            return result
        } finally {
            incoming?.delete()
            if (!handedOff) {
                try { plan?.close() } finally { lease.close() }
            }
        }
    }

    fun installed(): MicrosoftTtsBundleSummary? {
        val context = appCtx
        if (!root(context).exists()) return null
        acquire(context).use {
            val store = openStore(context)
            if (store.snapshot().installed.isEmpty()) return null
            val packages = store.catalogPackages()
            val graph = packages.associate { it.manifest.packageId to it.digest }
            return summary(packages, store.catalogVoices(graph))
        }
    }

    private fun summary(
        items: List<MicrosoftTtsPackageStore.Installed>,
        selectedVoices: List<MicrosoftTtsPackageStore.Voice>,
    ): MicrosoftTtsBundleSummary {
        val engine = items.singleOrNull { it.manifest.kind == "engine" }
            ?: error("整合包缺少微软离线引擎")
        val voicePackages = items.filter { it.manifest.contentRole == "voices" }
        val voices = selectedVoices.map { it.name }
        require(voices.isNotEmpty()) { "整合包没有发音人" }
        return MicrosoftTtsBundleSummary(voices, engine.manifest.versionName,
            engine.manifest.publisher.name, requireNotNull(engine.signer) { "离线引擎缺少签名" },
            voicePackages.all { it.manifest.credentials?.containsKey("model_license") == true })
    }

    fun remove(): Boolean {
        val context = appCtx
        val root = root(context)
        if (!root.exists()) return true
        return acquire(context).use {
            val store = openStore(context)
            check(store.snapshot().active.isEmpty()) { "离线语音正在使用，请先停止朗读" }
            val target = File(root.parentFile, ".tts-offline-removed-${UUID.randomUUID()}")
            Files.move(root.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            runCatching { deleteTree(target) }.isSuccess
        }
    }

    private fun openStore(context: Context): MicrosoftTtsPackageStore {
        val root = root(context)
        root.parentFile?.listFiles()?.filter {
            it.name.matches(Regex("\\.tts-offline-removed-[0-9a-f-]{36}"))
        }?.forEach { runCatching { deleteTree(it) } }
        val store = MicrosoftTtsPackageStore(root)
        root.listFiles()?.filter { it.isFile && it.name.matches(Regex("\\.incoming-[0-9a-f-]{36}\\.zip")) }
            ?.forEach { check(it.delete()) { "无法清理上次未完成的导入" } }
        root.listFiles()?.filter { it.isFile && it.name.matches(Regex("\\.license-[0-9a-f-]{36}\\.tmp")) }
            ?.forEach { check(it.delete()) { "无法清理上次未完成的许可导入" } }
        return store
    }

    private fun deleteTree(file: File) {
        if (Files.isSymbolicLink(file.toPath())) {
            Files.deleteIfExists(file.toPath())
            return
        }
        if (file.isDirectory) file.listFiles()?.forEach(::deleteTree)
            ?: error("无法清理离线语音文件")
        Files.deleteIfExists(file.toPath())
    }
}
