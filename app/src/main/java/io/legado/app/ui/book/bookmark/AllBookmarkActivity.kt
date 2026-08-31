package io.legado.app.ui.book.bookmark

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Bookmark
import io.legado.app.databinding.ActivityAllBookmarkBinding
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.SelectDirectoryContract
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 所有书签
 */
class AllBookmarkActivity : VMBaseActivity<ActivityAllBookmarkBinding, AllBookmarkViewModel>() {

    override val viewModel by viewModels<AllBookmarkViewModel>()
    override val binding by viewBinding(ActivityAllBookmarkBinding::inflate)
    private var bookmarks by mutableStateOf<List<Bookmark>>(emptyList())
    private val exportDir = registerForActivityResult(SelectDirectoryContract()) {
        it.uri?.let { uri ->
            when (it.requestCode) {
                1 -> viewModel.exportBookmark(uri)
                2 -> viewModel.exportBookmarkMd(uri)
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.root.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        binding.root.setContent {
            NgAppTheme {
                AllBookmarkScreen(
                    bookmarks = bookmarks,
                    query = viewModel.searchQuery,
                    onAction = ::handleScreenAction,
                )
            }
        }
        lifecycleScope.launch {
            appDb.bookmarkDao.flowAll().catch {
                AppLog.put("所有书签界面获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).collect {
                bookmarks = it
            }
        }
    }

    private fun handleScreenAction(action: AllBookmarkScreenAction) {
        when (action) {
            AllBookmarkScreenAction.Back -> finish()
            AllBookmarkScreenAction.ExportJson -> exportDir.launch(
                SelectDirectoryContract.Request(requestCode = 1)
            )
            AllBookmarkScreenAction.ExportMarkdown -> exportDir.launch(
                SelectDirectoryContract.Request(requestCode = 2)
            )
            is AllBookmarkScreenAction.QueryChanged -> {
                viewModel.updateSearchQuery(action.query)
            }
            is AllBookmarkScreenAction.Open -> openBookmark(
                bookmark = action.bookmark,
                position = action.position,
            )
            is AllBookmarkScreenAction.Edit -> showDialogFragment(
                BookmarkDialog(action.bookmark, action.position)
            )
        }
    }

    private fun openBookmark(bookmark: Bookmark, position: Int) {
        lifecycleScope.launch {
            val book = withContext(IO) {
                appDb.bookDao.getBook(bookmark.bookName, bookmark.bookAuthor)
            }
            if (book == null) {
                showDialogFragment(BookmarkDialog(bookmark, position))
            } else {
                startActivityForBook(book) {
                    putExtra("index", bookmark.chapterIndex)
                    putExtra("chapterPos", bookmark.chapterPos)
                }
            }
        }
    }

}
