package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.PopupWindow
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.Bookmark
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.NgThemeRuntimeAssets
import io.legado.app.help.config.TextSelectionActionOrder
import io.legado.app.help.config.TextSelectionBuiltInAction
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.dpToPx
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val LEGADO_PROCESS_TEXT_ACTIVITY =
    "io.legado.app.receiver.SharedReceiverActivity"
private const val TEXT_HIGHLIGHT_NOTE_PREVIEW_MIN_WIDTH_DP = 176
private const val TEXT_HIGHLIGHT_NOTE_PREVIEW_MIN_HEIGHT_DP = 56
private const val TEXT_HIGHLIGHT_NOTE_PREVIEW_MAX_HEIGHT_FLOOR_DP = 180
private const val TEXT_HIGHLIGHT_NOTE_PREVIEW_MAX_HEIGHT_RATIO = 0.4f
private const val TEXT_HIGHLIGHT_NOTE_PREVIEW_TEXT_HORIZONTAL_SPACE_DP = 74
private const val TEXT_HIGHLIGHT_NOTE_PREVIEW_TEXT_VERTICAL_SPACE_DP = 28
private const val TEXT_HIGHLIGHT_NOTE_PREVIEW_TEXT_SIZE_SP = 15
private const val TEXT_HIGHLIGHT_NOTE_PREVIEW_LINE_HEIGHT_SP = 21

internal enum class TextHighlightPopupMode {
    TOOLBAR,
    NOTE_PREVIEW,
}

internal fun initialTextHighlightPopupMode(textHighlight: Bookmark?): TextHighlightPopupMode {
    return if (textHighlight?.content?.isNotBlank() == true) {
        TextHighlightPopupMode.NOTE_PREVIEW
    } else {
        TextHighlightPopupMode.TOOLBAR
    }
}

@SuppressLint("RestrictedApi")
class TextActionMenu(private val context: ComponentActivity, private val callBack: CallBack) :
    PopupWindow(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {

    private val menuItems: List<MenuItemImpl> = buildMenuItems()
    private val actionOrderState = mutableStateOf(TextSelectionActionOrder.load())
    private val disabledActionKeysState = mutableStateOf(TextSelectionActionOrder.disabledKeys())
    private val currentPageState = mutableIntStateOf(0)
    private val moreMenuVisibleState = mutableStateOf(false)
    private val textHighlightState = mutableStateOf<Bookmark?>(null)
    private val textHighlightPopupModeState = mutableStateOf(TextHighlightPopupMode.TOOLBAR)
    private val noteEditorVisibleState = mutableStateOf(false)
    private val noteDraftState = mutableStateOf("")
    private val themeSnapshotState = mutableStateOf(ReadDrawerStyle.themeSnapshot(context))
    private var moreMenuPopup: PopupWindow? = null
    private var popupParentView: View? = null
    private var toolbarX = 0
    private var toolbarY = 0
    private var toolbarWidth = 0
    private var toolbarHeight = 0
    private var menuSafeLeft = 0
    private var menuSafeRight = 0
    private var menuSafeTop = 0
    private var menuSafeBottom = 0
    private var toolbarDragX = 0f
    private var toolbarDragY = 0f
    private var popupAnchorTopY = 0
    private var popupAnchorBottomY = 0
    private var popupCenteredSafeLeft = 0
    private var popupCenteredSafeRight = 0
    private var fullSafeBottom = 0
    private var measuredToolbarHeight = 0
    private var layoutUpdatePending = false
    private val visibleFrame = Rect()
    private val windowLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        if (isShowing) {
            val oldBottom = menuSafeBottom
            refreshVisibleBounds()
            if (oldBottom != menuSafeBottom) scheduleToolbarLayout()
        }
    }

    private fun scheduleToolbarLayout() {
        if (layoutUpdatePending) return
        layoutUpdatePending = true
        contentView.post {
            layoutUpdatePending = false
            if (isShowing && textHighlightPopupModeState.value == TextHighlightPopupMode.TOOLBAR) {
                updateToolbarEditorHeight(useMeasuredHeight = true)
            }
        }
    }

    private fun refreshVisibleBounds() {
        val parent = popupParentView ?: return
        contentView.getWindowVisibleDisplayFrame(visibleFrame)
        val screen = IntArray(2)
        val window = IntArray(2)
        parent.getLocationOnScreen(screen)
        parent.getLocationInWindow(window)
        menuSafeBottom = if (visibleFrame.bottom > visibleFrame.top) {
            min(fullSafeBottom, visibleFrame.bottom - (screen[1] - window[1]) - 8.dpToPx())
                .coerceAtLeast(menuSafeTop + 1)
        } else fullSafeBottom
    }
    private val actions: List<TextSelectionAction> by lazy {
        menuItems.map { item ->
            TextSelectionAction(
                title = item.title.toString(),
                iconRes = menuIcon(item.itemId),
                iconBitmap = item.icon?.let { drawable ->
                    val iconSize = 24.dpToPx()
                    runCatching {
                        drawable.toBitmap(iconSize, iconSize).asImageBitmap()
                    }.getOrNull()
                },
                onClick = { onActionClick(item) },
            )
        }
    }
    private val orderedBuiltInActions: List<TextSelectionAction>
        get() {
            val builtIns = menuItems.zip(actions).take(TextSelectionBuiltInAction.entries.size)
                .associateBy { (item) -> item.itemId }
            return actionOrderState.value.filterNot { it.key in disabledActionKeysState.value }.mapNotNull { builtIn ->
                builtIns[builtIn.menuId]?.let { (item, action) ->
                    val highlight = textHighlightState.value
                    if (item.itemId == R.id.menu_bookmark && highlight != null) {
                        action.copy(
                            title = context.getString(R.string.delete_highlight),
                            iconRes = R.drawable.ic_book_info_delete,
                            iconBitmap = null,
                            onClick = { deleteTextHighlight(highlight) },
                        )
                    } else action
                }
            }
        }
    private val primaryActions: List<TextSelectionAction>
        get() = orderedBuiltInActions.take(TEXT_SELECTION_FIRST_PAGE_ACTION_COUNT)
    private val moreActions: List<TextSelectionAction>
        get() = orderedBuiltInActions.drop(TEXT_SELECTION_FIRST_PAGE_ACTION_COUNT) +
            actions.drop(TextSelectionBuiltInAction.entries.size)

    init {
        contentView = ComposeView(context).apply {
            attachViewTreeOwners()
            setBackgroundColor(Color.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NgAppTheme(
                    snapshot = themeSnapshotState.value,
                    updateSystemBars = false,
                ) {
                    val textHighlight = textHighlightState.value
                    if (
                        textHighlight != null &&
                        textHighlightPopupModeState.value == TextHighlightPopupMode.NOTE_PREVIEW
                    ) {
                        TextHighlightNotePreview(
                            note = textHighlight.content,
                            onSettings = ::showTextHighlightToolbar,
                        )
                    } else {
                        TextSelectionToolbar(
                            primaryActions = primaryActions,
                            currentPage = currentPageState.intValue,
                            onPageChange = {
                                currentPageState.intValue = it
                                setMoreMenuVisible(false)
                            },
                            moreMenuVisible = moreMenuVisibleState.value,
                            onMoreMenuVisibleChange = ::setMoreMenuVisible,
                            onLongClick = ::toggleSelectionReadMode,
                            textHighlight = textHighlight,
                            onHighlightStyleChange = ::updateTextHighlight,
                            noteEditorVisible = noteEditorVisibleState.value,
                            noteDraft = noteDraftState.value,
                            onNoteEditorVisibleChange = ::setNoteEditorVisible,
                            onNoteDraftChange = { noteDraftState.value = it },
                            onNoteDone = { setNoteEditorVisible(false) },
                            dragEnabled = !noteEditorVisibleState.value,
                            onDragStart = ::startToolbarDrag,
                            onDrag = ::dragToolbarBy,
                            onContentHeightChanged = { measuredHeight ->
                                if (measuredToolbarHeight != measuredHeight) {
                                    measuredToolbarHeight = measuredHeight
                                    scheduleToolbarLayout()
                                }
                            },
                        )
                    }
                }
            }
        }
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        elevation = 0f
        isTouchable = true
        isOutsideTouchable = false
        isFocusable = false
        inputMethodMode = PopupWindow.INPUT_METHOD_NEEDED
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        setOnDismissListener {
            highlightMenuGeneration++
            contentView.viewTreeObserver.removeOnGlobalLayoutListener(windowLayoutListener)
            popupParentView?.viewTreeObserver?.removeOnGlobalLayoutListener(windowLayoutListener)
            commitTextHighlightNote()
            dismissMoreMenu()
            currentPageState.intValue = 0
            popupParentView = null
            textHighlightState.value = null
            textHighlightPopupModeState.value = TextHighlightPopupMode.TOOLBAR
            noteEditorVisibleState.value = false
            noteDraftState.value = ""
            isFocusable = false
            callBack.onTextHighlightMenuDismissed()
        }
    }

    fun show(
        view: View,
        windowHeight: Int,
        startTopY: Int,
        startBottomY: Int,
        endBottomY: Int,
    ) {
        showInternal(
            view = view,
            windowHeight = windowHeight,
            startTopY = startTopY,
            startBottomY = startBottomY,
            endBottomY = endBottomY,
            textHighlight = null,
            anchorX = null,
        )
    }

    fun showTextHighlight(
        view: View,
        windowHeight: Int,
        anchorX: Int,
        anchorTopY: Int,
        anchorBottomY: Int,
        textHighlight: Bookmark,
    ) {
        showInternal(
            view = view,
            windowHeight = windowHeight,
            startTopY = anchorTopY,
            startBottomY = anchorBottomY,
            endBottomY = anchorBottomY,
            textHighlight = textHighlight,
            anchorX = anchorX,
        )
    }

    private fun showInternal(
        view: View,
        windowHeight: Int,
        startTopY: Int,
        startBottomY: Int,
        endBottomY: Int,
        textHighlight: Bookmark?,
        anchorX: Int?,
    ) {
        highlightMenuGeneration++
        ReadFloatingAppearanceState.refreshFromConfig()
        actionOrderState.value = TextSelectionActionOrder.load()
        disabledActionKeysState.value = TextSelectionActionOrder.disabledKeys()
        themeSnapshotState.value = ReadDrawerStyle.themeSnapshot(context)
        currentPageState.intValue = 0
        dismissMoreMenu()
        popupParentView = view
        measuredToolbarHeight = 0
        textHighlightState.value = textHighlight
        textHighlightPopupModeState.value = initialTextHighlightPopupMode(textHighlight)
        noteEditorVisibleState.value = false
        noteDraftState.value = textHighlight?.content.orEmpty()
        isFocusable = false
        if (textHighlight == null) {
            callBack.onTextHighlightMenuDismissed()
        } else {
            callBack.onTextHighlightOpened(textHighlight)
        }

        val rootWidth = view.rootView.width.takeIf { it > 0 }
            ?: context.resources.displayMetrics.widthPixels
        val insets = ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
        val leftInset = insets?.left ?: 0
        val rightInset = insets?.right ?: 0
        val topInset = insets?.top ?: 0
        val bottomInset = insets?.bottom ?: 0

        val horizontalMargin = 16.dpToPx()
        val safeLeft = leftInset + horizontalMargin
        val safeRight = rootWidth - rightInset - horizontalMargin
        val safeWidth = (safeRight - safeLeft).coerceAtLeast(1)
        val verticalMargin = 8.dpToPx()
        val availableHeight = (
            windowHeight - topInset - bottomInset - verticalMargin * 2
        ).coerceAtLeast(1)
        val notePreviewMaxHeight = max(
            TEXT_HIGHLIGHT_NOTE_PREVIEW_MAX_HEIGHT_FLOOR_DP.dpToPx(),
            (availableHeight * TEXT_HIGHLIGHT_NOTE_PREVIEW_MAX_HEIGHT_RATIO).roundToInt(),
        ).coerceAtMost(availableHeight)
        popupCenteredSafeLeft = safeLeft
        popupCenteredSafeRight = safeRight
        popupAnchorTopY = startTopY
        popupAnchorBottomY = max(startBottomY, endBottomY)
        val notePreviewVisible =
            textHighlightPopupModeState.value == TextHighlightPopupMode.NOTE_PREVIEW
        val desiredWidth = if (notePreviewVisible) {
            textHighlightNotePreviewWidthPx(
                note = textHighlight?.content.orEmpty(),
                maxWidth = safeWidth,
            )
        } else {
            textSelectionToolbarWidthDp(primaryActions.size).dpToPx()
        }
        val popupWidth = desiredWidth.coerceAtMost(safeWidth)

        val desiredHeight = if (notePreviewVisible) {
            textHighlightNotePreviewHeightPx(
                note = textHighlight?.content.orEmpty(),
                popupWidth = popupWidth,
                maxHeight = notePreviewMaxHeight,
            )
        } else {
            textSelectionToolbarHeightDp(textHighlight != null).dpToPx()
        }
        val popupHeight = desiredHeight.coerceAtMost(availableHeight)

        width = popupWidth
        height = popupHeight

        val gap = 8.dpToPx()
        val minTop = topInset + verticalMargin
        val maxTop = (
            windowHeight - bottomInset - verticalMargin - popupHeight
        ).coerceAtLeast(minTop)
        val above = startTopY - popupHeight - gap
        val below = max(startBottomY, endBottomY) + gap
        val popupY = when {
            above >= minTop -> above
            below <= maxTop -> below
            else -> above.coerceIn(minTop, maxTop)
        }
        val popupX = if (notePreviewVisible && anchorX != null) {
            (anchorX - popupWidth / 2).coerceIn(
                safeLeft,
                (safeRight - popupWidth).coerceAtLeast(safeLeft),
            )
        } else {
            safeLeft + (safeWidth - popupWidth) / 2
        }

        toolbarX = popupX
        toolbarY = popupY
        toolbarWidth = popupWidth
        toolbarHeight = popupHeight
        menuSafeLeft = leftInset + TEXT_SELECTION_MORE_PANEL_SCREEN_MARGIN_DP.dpToPx()
        menuSafeRight = rootWidth - rightInset -
            TEXT_SELECTION_MORE_PANEL_SCREEN_MARGIN_DP.dpToPx()
        menuSafeTop = topInset + TEXT_SELECTION_MORE_PANEL_SCREEN_MARGIN_DP.dpToPx()
        menuSafeBottom = windowHeight - bottomInset -
            TEXT_SELECTION_MORE_PANEL_SCREEN_MARGIN_DP.dpToPx()
        fullSafeBottom = menuSafeBottom
        toolbarDragX = toolbarX.toFloat()
        toolbarDragY = toolbarY.toFloat()

        showAtLocation(
            view,
            Gravity.TOP or Gravity.START,
            popupX,
            popupY,
        )
        // PopupWindow 会创建独立 DecorView，必须在下一帧 Compose attach 前补齐 owners。
        var popupView: View? = contentView
        while (popupView != null) {
            popupView.attachViewTreeOwners()
            popupView = popupView.parent as? View
        }
        contentView.viewTreeObserver.addOnGlobalLayoutListener(windowLayoutListener)
        view.viewTreeObserver.removeOnGlobalLayoutListener(windowLayoutListener)
        view.viewTreeObserver.addOnGlobalLayoutListener(windowLayoutListener)
        scheduleToolbarLayout()
    }

    private fun View.attachViewTreeOwners() {
        setViewTreeLifecycleOwner(this@TextActionMenu.context)
        setViewTreeViewModelStoreOwner(this@TextActionMenu.context)
        setViewTreeSavedStateRegistryOwner(this@TextActionMenu.context)
    }

    private fun buildMenuItems(): List<MenuItemImpl> {
        val appMenu = MenuBuilder(context)
        val processTextMenu = MenuBuilder(context)
        SupportMenuInflater(context).inflate(R.menu.content_select_action, appMenu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            onInitializeMenu(processTextMenu)
        }
        return appMenu.visibleItems + processTextMenu.visibleItems
    }

    private var highlightMenuGeneration = 0L
    private var pendingHighlightGeneration: Long? = null

    private fun onActionClick(item: MenuItemImpl) {
        dismissMoreMenu()
        if (item.itemId == R.id.menu_bookmark && textHighlightState.value == null) {
            val mine = highlightMenuGeneration
            if (pendingHighlightGeneration == mine) return
            pendingHighlightGeneration = mine
            context.lifecycleScope.launch {
                try {
                    val textHighlight = callBack.onTextHighlightCreate()
                    if (mine != highlightMenuGeneration || !isShowing) return@launch
                    if (textHighlight == null) {
                        callBack.onMenuActionFinally()
                        return@launch
                    }
                    textHighlightState.value = textHighlight
                    noteDraftState.value = textHighlight.content
                    callBack.onTextHighlightOpened(textHighlight)
                    currentPageState.intValue = 0
                    updateToolbarEditorHeight()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppLog.put("保存划线失败", error)
                    if (mine == highlightMenuGeneration && isShowing) context.toastOnUi(R.string.create_bookmark_error)
                } finally {
                    if (pendingHighlightGeneration == mine) pendingHighlightGeneration = null
                }
            }
            return
        }
        commitTextHighlightNote()
        if (!callBack.onMenuItemSelected(item.itemId)) {
            onMenuItemSelected(item)
        }
        callBack.onMenuActionFinally()
    }

    private fun updateTextHighlight(style: Int, color: Int) {
        val current = textHighlightState.value ?: return
        if (current.highlightStyle == style && current.highlightColor == color) return
        val updated = current.copy(
            highlightStyle = style,
            highlightColor = color,
        )
        textHighlightState.value = updated
        callBack.onTextHighlightUpdate(updated)
    }

    private fun deleteTextHighlight(highlight: Bookmark) {
        textHighlightState.value = null
        noteEditorVisibleState.value = false
        noteDraftState.value = ""
        isFocusable = false
        callBack.onTextHighlightDelete(highlight)
        dismiss()
        callBack.onMenuActionFinally()
    }

    private fun setNoteEditorVisible(visible: Boolean) {
        if (textHighlightState.value == null || noteEditorVisibleState.value == visible) return
        if (visible) {
            dismissMoreMenu()
            isFocusable = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NEEDED
            noteEditorVisibleState.value = true
        } else {
            commitTextHighlightNote()
            noteEditorVisibleState.value = false
            isFocusable = false
        }
        updateToolbarEditorHeight()
    }

    private fun commitTextHighlightNote() {
        val current = textHighlightState.value ?: return
        val note = noteDraftState.value
        if (current.content == note) return
        val updated = current.copy(content = note)
        textHighlightState.value = updated
        callBack.onTextHighlightUpdate(updated)
    }

    private fun showTextHighlightToolbar() {
        if (
            !isShowing ||
            textHighlightState.value == null ||
            textHighlightPopupModeState.value != TextHighlightPopupMode.NOTE_PREVIEW
        ) {
            return
        }
        dismissMoreMenu()
        currentPageState.intValue = 0
        noteEditorVisibleState.value = false
        textHighlightPopupModeState.value = TextHighlightPopupMode.TOOLBAR

        val safeWidth = (popupCenteredSafeRight - popupCenteredSafeLeft).coerceAtLeast(1)
        val targetWidth = textSelectionToolbarWidthDp(primaryActions.size)
            .dpToPx()
            .coerceAtMost(safeWidth)
        val safeHeight = (menuSafeBottom - menuSafeTop).coerceAtLeast(1)
        val targetHeight = textSelectionToolbarHeightDp(showHighlightEditor = true)
            .dpToPx()
            .coerceAtMost(safeHeight)
        val targetX = popupCenteredSafeLeft + (safeWidth - targetWidth) / 2
        val targetY = anchoredPopupY(targetHeight)

        toolbarX = targetX
        toolbarY = targetY
        toolbarWidth = targetWidth
        toolbarHeight = targetHeight
        toolbarDragX = targetX.toFloat()
        toolbarDragY = targetY.toFloat()
        width = targetWidth
        height = targetHeight
        update(targetX, targetY, targetWidth, targetHeight)
    }

    private fun anchoredPopupY(popupHeight: Int): Int {
        val gap = 8.dpToPx()
        val minTop = menuSafeTop
        val maxTop = (menuSafeBottom - popupHeight).coerceAtLeast(minTop)
        val above = popupAnchorTopY - popupHeight - gap
        val below = popupAnchorBottomY + gap
        return when {
            above >= minTop -> above.coerceAtMost(maxTop)
            below <= maxTop -> below.coerceAtLeast(minTop)
            else -> above.coerceIn(minTop, maxTop)
        }
    }

    private fun textHighlightNotePreviewTextPaint(): TextPaint {
        return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = NgThemeRuntimeAssets.appTypeface(context)
            textSize = spToPx(TEXT_HIGHLIGHT_NOTE_PREVIEW_TEXT_SIZE_SP)
        }
    }

    private fun textHighlightNotePreviewWidthPx(note: String, maxWidth: Int): Int {
        val paint = textHighlightNotePreviewTextPaint()
        val desiredTextWidth = note
            .trim()
            .lineSequence()
            .maxOfOrNull { StaticLayout.getDesiredWidth(it, paint) }
            ?: 0f
        val desiredWidth = desiredTextWidth.roundToInt() +
            TEXT_HIGHLIGHT_NOTE_PREVIEW_TEXT_HORIZONTAL_SPACE_DP.dpToPx()
        val minWidth = min(
            TEXT_HIGHLIGHT_NOTE_PREVIEW_MIN_WIDTH_DP.dpToPx(),
            maxWidth,
        )
        return desiredWidth.coerceIn(
            minWidth,
            maxWidth.coerceAtLeast(minWidth),
        )
    }

    @Suppress("DEPRECATION")
    private fun textHighlightNotePreviewHeightPx(
        note: String,
        popupWidth: Int,
        maxHeight: Int,
    ): Int {
        val textWidth = (
            popupWidth - TEXT_HIGHLIGHT_NOTE_PREVIEW_TEXT_HORIZONTAL_SPACE_DP.dpToPx()
        ).coerceAtLeast(1)
        val layout = StaticLayout(
            note.trim(),
            textHighlightNotePreviewTextPaint(),
            textWidth,
            Layout.Alignment.ALIGN_NORMAL,
            1f,
            0f,
            false,
        )
        val lineHeight = spToPx(TEXT_HIGHLIGHT_NOTE_PREVIEW_LINE_HEIGHT_SP).roundToInt()
        val desiredHeight = layout.lineCount.coerceAtLeast(1) * lineHeight +
            TEXT_HIGHLIGHT_NOTE_PREVIEW_TEXT_VERTICAL_SPACE_DP.dpToPx()
        val minHeight = min(
            TEXT_HIGHLIGHT_NOTE_PREVIEW_MIN_HEIGHT_DP.dpToPx(),
            maxHeight,
        )
        return desiredHeight.coerceIn(
            minHeight,
            maxHeight.coerceAtLeast(minHeight),
        )
    }

    private fun spToPx(value: Int): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            value.toFloat(),
            context.resources.displayMetrics,
        )
    }

    private fun updateToolbarEditorHeight(useMeasuredHeight: Boolean = false) {
        if (!isShowing) return
        refreshVisibleBounds()
        if (!useMeasuredHeight) measuredToolbarHeight = 0
        val desiredHeight = measuredToolbarHeight.takeIf { it > 0 } ?: textSelectionToolbarHeightDp(
            showHighlightEditor = textHighlightState.value != null,
            showNoteEditor = noteEditorVisibleState.value,
        ).dpToPx()
        val safeHeight = (menuSafeBottom - menuSafeTop).coerceAtLeast(1)
        val gap = 8.dpToPx()
        val aboveSpace = (popupAnchorTopY - gap - menuSafeTop).coerceIn(0, safeHeight)
        val belowSpace = (menuSafeBottom - popupAnchorBottomY - gap).coerceIn(0, safeHeight)
        // 两侧都不足时选择较大的一侧，内容在受限窗口内滚动。
        // 选区占满可视区域时无法完全避让，退回可视范围并保留手动拖动。
        val availableSpace = max(aboveSpace, belowSpace)
            .takeIf { it >= min(desiredHeight, TEXT_SELECTION_TOOLBAR_HEIGHT_DP.dpToPx()) }
            ?: safeHeight
        val expandedHeight = desiredHeight.coerceAtMost(availableSpace)
        val expandedY = anchoredPopupY(expandedHeight)
        if (toolbarHeight == expandedHeight && toolbarY == expandedY) {
            update() // 同步备注输入的 focusable / IME 标记。
            return
        }
        toolbarY = expandedY
        toolbarHeight = expandedHeight
        toolbarDragX = toolbarX.toFloat()
        toolbarDragY = toolbarY.toFloat()
        height = expandedHeight
        update(toolbarX, expandedY, toolbarWidth, expandedHeight)
    }

    private fun startToolbarDrag() {
        dismissMoreMenu()
        toolbarDragX = toolbarX.toFloat()
        toolbarDragY = toolbarY.toFloat()
    }

    private fun dragToolbarBy(deltaX: Float, deltaY: Float) {
        if (!isShowing) return
        val maxX = (menuSafeRight - toolbarWidth).coerceAtLeast(menuSafeLeft)
        val maxY = (menuSafeBottom - toolbarHeight).coerceAtLeast(menuSafeTop)
        toolbarDragX = (toolbarDragX + deltaX).coerceIn(menuSafeLeft.toFloat(), maxX.toFloat())
        toolbarDragY = (toolbarDragY + deltaY).coerceIn(menuSafeTop.toFloat(), maxY.toFloat())
        val targetX = toolbarDragX.roundToInt()
        val targetY = toolbarDragY.roundToInt()
        if (targetX == toolbarX && targetY == toolbarY) return
        toolbarX = targetX
        toolbarY = targetY
        update(toolbarX, toolbarY, toolbarWidth, toolbarHeight)
    }

    private fun setMoreMenuVisible(visible: Boolean) {
        if (!visible) {
            dismissMoreMenu()
            return
        }
        if (noteEditorVisibleState.value) {
            setNoteEditorVisible(false)
        }
        val parentView = popupParentView ?: return
        if (moreActions.isEmpty() || !isShowing) return

        dismissMoreMenu()
        moreMenuVisibleState.value = true

        val gap = TEXT_SELECTION_MORE_PANEL_GAP_DP.dpToPx()
        val desiredHeight = textSelectionMoreMenuHeightDp(moreActions.size).dpToPx()
        val availableAbove = (toolbarY - gap - menuSafeTop).coerceAtLeast(0)
        val availableBelow = (
            menuSafeBottom - toolbarY - toolbarHeight - gap
        ).coerceAtLeast(0)
        val placeAbove = availableAbove >= desiredHeight || availableAbove >= availableBelow
        val availableHeight = if (placeAbove) availableAbove else availableBelow
        val panelHeight = desiredHeight.coerceAtMost(availableHeight).coerceAtLeast(1)
        val safeWidth = (menuSafeRight - menuSafeLeft).coerceAtLeast(1)
        val panelWidth = TEXT_SELECTION_MORE_PANEL_WIDTH_DP.dpToPx()
            .coerceAtMost(safeWidth)
        val maxX = (menuSafeRight - panelWidth).coerceAtLeast(menuSafeLeft)
        val panelX = (toolbarX + toolbarWidth - panelWidth)
            .coerceIn(menuSafeLeft, maxX)
        val panelY = if (placeAbove) {
            toolbarY - gap - panelHeight
        } else {
            toolbarY + toolbarHeight + gap
        }

        val menuView = ComposeView(context).apply {
            attachViewTreeOwners()
            setBackgroundColor(Color.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NgAppTheme(
                    snapshot = themeSnapshotState.value,
                    updateSystemBars = false,
                ) {
                    TextSelectionMoreMenu(
                        actions = moreActions,
                        onLongClick = ::toggleSelectionReadMode,
                    )
                }
            }
        }
        val popup = PopupWindow(menuView, panelWidth, panelHeight).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 0f
            isTouchable = true
            isOutsideTouchable = false
            isFocusable = false
        }
        moreMenuPopup = popup
        popup.setOnDismissListener {
            if (moreMenuPopup === popup) {
                moreMenuPopup = null
                moreMenuVisibleState.value = false
            }
        }
        popup.showAtLocation(
            parentView,
            Gravity.TOP or Gravity.START,
            panelX,
            panelY,
        )
        var popupView: View? = menuView
        while (popupView != null) {
            popupView.attachViewTreeOwners()
            popupView = popupView.parent as? View
        }
    }

    private fun dismissMoreMenu() {
        val popup = moreMenuPopup
        moreMenuPopup = null
        popup?.setOnDismissListener(null)
        popup?.dismiss()
        moreMenuVisibleState.value = false
    }

    private fun toggleSelectionReadMode() {
        if (AppConfig.contentSelectSpeakMod == 0) {
            AppConfig.contentSelectSpeakMod = 1
            context.toastOnUi("切换为从选择的地方开始一直朗读")
        } else {
            AppConfig.contentSelectSpeakMod = 0
            context.toastOnUi("切换为朗读选择内容")
        }
    }

    @DrawableRes
    private fun menuIcon(itemId: Int): Int = when (itemId) {
        R.id.menu_replace -> R.drawable.ic_cfg_replace
        R.id.menu_ai_purify -> R.drawable.ic_ai_purify
        R.id.menu_copy -> R.drawable.ic_copy
        R.id.menu_bookmark -> R.drawable.ic_text_highlight
        R.id.menu_aloud -> R.drawable.ic_read_aloud
        R.id.menu_dict -> R.drawable.ic_translate
        R.id.menu_search_content -> R.drawable.ic_search
        R.id.menu_browser -> R.drawable.ic_web_outline
        R.id.menu_share_str -> R.drawable.ic_share
        R.id.menu_edit_content -> R.drawable.ic_edit
        else -> R.drawable.ic_ai_capability_text
    }

    private fun onMenuItemSelected(item: MenuItemImpl) {
        when (item.itemId) {
            R.id.menu_copy -> context.sendToClip(callBack.selectedText)
            R.id.menu_share_str -> context.share(callBack.selectedText)
            R.id.menu_browser -> {
                kotlin.runCatching {
                    val intent = if (callBack.selectedText.isAbsUrl()) {
                        Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse(callBack.selectedText)
                        }
                    } else {
                        Intent(Intent.ACTION_WEB_SEARCH).apply {
                            putExtra(SearchManager.QUERY, callBack.selectedText)
                        }
                    }
                    context.startActivity(intent)
                }.onFailure {
                    it.printOnDebug()
                    context.toastOnUi(it.localizedMessage ?: "ERROR")
                }
            }

            else -> item.intent?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    kotlin.runCatching {
                        it.putExtra(Intent.EXTRA_PROCESS_TEXT, callBack.selectedText)
                        context.startActivity(it)
                    }.onFailure { error ->
                        AppLog.put("执行文本菜单操作出错\n$error", error, true)
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntent(): Intent {
        return Intent()
            .setAction(Intent.ACTION_PROCESS_TEXT)
            .setType("text/plain")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getSupportedActivities(): List<ResolveInfo> {
        return context.packageManager
            .queryIntentActivities(createProcessTextIntent(), 0)
            .filterNot { it.activityInfo.name == LEGADO_PROCESS_TEXT_ACTIVITY }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntentForResolveInfo(info: ResolveInfo): Intent {
        return createProcessTextIntent()
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
            .setClassName(info.activityInfo.packageName, info.activityInfo.name)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun onInitializeMenu(menu: Menu) {
        kotlin.runCatching {
            var menuItemOrder = 100
            for (resolveInfo in getSupportedActivities()) {
                menu.add(
                    Menu.NONE,
                    Menu.NONE,
                    menuItemOrder++,
                    resolveInfo.loadLabel(context.packageManager),
                ).apply {
                    intent = createProcessTextIntentForResolveInfo(resolveInfo)
                    icon = resolveInfo.loadIcon(context.packageManager)
                }
            }
        }.onFailure {
            context.toastOnUi("获取文字操作菜单出错:${it.localizedMessage}")
        }
    }

    interface CallBack {
        val selectedText: String

        fun onMenuItemSelected(itemId: Int): Boolean

        suspend fun onTextHighlightCreate(): Bookmark?

        fun onTextHighlightOpened(bookmark: Bookmark)

        fun onTextHighlightUpdate(bookmark: Bookmark)

        fun onTextHighlightDelete(bookmark: Bookmark)

        fun onTextHighlightMenuDismissed()

        fun onMenuActionFinally()
    }
}
