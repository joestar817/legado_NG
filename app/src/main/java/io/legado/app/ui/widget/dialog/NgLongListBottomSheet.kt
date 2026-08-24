package io.legado.app.ui.widget.dialog

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doOnTextChanged
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.legado.app.R
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.design.components.view.NgSearchBar
import io.legado.app.utils.dpToPx

class NgLongListBottomSheet(
    private val context: Context,
    searchHint: CharSequence,
    title: CharSequence? = null,
    private val showSearch: Boolean = true,
    private val showCloseButton: Boolean = false,
    private val heightRatio: Float = 0.88f,
    private val compact: Boolean = false,
    private val searchInitiallyVisible: Boolean = !compact,
    private val showCompactSearchAction: Boolean = compact
) {

    val dialog = BottomSheetDialog(context)
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val horizontalPadding = if (compact) 12.dpToPx() else 24.dpToPx()
        setPadding(
            horizontalPadding,
            if (compact) 10.dpToPx() else 14.dpToPx(),
            horizontalPadding,
            if (compact) 14.dpToPx() else 18.dpToPx()
        )
    }
    private val titleAction = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
        textSize = if (compact) 14f else 15f
        isVisible = false
        setPadding(10.dpToPx(), 0, 10.dpToPx(), 0)
    }
    val searchBar = NgSearchBar(context).apply {
        hint = searchHint
        isVisible = searchInitiallyVisible
    }
    val searchEdit get() = searchBar.editText
    private val compactSearchAction = ImageButton(context).apply {
        setImageResource(
            if (searchInitiallyVisible) R.drawable.ic_baseline_close else R.drawable.ic_search
        )
        background = null
        contentDescription = context.getString(R.string.search)
        setColorFilter(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
        setPadding(10.dpToPx(), 10.dpToPx(), 10.dpToPx(), 10.dpToPx())
        setOnClickListener { toggleCompactSearch() }
    }
    private val compactEndActions = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        orientation = LinearLayout.HORIZONTAL
    }
    private var compactTitleView: TextView? = null
    private var onQueryChanged: ((String) -> Unit)? = null
    private var scrollableContent: NestedScrollView? = null
    private var searchVisibilityHost: View? = null
    val contentFrame = FrameLayout(context)

    init {
        if (title != null) {
            root.addView(
                createTitleBar(title),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (if (compact) 44 else 54).dpToPx()
                ).apply {
                    bottomMargin = when {
                        compact -> 8.dpToPx()
                        showSearch -> 8.dpToPx()
                        else -> 12.dpToPx()
                    }
                }
            )
        }
        if (showSearch) {
            root.addView(
                searchBar,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    44.dpToPx()
                ).apply {
                    bottomMargin = 12.dpToPx()
                }
            )
        }
        root.addView(
            contentFrame,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0
            ).apply {
                weight = 1f
            }
        )
        dialog.setContentView(
            context.createNgBottomDrawerViewHost(
                contentView = root,
                fillMaxHeight = true,
            )
        )
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            sheet.setBackgroundColor(Color.TRANSPARENT)
            sheet.layoutParams = sheet.layoutParams.apply {
                height = (context.resources.displayMetrics.heightPixels * heightRatio).toInt()
            }
            BottomSheetBehavior.from(sheet).apply {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun createTitleBar(title: CharSequence): View {
        return FrameLayout(context).apply {
            if (showCloseButton) {
                addView(
                    ImageButton(context).apply {
                        setImageResource(R.drawable.ic_baseline_close)
                        background = null
                        contentDescription = context.getString(R.string.close)
                        setColorFilter(ContextCompat.getColor(context, R.color.ng_on_surface))
                        setOnClickListener { dismiss() }
                    },
                    FrameLayout.LayoutParams(
                        48.dpToPx(),
                        48.dpToPx(),
                        Gravity.START or Gravity.CENTER_VERTICAL
                    )
                )
            }
            addView(
                TextView(context).apply {
                    text = title
                    gravity = if (compact) {
                        Gravity.START or Gravity.CENTER_VERTICAL
                    } else {
                        Gravity.CENTER
                    }
                    setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface))
                    textSize = if (compact) 17f else 20f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    if (compact) {
                        compactTitleView = this
                        setPadding(
                            if (showCloseButton) 48.dpToPx() else 4.dpToPx(),
                            0,
                            if (showSearch) 120.dpToPx() else 72.dpToPx(),
                            0
                        )
                    }
                },
                FrameLayout.LayoutParams(
                    if (compact) {
                        ViewGroup.LayoutParams.MATCH_PARENT
                    } else {
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    },
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    if (compact) Gravity.START else Gravity.CENTER
                )
            )
            if (compact) {
                compactEndActions.removeAllViews()
                if (showSearch && showCompactSearchAction) {
                    compactEndActions.addView(
                        compactSearchAction,
                        LinearLayout.LayoutParams(40.dpToPx(), 40.dpToPx())
                    )
                }
                compactEndActions.addView(
                    titleAction,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                addView(
                    compactEndActions,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.END or Gravity.CENTER_VERTICAL
                    )
                )
            } else {
                addView(
                    titleAction,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.END or Gravity.CENTER_VERTICAL
                    )
                )
            }
        }
    }

    private fun toggleCompactSearch() {
        val show = !searchBar.isVisible
        setSearchVisible(show)
        compactSearchAction.setImageResource(
            if (show) R.drawable.ic_baseline_close else R.drawable.ic_search
        )
        compactSearchAction.contentDescription = context.getString(
            if (show) R.string.close else R.string.search
        )
        val inputMethodManager = context.getSystemService(InputMethodManager::class.java)
        if (show) {
            searchEdit.requestFocus()
            searchEdit.post { inputMethodManager?.showSoftInput(searchEdit, InputMethodManager.SHOW_IMPLICIT) }
        } else {
            searchEdit.setText("")
            searchEdit.clearFocus()
            inputMethodManager?.hideSoftInputFromWindow(searchEdit.windowToken, 0)
        }
    }

    private fun setSearchVisible(visible: Boolean) {
        searchBar.isVisible = visible
        searchVisibilityHost?.isVisible = visible
    }

    fun setScrollableContent(
        render: (container: LinearLayout, query: String, dialog: BottomSheetDialog) -> Unit
    ) {
        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val listScroll = NestedScrollView(context).apply {
            isFillViewport = false
            clipToPadding = false
            setPadding(0, 0, 0, 10.dpToPx())
            addView(listContainer)
        }
        setContent(listScroll) { query ->
            render(listContainer, query, dialog)
        }
    }

    fun setContent(
        content: View,
        onQueryChanged: (String) -> Unit
    ) {
        this.onQueryChanged = onQueryChanged
        scrollableContent = content as? NestedScrollView
        contentFrame.removeAllViews()
        contentFrame.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        searchEdit.doOnTextChanged { text, _, _, _ ->
            onQueryChanged(text?.toString().orEmpty())
        }
        onQueryChanged(searchEdit.text?.toString().orEmpty())
    }

    fun refreshContent() {
        onQueryChanged?.invoke(searchEdit.text?.toString().orEmpty())
    }

    fun scrollContentToTop() {
        scrollableContent?.post {
            scrollableContent?.scrollTo(0, 0)
        }
    }

    fun setFooter(footer: View) {
        root.addView(
            footer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12.dpToPx()
            }
        )
    }

    fun setTopContent(content: View) {
        val contentIndex = root.indexOfChild(contentFrame).coerceAtLeast(0)
        root.addView(
            content,
            contentIndex,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 10.dpToPx()
            }
        )
    }

    /**
     * 将紧凑抽屉的折叠搜索框放入 Reading NG 的透明筛选容器。
     * 搜索开关仍由标题栏控制，业务页面不再重复处理容器显隐。
     */
    fun useCompactFilterSearchPanel() {
        check(compact && showSearch) {
            "Filter search panel requires compact mode with search enabled"
        }
        check(searchVisibilityHost == null) { "Filter search panel is already installed" }

        val initiallyVisible = searchBar.isVisible
        (searchBar.parent as? ViewGroup)?.removeView(searchBar)
        searchBar.setBackgroundResource(R.drawable.ng_bg_tts_filter_search)

        val panel = FrameLayout(context).apply {
            setBackgroundResource(R.drawable.ng_bg_tts_filter_panel)
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 10.dpToPx())
            isVisible = initiallyVisible
            addView(
                searchBar,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    40.dpToPx()
                )
            )
        }
        searchVisibilityHost = panel
        setSearchVisible(initiallyVisible)
        setTopContent(panel)
    }

    fun addCompactTitleIcon(
        iconRes: Int,
        contentDescription: CharSequence,
        onClick: (ImageButton) -> Unit
    ): ImageButton {
        check(compact) { "Title icons require compact mode" }
        val button = ImageButton(context).apply {
            setImageResource(iconRes)
            background = null
            this.contentDescription = contentDescription
            setColorFilter(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
            setPadding(9.dpToPx(), 9.dpToPx(), 9.dpToPx(), 9.dpToPx())
            setOnClickListener { onClick(this) }
        }
        val searchIndex = compactEndActions.indexOfChild(compactSearchAction)
        compactEndActions.addView(
            button,
            searchIndex.takeIf { it >= 0 } ?: 0,
            LinearLayout.LayoutParams(40.dpToPx(), 40.dpToPx())
        )
        compactTitleView?.setPadding(
            if (showCloseButton) 48.dpToPx() else 4.dpToPx(),
            0,
            (if (showSearch && showCompactSearchAction) 160 else 120).dpToPx(),
            0
        )
        return button
    }

    fun setTitleAction(text: CharSequence, action: () -> Unit) {
        titleAction.text = text
        if (compact) titleAction.setTextColor(context.accentColor)
        titleAction.isVisible = true
        titleAction.setOnClickListener { action() }
    }

    fun show() {
        dialog.show()
    }

    fun dismiss() {
        dialog.dismiss()
    }
}
