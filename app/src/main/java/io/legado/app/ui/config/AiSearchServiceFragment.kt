package io.legado.app.ui.config

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.help.ai.AiDoubaoSearchMode
import io.legado.app.help.ai.AiExternalSearchDebugResult
import io.legado.app.help.ai.AiExternalSearchProvider
import io.legado.app.help.ai.AiExternalSearchService
import io.legado.app.help.ai.AiExternalSearchStore
import io.legado.app.help.ai.AiExternalSearchTool
import io.legado.app.help.ai.AiTavilySearchDepth
import io.legado.app.ui.design.components.NgButtonShapeVariant
import io.legado.app.ui.design.components.NgManagementTrailing
import io.legado.app.ui.design.components.NgStatusTagSpec
import io.legado.app.ui.design.components.NgStatusTagStyle
import io.legado.app.ui.design.components.NgStatusTagVariant
import io.legado.app.ui.design.components.compose.NgButton
import io.legado.app.ui.design.components.compose.NgFloatingSearchToolbar
import io.legado.app.ui.design.components.compose.NgFloatingTitleToolbar
import io.legado.app.ui.design.components.compose.NgFormControlGroup
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldGroup
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormMultilineField
import io.legado.app.ui.design.components.compose.NgFormMultilineFieldVariant
import io.legado.app.ui.design.components.compose.NgFormNumberSettingRow
import io.legado.app.ui.design.components.compose.NgFormNumberSettingRowVariant
import io.legado.app.ui.design.components.compose.NgFormSelectMenuVariant
import io.legado.app.ui.design.components.compose.NgFormSelectOption
import io.legado.app.ui.design.components.compose.NgFormSelectRow
import io.legado.app.ui.design.components.compose.NgFormSwitchGroup
import io.legado.app.ui.design.components.compose.NgFormSwitchRow
import io.legado.app.ui.design.components.compose.NgFormSwitchRowVariant
import io.legado.app.ui.design.components.compose.NgListState
import io.legado.app.ui.design.components.compose.NgListStateContent
import io.legado.app.ui.design.components.compose.NgManagementTrailingIcon
import io.legado.app.ui.design.components.compose.NgPaneDialog
import io.legado.app.ui.design.components.compose.NgPasswordField
import io.legado.app.ui.design.components.compose.ngDraggedItem
import io.legado.app.ui.design.components.compose.ngReorderHandle
import io.legado.app.ui.design.components.compose.rememberNgLazyReorderState
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.widget.dialog.CodeDialog
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp

class AiSearchServiceFragment : BaseFragment(R.layout.fragment_ai_config_menu), ConfigBackHandler {
    private var editingProvider by mutableStateOf<AiExternalSearchProvider?>(null)
    private val debugModel by viewModels<AiSearchServiceDebugModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val providerId = savedInstanceState?.getString(STATE_PROVIDER)
        editingProvider = AiExternalSearchProvider.entries.firstOrNull { it.id == providerId }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        (view as ComposeView).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NgAppTheme {
                    val settings by AiExternalSearchStore.changes.collectAsState()
                    val listState = rememberSaveableStateHolder()
                    val back = { requireActivity().onBackPressedDispatcher.onBackPressed() }
                    val provider = editingProvider
                    if (provider != null) {
                        AiSearchServiceConfigScreen(
                            service = settings.service(provider),
                            model = debugModel,
                            onUpdateService = { transform ->
                                AiExternalSearchStore.update { it.withService(transform(it.service(provider))) }
                            },
                            onBack = back,
                            onShowResponse = ::showDebugResponse,
                            onHelp = { document ->
                                if (!childFragmentManager.isStateSaved) showHelp(document)
                            },
                        )
                    } else {
                        listState.SaveableStateProvider("searchServiceList") {
                            AiSearchServiceList(
                                services = settings.services,
                                onOpen = { editingProvider = it },
                                onBack = back,
                                onReorder = { order -> AiExternalSearchStore.update { it.withServiceOrder(order) } },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onConfigBackPressed(): Boolean {
        if (editingProvider == null) return false
        if (debugModel.handleBack()) return true
        debugModel.reset()
        editingProvider = null
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PROVIDER, editingProvider?.id)
    }

    private fun showDebugResponse(result: AiExternalSearchDebugResult<*>) {
        if (childFragmentManager.isStateSaved) return
        val response = result.response
        val text = buildString {
            response?.let { appendLine(getString(R.string.ai_search_debug_http_status, it.status)) }
            appendLine(getString(R.string.ai_search_debug_duration_ms, result.elapsedMs))
            result.error?.let { appendLine(it.reasonMessage(requireContext())) }
            appendLine()
            append(response?.body?.takeIf { it.isNotBlank() } ?: getString(R.string.ai_search_debug_no_body))
            if (response?.truncated == true) {
                appendLine()
                appendLine(getString(R.string.ai_search_debug_body_truncated))
            }
        }
        showDialogFragment(
            CodeDialog(
                code = text,
                title = getString(R.string.ai_search_debug_view_response),
                exportFilePrefix = "legado-search-response",
            ),
        )
    }

    private companion object {
        const val STATE_PROVIDER = "searchServiceEditingId"
    }
}

@Composable
internal fun AiSearchServiceList(
    services: List<AiExternalSearchService>,
    onOpen: (AiExternalSearchProvider) -> Unit,
    onBack: () -> Unit,
    onReorder: (List<AiExternalSearchProvider>) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val matching = services.filter { service ->
        matchesAiProviderName(service.displayName, query) ||
            matchesAiProviderName(service.provider.id, query) ||
            matchesAiProviderName(service.provider.host, query)
    }
    val state = if (matching.isNotEmpty()) {
        NgListState.Content(matching)
    } else {
        NgListState.Empty(stringResource(R.string.ai_search_no_matching_service))
    }
    val capabilityTags = searchServiceCapabilityTags()
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        NgFloatingSearchToolbar(
            query = query,
            onQueryChange = { query = it },
            hint = stringResource(R.string.ai_search_services),
            onBack = onBack,
            onSearch = { focusManager.clearFocus() },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        NgListStateContent(state, modifier = Modifier.fillMaxWidth().weight(1f)) { visibleServices ->
            var orderedServices by remember(visibleServices) { mutableStateOf(visibleServices) }
            val canReorder = query.isBlank() && visibleServices.size > 1
            val dragDescription = stringResource(R.string.group_drag_sort)
            val reorderState = rememberNgLazyReorderState(
                onMove = { from, to ->
                    if (canReorder && from in orderedServices.indices && to in orderedServices.indices && from != to) {
                        orderedServices = orderedServices.toMutableList().apply { add(to, removeAt(from)) }
                    }
                },
                onFinished = {
                    if (canReorder && orderedServices != visibleServices) {
                        onReorder(orderedServices.map(AiExternalSearchService::provider))
                    }
                },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = reorderState.listState,
                contentPadding = aiServiceListPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(orderedServices, key = { it.provider.id }) { item ->
                    val serviceId = item.provider.id
                    AiServiceListCard(
                        title = item.displayName,
                        host = item.provider.host.takeUnless { it == item.displayName }.orEmpty(),
                        iconRes = item.provider.iconRes,
                        enabled = item.enabled,
                        keyConfigured = item.apiKey.isNotBlank(),
                        modifier = Modifier.ngDraggedItem(reorderState, serviceId),
                        capabilityTags = capabilityTags,
                        trailingContent = if (canReorder) {
                            {
                                NgManagementTrailingIcon(
                                    trailing = NgManagementTrailing.DRAG,
                                    contentDescription = dragDescription,
                                    modifier = Modifier.ngReorderHandle(reorderState, serviceId, enabled = true),
                                )
                            }
                        } else {
                            null
                        },
                        onClick = { onOpen(item.provider) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun AiSearchServiceConfigScreen(
    service: AiExternalSearchService,
    model: AiSearchServiceDebugModel,
    onUpdateService: ((AiExternalSearchService) -> AiExternalSearchService) -> Unit,
    onBack: () -> Unit,
    onShowResponse: (AiExternalSearchDebugResult<*>) -> Unit,
    onHelp: (String) -> Unit,
) {
    val formState = rememberSaveableStateHolder()
    val formScroll = rememberScrollState()
    val resultTool = model.resultTool
    if (resultTool != null) {
        AiSearchServiceDebugScreen(service, resultTool, model, onBack, onShowResponse)
    } else {
        formState.SaveableStateProvider(service.provider.id) {
            var resetDialogVisible by rememberSaveable { mutableStateOf(false) }
            var resetVersion by rememberSaveable { mutableIntStateOf(0) }
            val context = LocalContext.current
            Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
                NgFloatingTitleToolbar(
                    title = service.displayName,
                    onBack = onBack,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    titleLeadingContent = { SearchServiceLogo(service.provider, Modifier.padding(end = 7.dp)) },
                    trailingContent = {
                        AiConfigHelpResetActions(
                            onHelp = { onHelp("${service.provider.id}SearchServiceHelp") },
                            onReset = { resetDialogVisible = true },
                        )
                    },
                )
                AiSearchServiceForm(
                    service = service,
                    resetVersion = resetVersion,
                    onUpdateService = onUpdateService,
                    onTest = model::startTest,
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(formScroll)
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
                )
            }
            if (resetDialogVisible) {
                val dismiss = { resetDialogVisible = false }
                NgPaneDialog(onDismissRequest = dismiss) {
                    AiClassicDialogContent(
                        title = stringResource(R.string.ai_provider_reset),
                        message = stringResource(R.string.ai_search_service_reset_message),
                        cancelText = stringResource(android.R.string.cancel),
                        confirmText = stringResource(android.R.string.ok),
                        onCancel = dismiss,
                        onConfirm = {
                            dismiss()
                            onUpdateService { current ->
                                AiExternalSearchService(
                                    provider = current.provider,
                                    apiKey = current.apiKey,
                                    enabled = current.enabled,
                                )
                            }
                            resetVersion++
                            Toast.makeText(context, R.string.ai_search_service_reset_done, Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AiSearchServiceForm(
    service: AiExternalSearchService,
    resetVersion: Int,
    onUpdateService: ((AiExternalSearchService) -> AiExternalSearchService) -> Unit,
    onTest: (AiExternalSearchService, AiExternalSearchTool, String) -> Unit,
    modifier: Modifier,
) {
    var countDraft by rememberSaveable(resetVersion) { mutableStateOf<String?>(null) }
    var testCountOverride by rememberSaveable { mutableStateOf<String?>(null) }
    val range = service.chatResultCountRange
    val arrow = rememberVectorPainter(Icons.Rounded.KeyboardArrowDown)
    val focus = LocalFocusManager.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NgFormFieldGroup {
            NgFormField(
                label = stringResource(R.string.ai_search_service_name),
                value = service.name,
                onValueChange = { name -> onUpdateService { it.copy(name = name) } },
            )
            NgPasswordField(
                label = stringResource(if (service.provider == AiExternalSearchProvider.DOUBAO) {
                    R.string.ai_search_doubao_key
                } else {
                    R.string.ai_api_key
                }),
                value = service.apiKey,
                onValueChange = { key -> onUpdateService { it.copy(apiKey = key) } },
                hiddenIcon = painterResource(R.drawable.ic_visibility_off),
                visibleIcon = painterResource(R.drawable.ic_visibility),
                showPasswordDescription = stringResource(R.string.ai_api_key_toggle_visibility),
                hidePasswordDescription = stringResource(R.string.ai_api_key_toggle_visibility),
            )
            NgFormField(
                label = stringResource(R.string.ai_base_url),
                value = service.provider.baseUrl,
                onValueChange = {},
                readOnly = true,
            )
            when (service.provider) {
                AiExternalSearchProvider.DEEPSEEK, AiExternalSearchProvider.TINYFISH, AiExternalSearchProvider.EXA -> Unit
                AiExternalSearchProvider.DOUBAO -> NgFormSelectRow(
                    title = stringResource(R.string.ai_search_doubao_mode),
                    selectedValue = service.doubaoMode.name,
                    options = AiDoubaoSearchMode.entries.map { mode ->
                        NgFormSelectOption(mode.id.replaceFirstChar(Char::uppercaseChar), mode.name)
                    },
                    onValueChange = { value ->
                        focus.clearFocus()
                        val mode = AiDoubaoSearchMode.valueOf(value)
                        val testCount = testCountOverride?.toIntOrNull()
                        if (mode == AiDoubaoSearchMode.GLOBAL && testCount != null && testCount > mode.resultCountRange.last) {
                            testCountOverride = mode.resultCountRange.last.toString()
                        }
                        onUpdateService { it.copy(doubaoMode = mode) }
                    },
                    arrowIcon = arrow,
                    menuVariant = NgFormSelectMenuVariant.END_ANCHORED_CONTENT,
                )
                AiExternalSearchProvider.TAVILY -> NgFormSelectRow(
                    title = stringResource(R.string.ai_search_tavily_depth),
                    selectedValue = service.tavilySearchDepth.name,
                    options = AiTavilySearchDepth.entries.map { depth ->
                        NgFormSelectOption(depth.apiValue.replaceFirstChar(Char::uppercaseChar), depth.name)
                    },
                    onValueChange = { value ->
                        focus.clearFocus()
                        onUpdateService { it.copy(tavilySearchDepth = AiTavilySearchDepth.valueOf(value)) }
                    },
                    arrowIcon = arrow,
                    menuVariant = NgFormSelectMenuVariant.END_ANCHORED_CONTENT,
                )
            }
            ResultCountRow(
                title = stringResource(R.string.ai_search_result_count),
                value = countDraft ?: service.chatResultCount.toString(),
                range = range,
                onValueChange = { text ->
                    countDraft = text
                    text.countIn(range)?.let { count -> onUpdateService { it.copy(resultCount = count) } }
                },
            )
        }
        NgFormSwitchGroup {
            val label = stringResource(R.string.ai_search_enable_service)
            NgFormSwitchRow(
                title = label,
                checked = service.enabled,
                onCheckedChange = { enabled -> onUpdateService { it.copy(enabled = enabled) } },
                variant = NgFormSwitchRowVariant.GROUPED,
                switchModifier = Modifier.semantics { contentDescription = label },
            )
        }
        SearchServiceTestForm(service, testCountOverride, { testCountOverride = it }, onTest)
    }
}

@Composable
private fun ResultCountRow(title: String, value: String, range: IntRange, onValueChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    val invalid = value.countIn(range) == null
    NgFormNumberSettingRow(
        title = title,
        summary = if (invalid) stringResource(R.string.ai_search_result_count_range, range.first, range.last) else null,
        value = value,
        onValueChange = { text -> if (text.all { it in '0'..'9' }) onValueChange(text) },
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        valueRange = range,
        onStepValueChange = { onValueChange(it.toString()) },
        onStepValueChangeFinished = {},
        variant = NgFormNumberSettingRowVariant.COMPACT_CHEVRONS,
        isError = invalid,
    )
}

private fun String.countIn(range: IntRange): Int? = toIntOrNull()?.takeIf { it in range }

@Composable
private fun SearchServiceTestForm(
    service: AiExternalSearchService,
    countOverride: String?,
    onCountOverrideChange: (String) -> Unit,
    onTest: (AiExternalSearchService, AiExternalSearchTool, String) -> Unit,
) {
    var tool by rememberSaveable { mutableStateOf(AiExternalSearchTool.SEARCH) }
    var keywords by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    val search = tool == AiExternalSearchTool.SEARCH
    val range = service.resultCountRange
    val countText = countOverride ?: service.chatResultCount.toString()
    val count = countText.countIn(range)
    val input = if (search) keywords else url
    val focus = LocalFocusManager.current
    val arrow = rememberVectorPainter(Icons.Rounded.KeyboardArrowDown)
    val canSubmit = input.isNotBlank() && (!search || count != null)
    val submit: () -> Unit = {
        if (canSubmit) {
            focus.clearFocus()
            val testService = if (search && count != null) service.copy(resultCount = count) else service
            onTest(testService, tool, input)
        }
    }
    NgFormControlGroup(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 3.dp, bottom = 10.dp)) {
        NgFormSelectRow(
            title = stringResource(R.string.ai_search_test_service),
            selectedValue = tool.name,
            options = listOf(
                NgFormSelectOption(stringResource(R.string.ai_search_test_search), AiExternalSearchTool.SEARCH.name),
                NgFormSelectOption(stringResource(R.string.ai_search_test_read), AiExternalSearchTool.READ_LINK.name),
            ),
            onValueChange = {
                focus.clearFocus()
                tool = AiExternalSearchTool.valueOf(it)
            },
            arrowIcon = arrow,
            menuVariant = NgFormSelectMenuVariant.END_ANCHORED_CONTENT,
        )
        NgFormGroupDivider(horizontalPadding = 0.dp)
        if (search) {
            ResultCountRow(
                title = stringResource(R.string.ai_search_test_return_count),
                value = countText,
                range = range,
                onValueChange = onCountOverrideChange,
            )
        }
        NgFormMultilineField(
            value = input,
            onValueChange = { if (search) keywords = it else url = it },
            placeholder = stringResource(if (search) R.string.ai_search_test_query else R.string.ai_search_test_url),
            modifier = Modifier.padding(top = if (search) 2.dp else 9.dp),
            minHeight = 48.dp,
            minLines = 1,
            maxLines = 6,
            containerColor = colorResource(R.color.ng_surface_card),
            variant = NgFormMultilineFieldVariant.INSET_ACTION,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (search) KeyboardType.Text else KeyboardType.Uri,
                imeAction = if (search) ImeAction.Search else ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onSearch = { submit() }, onGo = { submit() }),
            trailingContent = {
                NgButton(
                    onClick = submit,
                    modifier = Modifier.padding(3.dp).size(38.dp),
                    enabled = canSubmit,
                    shapeVariant = NgButtonShapeVariant.COMPACT_ROUNDED,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = stringResource(R.string.ai_search_test_run),
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
        )
    }
}

@get:DrawableRes
internal val AiExternalSearchProvider.iconRes: Int
    get() = when (this) {
        AiExternalSearchProvider.DEEPSEEK -> R.drawable.ic_provider_deepseek
        AiExternalSearchProvider.DOUBAO -> R.drawable.ic_model_doubao
        AiExternalSearchProvider.TAVILY -> R.drawable.ic_search_tavily
        AiExternalSearchProvider.TINYFISH -> R.drawable.ic_search_tinyfish
        AiExternalSearchProvider.EXA -> R.drawable.ic_search_exa
    }

@Composable
internal fun searchServiceCapabilityTags(): List<NgStatusTagSpec> =
    AiExternalSearchTool.entries.map { tool ->
        val label = when (tool) {
            AiExternalSearchTool.SEARCH -> R.string.search
            AiExternalSearchTool.READ_LINK -> R.string.ai_search_capability_fetch
        }
        NgStatusTagSpec(stringResource(label), NgStatusTagVariant.INFO, NgStatusTagStyle.COMPACT)
    }

@Composable
internal fun SearchServiceLogo(
    provider: AiExternalSearchProvider,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    Box(
        modifier.size(size).clip(CircleShape).background(colorResource(R.color.ng_icon_container)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(provider.iconRes), null, Modifier.size(size - 12.dp), tint = Color.Unspecified)
    }
}
