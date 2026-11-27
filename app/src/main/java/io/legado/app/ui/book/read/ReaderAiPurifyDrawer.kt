package io.legado.app.ui.book.read

import android.os.SystemClock
import androidx.activity.ComponentDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import io.legado.app.R
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.ai.AiProviderStore
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.config.AiPurifyChapterReviewDrawer
import io.legado.app.ui.book.read.config.AiPurifyDetailsDrawer
import io.legado.app.ui.book.read.config.AiPurifyParagraphReviewDrawer
import io.legado.app.ui.book.read.config.AiPurifyProgressDrawer
import io.legado.app.ui.book.read.config.AiPurifyRangeDrawer
import io.legado.app.ui.book.read.config.AiPurifyRangeOptions
import io.legado.app.ui.book.read.config.AiPurifyResultUi
import io.legado.app.ui.book.read.config.AiPurifyRuleDetailsDrawer
import io.legado.app.ui.book.read.config.AiPurifyRuleUi
import io.legado.app.ui.book.read.config.AiPurifyRunUi
import io.legado.app.ui.book.read.config.showReadComposeDialog
import io.legado.app.ui.config.displayName
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat

internal class ReaderAiPurifySession(
    private val activity: ReadBookActivity,
    val bookUrl: String,
    private val options: AiPurifyRangeOptions?,
    private val selectedLength: Int,
    private val generate: suspend (range: IntRange?, onChapterLoaded: () -> Unit) -> AiPurifyResultUi,
    private val onApplyError: (Exception) -> Unit,
    private val onFinished: (ReaderAiPurifySession) -> Unit,
) {
    private val chapter = options != null
    private val matchBookSource = !ReadBook.bookSource?.bookSourceUrl.isNullOrBlank()
    private val title = activity.getString(
        if (chapter) R.string.ai_purify_chapter else R.string.ai_purify_paragraph_section,
    )
    private var run by mutableStateOf<AiPurifyRunUi?>(null)
    private var details by mutableStateOf(false)
    private var activeRule by mutableStateOf<AiPurifyRuleUi?>(null)
    private var selectedRules by mutableStateOf<Set<String>>(emptySet())
    private var lastRange: IntRange? = null
    private var job: Job? = null
    private var dialog: ComponentDialog? = null
    private var finished = false

    fun open() {
        if (!canContinue()) return
        if (chapter) showDialog() else start(null)
    }

    fun cancel() {
        if (finished) return
        finished = true
        job?.cancel()
        dismissDialog()
        onFinished(this)
    }

    private fun canContinue(): Boolean {
        if (finished) return false
        if (activity.isFinishing || activity.isDestroyed) {
            cancel()
            return false
        }
        if (ReadBook.book?.bookUrl == bookUrl) return true
        cancel()
        activity.toastOnUi(R.string.ai_purify_drawer_book_changed)
        return false
    }

    private fun start(range: IntRange?) {
        if (job?.isActive == true || !canContinue()) return
        val background = if (chapter) AiConfig.purifyChapterAutoApply else AiConfig.purifyAutoApply
        lastRange = range
        details = false
        activeRule = null
        selectedRules = emptySet()
        val initial = AiPurifyRunUi(
            title = title,
            scope = when {
                range == null -> activity.getString(
                    R.string.ai_purify_drawer_selected_count,
                    NumberFormat.getIntegerInstance().format(selectedLength),
                )
                range.first == range.last -> activity.getString(R.string.ai_purify_drawer_chapter, range.first + 1)
                else -> activity.getString(R.string.ai_purify_drawer_chapters, range.first + 1, range.last + 1)
            },
            startedAtMs = SystemClock.elapsedRealtime(),
            stage = if (chapter) R.string.ai_purify_drawer_reading else R.string.ai_purify_drawer_checking_selection,
        )
        if (background) dismissDialog()
        run = initial
        if (!background) showDialog()

        job = activity.lifecycleScope.launch {
            try {
                val model = withContext(IO) { configuredPurifyModel() }
                run = initial.copy(configuredModel = model)
                val result = generate(range) {
                    run = run?.copy(stage = R.string.ai_purify_drawer_generating_rules)
                }
                ensureActive()
                if (!canContinue()) return@launch
                if (result.skippedChapters > 0) {
                    activity.toastOnUi(activity.getString(R.string.ai_purify_drawer_skipped, result.skippedChapters))
                }
                when {
                    result.autoApply -> applyResult(result, result.ruleKeys, background)
                    background && result.ruleCount == 0 -> {
                        activity.toastOnUi(R.string.ai_purify_drawer_no_changes)
                        cancel()
                    }
                    else -> {
                        selectedRules = result.ruleKeys
                        if (result.ruleCount > 0) details = false
                        run = run?.copy(
                            elapsedMs = SystemClock.elapsedRealtime() - initial.startedAtMs,
                            result = result,
                        )
                        if (background) {
                            activity.lifecycle.withResumed {
                                if (canContinue()) showDialog()
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                if (background) cancel()
                throw cancelled
            } catch (error: Exception) {
                if (!canContinue()) return@launch
                if (background) {
                    toastFailure(error)
                    cancel()
                } else {
                    run = run?.copy(
                        elapsedMs = SystemClock.elapsedRealtime() - initial.startedAtMs,
                        error = error.localizedMessage ?: error.toString(),
                    )
                }
            }
        }
    }

    private fun close() {
        if (chapter && run?.running == true) {
            job?.cancel()
            job = null
            run = null
        } else {
            cancel()
        }
    }

    private fun back() {
        when {
            activeRule != null -> activeRule = null
            details -> details = false
            else -> close()
        }
    }

    private fun retry() {
        start(lastRange)
    }

    private fun apply() {
        val result = run?.result ?: return
        if (job?.isActive == true || dialog?.isShowing != true || !canContinue()) return
        val selected = selectedRules
        if (chapter && selected.isEmpty()) return
        job = activity.lifecycleScope.launch {
            applyResult(result, selected, background = false)
        }
    }

    private suspend fun applyResult(result: AiPurifyResultUi, selected: Set<String>, background: Boolean) {
        dismissDialog()
        try {
            result.apply(selected)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (canContinue()) {
                if (background) toastFailure(error) else onApplyError(error)
            }
        } finally {
            cancel()
        }
    }

    private fun toastFailure(error: Exception) {
        activity.toastOnUi(
            error.localizedMessage?.takeIf(String::isNotBlank)
                ?: activity.getString(R.string.ai_purify_drawer_failed),
        )
    }

    private fun dismissDialog() {
        val shown = dialog ?: return
        dialog = null
        shown.dismiss()
    }

    private fun showDialog() {
        if (dialog != null) return
        lateinit var shown: ComponentDialog
        shown = showReadComposeDialog(
            context = activity,
            marginDp = 0,
            bottomAligned = true,
            cancelOnTouchOutside = false,
            onDismiss = {
                activity.bottomDialog = (activity.bottomDialog - 1).coerceAtLeast(0)
                if (dialog === shown) {
                    dialog = null
                    cancel()
                }
            },
        ) {
            val scopeState = rememberSaveableStateHolder()
            BackHandler(onBack = ::back)
            val current = run
            if (current == null) {
                options?.let {
                    scopeState.SaveableStateProvider("range") {
                        AiPurifyRangeDrawer(it, onStart = ::start, onClose = ::close)
                    }
                }
            } else {
                key(current.startedAtMs) {
                    val listState = rememberLazyListState()
                    val scrollState = rememberScrollState()
                    val rule = activeRule
                    val result = current.result
                    val paragraph = result?.paragraph
                    when {
                        details -> AiPurifyDetailsDrawer(current, matchBookSource, onBack = ::back, onRetry = ::retry)
                        rule != null -> AiPurifyRuleDetailsDrawer(rule, onBack = ::back)
                        result == null || result.ruleCount == 0 -> AiPurifyProgressDrawer(
                            current, onClose = ::close, onDetails = { details = true }, onRetry = ::retry,
                        )
                        paragraph != null -> AiPurifyParagraphReviewDrawer(
                            run = current,
                            scrollState = scrollState,
                            onRetry = ::retry,
                            onApply = ::apply,
                        )
                        else -> AiPurifyChapterReviewDrawer(
                            run = current,
                            selected = selectedRules,
                            listState = listState,
                            onSelectionChange = { selectedRules = it },
                            onRule = { activeRule = it },
                            onRetry = ::retry,
                            onApply = ::apply,
                        )
                    }
                }
            }
        }
        dialog = shown
        activity.bottomDialog++
    }
}

private fun configuredPurifyModel(): String? {
    val selection = runCatching { AiConfig.requirePurifyModel() }.getOrNull() ?: return null
    val provider = AiProviderStore.provider(selection.providerId) ?: return selection.modelId
    val model = provider.models.firstOrNull { it.id == selection.modelId } ?: return selection.modelId
    return model.displayName() + " · " + provider.name
}
