package io.legado.app.ui.main.bookshelf

import android.app.Dialog
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.BookshelfLayoutMode
import io.legado.app.help.config.BookshelfLayoutProfile
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgFlatActionRail
import io.legado.app.ui.design.components.compose.NgFlatActionRailItem
import io.legado.app.ui.design.components.compose.NgFlatActionRailVariant
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormGroup
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormSelectOption
import io.legado.app.ui.design.components.compose.NgFormSelectMenuVariant
import io.legado.app.ui.design.components.compose.NgFormSelectRow
import io.legado.app.ui.design.components.compose.NgFormSliderRow
import io.legado.app.ui.design.components.compose.NgFormStepperRow
import io.legado.app.ui.design.components.compose.NgFormSwitchSettingRow
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.theme.NgAppTheme

/** 书架布局 NG 抽屉。仅展示当前视图真正生效的配置。 */
class BookshelfLayoutDialog : BottomSheetDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        BottomSheetDialog(requireContext(), theme)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (view as ComposeView).setContent {
            NgAppTheme(updateSystemBars = false) {
                BookshelfLayoutSheet(
                    onConfirm = ::confirm,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.22f }
            decorView.setPadding(0, 0, 0, 0)
        }
        val sheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        sheet.setBackgroundColor(AndroidColor.TRANSPARENT)
        sheet.layoutParams = sheet.layoutParams.apply {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        BottomSheetBehavior.from(sheet).apply {
            skipCollapsed = true
            isFitToContents = true
            isDraggable = true
            isDraggableOnNestedScroll = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun show(manager: FragmentManager, tag: String?) {
        runCatching { super.show(manager, tag) }
            .onFailure { AppLog.put("显示书架布局抽屉失败 tag:$tag", it) }
    }

    private fun confirm(draft: BookshelfLayoutDraft) {
        val oldMode = AppConfig.activeBookshelfLayoutMode
        val oldProfile = AppConfig.getBookshelfLayoutProfile(oldMode)
        val oldShowWaitUpCount = AppConfig.showWaitUpCount
        val newProfile = draft.profiles[draft.selectedMode]
        val result = BookshelfLayoutResult(
            recreate = oldMode != draft.selectedMode ||
                oldProfile.columns != newProfile.columns ||
                oldProfile.innerColumns != newProfile.innerColumns ||
                oldProfile.showBookName != newProfile.showBookName ||
                oldProfile.coverRadius != newProfile.coverRadius ||
                oldProfile.spacing != newProfile.spacing,
            refresh = oldProfile.showUnread != newProfile.showUnread ||
                oldProfile.showLastUpdateTime != newProfile.showLastUpdateTime,
            waitCountChanged = oldShowWaitUpCount != draft.showWaitUpCount,
            sortChanged = oldProfile.sort != newProfile.sort,
        )
        draft.profiles.forEach(AppConfig::setBookshelfLayoutProfile)
        AppConfig.showWaitUpCount = draft.showWaitUpCount
        AppConfig.selectBookshelfLayoutMode(draft.selectedMode)
        callback?.onBookshelfLayoutConfirmed(result)
        dismissAllowingStateLoss()
    }

    private val callback: Callback?
        get() = (parentFragment as? Callback) ?: activity as? Callback

    interface Callback {
        fun onBookshelfLayoutConfirmed(result: BookshelfLayoutResult)
    }

    companion object {
        fun show(manager: FragmentManager) {
            BookshelfLayoutDialog().show(
                manager,
                BookshelfLayoutDialog::class.java.simpleName,
            )
        }
    }
}

data class BookshelfLayoutResult(
    val recreate: Boolean,
    val refresh: Boolean,
    val waitCountChanged: Boolean,
    val sortChanged: Boolean,
)

private data class BookshelfLayoutDraft(
    val selectedMode: BookshelfLayoutMode,
    val profiles: BookshelfLayoutProfiles,
    val showWaitUpCount: Boolean,
)

private data class BookshelfLayoutProfiles(
    val list: BookshelfLayoutProfile,
    val compact: BookshelfLayoutProfile,
    val grid: BookshelfLayoutProfile,
    val groupGrid: BookshelfLayoutProfile,
) {

    operator fun get(mode: BookshelfLayoutMode): BookshelfLayoutProfile {
        return when (mode) {
            BookshelfLayoutMode.LIST -> list
            BookshelfLayoutMode.COMPACT -> compact
            BookshelfLayoutMode.GRID -> grid
            BookshelfLayoutMode.GROUP_GRID -> groupGrid
        }
    }

    fun updated(
        mode: BookshelfLayoutMode,
        profile: BookshelfLayoutProfile,
    ): BookshelfLayoutProfiles {
        return when (mode) {
            BookshelfLayoutMode.LIST -> copy(list = profile)
            BookshelfLayoutMode.COMPACT -> copy(compact = profile)
            BookshelfLayoutMode.GRID -> copy(grid = profile)
            BookshelfLayoutMode.GROUP_GRID -> copy(groupGrid = profile)
        }
    }

    fun forEach(block: (BookshelfLayoutMode, BookshelfLayoutProfile) -> Unit) {
        BookshelfLayoutMode.entries.forEach { mode -> block(mode, this[mode]) }
    }

    companion object {
        fun fromConfig(): BookshelfLayoutProfiles {
            return BookshelfLayoutProfiles(
                list = AppConfig.getBookshelfLayoutProfile(BookshelfLayoutMode.LIST),
                compact = AppConfig.getBookshelfLayoutProfile(BookshelfLayoutMode.COMPACT),
                grid = AppConfig.getBookshelfLayoutProfile(BookshelfLayoutMode.GRID),
                groupGrid = AppConfig.getBookshelfLayoutProfile(BookshelfLayoutMode.GROUP_GRID),
            )
        }
    }
}

@Composable
private fun BookshelfLayoutSheet(
    onConfirm: (BookshelfLayoutDraft) -> Unit,
) {
    var selectedMode by remember { mutableStateOf(AppConfig.activeBookshelfLayoutMode) }
    var profiles by remember { mutableStateOf(BookshelfLayoutProfiles.fromConfig()) }
    var showWaitUpCount by remember { mutableStateOf(AppConfig.showWaitUpCount) }
    val profile = profiles[selectedMode]
    val maxDrawerHeight = (LocalConfiguration.current.screenHeightDp * 0.86f).dp
    val mainSelection = selectedMode.value
    val isGridBooks = selectedMode == BookshelfLayoutMode.GRID ||
        selectedMode == BookshelfLayoutMode.GROUP_GRID
    val arrow = painterResource(R.drawable.ic_ng_spinner_arrow_down)

    fun updateProfile(transform: (BookshelfLayoutProfile) -> BookshelfLayoutProfile) {
        profiles = profiles.updated(selectedMode, transform(profiles[selectedMode]))
    }

    NgBottomDrawerSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxDrawerHeight),
        contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            NgLongDrawerHeader(
                title = stringResource(R.string.bookshelf_layout),
                centerTitle = true,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    NgFlatActionRail(
                        items = listOf(
                            NgFlatActionRailItem(
                                R.drawable.ic_chapter_list,
                                stringResource(R.string.bookshelf_view_list),
                                emphasized = mainSelection == 0,
                            ),
                            NgFlatActionRailItem(
                                R.drawable.ic_format_line_spacing,
                                stringResource(R.string.bookshelf_view_compact),
                                emphasized = mainSelection == 1,
                            ),
                            NgFlatActionRailItem(
                                R.drawable.ic_grid_menu,
                                stringResource(R.string.bookshelf_view_grid),
                                emphasized = mainSelection == 2,
                            ),
                            NgFlatActionRailItem(
                                R.drawable.ic_folder_outline,
                                stringResource(R.string.bookshelf_view_group_grid),
                                emphasized = mainSelection == 3,
                            ),
                        ),
                        onItemClick = { index ->
                            selectedMode = BookshelfLayoutMode.entries[index]
                        },
                        variant = NgFlatActionRailVariant.MODE_PICKER,
                    )
                }
                item {
                    NgFormGroup(title = stringResource(R.string.bookshelf_layout_details)) {
                        if (isGridBooks) {
                            NgFormStepperRow(
                                title = stringResource(R.string.bookshelf_grid_columns),
                                value = profile.columns,
                                valueRange = if (
                                    selectedMode == BookshelfLayoutMode.GROUP_GRID
                                ) {
                                    2..4
                                } else {
                                    2..6
                                },
                                onValueChange = { value ->
                                    updateProfile { it.copy(columns = value) }
                                },
                            )
                            NgFormGroupDivider()
                            if (selectedMode == BookshelfLayoutMode.GROUP_GRID) {
                                NgFormStepperRow(
                                    title = stringResource(R.string.bookshelf_group_inner_columns),
                                    value = profile.innerColumns,
                                    valueRange = 2..6,
                                    onValueChange = { value ->
                                        updateProfile { it.copy(innerColumns = value) }
                                    },
                                )
                                NgFormGroupDivider()
                            }
                            NgFormSelectRow(
                                title = stringResource(R.string.bookshelf_book_name_position),
                                selectedValue = profile.showBookName.toString(),
                                options = listOf(
                                    NgFormSelectOption(
                                        stringResource(R.string.bookshelf_book_name_below),
                                        "0",
                                    ),
                                    NgFormSelectOption(
                                        stringResource(R.string.bookshelf_book_name_hidden),
                                        "1",
                                    ),
                                    NgFormSelectOption(
                                        stringResource(R.string.bookshelf_book_name_overlay),
                                        "2",
                                    ),
                                ),
                                onValueChange = { value ->
                                    updateProfile {
                                        it.copy(showBookName = value.toIntOrNull() ?: 0)
                                    }
                                },
                                arrowIcon = arrow,
                                menuVariant = NgFormSelectMenuVariant.END_ANCHORED_COMPACT,
                            )
                            NgFormGroupDivider()
                            NgFormStepperRow(
                                title = stringResource(R.string.bookshelf_cover_radius),
                                value = profile.coverRadius,
                                valueRange = BookshelfLayoutProfile.MIN_COVER_RADIUS..BookshelfLayoutProfile.MAX_COVER_RADIUS,
                                onValueChange = { value ->
                                    updateProfile { it.copy(coverRadius = value) }
                                },
                            )
                            NgFormGroupDivider()
                        }
                        NgFormSliderRow(
                            title = stringResource(R.string.bookshelf_book_spacing),
                            value = profile.spacing,
                            valueRange = 0..60,
                            onValueChange = { value ->
                                updateProfile { it.copy(spacing = value) }
                            },
                        )
                    }
                }
                if (selectedMode != BookshelfLayoutMode.GROUP_GRID) {
                    item {
                        NgFormGroup(title = stringResource(R.string.bookshelf_display_content)) {
                            val unreadTitle = if (selectedMode == BookshelfLayoutMode.GRID) {
                                R.string.show_unread_badge
                            } else {
                                R.string.show_unread_count
                            }
                            NgFormSwitchSettingRow(
                                title = stringResource(unreadTitle),
                                checked = profile.showUnread,
                                onCheckedChange = { checked ->
                                    updateProfile { it.copy(showUnread = checked) }
                                },
                            )
                            if (selectedMode == BookshelfLayoutMode.LIST) {
                                NgFormGroupDivider()
                                NgFormSwitchSettingRow(
                                    title = stringResource(R.string.show_last_update_time),
                                    checked = profile.showLastUpdateTime,
                                    onCheckedChange = { checked ->
                                        updateProfile { it.copy(showLastUpdateTime = checked) }
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    NgFormGroup(title = stringResource(R.string.bookshelf_global_settings)) {
                        NgFormSwitchSettingRow(
                            title = stringResource(R.string.show_wait_up_count),
                            checked = showWaitUpCount,
                            onCheckedChange = { checked ->
                                showWaitUpCount = checked
                            },
                        )
                    }
                }
                item {
                    NgFormGroup(title = stringResource(R.string.sort)) {
                        NgFormSelectRow(
                            title = stringResource(R.string.sort),
                            selectedValue = profile.sort.toString(),
                            options = listOf(
                                NgFormSelectOption(stringResource(R.string.bookshelf_px_0), "0"),
                                NgFormSelectOption(stringResource(R.string.bookshelf_px_1), "1"),
                                NgFormSelectOption(stringResource(R.string.bookshelf_px_2), "2"),
                                NgFormSelectOption(stringResource(R.string.bookshelf_px_3), "3"),
                                NgFormSelectOption(stringResource(R.string.bookshelf_px_4), "4"),
                                NgFormSelectOption(stringResource(R.string.bookshelf_px_5), "5"),
                            ),
                            onValueChange = { value ->
                                updateProfile { it.copy(sort = value.toIntOrNull() ?: 0) }
                            },
                            arrowIcon = arrow,
                            menuVariant = NgFormSelectMenuVariant.END_ANCHORED_COMPACT,
                        )
                    }
                }
            }
            NgFormActionButton(
                text = stringResource(R.string.complete),
                onClick = {
                    onConfirm(
                        BookshelfLayoutDraft(
                            selectedMode = selectedMode,
                            profiles = profiles,
                            showWaitUpCount = showWaitUpCount,
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                variant = NgButtonVariant.PRIMARY,
            )
        }
    }
}
