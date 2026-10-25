package io.legado.app.ui.book.bgm

import android.os.Bundle
import android.content.DialogInterface
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.viewModels
import io.legado.app.help.bgm.BgmPreviewController
import io.legado.app.model.AudioPlay
import io.legado.app.ui.book.audio.AudioPlayActivity
import io.legado.app.ui.book.read.aloud.ReadAloudComposeBottomSheet

/** Only the selection/preview UI is hosted here; the listening service owns BGM playback. */
internal class BgmListeningDialog : ReadAloudComposeBottomSheet() {
    override val useMusicPane: Boolean = true

    private val model by viewModels<BgmViewModel>()
    private var preview: BgmPreviewController? = null

    override fun listeningBook() =
        if (activity is AudioPlayActivity) AudioPlay.book else super.listeningBook()

    override fun listeningSourceOrigin(): String? =
        if (activity is AudioPlayActivity) AudioPlay.bookSource?.bookSourceUrl
        else super.listeningSourceOrigin()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model.configurePlaybackSelection(true)
        if (model.page.value == BgmPage.HOME) model.openQueue()
        val audition = BgmPreviewController(requireContext()).also { preview = it }
        (view as ComposeView).setContent {
            ListeningSheetTheme {
                CompositionLocalProvider(LocalBgmListeningMode provides true) {
                    BgmContent(model, audition, selectingForPlayback = true,
                        onClose = { model.cancelPlaybackSelectionWork(); dismiss() }, listening = true)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.refreshLocalFiles()
    }

    override fun onCancel(dialog: DialogInterface) {
        model.cancelPlaybackSelectionWork()
        preview?.stop()
        super.onCancel(dialog)
    }

    override fun onStop() {
        preview?.stop()
        super.onStop()
    }

    override fun onDestroyView() {
        preview?.release()
        preview = null
        super.onDestroyView()
    }
}
