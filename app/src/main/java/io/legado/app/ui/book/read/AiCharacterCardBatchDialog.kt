package io.legado.app.ui.book.read

import android.os.SystemClock
import androidx.activity.ComponentDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.help.ai.AiCharacterCardProgress
import io.legado.app.help.ai.AiCharacterCardSession
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.ai.discoverCharacterCardCandidates
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.character.BookCharacterDrawerVariant
import io.legado.app.ui.book.character.BookCharacterEditDialogContent
import io.legado.app.ui.book.character.BookCharacterEditor
import io.legado.app.ui.book.character.BookCharacterFormValue
import io.legado.app.ui.book.character.BookCharacterSilhouetteSettings
import io.legado.app.ui.book.character.BookCharacterSilhouetteStyle
import io.legado.app.ui.book.character.automaticCharacterSilhouetteStyle
import io.legado.app.ui.book.info.BookAiAssistantLauncher
import io.legado.app.ui.book.read.config.showReadComposeDialog
import io.legado.app.ui.book.read.config.showReadConfirmDialog
import io.legado.app.ui.config.displayName
import io.legado.app.ui.design.components.compose.NgFormNavigationRow
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal data class ReaderCharacterCandidate(
    val name: String,
    val existing: BookCharacter?,
)

internal data class ReaderCharacterBatchDraft(
    val value: ReaderCharacterDraft,
    val style: BookCharacterSilhouetteStyle? = null,
)

private enum class ReaderCharacterBatchPhase { DISCOVERY, CANDIDATES, GENERATION, REVIEW }

internal fun ReadBookActivity.showAiCharacterCardCandidates(
    book: Book,
    chapterIndex: Int,
    chapterTitle: String?,
    paragraphs: List<String>,
    onSaved: () -> Unit,
) {
    val model = readerCharacterCardModel {
        showAiCharacterCardCandidates(book, chapterIndex, chapterTitle, paragraphs, onSaved)
    } ?: return
    val workKey = BookCharacterProfile.workKey(book.name, book.author)
    val automaticStyle = book.automaticCharacterSilhouetteStyle()
    var phase by mutableStateOf(ReaderCharacterBatchPhase.DISCOVERY)
    var candidates by mutableStateOf(emptyList<ReaderCharacterCandidate>())
    var chosen by mutableStateOf(emptySet<String>())
    var drafts by mutableStateOf(emptyMap<String, ReaderCharacterBatchDraft>())
    var failures by mutableStateOf(emptyMap<String, String>())
    var saveErrors by mutableStateOf(emptyMap<String, String>())
    var toSave by mutableStateOf(emptySet<String>())
    var saved by mutableStateOf(emptySet<String>())
    var editing by mutableStateOf<String?>(null)
    var editorRevision by mutableIntStateOf(0)
    var formEdited = false
    var editedDrafts = false
    var showingDetails by mutableStateOf(false)
    var saving by mutableStateOf(false)
    var discoveryError by mutableStateOf<String?>(null)
    var timing by mutableStateOf<ReaderCharacterCardTiming?>(null)
    var progress by mutableStateOf(MutableStateFlow(AiCharacterCardProgress()))
    var scanTiming: ReaderCharacterCardTiming? = null
    var scanProgress = AiCharacterCardProgress()
    val draftSlots = Semaphore(AiConfig.characterCardConcurrencyLimit)
    var generatingTargets by mutableStateOf(emptyList<ReaderCharacterCandidate>())
    var activeCandidates by mutableStateOf(emptyList<ReaderCharacterCandidate>())
    var finishedDrafts by mutableIntStateOf(0)
    var retrying = false
    var job: Job? = null
    var run = 0
    lateinit var dialog: ComponentDialog

    fun sameBook(): Boolean {
        if (ReadBook.book?.bookUrl == book.bookUrl) return true
        toastOnUi(R.string.ai_character_card_changed_book)
        return false
    }

    fun discover() {
        if (job?.isActive == true || !sameBook()) return
        showingDetails = false
        phase = ReaderCharacterBatchPhase.DISCOVERY
        discoveryError = null
        val attempt = ++run
        val clock = ReaderCharacterCardTiming(SystemClock.elapsedRealtime())
        val report = MutableStateFlow(AiCharacterCardProgress())
        timing = clock
        progress = report
        job = lifecycleScope.launch {
            try {
                val found = withContext(Dispatchers.IO) {
                    val results = discoverCharacterCardCandidates(paragraphs, model) { report.value = it }
                    readerCharacterCandidates(results, appDb.bookCharacterDao.getCharacters(workKey))
                }
                if (run != attempt) return@launch
                candidates = found
                chosen = emptySet()
                phase = ReaderCharacterBatchPhase.CANDIDATES
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (run == attempt) {
                    discoveryError = error.localizedMessage?.take(240)
                        ?: getString(R.string.ai_character_card_candidates_invalid)
                }
            } finally {
                if (run == attempt) {
                    scanProgress = report.value
                    scanTiming = clock.finish(SystemClock.elapsedRealtime())
                    timing = scanTiming
                }
            }
        }
    }

    fun openEditor(name: String) {
        if (saving || name !in drafts) return
        formEdited = false
        editorRevision++
        editing = name
    }

    fun generate(targets: List<ReaderCharacterCandidate>, retry: Boolean = false) {
        if (targets.isEmpty() || job?.isActive == true || !sameBook()) return
        val previous = if (retry) progress.value else scanProgress
        val clock = ReaderCharacterCardTiming(
            startedAtMs = SystemClock.elapsedRealtime(),
            previousElapsedMs = (if (retry) timing else scanTiming)?.totalElapsedMs ?: 0L,
        )
        if (!retry) {
            drafts = emptyMap()
            failures = emptyMap()
            saveErrors = emptyMap()
            toSave = emptySet()
            saved = emptySet()
            editedDrafts = false
        }
        val attempt = ++run
        val report = MutableStateFlow(previous)
        val perCharacter = mutableMapOf<String, AiCharacterCardProgress>()
        val reasoning = AiConfig.characterCardReasoningLevel
        fun updateProgress(name: String, transform: (AiCharacterCardProgress) -> AiCharacterCardProgress) {
            synchronized(perCharacter) {
                perCharacter[name] = transform(perCharacter[name] ?: AiCharacterCardProgress())
                report.value = targets.fold(previous) { total, target ->
                    perCharacter[target.name]?.let(total::followedBy) ?: total
                }
            }
        }

        suspend fun generateDraft(candidate: ReaderCharacterCandidate) {
            val name = candidate.name
            activeCandidates = activeCandidates + candidate
            try {
                val entry = BookAiAssistantLauncher.characterCardSelection(book, name, chapterIndex, chapterTitle)
                val session = AiCharacterCardSession(entry, model, reasoning)
                val draft = withContext(Dispatchers.IO) {
                    val result = session.generate { snapshot -> updateProgress(name) { snapshot } }
                    val form = result.form ?: error(
                        result.explanation ?: getString(R.string.ai_character_card_no_preview),
                    )
                    readerCharacterDraft(workKey, name, form, fillMissingOnly = true)
                }
                if (run != attempt) return
                drafts = drafts + (name to ReaderCharacterBatchDraft(draft))
                failures = failures - name
                toSave = toSave + name
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (run != attempt) return
                updateProgress(name, AiCharacterCardProgress::interruptRunningTools)
                failures = failures + (name to (
                    error.localizedMessage?.take(240) ?: getString(R.string.ai_character_card_failed)
                ))
            } finally {
                if (run == attempt) {
                    activeCandidates = activeCandidates - candidate
                    finishedDrafts++
                }
            }
        }

        progress = report
        timing = clock
        showingDetails = false
        generatingTargets = targets
        activeCandidates = emptyList()
        finishedDrafts = 0
        retrying = retry
        phase = ReaderCharacterBatchPhase.GENERATION
        job = lifecycleScope.launch {
            try {
                coroutineScope {
                    targets.forEach { candidate ->
                        launch {
                            draftSlots.withPermit {
                                if (run == attempt) generateDraft(candidate)
                            }
                        }
                    }
                }
                if (run != attempt) return@launch
                phase = ReaderCharacterBatchPhase.REVIEW
                if (chosen.size == 1) drafts.keys.singleOrNull()?.let(::openEditor)
            } finally {
                if (run == attempt) timing = clock.finish(SystemClock.elapsedRealtime())
            }
        }
    }

    fun cancelGeneration() {
        run++
        job?.cancel()
        job = null
        if (!retrying) {
            timing = scanTiming
            progress = MutableStateFlow(scanProgress)
            phase = ReaderCharacterBatchPhase.CANDIDATES
        } else {
            progress = MutableStateFlow(progress.value.interruptRunningTools())
            timing = timing?.finish(SystemClock.elapsedRealtime())
            phase = ReaderCharacterBatchPhase.REVIEW
        }
    }

    fun confirmDiscard(onConfirm: () -> Unit) {
        showReadConfirmDialog(
            context = this,
            title = getString(R.string.ai_character_card_discard_draft),
            message = getString(R.string.ai_character_card_discard_draft_body),
            confirmLabel = getString(R.string.ai_character_card_discard),
            cancelLabel = getString(R.string.ai_character_card_keep_draft),
            onConfirm = onConfirm,
        )
    }

    fun close() {
        if (saving) return
        if (showingDetails) {
            showingDetails = false
            return
        }
        if (phase == ReaderCharacterBatchPhase.GENERATION) {
            cancelGeneration()
            return
        }
        val leave = {
            if (editing != null && chosen.size > 1) {
                editing = null
                formEdited = false
            } else {
                dialog.dismiss()
            }
        }
        if (formEdited || (editedDrafts && (editing == null || chosen.size == 1))) confirmDiscard(leave) else leave()
    }

    fun save(keys: Set<String>) {
        if (saving || job?.isActive == true || keys.isEmpty() || !sameBook()) return
        val writes = buildMap {
            for (candidate in candidates) {
                val name = candidate.name
                if (name in keys && name !in saved) drafts[name]?.let { put(name, it) }
            }
        }
        if (writes.isEmpty()) return
        saving = true
        dialog.setCancelable(false)
        job = lifecycleScope.launch {
            var count = 0
            try {
                saveErrors = saveErrors - writes.keys
                for ((name, draft) in writes) {
                    try {
                        withContext(Dispatchers.IO) {
                            BookCharacterEditor.save(
                                context = applicationContext,
                                workKey = workKey,
                                current = draft.value.existing,
                                castRole = null,
                                value = draft.value.form,
                                generatedFor = book,
                            )
                            draft.style?.let { style ->
                                runCatching { BookCharacterSilhouetteSettings.set(applicationContext, style) }
                                    .onFailure { AppLog.put("保存角色剪影风格失败", it) }
                            }
                        }
                        saved = saved + name
                        toSave = toSave - name
                        count++
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        AppLog.put("保存 AI 角色卡失败", error)
                        saveErrors = saveErrors + (name to (
                            error.localizedMessage?.take(240) ?: getString(R.string.ai_character_card_save_failed)
                        ))
                    }
                }
            } finally {
                saving = false
                dialog.setCancelable(true)
            }
            if (count > 0) onSaved()
            if (writes.keys.none { it in saveErrors }) {
                dialog.dismiss()
                toastOnUi(resources.getQuantityString(R.plurals.ai_character_card_saved_count, count, count))
            } else {
                val error = writes.keys.singleOrNull()?.let { saveErrors[it] }
                toastOnUi(error ?: getString(R.string.ai_character_card_batch_save_failed, count))
            }
        }
    }

    fun confirmEdit(value: BookCharacterFormValue, style: BookCharacterSilhouetteStyle?) {
        val name = editing ?: return
        val draft = drafts[name] ?: return
        if (value.name.isBlank()) {
            toastOnUi(R.string.character_name_empty)
            return
        }
        drafts = drafts + (name to ReaderCharacterBatchDraft(draft.value.copy(form = value), style))
        editedDrafts = editedDrafts || formEdited
        if (chosen.size == 1) save(setOf(name)) else {
            saveErrors = saveErrors - name
            formEdited = false
            editing = null
        }
    }

    val observer = object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) = dialog.dismiss()
    }
    dialog = showReadComposeDialog(
        context = this,
        marginDp = 0,
        bottomAligned = true,
        cancelOnTouchOutside = false,
        onDismiss = {
            run++
            job?.cancel()
            lifecycle.removeObserver(observer)
            bottomDialog = (bottomDialog - 1).coerceAtLeast(0)
        },
    ) {
        val currentProgress by progress.collectAsState()
        val focus = LocalFocusManager.current
        val currentEditor = editing
        val editorDraft = currentEditor?.let(drafts::get)
        val active = activeCandidates.firstOrNull() ?: generatingTargets.firstOrNull()
        val generatingCount = generatingTargets.size
        val batch = chosen.size > 1
        val discovering = phase == ReaderCharacterBatchPhase.DISCOVERY
        val generating = phase == ReaderCharacterBatchPhase.GENERATION
        BackHandler(onBack = ::close)
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides this@showAiCharacterCardCandidates) {
            key(editorRevision) {
                val editorState = rememberSaveableStateHolder()
                when {
                    showingDetails -> ReaderCharacterCardDetails(
                        name = editorDraft?.value?.form?.name ?: stringResource(
                            if (discovering || phase == ReaderCharacterBatchPhase.CANDIDATES) {
                                R.string.ai_character_card_candidates_search
                            } else R.string.ai_character_card_reading,
                        ),
                        chapterIndex = chapterIndex,
                        chapterTitle = chapterTitle,
                        modelName = model.model.displayName(),
                        providerName = model.provider.name,
                        progress = currentProgress,
                        timing = timing,
                        complete = !discovering && !generating && (failures.isEmpty() || drafts.isNotEmpty()),
                        interrupted = discoveryError != null || (!generating && failures.isNotEmpty() && drafts.isEmpty()),
                        onBack = { showingDetails = false },
                        onRetry = if (discoveryError != null) ::discover else null,
                        scannedChapter = (chapterIndex + 1).takeIf { scanTiming != null && discoveryError == null },
                    )
                    editorDraft != null -> editorState.SaveableStateProvider("editor") {
                        BookCharacterEditDialogContent(
                            title = stringResource(
                                when {
                                    batch -> R.string.ai_character_card_edit_draft
                                    editorDraft.value.existing != null -> R.string.ai_character_card_update
                                    else -> R.string.create_character_card
                                },
                            ),
                            confirmText = stringResource(
                                when {
                                    saving -> R.string.ai_character_card_saving
                                    batch -> R.string.complete
                                    else -> R.string.save
                                },
                            ),
                            initialValue = editorDraft.value.form,
                            initialSilhouetteStyle = editorDraft.style,
                            saving = saving,
                            showPortraitOptions = true,
                            automaticSilhouetteStyle = automaticStyle,
                            onConfirm = ::confirmEdit,
                            onClose = (::close).takeUnless { batch },
                            onBack = (::close).takeIf { batch },
                            onEditsChanged = { formEdited = it },
                            drawerVariant = BookCharacterDrawerVariant.READER_AI,
                            extraContent = {
                                NgFormNavigationRow(
                                    title = stringResource(R.string.ai_character_card_details),
                                    value = stringResource(R.string.ai_character_card_tool_count, currentProgress.tools.size),
                                    arrowIcon = painterResource(R.drawable.ic_chevron_right_20),
                                    enabled = !saving,
                                    onClick = {
                                        focus.clearFocus()
                                        showingDetails = true
                                    },
                                )
                            },
                        )
                    }
                    discovering || generating -> ReaderCharacterCardGeneration(
                        name = when {
                            discovering -> stringResource(R.string.ai_character_card_candidates_search)
                            generatingCount > 1 -> stringResource(R.string.ai_character_card_selected_count, generatingCount)
                            else -> generatingTargets.singleOrNull()?.name.orEmpty()
                        },
                        progress = currentProgress,
                        error = discoveryError,
                        timing = timing,
                        onDetails = { showingDetails = true },
                        onCancel = ::close,
                        onRetry = ::discover,
                        automaticSilhouetteStyle = automaticStyle,
                        stageLabel = when {
                            discovering -> stringResource(R.string.ai_character_card_candidates_scanning)
                            generatingCount > 1 && activeCandidates.isNotEmpty() -> stringResource(
                                R.string.ai_character_card_batch_progress,
                                activeCandidates.joinToString("、") { it.name }, finishedDrafts, generatingCount,
                            )
                            else -> null
                        },
                        discoverCandidates = discovering,
                        batchStep = if (generating && generatingCount > 1) finishedDrafts to generatingCount else null,
                        portraitName = active?.name.orEmpty(),
                        portraitCharacter = active?.existing,
                    )
                    phase == ReaderCharacterBatchPhase.CANDIDATES -> ReaderCharacterCandidateDrawer(
                        chapterNumber = chapterIndex + 1,
                        candidates = candidates,
                        selection = chosen,
                        onToggle = { name ->
                            chosen = if (name in chosen) chosen - name else chosen + name
                        },
                        onGenerate = { generate(candidates.filter { it.name in chosen }) },
                        onClose = ::close,
                    )
                    else -> ReaderCharacterReviewDrawer(
                        candidates = candidates.filter { it.name in chosen && it.name !in saved },
                        drafts = drafts,
                        failures = failures,
                        saveErrors = saveErrors,
                        selection = toSave,
                        saving = saving,
                        automaticStyle = automaticStyle,
                        onToggle = { name -> toSave = if (name in toSave) toSave - name else toSave + name },
                        onEdit = ::openEditor,
                        onDetails = { showingDetails = true },
                        onRetry = { generate(candidates.filter { it.name in failures }, retry = true) },
                        onSave = { save(toSave) },
                        onClose = ::close,
                    )
                }
            }
        }
    }
    bottomDialog++
    lifecycle.addObserver(observer)
    discover()
}

internal fun readerCharacterCandidates(
    names: List<String>,
    cards: List<BookCharacter>,
): List<ReaderCharacterCandidate> = names.map { name ->
    ReaderCharacterCandidate(name, CharacterCardSelection.match(name, cards))
}.distinctBy { candidate -> candidate.existing?.let { "card:${it.id}" } ?: candidate.name.lowercase() }
