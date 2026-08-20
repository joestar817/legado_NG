package io.legado.app.ui.main.bookshelf.style1

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.FragmentBookshelf1Binding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.BookshelfFloatingDockConfig
import io.legado.app.help.config.BookshelfFloatingDockSearchPosition
import io.legado.app.help.config.BookshelfHomeMode
import io.legado.app.help.config.BookshelfTopBarStyle
import io.legado.app.ui.about.ReadRecordActivity
import io.legado.app.ui.book.group.GroupManageDialog
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.manage.BookshelfManageActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.main.bookshelf.BaseBookshelfFragment
import io.legado.app.ui.main.bookshelf.BookshelfDockGroup
import io.legado.app.ui.main.bookshelf.style1.books.BooksFragment
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.widget.NgActionPopup
import io.legado.app.ui.widget.NgActionPopupItem
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.statusBarHeight
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.collections.set

/**
 * 书架界面
 */
class BookshelfFragment1() : BaseBookshelfFragment(R.layout.fragment_bookshelf1) {

    companion object {
        private const val SORT_MENU_ID_OFFSET = 1000
        private val sortValues = intArrayOf(4, 0, 1, 2, 3, 5)
    }

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    private val binding by viewBinding(FragmentBookshelf1Binding::bind)
    private val bookGroups = mutableListOf<BookGroup>()
    private val fragmentMap = hashMapOf<Long, BooksFragment>()
    private var activeBooksFragment: BooksFragment? = null
    private var pendingGroupSelection = false
    private var groupGridBooks: List<Book> = emptyList()
    private var groupGridCustomGroupMask: Long = 0L
    private var groupGridFolders by mutableStateOf<List<BookshelfGroupFolder>>(emptyList())
    private var groupGridBottomInsetPx by mutableIntStateOf(0)
    private var groupGridScrollToTopToken by mutableLongStateOf(0L)
    private var showGroupGrid by mutableStateOf(
        AppConfig.bookshelfHomeMode == BookshelfHomeMode.GROUP_GRID
    )
    private var dockGroups by mutableStateOf<List<BookshelfDockGroup>>(emptyList())
    private var selectedGroupIndex by mutableIntStateOf(0)
    private var dockTopDistancePx by mutableIntStateOf(0)
    private var dockContentTopInsetPx by mutableIntStateOf(0)
    private var dockTransparency by mutableIntStateOf(
        BookshelfFloatingDockConfig.DEFAULT_TRANSPARENCY_PERCENT
    )
    private var dockSearchPosition by mutableStateOf(
        BookshelfFloatingDockSearchPosition.LEFT
    )
    private var configuredTopBarStyle by mutableStateOf(
        AppConfig.bookshelfTopBarStyle
    )
    override val groupId: Long get() = selectedGroup?.groupId ?: 0

    override val books: List<Book>
        get() {
            val fragment = fragmentMap[groupId]
            return fragment?.getBooks() ?: emptyList()
        }

    override var onlyUpdateRead = false
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        pendingGroupSelection = true
        initView()
        initBookGroupData()
    }

    private val selectedGroup: BookGroup?
        get() = bookGroups.getOrNull(selectedGroupIndex)

    private fun initView() {
        updateFloatingDockSettings()
        binding.bookshelfScreen.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        binding.bookshelfScreen.setContent {
            NgAppTheme {
                BookshelfScreen(
                    dockGroups = dockGroups,
                    selectedGroupIndex = selectedGroupIndex,
                    groupGridMode = showGroupGrid,
                    configuredTopBarStyle = configuredTopBarStyle,
                    dockTopDistancePx = dockTopDistancePx,
                    dockContentTopInsetPx = dockContentTopInsetPx,
                    dockTransparency = dockTransparency,
                    dockSearchPosition = dockSearchPosition,
                    onSearchClick = {
                        SearchActivity.start(requireContext(), null)
                    },
                    onGroupClick = { index ->
                        selectGroup(index, showReselectionFeedback = true)
                    },
                    onGroupLongClick = { index ->
                        bookGroups.getOrNull(index)?.let { group ->
                            if (group.groupId != BookGroup.IdRoot &&
                                group.groupId != BookGroup.IdNoGroup
                            ) {
                                showDialogFragment(GroupManageDialog.forEdit(group))
                            }
                        }
                    },
                    onManageClick = ::openBookshelfManage,
                    onSortClick = ::showSortMenu,
                    onMenuItemClick = ::onBookshelfMenuItemClick,
                    onFloatingDockBoundsChanged = { bounds ->
                        binding.bookshelfScreen.setTag(R.id.bookshelf_floating_dock, bounds)
                    },
                )
            }
        }
        binding.bookshelfGroupGrid.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        binding.bookshelfGroupGrid.setContent {
            NgAppTheme {
                val bottomInset = with(LocalDensity.current) {
                    groupGridBottomInsetPx.toDp()
                }
                BookshelfGroupGrid(
                    folders = groupGridFolders,
                    bottomInset = bottomInset,
                    scrollToTopToken = groupGridScrollToTopToken,
                    onOpenBook = ::openBookFromGroupGrid,
                    onOpenBookInfo = ::openBookInfoFromGroupGrid,
                )
            }
        }
        initGroupGridData()
    }

    private fun initGroupGridData() {
        (activity as? MainActivity)?.resolveFloatingBottomContentInset { inset ->
            groupGridBottomInsetPx = inset
        }
        viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookDao.flowByGroup(BookGroup.IdAll)
                .catch { AppLog.put("书架分组网格加载书籍失败", it) }
                .collect { books ->
                    groupGridBooks = books
                    updateGroupGridFolders()
                }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookGroupDao.flowAll()
                .catch { AppLog.put("书架分组网格加载完整分组失败", it) }
                .collect { groups ->
                    groupGridCustomGroupMask = groups.customGroupMask()
                    updateGroupGridFolders()
                }
        }
        updateGroupGridVisibility()
    }

    private fun updateGroupGridFolders() {
        val folderGroups = buildList {
            add(
                BookGroup(
                    groupId = BookGroup.IdRoot,
                    groupName = getString(R.string.no_group),
                    order = Int.MIN_VALUE,
                )
            )
            addAll(
                bookGroups.filter {
                    it.groupId != BookGroup.IdRoot &&
                        it.groupId != BookGroup.IdNoGroup
                }
            )
        }
        groupGridFolders = buildBookshelfGroupFolders(
            groups = folderGroups,
            books = groupGridBooks,
            allCustomGroupMask = groupGridCustomGroupMask,
        )
    }

    private fun openBookFromGroupGrid(book: Book) {
        startActivityForBook(book)
    }

    private fun openBookInfoFromGroupGrid(book: Book) {
        startActivity<BookInfoActivity> {
            putExtra("name", book.name)
            putExtra("author", book.author)
        }
    }

    private fun updateGroupGridVisibility() {
        showGroupGrid = AppConfig.bookshelfHomeMode == BookshelfHomeMode.GROUP_GRID
        binding.bookshelfGroupGrid.isVisible = showGroupGrid
        binding.bookshelfPageContainer.isVisible = !showGroupGrid
    }

    fun back(): Boolean = false

    private fun updateFloatingDockSettings() {
        val displayMetrics = resources.displayMetrics
        dockContentTopInsetPx = requireContext().statusBarHeight
        val topGapPx = BookshelfFloatingDockConfig.resolveTopDistancePx(
            storedDistancePx = AppConfig.bookshelfFloatingDockTopDistancePx,
            screenWidthPx = displayMetrics.widthPixels,
            density = displayMetrics.density,
            statusBarHeightPx = dockContentTopInsetPx
        )
        dockTopDistancePx = BookshelfFloatingDockConfig.screenTopDistancePx(
            topGapPx = topGapPx,
            statusBarHeightPx = dockContentTopInsetPx
        )
        dockTransparency = AppConfig.bookshelfFloatingDockTransparency
        dockSearchPosition = AppConfig.bookshelfFloatingDockSearchPosition
        configuredTopBarStyle = AppConfig.bookshelfTopBarStyle
    }

    override fun onResume() {
        super.onResume()
        updateFloatingDockSettings()
        if (bookGroups.isEmpty()) return
        val visibleGroupIndex = resolveVisibleGroupIndex(selectedGroupIndex)
        if (visibleGroupIndex != selectedGroupIndex) {
            selectGroup(visibleGroupIndex, force = true)
        } else if (
            (pendingGroupSelection || !isSelectedGroupMounted())
        ) {
            selectGroup(selectedGroupIndex, force = true)
        }
    }

    private fun openBookshelfManage() {
        startActivity<BookshelfManageActivity> {
            putExtra("groupId", groupId)
        }
    }

    private fun showSortMenu(anchorRoot: View, anchorBoundsInRoot: Rect) {
        val currentSort = currentBookSort()
        NgActionPopup(
            requireContext(),
            sortValues.map { sort ->
                NgActionPopupItem(
                    itemId = SORT_MENU_ID_OFFSET + sort,
                    title = getString(sortLabelRes(sort)),
                    iconRes = sortIconRes(sort),
                    checked = sort == currentSort,
                    payload = sort
                )
            }
        ) { item ->
            (item.payload as? Int)?.let(::updateBookSort)
        }.show(
            anchorRoot = anchorRoot,
            anchorBoundsInRoot = anchorBoundsInRoot,
            marginDp = 2,
            verticalAnchorInsetDp = 8
        )
    }

    private fun updateBookSort(sort: Int) {
        selectedGroup?.let { group ->
            if (group.bookSort >= 0) {
                group.bookSort = sort
                appDb.bookGroupDao.update(group)
            } else {
                AppConfig.bookshelfSort = sort
            }
        } ?: run {
            AppConfig.bookshelfSort = sort
        }
        upSort()
    }

    private fun currentBookSort(): Int {
        return selectedGroup?.getRealBookSort() ?: AppConfig.bookshelfSort
    }

    private fun sortLabelRes(sort: Int): Int {
        return when (sort) {
            1 -> R.string.bookshelf_px_1
            2 -> R.string.bookshelf_px_2
            3 -> R.string.bookshelf_px_3
            4 -> R.string.bookshelf_px_4
            5 -> R.string.bookshelf_px_5
            else -> R.string.bookshelf_px_0
        }
    }

    private fun sortIconRes(sort: Int): Int {
        return when (sort) {
            0 -> R.drawable.ic_history
            1 -> R.drawable.ic_update
            2 -> R.drawable.ic_ai_capability_text
            3 -> R.drawable.ic_drag_handle
            5 -> R.drawable.ic_author
            else -> R.drawable.ic_baseline_sort_24
        }
    }

    private fun onBookshelfMenuItemClick(itemId: Int) {
        if (itemId == R.id.menu_read_record) {
            startActivity<ReadRecordActivity>()
        } else {
            handleBookshelfMenuItem(itemId)
        }
    }

    @Synchronized
    override fun upGroup(data: List<BookGroup>) {
        if (data.isEmpty()) {
            appDb.bookGroupDao.enableGroup(BookGroup.IdAll)
        } else {
            val noGroup = BookGroup(
                groupId = BookGroup.IdNoGroup,
                groupName = getString(R.string.no_group),
                order = Int.MIN_VALUE,
            )
            val visibleGroups = data
                .filterNot { it.groupId == BookGroup.IdNoGroup }
                .toMutableList()
                .apply {
                    val allIndex = indexOfFirst { it.groupId == BookGroup.IdAll }
                    add(if (allIndex >= 0) allIndex + 1 else 0, noGroup)
                }
            if (visibleGroups != bookGroups) {
                bookGroups.clear()
                bookGroups.addAll(visibleGroups)
                dockGroups = visibleGroups.map { group ->
                    BookshelfDockGroup(
                        groupId = group.groupId,
                        name = group.groupName,
                    )
                }
                updateGroupGridFolders()
                selectSavedGroup()
            }
        }
    }

    override fun upSort() {
        updateGroupFragments()
    }

    private fun selectSavedGroup() {
        if (bookGroups.isEmpty()) return
        val targetPosition = resolveVisibleGroupIndex(AppConfig.saveTabPosition)
        selectGroup(targetPosition, force = true)
    }

    private fun resolveVisibleGroupIndex(index: Int): Int {
        val safeIndex = index.coerceIn(0, bookGroups.lastIndex)
        val resolvedTopBarStyle = BookshelfTopBarStyle.resolveForLayout(
            configuredStyle = configuredTopBarStyle,
            groupGridMode = showGroupGrid,
        )
        if (resolvedTopBarStyle == BookshelfTopBarStyle.GROUP_NAVIGATION &&
            bookGroups[safeIndex].groupId == BookGroup.IdNoGroup
        ) {
            return bookGroups.indexOfFirst { it.groupId == BookGroup.IdAll }
                .takeIf { it >= 0 }
                ?: safeIndex
        }
        return safeIndex
    }

    private fun selectGroup(
        index: Int,
        showReselectionFeedback: Boolean = false,
        force: Boolean = false,
    ) {
        val group = bookGroups.getOrNull(index) ?: return
        val currentFragment = activeBooksFragment
        val reselected = index == selectedGroupIndex &&
            currentFragment?.configuredGroupId == group.groupId &&
            isFragmentMounted(currentFragment)
        if (reselected && !force) {
            if (showReselectionFeedback) {
                toastOnUi("${group.groupName}(${currentFragment.getBooksCount()})")
            }
            return
        }
        selectedGroupIndex = index
        AppConfig.saveTabPosition = index
        onlyUpdateRead = group.onlyUpdateRead
        if (childFragmentManager.isStateSaved) {
            pendingGroupSelection = true
            return
        }
        showGroupFragment(group)
    }

    private fun showGroupFragment(group: BookGroup) {
        val manager = childFragmentManager
        val validGroupIds = bookGroups.mapTo(hashSetOf(), BookGroup::groupId)
        manager.fragments.filterIsInstance<BooksFragment>().forEach { fragment ->
            fragmentMap[fragment.configuredGroupId] = fragment
        }
        val tag = booksFragmentTag(group.groupId)
        val target = (manager.findFragmentByTag(tag) as? BooksFragment)
            ?: fragmentMap[group.groupId]
            ?: BooksFragment(group)
        target.updateGroup(group)
        if (target.isAdded && !target.isDetached && !isFragmentMounted(target)) {
            manager.beginTransaction()
                .setReorderingAllowed(true)
                .detach(target)
                .commitNow()
        }
        val transaction = manager.beginTransaction().setReorderingAllowed(true)
        manager.fragments.filterIsInstance<BooksFragment>().forEach { fragment ->
            val fragmentGroupId = fragment.configuredGroupId
            when {
                fragmentGroupId !in validGroupIds -> {
                    transaction.remove(fragment)
                    fragmentMap.remove(fragmentGroupId)
                }

                fragment !== target && fragment.isAdded && !fragment.isDetached -> {
                    transaction.detach(fragment)
                }
            }
        }
        when {
            target.isDetached -> transaction.attach(target)

            !target.isAdded -> transaction.add(
                R.id.bookshelf_page_container,
                target,
                tag,
            )
        }
        transaction.setPrimaryNavigationFragment(target)
        transaction.commitNow()
        fragmentMap[group.groupId] = target
        activeBooksFragment = target
        pendingGroupSelection = false
    }

    private fun isSelectedGroupMounted(): Boolean {
        val group = selectedGroup ?: return false
        val fragment = activeBooksFragment
            ?.takeIf { it.configuredGroupId == group.groupId }
            ?: childFragmentManager.findFragmentByTag(
                booksFragmentTag(group.groupId)
            ) as? BooksFragment
            ?: fragmentMap[group.groupId]
        return isFragmentMounted(fragment)
    }

    private fun isFragmentMounted(fragment: BooksFragment?): Boolean {
        return fragment?.isAdded == true &&
            !fragment.isDetached &&
            fragment.view?.parent === binding.bookshelfPageContainer
    }

    private fun updateGroupFragments() {
        bookGroups.forEach { group ->
            val fragment = fragmentMap[group.groupId]
                ?: childFragmentManager.findFragmentByTag(
                    booksFragmentTag(group.groupId)
                ) as? BooksFragment
            fragment?.updateGroup(group)
        }
    }

    override fun gotoTop() {
        if (showGroupGrid) {
            groupGridScrollToTopToken++
        } else {
            activeBooksFragment?.gotoTop()
        }
    }

    override fun onDestroyView() {
        binding.bookshelfScreen.setTag(R.id.bookshelf_floating_dock, null)
        super.onDestroyView()
    }

    private fun booksFragmentTag(groupId: Long): String {
        return "bookshelf-group-$groupId"
    }
}
