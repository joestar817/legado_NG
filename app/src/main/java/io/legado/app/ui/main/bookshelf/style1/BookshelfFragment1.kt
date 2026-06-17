@file:Suppress("DEPRECATION")

package io.legado.app.ui.main.bookshelf.style1

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import com.google.android.material.tabs.TabLayout
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.FragmentBookshelf1Binding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.transparentNavBar
import io.legado.app.ui.about.ReadRecordActivity
import io.legado.app.ui.book.group.GroupEditDialog
import io.legado.app.ui.book.manage.BookshelfManageActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.main.bookshelf.BaseBookshelfFragment
import io.legado.app.ui.main.bookshelf.style1.books.BooksFragment
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.isCreated
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlin.collections.set

/**
 * 书架界面
 */
class BookshelfFragment1() : BaseBookshelfFragment(R.layout.fragment_bookshelf1),
    TabLayout.OnTabSelectedListener,
    SearchView.OnQueryTextListener {

    companion object {
        private const val SORT_MENU_GROUP_ID = 20260617
        private const val SORT_MENU_ID_OFFSET = 1000
        private const val GRADIENT_GROUP_SELECTED_COLOR = 0xDE000000.toInt()
        private const val GRADIENT_GROUP_UNSELECTED_COLOR = 0x8A000000.toInt()
        private val sortValues = intArrayOf(4, 0, 1, 2, 3, 5)
    }

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    private val binding by viewBinding(FragmentBookshelf1Binding::bind)
    private val adapter by lazy { TabFragmentPageAdapter(childFragmentManager) }
    private val tabLayout: TabLayout by lazy {
        binding.titleBar.findViewById(R.id.tab_layout)
    }
    private val bookGroups = mutableListOf<BookGroup>()
    private val fragmentMap = hashMapOf<Long, BooksFragment>()
    override val groupId: Long get() = selectedGroup?.groupId ?: 0

    override val books: List<Book>
        get() {
            val fragment = fragmentMap[groupId]
            return fragment?.getBooks() ?: emptyList()
        }

    override var onlyUpdateRead = false
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initView()
        initBookGroupData()
    }

    private val selectedGroup: BookGroup?
        get() = bookGroups.getOrNull(tabLayout.selectedTabPosition)

    private fun initView() {
        binding.titleBar.title = ""
        binding.titleBar.subtitle = ""
        animateTopBarIn()
        val searchView = binding.titleBar.findViewById<View>(R.id.tv_bookshelf_search)
        val moreButton = binding.titleBar.findViewById<View>(R.id.btn_bookshelf_more)
        applyTopBarBackground(searchView, moreButton)
        applyContentPanelBackground()
        searchView.bindSoftPress()
        moreButton.bindSoftPress()
        searchView.setOnClickListener {
            SearchActivity.start(requireContext(), null)
        }
        moreButton.setOnClickListener {
            showBookshelfMenu(it)
        }
        binding.tvBookshelfSort.bindSoftPress()
        binding.tvBookshelfEdit.bindSoftPress()
        binding.tvBookshelfViewHistory.bindSoftPress()
        binding.tvBookshelfSort.setOnClickListener {
            showSortMenu(it)
        }
        binding.tvBookshelfEdit.setOnClickListener {
            startActivity<BookshelfManageActivity> {
                putExtra("groupId", groupId)
            }
        }
        binding.tvBookshelfViewHistory.setOnClickListener {
            startActivity<ReadRecordActivity>()
        }
        updateSortLabel()
        binding.viewPagerBookshelf.setEdgeEffectColor(primaryColor)
        tabLayout.isTabIndicatorFullWidth = false
        tabLayout.tabMode = TabLayout.MODE_SCROLLABLE
        tabLayout.setSelectedTabIndicatorColor(requireContext().accentColor)
        tabLayout.setupWithViewPager(binding.viewPagerBookshelf)
        binding.viewPagerBookshelf.offscreenPageLimit = 1
        binding.viewPagerBookshelf.adapter = adapter
    }

    private fun applyTopBarBackground(searchView: View, moreButton: View) {
        if (isTransparentTopBar()) {
            searchView.setBackgroundResource(R.drawable.bg_bookshelf_top_search_transparent)
            moreButton.setBackgroundResource(R.drawable.bg_bookshelf_top_action_transparent)
        } else {
            searchView.setBackgroundResource(R.drawable.bg_bookshelf_top_search)
            moreButton.setBackgroundResource(R.drawable.bg_bookshelf_top_action)
        }
    }

    private fun applyContentPanelBackground() {
        binding.bookshelfContentPanel.setBackgroundResource(
            if (isTransparentTopBar()) {
                if (AppConfig.isNightTheme) {
                    R.drawable.bg_main_content_panel_night
                } else {
                    R.drawable.bg_main_content_panel
                }
            } else {
                R.color.transparent
            }
        )
    }

    private fun isTransparentTopBar(): Boolean {
        return requireContext().transparentNavBar || requireContext().getPrefBoolean(
            if (AppConfig.isNightTheme) {
                PreferKey.tNavBarN
            } else {
                PreferKey.tNavBar
            },
            false
        )
    }

    private fun animateTopBarIn() {
        binding.titleBar.alpha = 0f
        binding.titleBar.translationY = (-4).dpToPx().toFloat()
        binding.titleBar.post {
            binding.titleBar.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220L)
                .start()
        }
    }

    private fun View.bindSoftPress() {
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.animate()
                        .scaleX(0.97f)
                        .scaleY(0.97f)
                        .alpha(0.92f)
                        .setDuration(90L)
                        .start()
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(140L)
                        .start()
                }
            }
            false
        }
    }

    private fun showSortMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            sortValues.forEachIndexed { index, sort ->
                menu.add(
                    SORT_MENU_GROUP_ID,
                    SORT_MENU_ID_OFFSET + sort,
                    index,
                    getString(sortLabelRes(sort))
                )
            }
            menu.setGroupCheckable(SORT_MENU_GROUP_ID, true, true)
            menu.findItem(SORT_MENU_ID_OFFSET + currentBookSort())?.isChecked = true
            setOnMenuItemClickListener { item ->
                val sort = item.itemId - SORT_MENU_ID_OFFSET
                updateBookSort(sort)
                true
            }
            show()
        }
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
        updateSortLabel()
    }

    private fun updateSortLabel() {
        binding.tvBookshelfSort.text = getString(sortLabelRes(currentBookSort()))
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

    private fun showBookshelfMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.main_bookshelf, menu)
            menu.findItem(R.id.menu_search)?.isVisible = false
            setOnMenuItemClickListener(::onBookshelfMenuItemClick)
            show()
        }
    }

    private fun onBookshelfMenuItemClick(item: MenuItem): Boolean {
        onCompatOptionsItemSelected(item)
        return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        SearchActivity.start(requireContext(), query)
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        return false
    }

    @Synchronized
    override fun upGroup(data: List<BookGroup>) {
        if (data.isEmpty()) {
            appDb.bookGroupDao.enableGroup(BookGroup.IdAll)
        } else {
            if (data != bookGroups) {
                bookGroups.clear()
                bookGroups.addAll(data)
                adapter.notifyDataSetChanged()
                selectLastTab()
                tabLayout.post {
                    applyGroupTabViews()
                    updateGroupTabStyles()
                    updateSortLabel()
                }
                for (i in 0 until adapter.count) {
                    tabLayout.getTabAt(i)?.view?.setOnLongClickListener {
                        showDialogFragment(GroupEditDialog(bookGroups[i]))
                        true
                    }
                }
            }
        }
    }

    override fun upSort() {
        adapter.notifyDataSetChanged()
        updateSortLabel()
    }

    private fun selectLastTab() {
        tabLayout.post {
            tabLayout.removeOnTabSelectedListener(this)
            tabLayout.getTabAt(AppConfig.saveTabPosition)?.select()
            tabLayout.addOnTabSelectedListener(this)
            applyGroupTabViews()
            updateGroupTabStyles(animate = false)
            updateSortLabel()
        }
    }

    private fun applyGroupTabViews() {
        for (i in 0 until tabLayout.tabCount) {
            val tab = tabLayout.getTabAt(i) ?: continue
            val title = bookGroups.getOrNull(i)?.groupName ?: tab.text ?: ""
            val textView = tab.customView as? TextView ?: TextView(requireContext()).apply {
                gravity = Gravity.CENTER
                includeFontPadding = false
                isSingleLine = true
                setPadding(5.dpToPx(), 0, 5.dpToPx(), 0)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                tab.customView = this
            }
            textView.text = title
        }
    }

    private fun updateGroupTabStyles(animate: Boolean = true) {
        val selectedPosition = tabLayout.selectedTabPosition
        val gradientTheme = isGradientTheme()
        val normalTabTextColor = requireContext().getCompatColor(
            if (isTransparentTopBar()) R.color.primaryText else R.color.white
        )
        for (i in 0 until tabLayout.tabCount) {
            val textView = tabLayout.getTabAt(i)?.customView as? TextView ?: continue
            val selected = i == selectedPosition
            textView.setTextColor(
                if (gradientTheme) {
                    if (selected) GRADIENT_GROUP_SELECTED_COLOR else GRADIENT_GROUP_UNSELECTED_COLOR
                } else {
                    normalTabTextColor
                }
            )
            textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            textView.typeface = Typeface.create(
                if (selected) "sans-serif-medium" else "sans-serif",
                if (selected) Typeface.BOLD else Typeface.NORMAL
            )
            val targetTranslationY = if (selected) (-1).dpToPx().toFloat() else 0f
            if (animate) {
                textView.animate()
                    .translationY(targetTranslationY)
                    .setDuration(160L)
                    .start()
            } else {
                textView.translationY = targetTranslationY
                textView.scaleX = 1f
                textView.scaleY = 1f
            }
        }
    }

    private fun isGradientTheme(): Boolean {
        return AppConfig.themeMode == "4" || AppConfig.themeMode == "5"
    }

    override fun onTabReselected(tab: TabLayout.Tab) {
        selectedGroup?.let { group ->
            fragmentMap[group.groupId]?.let {
                toastOnUi("${group.groupName}(${it.getBooksCount()})")
            }
        }
    }

    override fun onTabUnselected(tab: TabLayout.Tab) {
        updateGroupTabStyles()
    }

    override fun onTabSelected(tab: TabLayout.Tab) {
        AppConfig.saveTabPosition = tab.position
        updateGroupTabStyles()
        updateSortLabel()
    }

    override fun gotoTop() {
        fragmentMap[groupId]?.gotoTop()
    }

    private inner class TabFragmentPageAdapter(fm: FragmentManager) :
        FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun getPageTitle(position: Int): CharSequence {
            return bookGroups[position].groupName
        }

        /**
         * 确定视图位置是否更改时调用
         * @return POSITION_NONE 已更改,刷新视图. POSITION_UNCHANGED 未更改,不刷新视图
         */
        override fun getItemPosition(any: Any): Int {
            val fragment = any as BooksFragment
            val position = fragment.position
            val group = bookGroups.getOrNull(position)
            if (fragment.groupId != group?.groupId) {
                return POSITION_NONE
            }
            val bookSort = group.getRealBookSort()
            fragment.setEnableRefresh(group.enableRefresh)
            if (fragment.bookSort != bookSort) {
                fragment.upBookSort(bookSort)
            }
            return POSITION_UNCHANGED
        }

        override fun getItem(position: Int): Fragment {
            val group = bookGroups[position]
            onlyUpdateRead = group.onlyUpdateRead
            return BooksFragment(position, group)
        }

        override fun getCount(): Int {
            return bookGroups.size
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            var fragment = super.instantiateItem(container, position) as BooksFragment
            val group = bookGroups[position]
            /**
             * Activity recreate 会复用之前的 Fragment，不正确的需要重新创建
             */
            if (fragment.isCreated && getItemPosition(fragment) == POSITION_NONE) {
                destroyItem(container, position, fragment)
                fragment = super.instantiateItem(container, position) as BooksFragment
            }
            fragmentMap[group.groupId] = fragment
            return fragment
        }

    }
}
