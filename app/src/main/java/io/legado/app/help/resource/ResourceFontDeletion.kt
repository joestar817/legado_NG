package io.legado.app.help.resource

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import io.legado.app.help.font.LocalFontAsset
import java.io.File

/** 与安装日志分开：只恢复用户已确认删除的这批路径，不扩大到后来新增的同内容副本。 */
@Keep
internal data class ResourceFontDeletion(
    @SerializedName("schemaVersion") val schemaVersion: Int = 1,
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("path") val path: String = "",
    @SerializedName("paths") val paths: List<String> = emptyList(),
    @SerializedName("size") val size: Long = 0,
    @SerializedName("payloadHashes") val payloadHashes: List<String> = emptyList(),
    @SerializedName("confirmedUsages") val confirmedUsages: List<String>? = null,
) {
    fun validate() {
        require(schemaVersion == 1 && ResourceFontDeletionPolicy.validHash(id)) { "字体删除记录损坏" }
        require(paths.isNotEmpty() && paths.size <= 2048 && paths.distinct().size == paths.size &&
            path in paths && paths.all { it.isNotBlank() && File(it).isAbsolute }) { "字体删除路径记录损坏" }
        require(size >= 0 && payloadHashes.all(ResourceFontDeletionPolicy::validHash)) { "字体删除校验记录损坏" }
    }

    fun asset() = LocalFontAsset(id = id, name = name, path = path, paths = paths, size = size)
}

@Keep
internal data class ResourceFontCacheCleanup(
    @SerializedName("hashes") val hashes: List<String> = emptyList(),
)

internal object ResourceFontDeletionPolicy {
    fun validHash(value: String) = value.matches(Regex("[a-f0-9]{64}"))

    /** New confirmations identify exactly one file; identical content is not deletion ownership. */
    fun singlePath(asset: LocalFontAsset): String {
        require(validHash(asset.id) && asset.paths.size == 1 && File(asset.path).isAbsolute &&
            File(asset.paths.single()).isAbsolute) { "请一次删除一个字体文件" }
        val selected = File(asset.path).canonicalPath
        require(selected == File(asset.paths.single()).canonicalPath) { "字体删除路径不一致" }
        return selected
    }

    /** Missing files may be a partially completed deletion; changed files or new users cancel its intent. */
    fun recoveryCancellation(
        expectedHash: String,
        fingerprints: Collection<String?>,
        confirmedUsages: List<String>? = null,
        usages: () -> Collection<String>,
    ): String? {
        if (fingerprints.any { it != null && it != expectedHash }) {
            return "字体已变化，已取消未完成的删除，请重新确认"
        }
        // Once the approved file is gone, recovery only finishes switching its references.
        if (confirmedUsages != null && fingerprints.all { it == null }) return null
        if (usages().any { it !in confirmedUsages.orEmpty() }) {
            return "字体正在使用，已取消未完成的删除，请重新确认"
        }
        return null
    }

    fun recordsToRemove(records: List<ResourceInstallation>, paths: Collection<String>): List<ResourceInstallation> {
        val canonical = paths.map { File(it).canonicalPath }.toSet()
        return records.filter { it.entry.category.type == "font" && File(it.target).canonicalPath in canonical }
    }

    fun cleanablePayloads(
        candidates: Collection<String>,
        retained: Collection<String>,
        leased: Collection<String>,
    ): Set<String> = candidates.filter { validHash(it) && it !in retained && it !in leased }.toSet()

    fun payloadsNeededByOtherResources(
        records: Collection<ResourceInstallation>,
        entries: Collection<ResourceEntry>,
    ): Set<String> = (records.map { it.entry } + entries)
        .filter { it.category.type != "font" }.mapTo(mutableSetOf()) { it.item.payload.sha256 }
}
