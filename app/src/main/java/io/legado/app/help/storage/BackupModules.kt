package io.legado.app.help.storage

import io.legado.app.constant.PreferKey
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadHighlightRuleStore
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.DirectLinkUpload
import io.legado.app.model.BookCover
import io.legado.app.utils.defaultSharedPreferences
import splitties.init.appCtx

/** IDs are persisted in the archive; labels may change independently. */
internal enum class BackupModule(val id: String, val title: String, val summary: String) {
    BOOKS("books", "书架与阅读记录", "书籍信息、进度、分组、书签与划线备注"),
    SOURCES("sources", "书源与规则", "书源、订阅规则、替换规则、目录规则与字典"),
    RSS("rss", "订阅", "订阅源与收藏"),
    READER("reader", "阅读设置", "阅读预设、高亮规则及关联图片和字体"),
    APPEARANCE("appearance", "外观与布局", "主题、界面布局、背景与界面字体"),
    COVERS("covers", "封面", "封面规则、默认封面与封面相册"),
    OTHER("other", "其它设置", "通用设置、服务器、搜索历史与视频设置"),
}

internal object BackupModules {
    private const val KEY = "ngBackup.modules.v1"
    fun selected(): Set<String> = appCtx.defaultSharedPreferences.getString(KEY, null)
        ?.split(',')?.filter { id -> BackupModule.entries.any { it.id == id } }?.toSet()
        ?: BackupModule.entries.map { it.id }.toSet()

    fun setSelected(ids: Set<String>) {
        check(appCtx.defaultSharedPreferences.edit().putString(KEY, ids.sorted().joinToString(",")).commit())
    }

    fun fileModule(name: String): BackupModule = when (name) {
        "bookshelf.json", "bookmark.json", "bookGroup.json", "readRecord.json" -> BackupModule.BOOKS
        "bookSource.json", "replaceRule.json", "sourceSub.json", "txtTocRule.json",
        "dictRule.json", "keyboardAssists.json" -> BackupModule.SOURCES
        "rssSources.json", "rssStar.json" -> BackupModule.RSS
        ReadBookConfig.configFileName, ReadBookConfig.shareConfigFileName,
        ReadHighlightRuleStore.fileName -> BackupModule.READER
        ThemeConfig.configFileName -> BackupModule.APPEARANCE
        BookCover.configFileName -> BackupModule.COVERS
        DirectLinkUpload.ruleFileName -> BackupModule.OTHER
        else -> BackupModule.OTHER
    }

    fun preferenceModule(key: String): BackupModule? = when {
        key == KEY || key.startsWith("ai", true) || key.startsWith("tts", true) ||
            key.startsWith("readAloud", true) || key.contains("tts", true) ||
            key.startsWith("defaultNarrator") || key.startsWith("defaultDialogue") ||
            key.startsWith("listening") || key.contains("ReadAloud", true) || key == PreferKey.fontFolder ||
            key == "ngInterfaceFont.directory" -> null
        key in BackupConfig.readPreferenceKeys || key.startsWith("read") ||
            key.startsWith("ngReader") || key.startsWith("textSelection") ||
            key.startsWith("autoRead") || key.startsWith("clickAction") || key.startsWith("toc") ||
            key == PreferKey.systemTypefaces || key == PreferKey.brightnessVwPos ||
            key in readerExtraKeys -> BackupModule.READER
        key in BackupConfig.coverPreferenceKeys || key.startsWith("ngCover") ||
            key.startsWith("cover") || key == PreferKey.defaultCover ||
            key == PreferKey.defaultCoverDark -> BackupModule.COVERS
        key in BackupRestorePolicy.themeConfigPreferenceKeys ||
            key in BackupConfig.bookshelfLayoutPreferenceKeys || key.startsWith("ng") ||
            key.startsWith("bookshelf") || key == PreferKey.themeMode ||
            key == PreferKey.hideNavigationBar || key == PreferKey.hideSystemNavigationBar ||
            key == PreferKey.fontScale || key.startsWith("explore") || key == PreferKey.defaultHomePage ||
            key == PreferKey.dThemeName || key == PreferKey.dNThemeName ||
            key == PreferKey.showHome || key == PreferKey.showDiscovery -> BackupModule.APPEARANCE
        key.startsWith("rss", true) || key == PreferKey.showRss -> BackupModule.RSS
        else -> BackupModule.OTHER
    }

    fun includesPreference(key: String, modules: Set<String>): Boolean =
        preferenceModule(key)?.id in modules && BackupConfig.isPortablePreference(key)

    private val readerExtraKeys = setOf(
        PreferKey.brightness, PreferKey.nightBrightness, PreferKey.expandTextMenu,
        PreferKey.pageTouchSlop, PreferKey.pageTouchClick, PreferKey.prevKeys, PreferKey.nextKeys,
        PreferKey.chineseConverterType, PreferKey.replaceEnableDefault, PreferKey.showMangaUi,
        PreferKey.disableMangaScale, PreferKey.disableMangaPageAnim, PreferKey.enableMangaHorizontalScroll,
        PreferKey.hideMangaTitle, PreferKey.mangaColorFilter, PreferKey.enableMangaEInk,
        PreferKey.mangaEInkThreshold, PreferKey.disableHorizontalPageSnap, PreferKey.enableMangaGray,
    )
}
