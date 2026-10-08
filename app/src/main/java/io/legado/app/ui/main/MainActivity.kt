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
import android.view.ViewConfiguration
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
import io.legado.app.help.ai.AiChatEntryStyle
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.BookshelfGestureConfig
import io.legado.app.help.config.BookshelfSwipeMode
import io.legado.app.help.config.resolveBookshelfSwipe
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
import io.legado.app.ui.book.read.aloud.ReadAloudMiniPlayer
import io.legado.app.ui.config.AiChatActivity
import io.legado.app.ui.design.components.view.NgFloatingTabItem
import io.legado.app.ui.design.components.view.NgFloatingTabBarVariant
import io.legado.app.ui.main.bookshelf.BaseBookshelfFragment
import io.legado.app.ui.main.chatentry.ChatPetViewModel
import io.legado.app.ui.main.bookshelf.style1.BookshelfFragment1
import io.legado.app.ui.main.explore.ExploreFragment
import io.legado.app.ui.main.home.HomeFragment
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
    private val chatPetViewModel by viewModels<ChatPetViewModel>()
    private val idHome = MainPageIds.HOME
    private val idBookshelf = MainPageIds.BOOKSHELF
    private val idBookshelf1 = 11
    private val idExplore = MainPageIds.EXPLORE
    private val idRss = MainPageIds.RSS
    private val idMy = MainPageIds.MY
    private var exitTime: Long = 0
    private var bookshelfReselected: Long = 0
    private var exploreReselected: Long = 0
    private var pagePosition = 0
    private var mainPagerScrollState = ViewPager.SCROLL_STATE_IDLE
    private var shelfSwipeEligible = false
    private var shelfSwipeAction = 0
    private var shelfSwipeStartX = 0f
    private var shelfSwipeStartY = 0f
    private var shelfSwipeCancelled = false
    private var shelfSwipeMode = BookshelfSwipeMode.MAIN_PAGES
    private val fragmentMap = hashMapOf<Int, Fragment>()
    private var realPositions = MainPageIds.visible(
        AppConfig.showDiscovery,
        AppConfig.showRSS,
        AppConfig.showHome
    )
    private var bottomMenuCount = realPositions.size
    private val EXIT_INTERVAL = 2000L
    private val isBookshelfPage: Boolean
        get() = realPositions.getOrNull(pagePosition) == idBookshelf
    internal val hidesHomeListeningCapsule: Boolean
        get() = realPositions.getOrNull(pagePosition) == idHome &&
            (fragmentMap[idHome] as? HomeFragment)?.hasListeningWidget == true

    internal fun refreshHomeListeningCapsule() {
        ReadAloudMiniPlayer.refreshMainVisibility(this)
    }
    private val adapter by lazy {
        TabFragmentPageAdapter(supportFragmentManager)
    }
    private var onUpBooksBadgeView: BadgeView? = null
    private var bookshelfBadgeCount = 0
    private var defaultBottomNavigationIconTint: ColorStateList? = null
    private var defaultBottomNavigationIconSize = 0
    private var bottomNavigationIconTintCaptured = false

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        chatPetViewModel.restorePlacements(savedInstanceState?.getBundle("chatPetPlacements"))
        upBottomMenu()
        initView()
        upHomePage()
        openLastReadBookAfterStartup(savedInstanceState)
        onBackPressedDispatcher.addCallback(this) {
            if (realPositions.getOrNull(pagePosition) == idHome &&
                (fragmentMap[idHome] as? HomeFragment)?.back() == true
            ) return@addCallback
            if (isBookshelfPage &&
                (fragmentMap[idBookshelf1] as? BookshelfFragment1)?.back() == true
            ) return@addCallback
            if (pagePosition != 0) {
                binding.viewPagerMain.currentItem = 0
                return@addCallback
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
        binding.aiChatPet.setHostActive(binding.aiChatPet.visibility == View.VISIBLE)
    }

    override fun onPause() {
        shelfSwipeAction = 0
        shelfSwipeEligible = false
        shelfSwipeCancelled = true
        binding.aiChatPet.setHostActive(false)
        super.onPause()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val shelfHandled = handleBookshelfSwipe(ev)
        return if (shelfHandled) true else super.dispatchTouchEvent(ev)
    }

    private fun handleBookshelfSwipe(event: MotionEvent): Boolean {
        val shelf = fragmentMap[idBookshelf1] as? BookshelfFragment1
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                shelf?.completePendingGroupSwipe()
                shelfSwipeAction = 0
                shelfSwipeCancelled = false
                shelfSwipeMode = BookshelfGestureConfig.mode
                shelfSwipeStartX = event.rawX
                shelfSwipeStartY = event.rawY
                val bounds = Rect()
                val content = binding.root.findViewById<View>(R.id.bookshelf_content_panel)
                shelfSwipeEligible = isBookshelfPage && shelfSwipeMode != BookshelfSwipeMode.MAIN_PAGES &&
                    content?.getGlobalVisibleRect(bounds) == true && bounds.contains(event.rawX.toInt(), event.rawY.toInt()) &&
                    !isTouchInsideBookshelfFloatingDock(event) &&
                    !isTouchInsideView(event, binding.floatingBottomNavigation) &&
                    !isTouchInsideChatEntry(event)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (shelfSwipeAction == -1 || shelfSwipeAction == 1) {
                    shelf?.finishGroupSwipe(commit = false)
                    shelfSwipeAction = 2
                }
                if (shelfSwipeEligible && shelfSwipeMode == BookshelfSwipeMode.DISABLED) {
                    shelfSwipeAction = 2
                    cancelShelfTouchTarget(event)
                }
                shelfSwipeEligible = false
                shelfSwipeCancelled = true
            }
            MotionEvent.ACTION_MOVE -> if (shelfSwipeAction == -1 || shelfSwipeAction == 1) {
                if (!shelfSwipeCancelled) shelf?.updateGroupSwipe(event.rawX - shelfSwipeStartX)
            } else if (shelfSwipeEligible && shelfSwipeAction == 0) {
                if (shelfSwipeMode == BookshelfSwipeMode.GROUPS_FIRST &&
                    event.eventTime - event.downTime >= ViewConfiguration.getLongPressTimeout()) {
                    shelfSwipeEligible = false
                    return false
                }
                val dx = event.rawX - shelfSwipeStartX
                val dy = event.rawY - shelfSwipeStartY
                val slop = ViewConfiguration.get(this).scaledTouchSlop
                if (abs(dy) > slop && abs(dy) >= abs(dx)) {
                    shelfSwipeEligible = false
                } else if (abs(dx) > slop && abs(dx) > abs(dy)) {
                    val direction = if (dx < 0) 1 else -1
                    shelfSwipeAction = resolveBookshelfSwipe(shelfSwipeMode, direction, shelf?.canSwipeGroup(direction) == true)
                    shelfSwipeEligible = false
                    if ((shelfSwipeAction == -1 || shelfSwipeAction == 1) &&
                        shelf?.beginGroupSwipe(shelfSwipeAction) != true
                    ) {
                        shelfSwipeAction = 0
                    }
                    if (shelfSwipeAction != 0) {
                        // End the child's press and the outer pager's drag before claiming the gesture.
                        cancelShelfTouchTarget(event)
                        if (shelfSwipeAction == -1 || shelfSwipeAction == 1) {
                            shelf?.updateGroupSwipe(dx)
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val action = shelfSwipeAction
                if (action == -1 || action == 1) {
                    val dx = event.rawX - shelfSwipeStartX
                    val dy = event.rawY - shelfSwipeStartY
                    shelf?.finishGroupSwipe(commit = event.actionMasked == MotionEvent.ACTION_UP &&
                        !shelfSwipeCancelled && -dx * action >= 48.dpToPx() && abs(dx) > abs(dy) * 1.8f)
                }
                shelfSwipeAction = 0
                shelfSwipeEligible = false
                return action != 0
            }
        }
        return shelfSwipeAction != 0
    }

    private fun isTouchInsideView(event: MotionEvent, view: View): Boolean {
        val bounds = Rect()
        return view.isShown && view.getGlobalVisibleRect(bounds) &&
            bounds.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    private fun isTouchInsideChatEntry(event: MotionEvent): Boolean =
        isTouchInsideView(event, binding.fabAiChat) || binding.aiChatPet.isTouchOnPet(event.rawX, event.rawY)

    private fun cancelShelfTouchTarget(event: MotionEvent) {
        MotionEvent.obtain(event).also { cancel ->
            cancel.action = MotionEvent.ACTION_CANCEL
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
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

    override fun onNavigationItemSelected(item: MenuItem): Boolean = binding.run {
        when (item.itemId) {
            R.id.menu_home ->
                viewPagerMain.setCurrentItem(
                    MainPageIds.defaultPosition("home", realPositions), false
                )

            R.id.menu_bookshelf ->
                viewPagerMain.setCurrentItem(realPositions.indexOf(idBookshelf), false)

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
            R.id.menu_home -> idHome
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
            idHome -> (fragmentMap[idHome] as? HomeFragment)?.gotoTop()
            idBookshelf -> {
                if (System.currentTimeMillis() - bookshelfReselected > 300) {
                    bookshelfReselected = System.currentTimeMillis()
                } else {
                    (fragmentMap[idBookshelf1] as? BaseBookshelfFragment)?.gotoTop()
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
            if (isBookshelfPage) {
                startBookshelfGenericAiChat()
            } else {
                startActivity<AiChatActivity>()
            }
        }
        aiChatPet.setOnClickListener {
            aiChatPet.setHostActive(false)
            aiChatPet.visibility = View.GONE
            if (isBookshelfPage) startBookshelfGenericAiChat() else startActivity<AiChatActivity>()
        }
        aiChatPet.onAssetLoadFailed = { toastOnUi(R.string.ai_chat_entry_load_failed) }
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
        val style = AiConfig.chatEntryStyle
        fabAiChat.visibility = if (style == AiChatEntryStyle.BUTTON) View.VISIBLE else View.GONE
        fabAiChat.updateAccentColor(accentColor)
        val petEngine = style.petAssetPath?.let { chatPetViewModel.engineFor(style.id) }
        if (petEngine != null) {
            aiChatPet.bindEngine(petEngine)
            aiChatPet.visibility = View.VISIBLE
        } else {
            aiChatPet.setHostActive(false)
            aiChatPet.visibility = View.GONE
        }
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
        outState.putBundle("chatPetPlacements", chatPetViewModel.savePlacements())
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
        (fragmentMap[idBookshelf1] as? BaseBookshelfFragment)?.run {
            upSort()
        }
        super.recreate()
    }

    override fun observeLiveBus() {
        viewModel.onUpBooksLiveData.observe(this) {
            bookshelfBadgeCount = it
            if (onUpBooksBadgeView == null) {
                onUpBooksBadgeView = binding.bottomNavigationView.addBadgeView(1)
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
        val previousPageId = realPositions.getOrNull(pagePosition)
        val showDiscovery = AppConfig.showDiscovery
        val showRss = AppConfig.showRSS
        val showHome = AppConfig.showHome
        binding.bottomNavigationView.menu.let { menu ->
            menu.findItem(R.id.menu_home).isVisible = showHome
            menu.findItem(R.id.menu_discovery).isVisible = showDiscovery
            menu.findItem(R.id.menu_rss).isVisible = showRss
        }
        realPositions = MainPageIds.visible(showDiscovery, showRss, showHome)
        bottomMenuCount = realPositions.size
        val restoredPosition = MainPageIds.preservePosition(previousPageId, realPositions)
        pagePosition = restoredPosition
        updateFloatingBottomMenu()
        adapter.notifyDataSetChanged()
        binding.viewPagerMain.setCurrentItem(restoredPosition, false)
        syncPageSelection(restoredPosition)
    }

    private fun updateFloatingBottomMenu() = binding.run {
        if (!bottomNavigationIconTintCaptured) {
            defaultBottomNavigationIconTint = bottomNavigationView.itemIconTintList
            defaultBottomNavigationIconSize = bottomNavigationView.itemIconSize
            bottomNavigationIconTintCaptured = true
        }
        val themedIcons = NgThemeRuntimeAssets.navigationIcons(this@MainActivity)
        val themedHomeIcon = themedIcons?.home(this@MainActivity)
        val themedIconSizeDp = if (themedIcons == null) 24 else 40
        updateStandardBottomNavigationIcons(themedIcons)
        val items = (0 until bottomMenuCount).map { position ->
            when (realPositions[position]) {
                idHome -> NgFloatingTabItem(
                    iconRes = R.drawable.ic_bottom_home_e,
                    selectedIconRes = R.drawable.ic_bottom_home_s,
                    iconDrawable = themedHomeIcon,
                    tintIcon = themedHomeIcon == null,
                    iconSizeDp = themedIconSizeDp,
                    scaleIconToFit = themedIcons != null && themedHomeIcon == null,
                    contentDescription = getString(R.string.home_title),
                )
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
            menu.findItem(R.id.menu_home).icon = themedIcons?.home(this@MainActivity)
                ?: getDrawable(R.drawable.ic_bottom_home)?.apply {
                    setTintList(defaultBottomNavigationIconTint)
                }
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
        val position = MainPageIds.defaultPosition(AppConfig.defaultHomePage, realPositions)
        binding.viewPagerMain.setCurrentItem(position, false)
        syncPageSelection(position)
    }

    private fun syncPageSelection(position: Int) {
        pagePosition = position
        refreshHomeListeningCapsule()
        binding.bottomNavigationView.menu.findItem(getMenuItemId(realPositions[position])).isChecked = true
        binding.floatingBottomNavigation.select(position, notify = false)
        if (mainPagerScrollState == ViewPager.SCROLL_STATE_IDLE) {
            bindFloatingBottomBackdropToCurrentPage()
        }
    }

    private fun getFragmentId(position: Int): Int {
        val id = realPositions[position]
        if (id == idBookshelf) {
            return idBookshelf1
        }
        return id
    }

    private fun getMenuItemId(pageId: Int): Int = when (pageId) {
        idHome -> R.id.menu_home
        idBookshelf -> R.id.menu_bookshelf
        idExplore -> R.id.menu_discovery
        idRss -> R.id.menu_rss
        else -> R.id.menu_my_config
    }

    private inner class PageChangeCallback : ViewPager.SimpleOnPageChangeListener() {

        override fun onPageScrollStateChanged(state: Int) {
            mainPagerScrollState = state
            if (state == ViewPager.SCROLL_STATE_IDLE) {
                bindFloatingBottomBackdropToCurrentPage()
            } else {
                // Drawing the pager directly lets EdgeEffect stretch the backdrop RenderNode.
                // Its content-only parent keeps the pager on its own normal rendering layer.
                binding.floatingBottomNavigation.setLiquidBackdropSource(
                    binding.viewPagerMain.parent as View,
                )
            }
        }

        override fun onPageSelected(position: Int) {
            syncPageSelection(position)
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
            if (position !in realPositions.indices) return POSITION_NONE
            val fragmentId = getId(position)
            if ((fragmentId == idHome && any is HomeFragment)
                || (fragmentId == idBookshelf1 && any is BookshelfFragment1)
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
                idHome -> HomeFragment(position)
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
                        refreshHomeListeningCapsule()
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
