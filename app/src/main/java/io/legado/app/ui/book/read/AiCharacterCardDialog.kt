package io.legado.app.ui.book.read

import android.content.Intent
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
import io.legado.app.help.ai.AiCharacterCardModel
import io.legado.app.help.ai.AiCharacterCardProgress
import io.legado.app.help.ai.AiCharacterCardSession
import io.legado.app.help.ai.AiConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.character.BookCharacterDrawerVariant
import io.legado.app.ui.book.character.BookCharacterEditDialogContent
import io.legado.app.ui.book.character.BookCharacterEditor
import io.legado.app.ui.book.character.BookCharacterFormValue
import io.legado.app.ui.book.character.BookCharacterSilhouetteSettings
import io.legado.app.ui.book.character.BookCharacterSilhouetteStyle
import io.legado.app.ui.book.character.automaticCharacterSilhouetteStyle
import io.legado.app.ui.book.character.initialCharacterFormValue
import io.legado.app.ui.book.info.BookAiAssistantLauncher
import io.legado.app.ui.book.read.config.showReadComposeDialog
import io.legado.app.ui.book.read.config.showReadConfirmDialog
import io.legado.app.ui.config.AiConfigFragment
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.config.displayName
import io.legado.app.ui.design.components.compose.NgFormNavigationRow
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ReaderCharacterDraft(
    val form: BookCharacterFormValue,
    val existing: BookCharacter?,
)

internal data class ReaderCharacterCardTiming(
    private val startedAtMs: Long,
    val totalElapsedMs: Long? = null,
    private val previousElapsedMs: Long = 0,
) {
    fun elapsedMillis(nowMs: Long): Long = totalElapsedMs ?: (previousElapsedMs + nowMs - startedAtMs)

    fun finish(nowMs: Long): ReaderCharacterCardTiming = copy(totalElapsedMs = elapsedMillis(nowMs))
}

internal fun ReadBookActivity.readerCharacterCardModel(onMcpEnabled: () -> Unit): AiCharacterCardModel? {
    fun openSettings() {
        startActivity(Intent(this, ConfigActivity::class.java).apply {
            putExtra("configTag", ConfigTag.AI_CONFIG)
            putExtra(AiConfigFragment.EXTRA_INITIAL_PAGE, AiConfigFragment.PAGE_CHARACTER_CARD)
        })
    }
    val model = AiConfig.selectedCharacterCardModel()
    if (model == null) {
        showReadConfirmDialog(
            context = this,
            title = getString(R.string.ai_character_card),
            message = getString(R.string.ai_character_card_pick_model),
            confirmLabel = getString(R.string.ai_setting),
            cancelLabel = getString(R.string.cancel),
            onConfirm = ::openSettings,
        )
        return null
    }
    if (!AiConfig.internalMcpEnabled) {
        showReadConfirmDialog(
            context = this,
            title = getString(R.string.ai_character_card),
            message = getString(R.string.ai_character_card_mcp_required),
            confirmLabel = getString(R.string.enable),
            cancelLabel = getString(R.string.cancel),
            onConfirm = {
                AiConfig.internalMcpEnabled = true
                onMcpEnabled()
            },
        )
        return null
    }
    return model
}

internal fun ReadBookActivity.showAiCharacterCardSelection(
    book: Book,
    name: String,
    chapterIndex: Int,
    chapterTitle: String?,
    onSaved: () -> Unit,
) {
    val model = readerCharacterCardModel {
        showAiCharacterCardSelection(book, name, chapterIndex, chapterTitle, onSaved)
    } ?: return
    val modelName = model.model.displayName()
    val entry = BookAiAssistantLauncher.characterCardSelection(book, name, chapterIndex, chapterTitle)
    val workKey = BookCharacterProfile.workKey(book.name, book.author)
    val automaticStyle = book.automaticCharacterSilhouetteStyle()
    val progress = MutableStateFlow(AiCharacterCardProgress())
    var job: Job? = null
    var draft by mutableStateOf<ReaderCharacterDraft?>(null)
    var portraitCharacter by mutableStateOf<BookCharacter?>(null)
    var generationError by mutableStateOf<String?>(null)
    var generationTiming by mutableStateOf<ReaderCharacterCardTiming?>(null)
    var showDetails by mutableStateOf(false)
    var saving by mutableStateOf(false)
    var edited = false
    var revision by mutableIntStateOf(0)
    lateinit var dialog: ComponentDialog

    fun generate() {
        if (job?.isActive == true) return
        if (ReadBook.book?.bookUrl != book.bookUrl) {
            toastOnUi(R.string.ai_character_card_changed_book)
            return
        }
        showDetails = false
        draft?.let { portraitCharacter = it.existing }
        draft = null
        generationError = null
        edited = false
        revision++
        progress.value = AiCharacterCardProgress()
        val timing = ReaderCharacterCardTiming(SystemClock.elapsedRealtime())
        generationTiming = timing
        val session = AiCharacterCardSession(entry, model, AiConfig.characterCardReasoningLevel)
        job = lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { session.generate { progress.value = it } }
                val form = result.form
                if (form == null) {
                    generationError = result.explanation ?: getString(R.string.ai_character_card_no_preview)
                } else {
                    draft = withContext(Dispatchers.IO) {
                        readerCharacterDraft(workKey, name, form)
                    }
                    if (result.introNeedsReview) toastOnUi(R.string.ai_character_card_intro_needs_review)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                generationError = error.localizedMessage?.take(360)
                    ?: getString(R.string.ai_character_card_failed)
            } finally {
                generationTiming = timing.finish(SystemClock.elapsedRealtime())
            }
        }
    }

    fun close() {
        if (saving) return
        if (!edited) {
            dialog.dismiss()
            return
        }
        showReadConfirmDialog(
            context = this,
            title = getString(R.string.ai_character_card_discard_draft),
            message = getString(R.string.ai_character_card_discard_draft_body),
            confirmLabel = getString(R.string.ai_character_card_discard),
            cancelLabel = getString(R.string.ai_character_card_keep_draft),
            onConfirm = dialog::dismiss,
        )
    }

    fun retry() {
        if (!edited) {
            generate()
            return
        }
        showReadConfirmDialog(
            context = this,
            title = getString(R.string.ai_character_card_replace_draft),
            message = getString(R.string.ai_character_card_replace_draft_body),
            confirmLabel = getString(R.string.ai_character_card_regenerate),
            cancelLabel = getString(R.string.ai_character_card_keep_draft),
            onConfirm = ::generate,
        )
    }

    fun save(value: BookCharacterFormValue, style: BookCharacterSilhouetteStyle?) {
        val current = draft ?: return
        if (job?.isActive == true || saving) return
        if (value.name.isEmpty()) {
            toastOnUi(R.string.character_name_empty)
            return
        }
        if (ReadBook.book?.bookUrl != book.bookUrl) {
            toastOnUi(R.string.ai_character_card_changed_book)
            return
        }
        saving = true
        dialog.setCancelable(false)
        job = lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    BookCharacterEditor.save(
                        context = applicationContext,
                        workKey = workKey,
                        current = current.existing,
                        castRole = null,
                        value = value,
                        generatedFor = book,
                    )
                    style?.let {
                        runCatching { BookCharacterSilhouetteSettings.set(applicationContext, it) }
                            .onFailure { error -> AppLog.put("保存角色剪影风格失败", error) }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.put("保存 AI 角色卡失败", error)
                toastOnUi(error.localizedMessage ?: getString(R.string.ai_character_card_save_failed))
                return@launch
            } finally {
                saving = false
                dialog.setCancelable(true)
            }
            dialog.dismiss()
            onSaved()
            toastOnUi(R.string.ai_character_card_saved)
        }
    }

    val observer = object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            dialog.dismiss()
        }
    }
    dialog = showReadComposeDialog(
        context = this,
        marginDp = 0,
        bottomAligned = true,
        cancelOnTouchOutside = false,
        onDismiss = {
            job?.cancel()
            lifecycle.removeObserver(observer)
            bottomDialog = (bottomDialog - 1).coerceAtLeast(0)
        },
    ) {
        val currentProgress by progress.collectAsState()
        val currentDraft = draft
        val focusManager = LocalFocusManager.current
        BackHandler {
            if (showDetails) showDetails = false else close()
        }
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides this@showAiCharacterCardSelection) {
            key(revision) {
                val formState = rememberSaveableStateHolder()
                when {
                    showDetails -> ReaderCharacterCardDetails(
                        name = name,
                        chapterIndex = chapterIndex,
                        chapterTitle = chapterTitle,
                        modelName = modelName,
                        providerName = model.provider.name,
                        progress = currentProgress,
                        timing = generationTiming,
                        complete = currentDraft != null,
                        interrupted = generationError != null,
                        onBack = { showDetails = false },
                        onRetry = if (currentDraft != null || generationError != null) ::retry else null,
                    )
                    currentDraft != null -> formState.SaveableStateProvider("editor") {
                        BookCharacterEditDialogContent(
                            title = stringResource(
                                if (currentDraft.existing == null) R.string.create_character_card
                                else R.string.ai_character_card_update,
                            ),
                            confirmText = stringResource(if (saving) R.string.ai_character_card_saving else R.string.save),
                            initialValue = currentDraft.form,
                            saving = saving,
                            showPortraitOptions = true,
                            automaticSilhouetteStyle = automaticStyle,
                            onConfirm = ::save,
                            onClose = ::close,
                            onEditsChanged = { edited = it },
                            drawerVariant = BookCharacterDrawerVariant.READER_AI,
                            extraContent = {
                                NgFormNavigationRow(
                                    title = stringResource(R.string.ai_character_card_details),
                                    value = stringResource(R.string.ai_character_card_tool_count, currentProgress.tools.size),
                                    arrowIcon = painterResource(R.drawable.ic_chevron_right_20),
                                    enabled = !saving,
                                    onClick = {
                                        focusManager.clearFocus()
                                        showDetails = true
                                    },
                                )
                            },
                        )
                    }
                    else -> ReaderCharacterCardGeneration(
                        name = name,
                        progress = currentProgress,
                        error = generationError,
                        timing = generationTiming,
                        onDetails = { showDetails = true },
                        onCancel = ::close,
                        onRetry = ::retry,
                        automaticSilhouetteStyle = automaticStyle,
                        portraitCharacter = portraitCharacter,
                    )
                }
            }
        }
    }
    bottomDialog++
    lifecycle.addObserver(observer)
    generate()
}

internal fun readerCharacterDraft(
    workKey: String,
    selectedName: String,
    form: BookCharacterFormValue,
    fillMissingOnly: Boolean = false,
): ReaderCharacterDraft {
    val cards = appDb.bookCharacterDao.getCharacters(workKey)
    val existing = CharacterCardSelection.match(selectedName, cards)
        ?: CharacterCardSelection.match(form.name, cards)
    return ReaderCharacterDraft(form.withExistingCharacter(existing, fillMissingOnly), existing)
}

private fun BookCharacterFormValue.withExistingCharacter(
    existing: BookCharacter?,
    fillMissingOnly: Boolean,
): BookCharacterFormValue {
    if (existing == null) return this
    val previous = initialCharacterFormValue(existing, null)
    return copy(
        name = previous.name,
        aliases = (previous.aliases + aliases + name)
            .filterNot { it.equals(previous.name, ignoreCase = true) }
            .distinctBy { it.lowercase() },
        gender = previous.gender.takeIf { fillMissingOnly && it != BookCharacter.Gender.UNKNOWN }
            ?: gender.takeUnless { it == BookCharacter.Gender.UNKNOWN } ?: previous.gender,
        roleTag = previous.roleTag.takeIf { fillMissingOnly && it != BookCharacter.RoleTag.UNKNOWN }
            ?: roleTag.takeUnless { it == BookCharacter.RoleTag.UNKNOWN } ?: previous.roleTag,
        intro = if (fillMissingOnly) previous.intro ?: intro else intro ?: previous.intro,
        imagePrompt = if (fillMissingOnly) previous.imagePrompt ?: imagePrompt else imagePrompt ?: previous.imagePrompt,
        portraitUri = previous.portraitUri,
    )
}
