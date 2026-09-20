package io.legado.app.ui.main.bookshelf

import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.doOnAttach
import androidx.core.view.indices
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.about.NetworkLogDialog
import io.legado.app.ui.book.group.GroupManageDialog
import io.legado.app.ui.book.import.local.ImportBookActivity
import io.legado.app.ui.book.import.remote.RemoteBookActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.config.AiChatActivity
import io.legado.app.utils.CreateFileContract
import io.legado.app.utils.SelectFileContract
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.main.MainViewModel
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.postEvent
import io.legado.app.utils.readText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.takePersistableReadPermission
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.dpToPx

abstract class BaseBookshelfFragment(layoutId: Int) : VMBaseFragment<BookshelfViewModel>(layoutId),
    MainFragmentInterface,
    BookshelfLayoutDialog.Callback,
    BookshelfActionDialog.Callback,
    BookshelfAddProgressDialog.Callback {

    override val position: Int? get() = arguments?.getInt("position")

    val activityViewModel by activityViewModels<MainViewModel>()
    override val viewModel by viewModels<BookshelfViewModel>()

    private val importBookshelf =
        registerForActivityResult(SelectFileContract()) { uri ->
            kotlin.runCatching {
                uri?.let {
                    it.takePersistableReadPermission()
                    it.readText(requireContext())
                }?.let { text ->
                    viewModel.importBookshelf(text, groupId)
                }
            }.onFailure {
                toastOnUi(it.localizedMessage ?: "ERROR")
            }
        }
    private val exportResult = registerForActivityResult(CreateFileContract()) {
        it.save(viewLifecycleOwner, requireContext()) { uri ->
            showDialogFragment(BookshelfActionDialog.exportSuccess(uri.toString()))
        }
    }
    abstract val groupId: Long
    abstract val books: List<Book>
    abstract var onlyUpdateRead: Boolean
    private var groupsLiveData: LiveData<List<BookGroup>>? = null
    private var addProgressDialog: BookshelfAddProgressDialog? = null

    abstract fun gotoTop()

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        menuInflater.inflate(R.menu.main_bookshelf, menu)
        val moreActionContainer = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(48.dpToPx(), 48.dpToPx())
            doOnAttach {
                if (childCount == 0) {
                    val composeView = ComposeView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setViewCompositionStrategy(
                            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                        )
                    }
                    addView(composeView)
                    composeView.setContent {
                        NgAppTheme {
                            BookshelfToolbarMenuButton(
                                onMenuItemClick = ::handleBookshelfMenuItem,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
        menu.add(Menu.NONE, R.id.menu_more, Menu.NONE, R.string.menu).apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            actionView = moreActionContainer
        }
    }

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        super.onCompatOptionsItemSelected(item)
        handleBookshelfMenuItem(item.itemId)
    }

    protected fun handleBookshelfMenuItem(itemId: Int) {
        when (itemId) {
            R.id.menu_remote -> startActivity<RemoteBookActivity>()
            R.id.menu_search -> startActivity<SearchActivity>()
            R.id.menu_update_toc -> activityViewModel.upToc(books, onlyUpdateRead)
            R.id.menu_ai_assistant -> startActivity<AiChatActivity> {
                putExtra(AiChatActivity.EXTRA_ENTRY, AiChatActivity.ENTRY_BOOKSHELF)
                putExtra(AiChatActivity.EXTRA_EXPAND_SUGGESTIONS, true)
            }
            R.id.menu_bookshelf_layout -> configBookshelf()
            R.id.menu_bookshelf_settings -> BookshelfLayoutDialog.showSettings(childFragmentManager)
            R.id.menu_group_manage -> showDialogFragment<GroupManageDialog>()
            R.id.menu_add_local -> startActivity<ImportBookActivity>()
            R.id.menu_add_url -> showAddBookByUrlAlert()
            R.id.menu_export_bookshelf -> viewModel.exportBookshelf(books) { file ->
                exportResult.launch(
                    CreateFileContract.FileData("bookshelf.json", file, "application/json")
                )
            }

            R.id.menu_import_bookshelf -> showImportBookshelfDialog()
            R.id.menu_log -> showDialogFragment<AppLogDialog>()
            R.id.menu_network_log -> showDialogFragment<NetworkLogDialog>()
        }
    }

    protected fun initBookGroupData() {
        groupsLiveData?.removeObservers(viewLifecycleOwner)
        groupsLiveData = appDb.bookGroupDao.show.apply {
            observe(viewLifecycleOwner) {
                upGroup(it)
            }
        }
    }

    abstract fun upGroup(data: List<BookGroup>)

    abstract fun upSort()

    override fun observeLiveBus() {
        viewModel.addBookProgressLiveData.observe(this) { count ->
            if (count < 0) {
                dismissAddProgressDialog()
            } else {
                showAddProgressDialog(count)
            }
        }
    }

    fun showAddBookByUrlAlert() {
        showDialogFragment(BookshelfActionDialog.addUrl())
    }

    fun configBookshelf() {
        BookshelfLayoutDialog.show(childFragmentManager)
    }

    override fun onBookshelfLayoutConfirmed(result: BookshelfLayoutResult) {
        if (result.refresh) {
            postEvent(EventBus.BOOKSHELF_REFRESH, "")
        }
        if (result.waitCountChanged) {
            activityViewModel.postUpBooksLiveData(true)
        }
        if (result.sortChanged) {
            upSort()
        }
        if (result.recreate) {
            postEvent(EventBus.RECREATE, "")
        }
    }


    private fun showImportBookshelfDialog() {
        showDialogFragment(BookshelfActionDialog.importBookshelf())
    }

    override fun onBookshelfAddUrlConfirmed(value: String) {
        showAddProgressDialog()
        viewModel.addBookByUrl(value)
    }

    override fun onBookshelfImportConfirmed(value: String) {
        viewModel.importBookshelf(value, groupId)
    }

    override fun onBookshelfImportFileRequested() {
        importBookshelf.launch(arrayOf("text/*", "application/json"))
    }

    override fun onBookshelfExportPathCopied(value: String) {
        requireContext().sendToClip(value)
    }

    override fun onBookshelfAddProgressCancelled() {
        addProgressDialog = null
        viewModel.addBookJob?.cancel()
    }

    private fun showAddProgressDialog(count: Int? = null) {
        currentAddProgressDialog()?.let {
            it.updateProgress(count)
            return
        }
        if (childFragmentManager.isStateSaved) return
        BookshelfAddProgressDialog().also {
            addProgressDialog = it
            it.updateProgress(count)
            it.show(childFragmentManager, BookshelfAddProgressDialog.TAG)
        }
    }

    private fun dismissAddProgressDialog() {
        currentAddProgressDialog()?.dismissAllowingStateLoss()
        addProgressDialog = null
    }

    private fun currentAddProgressDialog(): BookshelfAddProgressDialog? {
        addProgressDialog?.let { return it }
        return (childFragmentManager.findFragmentByTag(BookshelfAddProgressDialog.TAG)
            as? BookshelfAddProgressDialog)?.also { addProgressDialog = it }
    }

}
