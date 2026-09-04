@file:Suppress("DEPRECATION")

package io.legado.app.ui.main

import android.content.res.ColorStateList
import android.graphics.Rect
import android.os.Bundle
import android.text.format.DateUtils
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.view.doOnNextLayout
import androidx.core.view.get
import androidx.core.view.postDelayed
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppConst.appInfo
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.databinding.ActivityMainBinding
import io.legado.app.help.AppWebDav
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.FloatingBottomBarConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.NgThemeNavigationIcons
import io.legado.app.help.config.NgThemeRuntimeAssets
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.storage.Backup
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.about.CrashLogsDialog
import io.legado.app.ui.association.ImportBookSourceDialog
import io.legado.app.ui.association.ImportReplaceRuleDialog
import io.legado.app.ui.association.ImportRssSourceDialog
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.config.AiChatActivity
import io.legado.app.ui.design.components.view.NgFloatingTabItem
import io.legado.app.ui.design.components.view.NgFloatingTabBarVariant
import io.legado.app.ui.main.bookshelf.BaseBookshelfFragment
import io.legado.app.ui.main.bookshelf.style1.BookshelfFragment1
import io.legado.app.ui.main.explore.ExploreFragment
import io.legado.app.ui.main.my.MyFragment
import io.legado.app.ui.main.rss.RssFragment
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.ui.widget.text.BadgeView
import io.legado.app.utils.isCreated
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.navigationBarHeight
import io.legado.app.utils.observeEvent
import io.legado.app.utils.dpToPx
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import splitties.views.bottomPadding
import kotlin.coroutines.resume
import androidx.core.view.get
import io.legado.app.help.update.AppUpdate
import io.legado.app.ui.about.UpdateDialog
import kotlin.math.abs

/**
 * 主界面
 */
@Suppress("PrivatePropertyName")
class MainActivity : VMBaseActivity<ActivityMainBinding, MainViewModel>(),
    BottomNavigationView.OnNavigationItemSelectedListener,
    BottomNavigationView.OnNavigationItemReselectedListener,
    MainViewModel.CallBack {

    override val binding by viewBinding(ActivityMainBinding::inflate)
    override val viewModel by viewModels<MainViewModel>()
    private val idBookshelf = 0
    private val idBookshelf1 = 11
    private val idExplore = 1
    private val idRss = 2
    private val idMy = 3
    private var exitTime: Long = 0
    private var bookshelfReselected: Long = 0
    private var exploreReselected: Long = 0
    private var pagePosition = 0
    private var mainPagerScrollState = ViewPager.SCROLL_STATE_IDLE
    private var aiChatSwipeStartX = 0f
    private var aiChatSwipeStartY = 0f
    private var aiChatSwipeStartedOnBookshelf = false
    private val fragmentMap = hashMapOf<Int, Fragment>()
    private var bottomMenuCount = 4
    private val EXIT_INTERVAL = 2000L
    private val AI_CHAT_SWIPE_START_RATIO = 0.5f
    private val AI_CHAT_SWIPE_DISTANCE_DP = 120
    private val realPositions = arrayOf(idBookshelf, idExplore, idRss, idMy)
    private val adapter by lazy {
        TabFragmentPageAdapter(supportFragmentManager)
    }
    private var onUpBooksBadgeView: BadgeView? = null
    private var bookshelfBadgeCount = 0
    private var defaultBottomNavigationIconTint: ColorStateList? = null
    private var defaultBottomNavigationIconSize = 0
    private var bottomNavigationIconTintCaptured = false

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        upBottomMenu()
        initView()
        upHomePage()
        openLastReadBookAfterStartup(savedInstanceState)
        onBackPressedDispatcher.addCallback(this) {
            if (pagePosition != 0) {
                binding.viewPagerMain.currentItem = 0
                return@addCallback
            }
            (fragmentMap[getFragmentId(0)] as? BookshelfFragment1)?.let {
                if (it.back()) {
                    return@addCallback
                }
            }
            if (System.currentTimeMillis() - exitTime > EXIT_INTERVAL) {
                toastOnUi(R.string.double_click_exit)
                exitTime = System.currentTimeMillis()
            } else {
                if (!BaseReadAloudService.isPlay()) {
                    finish()
                } else {
                    moveTaskToBack(true)
                }
            }
        }
    }

    private fun openLastReadBookAfterStartup(savedInstanceState: Bundle?) {
        if (savedInstanceState != null || !getPrefBoolean(PreferKey.defaultToRead)) {
            return
        }
        binding.root.post {
            lifecycleScope.launch {
                val hasLastReadBook = withContext(IO) {
                    appDb.bookDao.lastReadBook != null
                }
                if (hasLastReadBook && !isFinishing && !isDestroyed) {
                    startActivity<ReadBookActivity>()
                }
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        lifecycleScope.launch {
            //隐私协议
            if (!privacyPolicy()) return@launch
            //版本更新
            upVersion()
            checkUpdateOnProcessStart()
            notifyAppCrash()
            //备份同步
            backupSync()
            //设置回调
            viewModel.setActivityCallback(this@MainActivity)
            //自动更新书源
            binding.viewPagerMain.postDelayed(1000) {
                viewModel.ruleSubsUp()
            }
            //自动更新书籍
            val isAutoRefreshedBook = savedInstanceState?.getBoolean("isAutoRefreshedBook") ?: false
            if (AppConfig.autoRefreshBook && !isAutoRefreshedBook) {
                binding.viewPagerMain.postDelayed(2000) {
                    viewModel.upAllBookToc()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateBottomNavigationStyle()
        refreshAiChatFab()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        handleAiChatSwipe(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun handleAiChatSwipe(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                aiChatSwipeStartX = event.rawX
                aiChatSwipeStartY = event.rawY
                val startLimit = window.decorView.width * AI_CHAT_SWIPE_START_RATIO
                aiChatSwipeStartedOnBookshelf = pagePosition == 0 &&
                        event.rawX <= startLimit &&
                        !isTouchInsideBookshelfFloatingDock(event)
            }

            MotionEvent.ACTION_UP -> {
                if (aiChatSwipeStartedOnBookshelf) {
                    val dx = event.rawX - aiChatSwipeStartX
                    val dy = event.rawY - aiChatSwipeStartY
                    val isRightSwipe = dx >= AI_CHAT_SWIPE_DISTANCE_DP.dpToPx()
                            && abs(dx) > abs(dy) * 1.8f
                    if (isRightSwipe) {
                        startBookshelfGenericAiChat()
                    }
                }
                resetAiChatSwipe()
            }

            MotionEvent.ACTION_CANCEL -> resetAiChatSwipe()
        }
    }

    private fun isTouchInsideBookshelfFloatingDock(event: MotionEvent): Boolean {
        val floatingDock = binding.root.findViewById<View>(R.id.bookshelf_floating_dock)
            ?.takeIf { it.isShown }
        val bounds = Rect()
        if (floatingDock?.getGlobalVisibleRect(bounds) == true) {
            return bounds.contains(event.rawX.toInt(), event.rawY.toInt())
        }
        val composeBounds = binding.root.findViewById<View>(R.id.bookshelf_screen)
            ?.getTag(R.id.bookshelf_floating_dock) as? Rect
            ?: return false
        return composeBounds.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    private fun resetAiChatSwipe() {
        aiChatSwipeStartX = 0f
        aiChatSwipeStartY = 0f
        aiChatSwipeStartedOnBookshelf = false
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean = binding.run {
        when (item.itemId) {
            R.id.menu_bookshelf ->
                viewPagerMain.setCurrentItem(0, false)

            R.id.menu_discovery ->
                viewPagerMain.setCurrentItem(realPositions.indexOf(idExplore), false)

            R.id.menu_rss ->
                viewPagerMain.setCurrentItem(realPositions.indexOf(idRss), false)

            R.id.menu_my_config ->
                viewPagerMain.setCurrentItem(realPositions.indexOf(idMy), false)
        }
        return false
    }

    override fun onNavigationItemReselected(item: MenuItem) {
        val pageId = when (item.itemId) {
            R.id.menu_bookshelf -> idBookshelf
            R.id.menu_discovery -> idExplore
            R.id.menu_rss -> idRss
            R.id.menu_my_config -> idMy
            else -> return
        }
        handleNavigationReselected(pageId)
    }

    private fun handleNavigationReselected(pageId: Int) {
        when (pageId) {
            idBookshelf -> {
                if (System.currentTimeMillis() - bookshelfReselected > 300) {
                    bookshelfReselected = System.currentTimeMillis()
                } else {
                    (fragmentMap[getFragmentId(0)] as? BaseBookshelfFragment)?.gotoTop()
                }
            }

            idExplore -> {
                if (System.currentTimeMillis() - exploreReselected > 300) {
                    exploreReselected = System.currentTimeMillis()
                } else {
                    (fragmentMap[1] as? ExploreFragment)?.compressExplore()
                }
            }
        }
    }

    private fun initView() = binding.run {
        viewPagerMain.setEdgeEffectColor(primaryColor)
        viewPagerMain.offscreenPageLimit = 3
        viewPagerMain.adapter = adapter
        viewPagerMain.addOnPageChangeListener(PageChangeCallback())
        bottomNavigationView.setOnNavigationItemSelectedListener(this@MainActivity)
        bottomNavigationView.setOnNavigationItemReselectedListener(this@MainActivity)
        floatingBottomNavigation.setVariant(NgFloatingTabBarVariant.CONTENT_OVERLAY)
        bindFloatingBottomBackdropToCurrentPage()
        if (AppConfig.isEInkMode) {
            bottomNavigationView.setBackgroundResource(R.drawable.bg_eink_border_top)
        }
        root.setOnApplyWindowInsetsListenerCompat { _, windowInsets ->
            val height = windowInsets.navigationBarHeight
            bottomNavigationView.bottomPadding = height
            floatingBottomNavigationContainer.bottomPadding = height
            windowInsets
        }
        updateFloatingBottomMenu()
        updateBottomNavigationStyle()
        fabAiChat.setOnClickListener {
            if (pagePosition == 0) {
                startBookshelfGenericAiChat()
            } else {
                startActivity<AiChatActivity>()
            }
        }
        refreshAiChatFab()
    }

    private fun bindFloatingBottomBackdropToCurrentPage() = binding.run {
        val position = viewPagerMain.currentItem.coerceIn(0, bottomMenuCount - 1)
        val pageView = fragmentMap[getFragmentId(position)]
            ?.view
            ?.takeIf { it.isAttachedToWindow }
        val backgroundSource = root.rootView.findViewById<View>(
            R.id.ng_liquid_glass_backdrop_source,
        )
        floatingBottomNavigation.setLiquidBackdropSource(pageView ?: backgroundSource)
    }

    private fun startBookshelfGenericAiChat() {
        startActivity<AiChatActivity>()
    }

    private fun refreshAiChatFab() = binding.run {
        fabAiChat.visibility = if (AiConfig.chatFabEnabled) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
        fabAiChat.updateAccentColor(accentColor)
    }

    /**
     * 用户隐私与协议
     */
    private suspend fun privacyPolicy(): Boolean = suspendCancellableCoroutine sc@{ block ->
        if (LocalConfig.privacyPolicyOk) {
            block.resume(true)
            return@sc
        }
        val privacyPolicy = String(assets.open("privacyPolicy.md").readBytes())
        alert(getString(R.string.privacy_policy), privacyPolicy) {
            positiveButton(R.string.agree) {
                LocalConfig.privacyPolicyOk = true
                block.resume(true)
            }
            negativeButton(R.string.refuse) {
                finish()
                block.resume(false)
            }
        }
    }

    /**
     * 版本更新日志
     */
    private suspend fun upVersion() = suspendCancellableCoroutine sc@{ block ->
        if (LocalConfig.versionCode == appInfo.versionCode) {
            block.resume(null)
            return@sc
        }
        LocalConfig.versionCode = appInfo.versionCode
        if (LocalConfig.isFirstOpenApp) {
            val help = String(assets.open("web/help/md/appHelp.md").readBytes())
            val dialog = TextDialog(getString(R.string.help), help, TextDialog.Mode.MD)
            dialog.setOnDismissListener {
                block.resume(null)
            }
            showDialogFragment(dialog)
        } else if (!BuildConfig.DEBUG) {
            val log = String(assets.open("updateLog.md").readBytes())
            val dialog = TextDialog(getString(R.string.update_log), log, TextDialog.Mode.MD)
            dialog.setOnDismissListener {
                block.resume(null)
            }
            showDialogFragment(dialog)
        } else {
            block.resume(null)
        }
    }

    /**
     * 每个应用进程冷启动只自动检查一次，Activity 重建不重复触发。
     */
    private fun checkUpdateOnProcessStart() {
        if (!AppConfig.autoUpdateVariant || !AppUpdate.tryStartAutoCheck()) return
        AppUpdate.gitHubUpdate.check(lifecycleScope)
            .onSuccess {
                showDialogFragment(UpdateDialog(it))
            }
    }

    private fun notifyAppCrash() {
        if (!LocalConfig.appCrash || BuildConfig.DEBUG) {
            return
        }
        LocalConfig.appCrash = false
        alert(getString(R.string.draw), "检测到阅读发生了崩溃，是否打开崩溃日志以便报告问题？") {
            yesButton {
                showDialogFragment<CrashLogsDialog>()
            }
            noButton()
        }
    }

    /**
     * 备份同步
     */
    private fun backupSync() {
        if (!AppConfig.autoCheckNewBackup) {
            return
        }
        lifecycleScope.launch {
            val lastBackupFile =
                withContext(IO) { AppWebDav.lastBackUp().getOrNull() } ?: return@launch
            if (lastBackupFile.lastModify - LocalConfig.lastBackup > DateUtils.MINUTE_IN_MILLIS) {
                LocalConfig.lastBackup = lastBackupFile.lastModify
                alert(R.string.restore, R.string.webdav_after_local_restore_confirm) {
                    cancelButton()
                    okButton {
                        viewModel.restoreWebDav(lastBackupFile.displayName)
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (AppConfig.autoRefreshBook) {
            outState.putBoolean("isAutoRefreshedBook", true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Coroutine.async {
            BookHelp.clearInvalidCache()
        }
        if (!BuildConfig.DEBUG) {
            Backup.autoBack(this)
        }
    }

    /**
     * 如果重启太快fragment不会重建,这里更新一下书架的排序
     */
    override fun recreate() {
        (fragmentMap[getFragmentId(0)] as? BaseBookshelfFragment)?.run {
            upSort()
        }
        super.recreate()
    }

    override fun observeLiveBus() {
        viewModel.onUpBooksLiveData.observe(this) {
            bookshelfBadgeCount = it
            if (onUpBooksBadgeView == null) {
                onUpBooksBadgeView = binding.bottomNavigationView.addBadgeView(0)
            }
            onUpBooksBadgeView!!.setBadgeCount(it)
            updateFloatingBottomMenu()
        }
        observeEvent<String>(EventBus.RECREATE) {
            recreate()
        }
        observeEvent<Boolean>(EventBus.NOTIFY_MAIN) {
            binding.apply {
                if (it) {
                    bottomNavigationView.menu.clear()
                    bottomNavigationView.inflateMenu(R.menu.main_bnv)
                    onUpBooksBadgeView = null
                }
                upBottomMenu()
                if (it) {
                    viewPagerMain.setCurrentItem(bottomMenuCount - 1, false)
                }
            }
        }
        observeEvent<String>(PreferKey.threadCount) {
            viewModel.upPool()
        }
    }

    private fun upBottomMenu() {
        val showDiscovery = AppConfig.showDiscovery
        val showRss = AppConfig.showRSS
        binding.bottomNavigationView.menu.let { menu ->
            menu.findItem(R.id.menu_discovery).isVisible = showDiscovery
            menu.findItem(R.id.menu_rss).isVisible = showRss
        }
        var index = 0
        if (showDiscovery) {
            index++
            realPositions[index] = idExplore
        }
        if (showRss) {
            index++
            realPositions[index] = idRss
        }
        index++
        realPositions[index] = idMy
        bottomMenuCount = index + 1
        updateFloatingBottomMenu()
        adapter.notifyDataSetChanged()
    }

    private fun updateFloatingBottomMenu() = binding.run {
        if (!bottomNavigationIconTintCaptured) {
            defaultBottomNavigationIconTint = bottomNavigationView.itemIconTintList
            defaultBottomNavigationIconSize = bottomNavigationView.itemIconSize
            bottomNavigationIconTintCaptured = true
        }
        val themedIcons = NgThemeRuntimeAssets.navigationIcons(this@MainActivity)
        val themedIconSizeDp = if (themedIcons == null) 24 else 40
        updateStandardBottomNavigationIcons(themedIcons)
        val items = (0 until bottomMenuCount).map { position ->
            when (realPositions[position]) {
                idBookshelf -> NgFloatingTabItem(
                    iconRes = R.drawable.ic_bottom_books_e,
                    selectedIconRes = R.drawable.ic_bottom_books_s,
                    iconDrawable = themedIcons?.bookshelf(this@MainActivity),
                    tintIcon = themedIcons == null,
                    iconSizeDp = themedIconSizeDp,
                    count = bookshelfBadgeCount.takeIf { it > 0 },
                    contentDescription = getString(R.string.bookshelf)
                )

                idExplore -> NgFloatingTabItem(
                    iconRes = R.drawable.ic_bottom_explore_e,
                    selectedIconRes = R.drawable.ic_bottom_explore_s,
                    iconDrawable = themedIcons?.explore(this@MainActivity),
                    tintIcon = themedIcons == null,
                    iconSizeDp = themedIconSizeDp,
                    contentDescription = getString(R.string.discovery)
                )

                idRss -> NgFloatingTabItem(
                    iconRes = R.drawable.ic_bottom_rss_feed_e,
                    selectedIconRes = R.drawable.ic_bottom_rss_feed_s,
                    iconDrawable = themedIcons?.rss(this@MainActivity),
                    tintIcon = themedIcons == null,
                    iconSizeDp = themedIconSizeDp,
                    contentDescription = getString(R.string.rss)
                )

                else -> NgFloatingTabItem(
                    iconRes = R.drawable.ic_bottom_person_e,
                    selectedIconRes = R.drawable.ic_bottom_person_s,
                    iconDrawable = themedIcons?.my(this@MainActivity),
                    tintIcon = themedIcons == null,
                    iconSizeDp = themedIconSizeDp,
                    contentDescription = getString(R.string.my)
                )
            }
        }
        floatingBottomNavigation.setItems(
            items = items,
            selectedIndex = pagePosition.coerceIn(items.indices)
        ) { position ->
            if (position == pagePosition) {
                handleNavigationReselected(realPositions[position])
            } else {
                viewPagerMain.setCurrentItem(position, false)
            }
        }
    }

    private fun updateStandardBottomNavigationIcons(themedIcons: NgThemeNavigationIcons?) =
        binding.bottomNavigationView.run {
            itemIconTintList = defaultBottomNavigationIconTint.takeIf { themedIcons == null }
            itemIconSize = if (themedIcons == null) {
                defaultBottomNavigationIconSize
            } else {
                40.dpToPx()
            }
            menu.findItem(R.id.menu_bookshelf).icon = themedIcons?.bookshelf(this@MainActivity)
                ?: getDrawable(R.drawable.ic_bottom_books)
            menu.findItem(R.id.menu_discovery).icon = themedIcons?.explore(this@MainActivity)
                ?: getDrawable(R.drawable.ic_bottom_explore)
            menu.findItem(R.id.menu_rss).icon = themedIcons?.rss(this@MainActivity)
                ?: getDrawable(R.drawable.ic_bottom_rss_feed)
            menu.findItem(R.id.menu_my_config).icon = themedIcons?.my(this@MainActivity)
                ?: getDrawable(R.drawable.ic_bottom_person)
        }

    private fun updateBottomNavigationStyle() = binding.run {
        val useFloating = AppConfig.useFloatingBottomBar
        bottomNavigationView.visibility = if (useFloating) View.GONE else View.VISIBLE
        floatingBottomNavigationContainer.visibility =
            if (useFloating) View.VISIBLE else View.GONE
        if (useFloating) {
            val bottomDistancePx = FloatingBottomBarConfig.resolveBottomDistancePx(
                storedDistancePx = AppConfig.floatingBottomBarBottomDistancePx,
                density = resources.displayMetrics.density
            )
            (floatingBottomNavigation.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                layoutParams ->
                if (layoutParams.bottomMargin != bottomDistancePx) {
                    layoutParams.bottomMargin = bottomDistancePx
                    floatingBottomNavigation.layoutParams = layoutParams
                }
            }
            floatingBottomNavigation.setSurfaceAlpha(
                FloatingBottomBarConfig.surfaceAlpha(
                    AppConfig.floatingBottomBarTransparency
                )
            )
            floatingBottomNavigation.select(pagePosition, notify = false)
        }
    }

    fun resolveFloatingBottomContentInset(onResolved: (Int) -> Unit) = binding.run {
        fun resolve() {
            onResolved(
                if (AppConfig.useFloatingBottomBar) {
                    floatingBottomNavigationContainer.height
                } else {
                    0
                }
            )
        }
        if (AppConfig.useFloatingBottomBar &&
            (floatingBottomNavigationContainer.height == 0 ||
                    floatingBottomNavigationContainer.isLayoutRequested)
        ) {
            floatingBottomNavigationContainer.doOnNextLayout { resolve() }
        } else {
            resolve()
        }
    }

    fun applyFloatingBottomContentInset(target: View, baseBottomPadding: Int = 0) {
        resolveFloatingBottomContentInset { inset ->
            if (target.isAttachedToWindow) {
                target.updatePadding(bottom = baseBottomPadding + inset)
            }
        }
    }

    private fun upHomePage() {
        when (AppConfig.defaultHomePage) {
            "bookshelf" -> {}
            "explore" -> if (AppConfig.showDiscovery) {
                binding.viewPagerMain.setCurrentItem(realPositions.indexOf(idExplore), false)
            }

            "rss" -> if (AppConfig.showRSS) {
                binding.viewPagerMain.setCurrentItem(realPositions.indexOf(idRss), false)
            }

            "my" -> binding.viewPagerMain.setCurrentItem(realPositions.indexOf(idMy), false)
        }
    }

    private fun getFragmentId(position: Int): Int {
        val id = realPositions[position]
        if (id == idBookshelf) {
            return idBookshelf1
        }
        return id
    }

    private inner class PageChangeCallback : ViewPager.SimpleOnPageChangeListener() {

        override fun onPageScrollStateChanged(state: Int) {
            mainPagerScrollState = state
            if (state == ViewPager.SCROLL_STATE_IDLE) {
                bindFloatingBottomBackdropToCurrentPage()
            } else {
                binding.floatingBottomNavigation.setLiquidBackdropSource(
                    binding.viewPagerMain,
                )
            }
        }

        override fun onPageSelected(position: Int) {
            pagePosition = position
            binding.bottomNavigationView.menu[realPositions[position]].isChecked = true
            binding.floatingBottomNavigation.select(position, notify = false)
            if (mainPagerScrollState == ViewPager.SCROLL_STATE_IDLE) {
                bindFloatingBottomBackdropToCurrentPage()
            }
        }

    }

    @Suppress("DEPRECATION")
    private inner class TabFragmentPageAdapter(fm: FragmentManager) :
        FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        private fun getId(position: Int): Int {
            return getFragmentId(position)
        }

        override fun getItemPosition(any: Any): Int {
            val position = (any as MainFragmentInterface).position
                ?: return POSITION_NONE
            val fragmentId = getId(position)
            if ((fragmentId == idBookshelf1 && any is BookshelfFragment1)
                || (fragmentId == idExplore && any is ExploreFragment)
                || (fragmentId == idRss && any is RssFragment)
                || (fragmentId == idMy && any is MyFragment)
            ) {
                return POSITION_UNCHANGED
            }
            return POSITION_NONE
        }

        override fun getItem(position: Int): Fragment {
            return when (getId(position)) {
                idBookshelf1 -> BookshelfFragment1(position)
                idExplore -> ExploreFragment(position)
                idRss -> RssFragment(position)
                else -> MyFragment(position)
            }
        }

        override fun getCount(): Int {
            return bottomMenuCount
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            var fragment = super.instantiateItem(container, position) as Fragment
            if (fragment.isCreated && getItemPosition(fragment) == POSITION_NONE) {
                destroyItem(container, position, fragment)
                fragment = super.instantiateItem(container, position) as Fragment
            }
            fragmentMap[getId(position)] = fragment
            if (position == binding.viewPagerMain.currentItem) {
                container.post {
                    if (
                        position == binding.viewPagerMain.currentItem &&
                        mainPagerScrollState == ViewPager.SCROLL_STATE_IDLE
                    ) {
                        bindFloatingBottomBackdropToCurrentPage()
                    }
                }
            }
            return fragment
        }

    }

    override fun openImportUi(type:Int, source: String) {
        when (type) {
            0 -> showDialogFragment(
                ImportBookSourceDialog(source)
            )
            1 -> showDialogFragment(
                ImportRssSourceDialog(source)
            )
            2 -> showDialogFragment(
                ImportReplaceRuleDialog(source)
            )
        }
    }

}
