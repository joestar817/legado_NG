package io.legado.app.help.config

import android.content.Context
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import io.legado.app.constant.PreferKey
import io.legado.app.ui.design.theme.NgColorGenerationMode
import io.legado.app.ui.design.theme.NgColorMath
import io.legado.app.ui.design.theme.NgColorSpec
import io.legado.app.ui.design.theme.NgColorSystem
import io.legado.app.ui.design.theme.NgContrastLevel
import io.legado.app.ui.design.theme.NgManualColorSet
import io.legado.app.ui.design.theme.NgPaletteStyle
import io.legado.app.ui.design.theme.NgTopBarTextMode
import io.legado.app.utils.GSON
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.statusBarHeight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

internal const val NG_MANAGED_THEME_SCHEMA_VERSION = 1
internal const val NG_BUILT_IN_THEME_ID_PREFIX = "builtin."

@Keep
internal data class NgThemeBackground(
    @SerializedName("path") val path: String? = null,
    @SerializedName("blur") val blur: Int = 0
)

@Keep
internal data class NgThemeSceneProfile(
    @SerializedName("sceneId") val sceneId: String? = null,
    @SerializedName("intensity") val intensity: Int = DEFAULT_INTENSITY,
) {
    fun sceneType(): ListeningCartoonType? =
        ListeningCartoonType.fromStorageOrNull(sceneId)

    fun normalized(): NgThemeSceneProfile = copy(
        sceneId = sceneType()?.storageValue,
        intensity = intensity.coerceIn(MIN_INTENSITY, MAX_INTENSITY),
    )

    companion object {
        const val MIN_INTENSITY = 0
        const val MAX_INTENSITY = 100
        const val DEFAULT_INTENSITY = 100
    }
}

@Keep
internal data class NgThemeBarProfile(
    @SerializedName("useFloatingBottomBar")
    val useFloatingBottomBar: Boolean? = null,
    @SerializedName("floatingBottomBarBottomDistancePx")
    val floatingBottomBarBottomDistancePx: Int? = null,
    @SerializedName("floatingBottomBarTransparency")
    val floatingBottomBarTransparency: Int? = null,
    @SerializedName("bookshelfTopBarStyle")
    val bookshelfTopBarStyle: Int? = null,
    @SerializedName("bookshelfFloatingDockTopDistancePx")
    val bookshelfFloatingDockTopDistancePx: Int? = null,
    @SerializedName("bookshelfFloatingDockTransparency")
    val bookshelfFloatingDockTransparency: Int? = null,
    @SerializedName("bookshelfFloatingDockSearchPosition")
    val bookshelfFloatingDockSearchPosition: Int? = null,
) {
    fun normalized(): NgThemeBarProfile = copy(
        floatingBottomBarBottomDistancePx = floatingBottomBarBottomDistancePx?.let {
            FloatingBottomBarConfig.normalizeBottomDistancePx(it)
        },
        floatingBottomBarTransparency = floatingBottomBarTransparency?.let {
            FloatingBottomBarConfig.normalizeTransparencyPercent(it)
        },
        bookshelfTopBarStyle = bookshelfTopBarStyle?.let {
            BookshelfTopBarStyle.fromValue(it).value
        },
        bookshelfFloatingDockTopDistancePx = bookshelfFloatingDockTopDistancePx?.let {
            BookshelfFloatingDockConfig.normalizeTopDistancePx(it)
        },
        bookshelfFloatingDockTransparency = bookshelfFloatingDockTransparency?.let {
            BookshelfFloatingDockConfig.normalizeTransparencyPercent(it)
        },
        bookshelfFloatingDockSearchPosition = bookshelfFloatingDockSearchPosition?.let {
            BookshelfFloatingDockSearchPosition.fromValue(it).value
        },
    )

    companion object {
        const val EDITOR_DEFAULT_BOTTOM_DISTANCE_PX = 40
        const val EDITOR_DEFAULT_TOP_DISTANCE_PX = 360
    }
}

internal fun NgThemeBarProfile?.withFallback(
    fallback: NgThemeBarProfile
): NgThemeBarProfile {
    val profile = this
    return NgThemeBarProfile(
        useFloatingBottomBar = profile?.useFloatingBottomBar
            ?: fallback.useFloatingBottomBar,
        floatingBottomBarBottomDistancePx = profile?.floatingBottomBarBottomDistancePx
            ?: fallback.floatingBottomBarBottomDistancePx,
        floatingBottomBarTransparency = profile?.floatingBottomBarTransparency
            ?: fallback.floatingBottomBarTransparency,
        bookshelfTopBarStyle = profile?.bookshelfTopBarStyle
            ?: fallback.bookshelfTopBarStyle,
        bookshelfFloatingDockTopDistancePx = profile?.bookshelfFloatingDockTopDistancePx
            ?: fallback.bookshelfFloatingDockTopDistancePx,
        bookshelfFloatingDockTransparency = profile?.bookshelfFloatingDockTransparency
            ?: fallback.bookshelfFloatingDockTransparency,
        bookshelfFloatingDockSearchPosition =
            profile?.bookshelfFloatingDockSearchPosition
                ?: fallback.bookshelfFloatingDockSearchPosition,
    ).normalized()
}

@Keep
internal data class NgThemeNavigationAssets(
    @SerializedName("home") val home: String? = null,
    @SerializedName("bookshelf") val bookshelf: String? = null,
    @SerializedName("explore") val explore: String? = null,
    @SerializedName("rss") val rss: String? = null,
    @SerializedName("my") val my: String? = null,
) {
    fun normalized(): NgThemeNavigationAssets = copy(
        home = home.normalizedPackageRelativePath(),
        bookshelf = bookshelf.normalizedPackageRelativePath(),
        explore = explore.normalizedPackageRelativePath(),
        rss = rss.normalizedPackageRelativePath(),
        my = my.normalizedPackageRelativePath(),
    )
}

@Keep
internal data class NgThemeResourceProfile(
    @SerializedName("navigation")
    val navigation: NgThemeNavigationAssets = NgThemeNavigationAssets(),
    @SerializedName("appFont") val appFont: String? = null,
) {
    fun normalized(): NgThemeResourceProfile = copy(
        navigation = navigation.normalized(),
        appFont = appFont.normalizedPackageRelativePath(),
    )
}

@Keep
internal data class NgThemeCoverProfile(
    @SerializedName("applyAlbumSelection")
    val applyAlbumSelection: Boolean = false,
    @SerializedName("albumId") val albumId: String? = null,
    @SerializedName("loadOnlyWifi") val loadOnlyWifi: Boolean? = null,
    @SerializedName("useDefault") val useDefault: Boolean? = null,
    @SerializedName("showName") val showName: Boolean? = null,
    @SerializedName("showAuthor") val showAuthor: Boolean? = null,
    @SerializedName("showNameDark") val showNameDark: Boolean? = null,
    @SerializedName("showAuthorDark") val showAuthorDark: Boolean? = null,
) {
    fun normalized(): NgThemeCoverProfile = copy(
        albumId = albumId?.trim()?.takeIf(String::isNotEmpty),
    )
}

@Keep
internal data class NgManagedTheme(
    @SerializedName("schemaVersion")
    val schemaVersion: Int = NG_MANAGED_THEME_SCHEMA_VERSION,
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("colors") val colors: NgColorSystem,
    @SerializedName("lightBackground")
    val lightBackground: NgThemeBackground = NgThemeBackground(),
    @SerializedName("darkBackground")
    val darkBackground: NgThemeBackground = NgThemeBackground(),
    @SerializedName("transparentAppBars")
    val transparentAppBars: Boolean = false,
    @SerializedName("barProfile")
    val barProfile: NgThemeBarProfile? = null,
    @SerializedName("packageRootPath") val packageRootPath: String? = null,
    @SerializedName("resourceProfile")
    val resourceProfile: NgThemeResourceProfile? = null,
    @SerializedName("coverProfile")
    val coverProfile: NgThemeCoverProfile? = null,
    @SerializedName("ownedCoverAlbumIds")
    val ownedCoverAlbumIds: List<String>? = null,
    @SerializedName("sceneProfile")
    val sceneProfile: NgThemeSceneProfile? = null,
) {
    fun normalized(): NgManagedTheme = copy(
        schemaVersion = NG_MANAGED_THEME_SCHEMA_VERSION,
        id = id.trim(),
        name = name.trim(),
        colors = colors.normalized(),
        lightBackground = lightBackground.copy(blur = lightBackground.blur.coerceIn(0, 25)),
        darkBackground = darkBackground.copy(blur = darkBackground.blur.coerceIn(0, 25)),
        barProfile = barProfile?.normalized(),
        resourceProfile = resourceProfile?.normalized() ?: NgThemeResourceProfile(),
        coverProfile = coverProfile?.normalized(),
        ownedCoverAlbumIds = ownedCoverAlbumIds.orEmpty()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .takeIf { it.isNotEmpty() },
        sceneProfile = sceneProfile?.normalized()?.takeIf { it.sceneType() != null },
    )

    fun resolvePackageAsset(relativePath: String?): File? {
        val normalizedPath = relativePath.normalizedPackageRelativePath() ?: return null
        val root = packageRootPath?.let(::File)?.canonicalFile ?: return null
        val target = File(root, normalizedPath).canonicalFile
        return target.takeIf {
            it.toPath().startsWith(root.toPath()) && it.isFile
        }
    }
}

internal val NgManagedTheme.isBuiltIn: Boolean
    get() = id.startsWith(NG_BUILT_IN_THEME_ID_PREFIX)

private fun String?.normalizedPackageRelativePath(): String? {
    val normalized = this?.trim()?.replace('\\', '/')
        ?.takeIf { it.isNotEmpty() && !it.startsWith('/') }
        ?: return null
    val segments = normalized.split('/')
    return normalized.takeIf {
        ':' !in it && segments.none { segment ->
            segment.isEmpty() || segment == "." || segment == ".."
        }
    }
}

internal data class NgThemeLibraryState(
    val savedThemes: List<NgManagedTheme> = emptyList(),
    val activeThemeId: String? = null
)

/** 新主题管理只使用 NG v1 记录，不读取或迁移旧 themeConfig.json。 */
internal object NgThemeLibraryStore {

    private const val THEMES_KEY = "ngManagedThemes.v1"
    private const val ACTIVE_THEME_KEY = "ngActiveManagedThemeId.v1"
    private val RETIRED_BUILT_IN_THEME_IDS = setOf(
        "builtin.ng.classic",
        "builtin.ng.warm",
        "builtin.ng.bamboo",
        "builtin.ng.mist",
        "builtin.ng.rain_night",
    )
    private val lock = Any()
    private var initialized = false
    private var installedBuiltInThemes: List<NgManagedTheme> = emptyList()
    private val mutableState = MutableStateFlow(NgThemeLibraryState())

    fun observe(context: Context): StateFlow<NgThemeLibraryState> {
        ensureInitialized(context)
        return mutableState.asStateFlow()
    }

    fun current(context: Context): NgThemeLibraryState {
        ensureInitialized(context)
        return mutableState.value
    }

    fun builtInThemes(context: Context): List<NgManagedTheme> {
        ensureInitialized(context)
        return installedBuiltInThemes
    }

    fun allThemes(context: Context): List<NgManagedTheme> =
        builtInThemes(context) + current(context).savedThemes

    fun activeTheme(context: Context): NgManagedTheme? {
        val state = current(context)
        return allThemes(context).firstOrNull { it.id == state.activeThemeId }
    }

    /** 只在尚未选择过 NG 主题时应用发布默认主题，已有选择保持不变。 */
    fun applyDefaultThemeIfNeeded(context: Context) {
        val selectedThemeId = context.defaultSharedPreferences.getString(
            ACTIVE_THEME_KEY,
            null,
        )
        NgDynamicSceneTheme.fromLegacyThemeId(selectedThemeId)?.let { preset ->
            migrateLegacyDynamicTheme(context, preset)
            return
        }
        if (selectedThemeId != null && selectedThemeId in RETIRED_BUILT_IN_THEME_IDS) {
            val defaultTheme = builtInThemes(context)
                .firstOrNull { it.id == NgBuiltInThemes.defaultTheme.id }
                ?: return
            apply(context, defaultTheme)
            return
        }
        if (context.defaultSharedPreferences.contains(ACTIVE_THEME_KEY)) {
            activeTheme(context)
                ?.takeIf { it.isBuiltIn }
                ?.let { ThemeConfig.repairReinstalledThemeBackgrounds(context, it) }
            return
        }
        val defaultTheme = builtInThemes(context)
            .firstOrNull { it.id == NgBuiltInThemes.defaultTheme.id }
            ?: return
        apply(context, defaultTheme)
    }

    private fun migrateLegacyDynamicTheme(
        context: Context,
        preset: ListeningCartoonType,
    ) {
        val previousMode = NgThemeModeStore.current(context)
        if (previousMode == NgThemePresentationMode.STANDARD) {
            NgDynamicSceneTheme.migrateLegacyColorsIfCustomized(
                context = context,
                preset = preset,
                colors = NgColorConfigStore.current(context),
            )
        }
        val standardFallback = builtInThemes(context)
            .firstOrNull { it.id == NgBuiltInThemes.defaultTheme.id }
            ?: return
        // Scene themes used to own the global bar profile. Preserve the user's current geometry
        // while replacing only the now-invalid regular theme colors and backgrounds.
        if (!apply(context, standardFallback.copy(barProfile = null))) return
        NgDynamicSceneTheme.select(context, preset)
        if (previousMode == NgThemePresentationMode.STANDARD) {
            NgThemeModeStore.activateInternal(
                context,
                NgThemePresentationMode.DYNAMIC_SCENE,
            )
        }
    }

    fun snapshotCurrent(context: Context, name: String): NgManagedTheme {
        val state = current(context)
        val active = allThemes(context).firstOrNull { it.id == state.activeThemeId }
        val existing = state.savedThemes.firstOrNull { it.name.equals(name.trim(), true) }
        return NgManagedTheme(
            id = existing?.id ?: "local.${UUID.randomUUID()}",
            name = name.trim(),
            colors = NgColorConfigStore.current(context),
            lightBackground = NgThemeBackground(
                path = context.getPrefString(PreferKey.bgImage),
                blur = context.getPrefInt(PreferKey.bgImageBlurring, 0)
            ),
            darkBackground = NgThemeBackground(
                path = context.getPrefString(PreferKey.bgImageN),
                blur = context.getPrefInt(PreferKey.bgImageNBlurring, 0)
            ),
            transparentAppBars = context.getPrefBoolean(PreferKey.tNavBar, false),
            barProfile = currentBarProfile(context),
            packageRootPath = active?.packageRootPath,
            resourceProfile = active?.resourceProfile ?: NgThemeResourceProfile(),
            ownedCoverAlbumIds = active?.ownedCoverAlbumIds,
            coverProfile = NgThemeCoverProfile(
                applyAlbumSelection = true,
                albumId = NgCoverAlbumStore.current(context).selectedAlbumId,
                loadOnlyWifi = context.getPrefBoolean(PreferKey.loadCoverOnlyWifi, false),
                useDefault = context.getPrefBoolean(PreferKey.useDefaultCover, false),
                showName = context.getPrefBoolean(PreferKey.coverShowName, true),
                showAuthor = context.getPrefBoolean(PreferKey.coverShowAuthor, true),
                showNameDark = context.getPrefBoolean(PreferKey.coverShowNameN, true),
                showAuthorDark = context.getPrefBoolean(PreferKey.coverShowAuthorN, true),
            ),
        ).normalized()
    }

    fun editableBarProfile(
        context: Context,
        profile: NgThemeBarProfile?
    ): NgThemeBarProfile = profile.withFallback(currentBarProfile(context))

    fun currentThemeName(context: Context): String {
        val state = current(context)
        allThemes(context).firstOrNull { it.id == state.activeThemeId }?.let { return it.name }
        val dayName = context.getPrefString(PreferKey.dThemeName)
        return when (dayName) {
            null, "", "默认", "经典主题" -> NgBuiltInThemes.defaultTheme.name
            else -> dayName
        }
    }

    fun saveCurrent(context: Context, name: String): NgManagedTheme {
        require(name.isNotBlank()) { "主题名称不能为空" }
        val saved = addOrReplace(context, snapshotCurrent(context, name))
        synchronized(lock) {
            persistActive(context, saved.id)
            mutableState.value = mutableState.value.copy(activeThemeId = saved.id)
        }
        return saved
    }

    fun addOrReplace(context: Context, theme: NgManagedTheme): NgManagedTheme = synchronized(lock) {
        ensureInitialized(context)
        val normalized = theme.normalized()
        require(normalized.id.isNotEmpty() && normalized.name.isNotEmpty()) { "主题数据不完整" }
        require(!normalized.isBuiltIn) { "内置主题不能被覆盖" }
        val current = mutableState.value
        val replacedIds = current.savedThemes
            .filter { it.id == normalized.id || it.name.equals(normalized.name, true) }
            .mapTo(hashSetOf()) { it.id }
        val updated = buildList {
            addAll(current.savedThemes.filterNot { it.id in replacedIds })
            add(normalized)
        }.sortedBy { it.name.lowercase() }
        persistThemes(context, updated)
        mutableState.value = current.copy(savedThemes = updated)
        normalized
    }

    fun rename(context: Context, themeId: String, newName: String): Boolean = synchronized(lock) {
        ensureInitialized(context)
        if (themeId.startsWith(NG_BUILT_IN_THEME_ID_PREFIX)) return@synchronized false
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return@synchronized false
        val current = mutableState.value
        if (current.savedThemes.any { it.id != themeId && it.name.equals(trimmed, true) }) {
            return@synchronized false
        }
        val updated = current.savedThemes.map {
            if (it.id == themeId) it.copy(name = trimmed) else it
        }
        if (updated == current.savedThemes) return@synchronized false
        persistThemes(context, updated)
        mutableState.value = current.copy(savedThemes = updated)
        true
    }

    fun remove(context: Context, themeId: String): NgManagedTheme? = synchronized(lock) {
        ensureInitialized(context)
        if (themeId.startsWith(NG_BUILT_IN_THEME_ID_PREFIX)) return@synchronized null
        val current = mutableState.value
        val removed = current.savedThemes.firstOrNull { it.id == themeId }
            ?: return@synchronized null
        val updated = current.savedThemes.filterNot { it.id == themeId }
        val nextActive = current.activeThemeId.takeUnless { it == themeId }
        persistThemes(context, updated)
        persistActive(context, nextActive)
        mutableState.value = NgThemeLibraryState(updated, nextActive)
        val orphanedAlbumIds = orphanedCoverAlbumIds(removed, updated)
        NgCoverAlbumStore.removeImported(context, orphanedAlbumIds)
        removed.packageRootPath
            ?.takeIf { root -> updated.none { it.packageRootPath == root } }
            ?.let { deleteOwnedPackageRoot(context, it) }
        removed
    }

    fun detachCoverAlbum(context: Context, albumId: String): Boolean = synchronized(lock) {
        ensureInitialized(context)
        if (albumId.isBlank()) return@synchronized false
        val current = mutableState.value
        var changed = false
        val updated = current.savedThemes.map { theme ->
            val nextOwnedAlbumIds = theme.ownedCoverAlbumIds.orEmpty()
                .filterNot { it == albumId }
                .takeIf { it.isNotEmpty() }
            val nextCoverProfile = theme.coverProfile?.let { profile ->
                if (profile.albumId == albumId) {
                    profile.copy(applyAlbumSelection = false, albumId = null)
                } else {
                    profile
                }
            }
            if (
                nextOwnedAlbumIds != theme.ownedCoverAlbumIds ||
                nextCoverProfile != theme.coverProfile
            ) {
                changed = true
                theme.copy(
                    coverProfile = nextCoverProfile,
                    ownedCoverAlbumIds = nextOwnedAlbumIds,
                )
            } else {
                theme
            }
        }
        if (!changed) return@synchronized false
        persistThemes(context, updated)
        mutableState.value = current.copy(savedThemes = updated)
        true
    }

    fun apply(context: Context, theme: NgManagedTheme): Boolean {
        val previousActiveId = synchronized(lock) {
            ensureInitialized(context)
            val previous = mutableState.value.activeThemeId
            persistActive(context, theme.id)
            mutableState.value = mutableState.value.copy(activeThemeId = theme.id)
            previous
        }
        if (ThemeConfig.applyManagedTheme(context, theme)) return true
        synchronized(lock) {
            persistActive(context, previousActiveId)
            mutableState.value = mutableState.value.copy(activeThemeId = previousActiveId)
        }
        return false
    }

    private fun currentBarProfile(context: Context): NgThemeBarProfile = NgThemeBarProfile(
        useFloatingBottomBar = AppConfig.useFloatingBottomBar,
        floatingBottomBarBottomDistancePx = FloatingBottomBarConfig.resolveBottomDistancePx(
            storedDistancePx = AppConfig.floatingBottomBarBottomDistancePx,
            density = context.resources.displayMetrics.density,
        ),
        floatingBottomBarTransparency = AppConfig.floatingBottomBarTransparency,
        bookshelfTopBarStyle = AppConfig.bookshelfTopBarStyle.value,
        bookshelfFloatingDockTopDistancePx = BookshelfFloatingDockConfig.resolveTopDistancePx(
            storedDistancePx = AppConfig.bookshelfFloatingDockTopDistancePx,
            screenWidthPx = context.resources.displayMetrics.widthPixels,
            density = context.resources.displayMetrics.density,
            statusBarHeightPx = context.statusBarHeight,
        ),
        bookshelfFloatingDockTransparency = AppConfig.bookshelfFloatingDockTransparency,
        bookshelfFloatingDockSearchPosition =
            AppConfig.bookshelfFloatingDockSearchPosition.value,
    )

    fun uniqueName(context: Context, requestedName: String): String {
        val base = requestedName.trim().ifEmpty { "导入主题" }
        val names = allThemes(context).mapTo(hashSetOf()) { it.name.lowercase() }
        if (base.lowercase() !in names) return base
        var index = 2
        while ("$base $index".lowercase() in names) index++
        return "$base $index"
    }

    private fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            val prefs = context.defaultSharedPreferences
            installedBuiltInThemes = NgThemePackageManager.installBuiltInThemes(
                context = context,
                definitions = NgBuiltInThemes.all,
            )
            val saved = prefs.getString(THEMES_KEY, null)?.let { raw ->
                runCatching {
                    GSON.fromJson(raw, Array<NgManagedTheme>::class.java)
                        .orEmpty()
                        .filter { it.schemaVersion == NG_MANAGED_THEME_SCHEMA_VERSION }
                        .map(NgManagedTheme::normalized)
                        .filter {
                            it.id.isNotEmpty() && it.name.isNotEmpty() && !it.isBuiltIn
                        }
                }.getOrDefault(emptyList())
            }.orEmpty()
            mutableState.value = NgThemeLibraryState(
                savedThemes = saved,
                activeThemeId = prefs.getString(ACTIVE_THEME_KEY, null)
            )
            initialized = true
        }
    }

    private fun persistThemes(context: Context, themes: List<NgManagedTheme>) {
        check(
            context.defaultSharedPreferences.edit()
                .putString(THEMES_KEY, GSON.toJson(themes))
                .commit()
        ) { "无法保存主题列表" }
    }

    private fun persistActive(context: Context, themeId: String?) {
        context.defaultSharedPreferences.edit()
            .putString(ACTIVE_THEME_KEY, themeId)
            .apply()
    }

    private fun deleteOwnedPackageRoot(context: Context, path: String) {
        runCatching {
            val root = File(path).canonicalFile
            val owned = File(context.filesDir, NgThemePackageManager.PACKAGE_DIR).canonicalFile
            if (root.parentFile == owned && root.isDirectory) root.deleteRecursively()
        }
    }
}

private fun NgManagedTheme.coverAlbumReferences(): Set<String> = buildSet {
    addAll(ownedCoverAlbumIds.orEmpty())
    coverProfile?.albumId?.let(::add)
}

internal fun orphanedCoverAlbumIds(
    removed: NgManagedTheme,
    remaining: List<NgManagedTheme>,
): Set<String> {
    val retainedAlbumIds = remaining.flatMapTo(hashSetOf()) { it.coverAlbumReferences() }
    return removed.coverAlbumReferences() - retainedAlbumIds
}

internal object NgBuiltInThemes {
    private const val BACKGROUND_PREFIX = "asset://defaultData/theme/"
    private const val CARTOON_BACKGROUND_PREFIX = "asset://listening_motion/cartoon/"

    private val standardFloatingBarProfile = NgThemeBarProfile(
        useFloatingBottomBar = true,
        floatingBottomBarBottomDistancePx = 40,
        floatingBottomBarTransparency = 40,
        bookshelfTopBarStyle = BookshelfTopBarStyle.GROUP_NAVIGATION.value,
        bookshelfFloatingDockTopDistancePx = 50,
        bookshelfFloatingDockTransparency = 40,
        bookshelfFloatingDockSearchPosition =
            BookshelfFloatingDockSearchPosition.LEFT.value,
    )

    val autumn = theme(
        id = "builtin.ng.autumn_mountains",
        name = "秋山书意",
        lightPrimary = 0xFFF78E66.toInt(),
        lightSecondary = 0xFFFFFFFF.toInt(),
        darkPrimary = 0xFF758DB4.toInt(),
        darkSecondary = 0xFF2F3B4B.toInt(),
        darkPrimaryText = 0xFFF2F5F8.toInt(),
        darkSecondaryText = 0xFFB8C2CC.toInt(),
        darkBackgroundColor = 0xFF192633.toInt(),
        darkLabelContainer = 0xFF263440.toInt(),
        lightBackgroundPath = "${BACKGROUND_PREFIX}reading_ng_autumn_mountains.webp",
        darkBackgroundPath = "${BACKGROUND_PREFIX}reading_ng_autumn_mountains_dark.webp",
        darkTopBarTextMode = NgTopBarTextMode.LIGHT,
        transparentAppBars = true,
    ).copy(
        barProfile = NgThemeBarProfile(
            useFloatingBottomBar = true,
            floatingBottomBarBottomDistancePx = 40,
            floatingBottomBarTransparency = 40,
            bookshelfTopBarStyle = BookshelfTopBarStyle.GROUP_NAVIGATION.value,
            bookshelfFloatingDockTopDistancePx = 360,
            bookshelfFloatingDockTransparency = 40,
            bookshelfFloatingDockSearchPosition =
                BookshelfFloatingDockSearchPosition.LEFT.value,
        ),
    )

    val sakura = dynamicTheme(
        id = "builtin.ng.sakura",
        name = "湖畔樱花",
        sceneType = ListeningCartoonType.SAKURA,
        backgroundPath = "${CARTOON_BACKGROUND_PREFIX}sakura/background.webp",
    )

    val cats = dynamicTheme(
        id = "builtin.ng.cats",
        name = "好奇猫咪",
        sceneType = ListeningCartoonType.CATS,
        backgroundPath = "${CARTOON_BACKGROUND_PREFIX}cats/poster.webp",
    )

    val defaultTheme = autumn

    val all = listOf(autumn)

    private fun dynamicTheme(
        id: String,
        name: String,
        sceneType: ListeningCartoonType,
        backgroundPath: String,
    ): NgManagedTheme {
        val primary = sceneType.scenePrimaryColor()
        val base = theme(
            id = id,
            name = name,
            lightPrimary = primary,
            lightSecondary = 0xFFFFFFFF.toInt(),
            darkPrimary = primary,
            darkSecondary = 0xFF303030.toInt(),
            lightBackgroundPath = backgroundPath,
            darkBackgroundPath = backgroundPath,
            lightTopBarTextMode = NgTopBarTextMode.LIGHT,
            darkTopBarTextMode = NgTopBarTextMode.LIGHT,
            transparentAppBars = true,
        )
        return base.copy(
            sceneProfile = NgThemeSceneProfile(
                sceneId = sceneType.storageValue,
                intensity = NgThemeSceneProfile.DEFAULT_INTENSITY,
            ),
        )
    }

    private fun theme(
        id: String,
        name: String,
        lightPrimary: Int,
        lightSecondary: Int,
        darkPrimary: Int,
        darkSecondary: Int,
        darkPrimaryText: Int? = null,
        darkSecondaryText: Int? = null,
        darkBackgroundColor: Int = 0xFF202124.toInt(),
        darkLabelContainer: Int = 0xFF2A2B2F.toInt(),
        lightBackgroundPath: String? = null,
        darkBackgroundPath: String? = null,
        lightTopBarTextMode: NgTopBarTextMode = NgTopBarTextMode.AUTO,
        darkTopBarTextMode: NgTopBarTextMode = NgTopBarTextMode.AUTO,
        transparentAppBars: Boolean = false
    ): NgManagedTheme {
        val light = manualColors(
            primary = lightPrimary,
            secondary = lightSecondary,
            background = 0xFFF5F5F5.toInt(),
            label = 0xFFEEEEEE.toInt()
        )
        val dark = manualColors(
            primary = darkPrimary,
            secondary = darkSecondary,
            background = darkBackgroundColor,
            label = darkLabelContainer,
            primaryText = darkPrimaryText,
            secondaryText = darkSecondaryText,
        )
        return NgManagedTheme(
            id = id,
            name = name,
            colors = NgColorSystem(
                mode = NgColorGenerationMode.MANUAL,
                lightSeed = lightPrimary,
                darkSeed = darkPrimary,
                paletteStyle = NgPaletteStyle.TONAL_SPOT,
                contrast = NgContrastLevel.DEFAULT,
                colorSpec = NgColorSpec.MATERIAL_3_2021,
                manualLight = light,
                manualDark = dark,
                lightTopBarTextMode = lightTopBarTextMode,
                darkTopBarTextMode = darkTopBarTextMode,
            ),
            lightBackground = NgThemeBackground(lightBackgroundPath),
            darkBackground = NgThemeBackground(darkBackgroundPath),
            transparentAppBars = transparentAppBars,
            barProfile = standardFloatingBarProfile,
        )
    }

    private fun manualColors(
        primary: Int,
        secondary: Int,
        background: Int,
        label: Int,
        primaryText: Int? = null,
        secondaryText: Int? = null,
    ) = NgManualColorSet(
        primary = primary,
        secondary = secondary,
        primaryText = primaryText ?: NgColorMath.contentColorFor(background),
        secondaryText = secondaryText ?: NgColorMath.contentColorFor(label),
        background = background,
        labelContainer = label
    )
}
