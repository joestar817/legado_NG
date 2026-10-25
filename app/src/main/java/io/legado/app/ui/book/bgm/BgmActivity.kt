package io.legado.app.ui.book.bgm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.legado.app.base.BaseActivity
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.help.bgm.BgmPreviewController
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.viewbindingdelegate.viewBinding

/** Management and the player's explicit music selection share catalogue rendering, not actions. */
class BgmActivity : BaseActivity<ComposeActivityBinding>() {
    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val bindNgToolbarMenu = false
    private val model by viewModels<BgmViewModel>()
    private lateinit var preview: BgmPreviewController
    private val selectingForPlayback get() = intent.getBooleanExtra(EXTRA_PLAYBACK_SELECTION, false)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        preview = BgmPreviewController(this)
        model.configurePlaybackSelection(selectingForPlayback)
        binding.composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.composeView.setContent { NgAppTheme { BgmContent(model, preview, selectingForPlayback, ::finish) } }
    }

    override fun onStop() {
        if (::preview.isInitialized) preview.stop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        model.refreshLocalFiles()
    }

    override fun onDestroy() {
        if (::preview.isInitialized) preview.release()
        super.onDestroy()
    }


    companion object {
        private const val EXTRA_PLAYBACK_SELECTION = "bgm.selectForPlayback"

        fun open(context: Context) {
            context.startActivity(Intent(context, BgmActivity::class.java))
        }

        fun selectForPlayback(context: Context) {
            context.startActivity(Intent(context, BgmActivity::class.java)
                .putExtra(EXTRA_PLAYBACK_SELECTION, true))
        }

    }
}
