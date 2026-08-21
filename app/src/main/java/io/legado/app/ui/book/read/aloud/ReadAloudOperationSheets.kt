package io.legado.app.ui.book.read.aloud

import android.os.Bundle
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.help.IntentHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.tts.BookTtsAutomationConfig
import io.legado.app.help.tts.BookTtsCastingCoordinator
import io.legado.app.help.tts.TtsEngineCapability
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.help.tts.TtsVoice
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.config.TtsVoiceFilterSupport
import io.legado.app.ui.config.TtsVoiceOption
import io.legado.app.ui.config.TtsVoicePreviewController
import io.legado.app.ui.config.TtsVoicePreviewState
import io.legado.app.ui.config.TtsVoicePreviewStatus
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
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
    val drawerHeight = (LocalConfiguration.current.screenHeightDp * 0.82f).dp
    BackHandler(enabled = screen == ModeDrawerScreen.ENGINES, onBack = onBack)
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
            when (screen) {
                ModeDrawerScreen.MAIN -> ModeMainContent(
                    state = state,
                    onDismiss = onDismiss,
                    onModeChange = onModeChange,
                    onOpenEngines = onOpenEngines,
                    onAutoCreateChange = onAutoCreateChange,
                    onAutoAssignChange = onAutoAssignChange,
                    onSceneVoiceChange = onSceneVoiceChange,
                    onStoryboard = onStoryboard,
                )
                ModeDrawerScreen.ENGINES -> EngineSelectionContent(
                    state = state,
                    onBack = onBack,
                    onEngineSelect = onEngineSelect,
                    onClearEngine = onClearEngine,
                )
            }
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
            .weight(1f),
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
                ListeningSettingsGroup(title = "多人朗读") {
                    ListeningActionRow(
                        title = "多人引擎",
                        summary = state.selectedEngine?.name ?: "未选择多人 TTS 引擎",
                        onClick = onOpenEngines,
                    )
                    ListeningDivider()
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
                CapabilityTags(engine = state.selectedEngine)
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
private fun CapabilityTags(engine: TtsEngineSetting?) {
    val tags = if (engine == null) {
        listOf("需先选择引擎")
    } else buildList {
        add("角色识别")
        add("片段拆分")
        if (engine?.supportsCapability(TtsEngineCapability.SCENE_CONTEXT) == true ||
            engine?.supportsCapability(TtsEngineCapability.PERFORMANCE_INSTRUCTION) == true
        ) add("场景理解")
        if (engine?.supportsCapability(TtsEngineCapability.PERFORMANCE_INSTRUCTION) == true) {
            add("演员指导")
        }
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.forEach { label -> ListeningChip(label = label, selected = true) }
    }
}

@Composable
private fun ColumnScope.EngineSelectionContent(
    state: ModeDrawerState,
    onBack: () -> Unit,
    onEngineSelect: (TtsEngineSetting) -> Unit,
    onClearEngine: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val engines = state.engines.filter {
        query.isBlank() || it.name.contains(query.trim(), ignoreCase = true)
    }
    NgLongDrawerHeader(
        title = "多人朗读引擎",
        navigationIconRes = R.drawable.ic_arrow_back,
        navigationContentDescription = "返回",
        onNavigationClick = onBack,
        actionIconRes = if (state.selectedEngine != null) R.drawable.ic_clear else null,
        actionContentDescription = "清除选择",
        onActionClick = if (state.selectedEngine != null) onClearEngine else null,
        centerTitle = true,
    )
    ListeningSearchField(
        query = query,
        onQueryChange = { query = it },
        hint = "搜索多人朗读引擎",
        modifier = Modifier.padding(vertical = 8.dp),
    )
    when {
        state.loadingEngines -> ListeningLoadingState("正在加载引擎…")
        engines.isEmpty() -> ListeningLoadingState("没有可用的多人朗读引擎")
        else -> LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(engines, key = { it.id }) { engine ->
                ListeningSelectionCard(
                    title = engine.name,
                    summary = "脚本引擎 · ${engine.enabledVoices().size} 个可用发音人",
                    selected = engine.id == state.selectedEngine?.id,
                    onClick = { onEngineSelect(engine) },
                )
            }
        }
    }
}

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

internal class ReadAloudMoreDialog : ReadAloudComposeBottomSheet() {

    private var state by mutableStateOf<MoreDrawerState?>(null)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        state = loadState()
        (view as ComposeView).setContent {
            ListeningSheetTheme {
                state?.let { current ->
                    ReadAloudMoreSheetContent(
                        state = current,
                        onToggle = ::toggle,
                        onWorkerCountChange = ::setWorkerCount,
                        onOpenEngine = {
                            (activity as? ReadAloudPlayerActivity)?.openEngineConfig()
                            dismissAllowingStateLoss()
                        },
                        onOpenSystemTts = IntentHelp::openTTSSetting,
                        onStop = {
                            val player = activity as? ReadAloudPlayerActivity ?: return@ReadAloudMoreSheetContent
                            ReadAloud.stop(player)
                            dismissAllowingStateLoss()
                            player.finish()
                        },
                    )
                }
            }
        }
    }

    private fun loadState(): MoreDrawerState {
        val context = requireContext()
        return MoreDrawerState(
            ignoreAudioFocus = context.getPrefBoolean(PreferKey.ignoreAudioFocus, false),
            pauseOnCall = context.getPrefBoolean(PreferKey.pauseReadAloudWhilePhoneCalls, false),
            wakeLock = context.getPrefBoolean(PreferKey.readAloudWakeLock, false),
            mediaButtonPerNext = context.getPrefBoolean("mediaButtonPerNext", false),
            readByPage = context.getPrefBoolean(PreferKey.readAloudByPage, false),
            skipChapterTitle = context.getPrefBoolean(PreferKey.skipReadAloudChapterTitle, false),
            workerCount = AppConfig.readAloudWorkerCount.coerceIn(1, 5),
            engineName = runCatching { TtsEngineStore.activeEngine().name }.getOrDefault("未选择"),
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
    onToggle: (MoreToggle, Boolean) -> Unit,
    onWorkerCountChange: (Int) -> Unit,
    onOpenEngine: () -> Unit,
    onOpenSystemTts: () -> Unit,
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

private data class VoiceGroup(
    val engine: TtsEngineSetting,
    val options: List<TtsVoiceOption>,
)

private data class VoiceDrawerState(
    val loading: Boolean = true,
    val groups: List<VoiceGroup> = emptyList(),
    val preview: TtsVoicePreviewStatus = TtsVoicePreviewStatus(null, TtsVoicePreviewState.IDLE),
)

internal class ReadAloudVoiceDialog : ReadAloudComposeBottomSheet() {

    private var state by mutableStateOf(VoiceDrawerState())
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
                ReadAloudVoiceSheetContent(
                    state = state,
                    isSelected = ::isSelected,
                    onSelect = ::selectVoice,
                    onPreview = { previewController?.preview(it.engine, it.voice, it.systemDefault) },
                )
            }
        }
        loadVoices()
    }

    private fun loadVoices() {
        viewLifecycleOwner.lifecycleScope.launch {
            val groups = withContext(IO) {
                TtsEngineStore.engines()
                    .filter { it.enabled }
                    .map { engine -> VoiceGroup(engine, voiceOptions(engine)) }
                    .filter { it.options.isNotEmpty() }
            }
            state = state.copy(loading = false, groups = groups)
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

    private fun isSelected(option: TtsVoiceOption): Boolean {
        val activeEngineId = runCatching { TtsEngineStore.activeEngineId() }.getOrDefault("")
        val activeVoiceId = runCatching {
            TtsEngineStore.engine(activeEngineId)?.activeVoiceId
        }.getOrNull()
        return activeEngineId == option.engine.id && if (option.systemDefault) {
            activeVoiceId.isNullOrBlank()
        } else {
            activeVoiceId == option.voice.id
        }
    }

    private fun selectVoice(option: TtsVoiceOption) {
        val player = activity as? ReadAloudPlayerActivity ?: return
        val wasRun = BaseReadAloudService.isRun
        val oldEngineType = runCatching { TtsEngineStore.activeEngine().type }.getOrNull()
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
                if (wasRun) {
                    ReadAloud.play(
                        player,
                        play = true,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadAloudVoiceSheetContent(
    state: VoiceDrawerState,
    isSelected: (TtsVoiceOption) -> Boolean,
    onSelect: (TtsVoiceOption) -> Unit,
    onPreview: (TtsVoiceOption) -> Unit,
) {
    val drawerHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    var query by remember { mutableStateOf("") }
    var selectedLanguages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedGenders by remember { mutableStateOf<Set<String>>(emptySet()) }
    val voices = state.groups.flatMap { it.options }.map { it.voice }
    val languageOptions = remember(voices) {
        TtsVoiceFilterSupport.availableLanguageLabels(voices)
    }
    val genderOptions = remember(voices) {
        listOf("男", "女").filter { label ->
            voices.any { TtsVoiceFilterSupport.genderLabel(it.gender) == label }
        }
    }
    val filteredGroups = state.groups.mapNotNull { group ->
        val options = group.options.filter { option ->
            val languageMatch = selectedLanguages.isEmpty() ||
                TtsVoiceFilterSupport.languageLabels(option.voice.language)
                    .any { it in selectedLanguages }
            val genderMatch = selectedGenders.isEmpty() ||
                TtsVoiceFilterSupport.genderLabel(option.voice.gender)
                    ?.let { it in selectedGenders } == true
            option.matchesName(query) && languageMatch && genderMatch
        }
        group.takeIf { options.isNotEmpty() }?.copy(options = options)
    }
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
            NgLongDrawerHeader(title = "朗读音色", centerTitle = true)
            ListeningSearchField(
                query = query,
                onQueryChange = { query = it },
                hint = "搜索发音人名称",
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (languageOptions.isNotEmpty() || genderOptions.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    languageOptions.forEach { label ->
                        ListeningChip(
                            label = label,
                            selected = label in selectedLanguages,
                            onClick = {
                                selectedLanguages = selectedLanguages.toggled(label)
                            },
                        )
                    }
                    genderOptions.forEach { label ->
                        ListeningChip(
                            label = label,
                            selected = label in selectedGenders,
                            onClick = { selectedGenders = selectedGenders.toggled(label) },
                        )
                    }
                }
            }
            when {
                state.loading -> ListeningLoadingState("正在加载发音人…")
                filteredGroups.isEmpty() -> ListeningLoadingState("没有匹配的发音人")
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    filteredGroups.forEach { group ->
                        item(key = "header:${group.engine.id}") {
                            Text(
                                text = group.engine.name,
                                color = Color(NgTheme.colors.primary),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
                            )
                        }
                        items(group.options, key = { it.previewKey() }) { option ->
                            VoiceSelectionCard(
                                option = option,
                                selected = isSelected(option),
                                previewState = state.preview.takeIf {
                                    it.key == option.previewKey()
                                }?.state ?: TtsVoicePreviewState.IDLE,
                                onSelect = { onSelect(option) },
                                onPreview = { onPreview(option) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun VoiceSelectionCard(
    option: TtsVoiceOption,
    selected: Boolean,
    previewState: TtsVoicePreviewState,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
) {
    val shape = RoundedCornerShape(15.dp)
    val labels = buildList {
        TtsVoiceFilterSupport.languageLabels(option.voice.language).forEach(::add)
        TtsVoiceFilterSupport.genderLabel(option.voice.gender)?.let(::add)
        option.voice.style?.takeIf { it.isNotBlank() }?.let(::add)
        option.voice.tags.take(3).forEach(::add)
    }.distinct()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(NgTheme.colors.surface).copy(alpha = 0.84f))
            .border(
                1.dp,
                if (selected) Color(NgTheme.colors.primary) else Color.Transparent,
                shape,
            )
            .combinedClickable(onClick = onSelect, onLongClick = onPreview)
            .padding(start = 14.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = null,
            tint = Color(NgTheme.colors.primary),
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        ) {
            Text(
                text = option.voice.name,
                color = Color(NgTheme.colors.onSurface),
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (labels.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    labels.forEach { label ->
                        Text(
                            text = label,
                            color = Color(NgTheme.colors.onSurfaceVariant),
                            fontSize = 10.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(NgTheme.colors.selectedContainer).copy(alpha = 0.60f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onPreview),
            contentAlignment = Alignment.Center,
        ) {
            when (previewState) {
                TtsVoicePreviewState.LOADING -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color(NgTheme.colors.primary),
                    strokeWidth = 2.dp,
                )
                TtsVoicePreviewState.PLAYING -> Icon(
                    imageVector = Icons.Rounded.StopCircle,
                    contentDescription = "停止试听",
                    tint = Color(NgTheme.colors.primary),
                )
                TtsVoicePreviewState.IDLE -> Icon(
                    imageVector = Icons.Rounded.Headphones,
                    contentDescription = "试听",
                    tint = Color(NgTheme.colors.onSurfaceVariant),
                )
            }
        }
        Icon(
            imageVector = if (selected) Icons.Rounded.CheckCircle
            else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = if (selected) "已选择" else null,
            tint = if (selected) Color(NgTheme.colors.primary)
            else Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.45f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ListeningSettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = Color(NgTheme.colors.primary),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 14.dp, bottom = 6.dp),
        )
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
private fun ListeningDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(1.dp)
            .background(Color(NgTheme.colors.outlineVariant).copy(alpha = 0.22f)),
    )
}

@Composable
private fun ListeningActionRow(
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
private fun ListeningSelectionCard(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(15.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(NgTheme.colors.surface).copy(alpha = 0.84f))
            .border(
                1.dp,
                if (selected) Color(NgTheme.colors.primary) else Color.Transparent,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color(NgTheme.colors.onSurface),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = summary,
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Icon(
            imageVector = if (selected) Icons.Rounded.CheckCircle
            else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) Color(NgTheme.colors.primary)
            else Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun ListeningSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(NgTheme.colors.surface).copy(alpha = 0.86f))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = Color(NgTheme.colors.primary),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(9.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(
                color = Color(NgTheme.colors.onSurface),
                fontSize = 14.sp,
            ),
            cursorBrush = SolidColor(Color(NgTheme.colors.primary)),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        text = hint,
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 14.sp,
                    )
                }
                inner()
            },
        )
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
private fun ListeningLoadingState(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 14.sp,
        )
    }
}

private fun Set<String>.toggled(value: String): Set<String> =
    if (value in this) this - value else this + value
