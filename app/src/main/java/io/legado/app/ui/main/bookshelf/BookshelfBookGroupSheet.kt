package io.legado.app.ui.main.bookshelf

import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.book.isNotShelf
import io.legado.app.lib.dialogs.alert
import io.legado.app.utils.dpToPx
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.systemservices.inputMethodManager

class BookshelfBookGroupSheet(
    private val fragment: Fragment,
    private val book: Book
) {

    private data class GroupItem(
        val group: BookGroup,
        val bookCount: Int
    )

    private val context: Context get() = fragment.requireContext()
    private val dialog by lazy { BottomSheetDialog(context) }

    fun show() {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val groups = withContext(IO) {
                val books = appDb.bookDao.all.filterNot { it.isNotShelf }
                appDb.bookGroupDao.all
                    .filter { it.groupId > 0 }
                    .map { group ->
                        GroupItem(
                            group = group,
                            bookCount = books.count { it.group and group.groupId > 0 }
                        )
                    }
            }
            if (fragment.isAdded) {
                showContent(groups)
            }
        }
    }

    private fun showContent(groups: List<GroupItem>) {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dpToPx(), 14.dpToPx(), 12.dpToPx(), 12.dpToPx())
            background = ContextCompat.getDrawable(context, R.drawable.ng_bg_read_aloud_sheet)
        }
        root.addView(
            TextView(context).apply {
                text = context.getString(R.string.bookshelf_move_to_group)
                gravity = Gravity.CENTER
                textSize = 15f
                includeFontPadding = false
                setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 14.dpToPx()
            }
        )
        root.addView(createGroupPanel(groups))
        root.addView(createCancelButton(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            52.dpToPx()
        ).apply {
            topMargin = 12.dpToPx()
        })

        dialog.setContentView(root)
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            sheet.setBackgroundColor(Color.TRANSPARENT)
            BottomSheetBehavior.from(sheet).apply {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        dialog.show()
    }

    private fun createGroupPanel(groups: List<GroupItem>): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dpToPx(), 6.dpToPx(), 14.dpToPx(), 6.dpToPx())
            background = ContextCompat.getDrawable(context, R.drawable.ng_bg_bookshelf_group_panel)
            addView(createNewGroupRow(), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                56.dpToPx()
            ))
            groups.forEach { item ->
                addView(createGroupRow(item), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    56.dpToPx()
                ))
            }
        }
    }

    private fun createNewGroupRow(): View {
        return createRow(
            iconRes = R.drawable.ic_add,
            title = context.getString(R.string.bookshelf_new_group),
            countText = null
        ) {
            showCreateGroupDialog()
        }
    }

    private fun createGroupRow(item: GroupItem): View {
        return createRow(
            iconRes = R.drawable.ic_folder_outline,
            title = item.group.groupName,
            countText = context.getString(R.string.bookshelf_group_book_count, item.bookCount)
        ) {
            moveToGroup(item.group.groupId)
        }
    }

    private fun createCancelButton(): View {
        return TextView(context).apply {
            text = context.getString(R.string.cancel)
            textSize = 17f
            gravity = Gravity.CENTER
            includeFontPadding = false
            isClickable = true
            isFocusable = true
            background = ContextCompat.getDrawable(context, R.drawable.ng_bg_bookshelf_group_panel)
            setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
            setOnClickListener {
                dialog.dismiss()
            }
        }
    }

    private fun createRow(
        iconRes: Int,
        title: String,
        countText: String?,
        onClick: () -> Unit
    ): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = ContextCompat.getDrawable(context, R.drawable.ng_bg_bookshelf_action_item)
            setOnClickListener { onClick() }
            addView(
                ImageView(context).apply {
                    setImageResource(iconRes)
                    setColorFilter(ContextCompat.getColor(context, R.color.ng_on_surface))
                },
                LinearLayout.LayoutParams(24.dpToPx(), 24.dpToPx()).apply {
                    marginEnd = 16.dpToPx()
                }
            )
            addView(
                TextView(context).apply {
                    text = title
                    textSize = 18f
                    includeFontPadding = false
                    setSingleLine(true)
                    setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface))
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            if (countText != null) {
                addView(
                    TextView(context).apply {
                        text = countText
                        textSize = 16f
                        includeFontPadding = false
                        setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }
    }

    private fun showCreateGroupDialog() {
        val binding = DialogEditTextBinding.inflate(fragment.layoutInflater).apply {
            editView.hint = context.getString(R.string.bookshelf_new_group_hint)
            editView.maxLines = 1
        }
        val alertDialog = fragment.alert(titleResource = R.string.bookshelf_new_group) {
            customView { binding.root }
            positiveButton(R.string.ok)
            cancelButton()
        }
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val groupName = binding.editView.text?.toString()?.trim().orEmpty()
            when {
                groupName.isBlank() -> binding.editView.error = context.getString(R.string.group_name_empty)
                groupName.length > 20 -> binding.editView.error = context.getString(R.string.bookshelf_new_group_hint)
                !groupName.matches(Regex("^[\\p{IsHan}A-Za-z0-9]+$")) ->
                    binding.editView.error = context.getString(R.string.bookshelf_new_group_hint)
                else -> {
                    alertDialog.dismiss()
                    createGroupAndMove(groupName)
                }
            }
        }
        binding.editView.post {
            binding.editView.requestFocus()
            inputMethodManager.showSoftInput(binding.editView, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun createGroupAndMove(groupName: String) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(IO) {
                val existing = appDb.bookGroupDao.getByName(groupName)
                if (existing != null) {
                    existing.groupId
                } else if (!appDb.bookGroupDao.canAddGroup) {
                    null
                } else {
                    val group = BookGroup(
                        groupId = appDb.bookGroupDao.getUnusedId(),
                        groupName = groupName,
                        order = appDb.bookGroupDao.maxOrder + 1
                    )
                    appDb.bookGroupDao.insert(group)
                    group.groupId
                }
            }
            if (result == null) {
                context.toastOnUi(R.string.book_group_full)
            } else {
                moveToGroup(result)
            }
        }
    }

    private fun moveToGroup(groupId: Long) {
        fragment.viewLifecycleOwner.lifecycleScope.launch(IO) {
            appDb.bookDao.update(book.copy(group = groupId))
            postEvent(EventBus.UP_BOOKSHELF, book.bookUrl)
        }
        dialog.dismiss()
    }
}
