package io.legado.app.help.pet

import android.net.Uri
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import io.legado.app.utils.GSON
import splitties.init.appCtx
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.zip.ZipFile

/** Optional display data; never changes the executable manifest or identity. */
@Keep
internal data class PetPackageInfo(
    @SerializedName("schemaVersion") val schemaVersion: Int = 1,
    @SerializedName("versionName") val versionName: String? = null,
    @SerializedName("author") val author: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("license") val license: String? = null,
    @SerializedName("preview") val preview: String? = null,
)

@Keep
internal data class PetInstallOrigin(
    @SerializedName("channel") val channel: String = "local",
    @SerializedName("resourceId") val resourceId: String? = null,
    @SerializedName("repository") val repository: String? = null,
    @SerializedName("catalogCommit") val catalogCommit: String? = null,
    @SerializedName("sourceCommit") val sourceCommit: String? = null,
    @SerializedName("sha256") val sha256: String? = null,
) {
    fun validate(packageHash: String) {
        require(channel == "local" || channel == "resource") { "宠物来源无效" }
        if (channel == "resource") {
            require(!resourceId.isNullOrBlank() && !repository.isNullOrBlank() &&
                catalogCommit?.matches(Regex("[a-f0-9]{40}")) == true &&
                sourceCommit?.matches(Regex("[a-f0-9]{40}")) == true && sha256 == packageHash) {
                "宠物来源收据无效"
            }
        } else require(resourceId == null && repository == null && catalogCommit == null && sourceCommit == null && sha256 == null) {
            "本地导入不能声明资源库收据"
        }
    }
}

internal enum class PetLocalStatus { READY, MISSING, INCOMPATIBLE, CORRUPT, LOAD_ERROR }
internal enum class PetImportAction { INSTALL, NO_OP, UPDATE, REPLACE, DOWNGRADE }
internal data class PetFailure(val stage: String, val message: String, val type: String? = null)
internal data class PetLocalRecord(
    val id: String,
    val manifest: PetPluginManifest?,
    val info: PetPackageInfo?,
    val sha256: String?,
    val sizeBytes: Long,
    val origin: PetInstallOrigin?,
    val installedAt: Long?,
    val status: PetLocalStatus,
    val error: PetFailure? = null,
)
internal data class PetImportResult(val id: String, val changed: Boolean)

internal object PetImportPolicy {
    fun action(old: PetLocalRecord?, manifest: PetPluginManifest, hash: String): PetImportAction = when {
        old == null -> PetImportAction.INSTALL
        old.sha256 == hash && old.status != PetLocalStatus.MISSING && old.status != PetLocalStatus.CORRUPT -> PetImportAction.NO_OP
        old.manifest == null -> PetImportAction.REPLACE
        manifest.versionCode > old.manifest.versionCode -> PetImportAction.UPDATE
        manifest.versionCode < old.manifest.versionCode -> PetImportAction.DOWNGRADE
        else -> PetImportAction.REPLACE
    }

    fun requirePermission(action: PetImportAction, allowReplace: Boolean, allowDowngrade: Boolean) {
        require(action != PetImportAction.REPLACE || allowReplace) { "同版本内容已变化，请明确确认替换" }
        require(action != PetImportAction.DOWNGRADE || allowDowngrade) { "导入版本较低，请明确确认降级" }
    }
}

internal object PetPackageDisplay {
    const val MAX_PREVIEW_BYTES = 1024 * 1024
    const val MAX_PACKAGE_BYTES = 64L * 1024 * 1024

    fun copyBounded(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return total
            total += count
            require(total <= limit) { "宠物文件超过大小限制" }
            output.write(buffer, 0, count)
        }
    }

    fun readBounded(input: InputStream, limit: Int): ByteArray = input.use { stream ->
        ByteArrayOutputStream().use { output ->
            copyBounded(stream, output, limit.toLong())
            output.toByteArray()
        }
    }

    fun info(file: File): PetPackageInfo? = ZipFile(file).use { archive ->
        val entry = archive.getEntry("assets/pet-info.json") ?: return null
        require(!entry.isDirectory && entry.size in 1..32768) { "宠物展示信息无效" }
        val raw = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(readBounded(archive.getInputStream(entry), 32768))).toString()
        val json = com.google.gson.JsonParser.parseString(raw)
        require(json.isJsonObject) { "宠物展示信息无效" }
        val fields = json.asJsonObject
        val allowed = setOf("schemaVersion", "versionName", "author", "description", "license", "preview")
        require(fields.keySet().all { it in allowed } && fields["schemaVersion"]?.toString() == "1") { "宠物展示信息版本或字段无效" }
        for (key in allowed - "schemaVersion") {
            fields[key]?.let { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) { "宠物展示信息字段无效" } }
        }
        val result = GSON.fromJson(json, PetPackageInfo::class.java) ?: error("宠物展示信息无效")
        fun text(value: String?, limit: Int, multiline: Boolean = false) {
            if (value == null) return
            require(value.isNotBlank() && value.length <= limit && value.none {
                Character.isISOControl(it) && !(multiline && it in "\n\r\t")
            }) { "宠物展示信息长度或字符无效" }
        }
        text(result.versionName, 64)
        text(result.author, 128)
        text(result.description, 4096, true)
        text(result.license, 256)
        text(result.preview, 240)
        result.preview?.let { path ->
            require(PetPackageInspector.safePath(path) && path.endsWith(".png", ignoreCase = true)) { "宠物预览路径无效" }
            preview(archive, path)
        }
        result
    }

    fun preview(file: File, info: PetPackageInfo?): ByteArray? {
        val path = info?.preview ?: return null
        return ZipFile(file).use { preview(it, path) }
    }

    private fun preview(archive: ZipFile, path: String): ByteArray {
        require(PetPackageInspector.safePath(path) && path.endsWith(".png", ignoreCase = true)) { "宠物预览路径无效" }
        val entry = archive.getEntry("assets/$path") ?: error("宠物预览缺失")
        require(!entry.isDirectory && entry.size in 33..MAX_PREVIEW_BYTES.toLong()) { "宠物预览大小无效" }
        return readBounded(archive.getInputStream(entry), MAX_PREVIEW_BYTES).also(::validatePng)
    }

    fun validatePng(bytes: ByteArray) {
        require(bytes.size in 57..MAX_PREVIEW_BYTES && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))) { "宠物预览必须为PNG" }
        val header = ByteBuffer.wrap(bytes)
        require(header.getInt(8) == 13 && header.getInt(12) == 0x49484452) { "宠物预览PNG头无效" }
        val width = header.getInt(16)
        val height = header.getInt(20)
        require(width in 1..1024 && height in 1..1024 && width.toLong() * height <= 1_048_576L) { "宠物预览像素过大" }
        val depth = bytes[24].toInt() and 255
        val color = bytes[25].toInt() and 255
        val depths = when (color) {
            0 -> setOf(1, 2, 4, 8, 16)
            2, 4, 6 -> setOf(8, 16)
            3 -> setOf(1, 2, 4, 8)
            else -> emptySet()
        }
        require(depth in depths && bytes[26].toInt() == 0 && bytes[27].toInt() == 0 && bytes[28].toInt() in 0..1) { "宠物预览PNG格式无效" }
        var position = 8
        var ended = false
        var hasData = false
        var dataEnded = false
        var hasPalette = false
        while (position <= bytes.size - 12) {
            val size = header.getInt(position)
            require(size >= 0 && size.toLong() + position + 12 <= bytes.size) { "宠物预览PNG损坏" }
            val type = header.getInt(position + 4)
            val typeName = String(bytes, position + 4, 4, Charsets.US_ASCII)
            require(typeName.matches(Regex("[A-Za-z]{4}"))) { "宠物预览PNG块无效" }
            val crc = java.util.zip.CRC32().apply { update(bytes, position + 4, size + 4) }.value
            require(crc == (header.getInt(position + 8 + size).toLong() and 0xffffffffL)) { "宠物预览PNG校验失败" }
            when (type) {
                0x49484452 -> require(position == 8 && size == 13) { "宠物预览PNG头重复" }
                0x504c5445 -> {
                    require(!hasPalette && !hasData && size in 1..768 && size % 3 == 0 && color != 0 && color != 4) { "宠物预览PNG调色板无效" }
                    hasPalette = true
                }
                0x49444154 -> {
                    require(!dataEnded && (color != 3 || hasPalette)) { "宠物预览PNG数据顺序无效" }
                    hasData = true
                }
                0x49454e44 -> Unit
                else -> require(!typeName[0].isUpperCase()) { "宠物预览PNG关键块不支持" }
            }
            if (hasData && type != 0x49444154) dataEnded = true
            position += size + 12
            if (type == 0x49454e44) { ended = size == 0 && position == bytes.size; break }
        }
        require(ended && hasData) { "宠物预览PNG不完整" }
    }
}

internal class PetImportCandidate internal constructor(
    private val prepared: PetPluginStore.PreparedPetPlugin,
    val manifest: PetPluginManifest,
    val info: PetPackageInfo?,
    val sha256: String,
    val sizeBytes: Long,
    val previous: PetLocalRecord?,
    val action: PetImportAction,
) {
    fun readPreview(): ByteArray? = prepared.readPreview()
    fun discard() = prepared.discard()
    internal fun commit(allowReplace: Boolean, allowDowngrade: Boolean): PetImportResult =
        PetImportResult(manifest.id, prepared.commit(allowReplace, allowDowngrade))
}

/** All functions perform file IO and must be called off the main thread. No method here executes DEX. */
internal object PetLocalManager {
    val revision get() = PetPluginStore.revision

    fun records(): List<PetLocalRecord> = PetPluginStore.records()

    fun inspect(uri: Uri, expectedId: String? = null): PetImportCandidate {
        val temporary = File.createTempFile("pet-import-", ".zip", appCtx.cacheDir)
        try {
            requireNotNull(appCtx.contentResolver.openInputStream(uri)) { "无法读取宠物文件" }.use { input ->
                temporary.outputStream().use { PetPackageDisplay.copyBounded(input, it, PetPackageDisplay.MAX_PACKAGE_BYTES) }
            }
            return inspect(temporary, expectedId)
        } finally { temporary.delete() }
    }

    fun inspect(file: File, expectedId: String? = null): PetImportCandidate {
        val manifest = PetPackageInspector.inspect(file)
        require(expectedId == null || manifest.id == expectedId) { "请选择同一宠物的更新包" }
        val sha256 = PetPluginStore.hash(file)
        val prepared = PetPluginStore.prepare(file, manifest.id, manifest.versionCode, sha256, PetInstallOrigin())
        return PetImportCandidate(prepared, manifest, prepared.info, sha256, file.length(), prepared.previous, prepared.action)
    }

    fun commit(candidate: PetImportCandidate, allowReplace: Boolean = false, allowDowngrade: Boolean = false) =
        candidate.commit(allowReplace, allowDowngrade)

    fun readPreview(id: String): ByteArray? = PetPluginStore.readPreview(id)
    fun exportFile(id: String): File = PetPluginStore.exportFile(id)
    fun diagnostics(id: String): String = PetPluginStore.diagnostics(id)
}
