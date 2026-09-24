package io.legado.app.ui.config

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.annotation.DrawableRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.legado.app.R
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.help.tts.TtsVoice
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.utils.showWithAppNavigationBarVisibility
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class TtsVoiceOption(
    val engine: TtsEngineSetting,
    val voice: TtsVoice,
    val systemDefault: Boolean,
) {
    fun matchesName(query: String): Boolean {
        return TtsVoiceFilterSupport.matchesName(voice, query)
    }

    fun previewKey(): String {
        return TtsVoicePreviewController.keyOf(engine, voice, systemDefault)
    }
}

/**
 * 默认声音与书籍角色共用的 Compose 发音人抽屉宿主。
 *
 * 抽屉主体与听书页共用 [TtsVoiceSelectionDrawerContent]；这里仅保留调用方标题操作、
 * 引擎范围、选中判断和保存回调。
 */
class TtsVoiceSelectionSheet(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val title: CharSequence? = null,
    private val searchHint: CharSequence,
    private val emptyText: CharSequence,
    private val engines: () -> List<TtsEngineSetting>,
    private val isSelected: (TtsVoiceOption) -> Boolean,
    private val onSelect: (TtsVoiceOption) -> Unit,
    private val beforePreview: () -> Unit = {},
    private val dismissOnSelect: Boolean = true,
    private val onCatalogRefreshed: () -> Unit = {},
    private val titleAction: Pair<CharSequence, () -> Unit>? = null,
    @param:DrawableRes private val titleActionIconRes: Int? = null,
) {
    private var state by mutableStateOf(TtsVoiceDrawerState())
    private var dialog: BottomSheetDialog? = null
    private var loadJob: Job? = null
    private var previewController: TtsVoicePreviewController? = null
    private var voiceParamsDialog: ComponentDialog? = null
    private var refreshing by mutableStateOf(false)
    private var refreshJob: Job? = null

    fun show() {
        if (dialog != null || loadJob?.isActive == true) return
        loadJob = lifecycleScope.launch {
            val snapshot = withContext(IO) {
                buildVoiceSnapshot()
            }
            state = snapshot.copy(preview = state.preview)
            loadJob = null
            showPreparedDialog()
        }
    }

    private fun showPreparedDialog() {
        val bottomSheet = BottomSheetDialog(context)
        dialog = bottomSheet
        previewController = TtsVoicePreviewController(
            context = context,
            lifecycleScope = lifecycleScope,
            beforePreview = beforePreview,
            onStatusChanged = { status -> state = state.copy(preview = status) },
        )
        val contentView = ComposeView(context).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    TtsVoiceSelectionDrawerContent(
                        title = title?.toString() ?: context.getString(R.string.tts_voices),
                        searchHint = searchHint.toString(),
                        emptyText = emptyText.toString(),
                        state = state,
                        contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
                        titleAction = titleAction?.let { (text, action) ->
                            TtsVoiceDrawerTitleAction(
                                text = text.toString(),
                                iconRes = titleActionIconRes,
                            ) {
                                action()
                                dismiss()
                            }
                        },
                        onSelect = { option ->
                            onSelect(option)
                            if (dismissOnSelect) dismiss()
                        },
                        onPreview = { option ->
                            previewController?.preview(
                                option.engine,
                                option.voice,
                                option.systemDefault,
                            )
                        },
                        onEditParams = ::editVoiceParams,
                        refreshing = refreshing,
                        onRefresh = ::refreshVoices,
                    )
                }
            }
        }
        bottomSheet.setContentView(contentView)
        bottomSheet.setOnShowListener {
            bottomSheet.window?.apply {
                setBackgroundDrawableResource(R.color.transparent)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                attributes = attributes.apply { dimAmount = 0.22f }
                decorView.setPadding(0, 0, 0, 0)
            }
            val sheet = bottomSheet.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
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
        bottomSheet.setOnDismissListener {
            loadJob?.cancel()
            refreshJob?.cancel()
            refreshJob = null
            loadJob = null
            previewController?.release()
            previewController = null
            voiceParamsDialog?.dismiss()
            voiceParamsDialog = null
            dialog = null
        }
        bottomSheet.showWithAppNavigationBarVisibility()
    }

    fun dismiss() {
        loadJob?.cancel()
        refreshJob?.cancel()
        loadJob = null
        dialog?.dismiss()
    }

    private fun refreshVoices() {
        if (refreshJob?.isActive == true) return
        refreshing = true
        refreshJob = lifecycleScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                val result = withContext(IO) { refreshTtsVoiceCatalogs(engines()) }
                if (dialog != null) {
                    state = withContext(IO) { buildVoiceSnapshot() }.copy(preview = state.preview)
                    onCatalogRefreshed()
                    context.toastOnUi(result.feedback())
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                context.toastOnUi("刷新发音人失败：${error.localizedMessage ?: error.javaClass.simpleName}")
            } finally {
                refreshing = false
                refreshJob = null
            }
        }
        refreshJob?.start()
    }

    private fun editVoiceParams(option: TtsVoiceOption) {
        if (option.systemDefault || !option.engine.isScriptEngine) return
        voiceParamsDialog?.dismiss()
        voiceParamsDialog = showTtsVoiceParamsDialog(
            context = context,
            engine = option.engine,
            voice = option.voice,
            onEngineUpdated = { updated ->
                previewController?.refreshPlaybackParams(updated, option.voice)
                state = state.withUpdatedEngine(updated)
            },
            onDismissed = { voiceParamsDialog = null },
        )
    }

    private fun buildVoiceSnapshot(): TtsVoiceDrawerState {
        val latest = TtsEngineStore.engines().associateBy { it.id }
        val groups = engines().mapNotNull { latest[it.id] }.map { engine ->
            TtsVoiceDrawerGroup(
                engineId = engine.id,
                engineName = engine.name,
                cards = voiceChoices(engine).map { option ->
                    option.toDrawerCard(selected = isSelected(option))
                },
            )
        }.filter { it.cards.isNotEmpty() }
        val cards = groups.flatMap { it.cards }
        return TtsVoiceDrawerState(
            loading = false,
            groups = groups,
            languageOptions = TtsVoiceFilterSupport.availableLanguageLabels(
                cards.map { it.option.voice }
            ),
            genderOptions = listOf("男", "女").filter { label ->
                cards.any { it.genderLabel == label }
            },
        )
    }

    private fun voiceChoices(engine: TtsEngineSetting): List<TtsVoiceOption> {
        if (engine.type == TtsEngineType.SYSTEM) {
            return listOf(
                TtsVoiceOption(
                    engine = engine,
                    voice = TtsVoice(
                        id = TtsEngineStore.SYSTEM_DEFAULT_ID,
                        name = context.getString(R.string.character_tts_system_default_voice),
                    ),
                    systemDefault = true,
                )
            )
        }
        return engine.enabledVoices().map { voice ->
            TtsVoiceOption(engine = engine, voice = voice, systemDefault = false)
        }
    }
}

internal data class TtsVoiceCatalogRefreshResult(val attempted: Int, val failed: Int) {
    fun feedback(): String = when {
        attempted == 0 -> "没有可刷新的发音人"
        failed == 0 -> "已刷新 $attempted 个引擎的发音人"
        else -> "已刷新 ${attempted - failed} 个引擎的发音人，$failed 个失败"
    }
}

/** 刷新当前选择范围内可在线获取的发音人；失败的引擎保留原缓存。 */
internal suspend fun refreshTtsVoiceCatalogs(
    engines: List<TtsEngineSetting>,
): TtsVoiceCatalogRefreshResult = coroutineScope {
    val semaphore = Semaphore(4)
    val candidates = engines.filter { it.enabled && it.supportsVoiceFetch() }
        .distinctBy { it.id }
    val failures = candidates
        .map { engine ->
            async(IO) {
                semaphore.withPermit {
                    try {
                        TtsEngineStore.ensureVoiceCatalog(
                            engineId = engine.id,
                            forceRefresh = true,
                            restartReadAloud = false,
                        )
                        false
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        true
                    }
                }
            }
        }.awaitAll().count { it }
    TtsVoiceCatalogRefreshResult(candidates.size, failures)
}
