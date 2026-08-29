package io.legado.app.ui.book.source.manage

import android.os.Bundle
import android.content.Intent
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.legado.app.base.BaseActivity
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.model.CheckSource
import io.legado.app.model.CheckSourceTaskStatus
import io.legado.app.model.CheckSourceTaskStore
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

/** 最近一次书源校验任务的运行进度与结果。 */
class BookSourceCheckActivity : BaseActivity<ComposeActivityBinding>() {

    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val bindNgToolbarMenu: Boolean = false

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        binding.composeView.setContent {
            NgAppTheme {
                val state by CheckSourceTaskStore.state.collectAsState()
                LaunchedEffect(state.status) {
                    keepScreenOn(state.status == CheckSourceTaskStatus.RUNNING)
                }
                DisposableEffect(Unit) {
                    onDispose { keepScreenOn(false) }
                }
                BookSourceCheckScreen(
                    state = state,
                    onBack = ::finish,
                    onCancel = { CheckSource.stop(this) },
                    onHandleResults = ::openGroupedSourceManagement,
                )
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                CheckSourceTaskStore.state.collect { state ->
                    if (
                        state.status == CheckSourceTaskStatus.COMPLETED ||
                        state.status == CheckSourceTaskStatus.CANCELLED
                    ) {
                        CheckSourceTaskStore.markResultsAcknowledged()
                    }
                }
            }
        }
    }

    private fun openGroupedSourceManagement() {
        startActivity(Intent(this, BookSourceActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(BookSourceActivity.EXTRA_OPEN_GROUP_VIEW, true)
        })
        finish()
    }

    private fun keepScreenOn(on: Boolean) {
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
