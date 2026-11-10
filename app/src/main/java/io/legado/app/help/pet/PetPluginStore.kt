package io.legado.app.help.pet

import android.util.AtomicFile
import androidx.annotation.Keep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.gson.annotations.SerializedName
import dalvik.system.DexClassLoader
import io.legado.pet.api.PetAssets
import io.legado.pet.api.PetPlugin
import android.util.Log
import java.io.Closeable
import io.legado.app.utils.GSON
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile

/** Only this small, stable ABI is supplied by the host; character code stays in the package. */
@Keep
internal data class PetPluginManifest(
    @SerializedName("schemaVersion") val schemaVersion: Int = 0,
    @SerializedName("kind") val kind: String = "",
    @SerializedName("apiVersion") val apiVersion: Int = 0,
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("versionCode") val versionCode: Long = 0,
    @SerializedName("entryClass") val entryClass: String = "",
)

@Keep
internal data class InstalledPetPlugin(
    @SerializedName("manifest") val manifest: PetPluginManifest,
    @SerializedName("sha256") val sha256: String,
    @SerializedName("info") val info: PetPackageInfo? = null,
    @SerializedName("origin") val origin: PetInstallOrigin? = null,
    @SerializedName("installedAt") val installedAt: Long? = null,
)

internal class PetPluginHandle(
    val id: String,
    val provider: PetPlugin,
    val file: File,
    val sha256: String,
) {
    fun openAssets() = PetAssetLease(ZipFile(file))
}

/** An open descriptor keeps this revision readable during replacement or removal. */
internal class PetAssetLease(private val archive: ZipFile) : PetAssets, Closeable {
    override fun open(path: String): InputStream {
        require(PetPackageInspector.safePath(path)) { "宠物素材路径无效" }
        return archive.getInputStream(archive.getEntry("assets/$path") ?: error("宠物素材缺失"))
    }

    override fun close() = archive.close()
}

internal fun Throwable.rethrowFatalPetError() {
    when (this) {
        is VirtualMachineError, is ThreadDeath -> throw this
        is java.lang.reflect.InvocationTargetException -> targetException?.rethrowFatalPetError()
        is ExceptionInInitializerError -> exception?.rethrowFatalPetError()
    }
}

internal data class PetChoice(val id: String, val name: String)

internal object PetIdentity {
    private val identifier = Regex("[a-z][a-z0-9_]{0,63}")
    const val CAPABILITY = "pet.api.2"

    fun valid(id: String) = id != "button" && id != "none" && identifier.matches(id)
}

/** Share platform types and the public SDK. Dependencies bundled by the author are plugin-local. */
private class PetApiParent(private val host: ClassLoader) :
    ClassLoader(android.content.Context::class.java.classLoader) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> =
        if (name.startsWith("io.legado.pet.api.")) host.loadClass(name) else super.loadClass(name, resolve)
}

/** Validate bytes and metadata before any plugin class can run. No ZIP entry is extracted. */
internal object PetPackageInspector {
    private val entryClass = Regex("""(?:[A-Za-z_$][A-Za-z0-9_$]*\.)+[A-Za-z_$][A-Za-z0-9_$]*""")

    fun safePath(name: String) = name.isNotBlank() && !name.contains('\\') &&
        name.split('/').all { it.isNotBlank() && it != "." && it != ".." } &&
        name.none { Character.isISOControl(it) || it == ':' }

    fun validate(manifest: PetPluginManifest) {
        require(manifest.schemaVersion == 2 && manifest.kind == "pet" && manifest.apiVersion == 2) { "宠物插件版本不兼容" }
        require(PetIdentity.valid(manifest.id) && manifest.name.isNotBlank() && manifest.versionCode > 0 &&
            entryClass.matches(manifest.entryClass)) { "宠物插件信息无效" }
    }

    fun inspect(file: File): PetPluginManifest {
        require(file.length() in 1..PetPackageDisplay.MAX_PACKAGE_BYTES) { "宠物插件文件过大或为空" }
        return ZipFile(file).use { archive ->
            val names = mutableSetOf<String>()
            var expanded = 0L
            val entries = archive.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name.removeSuffix("/")
                require(safePath(name) && names.add(name)) { "宠物插件含无效或重复路径" }
                require(entry.size in 0..(64L * 1024 * 1024)) { "宠物插件内容过大" }
                expanded += entry.size
                require(expanded <= 128L * 1024 * 1024 && names.size <= 512) { "宠物插件内容过大" }
                require(name != "lib" && !name.startsWith("lib/")) { "宠物插件不支持原生库" }
                if (!entry.isDirectory) {
                    var actual = 0L
                    val crc = java.util.zip.CRC32()
                    archive.getInputStream(entry).use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            actual += count
                            require(actual <= entry.size) { "宠物插件内容大小不一致" }
                            crc.update(buffer, 0, count)
                        }
                    }
                    require(actual == entry.size && crc.value == entry.crc) { "宠物插件内容校验失败" }
                }
            }
            require("classes.dex" in names) { "宠物插件缺少编译代码" }
            val magic = ByteArray(4)
            java.io.DataInputStream(archive.getInputStream(archive.getEntry("classes.dex"))).use { it.readFully(magic) }
            require(magic.contentEquals(byteArrayOf(100, 101, 120, 10))) { "宠物插件编译代码无效" }
            val entry = archive.getEntry("assets/plugin.json") ?: error("宠物插件缺少清单")
            require(entry.size in 1..16384) { "宠物插件清单无效" }
            val manifest = GSON.fromJson(
                PetPackageDisplay.readBounded(archive.getInputStream(entry), 16384).toString(Charsets.UTF_8),
                PetPluginManifest::class.java
            ) ?: error("宠物插件清单无效")
            validate(manifest)
            PetDexInspector.validate(archive, manifest)
            manifest
        }
    }
}

internal object PetPluginStore {
    private val root get() = File(appCtx.filesDir, "pet-plugins").apply { mkdirs() }
    private val loaded = mutableMapOf<String, PetPluginHandle>()
    private val failures = mutableMapOf<String, PetFailure>()
    private val mutableRevision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = mutableRevision.asStateFlow()
    private fun changed() { mutableRevision.value += 1 }

    @Synchronized
    fun invalidateLoad(id: String) {
        require(PetIdentity.valid(id)) { "宠物身份无效" }
        loaded.remove(id)
        failures.remove(id)
        changed()
    }

    @Synchronized
    fun reportFailure(id: String, stage: String, error: Throwable) {
        error.rethrowFatalPetError()
        val safeStage = stage.takeIf { it in setOf("record", "api", "package", "entry", "session", "render", "state", "runtime") } ?: "runtime"
        failures[id] = PetFailure(safeStage, "宠物运行失败，请重新加载或重新导入", error.javaClass.simpleName.take(80))
        loaded.remove(id)
    }

    private fun pointerSnapshot(id: String): String? {
        val atomic = pointer(id)
        if (!atomic.baseFile.exists() && !File(atomic.baseFile.path + ".bak").exists()) return null
        return atomic.openRead().use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    @Synchronized
    fun record(id: String): PetLocalRecord? {
        if (!PetIdentity.valid(id)) return null
        val atomic = pointer(id)
        if (!atomic.baseFile.exists() && !File(atomic.baseFile.path + ".bak").exists()) return null
        val receipt = try {
            atomic.openRead().use {
                GSON.fromJson(PetPackageDisplay.readBounded(it, 32768).toString(Charsets.UTF_8), InstalledPetPlugin::class.java)
            }.also {
                require(it != null && it.manifest.id == id && Regex("[a-f0-9]{64}").matches(it.sha256))
                it.origin?.validate(it.sha256)
            }
        } catch (error: Exception) {
            return PetLocalRecord(id, null, null, null, 0, null, null, PetLocalStatus.CORRUPT, PetFailure("record", "安装记录损坏"))
        }
        val file = packageFile(receipt)
        var info = receipt.info
        var failure: PetFailure? = null
        val status = when {
            !file.isFile -> PetLocalStatus.MISSING.also { failure = PetFailure("package", "安装包文件缺失") }
            receipt.manifest.apiVersion != 2 || receipt.manifest.schemaVersion != 2 ->
                PetLocalStatus.INCOMPATIBLE.also { failure = PetFailure("api", "宠物接口版本不兼容") }
            else -> try {
                require(!file.canWrite() && hash(file) == receipt.sha256)
                require(PetPackageInspector.inspect(file) == receipt.manifest)
                info = PetPackageDisplay.info(file)
                failure = failures[id]
                if (failure == null) PetLocalStatus.READY else PetLocalStatus.LOAD_ERROR
            } catch (error: Exception) {
                failure = PetFailure("inspect", "安装包校验失败")
                PetLocalStatus.CORRUPT
            }
        }
        return PetLocalRecord(id, receipt.manifest, info, receipt.sha256, file.length(), receipt.origin,
            receipt.installedAt, status, failure)
    }

    @Synchronized
    fun records(): List<PetLocalRecord> = root.listFiles().orEmpty()
        .mapNotNull { it.name.removeSuffix(".bak").takeIf { name -> name.endsWith(".json") }?.removeSuffix(".json") }
        .distinct().filter(PetIdentity::valid).mapNotNull(::record)
        .sortedWith(compareBy({ it.manifest?.name ?: it.id }, { it.id }))

    @Synchronized
    fun readPreview(id: String): ByteArray? {
        val receipt = installed(id) ?: return null
        val file = packageFile(receipt)
        require(file.isFile && hash(file) == receipt.sha256) { "宠物插件校验失败" }
        return PetPackageDisplay.preview(file, PetPackageDisplay.info(file))
    }

    @Synchronized
    fun exportFile(id: String): File {
        val receipt = installed(id) ?: error("宠物未安装")
        require(receipt.manifest.schemaVersion == 2) { "旧版宠物请先更新后导出" }
        return packageFile(receipt).also { file ->
            require(file.isFile && !file.canWrite() && hash(file) == receipt.sha256) { "宠物插件校验失败" }
        }
    }

    @Synchronized
    fun diagnostics(id: String): String {
        val item = record(id) ?: error("宠物未安装")
        return buildString {
            appendLine("pluginId=${item.id}")
            appendLine("versionName=${item.info?.versionName ?: "unknown"}")
            appendLine("versionCode=${item.manifest?.versionCode ?: "unknown"}")
            appendLine("hostVersion=${io.legado.app.BuildConfig.VERSION_NAME}")
            appendLine("apiVersion=${item.manifest?.apiVersion ?: "unknown"}")
            appendLine("sha256=${item.sha256 ?: "unknown"}")
            appendLine("status=${item.status}")
            item.error?.let { appendLine("stage=${it.stage}"); appendLine("error=${it.message}"); appendLine("type=${it.type ?: "unknown"}") }
        }
    }

    private fun pointer(id: String): AtomicFile {
        require(PetIdentity.valid(id)) { "宠物身份无效" }
        return AtomicFile(File(root, "$id.json"))
    }

    @Synchronized
    private fun installed(id: String): InstalledPetPlugin? = runCatching {
        pointer(id).openRead().use {
            GSON.fromJson(PetPackageDisplay.readBounded(it, 32768).toString(Charsets.UTF_8), InstalledPetPlugin::class.java)
        }
            .also { record ->
                val manifest = record.manifest
                // Keep old receipts visible for updates without loading the obsolete interface.
                if (manifest.schemaVersion != 1 || manifest.apiVersion != 1) PetPackageInspector.validate(manifest)
                require(record.manifest.id == id && Regex("[a-f0-9]{64}").matches(record.sha256))
            }
    }.getOrNull()

    private fun packageFile(record: InstalledPetPlugin): File {
        val suffix = if (record.manifest.schemaVersion == 1) "ngpet" else "zip"
        val id = record.manifest.id
        val digest = record.sha256
        return File(root, "$id-$digest.$suffix")
    }

    @Synchronized
    fun file(id: String): File? = installed(id)?.let(::packageFile)?.takeIf { it.isFile }

    @Synchronized
    fun installedChoices(): List<PetChoice> = root.listFiles().orEmpty()
        .mapNotNull { it.name.removeSuffix(".bak").takeIf { name -> name.endsWith(".json") }?.removeSuffix(".json") }
        .distinct()
        .filter(PetIdentity::valid)
        .mapNotNull(::installed)
        .filter { it.manifest.apiVersion == 2 && packageFile(it).isFile }
        .map { PetChoice(it.manifest.id, it.manifest.name) }
        .sortedWith(compareBy(PetChoice::name, PetChoice::id))

    @Synchronized
    fun available(id: String) = file(id) != null

    private fun entry(file: File, manifest: PetPluginManifest): Class<out PetPlugin> {
        val optimized = File(appCtx.codeCacheDir, "pet-plugins").apply { mkdirs() }
        val parent = PetApiParent(requireNotNull(PetPlugin::class.java.classLoader))
        val loader = DexClassLoader(file.path, optimized.path, null, parent)
        val type = loader.loadClass(manifest.entryClass).asSubclass(PetPlugin::class.java)
        require(type.classLoader === loader && !java.lang.reflect.Modifier.isAbstract(type.modifiers)) {
            "宠物插件入口无效"
        }
        return type
    }

    @Synchronized
    fun load(id: String): PetPluginHandle? {
        val record = installed(id) ?: return null
        if (record.manifest.apiVersion != 2) {
            failures[id] = PetFailure("api", "宠物接口版本不兼容")
            return null
        }
        val file = packageFile(record)
        if (!file.isFile) {
            failures[id] = PetFailure("package", "安装包文件缺失")
            return null
        }
        loaded[id]?.takeIf { it.sha256 == record.sha256 }?.let { return it }
        return try {
            require(!file.canWrite() && hash(file) == record.sha256) { "宠物插件校验失败" }
            require(PetPackageInspector.inspect(file) == record.manifest) { "宠物插件文件无效" }
            val provider = entry(file, record.manifest).getConstructor().newInstance()
            require(provider.id == id) { "宠物插件身份不一致" }
            failures.remove(id)
            PetPluginHandle(id, provider, file, record.sha256).also { loaded[id] = it }
        } catch (error: Throwable) {
            error.rethrowFatalPetError()
            reportFailure(id, "entry", error)
            Log.w("PetPlugin", "load failed: $id / " + error.javaClass.simpleName)
            null
        }
    }

    /** Copy while the open descriptor is writable, but the name is already read-only (Android 14 DCL). */
    @Synchronized
    fun prepare(payload: File, expectedId: String, version: Long, sha256: String,
                origin: PetInstallOrigin? = null): PreparedPetPlugin {
        require(hash(payload) == sha256) { "宠物插件校验失败" }
        val manifest = PetPackageInspector.inspect(payload)
        require(manifest.id == expectedId && manifest.versionCode == version) { "宠物插件身份或版本不一致" }
        origin?.validate(sha256)
        val info = PetPackageDisplay.info(payload)
        val snapshot = pointerSnapshot(expectedId)
        val previous = record(expectedId)
        val staged = File(root, ".${UUID.randomUUID()}.zip")
        try {
            FileOutputStream(staged).use { output ->
                check(staged.setReadOnly()) { "无法保护宠物插件文件" }
                payload.inputStream().use { PetPackageDisplay.copyBounded(it, output, PetPackageDisplay.MAX_PACKAGE_BYTES) }
                output.fd.sync()
            }
            check(hash(staged) == sha256) { "宠物插件校验失败" }
        } catch (error: Throwable) {
            staged.delete()
            throw error
        }
        return PreparedPetPlugin(staged, InstalledPetPlugin(manifest, sha256, info, origin, System.currentTimeMillis()), snapshot, previous)
    }

    class PreparedPetPlugin internal constructor(
        private val staged: File,
        private val record: InstalledPetPlugin,
        private val snapshot: String?,
        val previous: PetLocalRecord?,
    ) {
        val info get() = record.info
        val action = PetImportPolicy.action(previous, record.manifest, record.sha256)
        private var finished = false

        fun readPreview(): ByteArray? = synchronized(PetPluginStore) {
            check(!finished) { "导入候选已关闭" }
            PetPackageDisplay.preview(staged, record.info)
        }

        fun commit(allowReplace: Boolean = false, allowDowngrade: Boolean = false): Boolean = synchronized(PetPluginStore) {
            check(!finished) { "导入候选已关闭" }
            val id = record.manifest.id
            check(pointerSnapshot(id) == snapshot) { "宠物安装状态已变化，请重新选择文件确认" }
            PetImportPolicy.requirePermission(action, allowReplace, allowDowngrade)
            if (action == PetImportAction.NO_OP) {
                val current = file(id)
                check(current != null && !current.canWrite() && hash(current) == record.sha256) {
                    "宠物安装文件已变化，请重新选择文件确认"
                }
                discard()
                return@synchronized false
            }
            check(staged.isFile && !staged.canWrite() && hash(staged) == record.sha256) { "导入候选文件校验失败" }
            val oldFile = file(id)
            val target = packageFile(record)
            val reuse = target.isFile && !target.canWrite() && hash(target) == record.sha256
            val atomic = pointer(id)
            // Preserve the exact previous pointer, including a corrupt receipt being repaired.
            // This is ready before either the content file or the pointer can change.
            val rollback = if (snapshot == null) null else File.createTempFile(".rollback-", ".json", root).also { backup ->
                try {
                    FileOutputStream(backup).use { output ->
                        atomic.openRead().use { it.copyTo(output) }
                        output.fd.sync()
                    }
                    check(hash(backup) == snapshot) { "宠物安装记录备份失败" }
                } catch (error: Throwable) {
                    backup.delete()
                    throw error
                }
            }
            val encoded = GSON.toJson(record).toByteArray(Charsets.UTF_8)
            try {
                if (!reuse) android.system.Os.rename(staged.path, target.path)
                val output = atomic.startWrite()
                try {
                    output.write(encoded)
                    // AtomicFile.finishWrite logs some sync/rename failures instead of throwing.
                    output.fd.sync()
                    atomic.finishWrite(output)
                } catch (error: Throwable) {
                    atomic.failWrite(output)
                    throw error
                }
                val written = atomic.openRead().use { PetPackageDisplay.readBounded(it, 32768) }
                check(written.contentEquals(encoded) && installed(id) == record &&
                    target.isFile && !target.canWrite() && hash(target) == record.sha256) {
                    "宠物安装记录写入校验失败"
                }
            } catch (error: Throwable) {
                finished = true
                try {
                    if (rollback != null) {
                        android.system.Os.rename(rollback.path, atomic.baseFile.path)
                        File(atomic.baseFile.path + ".new").delete()
                        File(atomic.baseFile.path + ".bak").delete()
                    } else atomic.delete()
                    check(pointerSnapshot(id) == snapshot) { "宠物安装记录恢复校验失败" }
                } catch (restoreError: Throwable) {
                    error.addSuppressed(restoreError)
                    // Keep both content revisions and any rollback file for recovery.
                    throw IllegalStateException("宠物安装失败且记录恢复失败，请查看本地诊断", error)
                }
                if (!reuse && target != oldFile) target.delete()
                throw error
            }
            rollback?.delete()
            finished = true
            staged.delete()
            loaded.remove(id)
            failures.remove(id)
            changed()
            if (oldFile != target) oldFile?.delete()
            true
        }

        fun discard() = synchronized(PetPluginStore) {
            staged.delete()
            finished = true
        }
    }

    @Synchronized
    fun remove(id: String) {
        val oldFiles = root.listFiles().orEmpty().filter {
            it.isFile && it.name.matches(Regex("${Regex.escape(id)}-[a-f0-9]{64}\\.(zip|ngpet)"))
        }
        pointer(id).delete()
        check(!pointer(id).baseFile.exists()) { "宠物插件卸载失败" }
        loaded.remove(id)
        failures.remove(id)
        changed()
        oldFiles.forEach { check(it.delete() || !it.exists()) { "宠物插件文件删除失败" } }
    }

    fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            while (true) {
                val size = stream.read(buffer)
                if (size < 0) break
                digest.update(buffer, 0, size)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
