package io.legado.app.ui.book.read.aloud

import android.os.Bundle
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.help.IntentHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ListeningFluidType
import io.legado.app.help.config.ListeningFireStyle
import io.legado.app.help.config.ListeningMotionConfig
import io.legado.app.help.config.ListeningMotionColorMode
import io.legado.app.help.config.ListeningMotionEffect
import io.legado.app.help.config.ListeningMotionSettings
import io.legado.app.help.tts.BookTtsAutomationConfig
import io.legado.app.help.tts.BookTtsCastingCoordinator
import io.legado.app.help.tts.ReadAloudCacheManager
import io.legado.app.help.tts.TtsEngineCapability
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.help.tts.TtsVoice
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.config.TtsVoiceFilterSupport
import io.legado.app.ui.config.TtsVoiceDrawerGroup
import io.legado.app.ui.config.TtsEngineSelectionDrawerContent
import io.legado.app.ui.config.TtsVoiceDrawerState
import io.legado.app.ui.config.TtsVoiceOption
import io.legado.app.ui.config.TtsVoicePreviewController
import io.legado.app.ui.config.TtsVoiceSelectionDrawerContent
import io.legado.app.ui.config.toDrawerCard
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgButton
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgFormSwitchSettingRow
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class ModeDrawerScreen {
    MAIN,
    ENGINES,
}

private data class ModeDrawerState(
    val multiRole: Boolean = AppConfig.readAloudMultiRole,
    val selectedEngine: TtsEngineSetting? = null,
    val engines: List<TtsEngineSetting> = emptyList(),
    val automation: BookTtsAutomationConfig.Settings = BookTtsAutomationConfig.Settings(),
    val loadingEngines: Boolean = true,
)

internal class ReadAloudModeDialog : ReadAloudComposeBottomSheet() {

    private var screen by mutableStateOf(ModeDrawerScreen.MAIN)
    private var state by mutableStateOf(ModeDrawerState())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refreshState()
        (view as ComposeView).setContent {
            ListeningSheetTheme {
                ReadAloudModeSheetContent(
                    screen = screen,
                    state = state,
                    onBack = { screen = ModeDrawerScreen.MAIN },
                    onDismiss = { dismissAllowingStateLoss() },
                    onModeChange = ::setMultiRole,
                    onOpenEngines = { screen = ModeDrawerScreen.ENGINES },
                    onEngineSelect = ::selectEngine,
                    onClearEngine = { selectEngine(null) },
                    onAutoCreateChange = ::setAutoCreate,
                    onAutoAssignChange = ::setAutoAssign,
                    onSceneVoiceChange = ::setSceneVoice,
                    onStoryboard = {
                        (activity as? ReadAloudPlayerActivity)?.openStoryboardResult()
                        dismissAllowingStateLoss()
                    },
                )
            }
        }
    }

    private fun refreshState() {
        val workKey = workKey()
        val automation = workKey?.let(BookTtsAutomationConfig::get)
            ?: BookTtsAutomationConfig.Settings()
        state = state.copy(
            multiRole = AppConfig.readAloudMultiRole,
            automation = automation,
        )
        viewLifecycleOwner.lifecycleScope.launch {
            val engines = withContext(IO) {
                TtsEngineStore.engines().filter {
                    it.enabled && it.type == TtsEngineType.SCRIPT
                }
            }
            state = state.copy(
                engines = engines,
                selectedEngine = engines.firstOrNull { it.id == AppConfig.multiRoleTtsEngineId },
                loadingEngines = false,
            )
        }
    }

    private fun setMultiRole(enabled: Boolean) {
        (activity as? ReadAloudPlayerActivity)?.setMultiRoleEnabled(enabled)
        state = state.copy(multiRole = enabled)
    }

    private fun selectEngine(engine: TtsEngineSetting?) {
        (activity as? ReadAloudPlayerActivity)?.selectMultiRoleEngine(engine?.id)
        state = state.copy(selectedEngine = engine)
        screen = ModeDrawerScreen.MAIN
    }

    private fun setAutoCreate(enabled: Boolean) {
        val key = workKey() ?: return
        BookTtsAutomationConfig.setAutoCreateTemporaryRoles(key, enabled)
        state = state.copy(automation = state.automation.copy(autoCreateTemporaryRoles = enabled))
    }

    private fun setAutoAssign(enabled: Boolean) {
        val key = workKey() ?: return
        val wasEnabled = BookTtsAutomationConfig.get(key).autoAssignVoices
        BookTtsAutomationConfig.setAutoAssignVoices(key, enabled)
        state = state.copy(automation = state.automation.copy(autoAssignVoices = enabled))
        if (!wasEnabled && enabled) assignUnboundVoices(key)
    }

    private fun setSceneVoice(enabled: Boolean) {
        val key = workKey() ?: return
        BookTtsAutomationConfig.setAutoSwitchSceneVoices(key, enabled)
        state = state.copy(automation = state.automation.copy(autoSwitchSceneVoices = enabled))
        val player = activity as? ReadAloudPlayerActivity ?: return
        if (BaseReadAloudService.isRun && AppConfig.readAloudMultiRole) {
            ReadAloud.refreshTtsRoute(player)
        }
    }

    private fun assignUnboundVoices(workKey: String) {
        val player = activity as? ReadAloudPlayerActivity ?: return
        if (BaseReadAloudService.isRun && AppConfig.readAloudMultiRole) {
            ReadAloud.prepareTtsCasting(player)
        }
        viewLifecycleOwner.lifecycleScope.launch(IO) {
            runCatching { BookTtsCastingCoordinator.assignUnboundRoles(workKey) }
                .onSuccess { count ->
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        player.toastOnUi(
                            if (count > 0) {
                                player.getString(R.string.character_auto_assign_done, count)
                            } else {
                                player.getString(R.string.character_auto_assign_no_change)
                            }
                        )
                        if (BaseReadAloudService.isRun && AppConfig.readAloudMultiRole) {
                            ReadAloud.refreshTtsRoute(player)
                        }
                    }
                }
                .onFailure {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        player.toastOnUi(R.string.character_auto_assign_failed)
                        if (BaseReadAloudService.isRun && AppConfig.readAloudMultiRole) {
                            ReadAloud.refreshTtsRoute(player)
                        }
                    }
                }
        }
    }

    private fun workKey(): String? {
        val book = ReadBook.book ?: return null
        return BookCharacterProfile.workKey(book.name, book.author).takeIf { it.isNotBlank() }
    }
}

@Composable
private fun ReadAloudModeSheetContent(
    screen: ModeDrawerScreen,
    state: ModeDrawerState,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onModeChange: (Boolean) -> Unit,
    onOpenEngines: () -> Unit,
    onEngineSelect: (TtsEngineSetting) -> Unit,
    onClearEngine: () -> Unit,
    onAutoCreateChange: (Boolean) -> Unit,
    onAutoAssignChange: (Boolean) -> Unit,
    onSceneVoiceChange: (Boolean) -> Unit,
    onStoryboard: () -> Unit,
) {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    BackHandler(enabled = screen == ModeDrawerScreen.ENGINES, onBack = onBack)
    when (screen) {
        ModeDrawerScreen.MAIN -> {
            NgBottomDrawerSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = (screenHeightDp * 0.82f).dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    ModeMainContent(
                        state = state,
                        onDismiss = onDismiss,
                        onModeChange = onModeChange,
                        onOpenEngines = onOpenEngines,
                        onAutoCreateChange = onAutoCreateChange,
                        onAutoAssignChange = onAutoAssignChange,
                        onSceneVoiceChange = onSceneVoiceChange,
                        onStoryboard = onStoryboard,
                    )
                }
            }
        }

        ModeDrawerScreen.ENGINES -> {
            TtsEngineSelectionDrawerContent(
                title = "多角色 TTS 引擎",
                searchHint = "搜索多角色 TTS 引擎",
                emptyText = "没有可用的多人朗读引擎",
                engines = state.engines,
                selectedEngineId = state.selectedEngine?.id,
                loading = state.loadingEngines,
                onSelect = onEngineSelect,
                onClear = onClearEngine,
            )
        }
    }
}

@Composable
private fun ColumnScope.ModeMainContent(
    state: ModeDrawerState,
    onDismiss: () -> Unit,
    onModeChange: (Boolean) -> Unit,
    onOpenEngines: () -> Unit,
    onAutoCreateChange: (Boolean) -> Unit,
    onAutoAssignChange: (Boolean) -> Unit,
    onSceneVoiceChange: (Boolean) -> Unit,
    onStoryboard: () -> Unit,
) {
    NgLongDrawerHeader(
        title = "朗读模式",
        centerTitle = true,
        onNavigationClick = onDismiss,
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeChoiceCard(
                    icon = Icons.Rounded.Person,
                    title = "单人模式",
                    summary = "单一音色朗读",
                    selected = !state.multiRole,
                    onClick = { onModeChange(false) },
                    modifier = Modifier.weight(1f),
                )
                ModeChoiceCard(
                    icon = Icons.Rounded.Groups,
                    title = "多人模式",
                    summary = "多角色朗读",
                    selected = state.multiRole,
                    onClick = { onModeChange(true) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (state.multiRole) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "多人朗读",
                        color = Color(NgTheme.colors.primary),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 14.dp, bottom = 6.dp),
                    )
                    MultiRoleEngineCard(
                        engine = state.selectedEngine,
                        onClick = onOpenEngines,
                    )
                }
            }
            item {
                ListeningSettingsGroup(title = null) {
                    NgFormSwitchSettingRow(
                        title = "自动生成临时角色",
                        summary = "保存跨章节重复出现的演播身份",
                        checked = state.automation.autoCreateTemporaryRoles,
                        onCheckedChange = onAutoCreateChange,
                    )
                    ListeningDivider()
                    NgFormSwitchSettingRow(
                        title = "自动分配发音人",
                        summary = "只补齐可自动替换的未绑定角色",
                        checked = state.automation.autoAssignVoices,
                        onCheckedChange = onAutoAssignChange,
                    )
                    if (state.selectedEngine?.supportsCapability(
                            TtsEngineCapability.CASTING_METADATA
                        ) == true
                    ) {
                        ListeningDivider()
                        NgFormSwitchSettingRow(
                            title = "按场景切换音色",
                            summary = "保留手动与继承绑定",
                            checked = state.automation.autoSwitchSceneVoices,
                            onCheckedChange = onSceneVoiceChange,
                        )
                    }
                }
            }
            item {
                ListeningActionRow(
                    title = "分镜结果",
                    summary = "查看当前书的分镜缓存与片段路由",
                    leadingIcon = Icons.Rounded.Movie,
                    enabled = state.multiRole,
                    onClick = onStoryboard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(NgTheme.colors.surface).copy(alpha = 0.82f)),
                )
            }
        }
    }
}

@Composable
private fun ModeChoiceCard(
    icon: ImageVector,
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(17.dp)
    Column(
        modifier = modifier
            .height(124.dp)
            .clip(shape)
            .background(
                if (selected) {
                    Color(NgTheme.colors.selectedContainer)
                } else {
                    Color(NgTheme.colors.surface).copy(alpha = 0.82f)
                }
            )
            .border(
                1.dp,
                if (selected) Color(NgTheme.colors.primary) else Color.Transparent,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Color(NgTheme.colors.primary)
            else Color(NgTheme.colors.onSurfaceVariant),
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = title,
            modifier = Modifier.padding(top = 8.dp),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = summary,
            modifier = Modifier.padding(top = 4.dp),
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 12.sp,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiRoleEngineCard(
    engine: TtsEngineSetting?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colorResource(R.color.ng_surface_card))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = engine?.name ?: "未选择多人 TTS 引擎",
            color = Color(NgTheme.colors.onSurface),
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(
            modifier = Modifier.padding(top = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            capabilityTags(engine).forEach { tag ->
                VoiceTagChip(
                    text = tag.text,
                    contentColorRes = tag.contentColorRes,
                    containerColorRes = tag.containerColorRes,
                )
            }
        }
    }
}

private fun capabilityTags(engine: TtsEngineSetting?): List<CapabilityTag> {
    if (engine == null) {
        return listOf(
            CapabilityTag(
                text = "需先选择引擎",
                contentColorRes = R.color.ng_warning,
                containerColorRes = R.color.ng_warning_container,
            )
        )
    }
    return buildList {
        add(
            CapabilityTag(
                text = "角色识别",
                contentColorRes = R.color.ng_tts_tag_blue,
                containerColorRes = R.color.ng_tts_tag_blue_container,
            )
        )
        add(
            CapabilityTag(
                text = "片段拆分",
                contentColorRes = R.color.ng_tts_tag_purple,
                containerColorRes = R.color.ng_tts_tag_purple_container,
            )
        )
        if (engine.supportsCapability(TtsEngineCapability.SCENE_CONTEXT) ||
            engine.supportsCapability(TtsEngineCapability.PERFORMANCE_INSTRUCTION)
        ) {
            add(
                CapabilityTag(
                    text = "场景理解",
                    contentColorRes = R.color.ng_tts_tag_orange,
                    containerColorRes = R.color.ng_tts_tag_orange_container,
                )
            )
        }
        if (engine.supportsCapability(TtsEngineCapability.PERFORMANCE_INSTRUCTION)) {
            add(
                CapabilityTag(
                    text = "演员指导",
                    contentColorRes = R.color.ng_tts_tag_green,
                    containerColorRes = R.color.ng_tts_tag_green_container,
                )
            )
        }
    }
}

private data class CapabilityTag(
    val text: String,
    val contentColorRes: Int,
    val containerColorRes: Int,
)

private data class MoreDrawerState(
    val ignoreAudioFocus: Boolean,
    val pauseOnCall: Boolean,
    val wakeLock: Boolean,
    val mediaButtonPerNext: Boolean,
    val readByPage: Boolean,
    val skipChapterTitle: Boolean,
    val workerCount: Int,
    val engineName: String,
)

private enum class MoreDrawerScreen {
    SETTINGS,
    MOTION,
}

internal class ReadAloudMoreDialog : ReadAloudComposeBottomSheet() {

    private var state by mutableStateOf<MoreDrawerState?>(null)
    private var motionState by mutableStateOf(ListeningMotionConfig.current())
    private var screen by mutableStateOf(MoreDrawerScreen.SETTINGS)
    private var showClearCacheConfirmation by mutableStateOf(false)
    private var clearingCache by mutableStateOf(false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        state = loadState(engineName = "正在读取…")
        viewLifecycleOwner.lifecycleScope.launch {
            val engineName = withContext(IO) {
                runCatching {
                    TtsEngineStore.activeEngine().takeIf { it.enabled }?.name ?: "未选择"
                }.getOrDefault("未选择")
            }
            state = state?.copy(engineName = engineName)
        }
        motionState = ListeningMotionConfig.current()
        (view as ComposeView).setContent {
            ListeningSheetTheme {
                BackHandler(enabled = screen == MoreDrawerScreen.MOTION) {
                    screen = MoreDrawerScreen.SETTINGS
                }
                when (screen) {
                    MoreDrawerScreen.SETTINGS -> state?.let { current ->
                        ReadAloudMoreSheetContent(
                            state = current,
                            motionState = motionState,
                            onToggle = ::toggle,
                            onWorkerCountChange = ::setWorkerCount,
                            onOpenMotion = { screen = MoreDrawerScreen.MOTION },
                            onOpenEngine = {
                                (activity as? ReadAloudPlayerActivity)?.openEngineConfig()
                                dismissAllowingStateLoss()
                            },
                            onOpenSystemTts = IntentHelp::openTTSSetting,
                            clearingCache = clearingCache,
                            onClearCache = { showClearCacheConfirmation = true },
                            onStop = {
                                val player = activity as? ReadAloudPlayerActivity
                                    ?: return@ReadAloudMoreSheetContent
                                ReadAloud.stop(player)
                                dismissAllowingStateLoss()
                                player.finish()
                            },
                        )
                    }

                    MoreDrawerScreen.MOTION -> ReadAloudMotionSheetContent(
                        state = motionState,
                        onBack = { screen = MoreDrawerScreen.SETTINGS },
                        onEnabledChange = ::setMotionEnabled,
                        onEffectChange = ::setMotionEffect,
                        onFireStyleChange = ::setFireStyle,
                        onFluidTypeChange = ::setFluidType,
                        onColorModeChange = ::setMotionColorMode,
                        onCustomColorChange = ::setMotionCustomColor,
                        onIntensityPreview = ::previewMotionIntensity,
                        onIntensityCommitted = ::commitMotionIntensity,
                    )
                }
                if (showClearCacheConfirmation) {
                    ReadAloudClearCacheConfirmationDialog(
                        onDismiss = { showClearCacheConfirmation = false },
                        onConfirm = {
                            showClearCacheConfirmation = false
                            clearReadAloudCaches()
                        },
                    )
                }
            }
        }
    }

    private fun clearReadAloudCaches() {
        val player = activity as? ReadAloudPlayerActivity ?: return
        val book = ReadBook.book ?: run {
            player.toastOnUi("当前书籍为空")
            return
        }
        if (clearingCache) return
        val wasRunning = BaseReadAloudService.isRun
        val shouldResume = player.wantsPlayback()
        val pageIndex = ReadBook.durPageIndex
        val startPos = ReadBook.durChapterPos
        if (wasRunning) ReadAloud.pause(player)
        clearingCache = true
        player.lifecycleScope.launch {
            try {
                val result = withContext(IO) { ReadAloudCacheManager.clearCurrentBook(book) }
                player.toastOnUi(
                    if (result.storyboardChapterCount == 0 && result.ttsFileCount == 0) {
                        "当前书籍暂无分镜或 TTS 缓存"
                    } else {
                        "已清理当前书籍缓存：分镜 ${result.storyboardChapterCount} 章，" +
                            "TTS ${result.ttsFileCount} 个"
                    }
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                player.toastOnUi("清理朗读缓存失败\n${error.localizedMessage ?: "未知错误"}")
            } finally {
                if (wasRunning && BaseReadAloudService.isRun) {
                    ReadAloud.playByEventBus(
                        play = shouldResume,
                        pageIndex = pageIndex,
                        startPos = startPos,
                        forceRebuild = true,
                    )
                }
                clearingCache = false
            }
        }
    }

    private fun loadState(engineName: String): MoreDrawerState {
        val context = requireContext()
        return MoreDrawerState(
            ignoreAudioFocus = context.getPrefBoolean(PreferKey.ignoreAudioFocus, false),
            pauseOnCall = context.getPrefBoolean(PreferKey.pauseReadAloudWhilePhoneCalls, false),
            wakeLock = context.getPrefBoolean(PreferKey.readAloudWakeLock, false),
            mediaButtonPerNext = context.getPrefBoolean("mediaButtonPerNext", false),
            readByPage = context.getPrefBoolean(PreferKey.readAloudByPage, false),
            skipChapterTitle = context.getPrefBoolean(PreferKey.skipReadAloudChapterTitle, false),
            workerCount = AppConfig.readAloudWorkerCount.coerceIn(1, 5),
            engineName = engineName,
        )
    }

    private fun toggle(key: MoreToggle, enabled: Boolean) {
        val context = context ?: return
        when (key) {
            MoreToggle.IGNORE_AUDIO_FOCUS -> {
                context.putPrefBoolean(PreferKey.ignoreAudioFocus, enabled)
                state = state?.copy(ignoreAudioFocus = enabled)
            }
            MoreToggle.PAUSE_ON_CALL -> {
                context.putPrefBoolean(PreferKey.pauseReadAloudWhilePhoneCalls, enabled)
                state = state?.copy(pauseOnCall = enabled)
            }
            MoreToggle.WAKE_LOCK -> {
                context.putPrefBoolean(PreferKey.readAloudWakeLock, enabled)
                state = state?.copy(wakeLock = enabled)
            }
            MoreToggle.MEDIA_BUTTON_NEXT -> {
                context.putPrefBoolean("mediaButtonPerNext", enabled)
                state = state?.copy(mediaButtonPerNext = enabled)
            }
            MoreToggle.READ_BY_PAGE -> {
                context.putPrefBoolean(PreferKey.readAloudByPage, enabled)
                state = state?.copy(readByPage = enabled)
                notifyRuntimeChanged()
            }
            MoreToggle.SKIP_CHAPTER_TITLE -> {
                context.putPrefBoolean(PreferKey.skipReadAloudChapterTitle, enabled)
                state = state?.copy(skipChapterTitle = enabled)
                notifyRuntimeChanged()
            }
        }
    }

    private fun setWorkerCount(count: Int) {
        val normalized = count.coerceIn(1, 5)
        context?.putPrefString(PreferKey.readAloudWorkerCount, normalized.toString())
        state = state?.copy(workerCount = normalized)
        notifyRuntimeChanged()
    }

    private fun setMotionEnabled(enabled: Boolean) {
        ListeningMotionConfig.enabled = enabled
        motionState = motionState.copy(enabled = enabled)
        notifyMotionChanged()
    }

    private fun setMotionEffect(effect: ListeningMotionEffect) {
        ListeningMotionConfig.effect = effect
        val intensity = ListeningMotionConfig.intensityFor(effect)
        motionState = motionState.copy(effect = effect, intensity = intensity)
        notifyMotionChanged()
    }

    private fun setFireStyle(style: ListeningFireStyle) {
        ListeningMotionConfig.fireStyle = style
        motionState = motionState.copy(fireStyle = style)
        notifyMotionChanged()
    }

    private fun setFluidType(type: ListeningFluidType) {
        ListeningMotionConfig.fluidType = type
        motionState = motionState.copy(fluidType = type)
        notifyMotionChanged()
    }

    private fun setMotionColorMode(mode: ListeningMotionColorMode) {
        ListeningMotionConfig.colorMode = mode
        motionState = motionState.copy(colorMode = mode)
        notifyMotionChanged()
    }

    private fun setMotionCustomColor(color: Int) {
        ListeningMotionConfig.customColor = color
        motionState = motionState.copy(customColor = color)
        notifyMotionChanged()
    }

    private fun previewMotionIntensity(intensity: Int) {
        motionState = motionState.copy(intensity = intensity.coerceIn(0, 100))
        notifyMotionChanged()
    }

    private fun commitMotionIntensity() {
        if (motionState.effect == ListeningMotionEffect.FLUID) {
            ListeningMotionConfig.fluidIntensity = motionState.intensity
        } else {
            ListeningMotionConfig.intensity = motionState.intensity
        }
        notifyMotionChanged()
    }

    private fun notifyMotionChanged() {
        (activity as? ReadAloudPlayerActivity)?.previewMotionSettings(motionState)
    }

    private fun notifyRuntimeChanged() {
        if (BaseReadAloudService.isRun) postEvent(EventBus.MEDIA_BUTTON, false)
    }
}

private enum class MoreToggle {
    IGNORE_AUDIO_FOCUS,
    PAUSE_ON_CALL,
    WAKE_LOCK,
    MEDIA_BUTTON_NEXT,
    READ_BY_PAGE,
    SKIP_CHAPTER_TITLE,
}

@Composable
private fun ReadAloudMoreSheetContent(
    state: MoreDrawerState,
    motionState: ListeningMotionSettings,
    onToggle: (MoreToggle, Boolean) -> Unit,
    onWorkerCountChange: (Int) -> Unit,
    onOpenMotion: () -> Unit,
    onOpenEngine: () -> Unit,
    onOpenSystemTts: () -> Unit,
    clearingCache: Boolean,
    onClearCache: () -> Unit,
    onStop: () -> Unit,
) {
    val drawerHeight = (LocalConfiguration.current.screenHeightDp * 0.86f).dp
    NgBottomDrawerSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(drawerHeight),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            NgLongDrawerHeader(title = "朗读设置", centerTitle = true)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    ListeningSettingsGroup(title = "播放设置") {
                        NgFormSwitchSettingRow(
                            title = "忽略音频焦点",
                            summary = "允许与其他应用同时播放",
                            checked = state.ignoreAudioFocus,
                            onCheckedChange = { onToggle(MoreToggle.IGNORE_AUDIO_FOCUS, it) },
                        )
                        ListeningDivider()
                        NgFormSwitchSettingRow(
                            title = "来电暂停",
                            summary = "需要忽略音频焦点后启用",
                            checked = state.pauseOnCall,
                            enabled = state.ignoreAudioFocus,
                            onCheckedChange = { onToggle(MoreToggle.PAUSE_ON_CALL, it) },
                        )
                        ListeningDivider()
                        NgFormSwitchSettingRow(
                            title = "朗读唤醒锁",
                            summary = "朗读时保持设备唤醒",
                            checked = state.wakeLock,
                            onCheckedChange = { onToggle(MoreToggle.WAKE_LOCK, it) },
                        )
                        ListeningDivider()
                        NgFormSwitchSettingRow(
                            title = "媒体键切段",
                            summary = "关闭时使用系统默认媒体键行为",
                            checked = state.mediaButtonPerNext,
                            onCheckedChange = { onToggle(MoreToggle.MEDIA_BUTTON_NEXT, it) },
                        )
                        ListeningDivider()
                        NgFormSwitchSettingRow(
                            title = "按页朗读",
                            summary = "翻页时短暂停顿",
                            checked = state.readByPage,
                            onCheckedChange = { onToggle(MoreToggle.READ_BY_PAGE, it) },
                        )
                        ListeningDivider()
                        NgFormSwitchSettingRow(
                            title = "跳过章名",
                            summary = "换章时不朗读章节标题",
                            checked = state.skipChapterTitle,
                            onCheckedChange = { onToggle(MoreToggle.SKIP_CHAPTER_TITLE, it) },
                        )
                        ListeningDivider()
                        WorkerCountRow(
                            value = state.workerCount,
                            onValueChangeFinished = onWorkerCountChange,
                        )
                    }
                }
                item {
                    ListeningSettingsGroup(title = "朗读引擎") {
                        ListeningActionRow(
                            title = "朗读引擎管理",
                            summary = state.engineName,
                            onClick = onOpenEngine,
                        )
                        ListeningDivider()
                        ListeningActionRow(
                            title = "系统 TTS 设置",
                            summary = "打开 Android 系统发音设置",
                            onClick = onOpenSystemTts,
                        )
                    }
                }
                item {
                    ListeningActionRow(
                        title = stringResource(R.string.listening_motion_entry),
                        summary = when {
                            !motionState.enabled -> stringResource(R.string.close)
                            motionState.effect == ListeningMotionEffect.FLAME -> {
                                stringResource(
                                    R.string.listening_motion_flame_summary,
                                    motionState.fireStyle.label(),
                                )
                            }
                            motionState.effect == ListeningMotionEffect.FLUID -> {
                                stringResource(
                                    R.string.listening_motion_fluid_summary,
                                    motionState.fluidType.label(),
                                )
                            }
                            else -> motionState.effect.label()
                        },
                        leadingIcon = Icons.Rounded.AutoAwesome,
                        onClick = onOpenMotion,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(NgTheme.colors.surface).copy(alpha = 0.82f)),
                    )
                }
                item {
                    ListeningActionRow(
                        title = "清理缓存",
                        summary = if (clearingCache) {
                            "正在清理当前书籍的分镜与 TTS 缓存…"
                        } else {
                            "清理当前书籍的分镜与 TTS 音频缓存"
                        },
                        leadingIcon = Icons.Outlined.CleaningServices,
                        enabled = !clearingCache,
                        onClick = onClearCache,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(NgTheme.colors.surface).copy(alpha = 0.82f)),
                    )
                }
                item {
                    ListeningActionRow(
                        title = "停止朗读",
                        summary = "停止服务并关闭播放器",
                        leadingIcon = Icons.Rounded.StopCircle,
                        danger = true,
                        onClick = onStop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(NgTheme.colors.surface).copy(alpha = 0.82f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadAloudClearCacheConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        NgDialog(
            title = "清理朗读缓存",
            variant = NgDialogVariant.CONFIRMATION,
            actions = {
                NgButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .width(84.dp)
                        .height(42.dp),
                    variant = NgButtonVariant.OUTLINE,
                ) {
                    Text("取消", fontSize = 15.sp)
                }
                NgButton(
                    onClick = onConfirm,
                    modifier = Modifier
                        .width(84.dp)
                        .height(42.dp),
                    variant = NgButtonVariant.DANGER,
                ) {
                    Text("清理", fontSize = 15.sp)
                }
            },
        ) {
            Text(
                text = "将清理当前书籍的全部分镜缓存和 TTS 音频缓存。" +
                    "正式角色、人工声音绑定、朗读引擎和设置不会删除；" +
                    "未绑定的临时演播身份会随分镜重新生成。" +
                    "清理时朗读会短暂停顿，并从当前位置重新准备。",
                modifier = Modifier.fillMaxWidth(),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
        }
    }
}

@Composable
private fun WorkerCountRow(
    value: Int,
    onValueChangeFinished: (Int) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(96.dp)) {
            Text(
                text = "并发预缓存",
                color = Color(NgTheme.colors.onSurface),
                fontSize = 14.sp,
            )
            Text(
                text = "$draft",
                color = Color(NgTheme.colors.primary),
                fontSize = 12.sp,
            )
        }
        NgSlider(
            value = draft.toFloat(),
            onValueChange = { draft = it.roundToInt().coerceIn(1, 5) },
            valueRange = 1f..5f,
            steps = 3,
            variant = NgSliderVariant.DISCRETE,
            onValueChangeFinished = { onValueChangeFinished(draft) },
            modifier = Modifier.weight(1f),
        )
    }
}

internal class ReadAloudVoiceDialog : ReadAloudComposeBottomSheet() {

    private var state by mutableStateOf(TtsVoiceDrawerState())
    private var previewController: TtsVoicePreviewController? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val player = activity as? ReadAloudPlayerActivity ?: run {
            dismissAllowingStateLoss()
            return
        }
        previewController = TtsVoicePreviewController(
            context = player,
            lifecycleScope = viewLifecycleOwner.lifecycleScope,
            beforePreview = {
                player.stopStoryboardPreview()
                if (BaseReadAloudService.isPlay()) ReadAloud.pause(player)
            },
            onStatusChanged = { status -> state = state.copy(preview = status) },
        )
        (view as ComposeView).setContent {
            ListeningSheetTheme {
                TtsVoiceSelectionDrawerContent(
                    title = "发音人",
                    searchHint = "搜索引擎或发音人",
                    emptyText = "没有可选发音人",
                    state = state,
                    enableLongPressPreview = true,
                    onSelect = ::selectVoice,
                    onPreview = { previewController?.preview(it.engine, it.voice, it.systemDefault) },
                    onRetryFetch = ::loadVoices,
                )
            }
        }
        loadVoices()
    }

    private fun loadVoices() {
        state = state.copy(loading = true, fetchError = null)
        viewLifecycleOwner.lifecycleScope.launch {
            val snapshot = withContext(IO) {
                var engines = TtsEngineStore.engines().filter { it.enabled }
                val activeEngineId = runCatching {
                    TtsEngineStore.activeEngineId()
                }.getOrDefault("")
                var fetchError: String? = null
                engines.firstOrNull { it.id == activeEngineId }
                    ?.takeIf { engine ->
                        engine.type == TtsEngineType.SCRIPT &&
                            engine.supportsVoiceFetch() &&
                            engine.effectiveVoices().isEmpty()
                    }
                    ?.let { engine ->
                        try {
                            TtsEngineStore.ensureVoiceCatalog(
                                engineId = engine.id,
                                restartReadAloud = false,
                            )
                            engines = TtsEngineStore.engines().filter { it.enabled }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            fetchError = "获取发音人失败：${
                                error.localizedMessage ?: error.javaClass.simpleName
                            }"
                        }
                    }
                val activeVoiceId = engines.firstOrNull { it.id == activeEngineId }
                    ?.activeVoiceId
                val groups = engines.map { engine ->
                    TtsVoiceDrawerGroup(
                        engineId = engine.id,
                        engineName = engine.name,
                        cards = voiceOptions(engine).map { option ->
                            option.toDrawerCard(
                                selected = activeEngineId == engine.id && if (option.systemDefault) {
                                    activeVoiceId.isNullOrBlank()
                                } else {
                                    activeVoiceId == option.voice.id
                                }
                            )
                        },
                    )
                }.filter { it.cards.isNotEmpty() }
                val voices = groups.flatMap { group ->
                    group.cards.map { it.option.voice }
                }
                TtsVoiceDrawerState(
                    loading = false,
                    groups = groups,
                    languageOptions = TtsVoiceFilterSupport.availableLanguageLabels(voices),
                    genderOptions = listOf("男", "女").filter { label ->
                        groups.any { group ->
                            group.cards.any { it.genderLabel == label }
                        }
                    },
                    fetchError = fetchError,
                    canRetryFetch = engines.firstOrNull { it.id == activeEngineId }
                        ?.let { engine ->
                            engine.type == TtsEngineType.SCRIPT &&
                                engine.supportsVoiceFetch() &&
                                engine.effectiveVoices().isEmpty()
                        } == true,
                )
            }
            state = snapshot.copy(preview = state.preview)
            (activity as? ReadAloudPlayerActivity)?.invalidateVoiceLabel()
        }
    }

    private fun voiceOptions(engine: TtsEngineSetting): List<TtsVoiceOption> {
        if (engine.type == TtsEngineType.SYSTEM) {
            return listOf(
                TtsVoiceOption(
                    engine = engine,
                    voice = TtsVoice(
                        id = TtsEngineStore.SYSTEM_DEFAULT_ID,
                        name = getString(R.string.character_tts_system_default_voice),
                    ),
                    systemDefault = true,
                )
            )
        }
        return engine.enabledVoices().map {
            TtsVoiceOption(engine = engine, voice = it, systemDefault = false)
        }
    }

    private fun selectVoice(option: TtsVoiceOption) {
        val player = activity as? ReadAloudPlayerActivity ?: return
        val wasRun = BaseReadAloudService.isRun
        val oldEngineType = when {
            !wasRun -> null
            ReadAloud.httpTtsEngineV2 != null -> TtsEngineType.SCRIPT
            else -> TtsEngineType.SYSTEM
        }
        val shouldPlay = player.wantsPlayback()
        val pageIndex = ReadBook.durPageIndex
        val startPos = player.currentPageStartPos()
        player.runVoiceSwitch {
            if (wasRun && oldEngineType != null && oldEngineType != option.engine.type) {
                ReadAloud.stop(player)
            }
            val selected = TtsEngineStore.selectVoice(
                engineId = option.engine.id,
                voiceId = option.voice.id.takeUnless { option.systemDefault },
            )
            if (selected != null) {
                player.invalidateVoiceLabel()
                if (wasRun) {
                    ReadAloud.play(
                        player,
                        play = shouldPlay,
                        pageIndex = pageIndex,
                        startPos = startPos,
                        forceRebuild = true,
                    )
                }
                dismissAllowingStateLoss()
            }
        }
    }

    override fun onDestroyView() {
        previewController?.release()
        previewController = null
        super.onDestroyView()
    }
}

@Composable
private fun VoiceTagChip(
    text: String,
    contentColorRes: Int,
    containerColorRes: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = colorResource(contentColorRes),
        fontSize = 12.sp,
        lineHeight = 16.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .widthIn(max = 116.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colorResource(containerColorRes))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
internal fun ListeningSettingsGroup(
    title: String?,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                color = Color(NgTheme.colors.primary),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 14.dp, bottom = 6.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(NgTheme.colors.surface).copy(alpha = 0.84f))
                .border(
                    0.6.dp,
                    Color(NgTheme.colors.outlineVariant).copy(alpha = 0.22f),
                    RoundedCornerShape(16.dp),
                ),
        ) {
            content()
        }
    }
}

@Composable
internal fun ListeningDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(1.dp)
            .background(Color(NgTheme.colors.outlineVariant).copy(alpha = 0.22f)),
    )
}

@Composable
internal fun ListeningActionRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = if (danger) Color(NgTheme.colors.error) else Color(NgTheme.colors.primary),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (danger) Color(NgTheme.colors.error) else Color(NgTheme.colors.onSurface),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!danger) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(NgTheme.colors.onSurfaceVariant),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ListeningChip(
    label: String,
    selected: Boolean,
    onClick: (() -> Unit)? = null,
) {
    Text(
        text = label,
        color = if (selected) Color(NgTheme.colors.primary)
        else Color(NgTheme.colors.onSurfaceVariant),
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) Color(NgTheme.colors.selectedContainer)
                else Color(NgTheme.colors.surface).copy(alpha = 0.72f)
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun ListeningRetryState(
    text: String,
    onRetry: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 14.sp,
            )
            Text(
                text = "重新获取",
                color = Color(NgTheme.colors.primary),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(NgTheme.colors.selectedContainer))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

private fun Set<String>.toggled(value: String): Set<String> =
    if (value in this) this - value else this + value
