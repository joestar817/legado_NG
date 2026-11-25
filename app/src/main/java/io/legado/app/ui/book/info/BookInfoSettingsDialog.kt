package io.legado.app.ui.book.info

import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.components.compose.NgFormSwitchSettingRow
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.widget.dialog.VariableEditorContent
import io.legado.app.ui.widget.dialog.VariableEditorContentVariant
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.longToastOnUi
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/** 只承载设置展示；状态和副作用继续由详情页拥有。 */
internal class BookInfoSettingsDialog : BottomSheetDialogFragment() {

    private var selectedTab by mutableStateOf(BookInfoSettingsTab.General)
    private var layoutDraft by mutableStateOf<BookInfoLayout?>(null)
    private var carouselDraft by mutableStateOf<BookInfoCarouselPreferences?>(null)
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
        val defaultTab = if (expandedVariables.isEmpty()) BookInfoSettingsTab.General else BookInfoSettingsTab.Variables
        val restoredTab = savedInstanceState?.getString(STATE_TAB)
        selectedTab = BookInfoSettingsTab.entries.firstOrNull { it.name == restoredTab } ?: defaultTab
        layoutDraft = savedInstanceState?.getString(STATE_LAYOUT)?.let { BookInfoLayout.decode(it) }
        if (savedInstanceState?.containsKey(STATE_CAROUSEL_INTERVAL) == true) {
            carouselDraft = BookInfoCarouselPreferences(
                autoPlay = savedInstanceState.getBoolean(STATE_CAROUSEL_AUTO_PLAY),
                intervalSeconds = savedInstanceState.getInt(STATE_CAROUSEL_INTERVAL),
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TAB, selectedTab.name)
        layoutDraft?.let { outState.putString(STATE_LAYOUT, it.encode()) }
        carouselDraft?.let {
            outState.putBoolean(STATE_CAROUSEL_AUTO_PLAY, it.autoPlay)
            outState.putInt(STATE_CAROUSEL_INTERVAL, it.intervalSeconds)
        }
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
        val prefs = host.defaultSharedPreferences
        val savedLayout = BookInfoLayoutStore.read(prefs)
        val savedCarousel = BookInfoCarouselPreferences.read(prefs)
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
                val preview = layoutDraft
                SideEffect { host.previewBookInfoLayout(bookUrl, preview) }
                NgAppTheme(updateSystemBars = false) {
                    BookInfoSettingsSheet(
                        tabs = bookInfoSettingsTabs(state),
                        selectedTab = selectedTab,
                        onTabSelected = { tab ->
                            if (tab == BookInfoSettingsTab.Layout && selectedTab != tab) {
                                host.scrollToBookInfoCover(bookUrl)
                            }
                            selectedTab = tab
                        },
                        confirmEnabled = !saving && !completed,
                        onConfirm = { complete(host, bookUrl) },
                    ) { tab ->
                        when (tab) {
                            BookInfoSettingsTab.General, BookInfoSettingsTab.Variables -> {
                                val sections = bookInfoSettingsSections(state).filter { section ->
                                    (section.titleRes == R.string.book_info_settings_variables) ==
                                        (tab == BookInfoSettingsTab.Variables)
                                }
                                BookInfoSettingsFields(
                                    sections = sections,
                                    variableDraft = variableDraft,
                                    expandedVariables = expandedVariables,
                                    onVariableExpandedChange = { kind, expanded ->
                                        expandedVariables = if (expanded) {
                                            expandedVariables + kind
                                        } else {
                                            expandedVariables - kind
                                        }
                                    },
                                    onVariableChange = { kind, value ->
                                        variableDraft = variableDraft?.edit(kind, value)
                                    },
                                    onAction = { host.onBookInfoSettingsAction(bookUrl, it) },
                                    enabled = !saving && !completed,
                                )
                            }

                            BookInfoSettingsTab.Layout -> BookInfoLayoutSettings(
                                layout = layoutDraft ?: savedLayout,
                                onChange = { layoutDraft = it },
                            )

                            BookInfoSettingsTab.Characters -> BookInfoCarouselSettings(
                                preferences = carouselDraft ?: savedCarousel,
                                onChange = { carouselDraft = it },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        arguments?.getString(ARG_BOOK_URL)?.let { bookUrl ->
            (activity as? BookInfoActivity)?.previewBookInfoLayout(bookUrl, null)
        }
        super.onDestroyView()
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

    private fun complete(host: BookInfoActivity, bookUrl: String) {
        if (saving || completed || host.bookInfoSettingsState.book?.bookUrl != bookUrl) return
        saving = true
        try {
            val current = variableDraft
            if (current != null && !host.saveBookInfoVariables(current) { variableDraft = it }) {
                completed = true
                dismissAllowingStateLoss()
                return
            }
            val prefs = host.defaultSharedPreferences
            layoutDraft?.let {
                if (it != BookInfoLayoutStore.read(prefs)) BookInfoLayoutStore.save(prefs, it)
            }
            carouselDraft?.let {
                if (it != BookInfoCarouselPreferences.read(prefs)) it.save(prefs)
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
        private const val STATE_TAB = "tab"
        private const val STATE_LAYOUT = "layoutDraft"
        private const val STATE_CAROUSEL_INTERVAL = "carouselIntervalDraft"
        private const val STATE_CAROUSEL_AUTO_PLAY = "carouselAutoPlayDraft"
        private const val STATE_DRAFT = "variableDraft"
        private const val STATE_EXPANDED = "expandedVariables"
    }
}

internal enum class BookInfoSettingsTab(@param:StringRes val titleRes: Int) {
    General(R.string.book_info_settings_tab_general),
    Layout(R.string.book_info_layout),
    Characters(R.string.book_info_settings_tab_characters),
    Variables(R.string.book_info_settings_variables),
}

internal fun bookInfoSettingsTabs(state: BookInfoUiState) = BookInfoSettingsTab.entries.filter { tab ->
    when (tab) {
        BookInfoSettingsTab.General, BookInfoSettingsTab.Layout -> true
        BookInfoSettingsTab.Characters -> state.charactersVisible
        BookInfoSettingsTab.Variables -> state.sourceAvailable
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
    tabs: List<BookInfoSettingsTab>,
    selectedTab: BookInfoSettingsTab,
    onTabSelected: (BookInfoSettingsTab) -> Unit,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    content: @Composable (BookInfoSettingsTab) -> Unit,
) {
    val configuration = LocalConfiguration.current
    // Keep the cover visible above the drawer while previewing its size in portrait.
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val heightFraction = if (landscape) 0.85f else 0.66f
    val drawerHeight = (configuration.screenHeightDp * heightFraction).dp
    val focusManager = LocalFocusManager.current
    val tabState = rememberSaveableStateHolder()
    val currentTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
    val currentTab = tabs[currentTabIndex]
    NgBottomDrawerSurface(
        modifier = Modifier.fillMaxWidth().height(drawerHeight),
        contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            NgLongDrawerHeader(
                title = stringResource(R.string.book_info_settings),
                centerTitle = true,
            )
            TabRow(
                selectedTabIndex = currentTabIndex,
                containerColor = Color.Transparent,
                contentColor = Color(NgTheme.colors.primary),
            ) {
                tabs.forEach { tab ->
                    Tab(
                        selected = currentTab == tab,
                        onClick = {
                            focusManager.clearFocus()
                            onTabSelected(tab)
                        },
                        text = {
                            Text(
                                text = stringResource(tab.titleRes),
                                color = Color(if (currentTab == tab) NgTheme.colors.primary else NgTheme.colors.onSurface),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp)) {
                tabState.SaveableStateProvider(currentTab.name) {
                    content(currentTab)
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

@Composable
private fun BookInfoSettingsFields(
    sections: List<BookInfoSettingsSection>,
    variableDraft: BookInfoVariableDraft?,
    expandedVariables: Set<BookInfoVariableKind>,
    onVariableExpandedChange: (BookInfoVariableKind, Boolean) -> Unit,
    onVariableChange: (BookInfoVariableKind, String) -> Unit,
    onAction: (BookInfoMenuAction) -> Unit,
    enabled: Boolean,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(sections, key = { it.titleRes }) { section ->
            val fields: @Composable ColumnScope.() -> Unit = {
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
                                enabled = enabled,
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
                                onCheckedChange = { if (it != checked) onAction(item.action) },
                            )
                        }
                    }
                }
            }
            if (section.titleRes == R.string.book_info_settings_variables) {
                NgFormPanel(content = fields)
            } else {
                NgFormGroup(title = stringResource(section.titleRes), content = fields)
            }
        }
    }
}

@Composable
private fun BookInfoCarouselSettings(
    preferences: BookInfoCarouselPreferences,
    onChange: (BookInfoCarouselPreferences) -> Unit,
) {
    val range = BookInfoCarouselPreferences.INTERVAL_RANGE
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            NgFormPanel {
                NgFormSwitchSettingRow(
                    title = stringResource(R.string.book_info_character_auto_play),
                    checked = preferences.autoPlay,
                    onCheckedChange = { onChange(preferences.copy(autoPlay = it)) },
                )
                NgFormGroupDivider()
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                    val label = stringResource(R.string.book_info_character_interval)
                    val colors = NgTheme.colors
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            color = Color(if (preferences.autoPlay) colors.onSurface else colors.onSurfaceVariant),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(
                                R.string.book_info_character_interval_seconds,
                                preferences.intervalSeconds,
                            ),
                            color = Color(colors.onSurfaceVariant),
                            fontSize = 13.sp,
                        )
                    }
                    NgSlider(
                        value = preferences.intervalSeconds.toFloat(),
                        onValueChange = { onChange(preferences.copy(intervalSeconds = it.roundToInt())) },
                        valueRange = range.first.toFloat()..range.last.toFloat(),
                        steps = range.last - range.first - 1,
                        visualSteps = 0,
                        enabled = preferences.autoPlay,
                        modifier = Modifier.fillMaxWidth().height(40.dp).semantics { contentDescription = label },
                    )
                }
            }
        }
    }
}
