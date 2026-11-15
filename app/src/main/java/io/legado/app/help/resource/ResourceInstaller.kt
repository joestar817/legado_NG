package io.legado.app.help.resource

import io.legado.app.help.pet.PetPluginStore
import io.legado.app.help.home.HomeWidgetSkinRecord
import io.legado.app.help.home.HomeWidgetSkinStore
import io.legado.app.constant.EventBus
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import io.legado.app.help.config.NgManagedTheme
import io.legado.app.help.config.NgEffectPackageKind
import io.legado.app.help.config.NgEffectPackageLibrary
import io.legado.app.help.config.NgThemeArchive
import io.legado.app.help.config.NgThemeLibraryStore
import io.legado.app.help.config.NgThemePackageManager
import io.legado.app.help.config.NgThemePublication
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadPresetResourceCandidate
import io.legado.app.help.config.ReadPresetResourceConflict
import io.legado.app.help.config.readPresetConfirmationValid
import io.legado.app.help.config.customizationFingerprint
import io.legado.app.help.font.FontLibraryStore
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.utils.GSON
import io.legado.app.utils.postEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.util.UUID

internal class ResourceConflict(
    message: String,
    val themeConflict: ThemeResourceConflict? = null,
    val readPresetConflict: ReadPresetResourceConflict? = null,
    val widgetSkinConflict: HomeWidgetSkinRecord? = null,
) : IllegalStateException(message)

internal fun requireWidgetSkinReplacement(
    current: HomeWidgetSkinRecord?,
    expected: HomeWidgetSkinRecord?,
    confirmed: HomeWidgetSkinRecord?,
    mode: ResourceInstallMode,
) {
    if (current != expected || confirmed != null && current != confirmed) {
        throw ResourceConflict("已安装皮肤发生变化，请重新确认",
            widgetSkinConflict = current?.takeIf { !it.fromResource })
    }
    if (current?.fromResource == false && (mode != ResourceInstallMode.REPLACE || confirmed != current)) {
        throw ResourceConflict("本地已有同名皮肤，请选择保留或替换。", widgetSkinConflict = current)
    }
}

internal class PreparedResource(
    val target: String,
    val baseline: String,
    val commit: suspend () -> Unit,
    val discard: () -> Unit = {},
    val afterCommit: suspend () -> String? = { null },
)

internal object ResourceInstaller {
    fun fingerprint(record: ResourceInstallation): String? = when (record.entry.category.type) {
        "font", "background", "pet", HOME_WIDGET_SKIN_TYPE -> fileFor(record)?.takeIf { it.isFile }?.let(ResourceFiles::hash)
        "read_preset" -> ReadBookConfig.resourcePresetFingerprint(record.target)
        "tts" -> TtsEngineStore.engine(record.target)?.let { ResourceProtocol.hash(it.script.toByteArray()) }
        "theme", "listening_effect" -> NgEffectPackageLibrary.current(appCtx, packageKind(record.entry)).savedThemes.firstOrNull { it.id == record.target }?.let(::themeHash)
        else -> null
    }

    fun localState(record: ResourceInstallation): ResourceLocalState = when (fingerprint(record)) {
        null -> ResourceLocalState.MISSING
        record.baseline -> ResourceLocalState.PRESENT
        else -> ResourceLocalState.MODIFIED
    }

    fun fileFor(record: ResourceInstallation): File? {
        if (record.entry.category.type == "pet") return PetPluginStore.file(record.target)
        if (record.entry.category.type == HOME_WIDGET_SKIN_TYPE) return HomeWidgetSkinStore.file(record.target)
        if (record.entry.category.type == "font") {
            return requireNotNull(FontLibraryStore.managedFile(record.target)) { "字体资源本地路径越界" }
        }
        val root = when (record.entry.category.type) {
            "background" -> ResourceFiles.backgrounds
            else -> return null
        }.canonicalFile
        val file = File(record.target).canonicalFile
        require(file.parentFile == root) { "资源本地路径越界" }
        return file
    }

    private fun themeHash(theme: NgManagedTheme) = ResourceProtocol.hash(GSON.toJson(theme).toByteArray())

    suspend fun prepare(
        entry: ResourceEntry,
        payload: File,
        old: ResourceInstallation?,
        mode: ResourceInstallMode,
        confirmedThemeConflict: ThemeResourceConflict? = null,
        otherThemeTargets: Set<String> = emptySet(),
        confirmedReadPresetConflict: ReadPresetResourceConflict? = null,
        readPresetReplacement: ReadPresetResourceCandidate? = null,
        confirmedWidgetSkinReplacement: HomeWidgetSkinRecord? = null,
        expectedWidgetSkin: HomeWidgetSkinRecord? = null,
    ): PreparedResource {
        val localWidgetSkin = entry.category.type == HOME_WIDGET_SKIN_TYPE &&
            HomeWidgetSkinStore.current(entry.item.id)?.fromResource == false
        if (!localWidgetSkin && entry.category.type !in setOf("pet", "read_preset") && old != null &&
            mode == ResourceInstallMode.NORMAL && localState(old) == ResourceLocalState.MODIFIED) {
            throw ResourceConflict("本地资源已修改，选择保留本地、替换或安装副本。")
        }
        return when (entry.category.type) {
            "font" -> prepareFont(entry, payload)
            "background" -> prepareFile(entry, payload, old, mode)
            "read_preset" -> prepareReadPreset(entry, payload, old, mode, confirmedReadPresetConflict, readPresetReplacement)
            HOME_WIDGET_SKIN_TYPE -> prepareHomeWidgetSkin(entry, payload, mode, confirmedWidgetSkinReplacement, expectedWidgetSkin)
            "tts" -> prepareTts(entry, payload, old, mode)
            "theme" -> prepareTheme(entry, payload, old, mode, confirmedThemeConflict, otherThemeTargets)
            "pet" -> {
                require(mode != ResourceInstallMode.COPY) { "宠物插件不支持安装副本" }
                val id = requireNotNull(entry.item.payload.pluginId)
                val local = PetPluginStore.record(id)
                if (local != null && !PetResourcePolicy.identical(entry, local) && mode == ResourceInstallMode.NORMAL &&
                    (PetResourcePolicy.localChange(entry, local, old) ||
                        (local.manifest?.versionCode ?: 0) >= entry.item.versionCode)) {
                    throw ResourceConflict("将替换已安装的宠物，本地版本和来源可能不同，请明确确认")
                }
                val origin = io.legado.app.help.pet.PetInstallOrigin(
                    channel = "resource", resourceId = entry.item.id, repository = ResourceProtocol.REPOSITORY,
                    sourceCommit = entry.sourceCommit, catalogCommit = entry.catalogCommit,
                    sha256 = entry.item.payload.sha256,
                )
                val prepared = PetPluginStore.prepare(payload, id, entry.item.versionCode, entry.item.payload.sha256, origin)
                PreparedResource(id, entry.item.payload.sha256, commit = {
                    prepared.commit(allowReplace = PetResourcePolicy.replacementAllowed(entry, local, mode),
                        allowDowngrade = mode == ResourceInstallMode.REPLACE)
                }, discard = prepared::discard)
            }
            else -> error("暂不支持此资源类型")
        }
    }

    private fun prepareHomeWidgetSkin(
        entry: ResourceEntry, payload: File, mode: ResourceInstallMode,
        confirmed: HomeWidgetSkinRecord?, expected: HomeWidgetSkinRecord?,
    ): PreparedResource {
        require(mode != ResourceInstallMode.COPY) { "组件皮肤按资源版本管理" }
        val hash = entry.item.payload.sha256
        require(payload.length() == entry.item.payload.size && ResourceFiles.hash(payload) == hash) {
            "组件皮肤内容已变化，请重试"
        }
        val skin = HomeWidgetSkinStore.prepare(payload, entry.item.name, fromResource = true)
        require(skin.record.id == entry.item.id) { "组件皮肤与目录身份不一致" }
        requireWidgetSkinReplacement(skin.previous, expected, confirmed, mode)
        return PreparedResource(skin.record.id, hash, commit = {
            try {
                HomeWidgetSkinStore.install(skin)
            } catch (error: IllegalStateException) {
                throw ResourceConflict(error.message ?: "皮肤已变化，请重新确认",
                    widgetSkinConflict = HomeWidgetSkinStore.current(entry.item.id)?.takeIf { !it.fromResource })
            }
        })
    }

    private fun prepareFont(entry: ResourceEntry, payload: File): PreparedResource {
        val prepared = FontLibraryStore.prepareDownload(payload, entry.item.name)
        require(prepared.sha256 == entry.item.payload.sha256) { "字体内容已变化，请重试" }
        // 更新创建或复用内容相同的资产，原字体可能仍被预设使用，不能覆写它。
        return PreparedResource(prepared.file.absolutePath, prepared.sha256, commit = {
            val imported = prepared.commit()
            check(imported.path == prepared.file.absolutePath) { "字体入库位置发生变化" }
        })
    }

    private suspend fun prepareReadPreset(
        entry: ResourceEntry,
        payload: File,
        old: ResourceInstallation?,
        mode: ResourceInstallMode,
        confirmed: ReadPresetResourceConflict?,
        replacement: ReadPresetResourceCandidate?,
    ): PreparedResource {
        val sha256 = entry.item.payload.sha256
        require(ResourceFiles.hash(payload) == sha256) { "预设内容已变化，请重试" }
        val imported = ReadBookConfig.importResourceWithReport(payload, sha256)
        val previousTarget = old?.target
        suspend fun currentConflict() = withContext(Dispatchers.Main.immediate) {
            ReadBookConfig.resourcePresetConflict(imported.config.name, previousTarget, sha256)
        }
        fun requireConfirmation(current: ReadPresetResourceConflict?) {
            if (readPresetConfirmationValid(current, confirmed, mode, replacement)) return
            if (current == null) error("预设列表已变化，请重新操作")
            throw ResourceConflict("请选择替换原预设或保留两份。", readPresetConflict = current)
        }
        requireConfirmation(currentConflict())
        // A fresh target distinguishes a completed replacement from the old preset in the recovery journal,
        // including a reinstall of the same package whose payload hash has not changed.
        val target = UUID.randomUUID().toString()
        var affectsCurrentReader = false
        return PreparedResource(target, sha256, commit = {
            withContext(Dispatchers.Main.immediate) {
                // The user may have edited, reordered, or selected a preset while the dialog was open.
                requireConfirmation(ReadBookConfig.resourcePresetConflict(imported.config.name, previousTarget, sha256))
                ReadBookConfig.appendResourcePreset(imported.config, target, sha256,
                    replacement.takeIf { mode == ResourceInstallMode.REPLACE })
                affectsCurrentReader = mode == ResourceInstallMode.REPLACE && replacement?.index == ReadBookConfig.styleSelect
            }
        }, afterCommit = {
            if (affectsCurrentReader) withContext(Dispatchers.Main.immediate) {
                postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5, 13))
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            val warnings = imported.warnings +
                if (imported.readerSettings != null) listOf("已忽略包内全局阅读设置") else emptyList()
            if (warnings.isEmpty()) null else "已加入预设：${warnings.joinToString("；")}"
        })
    }

    private fun prepareFile(entry: ResourceEntry, payload: File, old: ResourceInstallation?, mode: ResourceInstallMode): PreparedResource {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(payload.path, bounds)
        require(bounds.outWidth in 1..24000 && bounds.outHeight in 1..24000 &&
            bounds.outWidth.toLong() * bounds.outHeight <= 100_000_000) { "背景图片无效或像素过大" }
        val options = BitmapFactory.Options().apply { inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 512) }
        val bitmap = BitmapFactory.decodeFile(payload.path, options) ?: error("背景图片无法解码")
        bitmap.recycle()
        val root = ResourceFiles.backgrounds
        root.mkdirs()
        val readable = entry.item.name.replace(Regex("[^\\p{L}\\p{N}._-]"), "_").take(40)
        val suffix = if (mode == ResourceInstallMode.COPY) "-${UUID.randomUUID()}" else ""
        val target = if (old != null && mode != ResourceInstallMode.COPY) fileFor(old)!! else
            File(root, "${readable}-${ResourceProtocol.key(entry.item.id)}$suffix.${entry.item.payload.format}")
        val before = target.takeIf { it.isFile }?.let(ResourceFiles::hash)
        if (old == null && before != null) throw ResourceConflict("本地已有同名文件，请安装副本。")
        return PreparedResource(target.absolutePath, entry.item.payload.sha256, commit = {
            check(target.takeIf { it.isFile }?.let(ResourceFiles::hash) == before) { "本地文件已变化，请重试" }
            val atomic = AtomicFile(target)
            val output = atomic.startWrite()
            try {
                payload.inputStream().use { it.copyTo(output) }
                atomic.finishWrite(output)
            } catch (error: Throwable) {
                atomic.failWrite(output)
                throw error
            }
        })
    }

    private fun prepareTts(entry: ResourceEntry, payload: File, old: ResourceInstallation?, mode: ResourceInstallMode): PreparedResource {
        val script = payload.readText()
        val parsed = TtsEngineStore.scriptEngineFromScript(script) ?: error("TTS 脚本元数据无效")
        require(parsed.id == entry.item.payload.engineId && !parsed.builtIn) { "TTS 引擎身份不一致" }
        val targetId = if (mode == ResourceInstallMode.COPY) "${parsed.id}_${UUID.randomUUID()}"
            else old?.target ?: parsed.id
        val existing = TtsEngineStore.engine(targetId)
        if (old == null && existing != null) throw ResourceConflict("本地已有此 TTS 引擎，请安装副本；不会认领或覆盖手工导入的引擎。")
        require(existing?.builtIn != true) { "不能覆盖内置引擎" }
        val installedScript = if (targetId == parsed.id) script else script.replace(
            Regex("(?m)^(\\s*//\\s*@uuid\\s+).*$"),
        ) { it.groupValues[1] + targetId }
        val replacement = parsed.copy(id = targetId, script = installedScript, enabled = false)
        val baseline = ResourceProtocol.hash(installedScript.toByteArray())
        return PreparedResource(targetId, baseline, commit = {
            synchronized(TtsEngineStore) {
                val latest = TtsEngineStore.engine(targetId)
                check(latest?.script == existing?.script && latest?.builtIn != true) { "本地 TTS 已变化，请重新检查" }
                // 只替换发布内容；所有个人参数、运行表和音色绑定继续归现有 Store。
                val next = if (latest == null) replacement else latest.copy(
                    script = installedScript,
                    contentType = replacement.contentType,
                    capabilities = replacement.capabilities,
                    sampleText = replacement.sampleText,
                )
                TtsEngineStore.saveEngine(next, restartReadAloud = false)
            }
        })
    }

    private fun packageKind(entry: ResourceEntry) = if (entry.category.type == "listening_effect")
        NgEffectPackageKind.LISTENING_EFFECT else NgEffectPackageKind.THEME

    private suspend fun prepareTheme(
        entry: ResourceEntry,
        payload: File,
        old: ResourceInstallation?,
        mode: ResourceInstallMode,
        confirmedThemeConflict: ThemeResourceConflict?,
        otherThemeTargets: Set<String>,
    ): PreparedResource {
        val kind = packageKind(entry)
        val pending = NgThemePackageManager.inspectTheme(appCtx, Uri.fromFile(payload), kind, findPrevious = false).getOrThrow()
        var detached: NgManagedTheme? = null
        try {
            val copy = mode == ResourceInstallMode.COPY
            val publishedId = pending.theme.id.takeIf { it.isNotEmpty() }
            val savedThemes = if (copy) emptyList() else NgEffectPackageLibrary.current(appCtx, kind).savedThemes
            val receiptTheme = savedThemes.firstOrNull { it.id == old?.target }
            val current = receiptTheme ?: savedThemes.firstOrNull { it.id == publishedId }
            val target = if (copy) null else current?.id ?: old?.target ?: publishedId
            check(confirmedThemeConflict == null || current != null) { "本地主题已变化，请重新选择下载。" }
            if (current != null) {
                val canReplace = (current.effect == null) == (pending.theme.effect == null) &&
                    current.id !in otherThemeTargets
                if (receiptTheme == null || !canReplace || confirmedThemeConflict != null) {
                    val conflict = ThemeResourceConflict(current, entry.item.payload.sha256, canReplace)
                    if (mode != ResourceInstallMode.REPLACE || !canReplace || confirmedThemeConflict != conflict) {
                        throw ResourceConflict(
                            if (canReplace) "本地已有相同 ID 的主题，请选择保留、替换或安装副本。"
                            else "相同 ID 已被其他主题占用，可保留本地或安装副本。",
                            conflict,
                        )
                    }
                }
            }
            val imported = pending.commit(appCtx, allowReplace = false, allowDowngrade = false,
                keepCustomizations = false, saveToLibrary = false)
            detached = imported
            val identified = imported.copy(
                id = if (copy) "local.${UUID.randomUUID()}" else target ?: imported.id,
                name = if (copy) NgEffectPackageLibrary.uniqueName(appCtx, kind, imported.name) else current?.name ?: imported.name,
            ).normalized()
            val publication = imported.publication ?: NgThemePublication(
                versionCode = entry.item.versionCode,
                versionName = entry.item.displayVersion,
                author = entry.item.author,
                sha256 = pending.sha256,
            )
            val candidate = identified.copy(publication = publication.copy(
                source = "resource",
                sourceId = entry.item.id,
                installedFingerprint = identified.customizationFingerprint(),
            ))
            var installed = candidate
            return PreparedResource(candidate.id, themeHash(candidate), commit = {
                installed = NgEffectPackageLibrary.saveResource(appCtx, kind, candidate, current)
            }, discard = {
                NgThemeArchive.releaseThemeAssets(appCtx, candidate, kind)
            }, afterCommit = {
                if (current == null) null
                else {
                    val refreshed = kind != NgEffectPackageKind.THEME ||
                        NgThemeLibraryStore.refreshInstalledTheme(appCtx, installed)
                    NgThemeArchive.releaseThemeAssets(appCtx, current, kind)
                    if (refreshed) null else "主题已入库，当前外观刷新失败，已保留背景资源。"
                }
            })
        } catch (error: Throwable) {
            detached?.let { NgThemeArchive.releaseThemeAssets(appCtx, it, kind) }
            throw error
        } finally {
            pending.discard()
        }
    }
}

