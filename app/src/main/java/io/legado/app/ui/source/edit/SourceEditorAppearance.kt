package io.legado.app.ui.source.edit

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.legado.app.R
import io.legado.app.help.config.NgThemeDrawerAssets
import io.legado.app.ui.design.components.compose.LocalNgCodePalette
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.defaultSharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

internal data class SourceEditorAppearance(
    val theme: SourceEditorTheme = SourceEditorTheme.WHITE,
    val image: File? = null,
    val showImage: Boolean = true,
) {
    val visibleImage: File? get() = image?.takeIf { showImage }
}

internal object SourceEditorAppearanceStore {
    private const val MODE = "sourceEditorColorMode"
    private const val IMAGE = "sourceEditorBackgroundFile"
    private const val SHOW_IMAGE = "sourceEditorShowBackground"
    private const val DIRECTORY = "source_editor_images"
    private val imageLock = Mutex()

    fun read(context: Context): SourceEditorAppearance {
        val prefs = context.defaultSharedPreferences
        val modeKey = prefs.getString(MODE, null)
        val theme = SourceEditorTheme.entries.firstOrNull { it.key == modeKey } ?: SourceEditorTheme.WHITE
        val name = prefs.getString(IMAGE, null)
        val image = name?.takeIf { it.isNotEmpty() && it != "." && it != ".." && File(it).name == it }
            ?.let { File(File(context.filesDir, DIRECTORY), it) }
        return SourceEditorAppearance(theme, image?.takeIf { it.isFile }, prefs.getBoolean(SHOW_IMAGE, true))
    }

    fun isAppearanceKey(key: String?) = key == null || key == MODE || key == IMAGE || key == SHOW_IMAGE

    fun setTheme(context: Context, theme: SourceEditorTheme) {
        context.defaultSharedPreferences.edit { putString(MODE, theme.key) }
    }

    fun setImageVisible(context: Context, visible: Boolean) {
        context.defaultSharedPreferences.edit { putBoolean(SHOW_IMAGE, visible) }
    }

    suspend fun setImage(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        imageLock.withLock {
            val previous = read(context)
            val input = if (uri.scheme == null) Uri.fromFile(File(requireNotNull(uri.path))) else uri
            val draft = File(NgThemeDrawerAssets.copyDraft(context, input))
            try {
                ensureActive()
                val directory = File(context.filesDir, DIRECTORY)
                check(directory.isDirectory || directory.mkdirs()) { context.getString(R.string.source_editor_image_failed) }
                val next = File(directory, draft.name)
                check(draft.renameTo(next)) { context.getString(R.string.source_editor_image_failed) }
                val prefs = context.defaultSharedPreferences
                if (!prefs.edit().putString(IMAGE, next.name).putBoolean(SHOW_IMAGE, true).commit()) {
                    prefs.edit {
                        putString(IMAGE, previous.image?.name)
                        putBoolean(SHOW_IMAGE, previous.showImage)
                    }
                    next.delete()
                    error(context.getString(R.string.source_editor_image_failed))
                }
                previous.image?.delete()
            } finally {
                NgThemeDrawerAssets.discardDraft(context, draft.path)
            }
            Unit
        }
    }
}

internal val LocalSourceEditorAppearance = staticCompositionLocalOf { SourceEditorAppearance() }

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun SourceEditorHost(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    var appearance by remember(context) { mutableStateOf(SourceEditorAppearanceStore.read(context)) }
    DisposableEffect(context) {
        val prefs = context.defaultSharedPreferences
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (SourceEditorAppearanceStore.isAppearanceKey(key)) appearance = SourceEditorAppearanceStore.read(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        appearance = SourceEditorAppearanceStore.read(context)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    NgAppTheme(snapshot = appearance.theme.snapshot) {
        CompositionLocalProvider(
            LocalSourceEditorAppearance provides appearance,
            LocalNgCodePalette provides appearance.theme.syntax,
        ) {
            Box(Modifier.fillMaxSize().background(Color(NgTheme.colors.background))) {
                appearance.visibleImage?.let { file ->
                    GlideImage(
                        model = file, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                content()
            }
        }
    }
}
