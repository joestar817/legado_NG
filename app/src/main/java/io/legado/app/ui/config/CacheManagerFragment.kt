package io.legado.app.ui.config

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.ui.design.theme.NgAppTheme

class CacheManagerFragment : BaseFragment(R.layout.fragment_cache_manager) {

    private val viewModel by viewModels<CacheManagerViewModel>()
    private var activeDialog by mutableStateOf<CacheManagerDialog?>(null)
    private var pendingBookUrl by mutableStateOf("")
    private var pendingBookName by mutableStateOf("")

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        activity?.setTitle(R.string.cache_manager)
        viewModel.refresh()
        (view as ComposeView).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            setContent {
                NgAppTheme {
                    CacheManagerScreen(
                        state = viewModel.state,
                        onClearAllClick = { activeDialog = CacheManagerDialog.CLEAR_ALL },
                        onClearInvalidClick = { activeDialog = CacheManagerDialog.CLEAR_INVALID },
                        onClearBookClick = { entry ->
                            pendingBookUrl = entry.bookUrl
                            pendingBookName = entry.name
                            activeDialog = CacheManagerDialog.CLEAR_BOOK
                        },
                        onPaceManualEnabledChanged = viewModel::setPaceManualEnabled,
                        onPaceMinIntervalChanged = viewModel::setPaceMinIntervalDraft,
                        onPaceMaxIntervalChanged = viewModel::setPaceMaxIntervalDraft,
                        onPaceIntervalChangeFinished = viewModel::savePaceIntervals,
                    )
                    when (activeDialog) {
                        CacheManagerDialog.CLEAR_ALL -> ConfigConfirmationDialog(
                            title = getString(R.string.cache_manager_clear_all),
                            message = getString(R.string.sure_del),
                            cancelText = getString(R.string.no),
                            confirmText = getString(R.string.ok),
                            danger = true,
                            onDismissRequest = { activeDialog = null },
                            onConfirm = {
                                activeDialog = null
                                viewModel.clearAll()
                            },
                        )
                        CacheManagerDialog.CLEAR_INVALID -> ConfigConfirmationDialog(
                            title = getString(R.string.cache_manager_clear_invalid),
                            message = getString(R.string.sure_del),
                            cancelText = getString(R.string.no),
                            confirmText = getString(R.string.ok),
                            danger = true,
                            onDismissRequest = { activeDialog = null },
                            onConfirm = {
                                activeDialog = null
                                viewModel.clearInvalid()
                            },
                        )
                        CacheManagerDialog.CLEAR_BOOK -> ConfigConfirmationDialog(
                            title = pendingBookName,
                            message = getString(R.string.sure_del),
                            cancelText = getString(R.string.no),
                            confirmText = getString(R.string.ok),
                            danger = true,
                            onDismissRequest = { activeDialog = null },
                            onConfirm = {
                                activeDialog = null
                                viewModel.clearBook(pendingBookUrl)
                            },
                        )
                        null -> Unit
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.cache_manager)
    }

    private enum class CacheManagerDialog {
        CLEAR_ALL,
        CLEAR_INVALID,
        CLEAR_BOOK,
    }
}
