package io.legado.app.ui.main.bookshelf

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.book.isNotShelf
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgCompactDrawerHeader
import io.legado.app.ui.design.components.compose.NgCompactDrawerSelectionItem
import io.legado.app.ui.design.components.compose.NgCompactDrawerSelectionPanel
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDrawerDragHandle
import io.legado.app.ui.design.components.compose.NgDrawerDragHandleVariant
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookshelfBookGroupSheet private constructor(
    private val host: Host,
    private val books: List<Book>,
) {

    private interface Host {
        val context: Context
        fun launch(block: suspend () -> Unit)
        fun isActive(): Boolean
    }

    constructor(fragment: Fragment, book: Book) : this(
        host = FragmentHost(fragment),
        books = listOf(book),
    )

    constructor(activity: FragmentActivity, books: List<Book>) : this(
        host = ActivityHost(activity),
        books = books.toList(),
    )

    private class FragmentHost(private val fragment: Fragment) : Host {
        override val context: Context get() = fragment.requireContext()
        override fun launch(block: suspend () -> Unit) {
            fragment.viewLifecycleOwner.lifecycleScope.launch { block() }
        }

        override fun isActive(): Boolean = fragment.isAdded
    }

    private class ActivityHost(private val activity: FragmentActivity) : Host {
        override val context: Context get() = activity
        override fun launch(block: suspend () -> Unit) {
            activity.lifecycleScope.launch { block() }
        }

        override fun isActive(): Boolean = !activity.isFinishing && !activity.isDestroyed
    }

    private data class GroupItem(
        val group: BookGroup,
        val bookCount: Int,
    )

    private val context: Context get() = host.context
    private val dialog by lazy { BottomSheetDialog(context) }

    fun show() {
        host.launch {
            val groups = withContext(IO) {
                val books = appDb.bookDao.all.filterNot { it.isNotShelf }
                appDb.bookGroupDao.all
                    .filter { it.groupId > 0 }
                    .map { group ->
                        GroupItem(
                            group = group,
                            bookCount = books.count { it.group and group.groupId > 0 },
                        )
                    }
            }
            if (host.isActive()) {
                showContent(groups)
            }
        }
    }

    private fun showContent(groups: List<GroupItem>) {
        val contentView = ComposeView(context).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    GroupSheetContent(
                        groups = groups,
                        onGroupClick = ::moveToGroup,
                    )
                }
            }
        }
        dialog.setContentView(contentView)
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            sheet.setBackgroundColor(AndroidColor.TRANSPARENT)
            BottomSheetBehavior.from(sheet).apply {
                skipCollapsed = true
                isDraggable = true
                isDraggableOnNestedScroll = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        dialog.show()
    }

    @Composable
    private fun GroupSheetContent(
        groups: List<GroupItem>,
        onGroupClick: (Long) -> Unit,
    ) {
        var createGroupDialogVisible by rememberSaveable { mutableStateOf(false) }
        val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.58f).dp
        val newGroupTitle = stringResource(R.string.bookshelf_new_group)
        val items = listOf(
            NgCompactDrawerSelectionItem(
                iconRes = R.drawable.ic_add,
                title = newGroupTitle,
            )
        ) + groups.map { item ->
            NgCompactDrawerSelectionItem(
                iconRes = R.drawable.ic_folder_outline,
                title = item.group.groupName,
                value = stringResource(
                    R.string.bookshelf_group_book_count,
                    item.bookCount,
                ),
            )
        }
        NgBottomDrawerSurface(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 12.dp),
            ) {
                NgDrawerDragHandle(variant = NgDrawerDragHandleVariant.COMPACT)
                NgCompactDrawerHeader(
                    title = stringResource(R.string.bookshelf_move_to_group),
                )
                Spacer(Modifier.height(2.dp))
                NgCompactDrawerSelectionPanel(
                    items = items,
                    onItemClick = { index ->
                        if (index == 0) {
                            createGroupDialogVisible = true
                        } else {
                            onGroupClick(groups[index - 1].group.groupId)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxListHeight),
                )
            }
        }
        if (createGroupDialogVisible) {
            BookshelfCreateGroupDialog(
                onDismiss = { createGroupDialogVisible = false },
                onConfirm = { groupName ->
                    createGroupDialogVisible = false
                    createGroupAndMove(groupName)
                },
            )
        }
    }

    private fun createGroupAndMove(groupName: String) {
        host.launch {
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
                        order = appDb.bookGroupDao.maxOrder + 1,
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
        host.launch {
            withContext(IO) {
                appDb.bookDao.update(*books.map { it.copy(group = groupId) }.toTypedArray())
                books.forEach { postEvent(EventBus.UP_BOOKSHELF, it.bookUrl) }
            }
        }
        dialog.dismiss()
    }
}

@Composable
private fun BookshelfCreateGroupDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var groupName by rememberSaveable { mutableStateOf("") }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    val groupNameEmpty = stringResource(R.string.group_name_empty)
    val groupNameHint = stringResource(R.string.bookshelf_new_group_hint)
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun confirm() {
        val normalizedName = groupName.trim()
        errorText = when {
            normalizedName.isBlank() -> groupNameEmpty
            normalizedName.length > 20 -> groupNameHint
            !normalizedName.matches(Regex("^[\\p{IsHan}A-Za-z0-9]+$")) -> groupNameHint
            else -> null
        }
        if (errorText == null) {
            onConfirm(normalizedName)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Dialog(onDismissRequest = onDismiss) {
        NgDialog(
            title = stringResource(R.string.bookshelf_new_group),
            variant = NgDialogVariant.STANDARD,
            actions = {
                NgFormActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    appearance = NgFormActionButtonAppearance.DIALOG,
                )
                NgFormActionButton(
                    text = stringResource(R.string.ok),
                    onClick = ::confirm,
                    variant = NgButtonVariant.PRIMARY,
                    appearance = NgFormActionButtonAppearance.DIALOG,
                )
            },
        ) {
            NgFormField(
                label = stringResource(R.string.group_name),
                value = groupName,
                onValueChange = {
                    groupName = it
                    errorText = null
                },
                modifier = Modifier.focusRequester(focusRequester),
                placeholder = stringResource(R.string.group_name),
                isError = errorText != null,
                supportingText = errorText,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                variant = NgFormFieldVariant.PLAIN_UNDERLINE,
            )
        }
    }
}
