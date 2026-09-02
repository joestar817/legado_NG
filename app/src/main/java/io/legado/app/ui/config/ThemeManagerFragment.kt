package io.legado.app.ui.config

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.EventBus
import io.legado.app.help.config.NgManagedTheme
import io.legado.app.help.config.NgThemeLibraryStore
import io.legado.app.help.config.NgThemePackageManager
import io.legado.app.help.config.isBuiltIn
import io.legado.app.model.BookCover
import io.legado.app.help.config.md3.Md3ThemeImportDraft
import io.legado.app.help.config.md3.Md3ThemeImportManager
import io.legado.app.help.config.md3.Md3ThemePackageNotRecognizedException
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.normalizeFileName
import io.legado.app.utils.postEvent
import io.legado.app.utils.CreateDocumentContract
import io.legado.app.utils.SelectFileContract
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ThemeManagerFragment : BaseFragment(R.layout.fragment_theme_manager) {

    private var pendingExportTheme: NgManagedTheme? = null
    private var originalEditTheme by mutableStateOf<NgManagedTheme?>(null)
    private var draftEditTheme by mutableStateOf<NgManagedTheme?>(null)
    private var pendingDarkBackground: Boolean? = null
    private var pendingMd3ImportUri: Uri? = null
    private var md3ImportDraft by mutableStateOf<Md3ThemeImportDraft?>(null)
    private var md3ImportInstalling by mutableStateOf(false)

    private val exportTheme = registerForActivityResult(
        CreateDocumentContract("application/zip")
    ) { uri ->
        val theme = pendingExportTheme
        pendingExportTheme = null
        if (uri == null || theme == null) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            NgThemePackageManager.exportTheme(requireContext(), theme, uri)
                .onSuccess { toastOnUi(R.string.ng_theme_export_success) }
                .onFailure { toastOnUi(getString(R.string.ng_theme_export_failed, it.message.orEmpty())) }
        }
    }

    private val importTheme = registerForActivityResult(
        SelectFileContract()
    ) { uri ->
        uri ?: return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            Md3ThemeImportManager.preview(requireContext(), uri)
                .onSuccess { draft ->
                    pendingMd3ImportUri = uri
                    md3ImportDraft = draft
                }
                .onFailure { error ->
                    if (error is Md3ThemePackageNotRecognizedException) {
                        importNativeTheme(uri)
                    } else {
                        toastOnUi(getString(R.string.ng_theme_import_failed, error.message.orEmpty()))
                    }
                }
        }
    }

    private val selectBackground = registerForActivityResult(
        SelectFileContract()
    ) { uri ->
        val dark = pendingDarkBackground
        pendingDarkBackground = null
        if (uri == null || dark == null) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { copyBackground(uri) }
                .onSuccess { path -> updateBackground(dark) { it.copy(path = path) } }
                .onFailure {
                    toastOnUi(getString(R.string.ng_theme_background_copy_failed, it.message.orEmpty()))
                }
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        activity?.setTitle(R.string.ng_theme_management)
        setSharedTitleBarVisible(false)
        (view as ComposeView).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by NgThemeLibraryStore.observe(requireContext()).collectAsState()
                NgAppTheme {
                    ThemeManagerScreen(
                        builtInThemes = NgThemeLibraryStore.builtInThemes(requireContext()),
                        savedThemes = state.savedThemes,
                        activeThemeId = state.activeThemeId,
                        currentThemeName = NgThemeLibraryStore.currentThemeName(requireContext()),
                        onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                        onSaveCurrent = ::saveCurrentTheme,
                        onImportPackage = ::importThemePackage,
                        onThemeSelected = { NgThemeLibraryStore.apply(requireContext(), it) },
                        onThemeEdit = ::editTheme,
                        editingTheme = originalEditTheme,
                        draftTheme = draftEditTheme,
                        onDismissThemeEditor = ::dismissThemeEditor,
                        onDraftThemeChanged = { draftEditTheme = it },
                        onSelectBackground = ::selectThemeBackground,
                        onBackgroundBlurChanged = { dark, blur ->
                            updateBackground(dark) { it.copy(blur = blur) }
                        },
                        onClearBackground = { dark ->
                            updateBackground(dark) { it.copy(path = null) }
                        },
                        onSaveTheme = ::saveEditedTheme,
                        onThemeExport = ::requestExport,
                        onThemeDelete = ::deleteTheme,
                        md3ImportDraft = md3ImportDraft,
                        md3ImportInstalling = md3ImportInstalling,
                        onDismissMd3Import = ::dismissMd3Import,
                        onConfirmMd3Import = ::installMd3Theme
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.ng_theme_management)
        setSharedTitleBarVisible(false)
    }

    private fun setSharedTitleBarVisible(visible: Boolean) {
        activity?.findViewById<View>(R.id.title_bar)?.visibility = if (visible) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun saveCurrentTheme(name: String) {
        NgThemeLibraryStore.saveCurrent(requireContext(), name)
    }

    private fun importThemePackage() {
        importTheme.launch(
            arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
        )
    }

    private suspend fun importNativeTheme(uri: Uri) {
        NgThemePackageManager.importTheme(requireContext(), uri)
            .onSuccess { theme ->
                NgThemeLibraryStore.apply(requireContext(), theme)
                toastOnUi(getString(R.string.ng_theme_import_success, theme.name))
            }
            .onFailure { toastOnUi(getString(R.string.ng_theme_import_failed, it.message.orEmpty())) }
    }

    private fun dismissMd3Import() {
        if (md3ImportInstalling) return
        pendingMd3ImportUri = null
        md3ImportDraft = null
    }

    private fun installMd3Theme(applyAfterInstall: Boolean) {
        val uri = pendingMd3ImportUri ?: return
        if (md3ImportInstalling) return
        md3ImportInstalling = true
        viewLifecycleOwner.lifecycleScope.launch {
            Md3ThemeImportManager.install(requireContext(), uri, applyAfterInstall)
                .onSuccess { result ->
                    pendingMd3ImportUri = null
                    md3ImportDraft = null
                    toastOnUi(getString(R.string.ng_theme_import_success, result.theme.name))
                }
                .onFailure { error ->
                    toastOnUi(getString(R.string.ng_theme_import_failed, error.message.orEmpty()))
                }
            md3ImportInstalling = false
        }
    }

    private fun editTheme(theme: NgManagedTheme) {
        val editableBarProfile = NgThemeLibraryStore.editableBarProfile(
            requireContext(),
            theme.barProfile,
        )
        originalEditTheme = theme
        draftEditTheme = theme.copy(barProfile = editableBarProfile)
    }

    private fun dismissThemeEditor() {
        originalEditTheme = null
        draftEditTheme = null
        pendingDarkBackground = null
    }

    private fun saveEditedTheme() {
        val context = requireContext()
        val original = originalEditTheme ?: return
        val draft = draftEditTheme?.normalized() ?: return
        if (draft.name.isBlank()) {
            toastOnUi(R.string.ng_theme_name_required)
            return
        }
        val builtIn = original.isBuiltIn
        val targetName = if (builtIn && draft.name.equals(original.name, true)) {
            NgThemeLibraryStore.uniqueName(
                context,
                getString(R.string.ng_theme_copy_name, draft.name)
            )
        } else {
            draft.name
        }
        val conflict = NgThemeLibraryStore.allThemes(context).any { existing ->
            existing.id != original.id && existing.name.equals(targetName, true)
        }
        if (conflict) {
            toastOnUi(R.string.ng_theme_name_conflict)
            return
        }
        val saved = NgThemeLibraryStore.addOrReplace(
            context,
            draft.copy(
                id = if (builtIn) "local.${UUID.randomUUID()}" else original.id,
                name = targetName
            )
        )
        val wasActive = NgThemeLibraryStore.current(context).activeThemeId == original.id
        dismissThemeEditor()
        if (wasActive && !builtIn) {
            view?.post { NgThemeLibraryStore.apply(context, saved) }
        } else {
            toastOnUi(R.string.ng_theme_saved_success)
        }
    }

    private fun selectThemeBackground(dark: Boolean) {
        pendingDarkBackground = dark
        selectBackground.launch(arrayOf("image/*"))
    }

    private fun updateBackground(
        dark: Boolean,
        update: (io.legado.app.help.config.NgThemeBackground) -> io.legado.app.help.config.NgThemeBackground
    ) {
        val current = draftEditTheme ?: return
        draftEditTheme = if (dark) {
            current.copy(darkBackground = update(current.darkBackground))
        } else {
            current.copy(lightBackground = update(current.lightBackground))
        }
    }

    private suspend fun copyBackground(uri: Uri): String = withContext(Dispatchers.IO) {
        val context = requireContext().applicationContext
        val extension = when (context.contentResolver.getType(uri)?.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/jpeg" -> "jpg"
            else -> "img"
        }
        val root = File(context.filesDir, BACKGROUND_DIR).apply { mkdirs() }
        val target = File(root, "${UUID.randomUUID()}.$extension")
        val input = context.contentResolver.openInputStream(uri) ?: error("无法读取图片")
        input.use { source ->
            target.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_BACKGROUND_BYTES) { "图片超过 32 MB" }
                    output.write(buffer, 0, read)
                }
            }
        }
        target.absolutePath
    }

    private fun requestExport(theme: NgManagedTheme) {
        pendingExportTheme = theme
        exportTheme.launch("${theme.name.normalizeFileName()}.ngtheme")
    }

    private fun deleteTheme(theme: NgManagedTheme) {
        runCatching { NgThemeLibraryStore.remove(requireContext(), theme.id) }
            .onSuccess { removed ->
                if (removed != null) {
                    BookCover.upDefaultCover()
                    postEvent(EventBus.BOOKSHELF_REFRESH, "")
                }
            }
            .onFailure { toastOnUi(it.localizedMessage.orEmpty()) }
    }

    override fun onDestroyView() {
        setSharedTitleBarVisible(true)
        super.onDestroyView()
    }

    private companion object {
        const val BACKGROUND_DIR = "ng_theme_backgrounds"
        const val MAX_BACKGROUND_BYTES = 32L * 1024 * 1024
    }
}
