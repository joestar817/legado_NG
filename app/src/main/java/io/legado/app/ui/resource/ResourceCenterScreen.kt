package io.legado.app.ui.resource

import android.graphics.BitmapFactory
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.PopupProperties
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.help.resource.*
import io.legado.app.help.home.HomeWidgetSkinRecord
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.NgFilterChipGroupVariant
import io.legado.app.ui.design.components.compose.*
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.pet.PetLocalResourceCard
import io.legado.app.ui.pet.versionLabel
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ResourceCenterScreen(
    state: ResourceCenterState,
    onBack: () -> Unit,
    onOpenRepository: () -> Unit,
    onUse: (ResourceEntry) -> Unit,
    onPriorityChanged: (Set<String>?) -> Unit,
    onOpenPetManager: (String) -> Unit,
    onUninstallPet: (String) -> Unit,
    initialPurpose: String? = null,
    initialType: String? = null,
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("resource-center-ui", 0) }
    var grid by rememberSaveable { mutableStateOf(preferences.getBoolean("grid", false)) }
    var page by rememberSaveable { mutableStateOf(ResourceCollectionPage.LIBRARY) }
    var purpose by rememberSaveable { mutableStateOf(ResourcePurpose.entries.firstOrNull { it.name == initialPurpose }) }
    var resourceType by rememberSaveable { mutableStateOf(initialType.orEmpty()) }
    var query by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf(false) }
    var detailJson by rememberSaveable { mutableStateOf<String?>(null) }
    val detail = remember(detailJson) {
        detailJson?.let { runCatching { GSON.fromJson(it, ResourceEntry::class.java) }.getOrNull() }
            ?.takeIf { ResourceProtocol.isBrowsableType(it.category.type) }
    }
    var conflict by remember { mutableStateOf<ResourceEntry?>(null) }
    var confirmedSkinCandidate by remember { mutableStateOf<HomeWidgetSkinRecord?>(null) }
    val showSkinConflict: (ResourceEntry, HomeWidgetSkinRecord) -> Unit = { entry, candidate ->
        confirmedSkinCandidate = candidate
        conflict = entry
    }
    var skinDownload by remember { mutableStateOf<Pair<ResourceEntry, ResourceTask?>?>(null) }
    val skinDownloadTask = skinDownload?.first?.item?.id?.let(state.tasks::get)
    LaunchedEffect(skinDownload, skinDownloadTask) {
        val (entry, before) = skinDownload ?: return@LaunchedEffect
        val task = skinDownloadTask ?: return@LaunchedEffect
        if (task !== before && !task.running) {
            skinDownload = null
            task.widgetSkinConflict?.let { showSkinConflict(entry, it) }
        }
    }
    var themeDownload by remember { mutableStateOf<Pair<ResourceEntry, ResourceTask?>?>(null) }
    val themeDownloadTask = themeDownload?.first?.item?.id?.let(state.tasks::get)
    LaunchedEffect(themeDownload, themeDownloadTask) {
        val (entry, before) = themeDownload ?: return@LaunchedEffect
        val task = themeDownloadTask ?: return@LaunchedEffect
        if (task !== before && !task.running) {
            themeDownload = null
            if (task.themeConflict != null) conflict = entry
        }
    }
    var presetDownload by remember { mutableStateOf<Pair<ResourceEntry, ResourceTask?>?>(null) }
    val presetDownloadTask = presetDownload?.first?.item?.id?.let(state.tasks::get)
    LaunchedEffect(presetDownload, presetDownloadTask) {
        val (entry, before) = presetDownload ?: return@LaunchedEffect
        val task = presetDownloadTask ?: return@LaunchedEffect
        if (task !== before && !task.running) {
            presetDownload = null
            if (task.readPresetConflict != null) conflict = entry
        }
    }
    var menu by remember { mutableStateOf(false) }
    var uninstallPetId by rememberSaveable { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = remember { FocusRequester() }
    var requestSearchFocus by remember { mutableStateOf(false) }
    LaunchedEffect(search, detail, requestSearchFocus) {
        if (search && detail == null && requestSearchFocus) {
            searchFocus.requestFocus()
            keyboard?.show()
            requestSearchFocus = false
        }
    }
    val listState = rememberLazyGridState()
    var pendingViewAnchor by remember { mutableStateOf<String?>(null) }
    val downloadedInstallations = remember(state.installations, state.localStates, state.widgetSkins) { state.downloadedInstallations().filter { it.entry.category.type != "pet" } }
    val updateTypes = remember(state.installations, state.localStates, state.petInstallations, state.widgetSkins) {
        state.updateTypes()
    }
    val updateEntries = remember(state.snapshot, state.installations, state.localStates, state.petInstallations, state.widgetSkins) { state.updatableEntries() }
    val updatesReady = remember(state.snapshot, state.loadedCategories, state.localChecked, state.installations,
        state.localStates, state.petInstallations, state.widgetSkins, state.storageError, state.accessDenied) { state.updatesReady() }
    val visibleCatalog = state.visibleCatalog
    val libraryEntries = remember(state.snapshot, state.cachedSnapshot, state.loadedCategories) { state.visibleCatalogEntries() }
    val entries = remember(state.snapshot, libraryEntries, downloadedInstallations, updateEntries, page) {
        when (page) {
            ResourceCollectionPage.LIBRARY -> libraryEntries
            ResourceCollectionPage.DOWNLOADED -> {
                val remote = state.snapshot?.entries.orEmpty().associateBy { it.item.id }
                downloadedInstallations.map { remote[it.entry.item.id] ?: it.entry }
            }
            ResourceCollectionPage.UPDATES -> updateEntries
        }
    }
    val categories = remember(visibleCatalog, downloadedInstallations) {
        (visibleCatalog?.categories.orEmpty().filter { it.count > 0 } +
            downloadedInstallations.map { it.entry.category }).distinctBy { it.id }
    }
    val typeOptions = remember(categories, purpose, state.petInstallations, page, initialType) {
        val options = resourceTypeOptions(categories, purpose)
        if (initialType == HOME_WIDGET_SKIN_TYPE && purpose == ResourcePurpose.APPEARANCE &&
            options.none { it.key == HOME_WIDGET_SKIN_TYPE }) {
            options + ResourceTypeOption(HOME_WIDGET_SKIN_TYPE, "组件皮肤")
        } else if (page == ResourceCollectionPage.DOWNLOADED && state.petInstallations.isNotEmpty() &&
            (purpose == null || purpose == ResourcePurpose.PETS) && options.none { it.key == "pet" }) {
            options + ResourceTypeOption("pet", "宠物插件")
        } else options
    }
    val selectedType = resourceType.takeIf { type -> typeOptions.any { it.key == type } }.orEmpty()
    val filtered = remember(entries, purpose, selectedType, query) {
        filterResourceEntries(entries, purpose, selectedType, query)
    }
    val localPets = remember(page, purpose, selectedType, query, state.petInstallations) {
        if (page != ResourceCollectionPage.DOWNLOADED ||
            (purpose != null && purpose != ResourcePurpose.PETS) ||
            (selectedType.isNotEmpty() && selectedType != "pet")) emptyList() else {
            val term = query.trim()
            state.petInstallations.values.filter { pet ->
                term.isEmpty() || listOfNotNull(pet.manifest?.name, pet.id, pet.info?.author, pet.info?.description)
                    .any { it.contains(term, ignoreCase = true) }
            }.sortedWith(compareBy({ it.manifest?.name ?: it.id }, { it.id }))
        }
    }
    val visibleCount = filtered.size + localPets.size
    val selectedTypes = resourcePriorityTypes(purpose, selectedType)
    val scopedCategories = remember(visibleCatalog, selectedTypes, page, updateTypes) {
        val types = if (page == ResourceCollectionPage.UPDATES) updateTypes
            else selectedTypes
        visibleCatalog?.categories.orEmpty().filter { types == null || it.type in types }.map { it.id }.toSet()
    }
    val scopePending = scopedCategories.any { it in state.pendingCategories }
    val scopeComplete = when (page) {
        ResourceCollectionPage.DOWNLOADED -> state.localChecked && state.storageError == null
        ResourceCollectionPage.UPDATES -> updatesReady
        ResourceCollectionPage.LIBRARY -> state.snapshot != null && scopedCategories.all { it in state.loadedCategories }
    }
    val pageLoading = when (page) {
        ResourceCollectionPage.DOWNLOADED -> !state.localChecked && state.storageError == null
        ResourceCollectionPage.UPDATES -> !updatesReady && (state.loading || scopePending || !state.localChecked)
        ResourceCollectionPage.LIBRARY -> state.loading || scopePending
    }
    val scopeError = if (page == ResourceCollectionPage.DOWNLOADED) null else {
        val error = state.error ?: visibleCatalog?.categories.orEmpty().filter { it.id in scopedCategories }.mapNotNull { category ->
            state.categoryErrors[category.id]?.let { "${category.resourceTypeTitle()}：$it" }
        }.joinToString("；").takeIf { it.isNotBlank() }
        if (error != null && page == ResourceCollectionPage.LIBRARY && state.cachedSnapshot != null) "暂未同步 · $error" else error
    }
    val retryScope: () -> Unit = {
        if (page == ResourceCollectionPage.DOWNLOADED) ResourceRepository.checkLocalFiles()
        else if (state.snapshot == null || state.error != null) ResourceRepository.refresh()
        else ResourceRepository.retryCategories(scopedCategories)
    }
    val priority = when {
        detail != null && !state.hasInstalled(detail) -> setOf(detail.category.type)
        page == ResourceCollectionPage.DOWNLOADED -> emptySet()
        page == ResourceCollectionPage.UPDATES -> updateTypes
        else -> resourcePriorityTypes(purpose, resourceType)
    }
    LaunchedEffect(priority) { onPriorityChanged(priority) }
    val keepSingleHeader = page == ResourceCollectionPage.LIBRARY && !scopeComplete && scopedCategories.size > 1
    val rows = remember(filtered, selectedType, grid, keepSingleHeader) {
        resourceDisplayRows(filtered, grouped = !grid && selectedType.isEmpty(), keepSingleHeader = keepSingleHeader)
    }
    val filterKey = listOf(page.name, purpose?.name.orEmpty(), selectedType, query)
    var previousFilterKey by rememberSaveable { mutableStateOf(filterKey) }
    LaunchedEffect(filterKey) {
        if (previousFilterKey != filterKey) {
            previousFilterKey = filterKey
            pendingViewAnchor = null
            listState.scrollToItem(0)
        }
    }
    LaunchedEffect(rows, localPets, grid, pendingViewAnchor) {
        pendingViewAnchor?.let { anchor ->
            val headerCount = if (localPets.isNotEmpty() && !grid && selectedType.isEmpty()) 1 else 0
            val index = if (anchor.startsWith("local-pet:")) {
                localPets.indexOfFirst { "local-pet:${it.id}" == anchor }.coerceAtLeast(0) + headerCount
            } else resourceAnchorIndex(rows, anchor) + localPets.size + headerCount
            listState.scrollToItem(index)
            pendingViewAnchor = null
        }
    }
    val filterItems = remember {
        listOf(NgFilterChipItem("", "全部")) + ResourcePurpose.entries.map {
            NgFilterChipItem(it.name, it.title)
        }
    }
    val initialLoading = pageLoading && visibleCount == 0
    val selectGrid: (Boolean) -> Unit = { next ->
        if (next != grid) {
            pendingViewAnchor = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                (it.key as? String)?.let { key -> key.startsWith("resource:") || key.startsWith("local-pet:") } == true
            }?.key as? String
            grid = next
            preferences.edit().putBoolean("grid", next).apply()
        }
    }
    val back: () -> Unit = {
        menu = false
        when {
            detail != null -> detailJson = null
            search -> { query = ""; search = false; keyboard?.hide() }
            else -> onBack()
        }
    }
    BackHandler { back() }

    val action: (ResourceEntry) -> Unit = { entry ->
        val id = entry.item.id
        val task = state.tasks[id]
        when {
            task?.running == true -> ResourceRepository.cancel(id)
            state.confirmedEntry(entry) == null -> if (canUseLocalResource(entry, state)) onUse(entry)
            task?.widgetSkinConflict != null -> showSkinConflict(entry, task.widgetSkinConflict)
            state.isLocalWidgetSkin(entry) -> {
                skinDownload = entry to task
                ResourceRepository.install(entry)
            }
            task?.conflict == true || state.isLocalPetChange(entry) ||
                (entry.category.type != "read_preset" && state.localStates[id] == ResourceLocalState.MODIFIED) -> conflict = entry
            state.hasInstalled(entry) && (entry.category.type in setOf("pet", HOME_WIDGET_SKIN_TYPE) || state.localStates[id] == ResourceLocalState.PRESENT) &&
                versionLabel(entry, state) != "可更新" -> onUse(entry)
            else -> {
                if (entry.category.type == HOME_WIDGET_SKIN_TYPE) skinDownload = entry to task
                if (entry.category.type == "theme") themeDownload = entry to task
                if (entry.category.type == "read_preset") presetDownload = entry to task
                ResourceRepository.install(entry)
            }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        val actions: @Composable RowScope.() -> Unit = {
            if (detail == null && !search) {
                NgFloatingToolbarActionButton(
                    R.drawable.ic_search, "搜索资源", { search = true; requestSearchFocus = true },
                    variant = NgFloatingToolbarVariant.STANDARD,
                )
                Spacer(Modifier.width(2.dp))
            }
            Box {
                NgFloatingToolbarActionButton(
                    R.drawable.ic_grid_menu, "更多", { menu = !menu },
                    variant = NgFloatingToolbarVariant.STANDARD,
                )
                val options = buildList {
                    add(NgExpandableActionMenuItem(
                        1, R.string.resource_center_refresh, R.drawable.ic_refresh_black_24dp,
                        enabled = !state.loading,
                    ))
                    if (detail == null) {
                        add(NgExpandableActionMenuItem(
                            4, R.string.resource_center_list_view, R.drawable.ic_chapter_list,
                            dividerBefore = true, checked = !grid,
                        ))
                        add(NgExpandableActionMenuItem(
                            5, R.string.resource_center_grid_view, R.drawable.ic_view_quilt, checked = grid,
                        ))
                    }
                    add(NgExpandableActionMenuItem(
                        2, R.string.resource_center_repository, R.drawable.ic_about_cloud_download,
                        dividerBefore = true,
                    ))
                    if (detail != null && state.hasInstalled(detail)) {
                        add(NgExpandableActionMenuItem(3, R.string.resource_center_redownload, R.drawable.ic_download,
                            enabled = state.confirmedEntry(detail) != null))
                    }
                }
                NgExpandableActionMenu(
                    expanded = menu, onDismissRequest = { menu = false }, items = options,
                    variant = NgExpandableActionMenuVariant.SIDE_SLIDE,
                    menuContainerColor = ngDrawerContentCardColor(),
                    properties = PopupProperties(focusable = true, clippingEnabled = false),
                    onItemClick = {
                        menu = false
                        when (it.itemId) {
                            1 -> ResourceRepository.refresh()
                            2 -> onOpenRepository()
                            3 -> detail?.let(state::confirmedEntry)?.let { entry ->
                                if (state.isLocalPetChange(entry) || (entry.category.type != "read_preset" &&
                                        !state.isLocalWidgetSkin(entry) &&
                                        state.localStates[entry.item.id] == ResourceLocalState.MODIFIED)) conflict = entry
                                else {
                                    if (entry.category.type == HOME_WIDGET_SKIN_TYPE) skinDownload = entry to state.tasks[entry.item.id]
                                    if (entry.category.type == "read_preset") presetDownload = entry to state.tasks[entry.item.id]
                                    ResourceRepository.install(entry, ResourceInstallMode.REPLACE)
                                }
                            }
                            4 -> selectGrid(false)
                            5 -> selectGrid(true)
                        }
                    },
                )
            }
        }
        if (search && detail == null) {
            NgFloatingSearchToolbar(
                query, { query = it }, "搜索资源", back,
                modifier = Modifier.padding(horizontal = 10.dp),
                variant = NgFloatingToolbarVariant.LARGE_TOUCH_TARGETS,
                searchFocusRequester = searchFocus,
                onSearch = { keyboard?.hide() }, trailingContent = actions,
            )
        } else {
            NgFloatingTitleToolbar(
                title = if (detail == null) "资源中心" else "资源详情",
                onBack = back, modifier = Modifier.padding(horizontal = 10.dp),
                variant = NgFloatingToolbarVariant.LARGE_TOUCH_TARGETS, trailingContent = actions,
            )
        }
        NgGlassSurface(
            modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()
                .padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 8.dp),
            role = NgMaterialRole.CONTENT,
            liquidCornerRadius = NgTheme.shapes.mediumDp.dp,
            shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
            style = NgGlassDefaults.bookDetailStyle(containerColor = colorResource(R.color.ng_surface_card)),
        ) {
            state.storageError?.let { ResourceMessageRow(it, onRetry = ResourceRepository::checkLocalFiles) }
            if (detail != null) {
                val displayed = when {
                    state.accessDenied -> state.installed(detail.item.id)?.entry
                    state.hasInstalled(detail) -> detail
                    else -> libraryEntries.firstOrNull { it.item.id == detail.item.id }
                }
                if (displayed == null) {
                    val waiting = !state.accessDenied && (state.loading || state.pendingCategories.isNotEmpty())
                    ResourceEmptyState(
                        loading = waiting,
                        title = if (waiting) "正在获取资源" else "资源暂不可用",
                        description = if (waiting) null else state.error ?: state.categoryErrors[detail.category.id],
                        actionLabel = if (waiting) null else "重试", onAction = ResourceRepository::refresh,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    if (state.loading || displayed.category.id in state.pendingCategories) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            NgDotsLoadingIndicator(Modifier.size(36.dp))
                            Text("正在检查更新", fontSize = 13.sp, color = Color(NgTheme.colors.onSurfaceVariant))
                        }
                    }
                    if (!state.hasInstalled(detail)) {
                        state.error?.let {
                            val message = if (state.cachedSnapshot != null) "暂未同步 · $it" else it
                            ResourceMessageRow(message, enabled = !state.loading, onRetry = ResourceRepository::refresh)
                        }
                    }
                    ResourceDetail(displayed, state, action, onViewLatest = { detailJson = GSON.toJson(it) },
                        onUninstall = { id ->
                            uninstallPetId = id
                        }, modifier = Modifier.weight(1f))
                }
            } else {
                ResourceCollectionTabs(
                    page = page,
                    updatesCount = updateEntries.size,
                    refreshing = pageLoading,
                    initialLoading = initialLoading,
                    onSelected = { page = it },
                    updatesReady = updatesReady,
                )
                NgFilterChipGroup(
                    items = filterItems,
                    selectedKeys = setOf(purpose?.name.orEmpty()),
                    onToggle = { key ->
                        val next = ResourcePurpose.entries.firstOrNull { it.name == key }
                        if (next != purpose) {
                            purpose = next
                            resourceType = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    variant = NgFilterChipGroupVariant.EVEN_ROW,
                )
                HorizontalDivider(
                    Modifier.padding(horizontal = 8.dp), thickness = 0.5.dp,
                    color = Color(NgTheme.colors.onSurface).copy(alpha = .12f),
                )
                ResourceCollectionBody(
                    empty = visibleCount == 0,
                    modifier = Modifier.weight(1f),
                    header = {
                        if (visibleCatalog != null || page != ResourceCollectionPage.LIBRARY) {
                            key(purpose) {
                                val presetConflicts = if (page == ResourceCollectionPage.UPDATES && !state.updatingAll) {
                                    updateEntries.count { state.tasks[it.item.id]?.readPresetConflict != null }
                                } else 0
                                Column {
                                    ResourceTypeFilterRow(
                                        count = visibleCount,
                                        selectedType = selectedType,
                                        types = typeOptions,
                                        onSelected = { resourceType = it },
                                        complete = scopeComplete,
                                        loading = pageLoading,
                                        pendingCount = presetConflicts,
                                        trailingAction = if (page == ResourceCollectionPage.UPDATES) {
                                            {
                                                val enabled = updatesReady && updateEntries.isNotEmpty() && !state.updatingAll && !state.loading && state.storageError == null
                                                TextButton(
                                                    onClick = ResourceRepository::updateAll,
                                                    enabled = enabled,
                                                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                                                        contentDescription = if (state.updatingAll) "正在全部更新" else "全部更新，共 ${updateEntries.size} 项，不受分类和搜索筛选限制"
                                                    },
                                                ) {
                                                    Text(if (state.updatingAll) "更新中" else "全部更新", fontSize = 14.sp,
                                                        maxLines = 1,
                                                        color = Color(NgTheme.colors.primary).let { if (enabled) it else it.copy(alpha = .45f) })
                                                }
                                            }
                                        } else null,
                                    )
                                    if (presetConflicts > 0 && (purpose != null || selectedType.isNotEmpty() || query.isNotBlank())) {
                                        Text("查看全部待处理", fontSize = 12.sp, color = Color(NgTheme.colors.primary),
                                            modifier = Modifier.align(Alignment.End).clickable {
                                                purpose = null; resourceType = ""; query = ""
                                            }.padding(horizontal = 14.dp, vertical = 4.dp))
                                    }
                                }
                            }
                        }
                    },
                    content = {
                        if (visibleCount == 0) {
                            val noCatalog = visibleCatalog == null && page != ResourceCollectionPage.DOWNLOADED &&
                                !(page == ResourceCollectionPage.UPDATES && updatesReady)
                            val filteredOut = (entries.isNotEmpty() || (page == ResourceCollectionPage.DOWNLOADED && state.petInstallations.isNotEmpty())) &&
                                (query.isNotBlank() || purpose != null || selectedType.isNotEmpty())
                            ResourceEmptyState(
                                loading = initialLoading,
                                title = when {
                                    initialLoading -> "正在获取资源"
                                    noCatalog -> "资源库暂不可访问"
                                    !scopeComplete -> "资源加载未完成"
                                    filteredOut -> "没有匹配的资源"
                                    page == ResourceCollectionPage.UPDATES -> "暂无可更新资源"
                                    page == ResourceCollectionPage.DOWNLOADED -> "还没有下载资源"
                                    else -> "暂无资源"
                                },
                                description = scopeError.takeIf { !initialLoading && !scopeComplete },
                                actionLabel = when {
                                    initialLoading -> null
                                    noCatalog -> "重试"
                                    !scopeComplete -> "重试"
                                    filteredOut -> "清除筛选"
                                    page == ResourceCollectionPage.DOWNLOADED -> "浏览资源"
                                    else -> "刷新"
                                },
                                onAction = {
                                    when {
                                        noCatalog || !scopeComplete -> retryScope()
                                        filteredOut -> { query = ""; purpose = null; resourceType = "" }
                                        page == ResourceCollectionPage.DOWNLOADED -> page = ResourceCollectionPage.LIBRARY
                                        else -> ResourceRepository.refresh()
                                    }
                                },
                            )
                        } else {
                            Column(Modifier.fillMaxSize()) {
                                scopeError?.let { ResourceMessageRow(it, enabled = !state.loading, onRetry = retryScope) }
                                val columns = if (grid && LocalConfiguration.current.screenWidthDp >= 360 && LocalDensity.current.fontScale < 1.3f) 2 else 1
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(columns), state = listState,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(if (grid) 10.dp else 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    if (localPets.isNotEmpty()) {
                                        if (!grid && selectedType.isEmpty()) item(key = "local-pets-header", span = { GridItemSpan(maxLineSpan) }) {
                                            ResourceSectionHeader("宠物插件", localPets.size)
                                        }
                                        items(localPets, key = { "local-pet:${it.id}" }, contentType = { if (grid) "local-pet-grid" else "local-pet-list" }) { pet ->
                                            PetLocalResourceCard(pet, grid) { onOpenPetManager(pet.id) }
                                        }
                                    }
                                    items(
                                        rows,
                                        key = { it.key },
                                        span = { GridItemSpan(if (it.entry == null) maxLineSpan else 1) },
                                        contentType = { if (it.entry == null) "group" else if (grid) "grid" else "list" },
                                    ) { row ->
                                        val entry = row.entry
                                        if (entry == null) {
                                            ResourceSectionHeader(row.groupName, row.count)
                                        } else {
                                            ResourceCard(entry, state, grid, onOpen = {
                                                keyboard?.hide()
                                                detailJson = GSON.toJson(entry)
                                            }, onAction = { action(entry) })
                                        }
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
    }
    uninstallPetId?.let { id ->
        Dialog(onDismissRequest = { uninstallPetId = null }) {
            NgDialog(title = "卸载宠物", actions = {
                NgFormActionButton("取消", { uninstallPetId = null }, Modifier.weight(1f),
                    appearance = NgFormActionButtonAppearance.SURFACE_CARD_BORDERLESS)
                NgFormActionButton("卸载", { onUninstallPet(id); uninstallPetId = null }, Modifier.weight(1f),
                    variant = NgButtonVariant.PRIMARY_LIGHT_CONTENT)
            }) {
                Text("卸载该宠物？若已选择，将切回悬浮按钮并保留入口开关状态。", color = Color(NgTheme.colors.onSurface))
            }
        }
    }
    conflict?.let(state::confirmedEntry)?.let { entry ->
        val presetConflict = state.tasks[entry.item.id]?.readPresetConflict
        val themeConflict = state.tasks[entry.item.id]?.themeConflict
        val skinReplacement = confirmedSkinCandidate?.takeIf { it.id == entry.item.id }
        if (presetConflict != null) {
            var selectedIndex by remember(presetConflict) {
                mutableStateOf(presetConflict.candidates.singleOrNull()?.index)
            }
            val selected = presetConflict.candidates.firstOrNull { it.index == selectedIndex }
            val dismiss = { ResourceRepository.dismissTask(entry.item.id); conflict = null }
            Dialog(onDismissRequest = dismiss) {
                NgDialog(title = null, variant = NgDialogVariant.COMPACT_CONFIRMATION, actions = {
                    ResourcePresetConflictAction(if (presetConflict.isUpdate) "暂不更新" else "取消",
                        onClick = dismiss, modifier = Modifier.weight(1f), secondary = true)
                    ResourcePresetConflictAction("保留两份", onClick = {
                        presetDownload = entry to state.tasks[entry.item.id]
                        ResourceRepository.install(entry, ResourceInstallMode.COPY,
                            confirmedReadPresetConflict = presetConflict)
                        conflict = null
                    }, modifier = Modifier.weight(1f))
                    ResourcePresetConflictAction("替换", enabled = selected != null, onClick = {
                        presetDownload = entry to state.tasks[entry.item.id]
                        ResourceRepository.install(entry, ResourceInstallMode.REPLACE,
                            confirmedReadPresetConflict = presetConflict, readPresetReplacement = selected)
                        conflict = null
                    }, modifier = Modifier.weight(1f))
                }) {
                    val multiple = presetConflict.candidates.size > 1
                    val message = when {
                        multiple && selected?.selected == true -> "已选中此预设，替换会覆盖修改。"
                        multiple -> "替换会覆盖所选预设的修改。"
                        selected?.selected == true -> "「${selected.name}」已选中，替换会覆盖修改。"
                        else -> "替换会覆盖「${selected?.name.orEmpty()}」的修改。"
                    }
                    Text(if (presetConflict.isUpdate) "更新预设" else "已有同名预设",
                        modifier = Modifier.fillMaxWidth(), color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(message, modifier = Modifier.fillMaxWidth(), color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center)
                    if (multiple) {
                        Spacer(Modifier.height(8.dp))
                        Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                            presetConflict.candidates.forEach { candidate ->
                                Row(Modifier.fillMaxWidth().clickable { selectedIndex = candidate.index }
                                    .heightIn(min = 44.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (candidate.index == selectedIndex) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                        contentDescription = null, tint = Color(NgTheme.colors.primary), modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text("${candidate.name} · 第${candidate.index + 1}项",
                                        color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 14.sp,
                                        fontWeight = FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
        } else Dialog(onDismissRequest = {
            if (skinReplacement != null) ResourceRepository.dismissTask(entry.item.id)
            conflict = null
            confirmedSkinCandidate = null
        }) {
            NgDialog(title = if (skinReplacement != null) "替换皮肤" else "本地资源冲突", actions = {
                if (entry.category.type == "pet") {
                    NgFormActionButton("保留本地", {
                        ResourceRepository.dismissTask(entry.item.id); conflict = null
                    }, Modifier.weight(1f), appearance = NgFormActionButtonAppearance.SURFACE_CARD_BORDERLESS)
                    if (state.hasInstalled(entry)) NgFormActionButton(
                        if ((state.pet(entry)?.manifest?.versionCode ?: 0) > entry.item.versionCode) "降级安装" else "替换",
                        { ResourceRepository.install(entry, ResourceInstallMode.REPLACE); conflict = null },
                        Modifier.weight(1f), variant = NgButtonVariant.PRIMARY_LIGHT_CONTENT,
                    )
                } else {
                    NgDialogTextActionButton("保留本地", onClick = {
                        ResourceRepository.dismissTask(entry.item.id); conflict = null; confirmedSkinCandidate = null
                    })
                    if (themeConflict?.canReplace ?: state.hasInstalled(entry)) NgDialogTextActionButton("替换", onClick = {
                        if (entry.category.type == "theme") themeDownload = entry to state.tasks[entry.item.id]
                        if (entry.category.type == HOME_WIDGET_SKIN_TYPE) skinDownload = entry to state.tasks[entry.item.id]
                        ResourceRepository.install(entry, ResourceInstallMode.REPLACE, themeConflict,
                            confirmedWidgetSkinReplacement = skinReplacement)
                        conflict = null
                        confirmedSkinCandidate = null
                    })
                    if (entry.category.type != HOME_WIDGET_SKIN_TYPE) NgDialogTextActionButton("安装副本", onClick = {
                        ResourceRepository.install(entry, ResourceInstallMode.COPY); conflict = null
                    })
                }
            }) {
                if (entry.category.type == "pet") {
                    state.pet(entry)?.let { pet ->
                        Text("${pet.versionLabel()} → ${entry.item.displayVersion}", color = Color(NgTheme.colors.onSurface), modifier = Modifier.padding(bottom = 10.dp))
                    }
                    Text("替换后来源将记为资源库。插件包含可执行代码，请确认来源可信。", color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                }
                Text(if (skinReplacement != null) "将用资源库版本替换「${entry.item.name}」。" else
                    state.tasks[entry.item.id]?.message?.takeIf { state.tasks[entry.item.id]?.conflict == true }
                        ?: "本地内容已修改。替换会更新资源内容，个人配置仍会保留。",
                    color = Color(NgTheme.colors.onSurface), fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun ResourcePresetConflictAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secondary: Boolean = false,
) {
    val primary = Color(NgTheme.colors.primary)
    val content = if (secondary) Color(NgTheme.colors.onSurfaceVariant) else primary
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, color = content.let { if (enabled) it else it.copy(alpha = .45f) },
                fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun versionLabel(entry: ResourceEntry, state: ResourceCenterState): String =
    runCatching { state.versionLabel(entry) }.getOrDefault("来源身份异常")

private fun statusLabel(entry: ResourceEntry, state: ResourceCenterState): String {
    val id = entry.item.id
    if (state.tasks[id]?.readPresetConflict != null || state.tasks[id]?.widgetSkinConflict != null) return "待选择"
    state.tasks[id]?.let { if (it.running || it.message != "已加入资源库") return it.message }
    if (state.isLocalWidgetSkin(entry)) return "本地已导入"
    if (state.localStates[id] == ResourceLocalState.MODIFIED) return "本地已修改"
    if (state.hasInstalled(entry) && state.sourceChecked(entry) && state.snapshot?.entries.orEmpty().none { it.item.id == id }) {
        return "来源已移除"
    }
    if (state.confirmedEntry(entry) == null) return if (state.hasInstalled(entry)) "已下载" else "待同步"
    return versionLabel(entry, state)
}

private fun actionLabel(entry: ResourceEntry, state: ResourceCenterState): String {
    val id = entry.item.id
    val task = state.tasks[id]
    if (task?.running == true) return if (task.message == "正在入库") "正在入库" else "取消下载"
    if (state.confirmedEntry(entry) == null) return if (canUseLocalResource(entry, state)) useLabel(entry) else "待同步"
    if (task?.widgetSkinConflict != null) return "处理冲突"
    if (state.isLocalWidgetSkin(entry)) return "下载"
    if (task?.conflict == true || state.isLocalPetChange(entry) || state.localStates[id] == ResourceLocalState.MODIFIED) return "处理冲突"
    if (!state.hasInstalled(entry)) return "下载"
    return if (versionLabel(entry, state) == "可更新") "更新" else useLabel(entry)
}

private fun useLabel(entry: ResourceEntry) = if (entry.category.type == "tts") "去配置" else "去使用"

private fun canUseLocalResource(entry: ResourceEntry, state: ResourceCenterState) =
    if (entry.category.type == "pet" || entry.category.type == HOME_WIDGET_SKIN_TYPE) state.hasInstalled(entry)
    else state.localStates[entry.item.id] == ResourceLocalState.PRESENT

private fun canAct(entry: ResourceEntry, state: ResourceCenterState): Boolean {
    if (state.storageError != null || state.tasks[entry.item.id]?.message == "正在入库") return false
    if (state.tasks[entry.item.id]?.running == true) return true
    if (state.confirmedEntry(entry) == null) return canUseLocalResource(entry, state)
    if (state.isLocalWidgetSkin(entry)) return ResourceProtocol.incompatibility(entry, BuildConfig.VERSION_CODE.toLong()) == null
    if (entry.category.type == "pet" && state.hasInstalled(entry) && versionLabel(entry, state) != "可更新") return true
    if (entry.category.type == "pet" && ResourceProtocol.incompatibility(entry, BuildConfig.VERSION_CODE.toLong()) != null) return false
    if (state.localStates[entry.item.id] == ResourceLocalState.PRESENT && versionLabel(entry, state) != "可更新") return true
    return ResourceProtocol.incompatibility(entry, BuildConfig.VERSION_CODE.toLong()) == null &&
        versionLabel(entry, state) !in listOf("来源版本异常", "来源身份异常", "本地版本较新")
}

@Composable
private fun ResourceCard(entry: ResourceEntry, state: ResourceCenterState, grid: Boolean, onOpen: () -> Unit, onAction: () -> Unit) {
    NgSettingsCardSurface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        appearance = NgSettingsItemAppearance.SURFACE_CARD, cornerRadius = 16.dp,
    ) {
        if (grid) {
            Column(Modifier.padding(10.dp)) {
                ResourcePreview(entry, state, Modifier.fillMaxWidth().height(88.dp), variant = ResourcePreviewVariant.COMPACT_GRID)
                Text(entry.item.name, Modifier.padding(top = 8.dp), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                    color = Color(NgTheme.colors.onSurface), maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                ResourceSummary(entry, state, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.End) {
                    ResourceCardAction(entry, state, onAction)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ResourcePreview(entry, state, Modifier.size(56.dp), variant = ResourcePreviewVariant.COMPACT_LIST)
                Column(Modifier.weight(1f).padding(start = 10.dp, end = 8.dp)) {
                    Text(entry.item.name, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = Color(NgTheme.colors.onSurface),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    ResourceSummary(entry, state)
                }
                Box(Modifier.width(0.5.dp).height(24.dp).background(Color(NgTheme.colors.onSurface).copy(alpha = .12f)))
                ResourceCardAction(entry, state, onAction)
            }
        }
        // 预留进度槽，下载开始/结束时不改变卡片与同排底边的位置。
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            state.tasks[entry.item.id]?.takeIf { it.running }?.progress?.let {
                LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxSize(), color = Color(NgTheme.colors.primary))
            }
        }
    }
}

@Composable
private fun ResourceSummary(
    entry: ResourceEntry,
    state: ResourceCenterState,
    modifier: Modifier = Modifier,
) {
    val size = Formatter.formatShortFileSize(LocalContext.current, entry.item.payload.size)
    val status = statusLabel(entry, state)
    val metadata = buildAnnotatedString {
        append(size)
        if (status != "未下载") {
            append("  |  ")
            withStyle(SpanStyle(color = Color(NgTheme.colors.primary))) { append(status) }
        }
    }
    Text(metadata, modifier = modifier, fontSize = 12.sp, lineHeight = 16.sp, color = Color(NgTheme.colors.onSurfaceVariant),
        maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun ResourceCardAction(entry: ResourceEntry, state: ResourceCenterState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val action = actionLabel(entry, state)
    val enabled = canAct(entry, state)
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.widthIn(min = 48.dp).heightIn(min = 48.dp)
            .semantics { contentDescription = "$action ${entry.item.name}" },
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Text(
            text = when (action) { "去使用" -> "使用"; "去配置" -> "配置"; else -> action },
            fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1,
            color = Color(NgTheme.colors.primary).let { if (enabled) it else it.copy(alpha = .45f) },
        )
    }
}

private enum class ResourcePreviewVariant { DEFAULT, COMPACT_LIST, COMPACT_GRID }

private data class ResourcePreviewState(val bitmap: ImageBitmap? = null, val loading: Boolean = false)

@Composable
private fun ResourcePreview(
    entry: ResourceEntry,
    state: ResourceCenterState,
    modifier: Modifier = Modifier,
    reading: Boolean = false,
    variant: ResourcePreviewVariant = ResourcePreviewVariant.DEFAULT,
) {
    val font = entry.category.type == "font"
    val widgetSkin = entry.category.type == HOME_WIDGET_SKIN_TYPE
    val catalogImage = font || entry.category.type in setOf("pet", "read_preset")
    val image = entry.item.preview?.let { if (reading) it.detail else it.thumbnail }
    val allowDownload = state.confirmedEntry(entry) != null
    val target = state.installed(entry.item.id)?.target
    val sourceKey = if (catalogImage) listOf(entry.catalogCommit, image) else
        listOf(entry.item.payload.sha256, target, state.localStates[entry.item.id], state.widgetSkins[entry.item.id]?.revision)
    val preview by key(entry.category.type, sourceKey, reading) {
        produceState(
            ResourcePreviewState(loading = catalogImage && image != null && allowDownload),
            if (catalogImage) state.snapshot?.checkedAt else null,
            allowDownload,
        ) {
            if (catalogImage && value.bitmap != null) return@produceState
            if (catalogImage) value = ResourcePreviewState(loading = image != null && allowDownload)
            val bitmap = withContext(Dispatchers.IO) {
                if (widgetSkin) {
                    if (state.widgetSkins[entry.item.id]?.revision == entry.item.payload.sha256) {
                        ResourceRepository.homeWidgetSkinPreview(entry.item.id, reading)?.asImageBitmap()
                    } else null
                } else if (catalogImage) {
                    ResourceRepository.catalogPreview(entry, detail = reading, allowDownload = allowDownload)?.asImageBitmap()
                } else {
                    ResourceRepository.backgroundPreview(entry, allowDownload = allowDownload)?.let { file ->
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.path, bounds)
                        val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / if (reading) 1024 else 384)
                        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
                    }
                }
            }
            value = ResourcePreviewState(bitmap)
        }
    }
    val compact = variant != ResourcePreviewVariant.DEFAULT
    val whiteFontDetail = font && reading
    val fontColor = if (whiteFontDetail) Color(0xFF202124) else Color(NgTheme.colors.onSurface)
    val fontPlaceholderColor = if (whiteFontDetail) Color(0xFF5F6368) else Color(NgTheme.colors.onSurfaceVariant)
    val previewColor = if (reading && entry.category.type == "pet") {
        Color.Transparent
    } else if (whiteFontDetail) Color.White else if (compact && !NgTheme.snapshot.isEInk) {
        lerp(Color(NgTheme.colors.surfaceContainer), ngDrawerContentCardColor(), .35f)
    } else Color(NgTheme.colors.surfaceContainer)
    Box(modifier.clip(RoundedCornerShape(if (compact) 10.dp else 12.dp)).background(previewColor), contentAlignment = Alignment.Center) {
        if (preview.bitmap != null) {
            Image(preview.bitmap!!, entry.item.name, Modifier.fillMaxSize(),
                contentScale = if (catalogImage || widgetSkin) ContentScale.Fit else ContentScale.Crop,
                colorFilter = if (font) ColorFilter.tint(fontColor) else null)
            if (reading && entry.category.type == "background") Text("翻开一页\n\n让文字安静下来\n\n在字里行间，慢慢阅读。",
                Modifier.padding(24.dp), color = Color(0xFF302C26), fontSize = 18.sp)
        } else if (catalogImage) {
            if (preview.loading) {
                NgDotsLoadingIndicator(Modifier.size(if (reading) 32.dp else 20.dp))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (font) Icons.Rounded.TextFields else Icons.Rounded.Image, "暂无预览", Modifier.size(if (reading) 32.dp else 24.dp),
                        tint = fontPlaceholderColor)
                    if (reading) Text("暂无预览", Modifier.padding(top = 6.dp), fontSize = 13.sp,
                        color = fontPlaceholderColor)
                }
            }
        } else {
            Icon(when (entry.category.type) {
                "theme" -> Icons.Rounded.Palette
                "tts" -> Icons.Rounded.GraphicEq
                else -> Icons.Rounded.Image
            }, entry.category.name, Modifier.size(when {
                reading -> 64.dp
                variant == ResourcePreviewVariant.COMPACT_LIST -> 28.dp
                else -> 36.dp
            }), tint = Color(NgTheme.colors.primary))
        }
    }
}

@Composable
private fun ResourceDetail(entry: ResourceEntry, state: ResourceCenterState, onAction: (ResourceEntry) -> Unit, onViewLatest: (ResourceEntry) -> Unit, onUninstall: (String) -> Unit, modifier: Modifier) {
    val incompatible = ResourceProtocol.incompatibility(entry, BuildConfig.VERSION_CODE.toLong())
    val latest = state.snapshot?.entries?.firstOrNull {
        it.item.id == entry.item.id && it.item.versionCode > entry.item.versionCode
    }
    val isTheme = entry.category.type == "theme"
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 24.dp)) {
            if (!isTheme) {
                val previewHeight = if (entry.category.type == "font" || entry.category.type == "tts") 140.dp else 280.dp
                ResourcePreview(entry, state, Modifier.fillMaxWidth().height(previewHeight), reading = true)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = if (isTheme) 0.dp else 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isTheme) {
                    ResourcePreview(entry, state, Modifier.size(48.dp), variant = ResourcePreviewVariant.COMPACT_LIST)
                }
                Column(Modifier.weight(1f)) {
                    Text(entry.item.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(NgTheme.colors.onSurface))
                    if (entry.item.author.isNotBlank()) {
                        Text(entry.item.author, Modifier.padding(top = 6.dp), fontSize = 14.sp, color = Color(NgTheme.colors.onSurfaceVariant))
                    }
                }
            }
            if (entry.item.description.isNotBlank()) Text(entry.item.description, Modifier.padding(vertical = 12.dp), fontSize = 14.sp, color = Color(NgTheme.colors.onSurfaceVariant))
            DetailLine("分类", entry.category.name)
            DetailLine("版本", entry.item.displayVersion)
            DetailLine("大小", Formatter.formatShortFileSize(LocalContext.current, entry.item.payload.size))
            DetailLine("发布时间", ResourceDateTime.format(entry.item.publishedAt))
            DetailLine("更新时间", ResourceDateTime.format(entry.item.updatedAt))
            if (entry.category.type == "pet") {
                state.pet(entry)?.let { DetailLine("本地版本", it.versionLabel()) }
            } else state.installed(entry.item.id)?.let { DetailLine("本地版本", it.entry.item.displayVersion) }
            DetailLine("状态", statusLabel(entry, state))
            incompatible?.let { Text(it, Modifier.padding(top = 12.dp), fontSize = 14.sp, color = Color(NgTheme.colors.error)) }
            entry.item.changelog?.takeIf { it.isNotBlank() }?.let {
                Text("更新说明", Modifier.padding(top = 12.dp), fontWeight = FontWeight.Medium, color = Color(NgTheme.colors.onSurface))
                Text(it, fontSize = 14.sp, color = Color(NgTheme.colors.onSurfaceVariant))
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp)) {
            state.tasks[entry.item.id]?.takeIf { it.running }?.progress?.let {
                LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), color = Color(NgTheme.colors.primary))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NgButton(
                    onClick = { if (latest != null) onViewLatest(latest) else onAction(entry) },
                    enabled = latest != null || canAct(entry, state),
                    modifier = Modifier.weight(1f).height(48.dp),
                    variant = NgButtonVariant.PRIMARY_LIGHT_CONTENT,
                ) { Text(if (latest != null) "查看新版" else actionLabel(entry, state), fontSize = 16.sp) }
                if (entry.category.type == "pet" && state.hasInstalled(entry)) {
                    NgButton(
                        onClick = { state.pet(entry)?.id?.let(onUninstall) },
                        enabled = state.tasks[entry.item.id]?.running != true,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) { Text("卸载插件", fontSize = 16.sp) }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    HorizontalDivider(color = Color(NgTheme.colors.onSurface).copy(alpha = 0.08f))
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.width(72.dp), color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 14.sp)
        Text(value, Modifier.weight(1f), color = Color(NgTheme.colors.onSurface), fontSize = 14.sp)
    }
}

