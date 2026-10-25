package io.legado.app.ui.main.bookshelf

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.book.isUserBookGroup
import io.legado.app.help.book.joinedBookCount
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgCompactDrawerHeader
import io.legado.app.ui.design.components.compose.NgCompactDrawerSelectionItem
import io.legado.app.ui.design.components.compose.NgCompactDrawerSelectionIndicator
import io.legado.app.ui.design.components.compose.NgCompactDrawerSelectionPanel
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDrawerDragHandle
import io.legado.app.ui.design.components.compose.NgDrawerDragHandleVariant
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.components.compose.NgFloatingTabBar
import io.legado.app.ui.design.components.compose.NgFloatingTabBarVariant
import io.legado.app.ui.design.components.compose.NgFloatingTabSpec
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.postEvent
import io.legado.app.utils.showWithAppNavigationBarVisibility
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookshelfBookGroupSheet private constructor(
    private val host: Host,
    private val books: List<Book>,
    private val onGroupSelected: ((Long) -> Unit)? = null,
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

    constructor(
        activity: FragmentActivity,
        books: List<Book>,
        onGroupSelected: ((Long) -> Unit)? = null,
    ) : this(
        host = ActivityHost(activity),
        books = books.toList(),
        onGroupSelected = onGroupSelected,
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
        val joinedCount: Int,
    )

    private val context: Context get() = host.context
    private val dialog by lazy { BottomSheetDialog(context) }
    private var groups by mutableStateOf<List<GroupItem>>(emptyList())
    private var busy by mutableStateOf(false)

    fun show() {
        if (books.isEmpty()) return
        host.launch {
            try {
                groups = withContext(IO) { loadGroups() }
                if (host.isActive()) showContent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure(e)
            }
        }
    }

    private fun loadGroups(): List<GroupItem> {
        val memberships = books.map { book ->
            // 详情继续使用其当前对象；书架/批量重新读取最新归属。
            if (onGroupSelected != null) book.group
            else appDb.bookDao.getBook(book.bookUrl)?.group ?: book.group
        }
        return appDb.bookGroupDao.all.filter { isUserBookGroup(it.groupId) }.map { group ->
            GroupItem(group, joinedBookCount(memberships, group.groupId))
        }
    }

    private fun showContent() {
        val contentView = ComposeView(context).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    GroupSheetContent()
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
        dialog.showWithAppNavigationBarVisibility()
    }

    @Composable
    private fun GroupSheetContent() {
        var adding by rememberSaveable { mutableStateOf(true) }
        var pendingGroups by rememberSaveable { mutableStateOf(0L) }
        var createGroupDialogVisible by rememberSaveable { mutableStateOf(false) }
        val maxListHeight = minOf(
            365.dp,
            (LocalConfiguration.current.screenHeightDp - 220).coerceAtLeast(52).dp,
        )
        val newGroupTitle = stringResource(R.string.bookshelf_new_group)
        val items = listOf(
            NgCompactDrawerSelectionItem(
                iconRes = R.drawable.ic_add,
                title = newGroupTitle,
                key = 0L,
                enabled = !busy,
            )
        ) + groups.map { item ->
            val joined = item.joinedCount == books.size
            NgCompactDrawerSelectionItem(
                iconRes = R.drawable.ic_folder_outline,
                title = item.group.groupName,
                value = when {
                    joined -> stringResource(R.string.bookshelf_group_joined)
                    item.joinedCount > 0 -> stringResource(R.string.bookshelf_group_partly_joined)
                    else -> null
                },
                key = item.group.groupId,
                indicator = if (adding) NgCompactDrawerSelectionIndicator.CHECKBOX
                    else NgCompactDrawerSelectionIndicator.NAVIGATION,
                selected = adding && (joined || pendingGroups and item.group.groupId != 0L),
                enabled = !busy && (!adding || !joined),
                muted = joined,
            )
        }
        NgBottomDrawerSurface(
            modifier = Modifier.fillMaxWidth(),
            contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 17.dp),
            ) {
                NgDrawerDragHandle(variant = NgDrawerDragHandleVariant.COMPACT)
                NgCompactDrawerHeader(
                    title = stringResource(R.string.group),
                )
                Spacer(Modifier.height(5.dp))
                NgFloatingTabBar(
                    items = listOf(
                        NgFloatingTabSpec(text = stringResource(R.string.bookshelf_group_add)),
                        NgFloatingTabSpec(text = stringResource(R.string.bookshelf_group_move)),
                    ),
                    selectedIndex = if (adding) 0 else 1,
                    onTabSelected = { index ->
                        adding = index == 0
                        pendingGroups = 0L
                    },
                    variant = NgFloatingTabBarVariant.DRAWER_SELECTION,
                    enabled = !busy,
                )
                Spacer(Modifier.height(12.dp))
                NgCompactDrawerSelectionPanel(
                    items = items,
                    onItemClick = { index ->
                        if (index == 0) {
                            createGroupDialogVisible = true
                        } else {
                            val groupId = groups[index - 1].group.groupId
                            if (adding) pendingGroups = pendingGroups xor groupId
                            else applyGroups(groupId, append = false)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxListHeight),
                )
                Spacer(Modifier.height(15.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NgFormActionButton(
                        text = stringResource(R.string.cancel),
                        onClick = { dialog.dismiss() },
                        modifier = Modifier.weight(1f),
                        enabled = !busy,
                        appearance = NgFormActionButtonAppearance.SURFACE_CARD_BORDERLESS,
                        buttonHeight = 44.dp,
                        textSize = 15.sp,
                        textLineHeight = 18.sp,
                    )
                    if (adding) {
                        val count = java.lang.Long.bitCount(pendingGroups)
                        NgFormActionButton(
                            text = if (count > 0) {
                                stringResource(R.string.bookshelf_group_confirm_count, count)
                            } else stringResource(R.string.bookshelf_group_confirm),
                            onClick = { applyGroups(pendingGroups, append = true) },
                            modifier = Modifier.weight(1f),
                            enabled = !busy && pendingGroups != 0L,
                            variant = NgButtonVariant.PRIMARY,
                            buttonHeight = 44.dp,
                            textSize = 15.sp,
                            textLineHeight = 18.sp,
                        )
                    }
                }
            }
        }
        if (createGroupDialogVisible) {
            BookshelfCreateGroupDialog(
                onDismiss = { createGroupDialogVisible = false },
                onConfirm = { groupName ->
                    createGroupDialogVisible = false
                    createGroup(groupName) { groupId ->
                        if (adding) {
                            if (groups.none { it.group.groupId == groupId && it.joinedCount == books.size }) {
                                pendingGroups = pendingGroups or groupId
                            }
                        } else applyGroups(groupId, append = false)
                    }
                },
            )
        }
    }

    private fun createGroup(groupName: String, onCreated: (Long) -> Unit) {
        if (busy) return
        setInteractionBusy(true)
        host.launch {
            val groupId = try {
                val result = withContext(IO) {
                    appDb.withTransaction {
                        val dao = appDb.bookGroupDao
                        val existing = dao.getByName(groupName)
                        val id = when {
                            existing != null && isUserBookGroup(existing.groupId) -> existing.groupId
                            !dao.canAddGroup -> null
                            else -> {
                                val group = BookGroup(
                                    groupId = dao.getUnusedId(),
                                    groupName = groupName,
                                    order = dao.maxOrder + 1,
                                )
                                dao.insert(group)
                                group.groupId
                            }
                        }
                        id to loadGroups()
                    }
                }
                groups = result.second
                result.first
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure(e)
                return@launch
            } finally {
                setInteractionBusy(false)
            }
            if (groupId == null) {
                context.toastOnUi(R.string.book_group_full)
            } else if (host.isActive() && dialog.isShowing) {
                onCreated(groupId)
            }
        }
    }

    private fun applyGroups(groupIds: Long, append: Boolean) {
        if (busy || groupIds == 0L || !dialog.isShowing) return
        setInteractionBusy(true)
        // Detail pages own saving/adding their current book; shelf callers keep the batch path.
        onGroupSelected?.let { select ->
            select(if (append) books.single().group or groupIds else groupIds)
            dialog.dismiss()
            return
        }
        host.launch {
            try {
                withContext(IO) {
                    appDb.withTransaction {
                        books.forEach { book ->
                            if (append) appDb.bookDao.addGroups(book.bookUrl, groupIds)
                            else appDb.bookDao.setGroups(book.bookUrl, groupIds)
                        }
                    }
                }
                books.forEach { postEvent(EventBus.UP_BOOKSHELF, it.bookUrl) }
                dialog.dismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure(e)
            } finally {
                setInteractionBusy(false)
            }
        }
    }

    private fun setInteractionBusy(value: Boolean) {
        busy = value
        dialog.setCancelable(!value)
        dialog.setCanceledOnTouchOutside(!value)
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let {
            BottomSheetBehavior.from(it).isDraggable = !value
        }
    }

    private fun reportFailure(error: Exception) {
        AppLog.put("更新书籍分组失败", error)
        context.toastOnUi(R.string.bookshelf_group_update_failed)
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
