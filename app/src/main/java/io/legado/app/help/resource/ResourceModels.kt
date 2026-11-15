package io.legado.app.help.resource

import io.legado.app.help.pet.PetIdentity
import io.legado.app.help.home.HomeWidgetSkinPackage
import io.legado.app.help.home.HomeWidgetSkinRecord
import io.legado.app.help.home.HomeWidgetSkinRenderer
import io.legado.app.help.home.InstalledHomeWidgetSkin
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Keep
internal data class ResourceCategory(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("type") val type: String = "",
    @SerializedName("index") val index: String = "",
    @SerializedName("count") val count: Int = 0,
    @SerializedName("indexSha256") val indexSha256: String? = null,
)

@Keep
internal data class ResourceCatalog(
    @SerializedName("schemaVersion") val schemaVersion: Int = 0,
    @SerializedName("sourceCommit") val sourceCommit: String = "",
    @SerializedName("categories") val categories: List<ResourceCategory> = emptyList(),
)

@Keep
internal data class ResourceIndex(
    @SerializedName("schemaVersion") val schemaVersion: Int = 0,
    @SerializedName("sourceCommit") val sourceCommit: String = "",
    @SerializedName("type") val type: String = "",
    @SerializedName("resources") val resources: List<ResourceItem> = emptyList(),
)

@Keep
internal data class ResourcePayload(
    @SerializedName("path") val path: String = "",
    @SerializedName("format") val format: String = "",
    @SerializedName("size") val size: Long = 0,
    @SerializedName("sha256") val sha256: String = "",
    @SerializedName("engineId") val engineId: String? = null,
    @SerializedName("pluginId") val pluginId: String? = null,
)

@Keep
internal data class ResourceRequirements(
    @SerializedName("minAppVersionCode") val minAppVersionCode: Long = 0,
    @SerializedName("capabilities") val capabilities: List<String> = emptyList(),
)

@Keep
internal data class ResourcePreviewImage(
    @SerializedName("path") val path: String = "",
    @SerializedName("size") val size: Long = 0,
    @SerializedName("sha256") val sha256: String = "",
    @SerializedName("width") val width: Int = 0,
    @SerializedName("height") val height: Int = 0,
)

@Keep
internal data class ResourceFontPreview(
    @SerializedName("thumbnail") val thumbnail: ResourcePreviewImage? = null,
    @SerializedName("detail") val detail: ResourcePreviewImage? = null,
)

@Keep
internal data class ResourceItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("author") val author: String = "",
    @SerializedName("license") val license: String? = null,
    @SerializedName("versionCode") val versionCode: Long = 0,
    @SerializedName("versionName") val versionName: String? = null,
    @SerializedName("publishedAt") val publishedAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null,
    @SerializedName("description") val description: String = "",
    @SerializedName("changelog") val changelog: String? = null,
    @SerializedName("tags") val tags: List<String> = emptyList(),
    @SerializedName("requirements") val requirements: ResourceRequirements? = null,
    @SerializedName("payload") val payload: ResourcePayload = ResourcePayload(),
    @SerializedName("preview") val preview: ResourceFontPreview? = null,
) {
    val displayVersion: String get() = versionName?.takeIf { it.isNotBlank() } ?: versionCode.toString()
}

@Keep
internal data class ResourceEntry(
    @SerializedName("category") val category: ResourceCategory = ResourceCategory(),
    @SerializedName("sourceCommit") val sourceCommit: String = "",
    @SerializedName("item") val item: ResourceItem = ResourceItem(),
    @SerializedName("catalogCommit") val catalogCommit: String = "",
)

@Keep
internal data class ResourceSnapshot(
    @SerializedName("schemaVersion") val schemaVersion: Int = 1,
    @SerializedName("catalogCommit") val catalogCommit: String = "",
    @SerializedName("catalog") val catalog: ResourceCatalog = ResourceCatalog(),
    @SerializedName("entries") val entries: List<ResourceEntry> = emptyList(),
    @SerializedName("checkedAt") val checkedAt: Long = 0,
)

@Keep
internal data class ResourceInstallation(
    @SerializedName("repository") val repository: String = ResourceProtocol.REPOSITORY,
    @SerializedName("entry") val entry: ResourceEntry = ResourceEntry(),
    @SerializedName("target") val target: String = "",
    @SerializedName("baseline") val baseline: String = "",
    @SerializedName("installedAt") val installedAt: Long = 0,
)

@Keep
internal data class ResourceInstallLedger(
    @SerializedName("schemaVersion") val schemaVersion: Int = 1,
    @SerializedName("installations") val installations: List<ResourceInstallation> = emptyList(),
)

internal const val HOME_WIDGET_SKIN_TYPE = "widget_skin"
internal const val HOME_WIDGET_SKIN_CAPABILITY = "home.widget.skin.1"
internal const val HOME_WIDGET_BLUE_FISH_CAPABILITY = "home.widget.skin.blue_fish.1"
internal const val THEME_CENTER_CROP_CAPABILITY = "theme.background.center_crop.1"

internal enum class ResourceLocalState { PRESENT, MODIFIED, MISSING }
internal enum class ResourceInstallMode { NORMAL, REPLACE, COPY }
internal data class ThemeResourceConflict(
    val previous: io.legado.app.help.config.NgManagedTheme,
    val payloadSha256: String,
    val canReplace: Boolean,
)
internal data class ResourceTask(
    val progress: Float? = null,
    val message: String = "准备下载",
    val running: Boolean = true,
    val conflict: Boolean = false,
    val themeConflict: ThemeResourceConflict? = null,
    val readPresetConflict: io.legado.app.help.config.ReadPresetResourceConflict? = null,
    val widgetSkinConflict: HomeWidgetSkinRecord? = null,
)
internal data class ResourceCenterState(
    val snapshot: ResourceSnapshot? = null,
    val installations: List<ResourceInstallation> = emptyList(),
    val localStates: Map<String, ResourceLocalState> = emptyMap(),
    val petInstallations: Map<String, io.legado.app.help.pet.PetLocalRecord> = emptyMap(),
    val widgetSkins: Map<String, InstalledHomeWidgetSkin> = emptyMap(),
    val tasks: Map<String, ResourceTask> = emptyMap(),
    val loading: Boolean = false,
    val updatingAll: Boolean = false,
    val error: String? = null,
    val storageError: String? = null,
    val localChecked: Boolean = false,
    val loadedCategories: Set<String> = emptySet(),
    val pendingCategories: Set<String> = emptySet(),
    val categoryErrors: Map<String, String> = emptyMap(),
    val accessDenied: Boolean = false,
    val cachedSnapshot: ResourceSnapshot? = null,
) {
    val visibleCatalog: ResourceCatalog? get() = snapshot?.catalog ?: cachedSnapshot?.catalog

    fun visibleCatalogEntries(): List<ResourceEntry> {
        val current = snapshot ?: return cachedSnapshot?.entries.orEmpty()
        val cached = cachedSnapshot ?: return current.entries
        val currentIds = current.entries.mapTo(hashSetOf()) { it.item.id.lowercase(Locale.ROOT) }
        val currentGroups = current.entries.groupBy { it.category.id }
        val cachedGroups = cached.entries.groupBy { it.category.id }
        return current.catalog.categories.flatMap { category ->
            if (category.id in loadedCategories) currentGroups[category.id].orEmpty()
            else cachedGroups[category.id].orEmpty().filter {
                it.category.type == category.type && it.item.id.lowercase(Locale.ROOT) !in currentIds
            }
        }
    }

    fun confirmedEntry(entry: ResourceEntry): ResourceEntry? {
        if (accessDenied || entry.category.id !in loadedCategories) return null
        return snapshot?.entries?.firstOrNull {
            it.category.id == entry.category.id && it.category.type == entry.category.type &&
                it.item.id == entry.item.id && it.item.versionCode == entry.item.versionCode &&
                it.item.payload == entry.item.payload
        }
    }

    /** 暂停分发的类型保留安装记录，当前目录仅显示可用类型。 */
    fun downloadedInstallations() = installations.filter {
        ResourceProtocol.isBrowsableType(it.entry.category.type) &&
            localStates[it.entry.item.id] != ResourceLocalState.MISSING &&
            (it.entry.category.type != HOME_WIDGET_SKIN_TYPE || widgetSkins[it.entry.item.id]?.fromResource == true)
    }

    fun installed(id: String) = installations.firstOrNull {
        it.entry.item.id == id && localStates[id] != ResourceLocalState.MISSING
    }

    fun pet(entry: ResourceEntry) = entry.item.payload.pluginId?.let(petInstallations::get)
    fun hasInstalled(entry: ResourceEntry) = when (entry.category.type) {
        "pet" -> PetResourcePolicy.present(pet(entry))
        HOME_WIDGET_SKIN_TYPE -> widgetSkins.containsKey(entry.item.id)
        else -> installed(entry.item.id) != null
    }
    fun isLocalPetChange(entry: ResourceEntry) = entry.category.type == "pet" &&
        PetResourcePolicy.localChange(entry, pet(entry), installed(entry.item.id))
    fun isLocalWidgetSkin(entry: ResourceEntry) =
        entry.category.type == HOME_WIDGET_SKIN_TYPE && widgetSkins[entry.item.id]?.fromResource == false
    fun versionLabel(entry: ResourceEntry): String {
        if (entry.category.type == "pet") return PetResourcePolicy.label(entry, pet(entry), installed(entry.item.id))
        if (entry.category.type == HOME_WIDGET_SKIN_TYPE && widgetSkins[entry.item.id]?.fromResource == false) return "本地已导入"
        return ResourceProtocol.versionState(entry, installed(entry.item.id))
    }

    fun updateCategories(): Set<String> {
        val types = updateTypes()
        return snapshot?.catalog?.categories.orEmpty().filter { it.type in types }.map { it.id }.toSet()
    }

    fun updateTypes(): Set<String> =
        downloadedInstallations().mapTo(mutableSetOf()) { it.entry.category.type }
            .apply { if (petInstallations.isNotEmpty()) add("pet") }

    fun updatesReady(): Boolean = localChecked && storageError == null &&
        (updateTypes().isEmpty() ||
            (snapshot != null && !accessDenied && updateCategories().all { it in loadedCategories }))

    fun sourceChecked(entry: ResourceEntry): Boolean = snapshot != null && !accessDenied &&
        snapshot.catalog.categories.filter { it.type == entry.category.type }.all { it.id in loadedCategories }

    /** 页签角标和批量更新共用完整集合，不受页面搜索或分类影响。 */
    fun updatableEntries(): List<ResourceEntry> {
        val installed = downloadedInstallations().associateBy { it.entry.item.id }
        return snapshot?.entries.orEmpty().filter {
            if (it.category.type == "pet") PetResourcePolicy.autoUpdate(it, pet(it), installed[it.item.id])
            else ResourceProtocol.isUpdate(it, installed[it.item.id])
        }
    }

    fun withLocalInstallations(
        records: List<ResourceInstallation>,
        states: Map<String, ResourceLocalState>,
        checkedTasks: Map<String, ResourceTask> = tasks,
    ) = copy(
        installations = records,
        localStates = states,
        storageError = null,
        localChecked = true,
        // 刚确认资源消失时，旧冲突/失败状态也失效；保留进行中的任务和之后的新下载结果。
        tasks = tasks.filter { (id, task) ->
            task.running || states[id] != ResourceLocalState.MISSING || localStates[id] == ResourceLocalState.MISSING ||
                checkedTasks[id] !== task
        },
    )
}

/** 不依赖 Android 的目录边界；展示字段变化不参与安装版本比较。 */
internal object ResourceProtocol {
    const val REPOSITORY = "joestar817/legado-resources"
    const val MAX_PAYLOAD_BYTES = 64L * 1024 * 1024
    const val MAX_PREVIEW_BYTES = 1024L * 1024
    const val MAX_INDEX_BYTES = 4 * 1024 * 1024
    private val sha = Regex("[a-f0-9]{40}")
    private val digest = Regex("[a-f0-9]{64}")
    private val identifier = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,159}")
    private val formats = mapOf(
        "font" to setOf("ttf", "otf"),
        "background" to setOf("png", "jpg", "jpeg", "webp"),
        "read_preset" to setOf("zip"),
        HOME_WIDGET_SKIN_TYPE to setOf("zip"),
        "tts" to setOf("js"),
        "theme" to setOf("ngtheme", "zip"),
        "pet" to setOf("zip"),
    )

    fun isBrowsableType(type: String) = type !in setOf("font", "background", "listening_effect")

    fun validCommit(value: String) = sha.matches(value) && value.any { it != '0' }

    /** Git smart-HTTP v0 的只读分支广告；不依赖有公共配额限制的 REST API。 */
    fun catalogCommit(advertisement: ByteArray): String {
        var offset = 0
        var service = false
        val matches = mutableListOf<String>()
        while (offset < advertisement.size) {
            require(offset + 4 <= advertisement.size) { "Git 分支信息被截断" }
            val length = advertisement.copyOfRange(offset, offset + 4).toString(Charsets.US_ASCII)
                .toIntOrNull(16) ?: error("Git 分支信息格式错误")
            offset += 4
            if (length == 0) continue
            require(length in 4..65520 && offset + length - 4 <= advertisement.size) { "Git 分支信息长度错误" }
            val line = advertisement.copyOfRange(offset, offset + length - 4).toString(Charsets.UTF_8)
                .substringBefore('\u0000').trimEnd('\n', '\r')
            offset += length - 4
            if (line == "# service=git-upload-pack") service = true
            val fields = line.split(' ', limit = 2)
            if (fields.size == 2 && fields[1] == "refs/heads/catalog" && validCommit(fields[0])) {
                matches.add(fields[0])
            }
        }
        require(service && matches.size == 1) { "未找到资源目录分支" }
        return matches.single()
    }

    fun safePath(path: String): Boolean = path.isNotBlank() &&
        path.none { it == '\\' || it == ':' || it == '%' || it == '?' || it == '#' || it.code < 32 } &&
        path.split('/').all { it.isNotBlank() && it != "." && it != ".." }

    fun validateCatalog(catalog: ResourceCatalog) {
        require(catalog.schemaVersion == 1 && validCommit(catalog.sourceCommit)) { "不支持的资源目录" }
        require(catalog.categories.size <= 32) { "资源分类过多" }
        require(catalog.categories.map { it.id.lowercase(Locale.ROOT) }.distinct().size == catalog.categories.size) { "资源分类重复" }
        catalog.categories.forEach {
            require(identifier.matches(it.id) && it.name.isNotBlank() && it.type.isNotBlank()) { "资源分类不完整" }
            require(it.index == "resources/${it.id}/index.json" && it.count in 0..10000) { "资源索引路径或数量不合法" }
            require(it.indexSha256 == null || digest.matches(it.indexSha256)) { "分类索引摘要不合法" }
        }
    }

    fun validateIndex(catalog: ResourceCatalog, category: ResourceCategory, index: ResourceIndex) {
        require(index.schemaVersion == 1 && index.sourceCommit == catalog.sourceCommit && index.type == category.type) { "资源索引快照不一致" }
        require(index.resources.size == category.count) { "资源索引数量不一致" }
        index.resources.forEach { item ->
            require(identifier.matches(item.id) && item.name.isNotBlank() && item.author.isNotBlank()) { "资源信息不完整" }
            require(item.versionCode > 0 && item.payload.size > 0 && digest.matches(item.payload.sha256)) { "资源版本或校验信息不合法" }
            require(safePath(item.payload.path) && item.payload.path.startsWith("resources/${category.id}/")) { "资源下载路径不合法" }
            require(item.payload.format.isNotBlank() && item.payload.path.endsWith(".${item.payload.format}", true)) { "资源格式不一致" }
            if (category.type == "tts") require(!item.payload.engineId.isNullOrBlank()) { "TTS 缺少引擎身份" }
            if (category.type == "pet") require(item.payload.pluginId?.matches(Regex("[a-z][a-z0-9_]{0,63}")) == true) { "宠物缺少插件身份" }
            validatePreview(item.preview, category.type)
        }
    }

    fun validatePreview(preview: ResourceFontPreview?, type: String) {
        if (preview == null) return
        require(type in setOf("font", "pet", "read_preset")) { "此资源类型不支持目录预览" }
        validatePreviewImage(requireNotNull(preview.thumbnail) { "预览缺少缩略图" }, 256, 256)
        val detail = requireNotNull(preview.detail) { "预览缺少详情图" }
        if (type == "pet" || type == "read_preset") validatePreviewImage(detail, 768, 768)
        else validatePreviewImage(detail, 960, 420)
    }

    private fun validatePreviewImage(image: ResourcePreviewImage, width: Int, height: Int) {
        require(digest.matches(image.sha256) && image.path == "previews/${image.sha256}.png") { "预览图片路径或摘要不合法" }
        require(image.size in 1..MAX_PREVIEW_BYTES && image.width == width && image.height == height) { "预览图片大小或尺寸不合法" }
    }

    fun validatePreviewSource(entry: ResourceEntry) {
        if (entry.item.preview == null) return
        validatePreview(entry.item.preview, entry.category.type)
        require(validCommit(entry.catalogCommit)) { "预览图片缺少目录来源" }
    }

    /** 先校验内容和 PNG 头，再交给 Android 解码器；不根据不可信头部分配大图。 */
    fun validatePreviewPng(bytes: ByteArray, image: ResourcePreviewImage) {
        require(image.size in 1..MAX_PREVIEW_BYTES && bytes.size.toLong() == image.size && hash(bytes) == image.sha256) {
            "预览图片大小或 SHA-256 不一致"
        }
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        require(bytes.size >= 33 && bytes.copyOfRange(0, 8).contentEquals(signature)) { "预览图片不是 PNG" }
        val header = java.nio.ByteBuffer.wrap(bytes)
        require(header.getInt(8) == 13 && header.getInt(12) == 0x49484452) { "预览图片头不合法" }
        require(header.getInt(16) == image.width && header.getInt(20) == image.height &&
            image.width in 1..2048 && image.height in 1..2048 && image.width.toLong() * image.height <= 2_000_000) {
            "预览图片实际尺寸不符"
        }
    }

    fun validateSnapshot(snapshot: ResourceSnapshot) =
        validateLoadedSnapshot(snapshot, snapshot.catalog.categories.map { it.id }.toSet())

    /** 分组可渐进展示，但每个已加载索引仍须完整校验，且不得混入未加载类别。 */
    fun validateLoadedSnapshot(snapshot: ResourceSnapshot, loaded: Set<String>) {
        require(snapshot.schemaVersion == 1 && validCommit(snapshot.catalogCommit)) { "目录缓存格式不合法" }
        validateCatalog(snapshot.catalog)
        require(loaded.all { id -> snapshot.catalog.categories.any { it.id == id } }) { "目录含有未知分类" }
        val categories = snapshot.catalog.categories.filter { it.id in loaded }
        categories.forEach { category ->
            val entries = snapshot.entries.filter { it.category.id == category.id }
            require(entries.all { it.category == category && it.sourceCommit == snapshot.catalog.sourceCommit }) { "目录快照不一致" }
            validateIndex(snapshot.catalog, category, ResourceIndex(1, snapshot.catalog.sourceCommit, category.type, entries.map { it.item }))
            entries.forEach {
                validatePreviewSource(it)
                require(it.item.preview == null || it.catalogCommit == snapshot.catalogCommit) { "预览图片目录快照不一致" }
            }
        }
        require(snapshot.entries.all { it.category.id in loaded } &&
            snapshot.entries.size == categories.sumOf { it.count }) { "目录含有未知分类" }
        require(snapshot.entries.map { it.item.id.lowercase(Locale.ROOT) }.distinct().size == snapshot.entries.size) { "资源身份重复" }
        val engineIds = snapshot.entries.filter { it.category.type == "tts" }.map { it.item.payload.engineId }
        require(engineIds.distinct().size == engineIds.size) { "TTS 引擎身份重复" }
    }

    fun incompatibility(entry: ResourceEntry, appVersion: Long): String? {
        if (entry.category.type == "pet" && entry.item.payload.format == "ngpet") return "旧版宠物插件，请更新插件"
        if (entry.item.payload.format !in formats[entry.category.type].orEmpty()) return "暂不支持此资源类型"
        if (entry.item.payload.size > MAX_PAYLOAD_BYTES) return "资源超过当前 64 MB 下载上限"
        if (entry.category.type == HOME_WIDGET_SKIN_TYPE && entry.item.payload.size > HomeWidgetSkinPackage.MAX_PACKAGE_BYTES)
            return "组件皮肤超过 8 MB 下载上限"
        val requirements = entry.item.requirements
        if (requirements != null && requirements.minAppVersionCode > appVersion) return "需要更新版本的阅读 NG"
        val capabilities = requirements?.capabilities.orEmpty()
        if (entry.category.type == "pet") {
            if (capabilities.toSet() != setOf(PetIdentity.CAPABILITY)) return "宠物插件接口不兼容"
        } else if (entry.category.type == HOME_WIDGET_SKIN_TYPE) {
            val expected = if (entry.item.id == HomeWidgetSkinRenderer.BLUE_FISH.resourceId) {
                setOf(HOME_WIDGET_SKIN_CAPABILITY, HOME_WIDGET_BLUE_FISH_CAPABILITY)
            } else setOf(HOME_WIDGET_SKIN_CAPABILITY)
            if (capabilities.toSet() != expected) return "组件皮肤接口不兼容"
        } else if (entry.category.type == "theme") {
            if (capabilities.any { it != "theme.effect.1" && it != THEME_CENTER_CROP_CAPABILITY })
                return "主题接口不兼容"
        } else if (capabilities.isNotEmpty()) return "资源要求当前未声明支持的能力"
        return null
    }

    fun versionState(remote: ResourceEntry, local: ResourceInstallation?): String {
        if (local == null) return "未下载"
        require(local.entry.category.type == remote.category.type &&
            local.entry.item.payload.engineId == remote.item.payload.engineId &&
            local.entry.item.payload.pluginId == remote.item.payload.pluginId) { "资源身份发生变化" }
        return when {
            remote.item.versionCode > local.entry.item.versionCode -> "可更新"
            remote.item.versionCode < local.entry.item.versionCode -> "本地版本较新"
            remote.item.payload.sha256 != local.entry.item.payload.sha256 -> "来源版本异常"
            else -> "已下载"
        }
    }

    fun isUpdate(remote: ResourceEntry, local: ResourceInstallation?): Boolean =
        local != null && local.repository == REPOSITORY && local.entry.item.id == remote.item.id &&
            runCatching { versionState(remote, local) == "可更新" }.getOrDefault(false)

    fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    fun key(id: String): String = hash(id.toByteArray()).take(20)
}

internal object ResourceDateTime {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

    fun format(value: String?, zone: ZoneId = ZoneId.systemDefault()): String {
        if (value.isNullOrBlank()) return "—"
        return runCatching { formatter.format(Instant.parse(value).atZone(zone)) }.getOrDefault("—")
    }
}

