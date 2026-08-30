package io.legado.app.ui.book.character

import android.net.Uri
import android.os.Bundle
import androidx.activity.addCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookTtsCastRole
import io.legado.app.help.ai.AiTtsStoryboardHelper
import io.legado.app.help.config.AppConfig
import io.legado.app.help.tts.ReadAloudTtsRouter
import io.legado.app.help.tts.BookTtsCastingCoordinator
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.help.tts.TtsPlayerFactory
import io.legado.app.help.tts.TtsScriptEngineClient
import io.legado.app.help.tts.TtsSpeedPolicy
import io.legado.app.help.tts.normalizeStoryboardSynthesisText
import io.legado.app.help.tts.toTtsSynthesisContext
import io.legado.app.help.tts.forEngineCapabilities
import io.legado.app.help.tts.writeReadAloudAudioWithWavRetry
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import splitties.init.appCtx

class BookStoryboardActivity : BaseActivity<ComposeActivityBinding>() {

    override val binding by viewBinding(ComposeActivityBinding::inflate)
    private lateinit var workKey: String
    private var bookName: String = ""
    private var bookAuthor: String = ""
    private var previewJob: Job? = null
    private var storyboardJob: Job? = null
    private var renderJob: Job? = null
    private var previewPlayer: ExoPlayer? = null
    private var showingCacheList by mutableStateOf(true)
    private var cacheRows by mutableStateOf<List<StoryboardCacheRow>>(emptyList())
    private var cacheLoading by mutableStateOf(false)
    private var cacheLoadingMessage by mutableStateOf("正在加载分镜缓存…")
    private var cacheErrorMessage by mutableStateOf<String?>(null)
    private var cacheSelectionMode by mutableStateOf(false)
    private var selectedCacheChapterIndexes by mutableStateOf<Set<Int>>(emptySet())
    private var detailChapterTitle by mutableStateOf("")
    private var detailSummary by mutableStateOf<StoryboardDetailSummaryUi?>(null)
    private var detailChapterIndex: Int? = null
    private var renderedDetailChapterIndex: Int? = null
    private var detailRefreshAllowed by mutableStateOf(false)
    private var detailLoading by mutableStateOf(false)
    private var detailLoadingMessage by mutableStateOf("正在生成 AI 分镜…")
    private var detailErrorMessage by mutableStateOf<String?>(null)
    private var detailScenes by mutableStateOf<List<StoryboardSceneUi>>(emptyList())
    private var expandedSceneIndexes by mutableStateOf<Set<Int>>(emptySet())
    private var expandedSegmentKeys by mutableStateOf<Set<String>>(emptySet())
    private var confirmationRequest by mutableStateOf<StoryboardConfirmationRequest?>(null)

    private data class StoryboardConfirmationRequest(
        val title: String,
        val message: String,
        val destructive: Boolean,
        val onConfirm: () -> Unit,
    )

    private data class StoryboardCacheRow(
        val chapterIndex: Int,
        val chapterTitle: String,
        val entry: AiTtsStoryboardHelper.CachedStoryboardEntry?,
        val isCurrent: Boolean
    )

    private data class StoryboardRenderState(
        val chapterTitle: String,
        val chapterIndex: Int,
        val scenes: List<StoryboardScene>,
        val summary: StoryboardDetailSummaryUi,
        val currentParagraphIndex: Int?
    )

    private data class StoryboardRoutingState(
        val scenes: List<StoryboardSceneUi>,
    )

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        bookName = intent.getStringExtra(BookCharacterActivity.EXTRA_BOOK_NAME).orEmpty()
        bookAuthor = intent.getStringExtra(BookCharacterActivity.EXTRA_BOOK_AUTHOR).orEmpty()
        workKey = intent.getStringExtra(BookCharacterActivity.EXTRA_WORK_KEY)
            ?: BookCharacterProfile.workKey(bookName, bookAuthor)
        onBackPressedDispatcher.addCallback(this) { navigateBack() }
        initContent()
        loadCacheList()
    }

    private fun initContent() {
        binding.composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        binding.composeView.setContent {
            NgAppTheme {
                if (showingCacheList) {
                    BookStoryboardCacheScreen(
                        rows = cacheRows.map { row ->
                            StoryboardCacheUiRow(
                                chapterIndex = row.chapterIndex,
                                chapterNumber = row.chapterTitle.storyboardChapterNumber(row.chapterIndex),
                                title = row.chapterTitle.storyboardArabicChapterTitle(row.chapterIndex),
                                meta = row.entry?.storyboard?.let { storyboard ->
                                    "${storyboard.scenes.size} 个场景 · ${storyboard.segmentCount} 个片段"
                                } ?: "尚未生成",
                                isCurrent = row.isCurrent,
                                deletable = row.entry != null,
                            )
                        },
                        loading = cacheLoading,
                        loadingMessage = cacheLoadingMessage,
                        errorMessage = cacheErrorMessage,
                        selectionMode = cacheSelectionMode,
                        selectedChapterIndexes = selectedCacheChapterIndexes,
                        onBack = ::navigateBack,
                        onExitSelection = ::exitCacheSelection,
                        onEnterSelection = { enterCacheSelection() },
                        onEnterSelectionWithChapter = { chapterIndex ->
                            enterCacheSelection(chapterIndex)
                        },
                        onToggleSelection = ::toggleCacheSelection,
                        onSelectAll = ::selectAllCachedStoryboards,
                        onInvertSelection = ::invertCachedStoryboardSelection,
                        onDeleteSelected = ::confirmDeleteSelectedStoryboards,
                        onRowClick = ::openCacheRow,
                        onDeleteRequested = ::requestDeleteCachedStoryboard,
                    )
                } else {
                    BookStoryboardDetailScreen(
                        chapterTitle = detailChapterTitle,
                        summary = detailSummary,
                        scenes = detailScenes,
                        expandedSceneIndexes = expandedSceneIndexes,
                        expandedSegmentKeys = expandedSegmentKeys,
                        refreshEnabled = detailRefreshAllowed,
                        loading = detailLoading,
                        loadingMessage = detailLoadingMessage,
                        errorMessage = detailErrorMessage,
                        onBack = ::navigateBack,
                        onRefresh = ::confirmRegenerateStoryboard,
                        onToggleScene = ::toggleScene,
                        onToggleSegmentDetails = ::toggleSegmentDetails,
                        onPreview = ::previewStoryboardSegment,
                    )
                }
                confirmationRequest?.let { request ->
                    BookStoryboardConfirmationDialog(
                        title = request.title,
                        message = request.message,
                        destructive = request.destructive,
                        onDismiss = { confirmationRequest = null },
                        onConfirm = {
                            confirmationRequest = null
                            request.onConfirm()
                        },
                    )
                }
            }
        }
    }

    private fun navigateBack() {
        if (!showingCacheList) {
            stopPreview()
            loadCacheList()
        } else {
            finish()
        }
    }

    private fun loadCacheList() {
        showingCacheList = true
        detailChapterIndex = null
        exitCacheSelection()
        stopPreview()
        renderJob?.cancel()
        renderJob = null
        cacheRows = emptyList()
        cacheErrorMessage = null
        setLoading(true, "正在加载分镜缓存…")
        storyboardJob?.cancel()
        storyboardJob = lifecycleScope.launch {
            val result = withContext(IO) {
                runCatching {
                    val book = ReadBook.book ?: error("当前书籍为空")
                    val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
                    val characters = appDb.bookCharacterDao.getCharacters(workKey)
                    val cached = AiTtsStoryboardHelper.listCachedStoryboards(book, chapters, characters)
                    val currentIndex = ReadBook.durChapterIndex
                    val currentTitle = ReadBook.curTextChapter?.title
                        ?: chapters.firstOrNull { it.index == currentIndex }?.title
                        ?: "当前章节"
                    val rows = cached.map { entry ->
                        StoryboardCacheRow(
                            chapterIndex = entry.chapterIndex,
                            chapterTitle = entry.chapterTitle,
                            entry = entry,
                            isCurrent = entry.chapterIndex == currentIndex
                        )
                    }.toMutableList()
                    if (rows.none { it.isCurrent }) {
                        rows += StoryboardCacheRow(
                            chapterIndex = currentIndex,
                            chapterTitle = currentTitle,
                            entry = null,
                            isCurrent = true
                        )
                    }
                    rows.sortedWith(
                        compareByDescending<StoryboardCacheRow> { it.isCurrent }
                            .thenByDescending { it.chapterIndex }
                    )
                }
            }
            result
                .onSuccess { rows ->
                    setLoading(false)
                    cacheRows = rows
                    cacheErrorMessage = null
                }
                .onFailure { renderEmpty("分镜缓存加载失败：${it.localizedMessage ?: "未知错误"}") }
            storyboardJob = null
        }
    }

    private fun enterCacheSelection(chapterIndex: Int? = null) {
        cacheSelectionMode = true
        selectedCacheChapterIndexes = chapterIndex
            ?.takeIf { index -> cacheRows.any { it.chapterIndex == index && it.entry != null } }
            ?.let { index -> setOf(index) }
            ?: emptySet()
    }

    private fun exitCacheSelection() {
        cacheSelectionMode = false
        selectedCacheChapterIndexes = emptySet()
    }

    private fun toggleCacheSelection(chapterIndex: Int) {
        if (cacheRows.none { it.chapterIndex == chapterIndex && it.entry != null }) return
        selectedCacheChapterIndexes = if (chapterIndex in selectedCacheChapterIndexes) {
            selectedCacheChapterIndexes - chapterIndex
        } else {
            selectedCacheChapterIndexes + chapterIndex
        }
    }

    private fun selectAllCachedStoryboards() {
        selectedCacheChapterIndexes = cacheRows
            .filter { it.entry != null }
            .mapTo(linkedSetOf()) { it.chapterIndex }
    }

    private fun invertCachedStoryboardSelection() {
        val selectableIndexes = cacheRows
            .filter { it.entry != null }
            .mapTo(linkedSetOf()) { it.chapterIndex }
        selectedCacheChapterIndexes = selectableIndexes - selectedCacheChapterIndexes
    }

    private fun confirmDeleteSelectedStoryboards() {
        val rows = cacheRows.filter { row ->
            row.chapterIndex in selectedCacheChapterIndexes && row.entry != null
        }
        if (rows.isEmpty()) return
        confirmationRequest = StoryboardConfirmationRequest(
            title = getString(R.string.book_storyboard_batch_delete_title, rows.size),
            message = getString(R.string.book_storyboard_batch_delete_message, rows.size),
            destructive = true,
            onConfirm = {
                exitCacheSelection()
                deleteCachedStoryboards(rows)
            },
        )
    }

    private fun openCacheRow(chapterIndex: Int) {
        val row = cacheRows.firstOrNull { it.chapterIndex == chapterIndex } ?: return
        row.entry?.let { entry ->
            showStoryboard(entry.storyboard, row.chapterIndex)
        } ?: run {
            if (row.isCurrent) loadStoryboard()
        }
    }

    private fun requestDeleteCachedStoryboard(chapterIndex: Int) {
        val row = cacheRows.firstOrNull { it.chapterIndex == chapterIndex } ?: return
        if (row.entry == null) return
        confirmDeleteCachedStoryboard(row)
    }

    private fun String.storyboardChapterNumber(fallbackIndex: Int): String {
        return STORYBOARD_CHAPTER_PREFIX.find(this)
            ?.groups
            ?.get(1)
            ?.value
            ?.storyboardArabicNumber()
            ?: (fallbackIndex + 1).toString()
    }

    private fun String.storyboardArabicChapterTitle(fallbackIndex: Int): String {
        val numberGroup = STORYBOARD_CHAPTER_PREFIX.find(this)
            ?.groups
            ?.get(1)
            ?: return this
        val number = numberGroup.value.storyboardArabicNumber()
            ?: (fallbackIndex + 1).toString()
        return replaceRange(numberGroup.range, number)
    }

    private fun String.storyboardArabicNumber(): String? {
        val normalized = map { char ->
            if (char in '０'..'９') {
                ('0'.code + char.code - '０'.code).toChar()
            } else {
                char
            }
        }.joinToString("")
        normalized.toLongOrNull()?.let { return it.toString() }

        fun digitOf(char: Char): Long? = when (char) {
            '零', '〇' -> 0L
            '一' -> 1L
            '二', '两' -> 2L
            '三' -> 3L
            '四' -> 4L
            '五' -> 5L
            '六' -> 6L
            '七' -> 7L
            '八' -> 8L
            '九' -> 9L
            in '0'..'9' -> char.digitToInt().toLong()
            else -> null
        }

        val hasUnit = normalized.any { it == '十' || it == '百' || it == '千' || it == '万' }
        if (!hasUnit) {
            var value = 0L
            normalized.forEach { char ->
                val digit = digitOf(char) ?: return null
                value = value * 10L + digit
            }
            return value.toString()
        }

        var total = 0L
        var section = 0L
        var currentDigit: Long? = null
        normalized.forEach { char ->
            digitOf(char)?.let { digit ->
                currentDigit = digit
                return@forEach
            }
            when (char) {
                '十', '百', '千' -> {
                    val unit = when (char) {
                        '十' -> 10L
                        '百' -> 100L
                        else -> 1_000L
                    }
                    section += (currentDigit ?: 1L) * unit
                    currentDigit = null
                }
                '万' -> {
                    section += currentDigit ?: 0L
                    total += (if (section == 0L) 1L else section) * 10_000L
                    section = 0L
                    currentDigit = null
                }
                else -> return null
            }
        }
        return (total + section + (currentDigit ?: 0L)).toString()
    }

    private fun confirmRegenerateStoryboard() {
        if (storyboardJob?.isActive == true) return
        confirmationRequest = StoryboardConfirmationRequest(
            title = getString(R.string.book_storyboard_regenerate),
            message = getString(R.string.book_storyboard_regenerate_message),
            destructive = false,
            onConfirm = { loadStoryboard(forceRegenerate = true) },
        )
    }

    private fun loadStoryboard(forceRegenerate: Boolean = false) {
        val chapter = ReadBook.curTextChapter
        val content = chapter?.let { AiTtsStoryboardHelper.readAloudContentFromChapter(it) }.orEmpty()
        if (chapter == null || content.isBlank()) {
            renderEmpty(getString(R.string.book_storyboard_empty))
            return
        }
        showingCacheList = false
        detailChapterIndex = ReadBook.durChapterIndex
        detailChapterTitle = chapter.title
        detailSummary = null
        detailRefreshAllowed = true
        detailScenes = emptyList()
        detailErrorMessage = null
        expandedSceneIndexes = emptySet()
        expandedSegmentKeys = emptySet()
        if (forceRegenerate) stopPreview()
        renderJob?.cancel()
        renderJob = null
        setLoading(true, "正在生成 AI 分镜…")
        storyboardJob?.cancel()
        storyboardJob = lifecycleScope.launch {
            val result = withContext(IO) {
                val characters = appDb.bookCharacterDao.getCharacters(workKey)
                val book = ReadBook.book ?: return@withContext Result.failure<Pair<ChapterStoryboard, List<BookCharacter>>>(
                    IllegalStateException("当前书籍为空")
                )
                runCatching {
                    if (forceRegenerate) {
                        AiTtsStoryboardHelper.regenerate(
                            book = book,
                            chapterIndex = ReadBook.durChapterIndex,
                            chapterTitle = chapter.title,
                            content = content,
                            characters = characters
                        )
                    } else {
                        AiTtsStoryboardHelper.getOrGenerate(
                            book = book,
                            chapterIndex = ReadBook.durChapterIndex,
                            chapterTitle = chapter.title,
                            content = content,
                            characters = characters
                        )
                    } to characters
                }
            }
            result
                .onSuccess {
                    showStoryboard(it.first, ReadBook.durChapterIndex)
                    if (forceRegenerate) {
                        setResult(RESULT_OK)
                        if (BaseReadAloudService.isRun && AppConfig.readAloudMultiRole) {
                            ReadAloud.refreshTtsRoute(this@BookStoryboardActivity)
                        }
                    }
                }
                .onFailure { renderEmpty("AI 分镜生成失败：${it.localizedMessage ?: "未知错误"}") }
            storyboardJob = null
        }
    }

    private fun renderEmpty(message: String) {
        setLoading(false)
        if (showingCacheList) {
            cacheRows = emptyList()
            cacheErrorMessage = message
            return
        }
        detailChapterTitle = detailChapterTitle.ifBlank {
            ReadBook.curTextChapter?.title ?: getString(R.string.book_storyboard)
        }
        detailSummary = null
        detailScenes = emptyList()
        detailErrorMessage = message
    }

    private fun showStoryboard(storyboard: ChapterStoryboard, chapterIndex: Int) {
        showingCacheList = false
        detailChapterIndex = chapterIndex
        if (storyboard.scenes.isEmpty()) {
            renderEmpty(getString(R.string.book_storyboard_empty))
            return
        }
        detailChapterTitle = storyboard.chapterTitle
        detailSummary = null
        detailRefreshAllowed = chapterIndex == ReadBook.durChapterIndex
        detailErrorMessage = null
        renderJob?.cancel()
        renderJob = null
        runCatching { prepareStoryboardRenderState(storyboard, chapterIndex) }
            .onSuccess { state ->
                renderStoryboard(state)
                loadStoryboardRoutingState(
                    chapterIndex = chapterIndex,
                    chapterTitle = state.chapterTitle,
                    scenes = state.scenes,
                )
            }
            .onFailure {
                if (it !is CancellationException) {
                    renderEmpty("分镜加载失败：${it.localizedMessage ?: "未知错误"}")
                }
            }
    }

    private fun prepareStoryboardRenderState(
        storyboard: ChapterStoryboard,
        chapterIndex: Int
    ): StoryboardRenderState {
        val scenes = storyboard.scenes.map { scene ->
            scene.copy(segments = scene.segments.filterNot { it.isChapterTitleSegment(storyboard.chapterTitle) })
        }.filter { it.segments.isNotEmpty() }
        val identityKeysByName = buildMap {
            storyboard.identityLinks.forEach { link ->
                val identityKey = link.characterId
                    ?.takeIf { it > 0L }
                    ?.let { "character:$it" }
                    ?: link.castRoleId
                        ?.takeIf { it > 0L }
                        ?.let { "cast:$it" }
                if (identityKey != null) {
                    link.aliasName.normalizedStoryboardPersonName()?.let { name ->
                        putIfAbsent(name, identityKey)
                    }
                }
            }
            scenes.forEach { scene ->
                scene.segments.forEach { segment ->
                    val identityKey = segment.speakerId
                        ?.takeIf { it > 0L }
                        ?.let { "character:$it" }
                        ?: segment.castRoleId
                            ?.takeIf { it > 0L }
                            ?.let { "cast:$it" }
                    if (identityKey != null) {
                        segment.speakerName?.normalizedStoryboardPersonName()?.let { name ->
                            putIfAbsent(name, identityKey)
                        }
                    }
                }
            }
        }
        val participantKeys = buildSet {
            scenes.forEach { scene ->
                scene.characters.forEach { name ->
                    name.normalizedStoryboardPersonName()?.let { normalizedName ->
                        add(identityKeysByName[normalizedName] ?: "name:$normalizedName")
                    }
                }
                scene.segments.forEach { segment ->
                    if (segment.type != StoryboardSegmentType.NARRATION) {
                        val participantKey = segment.speakerId
                            ?.takeIf { it > 0L }
                            ?.let { "character:$it" }
                            ?: segment.castRoleId
                                ?.takeIf { it > 0L }
                                ?.let { "cast:$it" }
                            ?: segment.speakerName
                                ?.normalizedStoryboardPersonName()
                                ?.let { identityKeysByName[it] ?: "name:$it" }
                        participantKey?.let(::add)
                    }
                }
            }
        }
        return StoryboardRenderState(
            chapterTitle = storyboard.chapterTitle,
            chapterIndex = chapterIndex,
            scenes = scenes,
            summary = StoryboardDetailSummaryUi(
                sceneCount = scenes.size,
                segmentCount = scenes.sumOf { it.segments.size },
                dialogueCount = scenes.sumOf { scene ->
                    scene.segments.count { it.type == StoryboardSegmentType.DIALOGUE }
                },
                personCount = participantKeys.size,
            ),
            currentParagraphIndex = currentParagraphIndex(chapterIndex)
        )
    }

    private fun loadStoryboardRoutingState(
        chapterIndex: Int,
        chapterTitle: String,
        scenes: List<StoryboardScene>,
    ) {
        renderJob = lifecycleScope.launch {
            val result = withContext(IO) {
                runCatching { prepareStoryboardRoutingState(chapterTitle, scenes) }
            }
            if (!showingCacheList && detailChapterIndex == chapterIndex) {
                result.onSuccess(::renderStoryboardRoutingState)
            }
            renderJob = null
        }
    }

    private fun prepareStoryboardRoutingState(
        chapterTitle: String,
        scenes: List<StoryboardScene>,
    ): StoryboardRoutingState {
        val characters = appDb.bookCharacterDao.getCharacters(workKey)
        val castRoles = appDb.bookCharacterDao.getTtsCastRoles(workKey)
            .filter { it.linkedCharacterId == null && it.isRoutableRole() }
        val canonicalCharacterIds = characters.mapTo(mutableSetOf()) { it.id }
        val stableCastRoleIds = castRoles.filter { it.isVisibleTemporaryRole() }
            .mapTo(mutableSetOf()) { it.id }
        val pendingCastRoleIds = castRoles.filter {
            it.identityState == BookTtsCastRole.IdentityState.PENDING
        }.mapTo(mutableSetOf()) { it.id }
        val castRoleNames = castRoles.filter { it.isVisibleTemporaryRole() }
            .flatMap { role ->
                buildList {
                    add(role.name)
                    GSON.fromJsonObject<List<String>>(role.aliasesJson).getOrNull().orEmpty().forEach(::add)
                }
            }
            .map(BookTtsCastingCoordinator::normalizeIdentityName)
            .toSet()
        val router = ReadBook.book?.let { ReadAloudTtsRouter.create(it) }
        val baseEngine = currentBaseEngine()
        return StoryboardRoutingState(
            scenes = buildStoryboardSceneUi(
                scenes = scenes,
                chapterTitle = chapterTitle,
                canonicalCharacterIds = canonicalCharacterIds,
                stableCastRoleIds = stableCastRoleIds,
                pendingCastRoleIds = pendingCastRoleIds,
                castRoleNames = castRoleNames,
                router = router,
                baseEngine = baseEngine,
            ),
        )
    }

    private fun renderStoryboardRoutingState(state: StoryboardRoutingState) {
        detailScenes = state.scenes
    }

    private fun renderStoryboard(state: StoryboardRenderState) {
        setLoading(false)
        detailChapterTitle = state.chapterTitle
        detailSummary = state.summary
        detailRefreshAllowed = state.chapterIndex == ReadBook.durChapterIndex
        if (state.scenes.isEmpty()) {
            renderEmpty(getString(R.string.book_storyboard_empty))
            return
        }
        detailScenes = buildStoryboardSceneUi(
            scenes = state.scenes,
            chapterTitle = state.chapterTitle,
        )
        detailErrorMessage = null
        val sceneIndexes = state.scenes.mapTo(linkedSetOf()) { it.index }
        val chapterChanged = renderedDetailChapterIndex != state.chapterIndex
        if (chapterChanged) {
            expandedSceneIndexes = emptySet()
            expandedSegmentKeys = emptySet()
        } else {
            expandedSceneIndexes = expandedSceneIndexes.intersect(sceneIndexes)
        }
        if (expandedSceneIndexes.isEmpty()) {
            val initialScene = state.currentParagraphIndex?.let { paragraphIndex ->
                state.scenes.firstOrNull { scene ->
                    scene.segments.any { it.paragraphIndex == paragraphIndex }
                }
            } ?: state.scenes.firstOrNull()
            initialScene?.let { expandedSceneIndexes = setOf(it.index) }
        }
        renderedDetailChapterIndex = state.chapterIndex
    }

    private fun setLoading(loading: Boolean, message: String = "正在生成 AI 分镜…") {
        if (showingCacheList) {
            cacheLoading = loading
            cacheLoadingMessage = message
            if (loading) cacheErrorMessage = null
            return
        }
        detailLoading = loading
        if (loading) {
            detailLoadingMessage = message
            detailErrorMessage = null
        }
    }

    private fun confirmDeleteCachedStoryboard(row: StoryboardCacheRow) {
        confirmationRequest = StoryboardConfirmationRequest(
            title = getString(R.string.book_storyboard_delete_title),
            message = getString(R.string.book_storyboard_delete_message, row.chapterTitle),
            destructive = true,
            onConfirm = { deleteCachedStoryboard(row) },
        )
    }

    private fun deleteCachedStoryboard(row: StoryboardCacheRow) {
        val entry = row.entry ?: return
        lifecycleScope.launch(IO) {
            val result = runCatching { AiTtsStoryboardHelper.deleteCachedStoryboard(entry) }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                result
                    .onSuccess {
                        cacheRows = if (row.isCurrent) {
                            cacheRows.map { cachedRow ->
                                if (cachedRow.chapterIndex == row.chapterIndex) {
                                    cachedRow.copy(entry = null)
                                } else {
                                    cachedRow
                                }
                            }
                        } else {
                            cacheRows.filterNot { it.chapterIndex == row.chapterIndex }
                        }
                        toastOnUi(R.string.book_storyboard_delete_done)
                    }
                    .onFailure {
                        toastOnUi(
                            getString(
                                R.string.book_storyboard_delete_failed,
                                it.localizedMessage ?: getString(R.string.unknown_error)
                            )
                        )
                    }
            }
        }
    }

    private fun deleteCachedStoryboards(rows: List<StoryboardCacheRow>) {
        lifecycleScope.launch(IO) {
            val deletedIndexes = linkedSetOf<Int>()
            var failedCount = 0
            rows.forEach { row ->
                val entry = row.entry
                if (entry == null) return@forEach
                runCatching { AiTtsStoryboardHelper.deleteCachedStoryboard(entry) }
                    .onSuccess { deletedIndexes += row.chapterIndex }
                    .onFailure { failedCount += 1 }
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (deletedIndexes.isNotEmpty()) {
                    cacheRows = cacheRows.mapNotNull { row ->
                        when {
                            row.chapterIndex !in deletedIndexes -> row
                            row.isCurrent -> row.copy(entry = null)
                            else -> null
                        }
                    }
                    toastOnUi(
                        getString(
                            R.string.book_storyboard_batch_delete_done,
                            deletedIndexes.size,
                        ),
                    )
                }
                if (failedCount > 0) {
                    toastOnUi(
                        getString(
                            R.string.book_storyboard_batch_delete_failed,
                            failedCount,
                        ),
                    )
                }
            }
        }
    }

    private fun toggleScene(sceneIndex: Int) {
        expandedSceneIndexes = if (sceneIndex in expandedSceneIndexes) {
            expandedSceneIndexes - sceneIndex
        } else {
            expandedSceneIndexes + sceneIndex
        }
    }

    private fun toggleSegmentDetails(segmentKey: String) {
        expandedSegmentKeys = if (segmentKey in expandedSegmentKeys) {
            expandedSegmentKeys - segmentKey
        } else {
            expandedSegmentKeys + segmentKey
        }
    }

    private fun buildStoryboardSceneUi(
        scenes: List<StoryboardScene>,
        chapterTitle: String,
        canonicalCharacterIds: Set<Long> = emptySet(),
        stableCastRoleIds: Set<Long> = emptySet(),
        pendingCastRoleIds: Set<Long> = emptySet(),
        castRoleNames: Set<String> = emptySet(),
        router: ReadAloudTtsRouter? = null,
        baseEngine: TtsEngineSetting? = null,
    ): List<StoryboardSceneUi> {
        return scenes.map { scene ->
            StoryboardSceneUi(
                index = scene.index,
                title = scene.displayTitle(chapterTitle),
                meta = buildList {
                    scene.characters.joinToString("、").takeIf { it.isNotBlank() }?.let(::add)
                    add("${scene.segments.size} 个片段")
                }.joinToString(" · "),
                source = scene,
                segments = scene.segments.mapIndexed { segmentIndex, segment ->
                    val identity = when (segment.type) {
                        StoryboardSegmentType.NARRATION -> "旁白"
                        StoryboardSegmentType.DIALOGUE, StoryboardSegmentType.THOUGHT ->
                            segment.speakerName ?: segment.virtualSpeakerName()
                    }
                    val identityStatus = segment.identityStatus(
                        canonicalCharacterIds = canonicalCharacterIds,
                        stableCastRoleIds = stableCastRoleIds,
                        pendingCastRoleIds = pendingCastRoleIds,
                        castRoleNames = castRoleNames,
                    )
                    StoryboardSegmentUi(
                        key = "${scene.index}:${segment.paragraphIndex}:${segment.start}:${segment.end}:$segmentIndex",
                        identity = identity,
                        meta = "第 ${segment.paragraphIndex + 1} 段",
                        status = identityStatus.takeIf {
                            it != identity && it != segment.type.displayName()
                        },
                        voice = actualVoiceLabel(
                            scene = scene,
                            segment = segment,
                            router = router,
                            baseEngine = baseEngine,
                        ),
                        text = segment.text.trimStart(' ', '\t', '\u3000'),
                        details = segment.details(scene),
                        source = segment,
                    )
                },
            )
        }
    }

    private fun StoryboardScene.displayTitle(chapterTitle: String): String {
        val number = index.coerceAtLeast(1)
        val semanticTitle = title
            .replace(Regex("""^\s*(?:分镜|场景)\s*\d+\s*[·:：\-]?\s*"""), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        val shortTitle = semanticTitle.ifBlank { summary }
            .take(24)
            .takeIf {
                it.isNotBlank() && it.normalizedStoryboardTitle() != chapterTitle.normalizedStoryboardTitle()
            }
        return buildString {
            append("场景 $number")
            shortTitle?.let { append(" · ").append(it) }
        }
    }

    private fun StoryboardSegmentType.displayName(): String = when (this) {
        StoryboardSegmentType.NARRATION -> "旁白"
        StoryboardSegmentType.DIALOGUE -> "对白"
        StoryboardSegmentType.THOUGHT -> "心声"
    }

    private fun StoryboardSegment.identityStatus(
        canonicalCharacterIds: Set<Long>,
        stableCastRoleIds: Set<Long>,
        pendingCastRoleIds: Set<Long>,
        castRoleNames: Set<String>,
    ): String = when {
        type == StoryboardSegmentType.NARRATION -> "旁白"
        identityType == StoryboardSegment.IdentityType.FORMAL_CHARACTER -> "角色卡"
        identityType == StoryboardSegment.IdentityType.CAST_ROLE ||
            identityType == StoryboardSegment.IdentityType.STABLE_CANDIDATE -> "临时角色"
        identityType == StoryboardSegment.IdentityType.PENDING ||
            identityType == StoryboardSegment.IdentityType.GUEST -> "待确认"
        speakerId != null && speakerId in canonicalCharacterIds -> "角色卡"
        castRoleId != null && castRoleId in stableCastRoleIds -> "临时角色"
        castRoleId != null && castRoleId in pendingCastRoleIds -> "待确认"
        speakerName?.let { BookTtsCastingCoordinator.normalizeIdentityName(it) in castRoleNames } == true -> "临时角色"
        speakerGender == StoryboardSegment.SpeakerGender.MALE -> "男性兜底"
        speakerGender == StoryboardSegment.SpeakerGender.FEMALE -> "女性兜底"
        else -> "待确认"
    }

    private fun actualVoiceLabel(
        scene: StoryboardScene,
        segment: StoryboardSegment,
        router: ReadAloudTtsRouter?,
        baseEngine: TtsEngineSetting?,
    ): String {
        baseEngine ?: return "声音加载中…"
        val route = resolvedRoute(scene, segment, router, baseEngine)
        val engine = route?.engine ?: baseEngine
        val voiceId = route?.voiceId ?: engine.activeVoiceId
        val voiceName = engine.enabledVoices().firstOrNull { it.id == voiceId }?.name
            ?: voiceId?.takeIf { it.isNotBlank() }
            ?: "默认声音"
        return if (route?.bindingUnavailable == true) {
            "发音人不可用 · 已改用 $voiceName · ${engine.name}"
        } else {
            buildString {
                append(voiceName).append(" · ").append(engine.name)
                if (route?.sceneOverrideUsed == true) append(" · 场景音色")
            }
        }
    }

    private fun currentBaseEngine() =
        (ReadAloud.httpTtsEngineV2 ?: runCatching { TtsEngineStore.activeEngine() }.getOrNull())
            ?.takeIf { it.enabled && it.type == TtsEngineType.SCRIPT }

    private fun resolvedRoute(
        scene: StoryboardScene,
        segment: StoryboardSegment,
        router: ReadAloudTtsRouter?,
        baseEngine: TtsEngineSetting,
    ): ReadAloudTtsRouter.Route? {
        return router?.route(segment, baseEngine, scene) ?: ReadAloudTtsRouter.Route(
            engine = baseEngine,
            voiceId = baseEngine.activeVoiceId,
            styleId = null,
            fallbackUsed = segment.type == StoryboardSegmentType.DIALOGUE ||
                segment.type == StoryboardSegmentType.THOUGHT
        )
    }

    private fun StoryboardSegment.details(scene: StoryboardScene): String {
        return buildList {
            performanceContext.joinToString("；")
                .ifBlank { scene.contextText }
                .compactDetail()
                .takeIf { it.isNotBlank() }
                ?.let { add("场景：$it") }
            performanceInstruction.compactDetail()
                .takeIf { it.isNotBlank() }
                ?.let { add("演播：$it") }
            evidence.compactDetail()
                .takeIf { it.isNotBlank() }
                ?.let { add("依据：$it") }
        }.distinct().joinToString("\n")
    }

    private fun String.compactDetail(): String = replace(Regex("\\s+"), " ").trim().take(240)

    private fun currentParagraphIndex(chapterIndex: Int): Int? {
        if (chapterIndex != ReadBook.durChapterIndex) return null
        val chapter = ReadBook.curTextChapter?.takeIf { it.isCompleted } ?: return null
        val pageSplit = appCtx.getPrefBoolean(PreferKey.readAloudByPage)
        val paragraphs = chapter.getParagraphs(pageSplit).filter { it.text.isNotBlank() }
        val position = ReadBook.durChapterPos
        return paragraphs.indexOfFirst { position in it.chapterIndices }.takeIf { it >= 0 }
    }

    private fun StoryboardSegment.virtualSpeakerName(): String {
        return when (speakerGender) {
            StoryboardSegment.SpeakerGender.MALE -> "对白男"
            StoryboardSegment.SpeakerGender.FEMALE -> "对白女"
            else -> if (type == StoryboardSegmentType.THOUGHT) "心声" else "待确认说话人"
        }
    }

    private fun StoryboardSegment.isChapterTitleSegment(chapterTitle: String): Boolean {
        if (type != StoryboardSegmentType.NARRATION || paragraphIndex != 0) {
            return false
        }
        val normalizedTitle = chapterTitle.normalizedStoryboardTitle()
        return normalizedTitle.isNotBlank() && text.normalizedStoryboardTitle() == normalizedTitle
    }

    private fun String.normalizedStoryboardTitle(): String {
        return filterNot { it.isWhitespace() || it == '\u3000' }
    }

    private fun String.normalizedStoryboardPersonName(): String? {
        val normalizedName = BookTtsCastingCoordinator.normalizeIdentityName(this)
        return normalizedName.takeIf {
            it.isNotBlank() && it !in NON_PERSON_STORYBOARD_NAMES
        }
    }

    private fun previewStoryboardSegment(scene: StoryboardScene, segment: StoryboardSegment) {
        stopPreview()
        val text = normalizeStoryboardSynthesisText(segment.text, segment.type)
        if (text.isBlank()) {
            toastOnUi("片段内容为空")
            return
        }
        val baseEngine = (ReadAloud.httpTtsEngineV2 ?: runCatching { TtsEngineStore.activeEngine() }.getOrNull())
            ?.takeIf { it.enabled && it.type == TtsEngineType.SCRIPT }
        if (baseEngine == null) {
            toastOnUi("当前朗读引擎不支持片段试听")
            return
        }
        toastOnUi("正在合成片段试听...")
        previewJob = lifecycleScope.launch {
            val result = runCatching {
                val file = withContext(IO) {
                    val router = ReadBook.book?.let { ReadAloudTtsRouter.create(it) }
                    val route = router?.route(segment, baseEngine, scene)
                    val engine = (route?.engine ?: baseEngine)
                        .takeIf { it.enabled && it.type == TtsEngineType.SCRIPT }
                        ?: error("角色绑定的朗读引擎不可用")
                    val synthesisContext = segment
                        .toTtsSynthesisContext(scene)
                        ?.forEngineCapabilities(engine)
                    val file = File(cacheDir, "storyboard_preview_${System.currentTimeMillis()}.audio")
                    writeReadAloudAudioWithWavRetry(file, text) {
                        TtsScriptEngineClient.getSynthesisStream(
                            engine = engine,
                            text = text,
                            voiceId = route?.voiceId ?: engine.activeVoiceId,
                            styleId = route?.styleId,
                            synthesisContext = synthesisContext
                        )
                    }
                }
                previewPlayer?.release()
                previewPlayer = TtsPlayerFactory.create(this@BookStoryboardActivity).apply {
                    setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                    setPlaybackSpeed(TtsSpeedPolicy.playbackRate(AppConfig.speechRatePlay))
                    prepare()
                    play()
                }
            }
            result.onFailure {
                if (it !is CancellationException) {
                    toastOnUi("片段试听失败：${it.localizedMessage ?: it.javaClass.simpleName}")
                }
            }
        }
    }

    private fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        previewPlayer?.release()
        previewPlayer = null
    }

    override fun onDestroy() {
        storyboardJob?.cancel()
        storyboardJob = null
        renderJob?.cancel()
        renderJob = null
        stopPreview()
        super.onDestroy()
    }

    private companion object {
        val STORYBOARD_CHAPTER_PREFIX = Regex(
            """^\s*第\s*([0-9０-９一二三四五六七八九十百千万零〇两]+)\s*[章节卷回]"""
        )
        val NON_PERSON_STORYBOARD_NAMES = setOf(
            "旁白",
            "对白男",
            "对白女",
            "心声",
            "待确认说话人",
        )
    }

}
