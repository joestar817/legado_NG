package io.legado.app.ui.book.bookmark

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.data.appDb
import io.legado.app.data.entities.Bookmark
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.book.read.ReadFloatingAppearanceState
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookmarkDialog() : BaseComposeDialogFragment() {

    constructor(bookmark: Bookmark, editPos: Int = -1) : this() {
        arguments = Bundle().apply {
            putInt("editPos", editPos)
            putParcelable("bookmark", bookmark)
        }
    }

    private var bookmark: Bookmark? = null

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(marginDp = 24, dimAmount = 0.42f)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val arguments = arguments ?: run {
            dismiss()
            return
        }
        @Suppress("DEPRECATION")
        val currentBookmark = arguments.getParcelable<Bookmark>("bookmark") ?: run {
            dismiss()
            return
        }
        bookmark = currentBookmark
        val isEditing = arguments.getInt("editPos", -1) >= 0
        val useReadPreset = activity is ReadBookActivity
        if (useReadPreset) {
            ReadFloatingAppearanceState.refreshFromConfig()
        }
        val themeSnapshot = if (useReadPreset) {
            ReadDrawerStyle.themeSnapshot(requireContext())
        } else {
            NgThemeResolver.resolve(requireContext())
        }

        (view as ComposeView).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NgAppTheme(
                    snapshot = themeSnapshot,
                    updateSystemBars = false,
                ) {
                    BookmarkDialogContent(
                        chapterName = currentBookmark.chapterName,
                        initialBookmarkText = currentBookmark.bookText,
                        initialNote = currentBookmark.content,
                        showDelete = isEditing,
                        useReadPreset = useReadPreset,
                        onCancel = ::dismiss,
                        onConfirm = ::saveBookmark,
                        onDelete = ::deleteBookmark,
                    )
                }
            }
        }
    }

    private fun saveBookmark(bookText: String, content: String) {
        val currentBookmark = bookmark ?: return
        currentBookmark.bookText = bookText
        currentBookmark.content = content
        lifecycleScope.launch {
            withContext(IO) {
                appDb.bookmarkDao.insert(currentBookmark)
            }
            dismiss()
        }
    }

    private fun deleteBookmark() {
        val currentBookmark = bookmark ?: return
        lifecycleScope.launch {
            withContext(IO) {
                appDb.bookmarkDao.delete(currentBookmark)
            }
            dismiss()
        }
    }
}
