package io.legado.app.ui.config

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Image as ImageIcon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.google.gson.JsonObject
import io.legado.app.R
import io.legado.app.help.ai.AiChatClient
import io.legado.app.help.ai.AiChatContextEvent
import io.legado.app.help.ai.AiChatContextEventType
import io.legado.app.help.ai.AiChatContextManager
import io.legado.app.help.ai.AiChatContextUsage
import io.legado.app.help.ai.AiChatCompactionRecord
import io.legado.app.help.ai.AiChatPromptUsageAnchor
import io.legado.app.help.ai.AiChatHistoryState
import io.legado.app.help.ai.AiChatHistoryStore
import io.legado.app.help.ai.AiChatInteraction
import io.legado.app.help.ai.AiChatInteractionOption
import io.legado.app.help.ai.AiChatInteractionParser
import io.legado.app.help.ai.AiChatInteractionSubmit
import io.legado.app.help.ai.AiChatInteractionType
import io.legado.app.help.ai.AiChatMessageSnapshot
import io.legado.app.help.ai.AiChatSessionSnapshot
import io.legado.app.help.ai.AiChatStreamUpdate
import io.legado.app.help.ai.AiChatTurnResult
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.ai.AiMemoryTraceItem
import io.legado.app.help.ai.AiPendingToolCall
import io.legado.app.help.ai.AiSkillRegistry
import io.legado.app.help.ai.AiSkillScope
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.ui.widget.compose.NgExpandableChildRow
import io.legado.app.ui.widget.compose.NgExpandableSectionHeader
import io.legado.app.ui.widget.compose.NgFunctionMenu
import io.legado.app.ui.widget.compose.NgFunctionMenuAction
import io.legado.app.ui.widget.compose.NgListBadge
import io.legado.app.ui.widget.compose.NgListBadgeTone
import io.legado.app.ui.widget.compose.toggleNgExpandedKey
import io.legado.app.ui.widget.dialog.applyNgWindow
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.dpToPx
import io.legado.app.utils.openUrl
import io.legado.app.web.mcp.McpInternalToolCatalog
import io.legado.app.web.mcp.McpInternalToolCapability
import io.legado.app.web.mcp.McpInternalToolModule
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Chat shell adapted from RikkaHub's ChatPage / ChatInput / ChatMessage structure.
 *
 * The UI keeps RikkaHub's drawer + top bar + message list + bottom input shape, while
 * generation is routed through Legado's AiChatClient and built-in MCP channel.
 */
class AiChatActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ENTRY = "entry"
        const val EXTRA_AVAILABLE_CONTEXTS = "available_contexts"
        const val EXTRA_LOADED_SKILL_IDS = "loaded_skill_ids"
        const val EXTRA_CONTEXT_ATTACHMENTS = "context_attachments"
        const val EXTRA_EXPAND_SUGGESTIONS = "expand_suggestions"
        const val ENTRY_BOOKSHELF = "bookshelf"
        const val ENTRY_BOOK_DETAIL = "book_detail"
        const val ENTRY_BOOK_SCAN = "book_scan"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = true
        setContent {
            RikkaChatTheme {
                AiChatRoute(
                    onBack = { finish() }
                )
            }
        }
    }
}

private enum class AiChatInputAttachmentType {
    CONTEXT,
    SKILL
}

private data class AiChatInputAttachment(
    val id: String,
    val type: AiChatInputAttachmentType,
    val title: String,
    val subtitle: String,
    val prompt: String,
    val suggestions: List<String> = emptyList()
)

@Composable
private fun RikkaChatTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val accent = Color(context.accentColor)
    val background = Color(context.backgroundColor)
    val surface = Color(context.bottomBackground)
    val onAccent = if (ColorUtils.isColorLight(context.accentColor)) {
        Color(0xFF1D1B20)
    } else {
        Color.White
    }
    val colorScheme = lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.18f),
        onPrimaryContainer = Color(0xFF24124C),
        secondary = Color(0xFF675C73),
        secondaryContainer = accent.copy(alpha = 0.10f),
        tertiary = accent,
        tertiaryContainer = accent.copy(alpha = 0.14f),
        background = background,
        onBackground = Color(0xFF1D1B20),
        surface = surface,
        onSurface = Color(0xFF1D1B20),
        surfaceVariant = Color(0xFFE9E1EC),
        onSurfaceVariant = Color(0xFF49454F),
        outline = Color(0xFF7A757F),
        outlineVariant = Color(0xFFCBC4CF)
    )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}

@Composable
private fun ChatBackgroundImage(
    drawableProvider: () -> Drawable,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageDrawable(drawableProvider())
            }
        },
        update = { imageView ->
            imageView.setImageDrawable(drawableProvider())
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiChatRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val chatClient = remember { AiChatClient() }
    val entrySource = remember {
        (context as? AiChatActivity)?.intent?.getStringExtra(AiChatActivity.EXTRA_ENTRY)
    }
    val preloadSkillIds = remember {
        (context as? AiChatActivity)
            ?.intent
            ?.getStringArrayListExtra(AiChatActivity.EXTRA_LOADED_SKILL_IDS)
            .orEmpty()
    }
    val preloadContextAttachments = remember {
        buildIntentContextInputAttachments(
            (context as? AiChatActivity)
                ?.intent
                ?.getStringArrayListExtra(AiChatActivity.EXTRA_CONTEXT_ATTACHMENTS)
                .orEmpty()
        )
    }
    val expandSuggestionsOnEntry = remember {
        (context as? AiChatActivity)
            ?.intent
            ?.getBooleanExtra(AiChatActivity.EXTRA_EXPAND_SUGGESTIONS, false) == true
    }
    val entryAttachments = remember(entrySource, preloadSkillIds, preloadContextAttachments) {
        (buildEntryInputAttachments(entrySource) +
            buildSkillInputAttachments(preloadSkillIds) +
            preloadContextAttachments)
            .distinctBy { it.id }
    }
    val availableContextAttachments = remember(entrySource) {
        val contextSources = (context as? AiChatActivity)
            ?.intent
            ?.getStringArrayListExtra(AiChatActivity.EXTRA_AVAILABLE_CONTEXTS)
            .orEmpty()
        buildContextInputAttachments((listOfNotNull(entrySource) + contextSources).distinct())
    }
    val inputAttachments = remember(entrySource) {
        mutableStateListOf<AiChatInputAttachment>().apply {
            addAll(entryAttachments)
        }
    }
    val inputAttachmentUsageKey = inputAttachments.joinToString(separator = "|") { attachment ->
        "${attachment.id}:${attachment.prompt.hashCode()}"
    }
    val uploadMessages = remember {
        mutableStateListOf<JsonObject>().apply {
            add(chatClient.newSystemMessage())
        }
    }
    var contextUsage by remember {
        mutableStateOf(AiChatContextManager.usage(uploadMessages))
    }
    var contextStatusText by remember { mutableStateOf<String?>(null) }
    var contextCompactionCount by remember { mutableIntStateOf(0) }
    var showContextUsageDialog by remember { mutableStateOf(false) }
    var contextUsageRevision by remember { mutableIntStateOf(0) }
    var manualContextCompacting by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<ChatUiMessage>() }
    val sessions = remember { mutableStateListOf<AiChatSessionSnapshot>() }
    val listState = rememberLazyListState()
    var activeSessionId by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sendingJob by remember { mutableStateOf<Job?>(null) }
    var activeLoadingMessageId by remember { mutableStateOf<String?>(null) }
    var activeLoadingStartedAt by remember { mutableStateOf<Long?>(null) }
    var activeStreamContentTarget by remember { mutableStateOf("") }
    var activeStreamReasoningTarget by remember { mutableStateOf("") }
    var activeStreamToolTraceTarget by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeStreamMemoryTraceTarget by remember { mutableStateOf<List<AiMemoryTraceItem>>(emptyList()) }
    var selectedDrawerIndex by remember { mutableIntStateOf(2) }
    var configVersion by remember { mutableIntStateOf(0) }
    var previewMode by remember { mutableStateOf(false) }
    var globalSearchMode by remember { mutableStateOf(false) }
    var globalSearchQuery by remember { mutableStateOf("") }
    var statsMode by remember { mutableStateOf(false) }
    var exportSelectionMode by remember { mutableStateOf(false) }
    var exportFormatDialog by remember { mutableStateOf(false) }
    var showContextAttachmentSheet by remember { mutableStateOf(false) }
    var showSkillAttachmentSheet by remember { mutableStateOf(false) }
    var showMcpCapabilitySheet by remember { mutableStateOf(false) }
    val enabledMcpCapabilityIds = remember {
        mutableStateListOf<String>().apply {
            addAll(defaultMcpCapabilityIds(entrySource))
        }
    }
    var contextPreviewAttachments by remember { mutableStateOf<List<AiChatInputAttachment>>(emptyList()) }
    var historyLoaded by remember { mutableStateOf(false) }
    var historyManageMode by remember { mutableStateOf(false) }
    var pendingDeleteHistoryIds by remember { mutableStateOf<List<String>>(emptyList()) }
    val selectedExportMessageIds = remember { mutableStateListOf<String>() }
    val selectedHistoryIds = remember { mutableStateListOf<String>() }
    val selectedModelLabel = remember(configVersion) {
        AiAssistantConfigUi.selectedModelLabel()
    }
    val selectedModelIconRes = remember(configVersion) {
        AiAssistantConfigUi.selectedModelIconRes()
    }
    val selectedReasoningEnabled = remember(configVersion) {
        AiAssistantConfigUi.selectedReasoningEnabled()
    }
    val internalMcpEnabled = remember(configVersion) {
        AiConfig.internalMcpEnabled
    }
    val skillSuggestions = inputAttachments
        .filter { it.type == AiChatInputAttachmentType.SKILL }
        .flatMap { it.suggestions }
        .distinct()
    var suggestionsExpanded by remember { mutableStateOf(expandSuggestionsOnEntry) }
    val currentTitle = sessions.firstOrNull { it.id == activeSessionId }?.title
        ?: messages.deriveChatTitle()
        ?: "新聊天"

    fun updateMessage(id: String, transform: (ChatUiMessage) -> ChatUiMessage) {
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) {
            messages[index] = transform(messages[index])
        }
    }

    fun updateDeliveryState(ids: Collection<String>, state: ChatDeliveryState) {
        ids.forEach { id ->
            updateMessage(id) { message -> message.copy(deliveryState = state) }
        }
    }

    fun syncActiveSkillContext() {
        val skill = inputAttachments.lastOrNull {
            it.type == AiChatInputAttachmentType.SKILL
        }
        AiChatContextManager.syncActiveSkill(
            messages = uploadMessages,
            title = skill?.title,
            prompt = skill?.prompt
        )
    }

    fun refreshContextUsage() {
        syncActiveSkillContext()
        val revision = ++contextUsageRevision
        val pendingContexts = inputAttachments.filter {
            it.type == AiChatInputAttachmentType.CONTEXT
        }
        val snapshot = buildList {
            addAll(uploadMessages.map { it.deepCopy().asJsonObject })
            if (pendingContexts.isNotEmpty()) {
                add(chatClient.newUserMessage(buildUserUploadContent("", pendingContexts)))
            }
        }
        val promptUsageAnchor = messages.latestPromptUsageAnchor()
        val mcpCapabilityIds = enabledMcpCapabilityIds.toList()
        contextUsage = AiChatContextManager.usage(snapshot)
        scope.launch {
            val measured = withContext(Dispatchers.IO) {
                runCatching {
                    chatClient.estimateContextUsage(
                        snapshot,
                        promptUsageAnchor,
                        enabledMcpCapabilityIds = mcpCapabilityIds
                    )
                }
                    .getOrElse { AiChatContextManager.usage(snapshot) }
            }
            if (revision == contextUsageRevision) {
                contextUsage = measured
            }
        }
    }

    LaunchedEffect(Unit) {
        val history = withContext(Dispatchers.IO) {
            AiChatHistoryStore.load()
        }
        sessions.clear()
        sessions += history.sessions
        historyLoaded = true
        if (sending || messages.isNotEmpty()) {
            return@LaunchedEffect
        }
        activeSessionId = UUID.randomUUID().toString()
        uploadMessages.clear()
        uploadMessages += chatClient.newSystemMessage()
        refreshContextUsage()
        messages.clear()
        selectedDrawerIndex = 2
    }
    LaunchedEffect(configVersion) {
        refreshContextUsage()
    }
    LaunchedEffect(historyLoaded, inputAttachmentUsageKey) {
        val loadedSkills = inputAttachments.filter {
            it.type == AiChatInputAttachmentType.SKILL
        }
        if (loadedSkills.size > 1) {
            val activeSkillId = loadedSkills.last().id
            inputAttachments.removeAll { attachment ->
                attachment.type == AiChatInputAttachmentType.SKILL && attachment.id != activeSkillId
            }
            return@LaunchedEffect
        }
        if (historyLoaded) {
            refreshContextUsage()
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    LaunchedEffect(activeLoadingMessageId, activeLoadingStartedAt, sending) {
        val loadingId = activeLoadingMessageId ?: return@LaunchedEffect
        val startedAt = activeLoadingStartedAt ?: return@LaunchedEffect
        while (sending && activeLoadingMessageId == loadingId) {
            updateMessage(loadingId) { message ->
                if (message.loading) {
                    message.copy(elapsedMs = System.currentTimeMillis() - startedAt)
                } else {
                    message
                }
            }
            delay(250)
        }
    }
    LaunchedEffect(activeLoadingMessageId, sending) {
        val loadingId = activeLoadingMessageId ?: return@LaunchedEffect
        while (sending && activeLoadingMessageId == loadingId) {
            updateMessage(loadingId) { message ->
                if (!message.loading) {
                    message
                } else {
                    val visibleContent = message.content
                        .takeUnless { it == "正在思考..." }
                        .orEmpty()
                    val nextContent = nextStreamingText(
                        current = visibleContent,
                        target = activeStreamContentTarget
                    )
                    val nextReasoning = nextStreamingText(
                        current = message.reasoning.orEmpty(),
                        target = activeStreamReasoningTarget
                    )
                    message.copy(
                        content = nextContent.ifBlank { "正在思考..." },
                        reasoning = nextReasoning.takeIf { it.isNotBlank() },
                        toolTrace = activeStreamToolTraceTarget
                            .takeIf { it.isNotEmpty() }
                            ?: message.toolTrace,
                        memoryTrace = activeStreamMemoryTraceTarget
                            .takeIf { it.isNotEmpty() }
                            ?: message.memoryTrace
                    )
                }
            }
            delay(32)
        }
    }
    LaunchedEffect(contextStatusText) {
        val current = contextStatusText ?: return@LaunchedEffect
        if (current.startsWith("上下文已")) {
            delay(2500)
            if (contextStatusText == current) {
                contextStatusText = null
            }
        }
    }
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    BackHandler(enabled = globalSearchMode) {
        globalSearchMode = false
        globalSearchQuery = ""
        selectedDrawerIndex = 2
    }
    BackHandler(enabled = statsMode) {
        statsMode = false
        selectedDrawerIndex = 2
    }
    BackHandler(enabled = previewMode) {
        previewMode = false
    }
    BackHandler(enabled = exportSelectionMode) {
        exportSelectionMode = false
        exportFormatDialog = false
        selectedExportMessageIds.clear()
    }
    BackHandler(enabled = historyManageMode) {
        historyManageMode = false
        selectedHistoryIds.clear()
    }

    fun placeholder(label: String) {
        Toast.makeText(context, "$label 后续接入", Toast.LENGTH_SHORT).show()
    }

    fun openAssistantSettings() {
        context.startActivity(Intent(context, ConfigActivity::class.java).apply {
            putExtra("configTag", ConfigTag.AI_CONFIG)
            putExtra(AiConfigFragment.EXTRA_INITIAL_PAGE, AiConfigFragment.PAGE_ASSISTANT)
        })
    }

    fun saveHistory(activeId: String? = activeSessionId) {
        val state = AiChatHistoryState(
            activeSessionId = activeId,
            sessions = sessions.toList()
        )
        scope.launch(Dispatchers.IO) {
            AiChatHistoryStore.save(state)
        }
    }

    fun persistActiveSession() {
        val visibleMessages = messages.filterNot { it.loading }
        if (visibleMessages.isEmpty()) {
            return
        }
        val now = System.currentTimeMillis()
        val sessionId = activeSessionId ?: UUID.randomUUID().toString().also { activeSessionId = it }
        val oldSession = sessions.firstOrNull { it.id == sessionId }
        val snapshot = AiChatSessionSnapshot(
            id = sessionId,
            title = visibleMessages.deriveChatTitle() ?: oldSession?.title ?: "新聊天",
            createdAt = oldSession?.createdAt ?: now,
            updatedAt = now,
            isPinned = oldSession?.isPinned ?: false,
            messages = visibleMessages.map { it.toSnapshot() },
            loadedSkillIds = inputAttachments.loadedSkillIds(),
            enabledMcpCapabilityIds = enabledMcpCapabilityIds.toList(),
            uploadMessages = uploadMessages.map { it.deepCopy().asJsonObject }
        )
        sessions.removeAll { it.id == sessionId }
        sessions.add(0, snapshot)
        sessions.sortChatSessions()
        saveHistory(sessionId)
    }

    fun updateMcpCapabilitySelection(capabilityIds: Collection<String>) {
        enabledMcpCapabilityIds.replaceWith(
            McpInternalToolCatalog.normalizeCapabilityIds(capabilityIds)
        )
        refreshContextUsage()
        persistActiveSession()
    }

    fun startNewChat() {
        persistActiveSession()
        activeSessionId = UUID.randomUUID().toString()
        uploadMessages.clear()
        uploadMessages += chatClient.newSystemMessage()
        contextCompactionCount = 0
        contextStatusText = null
        messages.clear()
        inputAttachments.clear()
        enabledMcpCapabilityIds.replaceWith(defaultMcpCapabilityIds(entrySource))
        refreshContextUsage()
        selectedDrawerIndex = 2
        saveHistory(activeSessionId)
    }

    fun openSession(session: AiChatSessionSnapshot) {
        activeSessionId = session.id
        messages.replaceWith(session.messages.map { it.toUiMessage() })
        uploadMessages.replaceUploadMessages(session, chatClient)
        inputAttachments.replaceWith(buildSkillInputAttachments(session.loadedSkillIds))
        enabledMcpCapabilityIds.replaceWith(session.enabledMcpCapabilityIds)
        refreshContextUsage()
        contextCompactionCount = AiChatContextManager.compactionRevision(uploadMessages)
        contextStatusText = if (messages.any {
                it.role == ChatRole.USER && it.deliveryState == ChatDeliveryState.RECOVERABLE
            }) {
            "上次请求未完成，发送新消息或点击重新生成可恢复"
        } else {
            null
        }
        selectedDrawerIndex = 2
        saveHistory(session.id)
    }

    suspend fun confirmToolCalls(toolCalls: List<AiPendingToolCall>): Boolean {
        if (toolCalls.isEmpty()) return true
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val activity = context as? AiChatActivity
                if (activity == null || activity.isFinishing) {
                    continuation.resume(false)
                    return@suspendCancellableCoroutine
                }
                val summaries = toolCalls.map { it.toWriteOperationSummary() }
                var resumed = false
                fun resumeOnce(value: Boolean) {
                    if (resumed) return
                    resumed = true
                    continuation.resume(value)
                }
                val dialog = Dialog(activity)
                val root = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    background = ContextCompat.getDrawable(activity, R.drawable.ng_bg_dialog)
                    clipToOutline = true
                }
                root.addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 10.dpToPx())
                    addView(TextView(activity).apply {
                        text = "确认写操作"
                        setTextColor(ContextCompat.getColor(activity, R.color.ng_on_surface))
                        textSize = 24f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    })
                })
                val scrollView = ScrollView(activity).apply {
                    isFillViewport = false
                    addView(LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(TextView(activity).apply {
                            text = "AI 请求执行以下写操作，确认后会写入或修改本地数据。"
                            setTextColor(ContextCompat.getColor(activity, R.color.ng_on_surface_variant))
                            textSize = 15f
                            setLineSpacing(2f, 1.05f)
                        })
                        summaries.forEachIndexed { index, summary ->
                            addView(createWriteOperationSummaryView(activity, index + 1, summary))
                        }
                    })
                }
                root.addView(scrollView, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0
                ).apply {
                    weight = 1f
                    leftMargin = 24.dpToPx()
                    rightMargin = 24.dpToPx()
                    bottomMargin = 10.dpToPx()
                })
                root.addView(LinearLayout(activity).apply {
                    gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.END
                    setPadding(24.dpToPx(), 10.dpToPx(), 24.dpToPx(), 20.dpToPx())
                    background = ContextCompat.getDrawable(activity, R.drawable.ng_bg_dialog_action_bar)
                    addView(TextView(activity).apply {
                        text = "取消"
                        gravity = android.view.Gravity.CENTER
                        setTextColor(ContextCompat.getColor(activity, R.color.ng_primary))
                        textSize = 14f
                        includeFontPadding = false
                        background = ContextCompat.getDrawable(activity, R.drawable.ng_bg_button_secondary)
                        setOnClickListener {
                            dialog.dismiss()
                            resumeOnce(false)
                        }
                    }, LinearLayout.LayoutParams(
                        76.dpToPx(),
                        36.dpToPx()
                    ).apply {
                        rightMargin = 8.dpToPx()
                    })
                    addView(TextView(activity).apply {
                        text = "执行"
                        gravity = android.view.Gravity.CENTER
                        setTextColor(ContextCompat.getColor(activity, R.color.ng_on_primary))
                        textSize = 14f
                        includeFontPadding = false
                        background = ContextCompat.getDrawable(activity, R.drawable.ng_bg_button_primary)
                        setOnClickListener {
                            dialog.dismiss()
                            resumeOnce(true)
                        }
                    }, LinearLayout.LayoutParams(
                        76.dpToPx(),
                        36.dpToPx()
                    ))
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ))
                dialog.setContentView(root)
                dialog.setOnCancelListener {
                        resumeOnce(false)
                }
                continuation.invokeOnCancellation {
                    dialog.dismiss()
                }
                dialog.show()
                dialog.applyNgWindow(height = (activity.resources.displayMetrics.heightPixels * 0.62f).toInt())
            }
        }
    }

    fun sendCurrentMessages(activeUserMessageIds: List<String> = emptyList()) {
        sending = true
        val promptUsageAnchor = messages.latestPromptUsageAnchor()
        val loadingId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        activeLoadingMessageId = loadingId
        activeLoadingStartedAt = startedAt
        activeStreamContentTarget = ""
        activeStreamReasoningTarget = ""
        activeStreamToolTraceTarget = emptyList()
        activeStreamMemoryTraceTarget = emptyList()
        val uploadMessagesBeforeSend = uploadMessages.map { it.deepCopy().asJsonObject }
        val mcpCapabilityIds = enabledMcpCapabilityIds.toList()
        messages += ChatUiMessage(
            id = loadingId,
            role = ChatRole.ASSISTANT,
            content = "正在思考...",
            elapsedMs = 0L,
            loading = true
        )
        scope.launch {
            listState.scrollToItem(messages.lastIndex)
        }
        sendingJob?.cancel()
        sendingJob = scope.launch {
            val requestMessages = uploadMessagesBeforeSend
                .map { it.deepCopy().asJsonObject }
                .toMutableList()
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    chatClient.send(
                        messages = requestMessages,
                        promptUsageAnchor = promptUsageAnchor,
                        onStreamUpdate = { update: AiChatStreamUpdate ->
                            scope.launch {
                                if (activeLoadingMessageId == loadingId) {
                                    activeStreamContentTarget = update.content
                                    activeStreamReasoningTarget = update.reasoning.orEmpty()
                                    activeStreamToolTraceTarget = update.toolTrace
                                    activeStreamMemoryTraceTarget = update.memoryTrace
                                }
                            }
                        },
                        onContextEvent = { event: AiChatContextEvent ->
                            scope.launch {
                                when (event.type) {
                                    AiChatContextEventType.STARTED -> {
                                        event.beforeUsage?.let { contextUsage = it }
                                        contextStatusText = "正在自动压缩上下文..."
                                    }
                                    AiChatContextEventType.COMPLETED -> {
                                        val after = event.afterTokens ?: event.beforeTokens
                                        event.afterUsage?.let { contextUsage = it }
                                        event.compaction?.let {
                                            contextCompactionCount = it.revision
                                        }
                                        contextStatusText =
                                            "上下文已自动压缩 · ${formatContextTokens(event.beforeTokens)} → ${formatContextTokens(after)}"
                                    }
                                }
                            }
                        },
                        enabledMcpCapabilityIds = mcpCapabilityIds,
                        onToolConfirmationRequired = { pendingCalls ->
                            confirmToolCalls(pendingCalls)
                        }
                    )
                }
            }
            val elapsedMs = System.currentTimeMillis() - startedAt
            val canceled = result.exceptionOrNull() is CancellationException
            result.onSuccess {
                updateDeliveryState(activeUserMessageIds, ChatDeliveryState.SENT)
                uploadMessages.clear()
                uploadMessages.addAll(
                    it.contextMessages.map { message -> message.deepCopy().asJsonObject }
                )
                val loadingIndex = messages.indexOfFirst { message -> message.id == loadingId }
                val completedMessage = it.toUiMessage(elapsedMs)
                val compactionMessages = it.contextCompactions.map { record ->
                    ChatUiMessage(
                        role = ChatRole.ASSISTANT,
                        content = "上下文已自动压缩",
                        contextCompaction = record
                    )
                }
                if (loadingIndex >= 0) {
                    messages.removeAt(loadingIndex)
                    messages.addAll(loadingIndex, compactionMessages + completedMessage)
                } else {
                    messages += compactionMessages
                    messages += completedMessage
                }
                contextUsage = it.contextUsage
                contextCompactionCount = AiChatContextManager.compactionRevision(uploadMessages)
            }.onFailure {
                messages.removeAll { message -> message.id == loadingId }
                uploadMessages.clear()
                uploadMessages.addAll(uploadMessagesBeforeSend)
                contextStatusText = null
                updateDeliveryState(
                    activeUserMessageIds,
                    if (canceled) ChatDeliveryState.RECOVERABLE else ChatDeliveryState.FAILED
                )
                if (it !is CancellationException) {
                    messages += ChatUiMessage(
                        role = ChatRole.ASSISTANT,
                        content = it.localizedMessage ?: it.toString(),
                        meta = "error"
                    )
                }
            }
            persistActiveSession()
            sending = false
            sendingJob = null
            activeLoadingMessageId = null
            activeLoadingStartedAt = null
            activeStreamContentTarget = ""
            activeStreamReasoningTarget = ""
            activeStreamToolTraceTarget = emptyList()
            activeStreamMemoryTraceTarget = emptyList()
            refreshContextUsage()
            if (canceled) {
                contextStatusText = null
                return@launch
            }
            val queuedMessages = messages.filter {
                it.role == ChatRole.USER && it.deliveryState == ChatDeliveryState.QUEUED
            }
            if (queuedMessages.isNotEmpty()) {
                val queuedIds = queuedMessages.map { it.id }
                queuedMessages.forEach { message ->
                    uploadMessages += chatClient.newUserMessage(
                        AiChatContextManager.removeEmbeddedSkill(
                            message.uploadContent?.takeIf { it.isNotBlank() } ?: message.content
                        )
                    )
                }
                updateDeliveryState(queuedIds, ChatDeliveryState.IN_FLIGHT)
                refreshContextUsage()
                persistActiveSession()
                sendCurrentMessages(queuedIds)
            }
        }
    }

    fun stopSending() {
        if (!sending) return
        sendingJob?.cancel()
    }

    fun manuallyCompressContext() {
        if (sending || manualContextCompacting) return
        manualContextCompacting = true
        sending = true
        val promptUsageAnchor = messages.latestPromptUsageAnchor()
        val snapshot = uploadMessages.map { it.deepCopy().asJsonObject }.toMutableList()
        val mcpCapabilityIds = enabledMcpCapabilityIds.toList()
        sendingJob = scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    chatClient.compactContext(
                        messages = snapshot,
                        promptUsageAnchor = promptUsageAnchor,
                        enabledMcpCapabilityIds = mcpCapabilityIds,
                        onContextEvent = { event ->
                            scope.launch {
                                when (event.type) {
                                    AiChatContextEventType.STARTED -> {
                                        event.beforeUsage?.let { contextUsage = it }
                                        contextStatusText = "正在手动压缩上下文..."
                                    }

                                    AiChatContextEventType.COMPLETED -> {
                                        val after = event.afterTokens ?: event.beforeTokens
                                        event.afterUsage?.let { contextUsage = it }
                                        event.compaction?.let {
                                            contextCompactionCount = it.revision
                                        }
                                        contextStatusText =
                                            "上下文已手动压缩 · ${formatContextTokens(event.beforeTokens)} → ${formatContextTokens(after)}"
                                    }
                                }
                            }
                        }
                    )
                }
            }
            val canceled = result.exceptionOrNull() is CancellationException
            result.onSuccess { compacted ->
                uploadMessages.clear()
                uploadMessages.addAll(
                    compacted.contextMessages.map { message -> message.deepCopy().asJsonObject }
                )
                contextUsage = compacted.contextUsage
                contextCompactionCount = compacted.record.revision
                messages += ChatUiMessage(
                    role = ChatRole.ASSISTANT,
                    content = "上下文已手动压缩",
                    contextCompaction = compacted.record
                )
                persistActiveSession()
            }.onFailure { error ->
                contextStatusText = null
                if (!canceled) {
                    Toast.makeText(
                        context,
                        error.localizedMessage ?: "上下文压缩失败",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            manualContextCompacting = false
            sending = false
            sendingJob = null
            refreshContextUsage()
            if (canceled) return@launch
            val queuedMessages = messages.filter {
                it.role == ChatRole.USER && it.deliveryState == ChatDeliveryState.QUEUED
            }
            if (queuedMessages.isNotEmpty()) {
                val queuedIds = queuedMessages.map { it.id }
                queuedMessages.forEach { message ->
                    uploadMessages += chatClient.newUserMessage(
                        AiChatContextManager.removeEmbeddedSkill(
                            message.uploadContent?.takeIf { it.isNotBlank() } ?: message.content
                        )
                    )
                }
                updateDeliveryState(queuedIds, ChatDeliveryState.IN_FLIGHT)
                refreshContextUsage()
                persistActiveSession()
                sendCurrentMessages(queuedIds)
            }
        }
    }

    fun sendMessage(messageOverride: String? = null) {
        val content = (messageOverride ?: input).trim()
        if (content.isBlank()) {
            Toast.makeText(context, R.string.ai_chat_empty, Toast.LENGTH_SHORT).show()
            return
        }
        input = ""
        val uploadContent = buildUserUploadContent(content, inputAttachments)
        val userMessage = ChatUiMessage(
            role = ChatRole.USER,
            content = content,
            deliveryState = ChatDeliveryState.QUEUED,
            uploadContent = uploadContent
        )
        messages += userMessage
        scope.launch {
            listState.scrollToItem(messages.lastIndex)
        }
        inputAttachments.removeAll { it.type == AiChatInputAttachmentType.CONTEXT }
        persistActiveSession()
        if (sending) return
        val recoverableMessages = messages.filter {
            it.role == ChatRole.USER && it.deliveryState == ChatDeliveryState.RECOVERABLE
        }
        val queuedMessages = messages.filter {
            it.role == ChatRole.USER && it.deliveryState == ChatDeliveryState.QUEUED
        }
        val queuedIds = (recoverableMessages + queuedMessages).map { it.id }
        queuedMessages.forEach { message ->
            uploadMessages += chatClient.newUserMessage(
                AiChatContextManager.removeEmbeddedSkill(
                    message.uploadContent?.takeIf { it.isNotBlank() } ?: message.content
                )
            )
        }
        syncActiveSkillContext()
        updateDeliveryState(queuedIds, ChatDeliveryState.IN_FLIGHT)
        refreshContextUsage()
        persistActiveSession()
        sendCurrentMessages(queuedIds)
    }

    fun regenerateMessage(message: ChatUiMessage) {
        if (sending) {
            Toast.makeText(context, "正在生成中", Toast.LENGTH_SHORT).show()
            return
        }
        val index = messages.indexOfFirst { it.id == message.id }
        if (index < 0) {
            return
        }
        val truncatedMessages = when (message.role) {
            ChatRole.USER -> messages.take(index + 1)
            ChatRole.ASSISTANT -> messages.take(index)
        }.filterNot { it.loading }
        if (truncatedMessages.none { it.role == ChatRole.USER }) {
            Toast.makeText(context, "没有可重新生成的用户消息", Toast.LENGTH_SHORT).show()
            return
        }
        val preservedHistory = AiChatContextManager.historyForRegeneration(
            messages = uploadMessages,
            targetRole = when (message.role) {
                ChatRole.USER -> "user"
                ChatRole.ASSISTANT -> "assistant"
            },
            targetContent = when (message.role) {
                ChatRole.USER -> AiChatContextManager.removeEmbeddedSkill(
                    message.uploadContent ?: message.content
                )
                ChatRole.ASSISTANT -> message.content
            }
        )
        messages.replaceWith(truncatedMessages)
        uploadMessages.clear()
        uploadMessages.addAll(preservedHistory ?: rebuildUploadMessages(chatClient, messages))
        syncActiveSkillContext()
        val activeUserIds = if (message.role == ChatRole.USER) listOf(message.id) else emptyList()
        updateDeliveryState(activeUserIds, ChatDeliveryState.IN_FLIGHT)
        persistActiveSession()
        sendCurrentMessages(activeUserIds)
    }

    fun deleteMessage(message: ChatUiMessage) {
        if (sending) {
            Toast.makeText(context, "正在生成中，暂不能删除消息", Toast.LENGTH_SHORT).show()
            return
        }
        val removed = messages.removeAll { it.id == message.id }
        if (!removed) {
            return
        }
        uploadMessages.clear()
        uploadMessages.addAll(rebuildUploadMessages(chatClient, messages))
        refreshContextUsage()
        contextCompactionCount = AiChatContextManager.compactionRevision(uploadMessages)
        if (messages.any { !it.loading }) {
            persistActiveSession()
        } else {
            activeSessionId?.let { sessionId ->
                sessions.removeAll { it.id == sessionId }
                saveHistory(sessionId)
            }
        }
    }

    fun toggleFavorite(message: ChatUiMessage) {
        val index = messages.indexOfFirst { it.id == message.id }
        if (index < 0) {
            return
        }
        val favorite = !messages[index].favorite
        messages[index] = messages[index].copy(favorite = favorite)
        persistActiveSession()
        Toast.makeText(
            context,
            if (favorite) "已添加收藏" else "已取消收藏",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun startExportSelection(message: ChatUiMessage) {
        val index = messages.indexOfFirst { it.id == message.id }
        if (index < 0) {
            return
        }
        exportSelectionMode = true
        selectedExportMessageIds.clear()
        selectedExportMessageIds.addAll(
            messages
                .take(index + 1)
                .filterNot { it.loading }
                .map { it.id }
        )
    }

    fun cancelExportSelection() {
        exportSelectionMode = false
        exportFormatDialog = false
        selectedExportMessageIds.clear()
    }

    fun invertExportSelection() {
        val selected = selectedExportMessageIds.toSet()
        val allIds = messages.filterNot { it.loading }.map { it.id }
        selectedExportMessageIds.clear()
        selectedExportMessageIds.addAll(allIds.filterNot { it in selected })
    }

    fun confirmExportSelection() {
        if (selectedExportMessageIds.isEmpty()) {
            Toast.makeText(context, "请选择要导出的消息", Toast.LENGTH_SHORT).show()
            return
        }
        exportFormatDialog = true
    }

    fun exportSelectedMessages(format: ChatExportFormat) {
        val selectedIds = selectedExportMessageIds.toSet()
        val selectedMessages = messages.filter { it.id in selectedIds && !it.loading }
        if (selectedMessages.isEmpty()) {
            Toast.makeText(context, "请选择要导出的消息", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            when (format) {
                ChatExportFormat.MARKDOWN -> exportChatMessagesAsMarkdown(context, currentTitle, selectedMessages)
                ChatExportFormat.IMAGE -> exportChatMessagesAsImage(context, currentTitle, selectedMessages)
            }
        }.onSuccess { file ->
            shareChatExportFile(context, file, format.mimeType)
            cancelExportSelection()
        }.onFailure {
            Toast.makeText(context, "导出失败：${it.localizedMessage ?: it}", Toast.LENGTH_SHORT).show()
        }
    }

    fun forkMessage(message: ChatUiMessage) {
        if (sending) {
            Toast.makeText(context, "正在生成中，暂不能创建分支", Toast.LENGTH_SHORT).show()
            return
        }
        val index = messages.indexOfFirst { it.id == message.id }
        if (index < 0) {
            return
        }
        val forkedMessages = messages.take(index + 1).filterNot { it.loading }
        if (forkedMessages.isEmpty()) {
            Toast.makeText(context, "没有可创建分支的消息", Toast.LENGTH_SHORT).show()
            return
        }
        persistActiveSession()
        activeSessionId = UUID.randomUUID().toString()
        messages.replaceWith(forkedMessages)
        uploadMessages.clear()
        uploadMessages.addAll(rebuildUploadMessages(chatClient, messages))
        refreshContextUsage()
        contextCompactionCount = AiChatContextManager.compactionRevision(uploadMessages)
        contextStatusText = null
        selectedDrawerIndex = 2
        persistActiveSession()
        Toast.makeText(context, "已创建分支", Toast.LENGTH_SHORT).show()
    }

    fun pinSessions(sessionIds: Collection<String>) {
        val targetIds = sessionIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toSet()
        if (targetIds.isEmpty()) {
            return
        }
        val now = System.currentTimeMillis()
        sessions.indices.forEach { index ->
            if (sessions[index].id in targetIds) {
                sessions[index] = sessions[index].copy(
                    isPinned = true,
                    updatedAt = now
                )
            }
        }
        sessions.sortChatSessions()
        saveHistory(activeSessionId)
        Toast.makeText(context, "已置顶 ${targetIds.size} 条聊天记录", Toast.LENGTH_SHORT).show()
    }

    fun pinSession(sessionId: String?) {
        if (sessionId.isNullOrBlank()) {
            persistActiveSession()
        }
        val targetId = sessionId ?: activeSessionId ?: return
        pinSessions(listOf(targetId))
    }

    fun deleteSessions(sessionIds: Collection<String>) {
        val targetIds = sessionIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toSet()
        if (targetIds.isEmpty()) {
            return
        }
        val wasActive = activeSessionId in targetIds
        val deleteCount = sessions.count { it.id in targetIds }
        sessions.removeAll { it.id in targetIds }
        selectedHistoryIds.removeAll(targetIds)
        scope.launch(Dispatchers.IO) {
            AiChatHistoryStore.deleteSessions(targetIds)
        }
        if (wasActive) {
            val nextSession = sessions.firstOrNull()
            if (nextSession != null) {
                openSession(nextSession)
            } else {
                activeSessionId = UUID.randomUUID().toString()
                messages.clear()
                uploadMessages.clear()
                uploadMessages += chatClient.newSystemMessage()
                inputAttachments.clear()
                contextCompactionCount = 0
                contextStatusText = null
                manualContextCompacting = false
                refreshContextUsage()
                selectedDrawerIndex = 2
                saveHistory(activeSessionId)
            }
        } else {
            saveHistory(activeSessionId)
        }
        if (selectedHistoryIds.isEmpty()) {
            historyManageMode = false
        }
        Toast.makeText(context, "已删除 $deleteCount 条聊天记录", Toast.LENGTH_SHORT).show()
    }

    fun deleteFavorite(item: DrawerFavoriteItem) {
        val itemSession = item.session
        if (itemSession == null || itemSession.id == activeSessionId) {
            val index = messages.indexOfFirst { it.id == item.messageId }
            if (index >= 0) {
                messages[index] = messages[index].copy(favorite = false)
                persistActiveSession()
                Toast.makeText(context, "已取消收藏", Toast.LENGTH_SHORT).show()
            }
            return
        }
        val sessionIndex = sessions.indexOfFirst { it.id == itemSession.id }
        if (sessionIndex < 0) {
            return
        }
        val updatedSession = sessions[sessionIndex].copy(
            messages = sessions[sessionIndex].messages.map { message ->
                if (message.id == item.messageId) {
                    message.copy(favorite = false)
                } else {
                    message
                }
            }
        )
        sessions[sessionIndex] = updatedSession
        saveHistory(activeSessionId)
        Toast.makeText(context, "已取消收藏", Toast.LENGTH_SHORT).show()
    }

    val chatBackgroundDrawable = remember(context) {
        runCatching {
            ThemeConfig.getBgImage(context, context.resources.displayMetrics)
        }.getOrNull()
    }
    val sessionSnapshots = sessions.toList()
    val currentMessageSnapshots = messages.toList()
    val drawerHistoryGroups = remember(sessionSnapshots) {
        buildDrawerHistoryGroups(sessionSnapshots)
    }
    val drawerFavoriteItems = remember(sessionSnapshots, activeSessionId, currentMessageSnapshots) {
        buildDrawerFavoriteItems(sessionSnapshots, activeSessionId, currentMessageSnapshots)
    }
    if (pendingDeleteHistoryIds.isNotEmpty()) {
        val deleteCount = pendingDeleteHistoryIds.size
        AlertDialog(
            onDismissRequest = { pendingDeleteHistoryIds = emptyList() },
            title = { Text("删除聊天记录") },
            text = { Text("确定删除 $deleteCount 条聊天记录？删除后不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ids = pendingDeleteHistoryIds
                        pendingDeleteHistoryIds = emptyList()
                        deleteSessions(ids)
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteHistoryIds = emptyList() }) {
                    Text("取消")
                }
            }
        )
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            RikkaChatDrawer(
                selectedIndex = selectedDrawerIndex,
                historyGroups = drawerHistoryGroups,
                historyLoaded = historyLoaded,
                activeSessionId = activeSessionId,
                favoriteItems = drawerFavoriteItems,
                backgroundDrawable = chatBackgroundDrawable,
                historyManageMode = historyManageMode,
                selectedHistoryIds = selectedHistoryIds.toSet(),
                onSelect = {
                    selectedDrawerIndex = it
                    historyManageMode = false
                    selectedHistoryIds.clear()
                    if (it == 1) {
                        previewMode = false
                        globalSearchMode = true
                        globalSearchQuery = ""
                        scope.launch { drawerState.close() }
                    }
                },
                onSessionSelect = {
                    persistActiveSession()
                    openSession(it)
                    scope.launch { drawerState.close() }
                },
                onPinSession = { session ->
                    pinSession(session.id)
                },
                onDeleteSession = { session ->
                    pendingDeleteHistoryIds = listOf(session.id)
                },
                onPinHistoryGroup = { group ->
                    pinSessions(group.items.map { it.session.id })
                },
                onDeleteHistoryGroup = { group ->
                    pendingDeleteHistoryIds = group.items.map { it.session.id }
                },
                onToggleHistoryManage = {
                    historyManageMode = !historyManageMode
                    selectedHistoryIds.clear()
                },
                onToggleHistorySelection = { session ->
                    if (session.id in selectedHistoryIds) {
                        selectedHistoryIds.remove(session.id)
                    } else {
                        selectedHistoryIds.add(session.id)
                    }
                },
                onSelectAllHistory = {
                    selectedHistoryIds.clear()
                    selectedHistoryIds.addAll(drawerHistoryGroups.flatMap { group ->
                        group.items.map { it.session.id }
                    })
                },
                onDeleteSelectedHistory = {
                    pendingDeleteHistoryIds = selectedHistoryIds.toList()
                },
                onPinFavorite = { item ->
                    pinSession(item.session?.id)
                },
                onDeleteFavorite = ::deleteFavorite,
                onOpenStats = {
                    persistActiveSession()
                    statsMode = true
                    globalSearchMode = false
                    previewMode = false
                    selectedDrawerIndex = 2
                    scope.launch { drawerState.close() }
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    openAssistantSettings()
                },
                onNewChat = {
                    startNewChat()
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            if (chatBackgroundDrawable != null) {
                ChatBackgroundImage(
                    drawableProvider = { chatBackgroundDrawable },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.66f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.74f)
                            )
                        )
                    )
            )
            if (globalSearchMode) {
                GlobalMessageSearchPage(
                    query = globalSearchQuery,
                    onQueryChange = { globalSearchQuery = it },
                    sessions = sessions,
                    activeSessionId = activeSessionId,
                    currentMessages = messages,
                    onBack = {
                        globalSearchMode = false
                        globalSearchQuery = ""
                        selectedDrawerIndex = 2
                    },
                    onOpenResult = { result ->
                        persistActiveSession()
                        if (result.session != null && result.session.id != activeSessionId) {
                            openSession(result.session)
                        }
                        globalSearchMode = false
                        globalSearchQuery = ""
                        previewMode = false
                        selectedDrawerIndex = 2
                        scope.launch {
                            listState.animateScrollToItem(result.messageIndex.coerceAtLeast(0))
                        }
                    }
                )
            } else if (statsMode) {
                AiChatStatsPage(
                    stats = remember(sessionSnapshots, activeSessionId, currentMessageSnapshots) {
                        buildAiChatStats(sessionSnapshots, activeSessionId, currentMessageSnapshots)
                    },
                    onBack = {
                        statsMode = false
                        selectedDrawerIndex = 2
                    }
                )
            } else {
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0),
                    topBar = {
                        Surface(
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.86f)
                        ) {
                            TopAppBar(
                                modifier = Modifier.statusBarsPadding(),
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Rounded.Menu, contentDescription = "Menu")
                                    }
                                },
                                title = {
                                    Column {
                                        Text(
                                            text = currentTitle,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "默认助手 / $selectedModelLabel",
                                            color = LocalContentColor.current.copy(alpha = 0.62f),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                },
                                actions = {
                                    IconButton(onClick = { previewMode = !previewMode }) {
                                        Icon(
                                            imageVector = if (previewMode) Icons.Rounded.Close else Icons.Rounded.Search,
                                            contentDescription = if (previewMode) "Close search" else "Search messages"
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            startNewChat()
                                        }
                                    ) {
                                        Icon(Icons.Rounded.AddComment, contentDescription = "New chat")
                                    }
                                }
                            )
                        }
                    },
                    bottomBar = {
                        Column {
                            AnimatedVisibility(visible = exportSelectionMode) {
                                ExportSelectionFloatingBar(
                                    selectedCount = selectedExportMessageIds.size,
                                    onCancel = ::cancelExportSelection,
                                    onInvert = ::invertExportSelection,
                                    onConfirm = ::confirmExportSelection
                                )
                            }
                            AnimatedVisibility(visible = !contextStatusText.isNullOrBlank()) {
                                Text(
                                    text = contextStatusText.orEmpty(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                    textAlign = TextAlign.Center
                                )
                            }
                            RikkaChatInput(
                                value = input,
                                onValueChange = { input = it },
                                sending = sending,
                                modelIconRes = selectedModelIconRes,
                                reasoningEnabled = selectedReasoningEnabled,
                                mcpEnabled = internalMcpEnabled && enabledMcpCapabilityIds.isNotEmpty(),
                                skillEnabled = inputAttachments.any {
                                    it.type == AiChatInputAttachmentType.SKILL
                                },
                                contextAvailable = availableContextAttachments.isNotEmpty()
                                        || inputAttachments.any {
                                    it.type == AiChatInputAttachmentType.CONTEXT
                                },
                                suggestionAvailable = skillSuggestions.isNotEmpty(),
                                suggestionExpanded = suggestionsExpanded,
                                contextUsage = contextUsage,
                                attachments = inputAttachments,
                                skills = inputAttachments.filter {
                                    it.type == AiChatInputAttachmentType.SKILL
                                },
                                onSend = ::sendMessage,
                                onStop = ::stopSending,
                                onModelClick = {
                                    AiAssistantConfigUi.showModelSelectSheet(context) {
                                        configVersion++
                                    }
                                },
                                onReasoningClick = {
                                    AiAssistantConfigUi.showReasoningSheet(context) {
                                        configVersion++
                                    }
                                },
                                onMcpClick = {
                                    if (internalMcpEnabled) {
                                        showMcpCapabilitySheet = true
                                    } else {
                                        AiAssistantConfigUi.showInternalMcpSheet(context) {
                                            configVersion++
                                        }
                                    }
                                },
                                onSuggestionClick = {
                                    suggestionsExpanded = !suggestionsExpanded
                                },
                                onContextUsageClick = {
                                    showContextUsageDialog = true
                                },
                                onSkillClick = {
                                    showSkillAttachmentSheet = true
                                },
                                onContextClick = {
                                    val loadedContexts = inputAttachments.filter {
                                        it.type == AiChatInputAttachmentType.CONTEXT
                                    }
                                    if (loadedContexts.isNotEmpty()) {
                                        contextPreviewAttachments = loadedContexts
                                    } else {
                                        showContextAttachmentSheet = true
                                    }
                                },
                                onContextPreview = { attachment ->
                                    contextPreviewAttachments = listOf(attachment)
                                },
                                onRemoveAttachment = { attachment ->
                                    inputAttachments.removeAll { it.id == attachment.id }
                                    refreshContextUsage()
                                },
                                onRemoveSkill = { skill ->
                                    inputAttachments.removeAll { it.id == skill.id }
                                    refreshContextUsage()
                                    persistActiveSession()
                                }
                            )
                        }
                    }
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        RikkaChatList(
                            padding = padding,
                            state = listState,
                            messages = messages,
                            previewMode = previewMode,
                            onRegenerate = ::regenerateMessage,
                            onDelete = ::deleteMessage,
                            onFork = ::forkMessage,
                            onToggleFavorite = ::toggleFavorite,
                            selectionMode = exportSelectionMode,
                            selectedIds = selectedExportMessageIds.toSet(),
                            onToggleSelection = { message ->
                                if (message.id in selectedExportMessageIds) {
                                    selectedExportMessageIds.remove(message.id)
                                } else {
                                    selectedExportMessageIds.add(message.id)
                                }
                            },
                            onExport = ::startExportSelection,
                            onInteractionSubmit = { prompt -> sendMessage(prompt) },
                            onJumpToMessage = { index ->
                                previewMode = false
                                scope.launch {
                                    listState.animateScrollToItem(index)
                                }
                            }
                        )
                        FloatingSkillSuggestionPanel(
                            suggestions = skillSuggestions,
                            expanded = suggestionsExpanded,
                            onSelect = { suggestion ->
                                suggestionsExpanded = false
                                sendMessage(suggestion)
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(
                                    end = 16.dp,
                                    bottom = padding.calculateBottomPadding() + 8.dp
                                )
                        )
                    }
                }
            }
        }
    }
    if (exportFormatDialog) {
        ChatExportFormatDialog(
            onDismiss = { exportFormatDialog = false },
            onExport = ::exportSelectedMessages
        )
    }
    if (showContextUsageDialog) {
        ContextUsageDialog(
            usage = contextUsage,
            compactionCount = maxOf(
                contextCompactionCount,
                messages.count { it.contextCompaction != null }
            ),
            sessionUsage = messages.contextSessionUsage(),
            manualCompressionAvailable = uploadMessages.any { message ->
                message.get("role")?.asString != "system"
            },
            manualCompacting = manualContextCompacting,
            onManualCompress = ::manuallyCompressContext,
            onDismiss = { showContextUsageDialog = false }
        )
    }
    if (showContextAttachmentSheet) {
        AiChatInputAttachmentSheet(
            title = "添加上下文",
            description = "上下文会显示在输入框内，并随下一条消息一次性发送给 AI。",
            emptyText = "当前入口没有可添加的上下文",
            availableAttachments = availableContextAttachments,
            loadedAttachmentIds = inputAttachments.map { it.id }.toSet(),
            onAdd = { attachment ->
                if (inputAttachments.none { it.id == attachment.id }) {
                    inputAttachments += attachment
                }
                refreshContextUsage()
                showContextAttachmentSheet = false
            },
            onDismiss = { showContextAttachmentSheet = false }
        )
    }
    if (contextPreviewAttachments.isNotEmpty()) {
        AiChatContextPreviewSheet(
            attachments = contextPreviewAttachments,
            onDismiss = { contextPreviewAttachments = emptyList() }
        )
    }
    if (showSkillAttachmentSheet) {
        AiChatInputAttachmentSheet(
            title = "加载技能",
            description = "选择新技能会替换当前技能，并持续用于当前会话。",
            emptyText = "暂无可在聊天中加载的技能",
            availableAttachments = buildAgentSkillInputAttachments(),
            loadedAttachmentIds = inputAttachments.map { it.id }.toSet(),
            onAdd = { attachment ->
                inputAttachments.removeAll {
                    it.type == AiChatInputAttachmentType.SKILL && it.id != attachment.id
                }
                if (inputAttachments.none { it.id == attachment.id }) {
                    inputAttachments += attachment
                    refreshContextUsage()
                    persistActiveSession()
                }
                showSkillAttachmentSheet = false
            },
            onDismiss = { showSkillAttachmentSheet = false }
        )
    }
    if (showMcpCapabilitySheet) {
        AiChatMcpCapabilitySheet(
            modules = McpInternalToolCatalog.modules,
            selectedCapabilityIds = enabledMcpCapabilityIds.toSet(),
            onSelectionChange = ::updateMcpCapabilitySelection,
            onDismiss = { showMcpCapabilitySheet = false }
        )
    }
}

@Composable
private fun AiChatStatsPage(
    stats: AiChatStats,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "stats_header") {
            Column {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
                ) {
                    Box(
                        modifier = Modifier.size(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
                Spacer(Modifier.height(26.dp))
                Text(
                    text = "统计",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        item(key = "token_activity") {
            AiTokenActivityCard(stats.tokenActivityPerDay)
        }
        item(key = "stats_grid") {
            AiChatStatsGrid(stats)
        }
    }
}

@Composable
private fun AiTokenActivityCard(tokenActivityPerDay: Map<LocalDate, Long>) {
    var mode by remember { mutableStateOf(TokenActivityMode.DAILY) }
    val bars = remember(tokenActivityPerDay, mode) {
        buildTokenActivityBars(tokenActivityPerDay, mode)
    }
    var selectedIndex by remember(bars) {
        mutableIntStateOf(
            bars.indexOfFirst { it.defaultSelected }
                .takeIf { it >= 0 }
                ?: bars.lastIndex
        )
    }
    val selectedBar = bars.getOrNull(selectedIndex)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Token 活动",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TokenActivityMode.entries.forEach { item ->
                    TokenActivityModeLabel(
                        text = item.title,
                        selected = mode == item,
                        onClick = { mode = item }
                    )
                }
            }
            Text(
                text = selectedBar?.let {
                    "${it.rangeLabel} · ${formatAiStatTokens(it.value)} Token"
                } ?: "暂无 Token 活动",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            )
            TokenActivityBarChart(
                bars = bars,
                selectedIndex = selectedIndex,
                onSelect = { selectedIndex = it }
            )
        }
    }
}

@Composable
private fun TokenActivityModeLabel(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (selected) 0.90f else 0.46f),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun TokenActivityBarChart(
    bars: List<TokenActivityBar>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val scrollState = rememberScrollState(initial = Int.MAX_VALUE)
    val maxValue = bars.maxOfOrNull { it.value } ?: 0L
    Row(
        modifier = Modifier.horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        bars.forEachIndexed { index, bar ->
            val selected = index == selectedIndex
            Column(
                modifier = Modifier
                    .width(22.dp)
                    .clickable { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier.height(116.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val heightRatio = if (maxValue > 0L) {
                        bar.value.toFloat() / maxValue.toFloat()
                    } else {
                        0f
                    }
                    val barHeight = if (bar.value > 0L) {
                        (10.dp + 104.dp * heightRatio).coerceAtMost(114.dp)
                    } else {
                        6.dp
                    }
                    Box(
                        modifier = Modifier
                            .width(if (selected) 14.dp else 12.dp)
                            .height(barHeight)
                            .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                            .background(
                                if (bar.value <= 0L) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                } else if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
                                }
                            )
                    )
                }
                Text(
                    text = bar.axisLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (selected) 0.90f else 0.56f),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun AiChatStatsGrid(stats: AiChatStats) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AiStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.History,
                label = "总对话数",
                value = formatAiStatCount(stats.totalConversations.toLong())
            )
            AiStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Description,
                label = "总消息数",
                value = formatAiStatCount(stats.totalMessages.toLong())
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AiStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Psychology,
                label = "输入 Token",
                value = formatAiStatTokens(stats.totalPromptTokens)
            )
            AiStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Psychology,
                label = "输出 Token",
                value = formatAiStatTokens(stats.totalCompletionTokens)
            )
        }
        if (stats.totalElapsedMs > 0L) {
            AiStatCard(
                modifier = Modifier.fillMaxWidth(),
                iconRes = R.drawable.ic_mingcute_time_line,
                label = "生成耗时",
                value = formatAiStatDuration(stats.totalElapsedMs)
            )
        }
    }
}

@Composable
private fun AiStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    @DrawableRes iconRes: Int? = null,
    label: String,
    value: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            } else if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GlobalMessageSearchPage(
    query: String,
    onQueryChange: (String) -> Unit,
    sessions: List<AiChatSessionSnapshot>,
    activeSessionId: String?,
    currentMessages: List<ChatUiMessage>,
    onBack: () -> Unit,
    onOpenResult: (GlobalMessageSearchResult) -> Unit
) {
    var submittedQuery by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        if (query.isBlank()) {
            submittedQuery = ""
        }
    }
    val results = remember(submittedQuery, sessions, activeSessionId, currentMessages) {
        buildGlobalMessageSearchResults(
            query = submittedQuery,
            sessions = sessions,
            activeSessionId = activeSessionId,
            currentMessages = currentMessages
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                enabled = query.isNotBlank(),
                onClick = {
                    onQueryChange("")
                    submittedQuery = ""
                }
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Clear search")
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "搜索消息",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索消息内容...") },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null)
            },
            trailingIcon = {
                IconButton(
                    enabled = query.isNotBlank(),
                    onClick = { submittedQuery = query.trim() }
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search")
                }
            },
            shape = RoundedCornerShape(32.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { submittedQuery = query.trim() }
            )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                submittedQuery.isBlank() -> {
                    Text(
                        text = "输入关键词并点击搜索以查找消息",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                results.isEmpty() -> {
                    Text(
                        text = "没有找到相关消息",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = results,
                            key = { "${it.sessionId.orEmpty()}:${it.messageId}:${it.messageIndex}" }
                        ) { result ->
                            GlobalSearchResultRow(
                                result = result,
                                query = submittedQuery,
                                onClick = { onOpenResult(result) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalSearchResultRow(
    result: GlobalMessageSearchResult,
    query: String,
    onClick: () -> Unit
) {
    val highlightColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
    val snippet = remember(result.snippet, query, highlightColor) {
        buildHighlightedText(
            text = result.snippet,
            query = query,
            highlightColor = highlightColor
        )
    }
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = result.sessionTitle.ifBlank { "新聊天" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = snippet,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${result.roleLabel} · ${formatChatSearchTime(result.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RikkaChatDrawer(
    selectedIndex: Int,
    historyGroups: List<DrawerHistoryGroup>,
    historyLoaded: Boolean,
    activeSessionId: String?,
    favoriteItems: List<DrawerFavoriteItem>,
    backgroundDrawable: Drawable?,
    historyManageMode: Boolean,
    selectedHistoryIds: Set<String>,
    onSelect: (Int) -> Unit,
    onSessionSelect: (AiChatSessionSnapshot) -> Unit,
    onPinSession: (AiChatSessionSnapshot) -> Unit,
    onDeleteSession: (AiChatSessionSnapshot) -> Unit,
    onPinHistoryGroup: (DrawerHistoryGroup) -> Unit,
    onDeleteHistoryGroup: (DrawerHistoryGroup) -> Unit,
    onToggleHistoryManage: () -> Unit,
    onToggleHistorySelection: (AiChatSessionSnapshot) -> Unit,
    onSelectAllHistory: () -> Unit,
    onDeleteSelectedHistory: () -> Unit,
    onPinFavorite: (DrawerFavoriteItem) -> Unit,
    onDeleteFavorite: (DrawerFavoriteItem) -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewChat: () -> Unit
) {
    val drawerWidth = LocalConfiguration.current.screenWidthDp.dp * 0.65f
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(drawerWidth),
        color = if (backgroundDrawable == null) {
            MaterialTheme.colorScheme.background
        } else {
            Color.Transparent
        },
        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (backgroundDrawable != null) {
                ChatBackgroundImage(
                    drawableProvider = { backgroundDrawable },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                DrawerActionRow(
                    icon = Icons.Rounded.Search,
                    label = "搜索聊天",
                    selected = selectedIndex == 1,
                    onClick = { onSelect(1) }
                )
                DrawerActionRow(
                    icon = Icons.Rounded.History,
                    label = "聊天历史",
                    selected = selectedIndex == 2,
                    onClick = { onSelect(2) }
                )
                DrawerActionRow(
                    icon = Icons.Rounded.Favorite,
                    label = "收藏内容",
                    selected = selectedIndex == 3,
                    onClick = { onSelect(3) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedIndex) {
                        3 -> DrawerFavoriteContent(
                            favoriteItems = favoriteItems,
                            onSessionSelect = onSessionSelect,
                            onPinFavorite = onPinFavorite,
                            onDeleteFavorite = onDeleteFavorite
                        )

                        else -> DrawerHistoryContent(
                            groups = historyGroups,
                            historyLoaded = historyLoaded,
                            activeSessionId = activeSessionId,
                            manageMode = historyManageMode,
                            selectedIds = selectedHistoryIds,
                            onSessionSelect = onSessionSelect,
                            onPinSession = onPinSession,
                            onDeleteSession = onDeleteSession,
                            onPinGroup = onPinHistoryGroup,
                            onDeleteGroup = onDeleteHistoryGroup,
                            onToggleManage = onToggleHistoryManage,
                            onToggleSelection = onToggleHistorySelection,
                            onSelectAll = onSelectAllHistory,
                            onDeleteSelected = onDeleteSelectedHistory,
                            onNewChat = onNewChat
                        )
                    }
                }
                DrawerBottomActions(
                    onOpenStats = onOpenStats,
                    onOpenSettings = onOpenSettings
                )
            }
        }
    }
}

@Composable
private fun DrawerBottomActions(
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DrawerBottomActionButton(
            iconRes = R.drawable.ic_solar_chart_bold,
            contentDescription = "统计",
            onClick = onOpenStats
        )
        Spacer(Modifier.width(14.dp))
        DrawerBottomActionButton(
            imageVector = Icons.Rounded.Settings,
            contentDescription = "设置",
            onClick = onOpenSettings
        )
    }
}

@Composable
private fun DrawerBottomActionButton(
    contentDescription: String,
    onClick: () -> Unit,
    @DrawableRes iconRes: Int? = null,
    imageVector: ImageVector? = null
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
        modifier = Modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (imageVector != null) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            } else if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = contentDescription,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun DrawerHistoryContent(
    groups: List<DrawerHistoryGroup>,
    historyLoaded: Boolean,
    activeSessionId: String?,
    manageMode: Boolean,
    selectedIds: Set<String>,
    onSessionSelect: (AiChatSessionSnapshot) -> Unit,
    onPinSession: (AiChatSessionSnapshot) -> Unit,
    onDeleteSession: (AiChatSessionSnapshot) -> Unit,
    onPinGroup: (DrawerHistoryGroup) -> Unit,
    onDeleteGroup: (DrawerHistoryGroup) -> Unit,
    onToggleManage: () -> Unit,
    onToggleSelection: (AiChatSessionSnapshot) -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onNewChat: () -> Unit
) {
    val groupTitles = groups.map { it.title }
    val collapsedGroups = remember(groupTitles) {
        mutableStateListOf(*groupTitles.toTypedArray())
    }
    val totalCount = groups.sumOf { it.items.size }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(key = "history_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DrawerSectionTitle(
                        text = if (manageMode) "聊天历史 · 已选 ${selectedIds.size}" else "聊天历史",
                        modifier = Modifier.weight(1f)
                    )
                    DrawerPlainTextButton(
                        text = if (manageMode) "取消" else "管理",
                        enabled = totalCount > 0,
                        onClick = onToggleManage
                    )
                }
                if (manageMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 2.dp, end = 2.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DrawerPlainTextButton(
                            text = "全选",
                            enabled = totalCount > 0,
                            onClick = onSelectAll
                        )
                        DrawerPlainTextButton(
                            text = "删除",
                            enabled = selectedIds.isNotEmpty(),
                            onClick = onDeleteSelected
                        )
                    }
                }
            }
        }
        if (!historyLoaded) {
            item(key = "history_loading") {
                Text(
                    text = "正在加载聊天历史",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        } else if (totalCount == 0) {
            item(key = "new_chat") {
                DrawerConversationRow(
                    label = "新聊天",
                    subtitle = "开始一次新的 AI 助理对话",
                    selected = true,
                    onClick = onNewChat
                )
            }
        } else {
            groups.forEach { group ->
                item(key = "group_${group.title}") {
                    val expanded = group.title !in collapsedGroups
                    DrawerHistoryGroupHeader(
                        group = group,
                        expanded = expanded,
                        onToggle = {
                            if (expanded) {
                                collapsedGroups.add(group.title)
                            } else {
                                collapsedGroups.remove(group.title)
                            }
                        },
                        onPinGroup = { onPinGroup(group) },
                        onDeleteGroup = { onDeleteGroup(group) }
                    )
                }
                if (group.title !in collapsedGroups) {
                    items(group.items, key = { it.session.id }) { item ->
                        val session = item.session
                        DrawerConversationRow(
                            label = item.title,
                            subtitle = item.subtitle,
                            selected = session.id == activeSessionId,
                            checked = session.id in selectedIds,
                            manageMode = manageMode,
                            onClick = {
                                if (manageMode) {
                                    onToggleSelection(session)
                                } else {
                                    onSessionSelect(session)
                                }
                            },
                            onPin = { onPinSession(session) },
                            onDelete = { onDeleteSession(session) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerHistoryGroupHeader(
    group: DrawerHistoryGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPinGroup: () -> Unit,
    onDeleteGroup: () -> Unit
) {
    Surface(
        onClick = onToggle,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 5.dp, end = 2.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) {
                    Icons.Rounded.KeyboardArrowDown
                } else {
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = group.title,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = group.items.size.toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            DrawerGroupMoreMenu(
                onPinGroup = onPinGroup,
                onDeleteGroup = onDeleteGroup
            )
        }
    }
}

@Composable
private fun DrawerPlainTextButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        },
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun DrawerFavoriteContent(
    favoriteItems: List<DrawerFavoriteItem>,
    onSessionSelect: (AiChatSessionSnapshot) -> Unit,
    onPinFavorite: (DrawerFavoriteItem) -> Unit,
    onDeleteFavorite: (DrawerFavoriteItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(key = "favorite_header") {
            DrawerSectionTitle("收藏内容")
        }
        if (favoriteItems.isEmpty()) {
            item(key = "favorite_empty") {
                Text(
                    text = "暂无收藏内容",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        } else {
            items(favoriteItems, key = { "${it.session?.id.orEmpty()}:${it.messageId}" }) { item ->
                Surface(
                    onClick = { item.session?.let(onSessionSelect) },
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.38f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(start = 10.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.preview,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        DrawerMoreMenu(
                            onPin = { onPinFavorite(item) },
                            onDelete = { onDeleteFavorite(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerSectionTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier.padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

@Composable
private fun DrawerActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DrawerConversationRow(
    label: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    checked: Boolean = false,
    manageMode: Boolean = false,
    onPin: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        color = when {
            checked -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
            selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            else -> Color.Transparent
        },
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 6.dp, end = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (manageMode) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { onClick() },
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(32.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!manageMode && onPin != null && onDelete != null) {
                DrawerMoreMenu(
                    onPin = onPin,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun DrawerMoreMenu(
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreHoriz,
                contentDescription = "更多",
                modifier = Modifier.size(20.dp)
            )
        }
        NgFunctionMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            actions = listOf(
                NgFunctionMenuAction(
                    icon = Icons.Rounded.PushPin,
                    text = "置顶",
                    onClick = {
                        expanded = false
                        onPin()
                    }
                ),
                NgFunctionMenuAction(
                    icon = Icons.Rounded.Delete,
                    text = "删除",
                    danger = true,
                    dividerBefore = true,
                    onClick = {
                        expanded = false
                        onDelete()
                    }
                )
            )
        )
    }
}

@Composable
private fun DrawerGroupMoreMenu(
    onPinGroup: () -> Unit,
    onDeleteGroup: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(30.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreHoriz,
                contentDescription = "分组操作",
                modifier = Modifier.size(18.dp)
            )
        }
        NgFunctionMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            actions = listOf(
                NgFunctionMenuAction(
                    icon = Icons.Rounded.PushPin,
                    text = "置顶分组",
                    onClick = {
                        expanded = false
                        onPinGroup()
                    }
                ),
                NgFunctionMenuAction(
                    icon = Icons.Rounded.Delete,
                    text = "清空分组",
                    danger = true,
                    dividerBefore = true,
                    onClick = {
                        expanded = false
                        onDeleteGroup()
                    }
                )
            )
        )
    }
}

@Composable
private fun RikkaChatList(
    padding: PaddingValues,
    state: LazyListState,
    messages: List<ChatUiMessage>,
    previewMode: Boolean,
    onRegenerate: (ChatUiMessage) -> Unit,
    onDelete: (ChatUiMessage) -> Unit,
    onFork: (ChatUiMessage) -> Unit,
    onToggleFavorite: (ChatUiMessage) -> Unit,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelection: (ChatUiMessage) -> Unit,
    onExport: (ChatUiMessage) -> Unit,
    onInteractionSubmit: (String) -> Unit,
    onJumpToMessage: (Int) -> Unit
) {
    if (previewMode) {
        RikkaChatSearchPreview(
            padding = padding,
            messages = messages,
            onJumpToMessage = onJumpToMessage
        )
        return
    }

    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = padding.calculateTopPadding() + 10.dp,
            end = 16.dp,
            bottom = padding.calculateBottomPadding() + 18.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            if (selectionMode && !message.loading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = message.id in selectedIds,
                        onCheckedChange = { onToggleSelection(message) },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        RikkaMessageItem(
                            message = message,
                            onRegenerate = { onRegenerate(message) },
                            onDelete = { onDelete(message) },
                            onFork = { onFork(message) },
                            onToggleFavorite = { onToggleFavorite(message) },
                            onExport = { onExport(message) },
                            onInteractionSubmit = onInteractionSubmit
                        )
                    }
                }
            } else {
                RikkaMessageItem(
                    message = message,
                    onRegenerate = { onRegenerate(message) },
                    onDelete = { onDelete(message) },
                    onFork = { onFork(message) },
                    onToggleFavorite = { onToggleFavorite(message) },
                    onExport = { onExport(message) },
                    onInteractionSubmit = onInteractionSubmit
                )
            }
        }
    }
}

@Composable
private fun RikkaChatSearchPreview(
    padding: PaddingValues,
    messages: List<ChatUiMessage>,
    onJumpToMessage: (Int) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val previewItems = messages
        .mapIndexed { index, message -> ChatPreviewItem(index, message) }
        .filter { (_, message) ->
            !message.loading &&
                message.content.isNotBlank() &&
                (searchQuery.isBlank() || message.content.contains(searchQuery, ignoreCase = true))
        }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding()
            )
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("搜索") },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear")
                    }
                }
            },
            shape = RoundedCornerShape(28.dp),
            singleLine = true
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(previewItems, key = { it.message.id }) { item ->
                MessagePreviewRow(
                    item = item,
                    searchQuery = searchQuery,
                    onClick = { onJumpToMessage(item.originalIndex) }
                )
            }
        }
    }
}

@Composable
private fun MessagePreviewRow(
    item: ChatPreviewItem,
    searchQuery: String,
    onClick: () -> Unit
) {
    val isUser = item.message.role == ChatRole.USER
    val snippet = remember(item.message.content, searchQuery) {
        extractMatchingSnippet(item.message.content.trim().ifBlank { "[...]" }, searchQuery)
    }
    val highlightedText = remember(snippet, searchQuery) {
        buildHighlightedText(
            text = snippet,
            query = searchQuery,
            highlightColor = Color(0xFFE8DDFF)
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.widthIn(max = 340.dp),
            shape = RoundedCornerShape(14.dp),
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
            }
        ) {
            Text(
                text = highlightedText,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RikkaMessageItem(
    message: ChatUiMessage,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    onFork: () -> Unit,
    onToggleFavorite: () -> Unit,
    onExport: () -> Unit,
    onInteractionSubmit: (String) -> Unit
) {
    message.contextCompaction?.let { record ->
        ContextCompactionEventItem(
            record = record,
            manual = message.content == "上下文已手动压缩"
        )
        return
    }
    val clipboard = LocalClipboardManager.current
    val isUser = message.role == ChatRole.USER
    var confirmAction by remember { mutableStateOf<PendingMessageAction?>(null) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isUser) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (message.deliveryState != ChatDeliveryState.SENT &&
                message.deliveryState != ChatDeliveryState.IN_FLIGHT
            ) {
                Text(
                    text = when (message.deliveryState) {
                        ChatDeliveryState.QUEUED -> "已排队"
                        ChatDeliveryState.IN_FLIGHT -> "发送中"
                        ChatDeliveryState.RECOVERABLE -> "待恢复"
                        ChatDeliveryState.FAILED -> "发送失败"
                        ChatDeliveryState.SENT -> ""
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (message.deliveryState == ChatDeliveryState.SENT ||
                message.deliveryState == ChatDeliveryState.RECOVERABLE ||
                message.deliveryState == ChatDeliveryState.FAILED
            ) {
                MessageActions(
                    message = message,
                    clipboard = clipboard,
                    showShareButton = false,
                    onRegenerate = { confirmAction = PendingMessageAction.REGENERATE },
                    onDelete = { confirmAction = PendingMessageAction.DELETE },
                    onFork = onFork,
                    onToggleFavorite = onToggleFavorite,
                    onExport = onExport
                )
            }
        } else {
            AssistantMessageHeader()
            if (message.loading) {
                ThinkingLoadingLine()
                if (!message.reasoning.isNullOrBlank()) {
                    ReasoningEntry(
                        reasoning = message.reasoning,
                        elapsedMs = message.elapsedMs,
                        thinking = true,
                        defaultExpanded = true
                    )
                }
                val streamingContent = message.content
                    .takeUnless { it == "正在思考..." }
                    .orEmpty()
                if (streamingContent.isNotBlank()) {
                    MarkdownMessageText(
                        content = streamingContent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                            .padding(top = 2.dp)
                    )
                }
                if (message.toolTrace.isNotEmpty()) {
                    ToolTraceEntry(message.toolTrace)
                }
                if (message.memoryTrace.isNotEmpty()) {
                    MemoryTraceEntry(message.memoryTrace)
                }
            } else {
                val interactionRender = remember(message.content) {
                    AiChatInteractionParser.parse(message.content)
                }
                if (!message.reasoning.isNullOrBlank()) {
                    ReasoningEntry(
                        reasoning = message.reasoning,
                        elapsedMs = message.elapsedMs
                    )
                }
                val displayContent = if (interactionRender.interactions.isNotEmpty()) {
                    interactionRender.content
                } else {
                    interactionRender.content.ifBlank { message.content }
                }
                if (displayContent.isNotBlank()) {
                    MarkdownMessageText(
                        content = displayContent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                            .padding(top = 2.dp)
                    )
                }
                interactionRender.interactions.forEach { interaction ->
                    ChatInteractionBlock(
                        interaction = interaction,
                        onSubmit = onInteractionSubmit
                    )
                }
                if (message.toolTrace.isNotEmpty()) {
                    ToolTraceEntry(message.toolTrace)
                }
                if (message.memoryTrace.isNotEmpty()) {
                    MemoryTraceEntry(message.memoryTrace)
                }
                MessageActions(
                    message = message,
                    clipboard = clipboard,
                    showShareButton = true,
                    onRegenerate = { confirmAction = PendingMessageAction.REGENERATE },
                    onDelete = { confirmAction = PendingMessageAction.DELETE },
                    onFork = onFork,
                    onToggleFavorite = onToggleFavorite,
                    onExport = onExport
                )
                if (!message.meta.isNullOrBlank()) {
                    Text(
                        text = message.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
    confirmAction?.let { action ->
        MessageConfirmDialog(
            action = action,
            onDismiss = { confirmAction = null },
            onConfirm = {
                confirmAction = null
                when (action) {
                    PendingMessageAction.REGENERATE -> onRegenerate()
                    PendingMessageAction.DELETE -> onDelete()
                }
            }
        )
    }
}

@Composable
private fun ContextCompactionEventItem(
    record: AiChatCompactionRecord,
    manual: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        ) {
            Text(
                text = "上下文已${if (manual) "手动" else "自动"}压缩 · " +
                    "${formatContextTokenValue(record.beforeTokens)} 到 " +
                    formatContextTokenValue(record.afterTokens),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun ChatInteractionBlock(
    interaction: AiChatInteraction,
    onSubmit: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border = BorderStroke(
            width = 0.8.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (interaction.title.isNotBlank()) {
                Text(
                    text = interaction.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (interaction.description.isNotBlank()) {
                Text(
                    text = interaction.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val minContentWidth = maxWidth
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    when (interaction.type) {
                        AiChatInteractionType.ACTIONS -> InteractionActions(
                            interaction = interaction,
                            minWidth = minContentWidth,
                            onSubmit = onSubmit
                        )

                        AiChatInteractionType.SINGLE_CHOICE -> InteractionSingleChoice(
                            interaction = interaction,
                            minWidth = minContentWidth,
                            onSubmit = onSubmit
                        )

                        AiChatInteractionType.MULTI_CHOICE -> InteractionMultiChoice(
                            interaction = interaction,
                            minWidth = minContentWidth,
                            onSubmit = onSubmit
                        )

                        AiChatInteractionType.CONFIRM -> InteractionConfirm(
                            interaction = interaction,
                            minWidth = minContentWidth,
                            onSubmit = onSubmit
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractionActions(
    interaction: AiChatInteraction,
    minWidth: Dp,
    onSubmit: (String) -> Unit
) {
    Column(
        modifier = Modifier.widthIn(min = minWidth),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        interaction.options.chunked(3).forEach { rowOptions ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowOptions.forEach { option ->
                    InteractionChip(
                        text = option.label,
                        primary = false,
                        compact = true,
                        modifier = Modifier.widthIn(max = 148.dp),
                        onClick = {
                            onSubmit(
                                AiChatInteractionParser.buildPrompt(
                                    interaction = interaction,
                                    option = option,
                                    submit = interaction.submit
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InteractionSingleChoice(
    interaction: AiChatInteraction,
    minWidth: Dp,
    onSubmit: (String) -> Unit
) {
    var selectedValue by remember(interaction.id) {
        mutableStateOf(interaction.options.firstOrNull()?.value.orEmpty())
    }
    Column(
        modifier = Modifier.widthIn(min = minWidth),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        interaction.options.forEach { option ->
            InteractionOptionRow(
                option = option,
                minWidth = minWidth,
                selected = option.value == selectedValue,
                multiple = false,
                onClick = { selectedValue = option.value }
            )
        }
        val selected = interaction.options.firstOrNull { it.value == selectedValue }
        InteractionSubmitRow(
            enabled = selected != null,
            label = interaction.submit?.label ?: "确认",
            minWidth = minWidth,
            onSubmit = {
                selected?.let { option ->
                    onSubmit(
                        AiChatInteractionParser.buildPrompt(
                            interaction = interaction,
                            option = option,
                            submit = interaction.submit
                        )
                    )
                }
            }
        )
    }
}

@Composable
private fun InteractionMultiChoice(
    interaction: AiChatInteraction,
    minWidth: Dp,
    onSubmit: (String) -> Unit
) {
    var selectedValues by remember(interaction.id) { mutableStateOf(emptySet<String>()) }
    Column(
        modifier = Modifier.widthIn(min = minWidth),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        interaction.options.forEach { option ->
            InteractionOptionRow(
                option = option,
                minWidth = minWidth,
                selected = option.value in selectedValues,
                multiple = true,
                onClick = {
                    selectedValues = if (option.value in selectedValues) {
                        selectedValues - option.value
                    } else {
                        selectedValues + option.value
                    }
                }
            )
        }
        val selected = interaction.options.filter { it.value in selectedValues }
        InteractionSubmitRow(
            enabled = selected.isNotEmpty(),
            label = interaction.submit?.label ?: "确认",
            minWidth = minWidth,
            onSubmit = {
                onSubmit(
                    AiChatInteractionParser.buildPrompt(
                        interaction = interaction,
                        options = selected,
                        submit = interaction.submit
                    )
                )
            }
        )
    }
}

@Composable
private fun InteractionConfirm(
    interaction: AiChatInteraction,
    minWidth: Dp,
    onSubmit: (String) -> Unit
) {
    Row(
        modifier = Modifier.widthIn(min = minWidth),
        horizontalArrangement = Arrangement.End
    ) {
        interaction.cancel?.let { cancel ->
            InteractionChip(
                text = cancel.label.ifBlank { "取消" },
                primary = false,
                modifier = Modifier.padding(end = 8.dp),
                onClick = {
                    onSubmit(
                        AiChatInteractionParser.buildPrompt(
                            interaction = interaction,
                            submit = cancel
                        )
                    )
                }
            )
        }
        InteractionChip(
            text = interaction.submit?.label ?: "确认",
            primary = true,
            onClick = {
                onSubmit(
                    AiChatInteractionParser.buildPrompt(
                        interaction = interaction,
                        submit = interaction.submit ?: AiChatInteractionSubmit("确认", "确认")
                    )
                )
            }
        )
    }
}

@Composable
private fun InteractionOptionRow(
    option: AiChatInteractionOption,
    minWidth: Dp,
    selected: Boolean,
    multiple: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .widthIn(min = minWidth)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (multiple) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        } else {
            RadioButton(selected = selected, onClick = onClick)
        }
        Column {
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (option.description.isNotBlank()) {
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InteractionSubmitRow(
    enabled: Boolean,
    label: String,
    minWidth: Dp,
    onSubmit: () -> Unit
) {
    Row(
        modifier = Modifier.widthIn(min = minWidth),
        horizontalArrangement = Arrangement.End
    ) {
        InteractionChip(
            text = label,
            primary = true,
            enabled = enabled,
            onClick = onSubmit
        )
    }
}

@Composable
private fun InteractionChip(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = if (primary) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val containerColor = if (primary) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = containerColor.copy(alpha = if (enabled) containerColor.alpha else 0.35f),
        border = BorderStroke(0.8.dp, contentColor.copy(alpha = 0.16f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = if (compact) 10.dp else 12.dp,
                vertical = if (compact) 6.dp else 7.dp
            ),
            color = contentColor.copy(alpha = if (enabled) 1f else 0.45f),
            style = if (compact) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.labelLarge
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ThinkingLoadingLine() {
    Row(
        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Thinking",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
        )
        ThinkingDots()
    }
}

@Composable
private fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "thinking_dots")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing)
        ),
        label = "thinking_dots_progress"
    )
    val colors = listOf(
        Color(0xFF8F83F7),
        Color(0xFFA394FF),
        Color(0xFFC8BCFF)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        colors.forEachIndexed { index, color ->
            val phase = (progress - index * 0.16f + 1f) % 1f
            val peak = 1f - abs(phase * 2f - 1f)
            Box(
                modifier = Modifier
                    .offset(y = (0.8f - peak * 1.6f).dp)
                    .alpha(0.22f + peak * 0.58f)
                    .size(4.5.dp)
                    .background(color = color, shape = CircleShape)
            )
        }
    }
}

@Composable
private fun AssistantMessageHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RikkaAssistantAvatar(
            iconRes = currentAssistantModelIconRes(),
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = currentAssistantModelLabel(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun ReasoningEntry(
    reasoning: String,
    elapsedMs: Long?,
    thinking: Boolean = false,
    defaultExpanded: Boolean = false
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    val title = elapsedMs?.let {
        val prefix = if (thinking) "思考中" else "已思考"
        "$prefix ${formatElapsedSeconds(it)}"
    } ?: "思考内容"

    InlineDetailEntry(
        label = title,
        expanded = expanded,
        onClick = { expanded = !expanded }
    )

    AnimatedVisibility(visible = expanded) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 6.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MarkdownMessageText(
                    content = reasoning,
                    modifier = Modifier.fillMaxWidth(),
                    textSizeSp = 14f
                )
            }
        }
    }
}

@Composable
private fun MarkdownMessageText(
    content: String,
    modifier: Modifier = Modifier,
    textSizeSp: Float = 16f
) {
    val blocks = remember(content) { splitMarkdownBlocks(content) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is ChatMarkdownBlock.Text -> MarkdownTextBlock(
                    content = block.value,
                    textSizeSp = textSizeSp
                )

                is ChatMarkdownBlock.Code -> MarkdownCodeBlock(
                    language = block.language,
                    code = block.code
                )

                is ChatMarkdownBlock.Image -> MarkdownImageBlock(
                    alt = block.alt,
                    url = block.url
                )

                is ChatMarkdownBlock.Table -> MarkdownTableBlock(block.table)
            }
        }
    }
}

@Composable
private fun MarkdownTextBlock(
    content: String,
    modifier: Modifier = Modifier,
    textSizeSp: Float = 16f
) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(GlideImagesPlugin.create(Glide.with(context)))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .build()
    }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            android.widget.TextView(viewContext).apply {
                setTextColor(textColor)
                setLinkTextColor(linkColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                includeFontPadding = true
                linksClickable = true
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.setLinkTextColor(linkColor)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            textView.linksClickable = true
            textView.movementMethod = LinkMovementMethod.getInstance()
            markwon.setMarkdown(textView, content)
        }
    )
}

@Composable
private fun MarkdownCodeBlock(
    language: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "text" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        Toast.makeText(context, "已复制代码", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun MarkdownImageBlock(
    alt: String,
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        onClick = { context.openMarkdownImage(url) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp, max = 320.dp)
                .clip(RoundedCornerShape(12.dp)),
            factory = { viewContext ->
                ImageView(viewContext).apply {
                    adjustViewBounds = true
                    contentDescription = alt
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setOnClickListener { context.openMarkdownImage(url) }
                }
            },
            update = { imageView ->
                imageView.contentDescription = alt
                imageView.setOnClickListener { context.openMarkdownImage(url) }
                Glide.with(imageView)
                    .load(url)
                    .error(R.drawable.image_loading_error)
                    .into(imageView)
            }
        )
    }
}

@Composable
private fun MarkdownTableBlock(
    table: MarkdownTable,
    modifier: Modifier = Modifier
) {
    val columns = table.headers.size
    if (columns == 0) return
    val rows = listOf(table.headers) + table.rows
    val widths = remember(table) { table.columnWidths() }
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
    val headerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    val rowColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.24f)
    val alternateRowColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(rowColor)
        ) {
            rows.forEachIndexed { rowIndex, row ->
                Row {
                    repeat(columns) { columnIndex ->
                        val text = row.getOrNull(columnIndex).orEmpty()
                        val isHeader = rowIndex == 0
                        Box(
                            modifier = Modifier
                                .width(widths[columnIndex])
                                .heightIn(min = if (isHeader) 38.dp else 42.dp)
                                .background(
                                    when {
                                        isHeader -> headerColor
                                        rowIndex % 2 == 0 -> alternateRowColor
                                        else -> rowColor
                                    }
                                )
                                .then(
                                    Modifier
                                        .background(Color.Transparent)
                                )
                        ) {
                            Text(
                                text = tableCellText(text),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                style = if (isHeader) {
                                    MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                } else {
                                    MaterialTheme.typography.bodyMedium
                                },
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color.Transparent)
                            ) {
                                HorizontalDivider(
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                    color = borderColor
                                )
                                if (columnIndex > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .width(1.dp)
                                            .fillMaxHeight()
                                            .background(borderColor)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Context.openMarkdownImage(url: String) {
    val activity = this as? AppCompatActivity
    if (activity == null) {
        openUrl(url)
        return
    }
    runCatching {
        PhotoDialog(url).show(activity.supportFragmentManager, "ai_chat_markdown_image")
    }.onFailure {
        openUrl(url)
    }
}

private fun splitMarkdownBlocks(content: String): List<ChatMarkdownBlock> {
    val blocks = mutableListOf<ChatMarkdownBlock>()
    var cursor = 0
    fencedCodeBlockRegex.findAll(content).forEach { match ->
        if (match.range.first > cursor) {
            appendTextAndImages(content.substring(cursor, match.range.first), blocks)
        }
        blocks += ChatMarkdownBlock.Code(
            language = match.groupValues[1].trim(),
            code = match.groupValues[2].trimEnd()
        )
        cursor = match.range.last + 1
    }
    if (cursor < content.length) {
        appendTextAndImages(content.substring(cursor), blocks)
    }
    return blocks.ifEmpty { listOf(ChatMarkdownBlock.Text(content)) }
}

private fun appendTextAndImages(
    text: String,
    blocks: MutableList<ChatMarkdownBlock>
) {
    appendTextTablesAndImages(text, blocks)
}

private fun appendTextTablesAndImages(
    text: String,
    blocks: MutableList<ChatMarkdownBlock>
) {
    val lines = text.lines()
    val buffer = StringBuilder()
    var index = 0
    while (index < lines.size) {
        val table = parseMarkdownTable(lines, index)
        if (table != null) {
            appendInlineTextAndImages(buffer.toString(), blocks)
            buffer.clear()
            blocks += ChatMarkdownBlock.Table(table.table)
            index = table.nextIndex
        } else {
            buffer.appendLine(lines[index])
            index++
        }
    }
    appendInlineTextAndImages(buffer.toString(), blocks)
}

private fun appendInlineTextAndImages(
    text: String,
    blocks: MutableList<ChatMarkdownBlock>
) {
    var cursor = 0
    markdownImageRegex.findAll(text).forEach { match ->
        if (match.range.first > cursor) {
            val plain = text.substring(cursor, match.range.first)
            if (plain.isNotBlank()) {
                blocks += ChatMarkdownBlock.Text(plain.trim())
            }
        }
        blocks += ChatMarkdownBlock.Image(
            alt = match.groupValues[1],
            url = match.groupValues[2].removeSurrounding("<", ">")
        )
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        val plain = text.substring(cursor)
        if (plain.isNotBlank()) {
            blocks += ChatMarkdownBlock.Text(plain.trim())
        }
    }
}

private sealed interface ChatMarkdownBlock {
    data class Text(val value: String) : ChatMarkdownBlock
    data class Code(val language: String, val code: String) : ChatMarkdownBlock
    data class Image(val alt: String, val url: String) : ChatMarkdownBlock
    data class Table(val table: MarkdownTable) : ChatMarkdownBlock
}

private data class MarkdownTable(
    val headers: List<String>,
    val rows: List<List<String>>
)

private data class MarkdownTableParseResult(
    val table: MarkdownTable,
    val nextIndex: Int
)

private fun parseMarkdownTable(lines: List<String>, startIndex: Int): MarkdownTableParseResult? {
    if (startIndex + 1 >= lines.size) return null
    val headerLine = lines[startIndex]
    val separatorLine = lines[startIndex + 1]
    if (!headerLine.contains('|') || !isMarkdownTableSeparator(separatorLine)) return null
    val headers = splitMarkdownTableRow(headerLine)
    if (headers.isEmpty()) return null
    val rows = mutableListOf<List<String>>()
    var index = startIndex + 2
    while (index < lines.size && lines[index].contains('|') && lines[index].isNotBlank()) {
        rows += splitMarkdownTableRow(lines[index])
        index++
    }
    if (rows.isEmpty()) return null
    return MarkdownTableParseResult(
        table = MarkdownTable(headers, rows),
        nextIndex = index
    )
}

private fun isMarkdownTableSeparator(line: String): Boolean {
    val cells = splitMarkdownTableRow(line)
    return cells.isNotEmpty() && cells.all { cell ->
        cell.matches(Regex(""":?-{3,}:?"""))
    }
}

private fun splitMarkdownTableRow(line: String): List<String> {
    return line.trim()
        .trim('|')
        .split('|')
        .map { it.trim() }
}

private fun MarkdownTable.columnWidths(): List<Dp> {
    val allRows = listOf(headers) + rows
    return headers.indices.map { columnIndex ->
        val maxUnits = allRows.maxOfOrNull { row ->
            row.getOrNull(columnIndex).orEmpty().tableDisplayUnits()
        } ?: 0f
        val preferred = (40f + maxUnits * 15f).roundToInt()
        preferred.coerceIn(72, if (columnIndex < 2) 320 else 380).dp
    }
}

private fun tableCellText(raw: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        tableBoldRegex.findAll(raw).forEach { match ->
            if (match.range.first > cursor) {
                append(raw.substring(cursor, match.range.first))
            }
            pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
            append(match.groupValues[2])
            pop()
            cursor = match.range.last + 1
        }
        if (cursor < raw.length) {
            append(raw.substring(cursor))
        }
    }
}

private fun String.tableDisplayUnits(): Float {
    return tablePlainText().fold(0f) { total, char ->
        total + when {
            char.code > 0xFF -> 1f
            char.isLetterOrDigit() -> 0.62f
            char.isWhitespace() -> 0.35f
            else -> 0.5f
        }
    }
}

private fun String.tablePlainText(): String {
    return tableBoldRegex.replace(this) { match ->
        match.groupValues[2]
    }
}

private val fencedCodeBlockRegex = Regex("""```([^\n`]*)\n([\s\S]*?)(?:\n```|$)""")
private val markdownImageRegex = Regex("""!\[([^]]*)]\((<[^>]+>|\S+?)(?:\s+"[^"]*")?\)""")
private val tableBoldRegex = Regex("""(\*\*|__)(.+?)\1""")

@Composable
private fun ToolTraceEntry(toolTrace: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    val title = "MCP 工具调用 ${toolTrace.size} 次"

    InlineDetailEntry(
        label = title,
        expanded = expanded,
        onClick = { expanded = !expanded }
    )

    AnimatedVisibility(visible = expanded) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 6.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    toolTrace.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryTraceEntry(memoryTrace: List<AiMemoryTraceItem>) {
    var expanded by remember { mutableStateOf(false) }
    val latest = memoryTrace.lastOrNull() ?: return
    val running = memoryTrace.any { it.status == AiMemoryTraceItem.STATUS_RUNNING }
    val label = when {
        running -> "正在生成记忆"
        latest.status == AiMemoryTraceItem.STATUS_SAVED -> latest.detail?.let { "已保存记忆：$it" } ?: "已保存记忆"
        latest.status == AiMemoryTraceItem.STATUS_FAILED -> "记忆保存失败"
        else -> latest.title
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = when (latest.status) {
                AiMemoryTraceItem.STATUS_FAILED -> MaterialTheme.colorScheme.error.copy(alpha = 0.78f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (running) {
            ThinkingDots()
        } else {
            Icon(
                imageVector = if (expanded) {
                    Icons.Rounded.KeyboardArrowDown
                } else {
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                modifier = Modifier.size(18.dp)
            )
        }
    }

    AnimatedVisibility(visible = expanded) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 6.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                memoryTrace.forEach { item ->
                    val prefix = when (item.status) {
                        AiMemoryTraceItem.STATUS_RUNNING -> "生成中"
                        AiMemoryTraceItem.STATUS_SAVED -> "已保存"
                        AiMemoryTraceItem.STATUS_FAILED -> "失败"
                        else -> item.status
                    }
                    Text(
                        text = "• $prefix：${item.detail ?: item.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineDetailEntry(
    label: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (expanded) "⌃" else "›",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
        )
    }
}

@Composable
private fun MessageActions(
    message: ChatUiMessage,
    clipboard: ClipboardManager,
    showShareButton: Boolean,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    onFork: () -> Unit,
    onToggleFavorite: () -> Unit,
    onExport: () -> Unit
) {
    val context = LocalContext.current
    var showActionsMenu by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MessageActionButton(onClick = { clipboard.setText(AnnotatedString(message.content)) }) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy")
        }
        MessageActionButton(onClick = onRegenerate) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Regenerate")
        }
        if (showShareButton) {
            MessageActionButton(onClick = { shareChatMessage(context, message) }) {
                Icon(Icons.Rounded.Share, contentDescription = "Share")
            }
        }
        Box {
            MessageActionButton(onClick = { showActionsMenu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More")
            }
            if (showActionsMenu) {
                ChatMessageActionsMenu(
                    message = message,
                    onDismissRequest = { showActionsMenu = false },
                    onExport = {
                        showActionsMenu = false
                        onExport()
                    },
                    onFork = {
                        showActionsMenu = false
                        onFork()
                    },
                    onToggleFavorite = {
                        showActionsMenu = false
                        onToggleFavorite()
                    },
                    onDelete = {
                        showActionsMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun ChatMessageActionsMenu(
    message: ChatUiMessage,
    onDismissRequest: () -> Unit,
    onExport: () -> Unit,
    onFork: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    NgFunctionMenu(
        expanded = true,
        onDismissRequest = onDismissRequest,
        actions = listOf(
            NgFunctionMenuAction(
                icon = Icons.Rounded.Share,
                text = "导出",
                onClick = onExport
            ),
            NgFunctionMenuAction(
                icon = Icons.AutoMirrored.Rounded.CallSplit,
                text = "分支",
                onClick = onFork
            ),
            NgFunctionMenuAction(
                icon = if (message.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                text = if (message.favorite) "取消" else "收藏",
                onClick = onToggleFavorite
            ),
            NgFunctionMenuAction(
                icon = Icons.Rounded.Delete,
                text = "删除",
                danger = true,
                dividerBefore = true,
                onClick = onDelete
            )
        )
    )
}

@Composable
private fun MessageConfirmDialog(
    action: PendingMessageAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (action) {
                    PendingMessageAction.REGENERATE -> "重新生成"
                    PendingMessageAction.DELETE -> "删除"
                }
            )
        },
        text = {
            Text(
                text = when (action) {
                    PendingMessageAction.REGENERATE -> "确定要重新生成此消息吗？"
                    PendingMessageAction.DELETE -> "确定要删除这条消息吗？"
                }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认")
            }
        }
    )
}

@Composable
private fun ExportSelectionFloatingBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onInvert: () -> Unit,
    onConfirm: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
            tonalElevation = 4.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Rounded.Close, contentDescription = "取消")
                }
                IconButton(onClick = onInvert) {
                    Icon(Icons.Rounded.DoneAll, contentDescription = "全选/反选")
                }
                Surface(
                    onClick = onConfirm,
                    enabled = selectedCount > 0,
                    shape = CircleShape,
                    color = if (selectedCount > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "确认",
                            tint = if (selectedCount > 0) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatExportFormatDialog(
    onDismiss: () -> Unit,
    onExport: (ChatExportFormat) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出格式") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExportFormatOption(
                    icon = Icons.Rounded.Description,
                    title = "Markdown",
                    description = "将选中消息导出为 Markdown 文件",
                    onClick = {
                        onDismiss()
                        onExport(ChatExportFormat.MARKDOWN)
                    }
                )
                ExportFormatOption(
                    icon = Icons.Rounded.ImageIcon,
                    title = "导出为图片",
                    description = "将选中消息导出为 PNG 图片",
                    onClick = {
                        onDismiss()
                        onExport(ChatExportFormat.IMAGE)
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ExportFormatOption(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MessageActionButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(8.dp)
            .size(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun ContextUsageIndicator(
    usage: AiChatContextUsage,
    onClick: () -> Unit
) {
    val actualTokens = usage.calibration?.contextTokens ?: 0
    val actualPercent = if (usage.contextWindowTokens > 0) {
        actualTokens.toFloat() / usage.contextWindowTokens
    } else {
        0f
    }
    val percent = (actualPercent * 100).toInt().coerceAtLeast(0)
    Surface(
        onClick = onClick,
        modifier = Modifier.size(38.dp),
        shape = CircleShape,
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { actualPercent.coerceIn(0f, 1f) },
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.dp,
                color = if (actualTokens >= usage.thresholdTokens) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                text = if (percent > 99) "99+" else "$percent",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ContextUsageDialog(
    usage: AiChatContextUsage,
    compactionCount: Int,
    sessionUsage: AiChatContextSessionUsage,
    manualCompressionAvailable: Boolean,
    manualCompacting: Boolean,
    onManualCompress: () -> Unit,
    onDismiss: () -> Unit
) {
    val breakdown = usage.breakdown
    val localCategories = listOf(
        ContextUsageCategory("系统提示词", breakdown.systemPromptTokens),
        ContextUsageCategory("MCP / tools", breakdown.toolTokens),
        ContextUsageCategory("Skill", breakdown.skillTokens),
        ContextUsageCategory("App 上下文", breakdown.appContextTokens),
        ContextUsageCategory("聊天内容", breakdown.conversationTokens),
        ContextUsageCategory("协议开销", breakdown.protocolTokens)
    )
    val categories = localCategories.filter { it.tokens > 0 }
    val categoryTotal = categories.sumOf { it.tokens }
    val actualTokens = usage.calibration?.contextTokens ?: 0
    val actualPercent = if (usage.contextWindowTokens > 0) {
        actualTokens.toFloat() / usage.contextWindowTokens
    } else {
        0f
    }
    val actualPercentValue = (actualPercent * 100).toInt().coerceAtLeast(0)
    val localPercent = if (usage.contextWindowTokens > 0) {
        usage.localEstimatedTokens.toFloat() / usage.contextWindowTokens
    } else {
        0f
    }
    val localPercentValue = (localPercent * 100).toInt().coerceAtLeast(0)
    val limitTokens = if (usage.compactionThresholdPercent == 0) {
        usage.contextWindowTokens
    } else {
        usage.thresholdTokens
    }
    val remainingTokens = (limitTokens - actualTokens).coerceAtLeast(0)
    val overallColor = when {
        actualTokens >= limitTokens -> MaterialTheme.colorScheme.error
        actualPercent >= 0.75f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val dominantTokens = categories.maxOfOrNull { it.tokens } ?: 0
    AlertDialog(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .widthIn(max = 360.dp),
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "上下文用量",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "已压缩 $compactionCount 次",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "实时用量",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatContextTokenValue(actualTokens),
                        color = overallColor,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = " / ${formatContextTokenValue(usage.contextWindowTokens)} tokens",
                        modifier = Modifier.padding(bottom = 3.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                LinearProgressIndicator(
                    progress = { actualPercent.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = overallColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    drawStopIndicator = {}
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "已用 $actualPercentValue%",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = if (usage.compactionThresholdPercent == 0) {
                            "距窗口 ${formatContextTokenValue(remainingTokens)}"
                        } else {
                            "距自动压缩 ${formatContextTokenValue(remainingTokens)}"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "本地估算",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = formatContextTokenValue(usage.localEstimatedTokens),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                LinearProgressIndicator(
                    progress = { localPercent.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    drawStopIndicator = {}
                )
                Text(
                    text = "占窗口 $localPercentValue%",
                    modifier = Modifier.align(Alignment.End),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
                categories.forEach { category ->
                    ContextBreakdownRow(
                        label = category.label,
                        tokens = category.tokens,
                        totalTokens = categoryTotal,
                        highlight = category.tokens == dominantTokens && category.tokens > 0
                    )
                }
                if (sessionUsage.hasData) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "会话累计",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "↑${formatContextTokenValue(sessionUsage.promptTokens)}  " +
                                "↓${formatContextTokenValue(sessionUsage.completionTokens)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    sessionUsage.latestCompaction?.let { record ->
                        Text(
                            text = "最近压缩 ${formatContextTokenValue(record.beforeTokens)} → " +
                                formatContextTokenValue(record.afterTokens),
                            modifier = Modifier.align(Alignment.End),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onManualCompress,
                enabled = manualCompressionAvailable && !manualCompacting
            ) {
                Text(if (manualCompacting) "正在压缩" else "手动压缩")
            }
        }
    )
}

@Composable
private fun ContextBreakdownRow(
    label: String,
    tokens: Int,
    totalTokens: Int,
    highlight: Boolean
) {
    val percent = if (totalTokens > 0) tokens * 100f / totalTokens else 0f
    val color = if (highlight) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = if (highlight) color else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                text = formatContextTokenValue(tokens),
                modifier = Modifier.width(64.dp),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "${percent.toInt()}%",
                modifier = Modifier.width(40.dp),
                color = if (highlight) color else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        LinearProgressIndicator(
            progress = { (percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {}
        )
    }
}

private data class ContextUsageCategory(
    val label: String,
    val tokens: Int
)

@Composable
private fun RikkaChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    sending: Boolean,
    @DrawableRes modelIconRes: Int,
    reasoningEnabled: Boolean,
    mcpEnabled: Boolean,
    skillEnabled: Boolean,
    contextAvailable: Boolean,
    suggestionAvailable: Boolean,
    suggestionExpanded: Boolean,
    contextUsage: AiChatContextUsage,
    attachments: List<AiChatInputAttachment>,
    skills: List<AiChatInputAttachment>,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onModelClick: () -> Unit,
    onReasoningClick: () -> Unit,
    onMcpClick: () -> Unit,
    onSuggestionClick: () -> Unit,
    onContextUsageClick: () -> Unit,
    onSkillClick: () -> Unit,
    onContextClick: () -> Unit,
    onContextPreview: (AiChatInputAttachment) -> Unit,
    onRemoveAttachment: (AiChatInputAttachment) -> Unit,
    onRemoveSkill: (AiChatInputAttachment) -> Unit
) {
    Surface(color = Color.Transparent) {
        Column(
            modifier = Modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    CompactSkillStatusRow(
                        skills = skills,
                        onRemove = onRemoveSkill
                    )
                    val contextAttachments = attachments.filter {
                        it.type == AiChatInputAttachmentType.CONTEXT
                    }
                    if (contextAttachments.isNotEmpty()) {
                        AiChatInputAttachmentRow(
                            attachments = contextAttachments,
                            onPreview = onContextPreview,
                            onRemove = onRemoveAttachment
                        )
                    }
                    TextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("输入消息与 AI 聊天") },
                        maxLines = 5,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            InputModelIcon(modelIconRes, onModelClick)
                            InputDrawableIcon(
                                iconRes = R.drawable.ic_ai_capability_reasoning,
                                active = reasoningEnabled,
                                onClick = onReasoningClick
                            )
                            InputDrawableIcon(
                                iconRes = R.drawable.ic_ai_capability_tool,
                                active = mcpEnabled,
                                onClick = onMcpClick
                            )
                            InputDrawableIcon(
                                iconRes = R.drawable.ic_ai_skill_puzzle,
                                active = skillEnabled,
                                onClick = onSkillClick
                            )
                        }
                        ContextUsageIndicator(
                            usage = contextUsage,
                            onClick = onContextUsageClick
                        )
                        if (contextAvailable) {
                            InputDrawableIcon(
                                iconRes = R.drawable.ic_ai_context_menu,
                                active = attachments.any {
                                    it.type == AiChatInputAttachmentType.CONTEXT
                                },
                                onClick = onContextClick
                            )
                        }
                        if (suggestionAvailable) {
                            InputDrawableIcon(
                                iconRes = R.drawable.ic_ai_chat_suggestion,
                                active = suggestionExpanded,
                                onClick = onSuggestionClick
                            )
                        }
                        if (sending && value.isNotBlank()) {
                            ChatInputActionButton(
                                icon = Icons.Rounded.ArrowUpward,
                                contentDescription = "Queue message",
                                enabled = true,
                                color = MaterialTheme.colorScheme.primary,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                onClick = onSend
                            )
                        }
                        ChatInputActionButton(
                            icon = if (sending) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                            contentDescription = if (sending) "Stop" else "Send",
                            enabled = sending || value.isNotBlank(),
                            color = when {
                                sending -> MaterialTheme.colorScheme.errorContainer
                                value.isBlank() -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.primary
                            },
                            tint = when {
                                sending -> MaterialTheme.colorScheme.onErrorContainer
                                value.isBlank() ->
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.onPrimary
                            },
                            onClick = if (sending) onStop else onSend
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatInputActionButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    color: Color,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(38.dp),
        shape = CircleShape,
        color = color
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CompactSkillStatusRow(
    skills: List<AiChatInputAttachment>,
    onRemove: (AiChatInputAttachment) -> Unit
) {
    AnimatedVisibility(visible = skills.isNotEmpty()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val skill = skills.lastOrNull() ?: return@AnimatedVisibility
                Icon(
                    painter = painterResource(R.drawable.ic_ai_skill_puzzle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Skill：${skill.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { onRemove(skill) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "移除 ${skill.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        }
    }
}

@Composable
private fun AiChatInputAttachmentRow(
    attachments: List<AiChatInputAttachment>,
    onPreview: (AiChatInputAttachment) -> Unit,
    onRemove: (AiChatInputAttachment) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        attachments.forEach { attachment ->
            AiChatInputAttachmentChip(
                attachment = attachment,
                onPreview = { onPreview(attachment) },
                onRemove = { onRemove(attachment) }
            )
        }
    }
}

@Composable
private fun AiChatInputAttachmentChip(
    attachment: AiChatInputAttachment,
    onPreview: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        onClick = onPreview,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .height(44.dp)
                .padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (attachment.type) {
                            AiChatInputAttachmentType.CONTEXT -> Icons.Rounded.Description
                            AiChatInputAttachmentType.SKILL -> Icons.Rounded.Psychology
                        },
                        contentDescription = null,
                        tint = if (attachment.type == AiChatInputAttachmentType.SKILL) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = attachment.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.widthIn(min = 40.dp, max = 180.dp)
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .size(26.dp)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiChatContextPreviewSheet(
    attachments: List<AiChatInputAttachment>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "上下文内容",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "这些内容会随下一条消息一次性发送给 AI，可在输入框中移除。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                attachments.forEach { attachment ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = attachment.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = attachment.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Text(
                                text = attachment.prompt,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiChatMcpCapabilitySheet(
    modules: List<McpInternalToolModule>,
    selectedCapabilityIds: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val expandedModuleIds = remember(modules) {
        mutableStateListOf<String>()
    }
    val allCapabilityIds = remember(modules) {
        modules.flatMap { module ->
            module.capabilities.map { capability -> capability.id }
        }.toSet()
    }
    val sheetBackgroundDrawable = remember(context) {
        runCatching {
            ThemeConfig.getBgImage(context, context.resources.displayMetrics)
        }.getOrNull()
    }
    val selectedToolCount = McpInternalToolCatalog
        .resolveToolNames(selectedCapabilityIds)
        .size
    val selectionActionText = if (selectedCapabilityIds.isEmpty()) {
        "全选"
    } else {
        "反选"
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Transparent,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.68f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
        ) {
            if (sheetBackgroundDrawable != null) {
                ChatBackgroundImage(
                    drawableProvider = { sheetBackgroundDrawable },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66FFFFF9))
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(top = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 8.dp, end = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI 功能",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "已选 ${selectedCapabilityIds.size} 项 · $selectedToolCount 个接口",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        enabled = allCapabilityIds.isNotEmpty(),
                        onClick = {
                            if (selectedCapabilityIds.isEmpty()) {
                                onSelectionChange(allCapabilityIds)
                            } else {
                                onSelectionChange(
                                    allCapabilityIds
                                        .filterNot { it in selectedCapabilityIds }
                                        .toSet()
                                )
                            }
                        }
                    ) {
                        Text(selectionActionText)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        top = 8.dp,
                        end = 12.dp,
                        bottom = 20.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    modules.forEach { module ->
                        item(key = "mcp-module-${module.id}") {
                            val expanded = module.id in expandedModuleIds
                            Column {
                                AiChatMcpModuleHeader(
                                    module = module,
                                    selectedCapabilityIds = selectedCapabilityIds,
                                    expanded = expanded,
                                    onToggleExpanded = {
                                        expandedModuleIds.toggleNgExpandedKey(module.id)
                                    },
                                    onSelectionChange = onSelectionChange
                                )
                                AnimatedVisibility(visible = expanded) {
                                    Column(modifier = Modifier.padding(top = 4.dp)) {
                                        module.capabilities.forEach { capability ->
                                            AiChatMcpCapabilityRow(
                                                capability = capability,
                                                selected = capability.id in selectedCapabilityIds,
                                                onToggle = {
                                                    val next = selectedCapabilityIds.toMutableSet()
                                                    if (!next.add(capability.id)) {
                                                        next.remove(capability.id)
                                                    }
                                                    onSelectionChange(next)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiChatMcpModuleHeader(
    module: McpInternalToolModule,
    selectedCapabilityIds: Set<String>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSelectionChange: (Set<String>) -> Unit
) {
    val moduleIds = module.capabilities.mapTo(linkedSetOf()) { it.id }
    val selectedCount = moduleIds.count { it in selectedCapabilityIds }
    val selectionState = when {
        selectedCount == 0 -> ToggleableState.Off
        selectedCount == moduleIds.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val toggleSelection = {
        val next = selectedCapabilityIds.toMutableSet()
        if (selectionState == ToggleableState.On) {
            next.removeAll(moduleIds)
        } else {
            next.addAll(moduleIds)
        }
        onSelectionChange(next)
    }
    NgExpandableSectionHeader(
        title = module.title,
        selectionState = selectionState,
        selectedText = "$selectedCount/${moduleIds.size}",
        expanded = expanded,
        onToggleExpanded = onToggleExpanded,
        onToggleSelection = toggleSelection,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AiChatMcpCapabilityRow(
    capability: McpInternalToolCapability,
    selected: Boolean,
    onToggle: () -> Unit
) {
    NgExpandableChildRow(
        title = capability.title,
        summary = capability.description,
        selected = selected,
        badges = buildList {
            add(NgListBadge(capability.toolNames.size.toString()))
            if (capability.containsWriteTools) {
                add(NgListBadge("写", NgListBadgeTone.Error))
            }
        },
        onToggle = onToggle,
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiChatInputAttachmentSheet(
    title: String,
    description: String,
    emptyText: String,
    availableAttachments: List<AiChatInputAttachment>,
    loadedAttachmentIds: Set<String>,
    onAdd: (AiChatInputAttachment) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AiChatAttachmentSheetSection(
                emptyText = emptyText,
                attachments = availableAttachments,
                loadedAttachmentIds = loadedAttachmentIds,
                onAdd = onAdd
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AiChatAttachmentSheetSection(
    emptyText: String,
    attachments: List<AiChatInputAttachment>,
    loadedAttachmentIds: Set<String>,
    onAdd: (AiChatInputAttachment) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (attachments.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        } else {
            attachments.forEach { attachment ->
                val loaded = attachment.id in loadedAttachmentIds
                Surface(
                    onClick = { if (!loaded) onAdd(attachment) },
                    enabled = !loaded,
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = when (attachment.type) {
                                AiChatInputAttachmentType.CONTEXT -> Icons.Rounded.Description
                                AiChatInputAttachmentType.SKILL -> Icons.Rounded.Psychology
                            },
                            contentDescription = null,
                            tint = if (attachment.type == AiChatInputAttachmentType.SKILL) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = attachment.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = attachment.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = if (loaded) "已添加" else "添加",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (loaded) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingSkillSuggestionPanel(
    suggestions: List<String>,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    AnimatedVisibility(
        visible = expanded && suggestions.isNotEmpty(),
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(140)) +
                scaleIn(
                    animationSpec = tween(180),
                    transformOrigin = TransformOrigin(1f, 1f),
                    initialScale = 0.92f
                ) +
                expandVertically(expandFrom = Alignment.Bottom),
        exit = fadeOut(animationSpec = tween(120)) +
                scaleOut(
                    animationSpec = tween(140),
                    transformOrigin = TransformOrigin(1f, 1f),
                    targetScale = 0.92f
                ) +
                shrinkVertically(shrinkTowards = Alignment.Bottom)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            horizontalAlignment = Alignment.End
        ) {
            suggestions.forEach { suggestion ->
                Surface(
                    onClick = { onSelect(suggestion) },
                    shape = RoundedCornerShape(7.dp),
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
                    shadowElevation = 2.dp
                ) {
                    Text(
                        text = suggestion,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun InputModelIcon(
    @DrawableRes iconRes: Int,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(34.dp),
        shape = CircleShape,
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun InputDrawableIcon(
    @DrawableRes iconRes: Int,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(34.dp),
        shape = CircleShape,
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (active) MaterialTheme.colorScheme.primary else Color.Unspecified
            )
        }
    }
}

@Composable
private fun RikkaAssistantAvatar(
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun AiChatTurnResult.toUiMessage(elapsedMs: Long): ChatUiMessage {
    val meta = buildList {
        usage?.promptTokens?.let { add("↑ ${formatNumber(it)} tokens") }
        usage?.completionTokens?.let { add("↓ ${formatNumber(it)} tokens") }
        usage?.completionTokens?.takeIf { elapsedMs > 0 }?.let {
            val tokensPerSecond = it * 1000.0 / elapsedMs
            add("⚡ ${"%.1f".format(tokensPerSecond)} tok/s")
        }
        add("◷ ${formatElapsedCompact(elapsedMs)}")
        warnings.filter { it.isNotBlank() }.forEach { add(it) }
    }.joinToString(" · ")
    return ChatUiMessage(
        role = ChatRole.ASSISTANT,
        content = content.ifBlank { reasoning?.takeIf { it.isNotBlank() } ?: "模型未返回正文" },
        meta = meta,
        reasoning = reasoning,
        toolTrace = toolTrace,
        memoryTrace = memoryTrace,
        elapsedMs = elapsedMs,
        promptTokens = usage?.promptTokens,
        localPromptTokens = null,
        contextAnchorTokens = contextCalibration?.contextTokens,
        localContextAnchorTokens = contextCalibration?.localContextTokens,
        usageProviderId = contextCalibration?.providerId,
        usageModelId = contextCalibration?.modelId
    )
}

private fun formatElapsedSeconds(elapsedMs: Long): String {
    val seconds = elapsedMs / 1000.0
    return if (seconds < 10) {
        "%.1f 秒".format(seconds)
    } else {
        "${seconds.toInt()} 秒"
    }
}

private fun formatElapsedCompact(elapsedMs: Long): String {
    return "%.1fs".format(elapsedMs / 1000.0)
}

private fun nextStreamingText(current: String, target: String): String {
    if (target.isBlank() || current == target) {
        return current
    }
    if (!target.startsWith(current)) {
        return target
    }
    val remaining = target.length - current.length
    val step = when {
        remaining > 120 -> 16
        remaining > 60 -> 10
        remaining > 24 -> 6
        remaining > 8 -> 3
        else -> 1
    }
    return target.take(current.length + minOf(step, remaining))
}

private fun formatNumber(value: Int): String {
    return "%,d".format(value)
}

private fun currentAssistantModelLabel(): String {
    return AiAssistantConfigUi.selectedModelLabel()
}

private fun currentAssistantModelIconRes(): Int {
    return AiAssistantConfigUi.selectedModelIconRes()
}

private fun shareChatMessage(context: Context, message: ChatUiMessage) {
    val text = message.content.ifBlank { message.reasoning.orEmpty() }.trim()
    if (text.isBlank()) {
        Toast.makeText(context, "没有可分享的内容", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    runCatching {
        context.startActivity(Intent.createChooser(intent, "分享消息"))
    }.onFailure {
        Toast.makeText(context, "未找到可用的分享应用", Toast.LENGTH_SHORT).show()
    }
}

private fun exportChatMessagesAsMarkdown(
    context: Context,
    title: String,
    messages: List<ChatUiMessage>
): File {
    val file = createChatExportFile(context, "md")
    file.writeText(buildChatExportMarkdown(title, messages), Charsets.UTF_8)
    return file
}

private fun exportChatMessagesAsImage(
    context: Context,
    title: String,
    messages: List<ChatUiMessage>
): File {
    val file = createChatExportFile(context, "png")
    val text = buildChatExportPlainText(title, messages)
    val bitmap = renderChatExportBitmap(text)
    FileOutputStream(file).use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    }
    bitmap.recycle()
    return file
}

private fun shareChatExportFile(context: Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileProvider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND)
        .setType(mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching {
        context.startActivity(Intent.createChooser(intent, "导出消息"))
    }.onFailure {
        Toast.makeText(context, "未找到可用的分享应用", Toast.LENGTH_SHORT).show()
    }
}

private fun createChatExportFile(context: Context, extension: String): File {
    val dir = File(context.cacheDir, "ai-chat-export").also { it.mkdirs() }
    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
    return File(dir, "chat-export-$timestamp.$extension").also {
        if (it.exists()) {
            it.delete()
        }
        it.createNewFile()
    }
}

private fun buildChatExportMarkdown(title: String, messages: List<ChatUiMessage>): String {
    return buildString {
        append("# ").append(title).append("\n\n")
        append("*导出时间：")
            .append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            .append("*\n\n")
        messages.forEach { message ->
            append("## ")
                .append(if (message.role == ChatRole.USER) "用户" else currentAssistantModelLabel())
                .append("\n\n")
            if (!message.reasoning.isNullOrBlank()) {
                append("> 已思考")
                message.elapsedMs?.let { append(" ").append(formatElapsedSeconds(it)) }
                append("\n>\n")
                message.reasoning.lines().forEach { line ->
                    append("> ").append(line).append('\n')
                }
                append('\n')
            }
            append(message.content.ifBlank { "（空消息）" }).append("\n\n")
            if (message.toolTrace.isNotEmpty()) {
                append("**MCP 工具调用 ").append(message.toolTrace.size).append(" 次**\n\n")
                message.toolTrace.forEach { trace ->
                    append("- ").append(trace.replace('\n', ' ')).append('\n')
                }
                append('\n')
            }
            append("---\n\n")
        }
    }
}

private fun buildChatExportPlainText(title: String, messages: List<ChatUiMessage>): String {
    return buildString {
        append(title).append("\n")
        append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        append("\n\n")
        messages.forEach { message ->
            append(if (message.role == ChatRole.USER) "用户" else currentAssistantModelLabel()).append("：\n")
            if (!message.reasoning.isNullOrBlank()) {
                append("已思考")
                message.elapsedMs?.let { append(" ").append(formatElapsedSeconds(it)) }
                append('\n')
            }
            append(message.content.ifBlank { "（空消息）" }).append("\n\n")
        }
    }
}

@Suppress("DEPRECATION")
private fun renderChatExportBitmap(text: String): Bitmap {
    val width = 1080
    val padding = 56
    val textWidth = width - padding * 2
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(32, 30, 36)
        textSize = 34f
    }
    val layout = StaticLayout(
        text,
        paint,
        textWidth,
        Layout.Alignment.ALIGN_NORMAL,
        1.25f,
        0f,
        true
    )
    val height = (layout.height + padding * 2).coerceAtMost(16000)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.rgb(254, 248, 255))
    canvas.save()
    canvas.translate(padding.toFloat(), padding.toFloat())
    layout.draw(canvas)
    canvas.restore()
    return bitmap
}

private fun ChatUiMessage.sheetInfoText(): String {
    return when (role) {
        ChatRole.USER -> "用户消息"
        ChatRole.ASSISTANT -> currentAssistantModelLabel()
    }
}

private fun <T> MutableList<T>.replaceWith(newItems: List<T>) {
    clear()
    addAll(newItems)
}

private fun MutableList<AiChatSessionSnapshot>.sortChatSessions() {
    sortWith(
        compareByDescending<AiChatSessionSnapshot> { it.isPinned }
            .thenByDescending { it.updatedAt }
    )
}

private fun MutableList<JsonObject>.replaceUploadMessages(
    session: AiChatSessionSnapshot,
    chatClient: AiChatClient
) {
    clear()
    val savedMessages = session.uploadMessages
        .filter { it.has("role") }
        .map { it.deepCopy().asJsonObject }
    if (savedMessages.isNotEmpty()) {
        addAll(savedMessages)
        return
    }
    addAll(rebuildUploadMessages(chatClient, session.messages.map { it.toUiMessage() }))
}

private fun rebuildUploadMessages(
    chatClient: AiChatClient,
    messages: List<ChatUiMessage>
): List<JsonObject> {
    return buildList {
        add(chatClient.newSystemMessage())
        messages.filterNot { it.loading }.forEach { message ->
            when (message.role) {
                ChatRole.USER -> if (message.deliveryState != ChatDeliveryState.QUEUED) {
                    add(chatClient.newUserMessage(
                        AiChatContextManager.removeEmbeddedSkill(
                            message.uploadContent ?: message.content
                        )
                    ))
                }
                ChatRole.ASSISTANT -> if (message.meta != "error" && message.contextCompaction == null) {
                    add(chatClient.newAssistantMessage(message.content, message.reasoning))
                }
            }
        }
    }
}

private fun buildEntryInputAttachments(entrySource: String?): List<AiChatInputAttachment> {
    return when (entrySource) {
        AiChatActivity.ENTRY_BOOKSHELF -> buildList {
            AiSkillRegistry.get(AiSkillRegistry.SKILL_BOOKSHELF_MANAGEMENT)
                ?.let(::toSkillInputAttachment)
                ?.let(::add)
        }

        else -> emptyList()
    }
}

private fun defaultMcpCapabilityIds(entrySource: String?): List<String> {
    return when (entrySource) {
        AiChatActivity.ENTRY_BOOKSHELF -> McpInternalToolCatalog.allCapabilityIds
        AiChatActivity.ENTRY_BOOK_DETAIL -> listOf(
            "bookshelf.query",
            "bookshelf.read_content",
            "bookshelf.manage_cache",
            "bookshelf.manage_characters",
            "ai.memory"
        )
        AiChatActivity.ENTRY_BOOK_SCAN -> listOf(
            "bookshelf.query",
            "bookshelf.read_content",
            "bookshelf.cache_status",
            "ai.memory_read"
        )
        else -> emptyList()
    }
}

@Suppress("UNUSED_PARAMETER")
private fun buildContextInputAttachments(contextSources: List<String>): List<AiChatInputAttachment> {
    return emptyList()
}

private fun buildAgentSkillInputAttachments(): List<AiChatInputAttachment> {
    return AiSkillRegistry.agentSkills().map(::toSkillInputAttachment)
}

private fun buildSkillInputAttachments(skillIds: List<String>): List<AiChatInputAttachment> {
    return skillIds.asReversed().firstNotNullOfOrNull { skillId ->
        AiSkillRegistry.get(skillId)
            ?.takeIf { it.scope == AiSkillScope.AGENT }
            ?.let(::toSkillInputAttachment)
    }?.let(::listOf).orEmpty()
}

private fun buildIntentContextInputAttachments(rawAttachments: List<String>): List<AiChatInputAttachment> {
    return rawAttachments.mapNotNull { raw ->
        runCatching {
            val json = GSON.fromJson(raw, JsonObject::class.java)
            val id = json.get("id")?.asString?.trim().orEmpty()
            val title = json.get("title")?.asString?.trim().orEmpty()
            val prompt = json.get("prompt")?.asString?.trim().orEmpty()
            if (id.isBlank() || title.isBlank() || prompt.isBlank()) {
                return@runCatching null
            }
            AiChatInputAttachment(
                id = "context.$id",
                type = AiChatInputAttachmentType.CONTEXT,
                title = title,
                subtitle = json.get("subtitle")?.asString?.trim().orEmpty(),
                prompt = prompt
            )
        }.getOrNull()
    }
}

private fun toSkillInputAttachment(skill: io.legado.app.help.ai.AiSkillDefinition): AiChatInputAttachment {
    return AiChatInputAttachment(
        id = "skill.${skill.id}",
        type = AiChatInputAttachmentType.SKILL,
        title = skill.name,
        subtitle = skill.summary,
        prompt = skill.prompt,
        suggestions = skill.suggestions
    )
}

private fun List<AiChatInputAttachment>.loadedSkillIds(): List<String> {
    return filter { it.type == AiChatInputAttachmentType.SKILL }
        .map { it.id.removePrefix("skill.").trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

private fun buildUserUploadContent(
    userContent: String,
    attachments: List<AiChatInputAttachment>
): String {
    val contexts = attachments.filter { it.type == AiChatInputAttachmentType.CONTEXT }
    if (contexts.isEmpty()) {
        return userContent
    }
    return buildString {
        append(AiChatContextManager.ATTACHMENT_PREAMBLE)
        append(AiChatContextManager.APP_CONTEXT_HEADING)
        contexts.forEach { attachment ->
            append("### ").append(attachment.title).append('\n')
            append(attachment.prompt).append("\n\n")
        }
        append(AiChatContextManager.USER_MESSAGE_HEADING)
        append(userContent)
    }
}

private fun extractMatchingSnippet(
    text: String,
    query: String
): String {
    if (query.isBlank()) {
        return text
    }
    val matchIndex = text.indexOf(query, ignoreCase = true)
    if (matchIndex < 0) {
        return text
    }
    val start = (matchIndex - 12).coerceAtLeast(0)
    val end = (matchIndex + query.length + 72).coerceAtMost(text.length)
    return buildString {
        if (start > 0) append("...")
        append(text.substring(start, end).replace(Regex("\\s+"), " "))
        if (end < text.length) append("...")
    }
}

private fun buildGlobalMessageSearchResults(
    query: String,
    sessions: List<AiChatSessionSnapshot>,
    activeSessionId: String?,
    currentMessages: List<ChatUiMessage>
): List<GlobalMessageSearchResult> {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) {
        return emptyList()
    }
    val activeSession = sessions.firstOrNull { it.id == activeSessionId }
    val activeTitle = currentMessages.deriveChatTitle()
        ?: activeSession?.title
        ?: "当前聊天"
    val activeUpdatedAt = activeSession?.updatedAt ?: System.currentTimeMillis()
    val activeResults = currentMessages
        .mapIndexedNotNull { index, message ->
            if (message.loading) {
                return@mapIndexedNotNull null
            }
            val corpus = message.searchCorpus()
            if (!corpus.contains(cleanQuery, ignoreCase = true)) {
                return@mapIndexedNotNull null
            }
            GlobalMessageSearchResult(
                session = activeSession,
                sessionId = activeSessionId,
                sessionTitle = activeTitle,
                messageId = message.id,
                messageIndex = index,
                roleLabel = if (message.role == ChatRole.USER) "用户" else "AI",
                snippet = extractMatchingSnippet(corpus.trim().ifBlank { message.drawerPreview() }, cleanQuery),
                updatedAt = activeUpdatedAt
            )
        }
    val historyResults = sessions
        .filterNot { it.id == activeSessionId }
        .flatMap { session ->
            session.messages.mapIndexedNotNull { index, message ->
                val corpus = message.searchCorpus()
                if (!corpus.contains(cleanQuery, ignoreCase = true)) {
                    return@mapIndexedNotNull null
                }
                GlobalMessageSearchResult(
                    session = session,
                    sessionId = session.id,
                    sessionTitle = session.title,
                    messageId = message.id,
                    messageIndex = index,
                    roleLabel = if (message.role == AiChatMessageSnapshot.ROLE_USER) "用户" else "AI",
                    snippet = extractMatchingSnippet(corpus.trim().ifBlank { message.drawerPreview() }, cleanQuery),
                    updatedAt = session.updatedAt
                )
            }
        }
    return (activeResults + historyResults)
        .sortedByDescending { it.updatedAt }
        .take(100)
}

private fun ChatUiMessage.searchCorpus(): String {
    return listOf(
        content,
        reasoning.orEmpty(),
        meta.orEmpty(),
        toolTrace.joinToString("\n"),
        memoryTrace.joinToString("\n") { it.title + " " + it.detail.orEmpty() }
    ).joinToString("\n")
}

private fun AiChatMessageSnapshot.searchCorpus(): String {
    return listOf(
        content,
        reasoning.orEmpty(),
        meta.orEmpty(),
        toolTrace.joinToString("\n")
    ).joinToString("\n")
}

private fun formatChatSearchTime(timeMillis: Long): String {
    if (timeMillis <= 0L) {
        return "未知时间"
    }
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timeMillis))
}

private fun buildHighlightedText(
    text: String,
    query: String,
    highlightColor: Color
): AnnotatedString {
    if (query.isBlank()) {
        return AnnotatedString(text)
    }
    val builder = AnnotatedString.Builder()
    var cursor = 0
    while (cursor < text.length) {
        val matchIndex = text.indexOf(query, cursor, ignoreCase = true)
        if (matchIndex < 0) {
            builder.append(text.substring(cursor))
            break
        }
        if (matchIndex > cursor) {
            builder.append(text.substring(cursor, matchIndex))
        }
        val matchEnd = matchIndex + query.length
        builder.pushStyle(SpanStyle(background = highlightColor))
        builder.append(text.substring(matchIndex, matchEnd))
        builder.pop()
        cursor = matchEnd
    }
    return builder.toAnnotatedString()
}

private fun List<ChatUiMessage>.deriveChatTitle(): String? {
    val source = firstOrNull { it.role == ChatRole.USER }?.content
        ?: firstOrNull { it.content.isNotBlank() }?.content
        ?: return null
    return source
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.take(24)
        ?.takeIf { it.isNotBlank() }
}

private fun buildDrawerHistoryGroups(
    sessions: List<AiChatSessionSnapshot>
): List<DrawerHistoryGroup> {
    val groupOrder = listOf("置顶", "书籍相关", "书架管理", "普通聊天")
    val items = sessions.map { session ->
        val identity = session.drawerIdentity()
        DrawerHistoryItem(
            session = session,
            groupTitle = if (session.isPinned) "置顶" else identity.groupTitle,
            title = identity.title,
            subtitle = identity.subtitle
        )
    }
    val grouped = items.groupBy { item ->
        item.groupTitle
    }
    val orderedGroups = groupOrder.mapNotNull { title ->
        grouped[title]
            ?.takeIf { it.isNotEmpty() }
            ?.let { DrawerHistoryGroup(title, it) }
    }
    val orderedTitles = groupOrder.toSet()
    val extraGroups = grouped
        .filterKeys { it !in orderedTitles }
        .map { (title, items) -> DrawerHistoryGroup(title, items) }
    return orderedGroups + extraGroups
}

private fun AiChatSessionSnapshot.drawerIdentity(): DrawerConversationIdentity {
    val skillText = loadedSkillIds.joinToString("|").lowercase(Locale.ROOT)
    val isCharacterCardEntry = skillText.hasDedicatedSkill("character_card_generate")
    val isBookshelfManagementEntry = skillText.hasDedicatedSkill("bookshelf_management")
    val subject = if (isCharacterCardEntry) {
        extractChatSubject(drawerCorpus())
    } else {
        null
    }
    val compactTitle = title.toCompactDrawerText()
    val scene = when {
        isCharacterCardEntry -> DrawerScene(
            groupTitle = "书籍相关",
            title = "角色卡"
        )

        isBookshelfManagementEntry -> DrawerScene(
            groupTitle = "书架管理",
            title = "书架管理"
        )

        else -> DrawerScene(
            groupTitle = "普通聊天",
            title = compactTitle.ifBlank { "普通聊天" }
        )
    }
    val titleText = if (subject != null && scene.title !in subject) {
        "${scene.title} · $subject"
    } else {
        scene.title
    }.toCompactDrawerText(maxLength = 32)
    val preview = messages
        .asReversed()
        .firstOrNull { it.content.isNotBlank() }
        ?.drawerPreview()
        .orEmpty()
        .takeUnless { it == title || it == titleText }
    val subtitle = listOfNotNull(
        formatDrawerHistoryTime(updatedAt),
        preview
    ).joinToString(" · ")
    return DrawerConversationIdentity(
        groupTitle = scene.groupTitle,
        title = titleText.ifBlank { "普通聊天" },
        subtitle = subtitle
    )
}

private fun AiChatSessionSnapshot.drawerCorpus(): String {
    val firstUser = messages.firstOrNull { it.role == AiChatMessageSnapshot.ROLE_USER }?.content.orEmpty()
    val lastText = messages.asReversed().firstOrNull { it.content.isNotBlank() }?.content.orEmpty()
    val uploadText = uploadMessages
        .asSequence()
        .mapNotNull { message ->
            runCatching {
                message.get("content")?.toString().orEmpty()
            }.getOrNull()
        }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
    return listOf(title, firstUser, lastText, uploadText, loadedSkillIds.joinToString("|"))
        .joinToString("\n")
        .replace("\\n", "\n")
        .take(4000)
}

private fun String.hasDedicatedSkill(skillId: String): Boolean {
    return split('|').any { loadedId ->
        loadedId.trim().replace('-', '_') == skillId
    }
}

private fun extractChatSubject(text: String): String? {
    val patterns = listOf(
        Regex("《([^》]{1,40})》"),
        Regex("(?:书名|作品|当前书)[:：]\\s*([^\\n，。；|]{1,40})"),
        Regex("(?:name|book_name|bookName)[:=]\\s*([^\\n，。；|]{1,40})", RegexOption.IGNORE_CASE)
    )
    return patterns
        .asSequence()
        .mapNotNull { pattern -> pattern.find(text)?.groupValues?.getOrNull(1) }
        .map { it.cleanDrawerSubject() }
        .firstOrNull { it.isNotBlank() }
}

private fun String.cleanDrawerSubject(): String {
    return replace(Regex("[\"'`{}\\[\\]]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim('：', ':', '-', '，', ',', '。')
        .take(24)
}

private fun String.toCompactDrawerText(maxLength: Int = 24): String {
    return lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.take(maxLength)
        .orEmpty()
}

private fun formatDrawerHistoryTime(timeMillis: Long): String {
    if (timeMillis <= 0L) {
        return "未知时间"
    }
    val now = System.currentTimeMillis()
    val diff = (now - timeMillis).coerceAtLeast(0L)
    return when {
        diff < 24L * 60L * 60L * 1000L -> {
            "今天 " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMillis))
        }

        diff < 7L * 24L * 60L * 60L * 1000L -> "${diff / (24L * 60L * 60L * 1000L)}天前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timeMillis))
    }
}

private fun buildDrawerFavoriteItems(
    sessions: List<AiChatSessionSnapshot>,
    activeSessionId: String?,
    messages: List<ChatUiMessage>
): List<DrawerFavoriteItem> {
    val activeSession = sessions.firstOrNull { it.id == activeSessionId }
    val titleCache = sessions.associate { session ->
        session.id to session.drawerIdentity().title
    }
    val activeTitle = activeSession?.let { titleCache[it.id] } ?: messages.deriveChatTitle() ?: "当前聊天"
    val activeItems = messages
        .filter { it.favorite && !it.loading }
        .map { message ->
            DrawerFavoriteItem(
                messageId = message.id,
                title = activeTitle,
                preview = message.drawerPreview(),
                session = activeSession
            )
        }
    val historyItems = sessions
        .filterNot { it.id == activeSessionId }
        .flatMap { session ->
            session.messages
                .filter { it.favorite }
                .map { message ->
                    DrawerFavoriteItem(
                        messageId = message.id,
                        title = titleCache[session.id] ?: session.title,
                        preview = message.drawerPreview(),
                        session = session
                    )
                }
        }
    return (activeItems + historyItems)
        .distinctBy { "${it.session?.id.orEmpty()}:${it.messageId}" }
        .take(50)
}

private fun buildTokenActivityBars(
    tokenActivityPerDay: Map<LocalDate, Long>,
    mode: TokenActivityMode
): List<TokenActivityBar> {
    val today = LocalDate.now()
    return when (mode) {
        TokenActivityMode.DAILY -> {
            val firstDay = today.withDayOfMonth(1)
            val daysInMonth = today.lengthOfMonth()
            (1..daysInMonth).map { day ->
                val date = firstDay.withDayOfMonth(day)
                TokenActivityBar(
                    axisLabel = day.toString(),
                    rangeLabel = "${date.monthValue}月${date.dayOfMonth}日",
                    value = tokenActivityPerDay[date] ?: 0L,
                    defaultSelected = date == today
                )
            }
        }

        TokenActivityMode.MONTHLY -> {
            val year = today.year
            (1..12).map { month ->
                val value = tokenActivityPerDay
                    .filterKeys { date -> date.year == year && date.monthValue == month }
                    .values
                    .sum()
                TokenActivityBar(
                    axisLabel = "${month}月",
                    rangeLabel = "${year}年${month}月",
                    value = value,
                    defaultSelected = month == today.monthValue
                )
            }
        }
    }
}

private fun buildAiChatStats(
    sessions: List<AiChatSessionSnapshot>,
    activeSessionId: String?,
    currentMessages: List<ChatUiMessage>
): AiChatStats {
    val activeSnapshot = activeSessionId?.takeIf { currentMessages.isNotEmpty() }?.let { sessionId ->
        AiChatSessionSnapshot(
            id = sessionId,
            title = currentMessages.deriveChatTitle() ?: "当前聊天",
            createdAt = sessions.firstOrNull { it.id == sessionId }?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            messages = currentMessages.filterNot { it.loading }.map { it.toSnapshot() }
        )
    }
    val statSessions = buildList {
        addAll(sessions.filterNot { it.id == activeSessionId })
        if (activeSnapshot != null) {
            add(activeSnapshot)
        } else {
            addAll(sessions.filter { it.id == activeSessionId })
        }
    }
    val zoneId = ZoneId.systemDefault()
    val conversationsPerDay = statSessions
        .filter { it.messages.isNotEmpty() }
        .groupingBy { session ->
            Instant.ofEpochMilli(session.updatedAt.takeIf { it > 0L } ?: session.createdAt)
                .atZone(zoneId)
                .toLocalDate()
        }
        .eachCount()
    val tokenStats = statSessions
        .asSequence()
        .flatMap { it.messages.asSequence() }
        .fold(AiTokenStats()) { acc, message ->
            val tokens = message.meta.parseAiTokenStats()
            acc.copy(
                promptTokens = acc.promptTokens + tokens.promptTokens,
                completionTokens = acc.completionTokens + tokens.completionTokens,
                elapsedMs = acc.elapsedMs + (message.elapsedMs ?: 0L)
            )
        }
    val tokenActivityPerDay = statSessions
        .filter { it.messages.isNotEmpty() }
        .groupBy { session ->
            Instant.ofEpochMilli(session.updatedAt.takeIf { it > 0L } ?: session.createdAt)
                .atZone(zoneId)
                .toLocalDate()
        }
        .mapValues { (_, daySessions) ->
            daySessions.sumOf { session ->
                session.messages.sumOf { message ->
                    val tokens = message.meta.parseAiTokenStats()
                    tokens.promptTokens + tokens.completionTokens
                }
            }
        }
    return AiChatStats(
        totalConversations = statSessions.count { it.messages.isNotEmpty() },
        totalMessages = statSessions.sumOf { it.messages.size },
        totalPromptTokens = tokenStats.promptTokens,
        totalCompletionTokens = tokenStats.completionTokens,
        totalElapsedMs = tokenStats.elapsedMs,
        conversationsPerDay = conversationsPerDay,
        tokenActivityPerDay = tokenActivityPerDay
    )
}

private fun String?.parseAiTokenStats(): AiTokenStats {
    if (isNullOrBlank()) {
        return AiTokenStats()
    }
    val promptTokens = Regex("↑\\s*([\\d,]+)\\s*tokens")
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.replace(",", "")
        ?.toLongOrNull()
        ?: 0L
    val completionTokens = Regex("↓\\s*([\\d,]+)\\s*tokens")
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.replace(",", "")
        ?.toLongOrNull()
        ?: 0L
    return AiTokenStats(
        promptTokens = promptTokens,
        completionTokens = completionTokens
    )
}

private fun List<ChatUiMessage>.latestPromptUsageAnchor(): AiChatPromptUsageAnchor? {
    val latestCompactionIndex = indexOfLast { it.contextCompaction != null }
    val anchorIndex = indices.lastOrNull { index ->
        val message = get(index)
        index > latestCompactionIndex &&
            message.role == ChatRole.ASSISTANT && message.meta != "error" &&
            (message.contextAnchorTokens != null || message.promptTokens != null ||
                message.meta.parseAiTokenStats().promptTokens in 1..Int.MAX_VALUE.toLong())
    } ?: -1
    if (anchorIndex < 0) return null
    return get(anchorIndex).let { message ->
        val promptTokens = message.promptTokens
            ?: message.meta.parseAiTokenStats().promptTokens
                .takeIf { it in 1..Int.MAX_VALUE.toLong() }
                ?.toInt()
            ?: message.contextAnchorTokens
            ?: return@let null
        AiChatPromptUsageAnchor(
            promptTokens = promptTokens,
            localPromptTokens = message.localPromptTokens,
            contextTokens = message.contextAnchorTokens,
            localContextTokens = message.localContextAnchorTokens,
            providerId = message.usageProviderId,
            modelId = message.usageModelId,
            expectedToolCallCount = if (
                message.localContextAnchorTokens == null && message.localPromptTokens == null
            ) {
                subList(latestCompactionIndex + 1, anchorIndex + 1).sumOf { it.toolTrace.size }
            } else {
                0
            }
        )
    }
}

private fun formatAiStatCount(count: Long): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}

private fun formatAiStatTokens(count: Long): String = when {
    count >= 1_000_000_000 -> "%.2fB".format(count / 1_000_000_000.0)
    count >= 1_000_000 -> "%.2fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}

private fun formatAiStatDuration(elapsedMs: Long): String {
    val seconds = elapsedMs / 1000
    return when {
        seconds >= 3600 -> "${seconds / 3600}小时${seconds % 3600 / 60}分"
        seconds >= 60 -> "${seconds / 60}分${seconds % 60}秒"
        else -> "${seconds}秒"
    }
}

private fun ChatUiMessage.drawerPreview(): String {
    return content.ifBlank { reasoning.orEmpty() }
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.take(80)
        ?.takeIf { it.isNotBlank() }
        ?: "收藏消息"
}

private fun AiChatMessageSnapshot.drawerPreview(): String {
    return content.ifBlank { reasoning.orEmpty() }
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.take(80)
        ?.takeIf { it.isNotBlank() }
        ?: "收藏消息"
}

private fun ChatUiMessage.toSnapshot(): AiChatMessageSnapshot {
    return AiChatMessageSnapshot(
        id = id,
        role = when (role) {
            ChatRole.USER -> AiChatMessageSnapshot.ROLE_USER
            ChatRole.ASSISTANT -> AiChatMessageSnapshot.ROLE_ASSISTANT
        },
        content = content,
        meta = meta,
        reasoning = reasoning,
        toolTrace = toolTrace,
        memoryTrace = memoryTrace,
        elapsedMs = elapsedMs,
        favorite = favorite,
        deliveryState = deliveryState.snapshotValue,
        uploadContent = uploadContent,
        promptTokens = promptTokens,
        localPromptTokens = localPromptTokens,
        contextAnchorTokens = contextAnchorTokens,
        localContextAnchorTokens = localContextAnchorTokens,
        usageProviderId = usageProviderId,
        usageModelId = usageModelId,
        contextCompactionBeforeTokens = contextCompaction?.beforeTokens,
        contextCompactionAfterTokens = contextCompaction?.afterTokens,
        contextCompactionRevision = contextCompaction?.revision,
        contextCompactionSummaryPromptTokens = contextCompaction?.summaryPromptTokens,
        contextCompactionSummaryCompletionTokens = contextCompaction?.summaryCompletionTokens
    )
}

private fun AiChatMessageSnapshot.toUiMessage(): ChatUiMessage {
    return ChatUiMessage(
        id = id.ifBlank { UUID.randomUUID().toString() },
        role = when (role) {
            AiChatMessageSnapshot.ROLE_USER -> ChatRole.USER
            else -> ChatRole.ASSISTANT
        },
        content = content,
        meta = meta,
        reasoning = reasoning,
        toolTrace = toolTrace,
        memoryTrace = memoryTrace.orEmpty(),
        elapsedMs = elapsedMs,
        favorite = favorite,
        deliveryState = ChatDeliveryState.fromSnapshot(deliveryState),
        uploadContent = uploadContent,
        promptTokens = promptTokens,
        localPromptTokens = localPromptTokens,
        contextAnchorTokens = contextAnchorTokens,
        localContextAnchorTokens = localContextAnchorTokens,
        usageProviderId = usageProviderId,
        usageModelId = usageModelId,
        contextCompaction = contextCompactionBeforeTokens
            ?.let { beforeTokens ->
                contextCompactionAfterTokens?.let { afterTokens ->
                    AiChatCompactionRecord(
                        beforeTokens = beforeTokens,
                        afterTokens = afterTokens,
                        revision = contextCompactionRevision ?: 1,
                        summaryPromptTokens = contextCompactionSummaryPromptTokens ?: 0,
                        summaryCompletionTokens = contextCompactionSummaryCompletionTokens ?: 0
                    )
                }
            }
    )
}

private data class ChatUiMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val content: String,
    val meta: String? = null,
    val reasoning: String? = null,
    val toolTrace: List<String> = emptyList(),
    val memoryTrace: List<AiMemoryTraceItem> = emptyList(),
    val elapsedMs: Long? = null,
    val favorite: Boolean = false,
    val loading: Boolean = false,
    val deliveryState: ChatDeliveryState = ChatDeliveryState.SENT,
    val uploadContent: String? = null,
    val promptTokens: Int? = null,
    val localPromptTokens: Int? = null,
    val contextAnchorTokens: Int? = null,
    val localContextAnchorTokens: Int? = null,
    val usageProviderId: String? = null,
    val usageModelId: String? = null,
    val contextCompaction: AiChatCompactionRecord? = null
)

private data class AiChatContextSessionUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val latestCompaction: AiChatCompactionRecord? = null
) {
    val hasData: Boolean
        get() = promptTokens > 0 || completionTokens > 0 || latestCompaction != null
}

private fun List<ChatUiMessage>.contextSessionUsage(): AiChatContextSessionUsage {
    val normalMessages = filter { message ->
        message.role == ChatRole.ASSISTANT && message.contextCompaction == null
    }
    return AiChatContextSessionUsage(
        promptTokens = normalMessages.sumOf { message ->
            message.promptTokens ?: message.meta.parseAiTokenStats().promptTokens.toInt()
        } + sumOf { it.contextCompaction?.summaryPromptTokens ?: 0 },
        completionTokens = normalMessages.sumOf { message ->
            message.meta.parseAiTokenStats().completionTokens.toInt()
        } + sumOf { it.contextCompaction?.summaryCompletionTokens ?: 0 },
        latestCompaction = asReversed().firstNotNullOfOrNull { it.contextCompaction }
    )
}

private data class DrawerFavoriteItem(
    val messageId: String,
    val title: String,
    val preview: String,
    val session: AiChatSessionSnapshot?
)

private data class DrawerHistoryGroup(
    val title: String,
    val items: List<DrawerHistoryItem>
)

private data class DrawerHistoryItem(
    val session: AiChatSessionSnapshot,
    val groupTitle: String,
    val title: String,
    val subtitle: String
)

private data class DrawerConversationIdentity(
    val groupTitle: String,
    val title: String,
    val subtitle: String
)

private data class DrawerScene(
    val groupTitle: String,
    val title: String
)

private data class AiChatStats(
    val totalConversations: Int,
    val totalMessages: Int,
    val totalPromptTokens: Long,
    val totalCompletionTokens: Long,
    val totalElapsedMs: Long,
    val conversationsPerDay: Map<LocalDate, Int>,
    val tokenActivityPerDay: Map<LocalDate, Long>
)

private data class AiTokenStats(
    val promptTokens: Long = 0L,
    val completionTokens: Long = 0L,
    val elapsedMs: Long = 0L
)

private enum class TokenActivityMode(val title: String) {
    DAILY("每日"),
    MONTHLY("每月")
}

private data class TokenActivityBar(
    val axisLabel: String,
    val rangeLabel: String,
    val value: Long,
    val defaultSelected: Boolean
)

private data class GlobalMessageSearchResult(
    val session: AiChatSessionSnapshot?,
    val sessionId: String?,
    val sessionTitle: String,
    val messageId: String,
    val messageIndex: Int,
    val roleLabel: String,
    val snippet: String,
    val updatedAt: Long
)

private enum class PendingMessageAction {
    REGENERATE,
    DELETE
}

private enum class ChatExportFormat(val mimeType: String) {
    MARKDOWN("text/markdown"),
    IMAGE("image/png")
}

private data class ChatPreviewItem(
    val originalIndex: Int,
    val message: ChatUiMessage
)

private enum class ChatRole {
    USER,
    ASSISTANT
}

private enum class ChatDeliveryState(val snapshotValue: String) {
    SENT(AiChatMessageSnapshot.DELIVERY_SENT),
    QUEUED(AiChatMessageSnapshot.DELIVERY_QUEUED),
    IN_FLIGHT(AiChatMessageSnapshot.DELIVERY_IN_FLIGHT),
    RECOVERABLE(AiChatMessageSnapshot.DELIVERY_IN_FLIGHT),
    FAILED(AiChatMessageSnapshot.DELIVERY_FAILED);

    companion object {
        fun fromSnapshot(value: String): ChatDeliveryState {
            return when (value) {
                AiChatMessageSnapshot.DELIVERY_QUEUED -> QUEUED
                AiChatMessageSnapshot.DELIVERY_IN_FLIGHT -> RECOVERABLE
                AiChatMessageSnapshot.DELIVERY_FAILED -> FAILED
                else -> SENT
            }
        }
    }
}

private fun formatContextTokens(tokens: Int): String {
    return "${formatContextTokenValue(tokens)} tokens"
}

private fun formatContextTokenValue(tokens: Int): String {
    val (value, suffix) = when {
        tokens >= 1_000_000 -> tokens / 1_000_000f to "M"
        tokens >= 1_000 -> tokens / 1_000f to "K"
        else -> return "$tokens"
    }
    return if (value >= 100 || value % 1f == 0f) {
        "${value.toInt()}$suffix"
    } else {
        String.format(java.util.Locale.US, "%.1f$suffix", value)
    }
}
