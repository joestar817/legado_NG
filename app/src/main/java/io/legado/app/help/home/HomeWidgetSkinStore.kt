package io.legado.app.help.home

import android.graphics.Bitmap
import android.net.Uri
import android.util.AtomicFile
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import io.legado.app.utils.GSON
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import splitties.init.appCtx
import java.io.File
import java.security.MessageDigest

@Keep
internal data class HomeWidgetSkinRecord(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("sha256") val sha256: String = "",
    @SerializedName("size") val size: Long = 0,
    @SerializedName("fromResource") val fromResource: Boolean = false,
) {
    fun validate() {
        require(HomeWidgetSkinRenderer.entries.any { it.resourceId == id } && name.isNotBlank() &&
            sha256.matches(Regex("[a-f0-9]{64}")) && size in 1..HomeWidgetSkinPackage.MAX_PACKAGE_BYTES
        ) { "组件皮肤记录无效" }
    }
}

@Keep
internal data class HomeWidgetSkinLedger(
    @SerializedName("schemaVersion") val schemaVersion: Int = 1,
    @SerializedName("skins") val skins: List<HomeWidgetSkinRecord> = emptyList(),
)

internal data class PreparedHomeWidgetSkin(
    val record: HomeWidgetSkinRecord,
    val archive: File,
    val previous: HomeWidgetSkinRecord?,
)

internal data class HomeWidgetSkinImport(
    val prepared: PreparedHomeWidgetSkin,
    val preview: Bitmap?,
    val alreadyInstalled: Boolean,
) {
    val previous get() = prepared.previous
    fun discard() { prepared.archive.delete() }
}

/** Component skins own their local files and selection independently of resource catalog receipts. */
internal object HomeWidgetSkinStore {
    private val lock = Any()
    private val root get() = File(appCtx.noBackupFilesDir, "home-widget-skins").apply { mkdirs() }
    private val ledger get() = AtomicFile(File(root, "installed.json"))
    private val mutableInstalled = MutableStateFlow<List<InstalledHomeWidgetSkin>>(emptyList())
    val installed = mutableInstalled.asStateFlow()
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()
    private val archiveName = Regex("ng\\.home_skin\\.(?:storybook|night_desk|blue_fish)-[a-f0-9]{64}\\.zip(?:\\.new|\\.bak)?")

    fun current(id: String): HomeWidgetSkinRecord? = synchronized(lock) { records().firstOrNull { it.id == id } }

    fun file(id: String): File? = synchronized(lock) { current(id)?.let(::archiveFile)?.takeIf { it.isFile } }

    fun fingerprint(id: String): String? = synchronized(lock) { file(id)?.let(::hash) }

    fun refresh() = synchronized(lock) { publish(records()) }

    fun prepare(archive: File, name: String? = null, fromResource: Boolean = false): PreparedHomeWidgetSkin {
        val renderer = HomeWidgetSkinPackage.read(archive)
        val record = HomeWidgetSkinRecord(
            renderer.resourceId, name ?: appCtx.getString(renderer.titleRes), hash(archive), archive.length(), fromResource,
        )
        record.validate()
        return PreparedHomeWidgetSkin(record, archive, current(record.id))
    }

    fun inspect(uri: Uri): HomeWidgetSkinImport {
        val archive = File.createTempFile("widget-skin-import-", ".zip", appCtx.cacheDir)
        try {
            requireNotNull(appCtx.contentResolver.openInputStream(uri)) { "无法读取皮肤文件" }.use { input ->
                archive.outputStream().buffered().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var size = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        size += count
                        require(size <= HomeWidgetSkinPackage.MAX_PACKAGE_BYTES) { "组件皮肤超过 8 MB 导入上限" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            val prepared = prepare(archive)
            val alreadyInstalled = synchronized(lock) { fingerprint(prepared.record.id) == prepared.record.sha256 }
            return HomeWidgetSkinImport(prepared, HomeWidgetSkinPackage.previewBitmap(archive, 2), alreadyInstalled)
        } catch (error: Throwable) {
            archive.delete()
            throw error
        }
    }

    fun install(prepared: PreparedHomeWidgetSkin) = synchronized(lock) {
        val record = prepared.record
        record.validate()
        require(prepared.archive.length() == record.size && hash(prepared.archive) == record.sha256) { "皮肤文件已变化，请重新选择" }
        require(HomeWidgetSkinPackage.read(prepared.archive).resourceId == record.id) { "皮肤身份不一致" }
        val saved = records()
        val previous = saved.firstOrNull { it.id == record.id }
        if (previous == record && fingerprint(record.id) == record.sha256) return@synchronized
        check(previous == prepared.previous) { "皮肤已变化，请重新操作" }
        val updated = saved.filterNot { it.id == record.id } + record
        write(AtomicFile(archiveFile(record))) { output -> prepared.archive.inputStream().use { it.copyTo(output) } }
        save(updated)
        publish(updated)
        mutableRevision.value++
        prune(updated)
    }

    fun remove(id: String, expectedRevision: String) = synchronized(lock) {
        val saved = records()
        val previous = saved.firstOrNull { it.id == id } ?: return@synchronized
        check(previous.sha256 == expectedRevision) { "皮肤已更新，请重新确认" }
        val remaining = saved - previous
        save(remaining)
        publish(remaining)
        mutableRevision.value++
        prune(remaining)
    }

    private fun records(): List<HomeWidgetSkinRecord> {
        val file = ledger
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return emptyList()
        val saved = file.openRead().use { GSON.fromJson(it.reader(Charsets.UTF_8), HomeWidgetSkinLedger::class.java) }
        require(saved?.schemaVersion == 1 && saved.skins.size <= HomeWidgetSkinRenderer.entries.size) { "组件皮肤记录无效" }
        saved.skins.forEach { it.validate() }
        require(saved.skins.map { it.id }.distinct().size == saved.skins.size) { "组件皮肤记录重复" }
        return saved.skins
    }

    private fun archiveFile(record: HomeWidgetSkinRecord): File {
        record.validate()
        return File(root, "${record.id}-${record.sha256}.zip")
    }

    private fun save(records: List<HomeWidgetSkinRecord>) = write(ledger) {
        it.write(GSON.toJson(HomeWidgetSkinLedger(skins = records)).toByteArray(Charsets.UTF_8))
    }

    private fun write(file: AtomicFile, block: (java.io.OutputStream) -> Unit) {
        val output = file.startWrite()
        try { block(output); file.finishWrite(output) }
        catch (error: Throwable) { file.failWrite(output); throw error }
    }

    private fun publish(records: List<HomeWidgetSkinRecord>) {
        val previous = mutableInstalled.value.associateBy { it.id }
        mutableInstalled.value = records.mapNotNull { record ->
            val archive = archiveFile(record)
            if (!archive.isFile || archive.length() != record.size || hash(archive) != record.sha256) return@mapNotNull null
            previous[record.id]?.takeIf {
                it.revision == record.sha256 && it.name == record.name && it.fromResource == record.fromResource
            } ?: runCatching {
                val renderer = HomeWidgetSkinPackage.read(archive)
                require(renderer.resourceId == record.id)
                InstalledHomeWidgetSkin(record.id, record.name, renderer, archive, record.sha256, record.fromResource, record.size)
            }.getOrNull()
        }
    }

    private fun prune(records: List<HomeWidgetSkinRecord>) {
        val retained = records.mapTo(hashSetOf()) { archiveFile(it).name }
        root.listFiles()?.filter { it.name !in retained && archiveName.matches(it.name) }?.forEach {
            runCatching { AtomicFile(it).delete() }
        }
    }

    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
