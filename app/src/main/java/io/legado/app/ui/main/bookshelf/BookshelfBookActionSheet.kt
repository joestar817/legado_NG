package io.legado.app.ui.main.bookshelf

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.widget.image.CoverImageView
import io.legado.app.utils.dpToPx
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookshelfBookActionSheet(
    private val fragment: Fragment,
    private val book: Book,
    private val callback: Callback
) {

    interface Callback {
        fun onDetail(book: Book)
        fun onCharacters(book: Book)
        fun onGroup(book: Book)
        fun onExport(book: Book)
        fun onListen(book: Book)
        fun onDownload(book: Book)
        fun onChangeSource(book: Book)
        fun onDelete(book: Book)
    }

    private val context: Context get() = fragment.requireContext()
    private val dialog by lazy { BottomSheetDialog(context) }
    private var tvCacheProgress: TextView? = null

    fun show() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = context.resources.displayMetrics.heightPixels / 2
            setPadding(16.dpToPx(), 20.dpToPx(), 16.dpToPx(), 0)
            background = ContextCompat.getDrawable(context, R.drawable.ng_bg_read_aloud_sheet)
        }
        root.addView(createHeader())
        root.addView(createActions(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 18.dpToPx()
        })
        root.addView(View(context), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        dialog.setContentView(root)
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            sheet.layoutParams = sheet.layoutParams.apply {
                height = context.resources.displayMetrics.heightPixels / 2
            }
            sheet.setBackgroundColor(Color.TRANSPARENT)
            BottomSheetBehavior.from(sheet).apply {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        dialog.show()
        loadCacheProgress()
    }

    private fun createHeader(): View {
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            addView(
                CoverImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    load(book, false, fragment, fragment.viewLifecycleOwner.lifecycle)
                },
                LinearLayout.LayoutParams(56.dpToPx(), 76.dpToPx())
            )
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        TextView(context).apply {
                            text = book.name
                            setSingleLine(true)
                            textSize = 18f
                            includeFontPadding = false
                            setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface))
                        },
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                    addView(
                        LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            addView(
                                TextView(context).apply {
                                    text = book.bookshelfAuthorText(context)
                                    setSingleLine(true)
                                    textSize = 14f
                                    includeFontPadding = false
                                    setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
                                },
                                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                            )
                            addView(
                                TextView(context).apply {
                                    text = cacheProgressText(null, book.totalChapterNum)
                                    setSingleLine(true)
                                    textSize = 14f
                                    includeFontPadding = false
                                    setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
                                    tvCacheProgress = this
                                },
                                LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    marginStart = 8.dpToPx()
                                    marginEnd = 12.dpToPx()
                                }
                            )
                        },
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = 7.dpToPx()
                        }
                    )
                },
                LinearLayout.LayoutParams(0, 76.dpToPx(), 1f).apply {
                    marginStart = 12.dpToPx()
                }
            )
        }
    }

    private fun createActions(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            val rows = listOf(
                listOf(
                    Action(R.string.action_detail, R.drawable.ic_bookshelf_action_detail) {
                        callback.onDetail(book)
                    },
                    Action(R.string.action_download, R.drawable.ic_bookshelf_action_download) {
                        callback.onDownload(book)
                    },
                    Action(R.string.change_origin, R.drawable.ic_exchange) {
                        callback.onChangeSource(book)
                    },
                    Action(R.string.book_info_listen, R.drawable.ic_bookshelf_action_headphones) {
                        callback.onListen(book)
                    }
                ),
                listOf(
                    Action(R.string.action_character, R.drawable.ic_bookshelf_action_user) {
                        callback.onCharacters(book)
                    },
                    Action(R.string.group, R.drawable.ic_bookshelf_action_folder) {
                        callback.onGroup(book)
                    },
                    Action(R.string.export, R.drawable.ic_bookshelf_action_export) {
                        callback.onExport(book)
                    },
                    Action(R.string.delete, R.drawable.ic_book_info_delete, danger = true) {
                        callback.onDelete(book)
                    }
                )
            )
            rows.forEach { row ->
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        row.forEach { action ->
                            addView(
                                createActionButton(action),
                                LinearLayout.LayoutParams(0, 118.dpToPx(), 1f)
                            )
                        }
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                    }
                )
            }
        }
    }

    private fun createActionButton(action: Action): View {
        val color = if (action.danger) {
            DangerColor
        } else {
            context.accentColor
        }
        val textColor = if (action.danger) {
            DangerTextColor
        } else {
            ContextCompat.getColor(context, R.color.ng_on_surface)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = ContextCompat.getDrawable(context, R.drawable.ng_bg_bookshelf_action_item)
            setOnClickListener {
                dialog.dismiss()
                action.onClick()
            }
            addView(
                FrameLayout(context).apply {
                    background = actionIconBackground(color, action.danger)
                    addView(
                        ImageView(context).apply {
                            setImageResource(action.iconRes)
                            setColorFilter(color)
                        },
                        FrameLayout.LayoutParams(24.dpToPx(), 24.dpToPx(), Gravity.CENTER)
                    )
                },
                LinearLayout.LayoutParams(54.dpToPx(), 54.dpToPx())
            )
            addView(
                TextView(context).apply {
                    text = context.getString(action.titleRes)
                    gravity = Gravity.CENTER
                    textSize = 13f
                    includeFontPadding = false
                    setTextColor(textColor)
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 10.dpToPx()
                }
            )
        }
    }

    private fun actionIconBackground(color: Int, danger: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (danger) {
                setColor(0xFFFFF0ED.toInt())
                setStroke(1.dpToPx(), 0xFFFFD2CA.toInt())
            } else {
                setColor(color.withAlpha(0x12))
                setStroke(1.dpToPx(), color.withAlpha(0x22))
            }
        }
    }

    private fun Int.withAlpha(alpha: Int): Int {
        return (this and 0x00FFFFFF) or (alpha shl 24)
    }

    private companion object {
        const val DangerColor = 0xFFE75B50.toInt()
        const val DangerTextColor = 0xFFC8473E.toInt()
    }

    private fun loadCacheProgress() {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val progress = withContext(IO) {
                val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
                val total = chapters.size.takeIf { it > 0 } ?: book.totalChapterNum
                if (book.isLocal) {
                    total to total
                } else {
                    val cacheFileNames = BookHelp.getChapterFiles(book)
                    val cached = chapters.count {
                        it.isVolume || cacheFileNames.contains(it.getFileName())
                    }
                    cached to total
                }
            }
            if (dialog.isShowing) {
                tvCacheProgress?.text = cacheProgressText(progress.first, progress.second)
            }
        }
    }

    private fun cacheProgressText(cached: Int?, total: Int): String {
        val progress = if (cached == null) {
            "--/${total.coerceAtLeast(0)}"
        } else {
            context.getString(R.string.book_cache_progress, cached, total.coerceAtLeast(cached))
        }
        return context.getString(R.string.book_cache_label) + progress
    }

    private data class Action(
        val titleRes: Int,
        val iconRes: Int,
        val danger: Boolean = false,
        val onClick: () -> Unit
    )
}
