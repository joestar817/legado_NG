package io.legado.app.help.storage

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.constant.AppConst.androidId
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.DictRule
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.ReadRecord
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.RssStar
import io.legado.app.data.entities.RuleSub
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.data.entities.Server
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.LauncherIconHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.upType
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadHighlightRuleStore
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.VideoPlay.VIDEO_PREF_NAME
import io.legado.app.model.BookCover
import io.legado.app.model.localBook.LocalBook
import io.legado.app.utils.ACache
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.getSharedPreferences
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.openInputStream
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.FileInputStream

/**
 * 恢复
 */
object Restore {

    private val mutex get() = Backup.mutex
    private const val TAG = "Restore"
    private val failures = arrayListOf<String>()

    suspend fun restore(context: Context, uri: Uri) {
        mutex.withLock {
            val staging = File(context.cacheDir, "backup-restore-${java.util.UUID.randomUUID()}")
            try {
                val input = if (uri.isContentScheme()) {
                    context.contentResolver.openInputStream(uri)
                } else File(requireNotNull(uri.path)).inputStream()
                requireNotNull(input) { "无法读取备份" }.use {
                    BackupResources.extract(it, staging)
                }
                restore(staging.path)
                LocalConfig.lastBackup = System.currentTimeMillis()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                appCtx.toastOnUi("恢复备份失败：${error.localizedMessage}")
                throw error
            } finally {
                staging.deleteRecursively()
            }
        }
    }

    suspend fun restoreLocked(path: String) {
        mutex.withLock { restore(path) }
    }

    private suspend fun restore(path: String) {
        failures.clear()
        val modules = BackupResources.validate(File(path))
        val aes = BackupAES()
        val storedPreferences = appCtx.getSharedPreferences(path, "config")?.all
        val backupPreferences = if (modules != null) {
            requireNotNull(storedPreferences) { "无法读取备份设置" }
            // Load typed primary configs before any copy/merge; their legacy loaders otherwise
            // swallow parse errors and silently fall back to defaults.
            if (BackupModule.READER.id in modules) {
                val configs = GSON.fromJson(File(path, ReadBookConfig.configFileName).readText(), Array<ReadBookConfig.Config>::class.java)
                require(!configs.isNullOrEmpty()) { "阅读预设为空" }
                configs.forEach { requireNotNull(it) { "阅读预设包含空项目" } }
                requireNotNull(GSON.fromJson(File(path, ReadBookConfig.shareConfigFileName).readText(), ReadBookConfig.Config::class.java))
                requireNotNull(GSON.fromJson(File(path, ReadHighlightRuleStore.fileName).readText(), Array<io.legado.app.help.config.ReadHighlightRule>::class.java))
            }
            if (BackupModule.APPEARANCE.id in modules) {
                requireNotNull(GSON.fromJson(File(path, ThemeConfig.configFileName).readText(), Array<ThemeConfig.Config>::class.java))
            }
            BackupResources.restoreResources(File(path), storedPreferences)
        } else storedPreferences
        val backupGroups = fileToListT<BookGroup>(path, "bookGroup.json")
        val isMd3Backup = modules == null && Md3BackupCompatibility.isBackup(
            backupPreferences,
            backupGroups.orEmpty().map(BookGroup::groupId)
        )
        if (isMd3Backup) {
            LogUtils.d(TAG, "检测到 MD3 备份，只恢复基础数据并过滤内置分组")
        }
        val bookGson = if (isMd3Backup) Md3BackupCompatibility.bookGson else GSON
        fileToListT<Book>(path, "bookshelf.json", bookGson)?.let {
            it.forEach { book ->
                book.upType()
            }
            it.filter { book -> book.isLocal }
                .forEach { book ->
                    book.coverUrl = LocalBook.getCoverPath(book)
                }
            val newBooks = arrayListOf<Book>()
            val restoredLocalBooks = arrayListOf<Book>()
            val ignoreLocalBook = BackupConfig.ignoreLocalBook
            it.forEach { book ->
                if (ignoreLocalBook && book.isLocal) {
                    return@forEach
                }
                if (appDb.bookDao.has(book.bookUrl)) {
                    try {
                        appDb.bookDao.update(book)
                    } catch (_: SQLiteConstraintException) {
                        appDb.bookDao.insert(book)
                    }
                } else {
                    newBooks.add(book)
                }
                if (book.isLocal) {
                    restoredLocalBooks.add(book)
                }
            }
            appDb.bookDao.insert(*newBooks.toTypedArray())
            rebuildLocalBookCoversAsync(restoredLocalBooks)
        }
        fileToListT<Bookmark>(path, "bookmark.json")?.let {
            appDb.bookmarkDao.insert(*it.toTypedArray())
        }
        backupGroups?.let {
            val supportedGroups = it.filter { group ->
                if (isMd3Backup) {
                    Md3BackupCompatibility.shouldRestoreGroup(group.groupId)
                } else {
                    group.groupId >= 0 || group.groupId == BookGroup.IdAll ||
                            group.groupId == BookGroup.IdLocal ||
                            group.groupId == BookGroup.IdAudio ||
                            group.groupId == BookGroup.IdVideo
                }
            }
            if (supportedGroups.isNotEmpty()) {
                appDb.bookGroupDao.insert(*supportedGroups.toTypedArray())
            }
        }
        fileToListT<BookSource>(path, "bookSource.json")?.let {
            appDb.bookSourceDao.insert(*it.toTypedArray())
        } ?: run {
            val bookSourceFile = File(path, "bookSource.json")
            if (bookSourceFile.exists()) {
                val json = bookSourceFile.readText()
                ImportOldData.importOldSource(json)
            }
        }
        fileToListT<RssSource>(path, "rssSources.json")?.let {
            appDb.rssSourceDao.insert(*it.toTypedArray())
        }
        fileToListT<RssStar>(path, "rssStar.json")?.let {
            appDb.rssStarDao.insert(*it.toTypedArray())
        }
        fileToListT<ReplaceRule>(path, "replaceRule.json")?.let {
            appDb.replaceRuleDao.insert(*it.toTypedArray())
        }
        fileToListT<SearchKeyword>(path, "searchHistory.json")?.let {
            appDb.searchKeywordDao.insert(*it.toTypedArray())
        }
        fileToListT<RuleSub>(path, "sourceSub.json")?.let {
            appDb.ruleSubDao.insert(*it.toTypedArray())
        }
        fileToListT<TxtTocRule>(path, "txtTocRule.json")?.let {
            appDb.txtTocRuleDao.insert(*it.toTypedArray())
        }
        fileToListT<DictRule>(path, "dictRule.json")?.let {
            appDb.dictRuleDao.insert(*it.toTypedArray())
        }
        fileToListT<KeyboardAssist>(path, "keyboardAssists.json")?.let {
            appDb.keyboardAssistsDao.deleteAll() //先删除所有,保证和备份数据一样
            appDb.keyboardAssistsDao.insert(*it.toTypedArray())
        }
        fileToListT<ReadRecord>(path, "readRecord.json")?.let {
            it.forEach { readRecord ->
                //判断是不是本机记录
                if (readRecord.deviceId != androidId) {
                    appDb.readRecordDao.insert(readRecord)
                } else {
                    val time = appDb.readRecordDao
                        .getReadTime(readRecord.deviceId, readRecord.bookName)
                    if (time == null || time < readRecord.readTime) {
                        appDb.readRecordDao.insert(readRecord)
                    }
                }
            }
        }
        File(path, "servers.json").takeIf {
            it.exists()
        }?.runCatching {
            var json = readText()
            if (!json.isJsonArray()) {
                json = aes.decryptStr(json)
            }
            GSON.fromJsonArray<Server>(json).getOrThrow().let {
                appDb.serverDao.insert(*it.toTypedArray())
            }
        }?.onFailure {
            failures += it.localizedMessage.orEmpty()
            AppLog.put("恢复服务器配置出错\n${it.localizedMessage}", it)
        }
        File(path, DirectLinkUpload.ruleFileName).takeIf {
            it.exists()
        }?.runCatching {
            val json = readText()
            ACache.get(cacheDir = false).put(DirectLinkUpload.ruleFileName, json)
        }?.onFailure {
            failures += it.localizedMessage.orEmpty()
            AppLog.put("恢复直链上传出错\n${it.localizedMessage}", it)
        }
        File(path, ThemeConfig.configFileName).takeIf(File::exists)?.let {
            if (modules != null && !BackupConfig.ignoreThemeConfig) {
                val themes = GSON.fromJson(it.readText(), Array<ThemeConfig.Config>::class.java).toList()
                ThemeConfig.configList.clear()
                ThemeConfig.configList.addAll(themes)
                ThemeConfig.save()
            } else {
                LogUtils.d(TAG, "保留当前 NG 主题配置")
            }
        }
        File(path, BookCover.configFileName).takeIf {
            it.exists() && !BackupConfig.ignoreCoverConfig
        }?.runCatching {
            val json = readText()
            BookCover.saveCoverRule(json)
        }?.onFailure {
            failures += it.localizedMessage.orEmpty()
            AppLog.put("恢复封面规则出错\n${it.localizedMessage}", it)
        }
        var readConfigsRestored = false
        if (!BackupConfig.ignoreReadConfig &&
            BackupRestorePolicy.shouldRestoreReadConfigs(isMd3Backup)
        ) {
            //恢复阅读界面配置
            File(path, ReadBookConfig.configFileName).takeIf {
                it.exists()
            }?.runCatching {
                replaceConfigFile(this, File(ReadBookConfig.configFilePath))
                ReadBookConfig.initConfigs()
                readConfigsRestored = true
            }?.onFailure {
                failures += it.localizedMessage.orEmpty()
            AppLog.put("恢复阅读界面出错\n${it.localizedMessage}", it)
            }
            File(path, ReadBookConfig.shareConfigFileName).takeIf {
                it.exists()
            }?.runCatching {
                replaceConfigFile(this, File(ReadBookConfig.shareConfigFilePath))
                ReadBookConfig.initShareConfig()
            }?.onFailure {
                failures += it.localizedMessage.orEmpty()
            AppLog.put("恢复阅读界面出错\n${it.localizedMessage}", it)
            }
        }
        if (!BackupConfig.ignoreReadConfig &&
            BackupRestorePolicy.shouldRestoreHighlightRules(isMd3Backup)
        ) {
            val highlightRuleFile = File(path, ReadHighlightRuleStore.fileName)
            if (highlightRuleFile.exists()) {
                highlightRuleFile.runCatching {
                    replaceConfigFile(this, File(ReadHighlightRuleStore.filePath))
                    ReadHighlightRuleStore.reloadFromFile()
                }.onFailure {
                    failures += it.localizedMessage.orEmpty()
            AppLog.put("恢复高亮规则出错\n${it.localizedMessage}", it)
                }
            } else if (readConfigsRestored) {
                ReadBookConfig.migrateRestoredEmbeddedHighlightRules()
            }
        }
        //AppWebDav.downBgs()
        backupPreferences?.let { map ->
            val edit = appCtx.defaultSharedPreferences.edit()
            if (modules != null) {
                appCtx.defaultSharedPreferences.all.keys.filter {
                    BackupModules.includesPreference(it, modules) && BackupConfig.keyIsNotIgnore(it)
                }.forEach(edit::remove)
            }
            map.forEach { (key, value) ->
                if (BackupConfig.keyIsNotIgnore(key) &&
                    (modules == null || BackupModules.includesPreference(key, modules)) &&
                    BackupRestorePolicy.shouldRestorePreference(key, isMd3Backup, modules != null)
                ) {
                    val compatibleValue = if (isMd3Backup) {
                        Md3BackupCompatibility.normalizePreference(key, value)
                    } else {
                        value
                    } ?: return@forEach
                    when (key) {
                        PreferKey.webDavPassword -> {
                            kotlin.runCatching {
                                aes.decryptStr(compatibleValue.toString())
                            }.getOrNull()?.let {
                                edit.putString(key, it)
                            } ?: let {
                                if (appCtx.getPrefString(PreferKey.webDavPassword)
                                    .isNullOrBlank()
                                ) {
                                    edit.putString(key, compatibleValue.toString())
                                }
                            }
                        }

                        else -> when (compatibleValue) {
                            is Int -> edit.putInt(key, compatibleValue)
                            is Boolean -> edit.putBoolean(key, compatibleValue)
                            is Long -> edit.putLong(key, compatibleValue)
                            is Float -> edit.putFloat(key, compatibleValue)
                            is String -> edit.putString(key, compatibleValue)
                            is Set<*> -> edit.putStringSet(key, compatibleValue.filterIsInstance<String>().toSet())
                        }
                    }
                }
            }
            check(edit.commit()) { "无法保存恢复后的设置" }
        }
        appCtx.getSharedPreferences(path, "videoConfig")?.all?.let { map ->
            appCtx.getSharedPreferences(VIDEO_PREF_NAME, Context.MODE_PRIVATE).edit().apply {
                map.forEach { (key, value) ->
                    when (value) {
                        is Int -> putInt(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is String -> putString(key, value)
                    }
                }
                apply()
            }
        }
        if ((modules == null || BackupModule.READER.id in modules) && !BackupConfig.ignoreReadConfig) ReadBookConfig.apply {
            comicStyleSelect = appCtx.getPrefInt(PreferKey.comicStyleSelect)
            readStyleSelect = appCtx.getPrefInt(PreferKey.readStyleSelect)
            shareLayout = appCtx.getPrefBoolean(PreferKey.shareLayout)
            reloadGlobalReadFloatingColorPreferences()
            hideStatusBar = appCtx.getPrefBoolean(PreferKey.hideStatusBar)
            readBodyToLh = appCtx.getPrefBoolean(PreferKey.readBodyToLh, true)
            useZhLayout = appCtx.getPrefBoolean(PreferKey.useZhLayout)
            isNightTheme = appCtx.getPrefBoolean(PreferKey.readNightTheme, false)
            autoReadSpeed = appCtx.getPrefInt(
                PreferKey.autoReadSpeed,
                ReadBookConfig.defaultAutoReadSpeed,
            )
            autoReadPageMode = appCtx.getPrefInt(
                PreferKey.autoReadPageMode,
                ReadBookConfig.defaultAutoReadPageMode,
            )
        }
        if (modules != null) {
            if (BackupModule.APPEARANCE.id in modules && !BackupConfig.ignoreThemeConfig)
                io.legado.app.help.config.NgThemeLibraryStore.reloadAfterRestore(appCtx)
            if (BackupModule.COVERS.id in modules && !BackupConfig.ignoreCoverConfig)
                io.legado.app.help.config.NgCoverAlbumStore.reloadAfterRestore(appCtx)
        }
        check(failures.isEmpty()) { "部分项目恢复失败：${failures.distinct().joinToString("；")}" }
        appCtx.toastOnUi(R.string.restore_success)
        withContext(Main) {
            delay(100)
            if (!BuildConfig.DEBUG) {
                LauncherIconHelp.changeIcon(appCtx.getPrefString(PreferKey.launcherIcon))
            }
            ThemeConfig.applyDayNight(appCtx)
        }
    }

    private var coverRebuildJob: Coroutine<Unit>? = null

    private fun rebuildLocalBookCoversAsync(books: List<Book>) {
        if (books.isEmpty()) return
        coverRebuildJob?.cancel()
        coverRebuildJob = Coroutine.async(context = IO) {
            var success = 0
            var failed = 0
            books.forEach { book ->
                ensureActive()
                if (!LocalBook.canExtractCover(book)) {
                    //TXT 等格式无法提取封面，跳过
                    return@forEach
                }
                val coverFile = book.coverUrl?.takeIf { it.isNotBlank() }?.let(::File)
                if (coverFile?.exists() == true) {
                    //已有封面文件则跳过，仅补缺失
                    return@forEach
                }
                if (!book.customCoverUrl.isNullOrBlank()) {
                    //自定义封面优先显示，无需重建自动封面
                    return@forEach
                }
                if (LocalBook.upCover(book)) {
                    success++
                } else {
                    failed++
                    AppLog.put("恢复后重建封面失败：${book.name}")
                }
            }
            if (success > 0 || failed > 0) {
                AppLog.put("恢复后封面重建完成：成功 $success 本，失败 $failed 本")
            }
            if (success > 0) {
                postEvent(EventBus.BOOKSHELF_REFRESH, "")
            }
            if (failed > 0) {
                withContext(Main) {
                    appCtx.toastOnUi(appCtx.getString(R.string.restore_cover_rebuild_done, success, failed))
                }
            }
        }
    }

    private fun replaceConfigFile(source: File, target: File) {
        val temporary = File.createTempFile("restore-", ".tmp", target.parentFile)
        try {
            source.copyTo(temporary, overwrite = true)
            java.nio.file.Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }

    private inline fun <reified T> fileToListT(
        path: String,
        fileName: String,
        gson: Gson = GSON
    ): List<T>? {
        try {
            val file = File(path, fileName)
            if (file.exists()) {
                LogUtils.d(TAG, "阅读恢复备份 $fileName 文件大小 ${file.length()}")
                FileInputStream(file).use {
                    return gson.fromJsonArray<T>(it).getOrThrow().also { list ->
                        LogUtils.d(TAG, "阅读恢复备份 $fileName 列表大小 ${list.size}")
                    }
                }
            } else {
                LogUtils.d(TAG, "阅读恢复备份 $fileName 文件不存在")
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            failures += "$fileName: ${e.localizedMessage}"
            AppLog.put("$fileName\n读取解析出错\n${e.localizedMessage}", e)
            appCtx.toastOnUi("$fileName\n读取文件出错\n${e.localizedMessage}")
        }
        return null
    }

}
