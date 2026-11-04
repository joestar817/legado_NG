package io.legado.app.ui.book.info

import android.app.Dialog
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.help.book.isLocalTxt
import io.legado.app.help.book.menuCapabilities
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormExpandableRow
import io.legado.app.ui.design.components.compose.NgFormGroup
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormSwitchSettingRow
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.widget.dialog.VariableEditorContent
import io.legado.app.ui.widget.dialog.VariableEditorContentVariant
import io.legado.app.utils.longToastOnUi
import kotlinx.coroutines.CancellationException

/** 只承载设置展示；状态和副作用继续由详情页拥有。 */
internal class BookInfoSettingsDialog : BottomSheetDialogFragment() {

    private var variableDraft by mutableStateOf<BookInfoVariableDraft?>(null)
    private var expandedVariables by mutableStateOf(emptySet<BookInfoVariableKind>())
    private var saving by mutableStateOf(false)
    private var completed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        variableDraft = BookInfoVariableDraft.restore(savedInstanceState?.getStringArrayList(STATE_DRAFT))
        val expandedNames = savedInstanceState?.getStringArrayList(STATE_EXPANDED)
            ?: listOfNotNull(arguments?.getString(ARG_INITIAL_VARIABLE))
        expandedVariables = BookInfoVariableKind.entries.filter { it.name in expandedNames }.toSet()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        variableDraft?.let { outState.putStringArrayList(STATE_DRAFT, it.savedState()) }
        outState.putStringArrayList(STATE_EXPANDED, ArrayList(expandedVariables.map { it.name }))
    }

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
        val host = activity as? BookInfoActivity ?: return
        val bookUrl = requireArguments().getString(ARG_BOOK_URL) ?: return
        (view as ComposeView).setContent {
            val state = host.bookInfoSettingsState
            val sourceKey = host.bookInfoSettingsSourceKey
            LaunchedEffect(state.book?.bookUrl, sourceKey, state.sourceAvailable, expandedVariables.isNotEmpty()) {
                val currentBook = state.book ?: return@LaunchedEffect
                if (currentBook.bookUrl != bookUrl) {
                    dismissAllowingStateLoss()
                    return@LaunchedEffect
                }
                val currentDraft = variableDraft
                if (currentDraft != null && !currentDraft.matchesContext(bookUrl, sourceKey)) {
                    dismissAllowingStateLoss()
                    return@LaunchedEffect
                }
                if (completed || !state.sourceAvailable || sourceKey == null ||
                    expandedVariables.isEmpty() || currentDraft != null
                ) return@LaunchedEffect
                try {
                    val loaded = host.loadBookInfoVariables(bookUrl, sourceKey)
                    if (loaded == null) {
                        dismissAllowingStateLoss()
                    } else if (!completed) {
                        variableDraft = loaded
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    expandedVariables = emptySet()
                    host.longToastOnUi(error.localizedMessage?.takeIf { it.isNotBlank() } ?: getString(R.string.error))
                }
            }
            if (state.book?.bookUrl == bookUrl) {
                NgAppTheme(updateSystemBars = false) {
                    BookInfoSettingsSheet(
                        state = state,
                        variableDraft = variableDraft,
                        expandedVariables = expandedVariables,
                        onVariableExpandedChange = { kind, expanded ->
                            expandedVariables = if (expanded) expandedVariables + kind else expandedVariables - kind
                        },
                        onVariableChange = { kind, value ->
                            variableDraft = variableDraft?.edit(kind, value)
                        },
                        onAction = { host.onBookInfoSettingsAction(bookUrl, it) },
                        confirmEnabled = !saving && !completed,
                        onConfirm = { complete(host) },
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.22f }
            decorView.setPadding(0, 0, 0, 0)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val sheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet,
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

    private fun complete(host: BookInfoActivity) {
        if (saving || completed) return
        saving = true
        try {
            val current = variableDraft
            if (current != null && !host.saveBookInfoVariables(current) { variableDraft = it }) {
                completed = true
                dismissAllowingStateLoss()
                return
            }
            completed = true
            dismiss()
        } catch (error: Exception) {
            host.longToastOnUi(error.localizedMessage?.takeIf { it.isNotBlank() } ?: getString(R.string.error))
        } finally {
            saving = false
        }
    }

    companion object {
        const val TAG = "BookInfoSettingsDialog"
        const val ARG_BOOK_URL = "bookUrl"
        const val ARG_INITIAL_VARIABLE = "initialVariable"
        private const val STATE_DRAFT = "variableDraft"
        private const val STATE_EXPANDED = "expandedVariables"
    }
}

internal data class BookInfoSetting(
    val action: BookInfoMenuAction,
    @param:StringRes val titleRes: Int,
    val checked: Boolean? = null,
    @param:StringRes val valueRes: Int? = null,
) {
    val variableKind: BookInfoVariableKind?
        get() = when (action) {
            BookInfoMenuAction.SetBookVariable -> BookInfoVariableKind.Book
            BookInfoMenuAction.SetSourceVariable -> BookInfoVariableKind.Source
            else -> null
        }
}

internal data class BookInfoSettingsSection(
    @param:StringRes val titleRes: Int,
    val items: List<BookInfoSetting>,
)

internal fun bookInfoSettingsSections(state: BookInfoUiState): List<BookInfoSettingsSection> = buildList {
    val book = state.book
    val bookSettings = buildList {
        if (book != null && state.sourceAvailable && book.menuCapabilities.update) {
            add(BookInfoSetting(BookInfoMenuAction.CanUpdate, R.string.allow_update, book.canUpdate))
        }
        if (book?.isLocalTxt == true) {
            add(
                BookInfoSetting(
                    BookInfoMenuAction.SplitLongChapter,
                    R.string.split_long_chapter,
                    book.getSplitLongChapter(),
                ),
            )
        }
    }
    if (bookSettings.isNotEmpty()) {
        add(BookInfoSettingsSection(R.string.book_info_settings_book, bookSettings))
    }
    add(
        BookInfoSettingsSection(
            R.string.book_info_settings_general,
            buildList {
                add(
                    BookInfoSetting(
                        BookInfoMenuAction.DeleteAlert,
                        R.string.book_info_delete_confirmation,
                        state.deleteAlertEnabled,
                    ),
                )
                if (state.otherWorksVisible) {
                    add(
                        BookInfoSetting(
                            BookInfoMenuAction.AutoLoadOtherWorks,
                            R.string.book_info_auto_load_related,
                            state.autoLoadOtherWorks,
                        ),
                    )
                }
            },
        ),
    )
    if (state.sourceAvailable) {
        add(
            BookInfoSettingsSection(
                R.string.book_info_settings_variables,
                listOf(
                    BookInfoSetting(BookInfoMenuAction.SetBookVariable, R.string.set_book_variable),
                    BookInfoSetting(
                        BookInfoMenuAction.SetSourceVariable,
                        R.string.set_source_variable,
                        valueRes = R.string.book_info_settings_source_shared,
                    ),
                ),
            ),
        )
    }
}

@Composable
private fun BookInfoSettingsSheet(
    state: BookInfoUiState,
    variableDraft: BookInfoVariableDraft?,
    expandedVariables: Set<BookInfoVariableKind>,
    onVariableExpandedChange: (BookInfoVariableKind, Boolean) -> Unit,
    onVariableChange: (BookInfoVariableKind, String) -> Unit,
    onAction: (BookInfoMenuAction) -> Unit,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    val sections = bookInfoSettingsSections(state)
    val focusManager = LocalFocusManager.current
    NgBottomDrawerSurface(
        modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight),
        contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            NgLongDrawerHeader(
                title = stringResource(R.string.book_info_settings),
                centerTitle = true,
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(sections, key = { it.titleRes }) { section ->
                    NgFormGroup(title = stringResource(section.titleRes)) {
                        section.items.forEachIndexed { index, item ->
                            key(item.action) {
                                if (index > 0) NgFormGroupDivider()
                                val checked = item.checked
                                if (checked == null) {
                                    val kind = requireNotNull(item.variableKind)
                                    NgFormExpandableRow(
                                        title = stringResource(item.titleRes),
                                        value = item.valueRes?.let { stringResource(it) }.orEmpty(),
                                        expanded = kind in expandedVariables,
                                        onExpandedChange = { onVariableExpandedChange(kind, it) },
                                        enabled = confirmEnabled,
                                    ) {
                                        if (variableDraft == null) {
                                            Text(
                                                text = stringResource(R.string.loading),
                                                color = Color(NgTheme.colors.onSurfaceVariant),
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp,
                                            )
                                        } else {
                                            VariableEditorContent(
                                                variable = when (kind) {
                                                    BookInfoVariableKind.Book -> variableDraft.bookValue
                                                    BookInfoVariableKind.Source -> variableDraft.sourceValue
                                                },
                                                comment = when (kind) {
                                                    BookInfoVariableKind.Book -> variableDraft.bookComment
                                                    BookInfoVariableKind.Source -> variableDraft.sourceComment
                                                },
                                                onVariableChange = { onVariableChange(kind, it) },
                                                variant = VariableEditorContentVariant.DRAWER,
                                            )
                                        }
                                    }
                                } else {
                                    NgFormSwitchSettingRow(
                                        title = stringResource(item.titleRes),
                                        checked = checked,
                                        onCheckedChange = {
                                            if (it != checked) onAction(item.action)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            NgFormActionButton(
                text = stringResource(R.string.complete),
                onClick = {
                    focusManager.clearFocus()
                    onConfirm()
                },
                enabled = confirmEnabled,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                variant = NgButtonVariant.PRIMARY,
            )
        }
    }
}
