package io.legado.app.ui.widget.dialog

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
import io.legado.app.help.config.ThemeConfig
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.utils.dpToPx
import io.legado.app.utils.windowSize
import splitties.systemservices.windowManager

class NgLongListBottomSheet(
    private val context: Context,
    searchHint: CharSequence,
    title: CharSequence? = null,
    private val showSearch: Boolean = true,
    private val showCloseButton: Boolean = false,
    private val heightRatio: Float = 0.88f
) {

    val dialog = BottomSheetDialog(context)
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(24.dpToPx(), 14.dpToPx(), 24.dpToPx(), 18.dpToPx())
        background = createSheetBackground()
    }
    private val titleAction = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
        textSize = 15f
        isVisible = false
        setPadding(10.dpToPx(), 0, 10.dpToPx(), 0)
    }
    val searchEdit = EditText(context).apply {
        background = GradientDrawable().apply {
            cornerRadius = 28.dpToPx().toFloat()
            setColor(ContextCompat.getColor(context, R.color.ng_neutral_container))
        }
        hint = searchHint
        setSingleLine(true)
        setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface))
        setHintTextColor(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
        textSize = 16f
        compoundDrawablePadding = 10.dpToPx()
        setPadding(18.dpToPx(), 0, 18.dpToPx(), 0)
    }
    val contentFrame = FrameLayout(context)

    private fun createSheetBackground() = runCatching {
        ThemeConfig.getBgImage(context, context.windowManager.windowSize)
    }.getOrNull()?.let { background ->
        val overlay = ColorDrawable(Color.argb(96, 255, 255, 249))
        ReadDrawerStyle.wrapTopRounded(LayerDrawable(arrayOf(background, overlay)))
    } ?: GradientDrawable().apply {
        val radius = 28.dpToPx().toFloat()
        cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
        setColor(ContextCompat.getColor(context, R.color.ng_surface_soft))
    }

    init {
        if (title != null) {
            root.addView(
                createTitleBar(title),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    54.dpToPx()
                ).apply {
                    bottomMargin = if (showSearch) 8.dpToPx() else 12.dpToPx()
                }
            )
        }
        if (showSearch) {
            root.addView(
                searchEdit,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    52.dpToPx()
                ).apply {
                    bottomMargin = 16.dpToPx()
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
        dialog.setContentView(root)
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
                    gravity = Gravity.CENTER
                    setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface))
                    textSize = 20f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
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

    fun setTitleAction(text: CharSequence, action: () -> Unit) {
        titleAction.text = text
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
