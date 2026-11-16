package io.legado.app.ui.book.changecover

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.constant.BookSourceType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.book.changesource.ChangeSourceFilter
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.ui.widget.dialog.ngDialogMaxHeight
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/** 封面换源与独立的书源筛选设置。 */
class ChangeCoverDialog() : BaseComposeDialogFragment() {

    constructor(name: String, author: String, sourceType: Int = BookSourceType.default) : this() {
        arguments = Bundle().apply {
            putString("name", name)
            putString("author", author)
            putInt("sourceType", sourceType)
        }
    }

    constructor(book: Book, selectedType: Int? = null) : this(
        book.name,
        book.author,
        ChangeSourceFilter.sourceTypeForBook(
            book.type,
            selectedType ?: book.readConfig?.customBookType,
        ),
    )

    private val callBack: CallBack? get() = activity as? CallBack
    private val viewModel: ChangeCoverViewModel by viewModels()
    private var covers by mutableStateOf(emptyList<SearchBook>())
    private var searchState by mutableIntStateOf(0)

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(height = ngDialogMaxHeight(0.92f))
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.initData(arguments)
        (view as ComposeView).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    val groups by viewModel.groups.collectAsStateWithLifecycle()
                    val selectedGroup by viewModel.selectedGroup.collectAsStateWithLifecycle()
                    val sameTypeOnly by viewModel.sameTypeOnly.collectAsStateWithLifecycle()
                    val blockSourceDialogs by viewModel.blockSourceDialogs.collectAsStateWithLifecycle()
                    val progress by viewModel.progress.collectAsStateWithLifecycle()
                    ChangeCoverDialogContent(
                        covers = covers,
                        searchState = searchState,
                        progress = progress,
                        groups = groups,
                        selectedGroup = selectedGroup,
                        sameTypeOnly = sameTypeOnly,
                        blockSourceDialogs = blockSourceDialogs,
                        onRefreshToggle = viewModel::startOrStopSearch,
                        onCoverClick = ::changeTo,
                        onGroupSelected = { group ->
                            if (group != viewModel.selectedGroup.value) {
                                covers = emptyList()
                                viewModel.setGroup(group)
                            }
                        },
                        onToggleSameTypeOnly = {
                            covers = emptyList()
                            viewModel.setSameTypeOnly(!viewModel.sameTypeOnly.value)
                        },
                        onToggleBlockSourceDialogs = {
                            viewModel.setBlockSourceDialogs(!viewModel.blockSourceDialogs.value)
                        },
                    )
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(STARTED) {
                launch {
                    viewModel.dataFlow.conflate().collect {
                        covers = it.toList()
                        delay(1000)
                    }
                }
                launch {
                    viewModel.searchState.collect {
                        searchState = it
                    }
                }
            }
        }
    }

    private fun changeTo(coverUrl: String) {
        callBack?.coverChangeTo(coverUrl)
        dismissAllowingStateLoss()
    }

    interface CallBack {
        fun coverChangeTo(coverUrl: String)
    }
}
