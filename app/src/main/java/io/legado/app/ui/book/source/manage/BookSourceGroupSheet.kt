package io.legado.app.ui.book.source.manage

import android.graphics.Color as AndroidColor
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.legado.app.R
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgCompactDrawerHeader
import io.legado.app.ui.design.components.compose.NgCompactDrawerSelectionItem
import io.legado.app.ui.design.components.compose.NgCompactDrawerSelectionPanel
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgDrawerDragHandle
import io.legado.app.ui.design.components.compose.NgDrawerDragHandleVariant
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.cnCompare
import io.legado.app.utils.splitNotBlank
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 批量把书源加入一个分组的统一底部抽屉。
 *
 * 交互与书架“移动到分组”保持一致：点击一个分组后立即执行并关闭；书源自身
 * 仍保留多分组语义，因此这里只追加目标分组，不覆盖已有分组。
 */
internal class BookSourceAddGroupSheet(
    private val activity: FragmentActivity,
    private val sources: List<BookSourcePart>,
    private val onAddToGroup: (List<BookSourcePart>, String) -> Unit,
) {

    private data class GroupItem(
        val name: String,
        val sourceCount: Int,
    )

    private val dialog by lazy { BottomSheetDialog(activity) }

    fun show() {
        activity.lifecycleScope.launch {
            val groups = withContext(IO) {
                val groupCounts = linkedMapOf<String, Int>()
                appDb.bookSourceDao.allPart.forEach { source ->
                    source.bookSourceGroup
                        ?.splitNotBlank(AppPattern.splitGroupRegex)
                        .orEmpty()
                        .distinct()
                        .forEach { group ->
                            groupCounts[group] = groupCounts.getOrDefault(group, 0) + 1
                        }
                }
                groupCounts.entries
                    .sortedWith { left, right -> left.key.cnCompare(right.key) }
                    .map { GroupItem(name = it.key, sourceCount = it.value) }
            }
            if (!activity.isFinishing && !activity.isDestroyed) {
                showContent(groups)
            }
        }
    }

    private fun showContent(groups: List<GroupItem>) {
        val contentView = ComposeView(activity).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    AddGroupSheetContent(
                        groups = groups,
                        onGroupClick = ::addToGroup,
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
    private fun AddGroupSheetContent(
        groups: List<GroupItem>,
        onGroupClick: (String) -> Unit,
    ) {
        var createGroupDialogVisible by rememberSaveable { mutableStateOf(false) }
        val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.58f).dp
        val items = listOf(
            NgCompactDrawerSelectionItem(
                iconRes = R.drawable.ic_add,
                title = stringResource(R.string.bookshelf_new_group),
            )
        ) + groups.map { item ->
            NgCompactDrawerSelectionItem(
                iconRes = R.drawable.ic_folder_outline,
                title = item.name,
                value = stringResource(R.string.book_source_count, item.sourceCount),
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
                    .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 12.dp),
            ) {
                NgDrawerDragHandle(variant = NgDrawerDragHandleVariant.COMPACT)
                NgCompactDrawerHeader(title = stringResource(R.string.add_group))
                Spacer(Modifier.height(2.dp))
                NgCompactDrawerSelectionPanel(
                    items = items,
                    onItemClick = { index ->
                        if (index == 0) {
                            createGroupDialogVisible = true
                        } else {
                            onGroupClick(groups[index - 1].name)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxListHeight),
                )
            }
        }

        if (createGroupDialogVisible) {
            BookSourceCreateGroupDialog(
                onDismiss = { createGroupDialogVisible = false },
                onConfirm = { groupName ->
                    createGroupDialogVisible = false
                    onGroupClick(groupName)
                },
            )
        }
    }

    private fun addToGroup(group: String) {
        onAddToGroup(sources, group)
        dialog.dismiss()
    }
}

@Composable
private fun BookSourceCreateGroupDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var groupName by rememberSaveable { mutableStateOf("") }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    val groupNameEmpty = stringResource(R.string.group_name_empty)
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun confirm() {
        val normalizedName = groupName.trim()
        if (normalizedName.isBlank()) {
            errorText = groupNameEmpty
        } else {
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
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                variant = NgFormFieldVariant.PLAIN_UNDERLINE,
            )
        }
    }
}

@Composable
internal fun BookSourceClearGroupsDialog(
    sourceCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.clear_group),
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .widthIn(max = 520.dp),
            variant = NgDialogVariant.CLASSIC_CONFIRMATION,
            titleFontWeight = FontWeight.Normal,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                )
                NgDialogTextActionButton(
                    text = stringResource(R.string.clear_group),
                    onClick = onConfirm,
                    danger = true,
                )
            },
        ) {
            Text(
                text = stringResource(R.string.clear_selected_book_source_groups_confirm, sourceCount),
                modifier = Modifier.fillMaxWidth(),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 16.sp,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}
