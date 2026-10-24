package io.legado.app.ui.book.bgm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import io.legado.app.base.BaseActivity
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.help.bgm.*
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.SelectDirectoryContract
import io.legado.app.utils.viewbindingdelegate.viewBinding

class BgmLocalImportActivity : BaseActivity<ComposeActivityBinding>() {
    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val bindNgToolbarMenu = false
    private val model by viewModels<BgmLocalImportViewModel>()
    private lateinit var preview: BgmPreviewController

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        preview = BgmPreviewController(this)
        model.initialize(intent.getStringExtra(ROOT), intent.getStringExtra(RELOCATE), intent.getBooleanExtra(SCAN, false))
        binding.composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.composeView.setContent { NgAppTheme {
            val state by model.state.collectAsState()
            val previewState by preview.state.collectAsState()
            var previewTrack by remember { mutableStateOf<MusicTrack?>(null) }
            var pickerStarted by rememberSaveable { mutableStateOf(false) }
            var confirmFolder by remember { mutableStateOf(false) }
            val folder = rememberLauncherForActivityResult(SelectDirectoryContract()) { result ->
                result.uri?.let { model.chooseRoot(it, intent.getBooleanExtra(SCAN, false)) }
            }
            val copies = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                if (uris.isEmpty()) finish() else model.importCopies(uris)
            }
            val pickFolder = {
                preview.stop()
                folder.launch(SelectDirectoryContract.Request(initialUri = state.root?.uri))
            }
            val back = {
                preview.stop()
                if (state.result != null) model.dismissResult()
                else if (!model.goUp()) { model.stopScan(); finish() }
            }
            BackHandler(onBack = back)
            LaunchedEffect(Unit) {
                if (!pickerStarted) {
                    pickerStarted = true
                    if (intent.getBooleanExtra(COPY, false)) copies.launch(arrayOf("audio/*"))
                    else if (state.root == null && !state.scanning && state.error.isBlank()) pickFolder()
                }
            }
            LaunchedEffect(previewState) {
                if (previewState.status == BgmPlaybackStatus.ERROR) Toast.makeText(this@BgmLocalImportActivity,
                    previewState.message, Toast.LENGTH_SHORT).show()
            }
            LaunchedEffect(state.path, state.recursive) { preview.stop() }
            Box(Modifier.fillMaxSize()) {
                BgmLocalImportScreen(state, previewState, model, back,
                    onChooseDirectory = {
                        if (state.selectedKeys.isNotEmpty()) confirmFolder = true else pickFolder()
                    },
                    onPreview = { entry -> model.preview(entry)?.let { track ->
                        if (previewState.trackKey == track.key && previewState.status == BgmPlaybackStatus.LOADING) preview.stop()
                        else if (previewState.trackKey == track.key && previewState.status in listOf(BgmPlaybackStatus.PLAYING, BgmPlaybackStatus.PAUSED)) preview.togglePause()
                        else { previewTrack = track; preview.play(track) }
                    } },
                    onAdd = { preview.stop(); model.addSelected() },
                    onExit = { preview.stop(); setResult(RESULT_OK); finish() })
                previewTrack?.takeIf { it.key == previewState.trackKey && previewState.status in listOf(
                    BgmPlaybackStatus.LOADING, BgmPlaybackStatus.PLAYING, BgmPlaybackStatus.PAUSED) }?.let { track ->
                    BgmPreviewBar(track, previewState, preview,
                        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
            if (confirmFolder) BgmConfirmDialog("更换目录", "更换目录将清空当前选择。", "更换",
                onConfirm = { confirmFolder = false; pickFolder() }, onDismiss = { confirmFolder = false })
        } }
    }

    override fun onStop() { if (::preview.isInitialized) preview.stop(); super.onStop() }
    override fun onDestroy() { if (::preview.isInitialized) preview.release(); super.onDestroy() }

    companion object {
        private const val ROOT = "bgm.import.root"
        private const val RELOCATE = "bgm.import.relocate"
        private const val SCAN = "bgm.import.scan"
        private const val COPY = "bgm.import.copy"
        internal fun open(context: Context, scan: Boolean = false, copy: Boolean = false, track: MusicTrack? = null) {
            context.startActivity(Intent(context, BgmLocalImportActivity::class.java)
                .putExtra(SCAN, scan).putExtra(COPY, copy).putExtra(ROOT, track?.localReference?.rootUri)
                .putExtra(RELOCATE, track?.key))
        }
    }
}
