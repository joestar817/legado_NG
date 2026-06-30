package io.legado.app.ui.config

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
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.History
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
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Image as ImageIcon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.google.gson.JsonObject
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.ai.AiChatClient
import io.legado.app.help.ai.AiChatHistoryState
import io.legado.app.help.ai.AiChatHistoryStore
import io.legado.app.help.ai.AiChatMessageSnapshot
import io.legado.app.help.ai.AiChatSessionSnapshot
import io.legado.app.help.ai.AiChatStreamUpdate
import io.legado.app.help.ai.AiChatTurnResult
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.ai.AiSkillRegistry
import io.legado.app.help.ai.AiSkillScope
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.book.isUpError
import io.legado.app.help.book.isVideo
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.ui.widget.compose.NgFunctionMenu
import io.legado.app.ui.widget.compose.NgFunctionMenuAction
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.openUrl
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
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
        const val ENTRY_BOOKSHELF = "bookshelf"
        const val CONTEXT_BOOKSHELF = "bookshelf"
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
    val entryAttachments = remember(entrySource) {
        buildEntryInputAttachments(entrySource)
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
    val uploadMessages = remember { mutableStateListOf<JsonObject>() }
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
    var selectedDrawerIndex by remember { mutableIntStateOf(2) }
    var configVersion by remember { mutableIntStateOf(0) }
    var previewMode by remember { mutableStateOf(false) }
    var globalSearchMode by remember { mutableStateOf(false) }
    var globalSearchQuery by remember { mutableStateOf("") }
    var exportSelectionMode by remember { mutableStateOf(false) }
    var exportFormatDialog by remember { mutableStateOf(false) }
    var showContextAttachmentSheet by remember { mutableStateOf(false) }
    var showSkillAttachmentSheet by remember { mutableStateOf(false) }
    var contextPreviewAttachments by remember { mutableStateOf<List<AiChatInputAttachment>>(emptyList()) }
    val selectedExportMessageIds = remember { mutableStateListOf<String>() }
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
    val currentTitle = sessions.firstOrNull { it.id == activeSessionId }?.title
        ?: messages.deriveChatTitle()
        ?: "新聊天"

    fun updateMessage(id: String, transform: (ChatUiMessage) -> ChatUiMessage) {
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) {
            messages[index] = transform(messages[index])
        }
    }

    LaunchedEffect(Unit) {
        val history = withContext(Dispatchers.IO) {
            AiChatHistoryStore.load()
        }
        sessions.clear()
        sessions += history.sessions
        val activeSession = history.activeSessionId
            ?.let { activeId -> history.sessions.firstOrNull { it.id == activeId } }
            ?: history.sessions.firstOrNull()
        if (entryAttachments.isNotEmpty()) {
            activeSessionId = UUID.randomUUID().toString()
            uploadMessages.clear()
            uploadMessages += chatClient.newSystemMessage()
            messages.clear()
            selectedDrawerIndex = 2
        } else if (activeSession != null) {
            activeSessionId = activeSession.id
            messages.replaceWith(activeSession.messages.map { it.toUiMessage() })
            uploadMessages.replaceUploadMessages(activeSession, chatClient)
            inputAttachments.replaceWith(buildSkillInputAttachments(activeSession.loadedSkillIds))
        } else if (uploadMessages.isEmpty()) {
            uploadMessages += chatClient.newSystemMessage()
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
                            ?: message.toolTrace
                    )
                }
            }
            delay(32)
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
    BackHandler(enabled = previewMode) {
        previewMode = false
    }
    BackHandler(enabled = exportSelectionMode) {
        exportSelectionMode = false
        exportFormatDialog = false
        selectedExportMessageIds.clear()
    }

    fun placeholder(label: String) {
        Toast.makeText(context, "$label 后续接入", Toast.LENGTH_SHORT).show()
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
            uploadMessages = uploadMessages.map { it.deepCopy().asJsonObject }
        )
        sessions.removeAll { it.id == sessionId }
        sessions.add(0, snapshot)
        sessions.sortChatSessions()
        saveHistory(sessionId)
    }

    fun startNewChat() {
        persistActiveSession()
        activeSessionId = UUID.randomUUID().toString()
        uploadMessages.clear()
        uploadMessages += chatClient.newSystemMessage()
        messages.clear()
        inputAttachments.clear()
        selectedDrawerIndex = 2
        saveHistory(activeSessionId)
    }

    fun openSession(session: AiChatSessionSnapshot) {
        activeSessionId = session.id
        messages.replaceWith(session.messages.map { it.toUiMessage() })
        uploadMessages.replaceUploadMessages(session, chatClient)
        inputAttachments.replaceWith(buildSkillInputAttachments(session.loadedSkillIds))
        selectedDrawerIndex = 2
        saveHistory(session.id)
    }

    fun sendCurrentMessages() {
        sending = true
        val loadingId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        activeLoadingMessageId = loadingId
        activeLoadingStartedAt = startedAt
        activeStreamContentTarget = ""
        activeStreamReasoningTarget = ""
        activeStreamToolTraceTarget = emptyList()
        val uploadMessageSizeBeforeSend = uploadMessages.size
        messages += ChatUiMessage(
            id = loadingId,
            role = ChatRole.ASSISTANT,
            content = "正在思考...",
            elapsedMs = 0L,
            loading = true
        )
        sendingJob?.cancel()
        sendingJob = scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    chatClient.send(uploadMessages) { update: AiChatStreamUpdate ->
                        scope.launch {
                            if (activeLoadingMessageId == loadingId) {
                                activeStreamContentTarget = update.content
                                activeStreamReasoningTarget = update.reasoning.orEmpty()
                                activeStreamToolTraceTarget = update.toolTrace
                            }
                        }
                    }
                }
            }
            val elapsedMs = System.currentTimeMillis() - startedAt
            messages.removeAll { it.id == loadingId }
            result.onSuccess {
                messages += it.toUiMessage(elapsedMs)
            }.onFailure {
                while (uploadMessages.size > uploadMessageSizeBeforeSend) {
                    uploadMessages.removeAt(uploadMessages.lastIndex)
                }
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
        }
    }

    fun stopSending() {
        if (!sending) return
        sendingJob?.cancel()
    }

    fun sendMessage(messageOverride: String? = null) {
        if (sending) return
        val content = (messageOverride ?: input).trim()
        if (content.isBlank()) {
            Toast.makeText(context, R.string.ai_chat_empty, Toast.LENGTH_SHORT).show()
            return
        }
        input = ""
        val userMessage = ChatUiMessage(role = ChatRole.USER, content = content)
        messages += userMessage
        uploadMessages += chatClient.newUserMessage(
            buildUserUploadContent(content, inputAttachments)
        )
        inputAttachments.removeAll { it.type == AiChatInputAttachmentType.CONTEXT }
        persistActiveSession()
        sendCurrentMessages()
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
        messages.replaceWith(truncatedMessages)
        uploadMessages.clear()
        uploadMessages.addAll(rebuildUploadMessages(chatClient, messages))
        persistActiveSession()
        sendCurrentMessages()
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
        selectedDrawerIndex = 2
        persistActiveSession()
        Toast.makeText(context, "已创建分支", Toast.LENGTH_SHORT).show()
    }

    fun pinSession(sessionId: String?) {
        if (sessionId.isNullOrBlank()) {
            persistActiveSession()
        }
        val targetId = sessionId ?: activeSessionId ?: return
        val index = sessions.indexOfFirst { it.id == targetId }
        if (index < 0) {
            return
        }
        sessions[index] = sessions[index].copy(
            isPinned = true,
            updatedAt = System.currentTimeMillis()
        )
        sessions.sortChatSessions()
        saveHistory(activeSessionId)
        Toast.makeText(context, "已置顶", Toast.LENGTH_SHORT).show()
    }

    fun deleteSession(session: AiChatSessionSnapshot) {
        val wasActive = session.id == activeSessionId
        sessions.removeAll { it.id == session.id }
        scope.launch(Dispatchers.IO) {
            AiChatHistoryStore.deleteSession(session.id)
        }
        if (wasActive) {
            val nextSession = sessions.firstOrNull()
            if (nextSession != null) {
                openSession(nextSession)
            } else {
                activeSessionId = null
                messages.clear()
                uploadMessages.clear()
                uploadMessages += chatClient.newSystemMessage()
                saveHistory(null)
            }
        } else {
            saveHistory(activeSessionId)
        }
        Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
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
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            RikkaChatDrawer(
                selectedIndex = selectedDrawerIndex,
                sessions = sessions,
                activeSessionId = activeSessionId,
                favoriteItems = buildDrawerFavoriteItems(sessions, activeSessionId, messages),
                backgroundDrawable = chatBackgroundDrawable,
                onSelect = {
                    selectedDrawerIndex = it
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
                onDeleteSession = ::deleteSession,
                onPinFavorite = { item ->
                    pinSession(item.session?.id)
                },
                onDeleteFavorite = ::deleteFavorite,
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
                            AiChatSkillSuggestionRow(
                                visible = messages.isEmpty()
                                        && input.isBlank()
                                        && inputAttachments.any {
                                    it.type == AiChatInputAttachmentType.SKILL
                                            && it.suggestions.isNotEmpty()
                                },
                                suggestions = inputAttachments
                                    .filter { it.type == AiChatInputAttachmentType.SKILL }
                                    .flatMap { it.suggestions }
                                    .distinct(),
                                onSelect = { suggestion ->
                                    sendMessage(suggestion)
                                }
                            )
                            AiChatLoadedSkillBar(
                                skills = inputAttachments.filter {
                                    it.type == AiChatInputAttachmentType.SKILL
                                },
                                onRemove = { skill ->
                                    inputAttachments.removeAll { it.id == skill.id }
                                    persistActiveSession()
                                }
                            )
                            RikkaChatInput(
                                value = input,
                                onValueChange = { input = it },
                                sending = sending,
                                modelIconRes = selectedModelIconRes,
                                reasoningEnabled = selectedReasoningEnabled,
                                mcpEnabled = internalMcpEnabled,
                                skillEnabled = inputAttachments.any {
                                    it.type == AiChatInputAttachmentType.SKILL
                                },
                                attachments = inputAttachments,
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
                                    AiAssistantConfigUi.showInternalMcpSheet(context) {
                                        configVersion++
                                    }
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
                                }
                            )
                        }
                    }
                ) { padding ->
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
                        onJumpToMessage = { index ->
                            previewMode = false
                            scope.launch {
                                listState.animateScrollToItem(index)
                            }
                        }
                    )
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
            description = "技能会作为可见加载项加入输入框，用于给普通聊天补充场景说明。",
            emptyText = "暂无可在聊天中加载的技能",
            availableAttachments = buildAgentSkillInputAttachments(),
            loadedAttachmentIds = inputAttachments.map { it.id }.toSet(),
            onAdd = { attachment ->
                if (inputAttachments.none { it.id == attachment.id }) {
                    inputAttachments += attachment
                    persistActiveSession()
                }
                showSkillAttachmentSheet = false
            },
            onDismiss = { showSkillAttachmentSheet = false }
        )
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
    sessions: List<AiChatSessionSnapshot>,
    activeSessionId: String?,
    favoriteItems: List<DrawerFavoriteItem>,
    backgroundDrawable: Drawable?,
    onSelect: (Int) -> Unit,
    onSessionSelect: (AiChatSessionSnapshot) -> Unit,
    onPinSession: (AiChatSessionSnapshot) -> Unit,
    onDeleteSession: (AiChatSessionSnapshot) -> Unit,
    onPinFavorite: (DrawerFavoriteItem) -> Unit,
    onDeleteFavorite: (DrawerFavoriteItem) -> Unit,
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
                when (selectedIndex) {
                    3 -> DrawerFavoriteContent(
                        favoriteItems = favoriteItems,
                        onSessionSelect = onSessionSelect,
                        onPinFavorite = onPinFavorite,
                        onDeleteFavorite = onDeleteFavorite
                    )

                    else -> DrawerHistoryContent(
                        sessions = sessions,
                        activeSessionId = activeSessionId,
                        onSessionSelect = onSessionSelect,
                        onPinSession = onPinSession,
                        onDeleteSession = onDeleteSession,
                        onNewChat = onNewChat
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerHistoryContent(
    sessions: List<AiChatSessionSnapshot>,
    activeSessionId: String?,
    onSessionSelect: (AiChatSessionSnapshot) -> Unit,
    onPinSession: (AiChatSessionSnapshot) -> Unit,
    onDeleteSession: (AiChatSessionSnapshot) -> Unit,
    onNewChat: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DrawerSectionTitle("聊天历史")
        if (sessions.isEmpty()) {
            DrawerConversationRow(
                label = "新聊天",
                selected = true,
                onClick = onNewChat
            )
        } else {
            sessions.forEach { session ->
                DrawerConversationRow(
                    label = session.title,
                    selected = session.id == activeSessionId,
                    onClick = { onSessionSelect(session) },
                    onPin = { onPinSession(session) },
                    onDelete = { onDeleteSession(session) }
                )
            }
        }
    }
}

@Composable
private fun DrawerFavoriteContent(
    favoriteItems: List<DrawerFavoriteItem>,
    onSessionSelect: (AiChatSessionSnapshot) -> Unit,
    onPinFavorite: (DrawerFavoriteItem) -> Unit,
    onDeleteFavorite: (DrawerFavoriteItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DrawerSectionTitle("收藏内容")
        if (favoriteItems.isEmpty()) {
            Text(
                text = "暂无收藏内容",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        } else {
            favoriteItems.forEach { item ->
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
private fun DrawerSectionTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
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
    selected: Boolean,
    onClick: () -> Unit,
    onPin: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 5.dp, end = 4.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (onPin != null && onDelete != null) {
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
                            onExport = { onExport(message) }
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
                    onExport = { onExport(message) }
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
    onExport: () -> Unit
) {
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
            } else {
                if (!message.reasoning.isNullOrBlank()) {
                    ReasoningEntry(
                        reasoning = message.reasoning,
                        elapsedMs = message.elapsedMs
                    )
                }
                MarkdownMessageText(
                    content = message.content,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .padding(top = 2.dp)
                )
                if (message.toolTrace.isNotEmpty()) {
                    ToolTraceEntry(message.toolTrace)
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
private fun RikkaChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    sending: Boolean,
    @DrawableRes modelIconRes: Int,
    reasoningEnabled: Boolean,
    mcpEnabled: Boolean,
    skillEnabled: Boolean,
    attachments: List<AiChatInputAttachment>,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onModelClick: () -> Unit,
    onReasoningClick: () -> Unit,
    onMcpClick: () -> Unit,
    onSkillClick: () -> Unit,
    onContextClick: () -> Unit,
    onContextPreview: (AiChatInputAttachment) -> Unit,
    onRemoveAttachment: (AiChatInputAttachment) -> Unit
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
                        InputDrawableIcon(
                            iconRes = R.drawable.ic_ai_context_menu,
                            active = attachments.any {
                                it.type == AiChatInputAttachmentType.CONTEXT
                            },
                            onClick = onContextClick
                        )
                        Surface(
                            onClick = {
                                if (sending) onStop() else onSend()
                            },
                            enabled = sending || value.isNotBlank(),
                            modifier = Modifier.size(38.dp),
                            shape = CircleShape,
                            color = when {
                                sending -> MaterialTheme.colorScheme.errorContainer
                                value.isBlank() -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.primary
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (sending) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                                    contentDescription = "Send",
                                    tint = if (sending) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else if (value.isBlank()) {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.onPrimary
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiChatLoadedSkillBar(
    skills: List<AiChatInputAttachment>,
    onRemove: (AiChatInputAttachment) -> Unit
) {
    AnimatedVisibility(visible = skills.isNotEmpty()) {
        Surface(color = Color.Transparent) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "当前skill：${skills.joinToString("、") { it.title }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                skills.forEach { skill ->
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .size(26.dp)
                            .clickable { onRemove(skill) },
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
private fun AiChatSkillSuggestionRow(
    visible: Boolean,
    suggestions: List<String>,
    onSelect: (String) -> Unit
) {
    AnimatedVisibility(visible = visible) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEach { suggestion ->
                Surface(
                    onClick = { onSelect(suggestion) },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = suggestion,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
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
        elapsedMs = elapsedMs
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
                ChatRole.USER -> add(chatClient.newUserMessage(message.content))
                ChatRole.ASSISTANT -> if (message.meta != "error") {
                    add(chatClient.newAssistantMessage(message.content, message.reasoning))
                }
            }
        }
    }
}

private fun buildEntryInputAttachments(entrySource: String?): List<AiChatInputAttachment> {
    return when (entrySource) {
        AiChatActivity.ENTRY_BOOKSHELF -> buildList {
            add(buildBookshelfContextInputAttachment())
            AiSkillRegistry.get(AiSkillRegistry.SKILL_BOOKSHELF_MANAGEMENT)
                ?.let(::toSkillInputAttachment)
                ?.let(::add)
        }

        else -> emptyList()
    }
}

private fun buildContextInputAttachments(contextSources: List<String>): List<AiChatInputAttachment> {
    return contextSources.distinct().mapNotNull { source ->
        when (source) {
            AiChatActivity.CONTEXT_BOOKSHELF -> buildBookshelfContextInputAttachment()
            else -> null
        }
    }
}

private fun buildBookshelfContextInputAttachment(): AiChatInputAttachment {
    return AiChatInputAttachment(
        id = "context.bookshelf.current",
        type = AiChatInputAttachmentType.CONTEXT,
        title = "当前书架",
        subtitle = "书架摘要，一次性附加",
        prompt = buildBookshelfContextSummary()
    )
}

private fun buildBookshelfContextSummary(): String {
    val allBooks = appDb.bookDao.all
    val shelfBooks = allBooks.filterNot { it.isNotShelf }
    val groups = appDb.bookGroupDao.all
    val customGroups = groups.filter { it.groupId > 0L }
    val customGroupMask = customGroups.fold(0L) { acc, group -> acc or group.groupId }
    val ungroupedCount = shelfBooks.count { book ->
        customGroupMask == 0L || (book.group and customGroupMask) == 0L
    }
    val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    return buildString {
        appendLine("# 当前书架摘要")
        appendLine()
        appendLine("- 生成时间：$generatedAt")
        appendLine("- 图书总数：${allBooks.size}")
        appendLine("- 正式书架图书：${shelfBooks.size}")
        appendLine("- 临时/未加入书架图书：${allBooks.size - shelfBooks.size}")
        appendLine("- 分组数量：${groups.size}（自定义 ${customGroups.size}）")
        appendLine("- 未加入自定义分组：$ungroupedCount")
        appendLine()
        appendLine("## 类型分布")
        appendLine()
        appendLine("| 类型 | 数量 |")
        appendLine("| --- | ---: |")
        appendLine("| 文本/网络 | ${shelfBooks.count { !it.isLocal && !it.isAudio && !it.isVideo && !it.isImage }} |")
        appendLine("| 本地 | ${shelfBooks.count { it.isLocal }} |")
        appendLine("| 音频 | ${shelfBooks.count { it.isAudio }} |")
        appendLine("| 视频 | ${shelfBooks.count { it.isVideo }} |")
        appendLine("| 图片 | ${shelfBooks.count { it.isImage }} |")
        appendLine("| 更新异常 | ${shelfBooks.count { it.isUpError }} |")
        appendLine()
        appendLine("## 自定义分组概况")
        appendLine()
        if (customGroups.isEmpty()) {
            appendLine("- 暂无自定义分组")
        } else {
            appendLine("| 分组 | 图书数 |")
            appendLine("| --- | ---: |")
            customGroups.sortedWith(compareBy<BookGroup> { it.order }.thenBy { it.groupName })
                .take(20)
                .forEach { group ->
                    val count = shelfBooks.count { (it.group and group.groupId) > 0L }
                    appendLine("| ${group.groupName.escapeMarkdownCell()} | $count |")
                }
            if (customGroups.size > 20) {
                appendLine()
                appendLine("- 还有 ${customGroups.size - 20} 个自定义分组未列出")
            }
        }
        appendTopCounts("作者分布 Top 8", shelfBooks.map { it.author.ifBlank { "未知作者" } })
        appendTopCounts("来源分布 Top 8", shelfBooks.map { it.originName.ifBlank { it.origin.ifBlank { "未知来源" } } })
    }
}

private fun StringBuilder.appendTopCounts(title: String, values: List<String>) {
    val counts = values.groupingBy { it }.eachCount()
        .toList()
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
        .take(8)
    appendLine()
    appendLine("## $title")
    appendLine()
    if (counts.isEmpty()) {
        appendLine("- 暂无数据")
        return
    }
    appendLine("| 名称 | 数量 |")
    appendLine("| --- | ---: |")
    counts.forEach { (name, count) ->
        appendLine("| ${name.escapeMarkdownCell()} | $count |")
    }
}

private fun String.escapeMarkdownCell(): String {
    return replace("|", "\\|").replace("\n", " ")
}

private fun buildAgentSkillInputAttachments(): List<AiChatInputAttachment> {
    return AiSkillRegistry.agentSkills().map(::toSkillInputAttachment)
}

private fun buildSkillInputAttachments(skillIds: List<String>): List<AiChatInputAttachment> {
    return skillIds.distinct().mapNotNull { skillId ->
        AiSkillRegistry.get(skillId)
            ?.takeIf { it.scope == AiSkillScope.AGENT }
            ?.let(::toSkillInputAttachment)
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
    if (attachments.isEmpty()) {
        return userContent
    }
    val contexts = attachments.filter { it.type == AiChatInputAttachmentType.CONTEXT }
    val skills = attachments.filter { it.type == AiChatInputAttachmentType.SKILL }
    return buildString {
        append("以下是用户在输入框中可见并随本次消息附带的 App 上下文与 Skill 说明。")
        append("这些内容用于帮助你理解场景，不要在回复中逐字复述；用户可随时移除它们。\n\n")
        if (contexts.isNotEmpty()) {
            append("## App 上下文\n")
            contexts.forEach { attachment ->
                append("### ").append(attachment.title).append('\n')
                append(attachment.prompt).append("\n\n")
            }
        }
        if (skills.isNotEmpty()) {
            append("## 已加载 Skill\n")
            skills.forEach { attachment ->
                append("### ").append(attachment.title).append('\n')
                append(attachment.prompt).append("\n\n")
            }
        }
        append("## 用户消息\n")
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
        toolTrace.joinToString("\n")
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

private fun buildDrawerFavoriteItems(
    sessions: List<AiChatSessionSnapshot>,
    activeSessionId: String?,
    messages: List<ChatUiMessage>
): List<DrawerFavoriteItem> {
    val activeSession = sessions.firstOrNull { it.id == activeSessionId }
    val activeTitle = activeSession?.title ?: messages.deriveChatTitle() ?: "当前聊天"
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
                        title = session.title,
                        preview = message.drawerPreview(),
                        session = session
                    )
                }
        }
    return (activeItems + historyItems)
        .distinctBy { "${it.session?.id.orEmpty()}:${it.messageId}" }
        .take(50)
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
        elapsedMs = elapsedMs,
        favorite = favorite
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
        elapsedMs = elapsedMs,
        favorite = favorite
    )
}

private data class ChatUiMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val content: String,
    val meta: String? = null,
    val reasoning: String? = null,
    val toolTrace: List<String> = emptyList(),
    val elapsedMs: Long? = null,
    val favorite: Boolean = false,
    val loading: Boolean = false
)

private data class DrawerFavoriteItem(
    val messageId: String,
    val title: String,
    val preview: String,
    val session: AiChatSessionSnapshot?
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
