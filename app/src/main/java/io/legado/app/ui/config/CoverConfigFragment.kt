package io.legado.app.ui.config

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.NgCoverAlbumStore
import io.legado.app.help.config.NgCoverAlbum
import io.legado.app.help.config.NgThemeLibraryStore
import io.legado.app.model.BookCover
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.SelectImageContract
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.inputStream
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import io.legado.app.utils.readUri
import io.legado.app.utils.removePref
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

class CoverConfigFragment : BaseFragment(R.layout.fragment_cover_config) {

    private val requestCodeCover = 111
    private val requestCodeCoverDark = 112
    private var screenState by mutableStateOf(CoverConfigScreenState())
    private var coverActionDark by mutableStateOf<Boolean?>(null)

    private val selectImage = registerForActivityResult(SelectImageContract()) {
        it.uri?.let { uri ->
            when (it.requestCode) {
                requestCodeCover -> setCoverFromUri(PreferKey.defaultCover, uri)
                requestCodeCoverDark -> setCoverFromUri(PreferKey.defaultCoverDark, uri)
            }
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        activity?.setTitle(R.string.cover_config)
        refreshContent()
        (view as ComposeView).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    CoverConfigScreen(
                        state = screenState,
                        onLoadCoverOnlyWifiChanged = {
                            setBooleanPreference(PreferKey.loadCoverOnlyWifi, it)
                        },
                        onOpenCoverRule = {
                            showDialogFragment(CoverRuleConfigDialog())
                        },
                        onUseDefaultCoverChanged = {
                            setBooleanPreference(PreferKey.useDefaultCover, it)
                            refreshCoverPresentation()
                        },
                        onCoverAlbumSelected = { albumId ->
                            if (NgCoverAlbumStore.select(requireContext(), albumId)) {
                                refreshCoverPresentation()
                                refreshContent()
                            }
                        },
                        onCoverAlbumDelete = ::deleteCoverAlbum,
                        onOpenDayCover = { openCoverEditor(dark = false) },
                        onDayShowNameChanged = {
                            setCoverTextPreference(PreferKey.coverShowName, it)
                        },
                        onDayShowAuthorChanged = {
                            setCoverTextPreference(PreferKey.coverShowAuthor, it)
                        },
                        onOpenNightCover = { openCoverEditor(dark = true) },
                        onNightShowNameChanged = {
                            setCoverTextPreference(PreferKey.coverShowNameN, it)
                        },
                        onNightShowAuthorChanged = {
                            setCoverTextPreference(PreferKey.coverShowAuthorN, it)
                        }
                    )
                    coverActionDark?.let { dark ->
                        ConfigChoiceDialog(
                            title = getString(
                                if (dark) R.string.night else R.string.day,
                            ),
                            options = listOf(
                                ConfigChoiceOption(
                                    label = getString(R.string.delete),
                                    value = COVER_ACTION_DELETE,
                                ),
                                ConfigChoiceOption(
                                    label = getString(R.string.select_image),
                                    value = COVER_ACTION_SELECT,
                                ),
                            ),
                            onDismissRequest = { coverActionDark = null },
                            onSelected = { action ->
                                coverActionDark = null
                                if (action == COVER_ACTION_DELETE) {
                                    removeCover(dark)
                                } else {
                                    selectCoverImage(dark)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.cover_config)
        if (view != null) refreshContent()
    }

    private fun refreshContent() {
        val albumState = NgCoverAlbumStore.current(requireContext())
        val selectedAlbum = albumState.albums.firstOrNull {
            it.id == albumState.selectedAlbumId
        }
        screenState = CoverConfigScreenState(
            loadCoverOnlyWifi = getPrefBoolean(PreferKey.loadCoverOnlyWifi, false),
            useDefaultCover = getPrefBoolean(PreferKey.useDefaultCover, false),
            coverAlbums = albumState.albums,
            selectedCoverAlbumId = albumState.selectedAlbumId,
            coverAlbumSummary = when {
                selectedAlbum != null -> getString(
                    R.string.ng_cover_album_selected_summary,
                    selectedAlbum.name,
                    selectedAlbum.lightImages.size,
                    selectedAlbum.darkImages.size,
                )
                albumState.albums.isEmpty() -> getString(R.string.ng_cover_album_empty)
                else -> getString(R.string.ng_cover_album_not_selected)
            },
            dayCoverSummary = coverSummary(PreferKey.defaultCover),
            dayShowName = getPrefBoolean(PreferKey.coverShowName, true),
            dayShowAuthor = getPrefBoolean(PreferKey.coverShowAuthor, true),
            nightCoverSummary = coverSummary(PreferKey.defaultCoverDark),
            nightShowName = getPrefBoolean(PreferKey.coverShowNameN, true),
            nightShowAuthor = getPrefBoolean(PreferKey.coverShowAuthorN, true)
        )
    }

    private fun coverSummary(key: String): String {
        val path = getPrefString(key).takeUnless { it.isNullOrBlank() }
            ?: return getString(R.string.select_image)
        return path.substringAfterLast('/').substringAfterLast('\\')
    }

    private fun setBooleanPreference(key: String, enabled: Boolean) {
        putPrefBoolean(key, enabled)
        refreshContent()
    }

    private fun setCoverTextPreference(key: String, enabled: Boolean) {
        putPrefBoolean(key, enabled)
        refreshCoverPresentation()
        refreshContent()
    }

    private fun refreshCoverPresentation() {
        BookCover.upDefaultCover()
        postEvent(EventBus.BOOKSHELF_REFRESH, "")
    }

    private fun deleteCoverAlbum(album: NgCoverAlbum) {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    NgThemeLibraryStore.detachCoverAlbum(appContext, album.id)
                    NgCoverAlbumStore.remove(appContext, album.id)
                }
            }.onSuccess { removed ->
                if (removed) {
                    refreshCoverPresentation()
                    refreshContent()
                }
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }

    private fun openCoverEditor(dark: Boolean) {
        val key = if (dark) PreferKey.defaultCoverDark else PreferKey.defaultCover
        if (getPrefString(key).isNullOrEmpty()) {
            selectCoverImage(dark)
            return
        }
        coverActionDark = dark
    }

    private fun removeCover(dark: Boolean) {
        removePref(if (dark) PreferKey.defaultCoverDark else PreferKey.defaultCover)
        refreshCoverPresentation()
        refreshContent()
    }

    private fun selectCoverImage(dark: Boolean) {
        selectImage.launch(if (dark) requestCodeCoverDark else requestCodeCover)
    }

    private fun setCoverFromUri(preferenceKey: String, uri: Uri) {
        readUri(uri) { fileDoc, inputStream ->
            runCatching {
                val suffix = if (fileDoc.name.contains(".9.png", true)) {
                    ".9.png"
                } else {
                    "." + fileDoc.name.substringAfterLast(".")
                }
                val fileName = uri.inputStream(requireContext()).getOrThrow().use {
                    MD5Utils.md5Encode(it) + suffix
                }
                val file = FileUtils.createFileIfNotExist(
                    requireContext().externalFiles,
                    "covers",
                    fileName
                )
                FileOutputStream(file).use {
                    inputStream.copyTo(it)
                }
                putPrefString(preferenceKey, file.absolutePath)
                refreshCoverPresentation()
                refreshContent()
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }

    private companion object {
        const val COVER_ACTION_DELETE = "delete"
        const val COVER_ACTION_SELECT = "select"
    }
}
