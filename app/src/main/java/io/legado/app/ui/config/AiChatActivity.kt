package io.legado.app.ui.config

import android.net.Uri

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.legado.app.help.ai.AI_BOOK_SEARCH_AUDIO_SCOPE_MEDIA
import io.legado.app.help.ai.AI_BOOK_SEARCH_SCOPE
import io.legado.app.help.ai.AiBookSearchAvailability
import io.legado.app.help.ai.AiBookSearchCandidate
import io.legado.app.help.ai.AiBookSearchReply
import io.legado.app.help.ai.AiBookSourceLookup
import io.legado.app.help.ai.AiBookSourceStatus
import io.legado.app.help.ai.bookSearchAvailability
import io.legado.app.help.ai.bookSearchDraft
import io.legado.app.help.ai.explicitAiBookSearchCandidate
import io.legado.app.ui.book.changesource.ChangeSourceFilter
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.info.EXTRA_BOOK_INFO_SEARCH_RESULT
import io.legado.app.ui.book.search.SearchScopeDialog
import io.legado.app.constant.BookSourceType
import io.legado.app.web.mcp.McpPreciseBookSearch

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.SystemClock
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.activity.ComponentDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
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
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.doOnLayout
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.Glide
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.AgentMemory
import io.legado.app.help.ai.AgentModeDefinition
import io.legado.app.help.ai.AgentModeEntryContext
import io.legado.app.help.ai.AgentModeEntryLaunchDecision
import io.legado.app.help.ai.AgentModeEntryMemoryState
import io.legado.app.help.ai.AgentModeRegistry
import io.legado.app.help.ai.AiChatClient
import io.legado.app.help.ai.AiActiveSkillSnapshot
import io.legado.app.help.ai.AiAvailableSkillSnapshot
import io.legado.app.help.ai.AiChatContextManager
import io.legado.app.help.ai.AiChatContextUsage
import io.legado.app.help.ai.AiChatCompactionRecord
import io.legado.app.help.ai.AiChatPromptUsageAnchor
import io.legado.app.help.ai.AiChatHistoryStore
import io.legado.app.help.ai.AiChatGptException
import io.legado.app.help.ai.AiChatInteraction
import io.legado.app.help.ai.AiChatInteractionOption
import io.legado.app.help.ai.AiChatInteractionParser
import io.legado.app.help.ai.AiChatInteractionRender
import io.legado.app.help.ai.AiChatInteractionPolicyContract
import io.legado.app.help.ai.AiChatInteractionPolicyRegistry
import io.legado.app.help.ai.AiChatInteractionMemoryWriter
import io.legado.app.help.ai.AiChatInteractionSubmit
import io.legado.app.help.ai.AiChatInteractionType
import io.legado.app.help.ai.AiChatMessageSnapshot
import io.legado.app.help.ai.codexImageFile
import io.legado.app.help.ai.AI_IMAGE_ATTACHMENT_LIMIT
import io.legado.app.help.ai.AiImageAssets
import io.legado.app.help.ai.availableCodexImageProvider
import io.legado.app.help.ai.isCodexChatSelection
import io.legado.app.help.ai.isDirectImageAction
import io.legado.app.help.ai.isCodexImage
import io.legado.app.help.ai.AiChatSessionSnapshot
import io.legado.app.help.ai.AiChatTurnResult
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.ai.AiMemoryTraceItem
import io.legado.app.help.ai.AiWebAccess
import io.legado.app.help.ai.AiWebActivity
import io.legado.app.help.ai.AiWebActivityAction
import io.legado.app.help.ai.AiExternalSearchStore
import io.legado.app.help.ai.hasLegacyChatGptNativeLabel
import io.legado.app.help.ai.webAccess
import io.legado.app.help.ai.webUrl
import io.legado.app.help.ai.withInlineLinks
import io.legado.app.help.ai.AiModelAbility
import io.legado.app.help.ai.AiModelGenerationIssue
import io.legado.app.help.ai.AiModelSelection
import io.legado.app.help.ai.AiProviderSetting
import io.legado.app.help.ai.AiProviderStore
import io.legado.app.help.ai.AiSkillRegistry
import io.legado.app.help.ai.AiSkillCapabilityGrantStore
import io.legado.app.help.ai.AiSkillPackageRegistry
import io.legado.app.help.ai.AiSkillPresentation
import io.legado.app.help.ai.AiSkillScope
import io.legado.app.help.ai.resolveAgentModeEntryLaunch
import io.legado.app.help.ai.resolveAgentModeEntryMemoryProbeTarget
import io.legado.app.help.ai.resolveModelGenerationRoute
import io.legado.app.help.ai.runtime.AgentSkillRuntimeDeclaration
import io.legado.app.help.ai.runtime.ToolExecutionReceipt
import io.legado.app.help.config.NgThemeRuntimeAssets
import io.legado.app.help.config.ThemeConfig
import io.legado.app.ui.design.theme.NgInterfaceFontMarkwonPlugin
import io.legado.app.ui.widget.compose.NgExpandableChildRow
import io.legado.app.ui.widget.compose.NgExpandableListVariant
import io.legado.app.ui.widget.compose.NgExpandableSectionHeader
import io.legado.app.ui.widget.compose.NgFunctionMenu
import io.legado.app.ui.widget.compose.NgFunctionMenuAction
import io.legado.app.ui.widget.compose.NgListBadge
import io.legado.app.ui.widget.compose.toggleNgExpandedKey
import io.legado.app.ui.widget.dialog.applyNgWindow
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgCircularSelectionIndicator
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgDotsLoadingIndicator
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDrawerBackdropSource
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgDrawerGlassAlertDialog
import io.legado.app.ui.design.components.compose.NgFloatingIconToolbar
import io.legado.app.ui.design.components.compose.NgFloatingIconToolbarAction
import io.legado.app.ui.design.components.compose.NgFloatingSearchToolbar
import io.legado.app.ui.design.components.compose.NgFloatingTitleToolbar
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgLiquidGlassBackdrop
import io.legado.app.ui.design.components.compose.NgLiquidGlassBackdropProvider
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgPaneDialog
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.components.compose.NgSideDrawerSurface
import io.legado.app.ui.design.components.compose.NgVisualSurface
import io.legado.app.ui.design.components.compose.ngDrawerContentCardColor
import io.legado.app.ui.design.components.compose.ngDrawerPrimaryTextColor
import io.legado.app.ui.design.components.compose.ngDrawerSecondaryTextColor
import io.legado.app.ui.design.components.compose.ngRecordLiquidGlassBackdrop
import io.legado.app.ui.design.components.compose.rememberNgLiquidGlassBackdrop
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.NgThemeGradientBackground
import io.legado.app.ui.design.theme.NgEffectThemeBackground
import io.legado.app.ui.design.theme.NgSolidButtonPalette
import io.legado.app.utils.GSON
import io.legado.app.utils.dpToPx
import io.legado.app.utils.openUrl
import io.legado.app.web.mcp.McpInternalToolCatalog
import io.legado.app.web.mcp.McpInternalToolCapability
import io.legado.app.web.mcp.McpInternalToolModule
import io.legado.app.web.mcp.McpToolSideEffect
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        const val EXTRA_MODE_ENTRY_CONTEXT = "mode_entry_context"
        const val EXTRA_INITIAL_INPUT = "initial_input"
        const val EXTRA_EXPAND_SUGGESTIONS = "expand_suggestions"
        internal const val EXTRA_TASK_SESSION_ID = "task_session_id"
        internal const val EXTRA_START_NEW_CHAT = "start_new_chat"
        const val ENTRY_BOOKSHELF = "bookshelf"
        const val ENTRY_BOOK_DETAIL = "book_detail"
        const val ENTRY_BOOK_SCAN = "book_scan"
        const val ENTRY_BOOK_SEARCH = "book_search"
        const val ENTRY_TEXT_SELECTION = "text_selection"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            NgAppTheme {
                AiChatRoute(
                    onBack = { finish() },
                    onOpenWebSettings = { AiAssistantWebSettingsDialog.show(supportFragmentManager) },
                )
            }
        }
    }
}

private const val MODE_ENTRY_START_PROMPT =
    "[AI_MODE_START] 使用当前固定 Mode 入口上下文完整执行系统工作流，" +
        "直到产生工作流要求的最终可见结果。不要要求用户重复提供入口信息，" +
        "不要复述本条内部触发消息，不要输出中间过程播报。"

private const val AI_CHAT_DRAWER_WIDTH_RATIO = 0.65f
private val AiChatComposerBottomPadding = 4.dp
private val AiChatComposerCornerRadius = 20.dp

private val MODE_ENTRY_CONTEXT_TOKEN =
    Regex("""\{\{context\.(title|payload\.[A-Za-z0-9_]+)\}\}""")

internal fun renderModeEntryConfirmationTemplate(
    template: String,
    context: AgentModeEntryContext?
): String {
    return MODE_ENTRY_CONTEXT_TOKEN.replace(template) { match ->
        when (val path = match.groupValues[1]) {
            "title" -> context?.title.orEmpty()
            else -> {
                val key = path.removePrefix("payload.")
                runCatching {
                    context?.payload
                        ?.get(key)
                        ?.takeIf { it.isJsonPrimitive }
                        ?.asJsonPrimitive
                        ?.asString
                }.getOrNull().orEmpty()
            }
        }
    }.trim()
}

internal fun buildModeEntryConfirmation(
    mode: AgentModeDefinition,
    context: AgentModeEntryContext?,
    memoryState: AgentModeEntryMemoryState = AgentModeEntryMemoryState.NOT_REQUIRED
): AiChatInteraction? {
    val probe = mode.entryPolicy.memoryProbe
    val confirmation = when {
        probe == null -> mode.entryPolicy.confirmation
        memoryState == AgentModeEntryMemoryState.ABSENT -> probe.absent
        memoryState == AgentModeEntryMemoryState.PRESENT -> probe.present
        else -> null
    }
    val notice = when {
        probe == null -> null
        memoryState == AgentModeEntryMemoryState.LOADING -> probe.loading
        memoryState == AgentModeEntryMemoryState.DISABLED -> probe.disabled
        memoryState == AgentModeEntryMemoryState.ERROR -> probe.error
        else -> null
    }
    val title = confirmation?.title ?: notice?.title ?: return null
    val description = confirmation?.description ?: notice?.description ?: return null
    val actionOptions = confirmation?.actions.orEmpty().map { action ->
        AiChatInteractionOption(
            label = action.label,
            value = action.value,
            description = action.description
        )
    }
    return AiChatInteraction(
        version = 1,
        id = "${mode.id}_entry_confirmation",
        type = if (actionOptions.isNotEmpty()) {
            AiChatInteractionType.ACTIONS
        } else {
            AiChatInteractionType.CONFIRM
        },
        title = renderModeEntryConfirmationTemplate(title, context),
        description = renderModeEntryConfirmationTemplate(description, context),
        options = actionOptions,
        submit = confirmation?.let {
            AiChatInteractionSubmit(
                label = it.confirmLabel,
                promptTemplate = if (actionOptions.isNotEmpty()) {
                    MODE_ENTRY_ACTION_PROMPT_TEMPLATE
                } else {
                    it.confirmLabel
                }
            )
        },
        cancel = null
    )
}

private const val MODE_ENTRY_ACTION_PROMPT_TEMPLATE = "[MODE_ENTRY_ACTION] action={{value}}"

internal enum class AiChatInputAttachmentType {
    CONTEXT,
    SKILL
}

internal data class AiChatInputAttachment(
    val id: String,
    val type: AiChatInputAttachmentType,
    val title: String,
    val subtitle: String,
    val prompt: String,
    val suggestions: List<String> = emptyList(),
    val skillRuntime: AgentSkillRuntimeDeclaration = AgentSkillRuntimeDeclaration(),
    val skillRevision: String = "",
    val skillContentHash: String = "",
    val skillRuntimeRevision: String = "",
    val availableSkillIds: List<String> = emptyList(),
    val availableSkills: List<AiAvailableSkillSnapshot> = emptyList(),
    val trustedBuiltIn: Boolean = false
)

private data class AiChatInteractionAction(
    val interaction: AiChatInteraction,
    val displayText: String,
    val prompt: String,
    val selectedValues: Map<String, String> = emptyMap(),
    val skipped: Boolean = false,
    val onCompleted: (Boolean) -> Unit = {}
)

private class AiModelSheetLaunchGate {
    private var showing = false
    private var lastOpenedAt = Long.MIN_VALUE

    fun tryOpen(nowMillis: Long): Boolean {
        if (showing || lastOpenedAt != Long.MIN_VALUE && nowMillis - lastOpenedAt < 700L) {
            return false
        }
        showing = true
        lastOpenedAt = nowMillis
        return true
    }

    fun release() {
        showing = false
    }
}

@Composable
private fun ChatBackgroundImage(
    drawable: Drawable,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = drawable) {
        value = withContext(Dispatchers.Default) {
            when (drawable) {
                is BitmapDrawable -> drawable.bitmap.asImageBitmap()
                else -> (drawable.constantState?.newDrawable() ?: drawable)
                    .toBitmap()
                    .asImageBitmap()
            }
        }
    }
    bitmap?.let { image ->
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiChatRoute(onBack: () -> Unit, onOpenWebSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelSheetLaunchGate = remember { AiModelSheetLaunchGate() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val chatClient = aiChatSessions.client
    val entrySource = remember {
        (context as? AiChatActivity)?.intent?.getStringExtra(AiChatActivity.EXTRA_ENTRY)
    }
    val initialAgentMode = remember(entrySource) {
        resolveEntryAgentMode(entrySource)
    }
    val initialModeEntryContext = remember(entrySource) {
        AgentModeEntryContext.fromJsonOrNull(
            (context as? AiChatActivity)
                ?.intent
                ?.getStringExtra(AiChatActivity.EXTRA_MODE_ENTRY_CONTEXT)
        )
    }
    val preloadSkillIds = remember {
        (context as? AiChatActivity)
            ?.intent
            ?.getStringArrayListExtra(AiChatActivity.EXTRA_LOADED_SKILL_IDS)
            .orEmpty()
    }
    val initialInput = remember {
        (context as? AiChatActivity)
            ?.intent
            ?.getStringExtra(AiChatActivity.EXTRA_INITIAL_INPUT)
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
    val taskSessionId = remember {
        (context as? AiChatActivity)?.intent?.getStringExtra(AiChatActivity.EXTRA_TASK_SESSION_ID)
    }
    val expandSuggestionsOnEntry = remember {
        taskSessionId == null && (context as? AiChatActivity)
            ?.intent
            ?.getBooleanExtra(AiChatActivity.EXTRA_EXPAND_SUGGESTIONS, false) == true
    }
    val entryAttachments = remember(
        entrySource,
        initialAgentMode,
        preloadSkillIds,
        preloadContextAttachments
    ) {
        (buildEntryInputAttachments(entrySource) +
            buildModeSkillInputAttachments(initialAgentMode, preloadSkillIds) +
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
    val sessionEntry = remember {
        if (taskSessionId != null || initialModeEntryContext?.contextId == CHARACTER_CARD_ENTRY_CONTEXT) null
        else chatSessionEntry(
            entrySource,
            initialAgentMode,
            initialModeEntryContext,
            initialInput,
            preloadSkillIds,
            preloadContextAttachments,
        )
    }
    var resumeSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    val restoreEntry = remember {
        val startNew = (context as? AiChatActivity)?.intent
            ?.getBooleanExtra(AiChatActivity.EXTRA_START_NEW_CHAT, false) == true
        sessionEntry.takeIf { resumeSessionId == null && taskSessionId == null && !startNew }
    }
    val targetSessionId = remember { resumeSessionId ?: taskSessionId ?: restoreEntry?.let(aiChatSessions::selectedFor) }
    val liveSession = remember { aiChatSessions.get(targetSessionId) }
    val initialSession = remember {
        liveSession ?: AiChatSessionRuntime(aiChatSessions, initialAgentMode, initialModeEntryContext, entryAttachments, initialInput)
    }
    val selectedSession = remember { mutableStateOf(initialSession) }
    val chatSession = selectedSession.value
    val sessionToRestore = targetSessionId?.takeIf { it != initialSession.id }
    var sessionRestored by remember { mutableStateOf(sessionToRestore == null) }
    val activeSessionId = chatSession.id
    val activeAgentMode = chatSession.mode
    var activeModeEntryContext by chatSession.entryContextState
    var modeEntryStarted by chatSession.modeStartedState
    var input by chatSession.inputState
    var bookSearchDraftEdited by chatSession.draftEditedState
    var bookSearchGreetingPending by chatSession.greetingPendingState
    val bookSearchHistoryRestored = sessionRestored
    var contextStatusText by chatSession.contextStatusState
    val messages = chatSession.messages
    val uploadMessages = chatSession.uploadMessages
    val inputAttachments = chatSession.inputAttachments
    val skillManagedCapabilityIds = chatSession.skillManagedCapabilityIds
    val manualMcpCapabilityIds = chatSession.manualMcpCapabilityIds
    val enabledMcpCapabilityIds = chatSession.enabledMcpCapabilityIds
    val contextUsage = chatSession.contextUsage
    val sending = chatSession.sending
    val manualContextCompacting = chatSession.manualContextCompacting
    val activeLoadingMessageId = chatSession.activeLoadingMessageId
    val activeLoadingStartedAt = chatSession.activeLoadingStartedAt
    val activeWebAccess = chatSession.activeWebAccess
    val sessions = aiChatSessions.history
    val loadedSessionDetailIds = aiChatSessions.loadedDetailIds
    val inputAttachmentUsageKey = inputAttachments.joinToString(separator = "|") { attachment ->
        "${attachment.id}:${attachment.prompt.hashCode()}"
    }
    val listState = rememberLazyListState()
    var showContextUsageDialog by remember { mutableStateOf(false) }
    var modeEntryMemoryState by remember(chatSession.id) {
        mutableStateOf(
            if (activeAgentMode.entryPolicy.memoryProbe == null) {
                AgentModeEntryMemoryState.NOT_REQUIRED
            } else {
                AgentModeEntryMemoryState.LOADING
            }
        )
    }
    var bookOpenJob by remember { mutableStateOf<Job?>(null) }
    var bookScopeSelection by remember(activeSessionId) { mutableStateOf<Pair<String, AiBookSearchCandidate>?>(null) }

    DisposableEffect(Unit) {
        aiChatSessions.attachWindow()
        onDispose { aiChatSessions.detachWindow() }
    }
    DisposableEffect(chatSession) {
        aiChatSessions.bind(chatSession)
        onDispose { aiChatSessions.unbind(chatSession) }
    }
    LaunchedEffect(sessionRestored) {
        if (sessionRestored && sessionEntry?.dedicated == true &&
            liveSession == null && selectedSession.value === initialSession) {
            aiChatSessions.select(sessionEntry, initialSession, createdForEntry = true)
        }
    }
    LaunchedEffect(chatSession, sessionRestored) {
        if (sessionRestored) {
            resumeSessionId = chatSession.id
            aiChatSessions.select(sessionEntry, chatSession)
            if (chatSession.savedSnapshot != null) aiChatSessions.saveSelection(chatSession.id)
        }
    }
    var selectedDrawerIndex by remember { mutableIntStateOf(2) }
    var configVersion by remember { mutableIntStateOf(0) }
    var generationBlock by remember { mutableStateOf<Pair<String, AiModelGenerationIssue>?>(null) }
    var showModelSheet by remember { mutableStateOf(false) }
    var showReasoningSheet by remember { mutableStateOf(false) }
    var showInternalMcpSheet by remember { mutableStateOf(false) }
    var chatSearch by remember(activeSessionId) { mutableStateOf<AiChatConversationSearchState?>(null) }
    val searchFocusManager = LocalFocusManager.current
    val searchKeyboard = LocalSoftwareKeyboardController.current
    var globalSearchMode by remember { mutableStateOf(false) }
    var globalSearchQuery by remember { mutableStateOf("") }
    var pendingGlobalSearchResult by remember { mutableStateOf<GlobalMessageSearchResult?>(null) }
    var statsMode by remember { mutableStateOf(false) }
    var imageImporting by remember(activeSessionId) { mutableStateOf(false) }
    var imagePreview by remember(activeSessionId) { mutableStateOf<Pair<File, Boolean>?>(null) }
    var showCodexImageSettings by remember(activeSessionId) { mutableStateOf(false) }
    val imageAttachmentsAvailable = activeAgentMode.id == AgentModeRegistry.GENERAL_ID
    fun currentModelSelection() = AiModelSelection(AiConfig.assistantProviderId, AiConfig.assistantModelId)
    val codexChatSelected = remember(configVersion) { currentModelSelection().isCodexChatSelection() }
    val showImageActions = imageAttachmentsAvailable && codexChatSelected
    val imageAttachmentMenu = remember(activeSessionId, showImageActions) { NgPopupToggleState() }
    val directImageDraft = showImageActions && chatSession.directImageDraft
    LaunchedEffect(chatSession, sessionRestored, showImageActions) {
        if (sessionRestored && !showImageActions && chatSession.directImageDraft) {
            chatSession.setExplicitImageDraft(false)
            chatSession.saveImageDraft()
        }
    }
    var exportSelectionMode by remember { mutableStateOf(false) }
    var exportFormatDialog by remember { mutableStateOf(false) }
    var showContextAttachmentSheet by remember { mutableStateOf(false) }
    var showSkillAttachmentSheet by remember { mutableStateOf(false) }
    var contextPreviewAttachments by remember { mutableStateOf<List<AiChatInputAttachment>>(emptyList()) }
    var pendingSkillCapabilityAuthorization by remember {
        mutableStateOf<AiChatInputAttachment?>(null)
    }
    var historyLoaded by remember { mutableStateOf(false) }
    var historyDetailsLoaded by remember { mutableStateOf(false) }
    var historyDetailsLoading by remember { mutableStateOf(false) }
    var pendingHistoryReadyAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var openingSessionId by remember { mutableStateOf<String?>(null) }
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
    val messageModelProviders = remember(configVersion) {
        AiProviderStore.providers().associateBy { it.id }
    }
    val selectedReasoningEnabled = remember(configVersion) {
        AiAssistantConfigUi.selectedReasoningEnabled()
    }
    val searchSettings by AiExternalSearchStore.changes.collectAsState()
    val bookSearchMode = activeAgentMode.id == AgentModeRegistry.BOOK_SEARCH_ID
    val webAccess = if (activeAgentMode.id == AgentModeRegistry.GENERAL_ID || bookSearchMode) {
        remember(configVersion, searchSettings, activeWebAccess, bookSearchMode) {
            val selection = AiAssistantConfigUi.selectedModel()
            searchSettings.webAccess(selection?.provider, selection?.model, activeWebAccess, bookSearchMode)
        }
    } else null
    val selectedModelSupportsToolCalls = remember(configVersion) {
        AiAssistantConfigUi.selectedModel()?.model
            ?.abilities
            ?.contains(AiModelAbility.TOOL) == true
    }
    val internalMcpEnabled = remember(configVersion) {
        AiConfig.internalMcpEnabled
    }
    val activeSkillAttachments = inputAttachments.filter {
        it.type == AiChatInputAttachmentType.SKILL
    }
    val hasBookSearchSkill = activeSkillAttachments.any { it.id == "skill.${AiSkillRegistry.SKILL_BOOK_SEARCH}" }
    fun bookSearchAvailability(): AiBookSearchAvailability {
        if (activeAgentMode.id != AgentModeRegistry.BOOK_SEARCH_ID) return AiBookSearchAvailability.READY
        val selection = AiAssistantConfigUi.selectedModel()
        val provider = selection?.provider ?: AiConfig.assistantProviderId.takeIf { it.isNotBlank() }
            ?.let(AiProviderStore::provider)
        return AiExternalSearchStore.current().bookSearchAvailability(hasBookSearchSkill, provider, selection?.model)
    }
    val bookSearchAvailability = remember(bookSearchMode, configVersion, searchSettings, hasBookSearchSkill) {
        bookSearchAvailability()
    }
    val fixedEntrySkillIds = entryAttachments
        .filter { it.type == AiChatInputAttachmentType.SKILL }
        .mapTo(mutableSetOf(), AiChatInputAttachment::id)
    val hasFixedEntrySkill = activeSkillAttachments.any { it.id in fixedEntrySkillIds }
    val skillInputPolicy = resolveSkillInputPolicy(
        hasActiveSkill = activeSkillAttachments.isNotEmpty(),
        allowsUserSkills = activeAgentMode.allowsUserSkills,
        fixedEntrySkill = hasFixedEntrySkill,
        modelSupportsToolCalls = selectedModelSupportsToolCalls || bookSearchMode
    )
    val skillToolErrorText = if (skillInputPolicy.toolError) {
        "当前模型不支持工具调用，请切换模型"
    } else {
        null
    }
    LaunchedEffect(skillInputPolicy.selectorVisible) {
        if (!skillInputPolicy.selectorVisible) {
            showSkillAttachmentSheet = false
            pendingSkillCapabilityAuthorization = null
        }
    }
    val skillSuggestions = inputAttachments
        .filter { it.type == AiChatInputAttachmentType.SKILL }
        .flatMap { it.suggestions }
        .distinct()
    var suggestionsExpanded by remember { mutableStateOf(expandSuggestionsOnEntry) }
    val currentTitle = resolveConversationTitle(
        modeEntryTitle = activeModeEntryContext?.title,
        derivedTitle = messages.deriveChatTitle(),
        previousTitle = sessions.firstOrNull { it.id == activeSessionId }?.title
    )
    val interactionPolicy = remember(activeAgentMode.identity) {
        AiChatInteractionPolicyRegistry.resolve(activeAgentMode)
    }

    fun ensureSelectedModelInCatalog(): Boolean {
        val missing = AiAssistantConfigUi.missingSelectedModelLabel() ?: return true
        if (modelSheetLaunchGate.tryOpen(SystemClock.elapsedRealtime())) {
            Toast.makeText(context, missing, Toast.LENGTH_LONG).show()
            showModelSheet = true
        }
        return false
    }

    fun ensureSkillToolSupport(): Boolean {
        val message = skillToolErrorText ?: return true
        if (!ensureSelectedModelInCatalog()) return false
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        return false
    }

    fun ensureModelGenerationRoute(): Boolean {
        val selection = AiAssistantConfigUi.selectedModel() ?: return ensureSelectedModelInCatalog()
        val issue = selection.provider.resolveModelGenerationRoute(selection.model).issue ?: return true
        generationBlock = selection.provider.id to issue
        return false
    }

    LaunchedEffect(Unit) {
        val history = aiChatSessions.readHistory(AiChatHistoryStore::loadIndex)
        aiChatSessions.mergeIndex(history.sessions)
        historyLoaded = true
        chatSession.refreshContextUsage()
        selectedDrawerIndex = 2
    }
    LaunchedEffect(chatSession, chatSession.providerStateRevision) {
        if (chatSession.providerStateRevision > 0) configVersion++
    }
    LaunchedEffect(chatSession, configVersion) {
        if (!chatSession.sending) chatSession.refreshContextUsage()
    }
    LaunchedEffect(
        activeSessionId,
        activeAgentMode.identity,
        activeModeEntryContext?.toJson(),
        modeEntryStarted,
        messages.size,
        configVersion
    ) {
        val probe = activeAgentMode.entryPolicy.memoryProbe
        if (probe == null || modeEntryStarted || messages.isNotEmpty()) {
            modeEntryMemoryState = AgentModeEntryMemoryState.NOT_REQUIRED
            return@LaunchedEffect
        }
        if (!AiConfig.memoryEnabled) {
            modeEntryMemoryState = AgentModeEntryMemoryState.DISABLED
            return@LaunchedEffect
        }
        modeEntryMemoryState = AgentModeEntryMemoryState.LOADING
        val target = runCatching {
            resolveAgentModeEntryMemoryProbeTarget(
                mode = activeAgentMode,
                context = activeModeEntryContext
            )
        }.getOrElse {
            modeEntryMemoryState = AgentModeEntryMemoryState.ERROR
            return@LaunchedEffect
        }
        modeEntryMemoryState = runCatching {
            withContext(Dispatchers.IO) {
                appDb.agentMemoryDao.search(
                    scopeType = target.scopeType,
                    scopeKey = target.scopeKey,
                    subject = "",
                    domain = target.domain,
                    memoryType = target.memoryType,
                    keyword = "",
                    status = target.status,
                    offset = 0,
                    limit = 1
                ).isNotEmpty()
            }
        }.fold(
            onSuccess = { present ->
                if (present) AgentModeEntryMemoryState.PRESENT
                else AgentModeEntryMemoryState.ABSENT
            },
            onFailure = { AgentModeEntryMemoryState.ERROR }
        )
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
        loadedSkills.singleOrNull()?.let { loadedSkill ->
            val granted = resolveModeManagedCapabilities(activeAgentMode, listOf(loadedSkill))
            if (skillManagedCapabilityIds.toList() != granted) {
                skillManagedCapabilityIds.replaceWith(granted)
            }
            val merged = McpInternalToolCatalog.normalizeCapabilityIds(
                manualMcpCapabilityIds + skillManagedCapabilityIds
            )
            if (merged != enabledMcpCapabilityIds.toList()) {
                enabledMcpCapabilityIds.replaceWith(merged)
            }
            val missing = if (activeAgentMode.allowsUserSkills) {
                loadedSkill.skillRuntime.mcpCapabilities.toSet() - granted.toSet()
            } else {
                emptySet()
            }
            if (missing.isNotEmpty() && pendingSkillCapabilityAuthorization == null) {
                pendingSkillCapabilityAuthorization = loadedSkill
            }
        } ?: run {
            if (skillManagedCapabilityIds.isNotEmpty()) {
                skillManagedCapabilityIds.clear()
                enabledMcpCapabilityIds.replaceWith(
                    McpInternalToolCatalog.normalizeCapabilityIds(manualMcpCapabilityIds)
                )
            }
        }
        if (historyLoaded) {
            chatSession.refreshContextUsage()
        }
    }
    val pendingSearchJump = pendingGlobalSearchResult
    LaunchedEffect(activeSessionId, messages.size) {
        if (chatSearch == null && pendingSearchJump == null && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    LaunchedEffect(chatSession, activeLoadingMessageId, activeLoadingStartedAt, sending) {
        val loadingId = activeLoadingMessageId ?: return@LaunchedEffect
        val startedAt = activeLoadingStartedAt ?: return@LaunchedEffect
        while (chatSession.sending && chatSession.activeLoadingMessageId == loadingId) {
            chatSession.updateMessage(loadingId) { message ->
                if (message.loading) {
                    message.copy(elapsedMs = System.currentTimeMillis() - startedAt)
                } else {
                    message
                }
            }
            delay(250)
        }
    }
    LaunchedEffect(chatSession, activeLoadingMessageId, sending) {
        val loadingId = activeLoadingMessageId ?: return@LaunchedEffect
        while (chatSession.sending && chatSession.activeLoadingMessageId == loadingId) {
            chatSession.updateMessage(loadingId) { message ->
                if (!message.loading) {
                    message
                } else {
                    val visibleContent = message.content
                        .takeUnless { it == "正在思考..." }
                        .orEmpty()
                    val nextContent = nextStreamingText(
                        current = visibleContent,
                        target = chatSession.activeStreamContentTarget
                    )
                    val nextReasoning = nextStreamingText(
                        current = message.reasoning.orEmpty(),
                        target = chatSession.activeStreamReasoningTarget
                    )
                    message.copy(
                        content = nextContent.ifBlank { "正在思考..." },
                        imageWait = chatSession.imageWait,
                        reasoning = nextReasoning.takeIf { it.isNotBlank() },
                        toolTrace = chatSession.activeStreamToolTraceTarget
                            .takeIf { it.isNotEmpty() }
                            ?: message.toolTrace,
                        memoryTrace = chatSession.activeStreamMemoryTraceTarget
                            .takeIf { it.isNotEmpty() }
                            ?: message.memoryTrace,
                        webActivity = chatSession.activeWebActivity,
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
    BackHandler(enabled = exportSelectionMode) {
        exportSelectionMode = false
        exportFormatDialog = false
        selectedExportMessageIds.clear()
    }
    BackHandler(enabled = historyManageMode) {
        historyManageMode = false
        selectedHistoryIds.clear()
    }
    fun showChatSearchResults(edit: Boolean) {
        chatSearch?.let { search ->
            search.focusResults = edit
            search.location = null
        }
    }

    fun exitChatSearch() {
        val search = chatSearch ?: return
        searchFocusManager.clearFocus()
        searchKeyboard?.hide()
        if (search.location == null && !search.hasLocatedMessage) {
            listState.requestScrollToItem(search.entryIndex, search.entryOffset)
        }
        chatSearch = null
    }

    fun backFromChatSearch() {
        if (chatSearch?.location != null) {
            showChatSearchResults(edit = false)
        } else {
            exitChatSearch()
        }
    }

    BackHandler(enabled = chatSearch != null && drawerState.isClosed) {
        backFromChatSearch()
    }

    fun placeholder(label: String) {
        Toast.makeText(context, "$label 后续接入", Toast.LENGTH_SHORT).show()
    }

    val assistantSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        configVersion++
        chatSession.refreshContextUsage()
    }

    fun openAssistantSettings(page: String = AiConfigFragment.PAGE_ASSISTANT) {
        assistantSettingsLauncher.launch(Intent(context, ConfigActivity::class.java).apply {
            putExtra("configTag", ConfigTag.AI_CONFIG)
            putExtra(AiConfigFragment.EXTRA_INITIAL_PAGE, page)
        })
    }

    fun saveHistory(activeId: String = activeSessionId) {
        aiChatSessions.saveSelection(activeId)
    }

    fun persistActiveSession() {
        chatSession.persist()
        if (sessions.none { it.id == activeSessionId }) selectedHistoryIds.remove(activeSessionId)
    }

    fun handleBookSearchAction(messageId: String, action: AiBookSearchAction) {
        when (action) {
            is AiBookSearchAction.Lookup -> chatSession.launchBookSourceLookup(messageId, action.candidate, action.keyword)
            is AiBookSearchAction.Scope -> bookScopeSelection = messageId to action.candidate
            AiBookSearchAction.EnableMcp -> showInternalMcpSheet = true
            is AiBookSearchAction.Reject -> {
                if (messageId == chatSession.activeBookLookupMessageId) chatSession.cancelBookSourceLookup()
                chatSession.updateMessage(messageId) { message ->
                    val reply = message.bookSearch ?: return@updateMessage message
                    message.copy(bookSearch = reply.copy(
                        rejectedKeys = (reply.rejectedKeys + action.keys).distinct(),
                        lookup = reply.lookup?.takeUnless { it.candidateKey in action.keys },
                    ))
                }
                persistActiveSession()
            }
            is AiBookSearchAction.Open -> {
                if (bookOpenJob?.isActive == true) return
                val hit = action.hit
                val reply = messages.firstOrNull { it.id == messageId }?.bookSearch ?: return
                val lookup = reply.lookup?.takeIf { hit in it.hits } ?: return
                val media = reply.candidates.firstOrNull { it.key == lookup.candidateKey }?.media ?: return
                bookOpenJob = scope.launch {
                    val book = try {
                        withContext(Dispatchers.IO) { McpPreciseBookSearch.resolveHit(hit, lookup.keyword, media) }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                    if (selectedSession.value !== chatSession || messages.none {
                            it.id == messageId && hit in it.bookSearch?.lookup?.hits.orEmpty()
                        }) return@launch
                    if (book != null) {
                        context.startActivity(Intent(context, BookInfoActivity::class.java)
                            .putExtra(EXTRA_BOOK_INFO_SEARCH_RESULT, book))
                    } else {
                        chatSession.updateMessage(messageId) { message ->
                            val state = message.bookSearch ?: return@updateMessage message
                            val result = state.lookup?.takeUnless { it.status == AiBookSourceStatus.RUNNING }
                                ?: return@updateMessage message
                            message.copy(bookSearch = state.copy(lookup = result.copy(status = AiBookSourceStatus.OPEN_FAILED)))
                        }
                        persistActiveSession()
                        Toast.makeText(context, R.string.ai_book_search_open_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    LaunchedEffect(activeSessionId, input, bookSearchMode, bookSearchHistoryRestored, bookSearchDraftEdited) {
        if (bookSearchMode && bookSearchHistoryRestored && (bookSearchDraftEdited || messages.isNotEmpty())) {
            delay(350)
            persistActiveSession()
        }
    }

    fun updateMcpCapabilitySelection(capabilityIds: Collection<String>) {
        if (!activeAgentMode.allowsManualMcpCapabilities) return
        val normalized = McpInternalToolCatalog.normalizeCapabilityIds(capabilityIds)
        manualMcpCapabilityIds.replaceWith(
            normalized.filterNot { capabilityId -> capabilityId in skillManagedCapabilityIds }
        )
        enabledMcpCapabilityIds.replaceWith(
            McpInternalToolCatalog.normalizeCapabilityIds(
                manualMcpCapabilityIds + skillManagedCapabilityIds
            )
        )
        chatSession.refreshContextUsage()
        persistActiveSession()
    }

    fun activateSkillAttachment(attachment: AiChatInputAttachment) {
        if (!skillInputPolicy.selectorVisible) return
        inputAttachments.removeAll {
            it.type == AiChatInputAttachmentType.SKILL && it.id != attachment.id
        }
        if (inputAttachments.none { it.id == attachment.id }) {
            inputAttachments += attachment
        }
        skillManagedCapabilityIds.replaceWith(attachment.authorizedSkillCapabilities())
        enabledMcpCapabilityIds.replaceWith(
            McpInternalToolCatalog.normalizeCapabilityIds(
                manualMcpCapabilityIds + skillManagedCapabilityIds
            )
        )
        chatSession.refreshContextUsage()
        persistActiveSession()
    }

    fun requestSkillAttachment(attachment: AiChatInputAttachment) {
        if (!skillInputPolicy.selectorVisible) return
        val requested = attachment.skillRuntime.mcpCapabilities.toSet()
        val missing = requested - attachment.authorizedSkillCapabilities().toSet()
        if (missing.isNotEmpty()) {
            pendingSkillCapabilityAuthorization = attachment
            return
        }
        activateSkillAttachment(attachment)
    }

    fun selectRuntime(next: AiChatSessionRuntime, createdForEntry: Boolean = false) {
        if (next.deleted) return
        bookOpenJob?.cancel()
        bookScopeSelection = null
        openingSessionId = null
        selectedSession.value = next
        resumeSessionId = next.id
        aiChatSessions.select(sessionEntry, next, createdForEntry)
        selectedDrawerIndex = 2
        sessionRestored = true
    }

    fun startNewChat() {
        persistActiveSession()
        selectRuntime(
            AiChatSessionRuntime(aiChatSessions, initialAgentMode, initialModeEntryContext, entryAttachments),
            createdForEntry = true,
        )
    }

    fun applyLoadedSession(snapshot: AiChatSessionSnapshot) {
        if (aiChatSessions.wasDeleted(snapshot.id)) return
        aiChatSessions.get(snapshot.id)?.let {
            selectRuntime(it)
            return
        }
        val identity = snapshot.normalizedAgentModeIdentity()
        val mode = AgentModeRegistry.resolve(identity)
        if (mode == null) {
            Toast.makeText(context, "当前版本不支持 Agent Mode：${identity.id}@${identity.revision}", Toast.LENGTH_SHORT).show()
            return
        }
        selectRuntime(
            AiChatSessionRuntime(
                owner = aiChatSessions,
                mode = mode,
                initialContext = snapshot.modeEntryContext,
                initialAttachments = buildModeSkillInputAttachments(mode, snapshot.loadedSkillIds, snapshot.uploadMessages),
                initialInput = snapshot.modeEntryContext.bookSearchDraft().orEmpty(),
                id = snapshot.id,
                saved = snapshot,
            )
        )
    }

    LaunchedEffect(historyLoaded) {
        if (!historyLoaded || sessionRestored) return@LaunchedEffect
        val target = sessionToRestore ?: return@LaunchedEffect
        val original = selectedSession.value
        val readResult = runCatching { aiChatSessions.readHistory { AiChatHistoryStore.loadSession(target) } }
        val saved = readResult.getOrNull()?.takeIf { snapshot ->
            restoreEntry?.matches(snapshot.normalizedAgentModeIdentity(), snapshot.modeEntryContext) != false
        }
        if (target == taskSessionId && saved == null && readResult.isSuccess) aiChatSessions.forgetTask(target)
        if (selectedSession.value === original && !original.hasWork && original.messages.isEmpty() && saved != null) {
            applyLoadedSession(saved)
        }
        sessionRestored = true
    }

    fun openSession(session: AiChatSessionSnapshot) {
        if (session.id == activeSessionId || aiChatSessions.wasDeleted(session.id)) return
        aiChatSessions.get(session.id)?.let { running ->
            selectRuntime(running)
            return
        }
        val cached = sessions.firstOrNull { it.id == session.id && it.id in loadedSessionDetailIds }
        if (cached != null) {
            applyLoadedSession(cached)
            return
        }
        openingSessionId = session.id
        contextStatusText = "正在加载会话…"
        scope.launch {
            val loaded = runCatching {
                aiChatSessions.readHistory { AiChatHistoryStore.loadSession(session.id) }
            }
            if (openingSessionId != session.id || selectedSession.value !== chatSession) return@launch
            openingSessionId = null
            if (contextStatusText == "正在加载会话…") contextStatusText = null
            loaded.onSuccess { snapshot ->
                if (snapshot != null) applyLoadedSession(snapshot)
                else Toast.makeText(context, "会话不存在或已被删除", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "会话加载失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun ensureAllHistoryDetails(onReady: (() -> Unit)? = null) {
        if (historyDetailsLoaded) {
            onReady?.invoke()
            return
        }
        if (historyDetailsLoading) {
            if (onReady == null) pendingHistoryReadyAction = null
            return
        }
        pendingHistoryReadyAction = onReady
        historyDetailsLoading = true
        if (onReady != null) {
            Toast.makeText(context, "正在加载历史内容…", Toast.LENGTH_SHORT).show()
        }
        scope.launch {
            val details = runCatching {
                aiChatSessions.readHistory(AiChatHistoryStore::loadAllSessionDetails)
            }.getOrElse {
                historyDetailsLoading = false
                if (pendingHistoryReadyAction != null) {
                    Toast.makeText(context, "历史内容加载失败，请重试", Toast.LENGTH_SHORT).show()
                }
                pendingHistoryReadyAction = null
                return@launch
            }
            aiChatSessions.mergeDetails(details)
            historyDetailsLoaded = true
            historyDetailsLoading = false
            val readyAction = pendingHistoryReadyAction
            pendingHistoryReadyAction = null
            readyAction?.invoke()
        }
    }

    val activityState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val viewingConversation = sessionRestored && activityState.isAtLeast(Lifecycle.State.RESUMED) &&
        !statsMode && !globalSearchMode && drawerState.isClosed && chatSearch == null
    if (viewingConversation) {
        DisposableEffect(activeSessionId) {
            aiChatSessions.beginViewing(activeSessionId)
            onDispose { aiChatSessions.endViewing(activeSessionId) }
        }
    }
    val approval = chatSession.pendingApproval.takeIf {
        activityState.isAtLeast(Lifecycle.State.RESUMED) && !statsMode && !globalSearchMode && drawerState.isClosed
    }
    AiChatToolApprovalDialog(context as? AiChatActivity, approval)

    fun ensureCodexImageRoute(selection: AiModelSelection = currentModelSelection()): Boolean {
        if (!selection.isCodexChatSelection()) {
            Toast.makeText(context, R.string.ai_image_codex_model_required, Toast.LENGTH_SHORT).show()
            return false
        }
        if (AiProviderStore.provider(selection.providerId).availableCodexImageProvider() == null) {
            showCodexImageSettings = true
            return false
        }
        return true
    }

    fun sendCurrentMessages(activeUserMessageIds: List<String> = emptyList()) {
        val lastUser = uploadMessages.lastOrNull { it.get("role")?.asString == "user" }
        val direct = imageAttachmentsAvailable && lastUser?.isDirectImageAction() == true
        val selection = currentModelSelection()
        val ready = if (direct) {
            ensureCodexImageRoute(selection)
        } else {
            bookSearchAvailability() == AiBookSearchAvailability.READY &&
                ensureSkillToolSupport() && ensureModelGenerationRoute()
        }
        if (!ready) {
            chatSession.updateDeliveryState(activeUserMessageIds, ChatDeliveryState.RECOVERABLE)
            persistActiveSession()
            return
        }
        chatSession.sendCurrentMessages(activeUserMessageIds, selection)
    }

    fun stopSending() {
        bookOpenJob?.cancel()
        chatSession.stopSending()
    }

    BackHandler(
        enabled = bookSearchMode && drawerState.isClosed && !globalSearchMode && !statsMode &&
            !exportSelectionMode && !historyManageMode && chatSearch == null,
    ) {
        bookOpenJob?.cancel()
        persistActiveSession()
        onBack()
    }

    fun manuallyCompressContext() {
        if (!ensureModelGenerationRoute()) return
        chatSession.manuallyCompressContext(currentModelSelection())
    }

    fun addImageAttachments(ids: List<String>, editAsset: String? = null) {
        val combined = (chatSession.imageDraftAssets + ids).distinct()
        if (combined.size > AI_IMAGE_ATTACHMENT_LIMIT) {
            Toast.makeText(context, context.getString(R.string.ai_image_attachment_limit,
                AI_IMAGE_ATTACHMENT_LIMIT), Toast.LENGTH_SHORT).show()
            return
        }
        chatSession.replaceImageDraftAssets(combined, editAsset)
        persistActiveSession()
    }

    fun continueEditingImage(file: File) {
        if (!imageAttachmentsAvailable || chatSession.deleted || !ensureCodexImageRoute()) return
        val origin = chatSession
        imagePreview = null
        scope.launch {
            val knownId = file.nameWithoutExtension.takeIf { AiImageAssets.file(it) == file }
            if (origin.imageDraftAssets.size >= AI_IMAGE_ATTACHMENT_LIMIT &&
                (knownId == null || knownId !in origin.imageDraftAssets)
            ) {
                Toast.makeText(context, context.getString(R.string.ai_image_attachment_limit,
                    AI_IMAGE_ATTACHMENT_LIMIT), Toast.LENGTH_SHORT).show()
                return@launch
            }
            try {
                val assetId = knownId ?: AiImageAssets.importExistingResult(file)
                if (selectedSession.value === origin && !origin.deleted) {
                    val editAsset = assetId.takeIf { currentModelSelection().isCodexChatSelection() }
                    addImageAttachments(listOf(assetId), editAsset = editAsset)
                } else if (knownId == null) {
                    withContext(Dispatchers.IO) { AiImageAssets.discardUnsent(assetId) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Toast.makeText(context,
                    (error as? AiChatGptException)?.messageRes ?: R.string.ai_image_attachment_unavailable,
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun sendMessage(
        messageOverride: String? = null,
        uploadOverride: String? = null,
        imageRetry: ChatUiMessage? = null,
    ) {
        if (!sessionRestored || chatSession.deleted || chatSession.runningHostAction) return
        val useMediaDraft = imageAttachmentsAvailable && messageOverride == null && uploadOverride == null && imageRetry == null
        val imageAssets = imageRetry?.imageAssetIds
            ?: if (useMediaDraft) chatSession.imageDraftAssets.toList() else emptyList()
        val selection = currentModelSelection()
        val imageAction = imageRetry?.directImageAction
            ?: (useMediaDraft && chatSession.directImageDraft && selection.isCodexChatSelection())
        if (!imageAction && !ensureSkillToolSupport()) return
        val content = (imageRetry?.content ?: messageOverride ?: input).trim()
        if (imageImporting && useMediaDraft) return
        if (content.isBlank() && (imageAssets.isEmpty() || imageAction)) {
            val label = if (imageAction && imageAssets.isNotEmpty()) R.string.ai_image_describe_edit else R.string.ai_chat_empty
            Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
            return
        }
        if (chatSession.sending && (imageAction || imageAssets.isNotEmpty())) {
            Toast.makeText(context, R.string.ai_image_wait_turn, Toast.LENGTH_SHORT).show()
            return
        }
        if (imageAction && !ensureCodexImageRoute(selection)) return
        if (bookSearchAvailability() != AiBookSearchAvailability.READY) return
        if (!imageAction && !ensureModelGenerationRoute()) return
        val isBookSearch = activeAgentMode.id == AgentModeRegistry.BOOK_SEARCH_ID
        if (isBookSearch) chatSession.cancelBookSourceLookup()
        if (imageRetry == null) input = ""
        if (useMediaDraft) {
            chatSession.clearImageDraft()
            chatSession.saveImageDraft()
        }
        val originalUpload = uploadOverride
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: if (imageAction) content else buildUserUploadContent(content, inputAttachments)
        val rejected = if (isBookSearch) messages.flatMap { message ->
            val reply = message.bookSearch ?: return@flatMap emptyList()
            reply.candidates.filter { it.key in reply.rejectedKeys }
        }.distinctBy { it.key }.takeLast(12) else emptyList()
        val uploadContent = if (rejected.isEmpty()) originalUpload else originalUpload +
            rejected.joinToString(prefix = "\n\n[用户已排除的作品]\n", separator = "\n") {
                "《${it.title}》 ${it.author} (${it.media?.name.orEmpty()})"
            }
        val userMessage = ChatUiMessage(
            role = ChatRole.USER,
            content = content,
            deliveryState = ChatDeliveryState.QUEUED,
            uploadContent = uploadContent,
            imageAssetIds = imageAssets,
            directImageAction = imageAction,
        )
        val specified = if (isBookSearch && !chatSession.sending) explicitAiBookSearchCandidate(content) else null
        if (specified != null) {
            messages += userMessage.copy(deliveryState = ChatDeliveryState.SENT)
            val message = ChatUiMessage(
                role = ChatRole.ASSISTANT,
                content = context.getString(R.string.ai_book_search_identified, specified.title, specified.author),
                bookSearch = AiBookSearchReply(listOf(specified), verified = true, findInSources = true),
            )
            messages += message
            uploadMessages += chatClient.newUserMessage(uploadContent)
            uploadMessages += JsonObject().apply {
                addProperty("role", "assistant")
                addProperty("content", message.content)
            }
            chatSession.refreshContextUsage()
            persistActiveSession()
            chatSession.launchBookSourceLookup(message.id, specified, automatically = true)
            scope.launch {
                if (selectedSession.value === chatSession) listState.scrollToItem(messages.lastIndex.coerceAtLeast(0))
            }
            return
        }
        messages += userMessage
        scope.launch {
            if (selectedSession.value === chatSession && chatSearch == null) listState.scrollToItem(messages.lastIndex)
        }
        inputAttachments.removeAll { it.type == AiChatInputAttachmentType.CONTEXT }
        persistActiveSession()
        if (chatSession.sending) return
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
                ),
                message.imageAssetIds,
                message.directImageAction,
            )
        }
        chatSession.syncModeEntryContext()
        chatSession.syncActiveSkillContext()
        chatSession.updateDeliveryState(queuedIds, ChatDeliveryState.IN_FLIGHT)
        chatSession.refreshContextUsage()
        persistActiveSession()
        sendCurrentMessages(queuedIds)
    }

    fun submitInteraction(messageId: String, action: AiChatInteractionAction) {
        if (!ensureSkillToolSupport() || !ensureModelGenerationRoute()) {
            action.onCompleted(false)
            return
        }
        if (chatSession.sending || chatSession.runningHostAction) {
            Toast.makeText(context, "正在生成中", Toast.LENGTH_SHORT).show()
            action.onCompleted(false)
            return
        }
        if (action.interaction.type != AiChatInteractionType.MULTI_TAG_STANCE) {
            chatSession.markInteractionResolved(
                messageId = messageId,
                interactionId = action.interaction.id,
                resultLabel = action.displayText
            )
            action.onCompleted(true)
            sendMessage(action.displayText, action.prompt)
            return
        }
        chatSession.syncModeEntryContext()
        chatSession.syncActiveSkillContext()
        val activeSkill = AiChatContextManager.activeSkillSnapshot(uploadMessages)
        if (!action.skipped && activeSkill == null) {
            Toast.makeText(context, "当前系统工作流状态无效，请重新进入功能", Toast.LENGTH_LONG).show()
            action.onCompleted(false)
            return
        }
        val interaction = action.interaction
        val selectedValues = action.selectedValues.toMap()
        val skipped = action.skipped
        val modelSelection = currentModelSelection()
        val turnId = UUID.randomUUID().toString()
        val task = chatSession.runHostAction {
            val result = runCatching {
                if (skipped) {
                    AiChatInteractionMemoryWriter.skipped(interaction)
                } else {
                    withContext(Dispatchers.IO) {
                        AiChatInteractionMemoryWriter.write(
                            interaction = interaction,
                            selectedValues = selectedValues,
                            agentMode = mode,
                            activeSkill = requireNotNull(activeSkill),
                            conversationId = id,
                            turnId = turnId
                        )
                    }
                }
            }
            result.onSuccess { writeResult ->
                if (deleted) return@onSuccess
                markInteractionResolved(
                    messageId = messageId,
                    interactionId = interaction.id,
                    resultLabel = writeResult.displayText,
                    resultSelections = selectedValues
                )
                this.uploadMessages += aiChatSessions.client.newUserMessage(writeResult.continuationPrompt)
                sendCurrentMessages(emptyList(), modelSelection)
            }.onFailure { error ->
                if (!deleted && error !is CancellationException) {
                    this.contextStatusText = error.localizedMessage ?: "阅读偏好保存失败"
                }
            }
        }
        scope.launch {
            val result = task.await()
            if (selectedSession.value === chatSession) {
                action.onCompleted(result.isSuccess)
            }
        }
    }

    fun regenerateMessage(message: ChatUiMessage) {
        val targetIndex = messages.indexOfFirst { it.id == message.id }
        val imagePrompt = if (targetIndex < 0) null else messages.take(targetIndex + 1)
            .lastOrNull { it.role == ChatRole.USER }?.takeIf { it.directImageAction }
        if (imagePrompt != null) {
            if (chatSession.sending || chatSession.runningHostAction || imageImporting) return
            sendMessage(imageRetry = imagePrompt)
            return
        }
        if (!ensureSkillToolSupport() || !ensureModelGenerationRoute()) return
        if (bookSearchAvailability() != AiBookSearchAvailability.READY) return
        if (chatSession.sending || chatSession.runningHostAction) {
            Toast.makeText(context, "正在生成中", Toast.LENGTH_SHORT).show()
            return
        }
        if (activeAgentMode.id == AgentModeRegistry.BOOK_SEARCH_ID) chatSession.cancelBookSourceLookup()
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
            },
            targetInterruptedReplyId = message.id.takeIf {
                message.role == ChatRole.ASSISTANT && message.uploadContent != null
            }
        )
        messages.replaceWith(truncatedMessages)
        uploadMessages.clear()
        uploadMessages.addAll(preservedHistory ?: rebuildUploadMessages(chatClient, messages))
        chatSession.syncModeEntryContext()
        chatSession.syncActiveSkillContext()
        val activeUserIds = if (message.role == ChatRole.USER) listOf(message.id) else emptyList()
        chatSession.updateDeliveryState(activeUserIds, ChatDeliveryState.IN_FLIGHT)
        persistActiveSession()
        sendCurrentMessages(activeUserIds)
    }

    fun deleteMessage(message: ChatUiMessage) {
        if (chatSession.sending || chatSession.runningHostAction) {
            Toast.makeText(context, "正在生成中，暂不能删除消息", Toast.LENGTH_SHORT).show()
            return
        }
        if (message.id == chatSession.activeBookLookupMessageId) chatSession.cancelBookSourceLookup()
        val removed = messages.removeAll { it.id == message.id }
        if (!removed) {
            return
        }
        uploadMessages.clear()
        uploadMessages.addAll(rebuildUploadMessages(chatClient, messages))
        chatSession.refreshContextUsage()
        chatSession.contextCompactionCount = AiChatContextManager.compactionRevision(uploadMessages)
        persistActiveSession()
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

    fun selectAllExportMessages() {
        selectedExportMessageIds.replaceWith(messages.filterNot { it.loading }.map { it.id })
    }

    fun invertExportSelection() {
        val selected = selectedExportMessageIds.toSet()
        val allIds = messages.filterNot { it.loading }.map { it.id }
        selectedExportMessageIds.replaceWith(allIds.filterNot { it in selected })
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
        val defaultAssistantLabel = context.getString(R.string.ai_assistant)
        val senderLabel: (ChatUiMessage) -> String = { message ->
            if (message.role == ChatRole.USER) "用户"
            else resolveChatMessageModel(message.usageProviderId, message.usageModelId, messageModelProviders)
                .label ?: defaultAssistantLabel
        }
        runCatching {
            when (format) {
                ChatExportFormat.MARKDOWN -> exportChatMessagesAsMarkdown(
                    context, buildChatExportMarkdown(currentTitle, selectedMessages, senderLabel)
                )
                ChatExportFormat.IMAGE -> exportChatMessagesAsImage(
                    context, buildChatExportPlainText(currentTitle, selectedMessages, senderLabel)
                )
            }
        }.onSuccess { file ->
            shareChatExportFile(context, file, format.mimeType)
            cancelExportSelection()
        }.onFailure {
            Toast.makeText(context, "导出失败：${it.localizedMessage ?: it}", Toast.LENGTH_SHORT).show()
        }
    }

    fun forkMessage(message: ChatUiMessage) {
        if (chatSession.sending || chatSession.runningHostAction) {
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
        val forked = AiChatSessionRuntime(aiChatSessions, activeAgentMode, activeModeEntryContext, inputAttachments.toList())
        forked.modeEntryStarted = modeEntryStarted
        forked.manualMcpCapabilityIds.replaceWith(manualMcpCapabilityIds.toList())
        forked.enabledMcpCapabilityIds.replaceWith(enabledMcpCapabilityIds.toList())
        forked.messages.addAll(forkedMessages)
        forked.uploadMessages.replaceWith(rebuildUploadMessages(chatClient, forked.messages))
        forked.contextCompactionCount = AiChatContextManager.compactionRevision(forked.uploadMessages)
        forked.persist()
        selectRuntime(forked)
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
        aiChatSessions.pin(targetIds, System.currentTimeMillis())
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
        aiChatSessions.delete(targetIds)
        selectedHistoryIds.removeAll(targetIds)
        if (wasActive) {
            val next = sessions.firstOrNull()
            if (next == null) {
                selectRuntime(
                    AiChatSessionRuntime(aiChatSessions, initialAgentMode, initialModeEntryContext, entryAttachments),
                    createdForEntry = true,
                )
            } else openSession(next)
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
        aiChatSessions.get(itemSession.id)?.let { session ->
            session.updateMessage(item.messageId) { it.copy(favorite = false) }
            session.persist()
            Toast.makeText(context, "已取消收藏", Toast.LENGTH_SHORT).show()
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
        aiChatSessions.save(updatedSession)
        Toast.makeText(context, "已取消收藏", Toast.LENGTH_SHORT).show()
    }

    val theme = NgTheme.snapshot
    val chatBackgroundDrawable = remember(context, theme.isDark) {
        runCatching {
            ThemeConfig.getBgImage(context, context.resources.displayMetrics)
        }.getOrNull()
    }
    val wallpaperBackdrop = if (!theme.isEInk) {
        rememberNgLiquidGlassBackdrop()
    } else {
        null
    }
    val chatBackdrop = wallpaperBackdrop.takeUnless { globalSearchMode || statsMode }
    // Bubbles sample the wallpaper. The floating controls sample the conversation below them.
    val controlsBackdrop = if (chatBackdrop != null) {
        rememberNgLiquidGlassBackdrop()
    } else {
        null
    }

    fun showBookScanContinueEntry(action: AiChatInteractionAction) {
        if (!ensureSkillToolSupport()) {
            action.onCompleted(false)
            return
        }
        if (chatSession.sending || modeEntryStarted || messages.isNotEmpty()) {
            action.onCompleted(false)
            return
        }
        val entryPolicy = activeAgentMode.entryPolicy
        if (entryPolicy.requiresContext && activeModeEntryContext == null) {
            modeEntryStarted = true
            messages += ChatUiMessage(
                role = ChatRole.ASSISTANT,
                content = "缺少当前功能所需的入口上下文，请从对应功能入口重新进入。",
                meta = "error"
            )
            persistActiveSession()
            action.onCompleted(false)
            return
        }
        modeEntryStarted = true
        chatSession.syncModeEntryContext()
        chatSession.syncActiveSkillContext()
        persistActiveSession()
        val contextSnapshot = activeModeEntryContext
        val workKey = contextSnapshot.payloadString("work_key")
        val task = chatSession.runHostAction {
            val memoryResult = runCatching {
                withContext(Dispatchers.IO) {
                    appDb.agentMemoryDao.search(
                        scopeType = "book",
                        scopeKey = workKey,
                        subject = "",
                        domain = "book_scan",
                        memoryType = "",
                        keyword = "",
                        status = "active",
                        offset = 0,
                        limit = 20
                    )
                }
            }
            if (deleted) return@runHostAction
            this.messages += ChatUiMessage(
                role = ChatRole.ASSISTANT,
                content = buildBookScanContinueEntryContent(
                    context = contextSnapshot,
                    memories = memoryResult.getOrDefault(emptyList()),
                    loadError = memoryResult.exceptionOrNull()?.localizedMessage
                )
            )
            refreshContextUsage()
            persist()
        }
        scope.launch {
            task.await()
            if (selectedSession.value === chatSession) {
                action.onCompleted(true)
                if (chatSearch == null) listState.scrollToItem(messages.lastIndex)
            }
        }
    }

    fun startModeEntry(): Boolean {
        if (!ensureSkillToolSupport() || !ensureModelGenerationRoute()) return false
        if (sending || modeEntryStarted || messages.isNotEmpty()) {
            return false
        }
        val entryPolicy = activeAgentMode.entryPolicy
        if (entryPolicy.requiresContext && activeModeEntryContext == null) {
            modeEntryStarted = true
            messages += ChatUiMessage(
                role = ChatRole.ASSISTANT,
                content = "缺少当前功能所需的入口上下文，请从对应功能入口重新进入。",
                meta = "error"
            )
            persistActiveSession()
            return false
        }
        modeEntryStarted = true
        chatSession.syncModeEntryContext()
        chatSession.syncActiveSkillContext()
        uploadMessages += chatClient.newUserMessage(MODE_ENTRY_START_PROMPT)
        chatSession.refreshContextUsage()
        persistActiveSession()
        sendCurrentMessages()
        return true
    }

    val modeEntryStateDecision = if (sessionRestored && !sending) {
        resolveAgentModeEntryLaunch(
            policy = activeAgentMode.entryPolicy,
            hasRequiredContext = activeModeEntryContext != null,
            alreadyStarted = modeEntryStarted,
            hasVisibleMessages = messages.isNotEmpty()
        )
    } else {
        AgentModeEntryLaunchDecision.NONE
    }
    val modeEntryConfirmation = if (
        modeEntryStateDecision == AgentModeEntryLaunchDecision.SHOW_CONFIRMATION
    ) {
        buildModeEntryConfirmation(
            mode = activeAgentMode,
            context = activeModeEntryContext,
            memoryState = modeEntryMemoryState
        )
    } else {
        null
    }
    val modeEntryLaunchDecision = modeEntryStateDecision

    val search = chatSearch
    val searchMatches = if (search != null) rememberChatSearchMatches(search, messages) else null
    val searchLocation = search?.location
    val searchResultIndex = searchMatches?.messages?.indexOfFirst {
        it.messageId == searchLocation?.match?.messageId
    } ?: -1

    fun locateChatSearchMatch(match: AiChatSearchMatch, query: AiChatSearchQuery) {
        if (messages.none { it.id == match.messageId && !it.loading }) return
        searchFocusManager.clearFocus()
        searchKeyboard?.hide()
        chatSearch?.location = AiChatSearchLocation(match, query)
    }

    LaunchedEffect(searchLocation, searchMatches) {
        if (searchLocation != null && searchMatches != null && searchResultIndex < 0) {
            showChatSearchResults(edit = false)
        }
    }

    LaunchedEffect(
        activeSessionId,
        activeAgentMode.identity,
        modeEntryLaunchDecision,
        skillToolErrorText
    ) {
        when (modeEntryLaunchDecision) {
            AgentModeEntryLaunchDecision.NONE -> Unit
            AgentModeEntryLaunchDecision.SHOW_CONFIRMATION -> Unit
            AgentModeEntryLaunchDecision.START_AUTOMATICALLY -> startModeEntry()
            AgentModeEntryLaunchDecision.MISSING_REQUIRED_CONTEXT -> {
                modeEntryStarted = true
                messages += ChatUiMessage(
                    role = ChatRole.ASSISTANT,
                    content = "缺少当前功能所需的入口上下文，请从对应功能入口重新进入。",
                    meta = "error"
                )
                persistActiveSession()
            }
        }
    }

    val sessionSnapshots = sessions.toList()
    val currentMessageSnapshots = messages.toList()
    val skillPresentations = remember(sessionSnapshots, configVersion) {
        val generalSkills = AiSkillRegistry.agentSkills()
        val systemWorkflows = AgentModeRegistry.all().mapNotNull { mode ->
            mode.systemWorkflowId?.let(AiSkillRegistry::systemWorkflow)
        }
        (generalSkills + systemWorkflows).associate { skill ->
            skill.id to (skill.name to skill.presentation)
        }
    }
    val drawerHistoryGroups = remember(sessionSnapshots, skillPresentations) {
        buildDrawerHistoryGroups(sessionSnapshots, skillPresentations)
    }
    val drawerFavoriteItems = remember(
        sessionSnapshots,
        activeSessionId,
        currentMessageSnapshots,
        skillPresentations
    ) {
        buildDrawerFavoriteItems(
            sessionSnapshots,
            activeSessionId,
            currentMessageSnapshots,
            skillPresentations
        )
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
    val imageAttachmentActions = if (imageAttachmentsAvailable) {
        rememberAiImageAttachmentActions(
            targetId = activeSessionId,
            attachmentCount = chatSession.imageDraftAssets.size,
            onDismiss = imageAttachmentMenu::close,
            onAdd = { addImageAttachments(it) },
            onDirect = if (showImageActions) {
                {
                    if (ensureCodexImageRoute()) {
                        chatSession.setExplicitImageDraft(true)
                        persistActiveSession()
                    }
                }
            } else null,
            importingChanged = { imageImporting = it },
        )
    } else null
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = search == null || drawerState.isOpen,
        drawerContent = {
            RikkaChatDrawer(
                wallpaperBackdrop = wallpaperBackdrop,
                selectedIndex = selectedDrawerIndex,
                historyGroups = drawerHistoryGroups,
                historyLoaded = historyLoaded,
                activeSessionId = activeSessionId,
                favoriteItems = drawerFavoriteItems,
                historyManageMode = historyManageMode,
                selectedHistoryIds = selectedHistoryIds.toSet(),
                onSelect = { targetIndex ->
                    historyManageMode = false
                    selectedHistoryIds.clear()
                    when (targetIndex) {
                        1 -> ensureAllHistoryDetails {
                            selectedDrawerIndex = targetIndex
                            chatSearch = null
                            globalSearchMode = true
                            globalSearchQuery = ""
                            scope.launch { drawerState.close() }
                        }

                        3 -> ensureAllHistoryDetails {
                            selectedDrawerIndex = targetIndex
                        }

                        else -> selectedDrawerIndex = targetIndex
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
                    chatSearch = null
                    selectedDrawerIndex = 2
                    scope.launch { drawerState.close() }
                    ensureAllHistoryDetails()
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
            if (chatBackdrop == null && !globalSearchMode && !statsMode) {
                AiChatBackground(
                    drawable = chatBackgroundDrawable,
                    backdrop = wallpaperBackdrop,
                    showMask = true,
                )
            }
            if (globalSearchMode) {
                val searchBackdrop = wallpaperBackdrop ?: rememberNgLiquidGlassBackdrop()
                AiChatBackground(
                    drawable = chatBackgroundDrawable,
                    backdrop = searchBackdrop,
                    showMask = false,
                )
                NgLiquidGlassBackdropProvider(searchBackdrop) {
                    GlobalMessageSearchPage(
                        query = globalSearchQuery,
                        backdrop = wallpaperBackdrop,
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
                            pendingGlobalSearchResult = result
                            if (result.session != null && result.session.id != activeSessionId) {
                                openSession(result.session)
                            }
                            globalSearchMode = false
                            globalSearchQuery = ""
                            chatSearch = null
                            selectedDrawerIndex = 2
                        },
                    )
                }
            } else if (statsMode) {
                val statsBackdrop = wallpaperBackdrop ?: rememberNgLiquidGlassBackdrop()
                AiChatBackground(
                    drawable = chatBackgroundDrawable,
                    backdrop = statsBackdrop,
                    showMask = false,
                )
                val stats = if (historyDetailsLoaded) {
                    val result by produceState<AiChatStats?>(
                        null, sessionSnapshots, activeSessionId, currentMessageSnapshots
                    ) {
                        value = withContext(Dispatchers.Default) {
                            buildAiChatStats(sessionSnapshots, activeSessionId, currentMessageSnapshots)
                        }
                    }
                    result
                } else null
                NgLiquidGlassBackdropProvider(statsBackdrop) {
                    AiChatStatsPage(
                        stats = stats,
                        loading = historyDetailsLoading || (historyDetailsLoaded && stats == null),
                        onRetry = { ensureAllHistoryDetails() },
                        onBack = {
                            statsMode = false
                            selectedDrawerIndex = 2
                        }
                    )
                }
            } else {
                LaunchedEffect(pendingSearchJump, activeSessionId, openingSessionId) {
                    val result = pendingSearchJump ?: return@LaunchedEffect
                    if (openingSessionId != null && openingSessionId == result.sessionId) {
                        return@LaunchedEffect
                    }
                    if (activeSessionId == result.sessionId) {
                        val index = messages.indexOfFirst { it.id == result.messageId }
                        if (index >= 0) {
                            val entryCount = if (modeEntryConfirmation == null) 0 else 1
                            listState.requestScrollToItem(index + entryCount)
                        }
                    }
                    pendingGlobalSearchResult = null
                }
                val bookSearchNotice: (@Composable () -> Unit)? = if (
                    bookSearchMode && bookSearchAvailability != AiBookSearchAvailability.READY
                ) {
                    { AiBookSearchWebNotice(bookSearchAvailability) }
                } else null
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0),
                    topBar = {
                        if (search == null) {
                            AiChatToolbar(
                                title = currentTitle,
                                modelLabel = selectedModelLabel,
                                bookSearchMode = bookSearchMode,
                                 backdrop = controlsBackdrop,
                                onOpenMenu = { scope.launch { drawerState.open() } },
                                onOpenSearch = {
                                    chatSearch = AiChatConversationSearchState(
                                        entryIndex = listState.firstVisibleItemIndex,
                                        entryOffset = listState.firstVisibleItemScrollOffset,
                                    )
                                },
                                onNewChat = { startNewChat() },
                            )
                        } else {
                            AiChatConversationSearchBar(
                                search = search,
                                 backdrop = controlsBackdrop,
                                resultIndex = searchResultIndex,
                                resultCount = searchMatches?.messages?.size ?: 0,
                                onBack = ::backFromChatSearch,
                                onExit = ::exitChatSearch,
                                onEdit = { showChatSearchResults(edit = true) },
                                onPrevious = {
                                    searchMatches?.messages?.getOrNull(searchResultIndex - 1)?.let {
                                        locateChatSearchMatch(it, searchMatches.query)
                                    }
                                },
                                onNext = {
                                    searchMatches?.messages?.getOrNull(searchResultIndex + 1)?.let {
                                        locateChatSearchMatch(it, searchMatches.query)
                                    }
                                },
                            )
                        }
                    },
                    bottomBar = bottomBar@{
                        if (search != null) return@bottomBar
                        Column {
                            AnimatedVisibility(visible = exportSelectionMode) {
                                ExportSelectionFloatingBar(
                                    selectedCount = selectedExportMessageIds.size,
                                    selectionAvailable = messages.any { !it.loading },
                                     backdrop = controlsBackdrop,
                                    onCancel = ::cancelExportSelection,
                                    onSelectAll = ::selectAllExportMessages,
                                    onInvert = ::invertExportSelection,
                                    onConfirm = ::confirmExportSelection,
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
                                onValueChange = {
                                    input = it
                                    if (imageAttachmentsAvailable) chatSession.saveImageDraft()
                                    if (bookSearchMode) {
                                        bookSearchDraftEdited = true
                                        bookSearchGreetingPending = false
                                    }
                                },
                                onInputFocused = {
                                    if (bookSearchMode) bookSearchGreetingPending = false
                                },
                                 backdrop = controlsBackdrop,
                                 messageInputEnabled = sessionRestored && !chatSession.runningHostAction && modeEntryConfirmation == null &&
                                    (skillToolErrorText == null || directImageDraft) &&
                                    bookSearchAvailability == AiBookSearchAvailability.READY,
                                 sendEnabled = sessionRestored && !chatSession.runningHostAction && modeEntryConfirmation == null &&
                                    (skillToolErrorText == null || directImageDraft) && !imageImporting &&
                                    bookSearchAvailability == AiBookSearchAvailability.READY,
                                sending = sending || chatSession.activeBookLookupMessageId != null,
                                inputNotice = bookSearchNotice,
                                inputHint = if (bookSearchMode) stringResource(
                                    if (messages.isEmpty()) R.string.ai_book_search_hint else R.string.ai_book_search_follow_up
                                ) else null,
                                modelIconRes = selectedModelIconRes,
                                reasoningEnabled = selectedReasoningEnabled,
                                webAccess = webAccess,
                                mcpEnabled = internalMcpEnabled && enabledMcpCapabilityIds.isNotEmpty(),
                                skillEnabled = inputAttachments.any {
                                    it.type == AiChatInputAttachmentType.SKILL
                                },
                                mcpControlVisible = activeAgentMode.allowsManualMcpCapabilities || bookSearchMode,
                                skillControlVisible = skillInputPolicy.selectorVisible,
                                contextAvailable = availableContextAttachments.isNotEmpty()
                                        || inputAttachments.any {
                                    it.type == AiChatInputAttachmentType.CONTEXT
                                },
                                suggestionAvailable = skillSuggestions.isNotEmpty(),
                                suggestionExpanded = suggestionsExpanded,
                                contextUsage = contextUsage,
                                contextUsageWithTools = imageAttachmentsAvailable && sessionRestored,
                                imageAssets = chatSession.imageDraftAssets,
                                directImage = directImageDraft,
                                showDirectImageLabel = directImageDraft && chatSession.explicitImageDraft,
                                imageImporting = imageImporting,
                                imageAttachmentButton = if (showImageActions && imageAttachmentActions != null && sessionRestored) {
                                    {
                                        AiImageAttachmentMenuButton(
                                            actions = imageAttachmentActions,
                                            menu = imageAttachmentMenu,
                                            enabled = !imageImporting,
                                        )
                                    }
                                } else null,
                                onImagePreview = { file -> imagePreview = file to false },
                                onImageRemove = { assetId ->
                                    chatSession.replaceImageDraftAssets(chatSession.imageDraftAssets - assetId)
                                    persistActiveSession()
                                },
                                onClearDirectImage = {
                                    chatSession.setExplicitImageDraft(false)
                                    persistActiveSession()
                                },
                                attachments = inputAttachments,
                                skills = activeSkillAttachments,
                                skillRemovable = skillInputPolicy.removable,
                                skillErrorText = skillToolErrorText.takeUnless { directImageDraft },
                                onSend = ::sendMessage,
                                onStop = ::stopSending,
                                onWebSettingsClick = onOpenWebSettings,
                                onModelClick = {
                                    if (modelSheetLaunchGate.tryOpen(SystemClock.elapsedRealtime())) {
                                        showModelSheet = true
                                    }
                                },
                                onReasoningClick = {
                                    val model = AiAssistantConfigUi.selectedModel()
                                    when {
                                        model == null -> Toast.makeText(
                                            context,
                                            R.string.ai_assistant_reasoning_select_model_first,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        !model.assistantReasoning().hasControls -> Toast.makeText(
                                            context,
                                            R.string.ai_assistant_reasoning_unsupported,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        else -> showReasoningSheet = true
                                    }
                                },
                                onMcpClick = {
                                    if (activeAgentMode.allowsManualMcpCapabilities || bookSearchMode) {
                                        showInternalMcpSheet = true
                                    }
                                },
                                onSuggestionClick = {
                                    suggestionsExpanded = !suggestionsExpanded
                                },
                                onContextUsageClick = {
                                    showContextUsageDialog = true
                                },
                                onSkillClick = {
                                    if (skillInputPolicy.selectorVisible) {
                                        showSkillAttachmentSheet = true
                                    }
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
                                    chatSession.refreshContextUsage()
                                },
                                onRemoveSkill = { skill ->
                                    if (skillInputPolicy.removable) {
                                        inputAttachments.removeAll { it.id == skill.id }
                                        skillManagedCapabilityIds.clear()
                                        enabledMcpCapabilityIds.replaceWith(
                                            McpInternalToolCatalog.normalizeCapabilityIds(
                                                manualMcpCapabilityIds
                                            )
                                        )
                                        chatSession.refreshContextUsage()
                                        persistActiveSession()
                                    }
                                    }
                                )
                        }
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier.fillMaxSize().then(
                            controlsBackdrop?.let {
                                Modifier.ngRecordLiquidGlassBackdrop(it)
                            } ?: Modifier
                        )
                    ) {
                        if (chatBackdrop != null) {
                            AiChatBackground(
                                drawable = chatBackgroundDrawable,
                                backdrop = chatBackdrop,
                                showMask = false,
                            )
                        }
                        if (search != null && searchLocation == null) {
                            AiChatConversationSearchResults(
                                search = search,
                                results = searchMatches,
                                padding = padding,
                                backdrop = chatBackdrop,
                                messageModelProviders = messageModelProviders,
                                assistantAvatar = { modelIconRes ->
                                    RikkaAssistantAvatar(
                                        iconRes = modelIconRes,
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                onOpenMessage = ::locateChatSearchMatch,
                            )
                            return@Box
                        }
                        if (search != null && searchLocation != null) {
                            LaunchedEffect(searchLocation, modeEntryConfirmation?.id) {
                                fun targetIndex(): Int {
                                    val index = messages.indexOfFirst { it.id == searchLocation.match.messageId }
                                    return if (index < 0 || modeEntryConfirmation == null) index else index + 1
                                }
                                val index = targetIndex()
                                if (index < 0) return@LaunchedEffect
                                listState.scrollToItem(index)
                                search.hasLocatedMessage = true
                                val matchOffset = searchLocation.matchOffset.await()
                                val layout = listState.layoutInfo
                                val visibleHeight = layout.viewportSize.height -
                                    layout.beforeContentPadding - layout.afterContentPadding
                                if (matchOffset > visibleHeight * 0.58f) {
                                    val latestIndex = targetIndex()
                                    if (latestIndex >= 0) {
                                        listState.scrollToItem(latestIndex, matchOffset - visibleHeight / 3)
                                    }
                                }
                            }
                        }
                        val greetingSessionId = activeSessionId
                        RikkaChatList(
                            padding = padding,
                            backdrop = chatBackdrop,
                            state = listState,
                            messages = messages,
                            messageModelProviders = messageModelProviders,
                            entryInteraction = modeEntryConfirmation,
                            bookSearchMode = bookSearchMode,
                            bookSearchGuide = if (
                                bookSearchMode && bookSearchHistoryRestored && messages.isEmpty() && search == null
                            ) {
                                {
                                    AssistantMessageHeader(
                                        fontWeight = FontWeight.Normal,
                                        modelLabel = selectedModelLabel,
                                        modelIconRes = selectedModelIconRes,
                                    )
                                    key(greetingSessionId) {
                                        AiBookSearchGreetingText(bookSearchGreetingPending) {
                                            if (activeSessionId == greetingSessionId) bookSearchGreetingPending = false
                                        }
                                    }
                                }
                            } else null,
                            showBookSearchActions = bookSearchMode && !sending && !exportSelectionMode && search == null,
                            bookSearchMcpEnabled = internalMcpEnabled,
                            onBookSearchAction = ::handleBookSearchAction,
                            interactionPolicy = interactionPolicy,
                            searchLocation = searchLocation,
                            onRegenerate = ::regenerateMessage,
                            onDelete = ::deleteMessage,
                            onFork = ::forkMessage,
                            onToggleFavorite = ::toggleFavorite,
                            selectionMode = exportSelectionMode && search == null,
                            selectedIds = selectedExportMessageIds.toSet(),
                            onToggleSelection = { message ->
                                if (message.id in selectedExportMessageIds) {
                                    selectedExportMessageIds.remove(message.id)
                                } else {
                                    selectedExportMessageIds.add(message.id)
                                }
                            },
                            onExport = { message ->
                                chatSearch = null
                                startExportSelection(message)
                            },
                            onEntryInteractionSubmit = { action ->
                                val entryAction = action.selectedValues[action.interaction.id]
                                    ?: action.prompt.substringAfter("action=", "")
                                        .substringBefore(' ')
                                        .trim()
                                if (
                                    activeAgentMode.id == AgentModeRegistry.BOOK_SCAN_ID &&
                                    entryAction == "continue_scan"
                                ) {
                                    showBookScanContinueEntry(action)
                                } else {
                                    action.onCompleted(startModeEntry())
                                }
                            },
                            onInteractionSubmit = ::submitInteraction,
                            onImagePreview = { file, generated -> imagePreview = file to generated },
                            onImageEdit = if (showImageActions) ::continueEditingImage else null,
                        )
                        if (search == null) FloatingSkillSuggestionPanel(
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
                chatSession.contextCompactionCount,
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
                chatSession.refreshContextUsage()
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
    if (showSkillAttachmentSheet && skillInputPolicy.selectorVisible) {
        AiChatInputAttachmentSheet(
            title = "加载技能",
            emptyText = "暂无可在聊天中加载的技能",
            centeredHeader = true,
            availableAttachments = buildSkillPickerAttachments(),
            loadedAttachmentIds = inputAttachments.map { it.id }.toSet(),
            onAdd = { attachment ->
                requestSkillAttachment(attachment)
                showSkillAttachmentSheet = false
            },
            onDismiss = { showSkillAttachmentSheet = false }
        )
    }
    pendingSkillCapabilityAuthorization
        ?.takeIf { skillInputPolicy.selectorVisible }
        ?.let { attachment ->
        val requestedCapabilities = attachment.skillRuntime.mcpCapabilities.mapNotNull { id ->
            McpInternalToolCatalog.capability(id)
        }
        AlertDialog(
            onDismissRequest = {
                pendingSkillCapabilityAuthorization = null
                inputAttachments.removeAll { it.id == attachment.id }
            },
            title = { Text("授权技能能力") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("“${attachment.title}”申请以下 MCP 能力。授权只绑定当前 Skill 内容版本；编辑后需要重新授权。")
                    requestedCapabilities.forEach { capability ->
                        Text(
                            text = "${capability.title}：${capability.description}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        AiSkillCapabilityGrantStore.grant(
                            attachment.skillRuntimeRevision,
                            attachment.skillRuntime.mcpCapabilities
                        )
                        pendingSkillCapabilityAuthorization = null
                        activateSkillAttachment(attachment)
                    }
                ) {
                    Text("授权并加载")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingSkillCapabilityAuthorization = null
                    inputAttachments.removeAll { it.id == attachment.id }
                }) {
                    Text("取消")
                }
            }
        )
    }
    imagePreview?.let { (file, generated) ->
        AiImagePreview(
            file,
            onDismiss = { imagePreview = null },
            onEdit = if (generated && showImageActions) {
                { continueEditingImage(file) }
            } else null,
        )
    }
    if (showCodexImageSettings) {
        NgPaneDialog(onDismissRequest = { showCodexImageSettings = false }) {
            NgDialog(
                title = null,
                variant = NgDialogVariant.CLASSIC_CONFIRMATION,
                actions = {
                    NgDialogTextActionButton(
                        text = stringResource(R.string.cancel),
                        onClick = { showCodexImageSettings = false },
                    )
                    NgDialogTextActionButton(
                        text = stringResource(R.string.setting),
                        onClick = {
                            showCodexImageSettings = false
                            openAssistantSettings(AiConfigFragment.PAGE_PROVIDERS)
                        },
                    )
                },
            ) {
                Text(
                    text = stringResource(R.string.ai_image_codex_required),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                )
            }
        }
    }
    if (showInternalMcpSheet && (activeAgentMode.allowsManualMcpCapabilities || bookSearchMode)) {
        AiChatInternalMcpSheet(
            enabled = internalMcpEnabled,
            onEnabledChange = { enabled ->
                if (AiConfig.internalMcpEnabled != enabled) {
                    AiConfig.internalMcpEnabled = enabled
                    configVersion++
                }
            },
            modules = if (bookSearchMode) {
                McpInternalToolCatalog.modules.mapNotNull { module ->
                    val available = module.capabilities.filter { it.id in activeAgentMode.fixedMcpCapabilityIds }
                    available.takeIf { it.isNotEmpty() }?.let { module.copy(capabilities = it) }
                }
            } else McpInternalToolCatalog.modules,
            selectedCapabilityIds = enabledMcpCapabilityIds.toSet(),
            onSelectionChange = ::updateMcpCapabilitySelection,
            selectionEnabled = !bookSearchMode,
            onDismiss = { showInternalMcpSheet = false }
        )
    }
    bookScopeSelection?.let { (messageId, candidate) ->
        val media = candidate.media ?: return@let
        val sources by produceState(initialValue = emptyList<io.legado.app.data.entities.BookSourcePart>(), media) {
            value = withContext(Dispatchers.IO) { McpPreciseBookSearch.sourcesFor(media) }
        }
        val groups = remember(sources) {
            sources.flatMap { ChangeSourceFilter.splitGroups(it.bookSourceGroup) }.distinct().sorted()
        }
        var query by remember(media) { mutableStateOf("") }
        val filtered = remember(sources, query) {
            sources.filter { it.bookSourceName.contains(query, ignoreCase = true) }
        }
        SearchScopeDialog(
            groups = groups,
            sources = filtered,
            onSourceQueryChange = { query = it },
            onApply = { selected ->
                val entry = activeModeEntryContext ?: return@SearchScopeDialog
                activeModeEntryContext = entry.copy(payload = entry.payload.deepCopy().apply {
                    addProperty(AI_BOOK_SEARCH_SCOPE, selected.toString())
                    if (media.sourceType == BookSourceType.audio && !selected.isAll()) {
                        addProperty(AI_BOOK_SEARCH_AUDIO_SCOPE_MEDIA, media.name)
                    } else {
                        remove(AI_BOOK_SEARCH_AUDIO_SCOPE_MEDIA)
                    }
                })
                bookScopeSelection = null
                chatSession.launchBookSourceLookup(messageId, candidate)
            },
            onDismiss = { bookScopeSelection = null },
        )
    }
    generationBlock?.let { (providerId, issue) ->
        val needsStreaming = issue == AiModelGenerationIssue.STREAMING_REQUIRED
        NgPaneDialog(onDismissRequest = { generationBlock = null }) {
            NgDialog(
                title = null,
                variant = NgDialogVariant.CLASSIC_CONFIRMATION,
                actions = {
                    NgDialogTextActionButton(
                        text = stringResource(if (needsStreaming) R.string.cancel else R.string.close),
                        onClick = { generationBlock = null },
                    )
                    if (needsStreaming) {
                        NgDialogTextActionButton(
                            text = stringResource(R.string.ai_enable_streaming_response),
                            onClick = {
                                synchronized(AiProviderStore) {
                                    val selection = AiAssistantConfigUi.selectedModel()
                                    if (selection != null && selection.provider.id == providerId &&
                                        selection.provider.resolveModelGenerationRoute(selection.model).issue ==
                                        AiModelGenerationIssue.STREAMING_REQUIRED
                                    ) {
                                        AiProviderStore.saveProvider(selection.provider.copy(streamResponseEnabled = true))
                                        configVersion++
                                    }
                                }
                                generationBlock = null
                            },
                        )
                    }
                },
            ) {
                Text(
                    text = stringResource(
                        if (needsStreaming) R.string.ai_assistant_streaming_required
                        else R.string.ai_model_interface_unsupported,
                    ),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                )
            }
        }
    }
    if (showReasoningSheet) {
        val selectedModel = remember(configVersion) { AiAssistantConfigUi.selectedModel() }
        if (selectedModel != null) {
            AiChatReasoningSheet(
                model = selectedModel,
                onChanged = { configVersion++ },
                onDismiss = { showReasoningSheet = false },
            )
        }
    }
    if (showModelSheet) {
        AiChatModelSheet(
            onChanged = { configVersion++ },
            onDismiss = {
                showModelSheet = false
                modelSheetLaunchGate.release()
            },
        )
    }
}

@Composable
private fun AiChatToolApprovalDialog(activity: AiChatActivity?, pending: AiChatToolApproval?) {
    val presenter = remember { Any() }
    val approval = pending?.takeIf { it.presenter == null || it.presenter === presenter } ?: return
    if (activity == null || activity.isFinishing || activity.isDestroyed || approval.response.isCompleted) return
    DisposableEffect(activity, approval) {
        approval.presenter = presenter
        val summaries = approval.calls.map { it.toWriteOperationSummary() }
        val destructive = approval.calls.any { it.sideEffect == McpToolSideEffect.DESTRUCTIVE }
        val dialog = ComponentDialog(activity)
        fun resolve(confirmed: Boolean) {
            approval.response.complete(confirmed)
            dialog.dismiss()
        }
        val composeView = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    AiToolWriteConfirmationContent(
                        summaries = summaries,
                        destructive = destructive,
                        onCancel = { resolve(false) },
                        onConfirm = { resolve(true) },
                    )
                }
            }
        }
        dialog.setContentView(composeView)
        dialog.setOnCancelListener { resolve(false) }
        dialog.show()
        dialog.applyNgWindow(height = (activity.resources.displayMetrics.heightPixels * 0.62f).toInt())
        onDispose {
            dialog.setOnCancelListener(null)
            dialog.dismiss()
            if (approval.presenter === presenter) approval.presenter = null
        }
    }
}

@Composable
private fun AiChatStatsPage(
    stats: AiChatStats?,
    loading: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        NgFloatingTitleToolbar(
            title = "统计",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (stats != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "token_activity") {
                        AiTokenActivityCard(stats.tokenActivityPerDay)
                    }
                    item(key = "stats_grid") {
                        AiChatStatsGrid(stats)
                    }
                }
            } else if (loading) {
                AiStatsCardSurface(
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    NgDotsLoadingIndicator(Modifier.size(56.dp))
                }
            } else {
                AiStatsCardSurface(
                    modifier = Modifier.align(Alignment.Center),
                    onClick = onRetry,
                ) {
                    Text(
                        text = stringResource(R.string.load_error_retry),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun AiStatsCardSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cornerRadius = 18.dp
    val shape = RoundedCornerShape(cornerRadius)
    NgVisualSurface(
        modifier = if (onClick == null) modifier else modifier.clip(shape).clickable(onClick = onClick),
        role = NgMaterialRole.CONTENT,
        cornerRadius = cornerRadius,
        shape = shape,
        style = NgGlassDefaults.neutralStyle(containerAlpha = 0.98f),
        content = content,
    )
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
    AiStatsCardSurface(modifier = Modifier.fillMaxWidth()) {
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
        color = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
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
    val accentColor = Color(NgTheme.colors.primary)
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
                                    accentColor.copy(alpha = if (selected) 0.42f else 0.20f)
                                } else if (selected) {
                                    accentColor.copy(alpha = 0.92f)
                                } else {
                                    accentColor.copy(alpha = 0.58f)
                                }
                            )
                    )
                }
                Text(
                    text = bar.axisLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
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
                icon = Icons.Rounded.ArrowUpward,
                label = "输入 Token",
                value = formatAiStatTokens(stats.totalPromptTokens)
            )
            AiStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.ArrowDownward,
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
    AiStatsCardSurface(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(NgTheme.colors.primary),
                    modifier = Modifier.size(20.dp)
                )
            } else if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = Color(NgTheme.colors.primary),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiChatToolbar(
    title: String,
    modelLabel: String,
    bookSearchMode: Boolean,
    backdrop: NgLiquidGlassBackdrop?,
    onOpenMenu: () -> Unit,
    onOpenSearch: () -> Unit,
    onNewChat: () -> Unit,
) {
    val iconSize = if (backdrop != null) Modifier.size(20.dp) else Modifier
    val navigation = @Composable {
        IconButton(onClick = onOpenMenu) {
            Icon(Icons.Rounded.Menu, contentDescription = "Menu", modifier = iconSize)
        }
    }
    val titleContent = @Composable {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "默认助手 / $modelLabel",
                color = LocalContentColor.current.copy(alpha = 0.62f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (bookSearchMode) FontWeight.Normal else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    val actions: @Composable RowScope.() -> Unit = {
        IconButton(onClick = onOpenSearch) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = stringResource(R.string.ai_chat_find_hint),
                modifier = Modifier.size(24.dp),
            )
        }
        IconButton(onClick = onNewChat) {
            Icon(
                imageVector = if (backdrop != null) Icons.Rounded.Add else Icons.Rounded.AddComment,
                contentDescription = "New chat",
                modifier = iconSize,
            )
        }
    }

    if (backdrop == null) {
        Surface(color = MaterialTheme.colorScheme.background.copy(alpha = 0.86f)) {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = navigation,
                title = titleContent,
                actions = actions,
            )
        }
    } else {
        AiChatFloatingToolbarSurface(backdrop) {
            navigation()
            Box(Modifier.weight(1f).padding(end = 4.dp)) { titleContent() }
            actions()
        }
    }
}

@Composable
private fun BoxScope.AiChatBackground(
    drawable: Drawable?,
    backdrop: NgLiquidGlassBackdrop?,
    showMask: Boolean,
) {
    NgDrawerBackdropSource(Modifier.matchParentSize()) {
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(backdrop?.let { Modifier.ngRecordLiquidGlassBackdrop(it) } ?: Modifier)
                    .background(MaterialTheme.colorScheme.background),
            ) {
                NgThemeGradientBackground(modifier = Modifier.fillMaxSize())
                if (drawable != null) {
                    ChatBackgroundImage(drawable = drawable, modifier = Modifier.fillMaxSize())
                }
            }
            NgEffectThemeBackground(modifier = Modifier.fillMaxSize())
        }
    }
    if (showMask) AiChatBackgroundMask()
}

@Composable
private fun AiChatBackgroundMask() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.66f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.74f),
                    )
                )
            )
    )
}

@Composable
private fun GlobalMessageSearchPage(
    query: String,
    backdrop: NgLiquidGlassBackdrop?,
    onQueryChange: (String) -> Unit,
    sessions: List<AiChatSessionSnapshot>,
    activeSessionId: String?,
    currentMessages: List<ChatUiMessage>,
    onBack: () -> Unit,
    onOpenResult: (GlobalMessageSearchResult) -> Unit,
) {
    var submittedQuery by remember { mutableStateOf("") }
    val keyword = remember(submittedQuery) {
        submittedQuery.takeIf { it.isNotEmpty() }?.let(::AiChatSearchQuery)
    }
    val context = LocalContext.current.applicationContext
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val completedSearch by produceState<Pair<AiChatSearchQuery, List<GlobalMessageSearchResult>>?>(
        null, keyword, sessions, activeSessionId, currentMessages, context,
    ) {
        if (keyword == null) {
            value = null
            return@produceState
        }
        val markdown = aiChatSearchMarkdown(context)
        snapshotFlow { sessions.toList() to currentMessages.toList() }
            .collectLatest { (savedSessions, messages) ->
                val matches = withContext(Dispatchers.Default) {
                    buildGlobalMessageSearchResults(keyword, savedSessions, activeSessionId, messages, markdown)
                }
                value = keyword to matches
            }
    }
    val results = completedSearch?.takeIf { it.first === keyword }?.second

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
    ) {
        NgFloatingSearchToolbar(
            query = query,
            onQueryChange = {
                onQueryChange(it)
                if (it.isBlank()) submittedQuery = ""
            },
            hint = stringResource(R.string.ai_chat_global_search_hint),
            onBack = onBack,
            onSearch = {
                submittedQuery = it.trim()
                focusManager.clearFocus()
                keyboard?.hide()
            },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (keyword != null && results != null) {
            if (results.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        contentColor = colorResource(R.color.ng_on_surface),
                    ) {
                        Text(
                            text = stringResource(R.string.ai_chat_find_empty),
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = results,
                        key = { "${it.sessionId.orEmpty()}:${it.messageId}:${it.messageIndex}" },
                    ) { result ->
                        GlobalSearchResultRow(
                            result = result,
                            query = keyword,
                            backdrop = backdrop,
                            onClick = { onOpenResult(result) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalSearchResultRow(
    result: GlobalMessageSearchResult,
    query: AiChatSearchQuery,
    backdrop: NgLiquidGlassBackdrop?,
    onClick: () -> Unit,
) {
    val highlight = aiChatSearchHighlightStyle()
    val snippet = remember(result.snippet, query, highlight) {
        query.highlight(AnnotatedString(result.snippet), highlight)
    }
    val time = remember(result.updatedAt) { formatChatSearchTime(result.updatedAt) }
    AiChatSearchResultSurface(backdrop = backdrop, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = result.sessionTitle.ifBlank { "新聊天" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val role = if (result.isUser) "用户" else "AI"
                Text(
                    text = "$role · $time",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun RikkaChatDrawer(
    wallpaperBackdrop: NgLiquidGlassBackdrop?,
    selectedIndex: Int,
    historyGroups: List<DrawerHistoryGroup>,
    historyLoaded: Boolean,
    activeSessionId: String?,
    favoriteItems: List<DrawerFavoriteItem>,
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
    ChatDrawerSurface(wallpaperBackdrop) {
        val standardStyle = useStandardChatDrawerStyle
        val drawerContentColor = drawerPrimaryContentColor()
        CompositionLocalProvider(LocalContentColor provides drawerContentColor) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(
                        horizontal = if (standardStyle) 13.dp else 6.dp,
                        vertical = if (standardStyle) 12.dp else 8.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(if (standardStyle) 4.dp else 6.dp),
            ) {
                if (!standardStyle) Spacer(Modifier.height(8.dp))
                DrawerActionRow(
                    icon = Icons.Rounded.Search,
                    standardIconRes = R.drawable.ic_search,
                    label = "搜索聊天",
                    selected = selectedIndex == 1,
                    onClick = { onSelect(1) },
                )
                DrawerActionRow(
                    icon = Icons.Rounded.History,
                    standardIconRes = R.drawable.ic_mingcute_time_line,
                    label = "聊天历史",
                    selected = selectedIndex == 2,
                    onClick = { onSelect(2) },
                )
                DrawerActionRow(
                    icon = Icons.Rounded.Favorite,
                    standardIconRes = R.drawable.ic_star,
                    label = "收藏内容",
                    selected = selectedIndex == 3,
                    onClick = { onSelect(3) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(
                        horizontal = 6.dp,
                        vertical = if (standardStyle) 8.dp else 6.dp,
                    ),
                    color = if (standardStyle) drawerDividerColor() else drawerContentColor.copy(alpha = 0.35f),
                )
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedIndex) {
                        3 -> DrawerFavoriteContent(
                            favoriteItems = favoriteItems,
                            onSessionSelect = onSessionSelect,
                            onPinFavorite = onPinFavorite,
                            onDeleteFavorite = onDeleteFavorite,
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
                            onNewChat = onNewChat,
                        )
                    }
                }
                DrawerBottomActions(
                    onOpenStats = onOpenStats,
                    onOpenSettings = onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun ChatDrawerSurface(
    wallpaperBackdrop: NgLiquidGlassBackdrop?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val drawerWidth = LocalConfiguration.current.screenWidthDp.dp * AI_CHAT_DRAWER_WIDTH_RATIO
    val surface: @Composable () -> Unit = {
        NgSideDrawerSurface(
            modifier = Modifier.fillMaxHeight().width(drawerWidth),
            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
            contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
            role = if (wallpaperBackdrop != null) NgMaterialRole.CONTROL else NgMaterialRole.SOFT_SURFACE,
            content = content,
        )
    }
    if (wallpaperBackdrop != null) {
        NgLiquidGlassBackdropProvider(wallpaperBackdrop, content = surface)
    } else {
        surface()
    }
}

@Composable
private fun DrawerBottomActions(
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val showLabels = useStandardChatDrawerStyle
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (showLabels) 6.dp else 10.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showLabels) HorizontalDivider(color = drawerDividerColor())
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (showLabels) Arrangement.spacedBy(7.dp) else Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DrawerBottomActionButton(
                label = "统计",
                showLabel = showLabels,
                painter = painterResource(R.drawable.ic_solar_chart_bold),
                onClick = onOpenStats,
                modifier = if (showLabels) Modifier.weight(1f) else Modifier,
            )
            if (!showLabels) Spacer(Modifier.width(14.dp))
            DrawerBottomActionButton(
                label = "设置",
                showLabel = showLabels,
                painter = if (showLabels) painterResource(R.drawable.ic_book_info_menu_settings)
                    else rememberVectorPainter(Icons.Rounded.Settings),
                onClick = onOpenSettings,
                modifier = if (showLabels) Modifier.weight(1f) else Modifier,
            )
        }
    }
}

@Composable
private fun DrawerBottomActionButton(
    label: String,
    showLabel: Boolean,
    painter: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (showLabel) drawerSecondaryContentColor() else drawerPrimaryContentColor()
    Surface(
        onClick = onClick,
        shape = if (showLabel) RoundedCornerShape(12.dp) else CircleShape,
        color = if (showLabel) MaterialTheme.colorScheme.surface.copy(alpha = 0.52f)
            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
        modifier = if (showLabel) modifier.heightIn(min = 40.dp) else modifier.size(48.dp),
    ) {
        Row(
            modifier = if (showLabel) Modifier.padding(horizontal = 7.dp, vertical = 6.dp) else Modifier,
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painter,
                contentDescription = if (showLabel) null else label,
                modifier = Modifier.size(if (showLabel) 22.dp else 24.dp),
                tint = contentColor,
            )
            if (showLabel) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 13.sp,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val useStandardChatDrawerStyle: Boolean
    @Composable get() = !NgTheme.snapshot.isEInk

@Composable
private fun drawerPrimaryContentColor(): Color = ngDrawerPrimaryTextColor(
    Color(if (useStandardChatDrawerStyle) NgTheme.colors.onSurface else NgTheme.colors.primary),
)

@Composable
private fun drawerSecondaryContentColor(): Color = ngDrawerSecondaryTextColor(
    if (useStandardChatDrawerStyle) Color(NgTheme.colors.onSurfaceVariant)
    else Color(NgTheme.colors.primary).copy(alpha = 0.78f),
)

@Composable
private fun drawerDividerColor(): Color = drawerSecondaryContentColor().copy(alpha = 0.20f)

@Composable
private fun drawerAccentContentColor(enabled: Boolean = true): Color {
    val color = Color(NgTheme.colors.primary)
    return if (enabled) color else color.copy(alpha = 0.45f)
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
    val sectionTitle = if (useStandardChatDrawerStyle) "会话" else "聊天历史"
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
                        text = if (manageMode) "$sectionTitle · 已选 ${selectedIds.size}" else sectionTitle,
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
                    color = drawerSecondaryContentColor(),
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
    val standardStyle = useStandardChatDrawerStyle
    val primaryContentColor = drawerPrimaryContentColor()
    val secondaryContentColor = drawerSecondaryContentColor()
    Surface(
        onClick = onToggle,
        color = if (standardStyle) Color.Transparent else MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(
                start = if (standardStyle) 10.dp else 8.dp,
                top = if (standardStyle) 4.dp else 5.dp,
                end = 2.dp,
                bottom = if (standardStyle) 4.dp else 5.dp,
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) {
                    Icons.Rounded.KeyboardArrowDown
                } else {
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight
                },
                contentDescription = null,
                modifier = Modifier.size(if (standardStyle) 18.dp else 20.dp),
                tint = primaryContentColor
            )
            if (standardStyle) Spacer(Modifier.width(6.dp))
            Text(
                text = group.title,
                modifier = Modifier.weight(1f),
                color = primaryContentColor,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = group.items.size.toString(),
                color = secondaryContentColor,
                style = MaterialTheme.typography.bodySmall
            )
            if (standardStyle) Spacer(Modifier.width(4.dp))
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
    val textColor = drawerAccentContentColor(enabled)
    val standardStyle = useStandardChatDrawerStyle
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (standardStyle) 9.dp else 6.dp, vertical = 3.dp),
        color = textColor,
        fontWeight = if (standardStyle) FontWeight.Medium else FontWeight.SemiBold,
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
                    color = drawerSecondaryContentColor(),
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
                                color = if (useStandardChatDrawerStyle) drawerPrimaryContentColor()
                                    else drawerAccentContentColor(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.preview,
                                style = MaterialTheme.typography.bodySmall,
                                color = drawerSecondaryContentColor(),
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
    val standardStyle = useStandardChatDrawerStyle
    Text(
        text = text,
        color = if (standardStyle) drawerSecondaryContentColor() else drawerAccentContentColor(),
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier.padding(horizontal = if (standardStyle) 11.dp else 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun DrawerActionRow(
    icon: ImageVector,
    @DrawableRes standardIconRes: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val standardStyle = useStandardChatDrawerStyle
    val contentColor = if (standardStyle && selected) drawerAccentContentColor() else drawerPrimaryContentColor()
    val selectedColor = if (standardStyle) drawerAccentContentColor().copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    Surface(
        onClick = onClick,
        color = if (selected) selectedColor else Color.Transparent,
        shape = RoundedCornerShape(if (standardStyle) 12.dp else 18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (standardStyle) 8.dp else 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = if (standardStyle) painterResource(standardIconRes) else rememberVectorPainter(icon),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (standardStyle && !selected) drawerSecondaryContentColor() else contentColor,
            )
            Text(
                text = label,
                style = if (standardStyle) MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp)
                    else MaterialTheme.typography.bodyMedium,
                fontWeight = if (standardStyle && selected) FontWeight.SemiBold else null,
                color = contentColor,
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
    val primaryContentColor = drawerPrimaryContentColor()
    val secondaryContentColor = drawerSecondaryContentColor()
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
                NgCircularSelectionIndicator(
                    state = if (checked) ToggleableState.On else ToggleableState.Off,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(32.dp)
                        .toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = { onClick() },
                        ),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = primaryContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryContentColor,
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
    val standardStyle = useStandardChatDrawerStyle
    val contentColor = if (standardStyle) drawerSecondaryContentColor() else drawerPrimaryContentColor()
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (standardStyle) Icons.Rounded.MoreVert else Icons.Rounded.MoreHoriz,
                contentDescription = "更多",
                modifier = Modifier.size(20.dp),
                tint = contentColor
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
    val standardStyle = useStandardChatDrawerStyle
    val contentColor = if (standardStyle) drawerSecondaryContentColor() else drawerPrimaryContentColor()
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(if (standardStyle) 36.dp else 30.dp)
        ) {
            Icon(
                imageVector = if (standardStyle) Icons.Rounded.MoreVert else Icons.Rounded.MoreHoriz,
                contentDescription = "分组操作",
                modifier = Modifier.size(18.dp),
                tint = contentColor
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
    backdrop: NgLiquidGlassBackdrop?,
    state: LazyListState,
    messages: List<ChatUiMessage>,
    messageModelProviders: Map<String, AiProviderSetting>,
    entryInteraction: AiChatInteraction?,
    bookSearchMode: Boolean,
    bookSearchGuide: (@Composable () -> Unit)?,
    showBookSearchActions: Boolean,
    bookSearchMcpEnabled: Boolean,
    onBookSearchAction: (String, AiBookSearchAction) -> Unit,
    interactionPolicy: AiChatInteractionPolicyContract?,
    searchLocation: AiChatSearchLocation?,
    onRegenerate: (ChatUiMessage) -> Unit,
    onDelete: (ChatUiMessage) -> Unit,
    onFork: (ChatUiMessage) -> Unit,
    onToggleFavorite: (ChatUiMessage) -> Unit,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelection: (ChatUiMessage) -> Unit,
    onExport: (ChatUiMessage) -> Unit,
    onEntryInteractionSubmit: (AiChatInteractionAction) -> Unit,
    onInteractionSubmit: (String, AiChatInteractionAction) -> Unit,
    onImagePreview: (File, Boolean) -> Unit,
    onImageEdit: ((File) -> Unit)?,
) {
    val horizontalPadding = if (backdrop == null) 16.dp else AiChatHorizontalPadding
    val selectionStartReduction = if (selectionMode) 8.dp else 0.dp
    // Keep scrolling messages inside the rounded outer edges of the floating controls.
    val viewportInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)
        .add(
            WindowInsets(
                top = AiChatToolbarTopPadding + AiChatToolbarCornerRadius,
                bottom = if (searchLocation == null) {
                    AiChatComposerBottomPadding + AiChatComposerCornerRadius
                } else 0.dp,
            ),
        )
    val listPadding = if (backdrop == null) {
        PaddingValues(
            start = horizontalPadding - selectionStartReduction,
            top = padding.calculateTopPadding() + 10.dp,
            end = horizontalPadding,
            bottom = padding.calculateBottomPadding() + 18.dp
        )
    } else {
        val viewportPadding = viewportInsets.asPaddingValues()
        PaddingValues(
            start = horizontalPadding - selectionStartReduction,
            top = (padding.calculateTopPadding() - viewportPadding.calculateTopPadding())
                .coerceAtLeast(0.dp) + 12.dp,
            end = horizontalPadding,
            bottom = (padding.calculateBottomPadding() - viewportPadding.calculateBottomPadding())
                .coerceAtLeast(0.dp) + 12.dp,
        )
    }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().then(
            when {
                backdrop != null -> Modifier.windowInsetsPadding(viewportInsets)
                searchLocation != null -> Modifier.navigationBarsPadding().imePadding()
                else -> Modifier
            }
        ),
        contentPadding = listPadding,
        verticalArrangement = Arrangement.spacedBy(if (backdrop == null) 16.dp else 12.dp),
    ) {
        if (bookSearchGuide != null) {
            item(key = "book-search-guide") {
                AiChatMessageSurface(isUser = false, backdrop = backdrop, actionsMenu = null) {
                    bookSearchGuide()
                }
            }
        }
        entryInteraction?.let { interaction ->
            item(key = "mode-entry-${interaction.id}") {
                Box(Modifier.padding(start = selectionStartReduction)) {
                    ChatInteractionBlock(
                        interaction = interaction,
                        resolvedLabel = null,
                        onSubmit = onEntryInteractionSubmit
                    )
                }
            }
        }
        items(messages, key = { it.id }) { message ->
            val messageLocation = searchLocation?.takeIf { it.match.messageId == message.id }
            val renderMessage: @Composable () -> Unit = {
                RikkaMessageItem(
                    message = message,
                    backdrop = backdrop,
                    messageModelProviders = messageModelProviders,
                    assistantLabelWeight = if (bookSearchMode) FontWeight.Normal else FontWeight.SemiBold,
                    interactionPolicy = interactionPolicy,
                    searchLocation = messageLocation,
                    onRegenerate = { onRegenerate(message) },
                    onDelete = { onDelete(message) },
                    onFork = { onFork(message) },
                    onToggleFavorite = { onToggleFavorite(message) },
                    onExport = { onExport(message) },
                    onInteractionSubmit = onInteractionSubmit,
                    onImagePreview = onImagePreview,
                    onImageEdit = onImageEdit,
                )
            }
            val messageContent: @Composable () -> Unit = {
                val bookSearch = message.bookSearch
                if (showBookSearchActions && bookSearch != null) {
                    Column {
                        renderMessage()
                        AiBookSearchReplyContent(bookSearch, actionsEnabled = true, mcpEnabled = bookSearchMcpEnabled) {
                            onBookSearchAction(message.id, it)
                        }
                    }
                } else {
                    renderMessage()
                }
            }
            if (selectionMode && !message.loading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    ExportMessageCheckbox(
                        checked = message.id in selectedIds,
                        onToggle = { onToggleSelection(message) },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        messageContent()
                    }
                }
            } else if (messageLocation != null) {
                Box(Modifier.fillMaxWidth().onGloballyPositioned(messageLocation::recordMessage)) {
                    messageContent()
                }
            } else if (selectionMode) {
                Box(Modifier.padding(start = selectionStartReduction)) { messageContent() }
            } else {
                messageContent()
            }
        }
    }
}

@Composable
private fun ExportMessageCheckbox(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NgCircularSelectionIndicator(
        state = if (checked) ToggleableState.On else ToggleableState.Off,
        modifier = modifier
            .size(48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = "选择消息" },
    )
}

private data class ChatSearchSource(
    val id: String,
    val isUser: Boolean,
    val content: String,
    val providerId: String? = null,
    val modelId: String? = null,
)

@Composable
private fun rememberChatSearchMatches(
    search: AiChatConversationSearchState,
    messages: List<ChatUiMessage>,
): AiChatSearchMatches? {
    val context = LocalContext.current.applicationContext
    val matches by produceState<AiChatSearchMatches?>(null, search, context) {
        val parsedMessages = mutableMapOf<ChatSearchSource, List<Pair<AiChatSearchPart, String>>>()
        val markdown = aiChatSearchMarkdown(context)
        snapshotFlow {
            val query = search.query.trim()
            val sources = if (query.isEmpty()) emptyList() else messages.mapNotNull { message ->
                if (message.loading || message.contextCompaction != null || message.content.isBlank()) {
                    null
                } else {
                    ChatSearchSource(
                        message.id, message.role == ChatRole.USER, message.content,
                        message.usageProviderId, message.usageModelId,
                    )
                }
            }
            query to sources
        }.collectLatest { (query, sources) ->
            value = if (query.isEmpty()) null else withContext(Dispatchers.Default) {
                parsedMessages.keys.retainAll(sources.toSet())
                val keyword = AiChatSearchQuery(query)
                val results = sources.mapNotNull { source ->
                    ensureActive()
                    val parts = parsedMessages.getOrPut(source) { source.searchableParts(markdown) }
                    parts.firstNotNullOfOrNull { (part, text) ->
                        val match = keyword.findIn(text) ?: return@firstNotNullOfOrNull null
                        AiChatSearchMatch(
                            messageId = source.id,
                            isUser = source.isUser,
                            part = part,
                            excerpt = keyword.excerpt(text, match.range),
                            providerId = source.providerId,
                            modelId = source.modelId,
                        )
                    }
                }
                AiChatSearchMatches(keyword, results)
            }
        }
    }
    return matches?.takeIf { it.query.text == search.query.trim() }
}

// Render the same text as the message view without binding images to a View.
internal fun aiChatSearchMarkdown(context: Context): Markwon = Markwon.builder(context)
    .usePlugin(HtmlPlugin.create())
    .usePlugin(TablePlugin.create(context))
    .build()

internal fun AiChatInteractionRender.displayContent(original: String): String =
    if (interactions.isNotEmpty()) content else content.ifBlank { original }

private fun ChatSearchSource.searchableParts(markdown: Markwon): List<Pair<AiChatSearchPart, String>> {
    if (isUser) return listOf(AiChatSearchPart(0) to content)
    val display = AiChatInteractionParser.parse(content).displayContent(content)
    fun plain(text: String): String = markdown.toMarkdown(normalizeAiChatMarkdown(text)).toString()
    return buildList {
        splitMarkdownBlocks(display).forEachIndexed { blockIndex, block ->
            val part = AiChatSearchPart(blockIndex)
            when (block) {
                is ChatMarkdownBlock.Text -> add(part to plain(block.value))
                is ChatMarkdownBlock.Code -> {
                    add(part to block.language.ifBlank { "text" })
                    add(part.copy(index = 1) to block.code)
                }
                is ChatMarkdownBlock.Callout -> {
                    add(part to block.callout.displayTitle())
                    add(part.copy(index = 1) to plain(block.callout.body))
                }
                is ChatMarkdownBlock.Table -> {
                    val columns = block.table.headers.size
                    (listOf(block.table.headers) + block.table.rows).forEachIndexed { rowIndex, row ->
                        row.take(columns).forEachIndexed { columnIndex, cell ->
                            add(part.copy(index = rowIndex * columns + columnIndex) to cell.tablePlainText())
                        }
                    }
                }
                is ChatMarkdownBlock.Image -> Unit
            }
        }
    }
}

@Composable
private fun RikkaMessageItem(
    message: ChatUiMessage,
    backdrop: NgLiquidGlassBackdrop?,
    messageModelProviders: Map<String, AiProviderSetting>,
    assistantLabelWeight: FontWeight,
    interactionPolicy: AiChatInteractionPolicyContract?,
    searchLocation: AiChatSearchLocation?,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    onFork: () -> Unit,
    onToggleFavorite: () -> Unit,
    onExport: () -> Unit,
    onInteractionSubmit: (String, AiChatInteractionAction) -> Unit,
    onImagePreview: (File, Boolean) -> Unit,
    onImageEdit: ((File) -> Unit)?,
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
    val hasUserActions = isUser && when (message.deliveryState) {
        ChatDeliveryState.SENT, ChatDeliveryState.RECOVERABLE, ChatDeliveryState.FAILED -> true
        ChatDeliveryState.QUEUED, ChatDeliveryState.IN_FLIGHT -> false
    }
    var confirmAction by remember { mutableStateOf<PendingMessageAction?>(null) }
    val actionsMenu: (@Composable (() -> Unit) -> Unit)? =
        if (hasUserActions && backdrop != null) {
            { onDismissRequest ->
                ChatMessageActionsMenu(
                    message = message,
                    onDismissRequest = onDismissRequest,
                    onCopy = { clipboard.setText(AnnotatedString(message.content)) },
                    onRegenerate = { confirmAction = PendingMessageAction.REGENERATE },
                    onExport = onExport,
                    onFork = onFork,
                    onToggleFavorite = onToggleFavorite,
                    onDelete = { confirmAction = PendingMessageAction.DELETE },
                )
            }
        } else {
            null
        }
    AiChatMessageSurface(isUser, backdrop, actionsMenu) {
        if (isUser) {
            AiImageThumbnailRow(message.imageAssetIds, onPreview = { onImagePreview(it, false) })
            val body = remember(message.content) { AnnotatedString(message.content) }
            if (message.content.isNotBlank() && backdrop != null) {
                SearchableChatText(
                    text = body,
                    modifier = Modifier.align(Alignment.Start),
                    style = MaterialTheme.typography.bodyLarge,
                    searchLocation = searchLocation,
                )
            } else if (message.content.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.widthIn(max = 320.dp)
                ) {
                    SearchableChatText(
                        text = body,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        searchLocation = searchLocation,
                    )
                }
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
            if (hasUserActions && backdrop == null) {
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
            val model = remember(message.usageProviderId, message.usageModelId, messageModelProviders) {
                resolveChatMessageModel(message.usageProviderId, message.usageModelId, messageModelProviders)
            }
            AssistantMessageHeader(
                fontWeight = assistantLabelWeight,
                modelLabel = model.label ?: stringResource(R.string.ai_assistant),
                modelIconRes = model.iconRes,
            )
            val generatedImages = remember(message.imageAssetIds, message.toolReceipts) {
                (message.imageAssetIds.mapNotNull(AiImageAssets::file) + message.toolReceipts
                    .filter { it.isCodexImage() }
                    .map { codexImageFile(it.conversationId, it.receiptId) }).distinct()
            }
            generatedImages.forEach { file ->
                key(file) {
                    AiChatImageResult(
                        file,
                        onPreview = { onImagePreview(file, true) },
                        onEdit = onImageEdit?.let { edit -> { edit(file) } },
                    )
                }
            }
            if (message.loading) {
                val wait = message.imageWait
                if (wait != null) AiImageWaitCard(wait)
                else if (generatedImages.isEmpty()) ThinkingLoadingLine(message.webActivity?.activeAction)
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
                val interactionPolicyResult = remember(interactionRender, interactionPolicy) {
                    AiChatInteractionPolicyRegistry.filter(
                        contract = interactionPolicy,
                        interactions = interactionRender.interactions
                    )
                }
                if (!message.reasoning.isNullOrBlank()) {
                    ReasoningEntry(
                        reasoning = message.reasoning,
                        elapsedMs = message.elapsedMs
                    )
                }
                val displayContent = interactionRender.displayContent(message.content)
                if (displayContent.isNotBlank()) {
                    val markdown = remember(displayContent, message.webActivity, searchLocation) {
                        message.webActivity?.takeIf { searchLocation == null }?.withInlineLinks(displayContent)
                            ?: displayContent
                    }
                    MarkdownMessageText(
                        content = markdown,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                            .padding(top = 2.dp),
                        searchLocation = searchLocation,
                    )
                }
                interactionPolicyResult.accepted.forEach { interaction ->
                    ChatInteractionBlock(
                        interaction = interaction,
                        resolvedLabel = message.interactionResultLabels[interaction.id],
                        resolvedSelections = message.interactionResultSelections[interaction.id].orEmpty(),
                        onSubmit = { action -> onInteractionSubmit(message.id, action) }
                    )
                }
                if (interactionPolicyResult.rejected.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
                        border = BorderStroke(
                            0.8.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                        )
                    ) {
                        Text(
                            text = "已阻止不符合当前工作流规则的交互，请重新生成本条回复。",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                message.webActivity?.let { WebSources(it, message.usageProviderId) }
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
                if (!message.meta.isNullOrBlank() && message.meta != "error") {
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
internal fun AiChatMessageSurface(
    isUser: Boolean,
    backdrop: NgLiquidGlassBackdrop?,
    actionsMenu: (@Composable (() -> Unit) -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val alignment = if (isUser) Alignment.End else Alignment.Start
    if (backdrop == null) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = alignment,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
        return
    }

    var menuExpanded by remember(actionsMenu != null) { mutableStateOf(false) }
    val bubbleGesture = if (actionsMenu != null) {
        val hapticFeedback = LocalHapticFeedback.current
        Modifier
            .pointerInput(hapticFeedback) {
                detectTapGestures(onLongPress = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuExpanded = true
                })
            }
            .semantics(mergeDescendants = true) {
                onLongClick(label = "消息操作") {
                    menuExpanded = true
                    true
                }
            }
    } else {
        Modifier
    }
    val corner = 20.dp
    val shape = RoundedCornerShape(
        topStart = corner,
        topEnd = corner,
        bottomEnd = if (isUser) 6.dp else corner,
        bottomStart = if (isUser) corner else 6.dp,
    )
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.TopEnd else Alignment.TopStart,
    ) {
        NgVisualSurface(
            modifier = if (isUser) {
                Modifier
                    .padding(start = 32.dp)
                    .widthIn(max = 320.dp)
                    .then(bubbleGesture)
            } else {
                Modifier.padding(end = 8.dp).fillMaxWidth()
            },
            role = NgMaterialRole.CONTENT,
            cornerRadius = corner,
            shape = shape,
            style = NgGlassDefaults.neutralStyle(containerAlpha = 0.94f)
                .copy(contentColor = MaterialTheme.colorScheme.onSurface),
            contentPadding = if (isUser) {
                PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            } else {
                PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            },
            liquidBackdrop = backdrop,
        ) {
            Column(
                horizontalAlignment = alignment,
                verticalArrangement = Arrangement.spacedBy(if (isUser) 2.dp else 4.dp),
                content = content,
            )
        }
        if (menuExpanded && actionsMenu != null) {
            Box(Modifier.align(Alignment.BottomEnd)) {
                actionsMenu { menuExpanded = false }
            }
        }
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
internal fun aiChatContentCardColor(): Color = if (NgTheme.snapshot.isDark) {
    Color(NgTheme.colors.surfaceContainerHigh)
} else {
    colorResource(R.color.ng_surface_card)
}

@Composable
private fun ChatInteractionBlock(
    interaction: AiChatInteraction,
    resolvedLabel: String?,
    resolvedSelections: Map<String, String> = emptyMap(),
    onSubmit: (AiChatInteractionAction) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = aiChatContentCardColor(),
        contentColor = colorResource(R.color.ng_on_surface),
        border = BorderStroke(
            width = 0.8.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.68f)
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
            if (resolvedLabel != null) {
                ResolvedInteractionContent(
                    interaction = interaction,
                    resolvedLabel = resolvedLabel,
                    resolvedSelections = resolvedSelections
                )
            } else BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val minContentWidth = maxWidth
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (interaction.type) {
                        AiChatInteractionType.POLICY_REF -> Unit

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

                        AiChatInteractionType.MULTI_TAG_STANCE -> InteractionMultiTagStance(
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
    onSubmit: (AiChatInteractionAction) -> Unit
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
                                AiChatInteractionAction(
                                    interaction = interaction,
                                    displayText = option.label,
                                    prompt = AiChatInteractionParser.buildPrompt(
                                        interaction = interaction,
                                        option = option,
                                        submit = interaction.submit
                                    ),
                                    selectedValues = mapOf(interaction.id to option.value)
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
    onSubmit: (AiChatInteractionAction) -> Unit
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
                        AiChatInteractionAction(
                            interaction = interaction,
                            displayText = option.label,
                            prompt = AiChatInteractionParser.buildPrompt(
                                interaction = interaction,
                                option = option,
                                submit = interaction.submit
                            )
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
    onSubmit: (AiChatInteractionAction) -> Unit
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
        Row(
            modifier = Modifier.widthIn(min = minWidth),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            interaction.skip?.let { skip ->
                InteractionChip(
                    text = skip.label.ifBlank { "跳过" },
                    primary = false,
                    modifier = Modifier.padding(end = 8.dp),
                    onClick = {
                        onSubmit(
                            AiChatInteractionAction(
                                interaction = interaction,
                                displayText = skip.label.ifBlank { "已跳过" },
                                prompt = AiChatInteractionParser.buildPrompt(
                                    interaction = interaction,
                                    submit = skip
                                ),
                                skipped = true
                            )
                        )
                    }
                )
            }
            InteractionChip(
                text = interaction.submit?.label ?: "确认",
                primary = true,
                enabled = selected.isNotEmpty(),
                onClick = {
                    onSubmit(
                        AiChatInteractionAction(
                            interaction = interaction,
                            displayText = selected.joinToString("、") { it.label },
                            prompt = AiChatInteractionParser.buildPrompt(
                                interaction = interaction,
                                options = selected,
                                submit = interaction.submit
                            )
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun InteractionMultiTagStance(
    interaction: AiChatInteraction,
    minWidth: Dp,
    onSubmit: (AiChatInteractionAction) -> Unit
) {
    var selectedValues by remember(interaction.id) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }
    var submitting by remember(interaction.id) { mutableStateOf(false) }
    fun dispatch(action: AiChatInteractionAction) {
        if (submitting) return
        submitting = true
        onSubmit(
            action.copy(
                onCompleted = { _ -> submitting = false }
            )
        )
    }
    Column(
        modifier = Modifier.widthIn(min = minWidth),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        interaction.items.forEachIndexed { index, item ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    interaction.options.forEach { option ->
                        InteractionOptionRow(
                            option = option,
                            minWidth = minWidth,
                            selected = selectedValues[item.id] == option.value,
                            multiple = false,
                            onClick = {
                                if (!submitting) {
                                    selectedValues = selectedValues + (item.id to option.value)
                                }
                            }
                        )
                    }
                }
            }
            if (index < interaction.items.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            }
        }
        Row(
            modifier = Modifier.widthIn(min = minWidth),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            interaction.skip?.let { skip ->
                InteractionChip(
                    text = skip.label.ifBlank { "暂时跳过" },
                    primary = false,
                    enabled = !submitting,
                    modifier = Modifier.padding(end = 8.dp),
                    onClick = {
                        dispatch(
                            AiChatInteractionAction(
                                interaction = interaction,
                                displayText = skip.label.ifBlank { "已暂时跳过" },
                                prompt = "",
                                skipped = true
                            )
                        )
                    }
                )
            }
            val selectedSummary = interaction.items.mapNotNull { item ->
                val value = selectedValues[item.id] ?: return@mapNotNull null
                val option = interaction.options.firstOrNull { it.value == value }
                    ?: return@mapNotNull null
                "${item.label}＝${option.label}"
            }.joinToString("；")
            InteractionChip(
                text = interaction.submit?.label ?: "保存并更新推荐",
                primary = true,
                enabled = selectedValues.isNotEmpty() && !submitting,
                onClick = {
                    dispatch(
                        AiChatInteractionAction(
                            interaction = interaction,
                            displayText = selectedSummary,
                            prompt = "",
                            selectedValues = selectedValues
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun ResolvedInteractionContent(
    interaction: AiChatInteraction,
    resolvedLabel: String,
    resolvedSelections: Map<String, String>
) {
    val selections = resolveInteractionSelections(interaction, resolvedSelections)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "✓ ${resolvedLabel.ifBlank { "已处理" }}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
        selections.forEachIndexed { index, item ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = item.itemLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RadioButton(selected = true, onClick = null)
                    Text(
                        text = item.optionLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            if (index < selections.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            }
        }
    }
}

internal data class ResolvedInteractionSelection(
    val itemId: String,
    val itemLabel: String,
    val description: String,
    val optionValue: String,
    val optionLabel: String
)

internal fun resolveInteractionSelections(
    interaction: AiChatInteraction,
    selectedValues: Map<String, String>
): List<ResolvedInteractionSelection> {
    val optionsByValue = interaction.options.associateBy { it.value }
    return interaction.items.mapNotNull { item ->
        val optionValue = selectedValues[item.id] ?: return@mapNotNull null
        val option = optionsByValue[optionValue] ?: return@mapNotNull null
        ResolvedInteractionSelection(
            itemId = item.id,
            itemLabel = item.label,
            description = item.description,
            optionValue = option.value,
            optionLabel = option.label
        )
    }
}

@Composable
private fun InteractionConfirm(
    interaction: AiChatInteraction,
    minWidth: Dp,
    onSubmit: (AiChatInteractionAction) -> Unit
) {
    if (interaction.submit != null || interaction.cancel != null) {
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
                            AiChatInteractionAction(
                                interaction = interaction,
                                displayText = cancel.label.ifBlank { "取消" },
                                prompt = AiChatInteractionParser.buildPrompt(
                                    interaction = interaction,
                                    submit = cancel
                                )
                            )
                        )
                    }
                )
            }
            interaction.submit?.let { submit ->
                InteractionChip(
                    text = submit.label,
                    primary = true,
                    onClick = {
                        onSubmit(
                            AiChatInteractionAction(
                                interaction = interaction,
                                displayText = submit.label,
                                prompt = AiChatInteractionParser.buildPrompt(
                                    interaction = interaction,
                                    submit = submit
                                )
                            )
                        )
                    }
                )
            }
        }
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
        Column(modifier = Modifier.weight(1f)) {
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
internal fun InteractionChip(
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
private fun ThinkingLoadingLine(webAction: AiWebActivityAction?) {
    Row(
        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = when (webAction) {
                AiWebActivityAction.SEARCH -> stringResource(R.string.ai_chat_web_searching)
                AiWebActivityAction.READ -> stringResource(R.string.ai_chat_web_reading)
                AiWebActivityAction.IMAGE_SEARCH -> stringResource(R.string.ai_chat_web_searching_images)
                null -> "Thinking"
            },
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
private fun AssistantMessageHeader(
    fontWeight: FontWeight,
    modelLabel: String,
    @DrawableRes modelIconRes: Int,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RikkaAssistantAvatar(
            iconRes = modelIconRes,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = modelLabel,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = fontWeight,
            color = colorResource(R.color.ng_on_surface),
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
            color = colorResource(R.color.ng_surface_card),
            contentColor = colorResource(R.color.ng_on_surface)
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
    textSizeSp: Float = 16f,
    searchLocation: AiChatSearchLocation? = null,
) {
    val blocks = remember(content) { splitMarkdownBlocks(content) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is ChatMarkdownBlock.Text -> MarkdownTextBlock(
                    content = block.value,
                    textSizeSp = textSizeSp,
                    searchLocation = searchLocation,
                    searchPart = AiChatSearchPart(index),
                )

                is ChatMarkdownBlock.Code -> MarkdownCodeBlock(
                    language = block.language,
                    code = block.code,
                    searchLocation = searchLocation,
                    blockIndex = index,
                )

                is ChatMarkdownBlock.Image -> MarkdownImageBlock(
                    alt = block.alt,
                    url = block.url
                )

                is ChatMarkdownBlock.Table -> MarkdownTableBlock(
                    table = block.table,
                    searchLocation = searchLocation,
                    blockIndex = index,
                )

                is ChatMarkdownBlock.Callout -> MarkdownCalloutBlock(
                    callout = block.callout,
                    searchLocation = searchLocation,
                    blockIndex = index,
                )
            }
        }
    }
}

private class ChatSearchTextAnchor(private val location: AiChatSearchLocation) {
    private var coordinates: LayoutCoordinates? = null
    private var lineTop: Float? = null

    fun placed(value: LayoutCoordinates) {
        coordinates = value
        lineTop?.let { location.recordMatch(value, it) }
    }

    fun setLineTop(value: Float) {
        lineTop = value
        coordinates?.let { location.recordMatch(it, value) }
    }
}

@Composable
private fun rememberChatSearchTextAnchor(
    location: AiChatSearchLocation?,
    part: AiChatSearchPart,
): ChatSearchTextAnchor? {
    return if (location != null && location.match.part == part) {
        remember(location) { ChatSearchTextAnchor(location) }
    } else null
}

@Composable
private fun SearchableChatText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    searchLocation: AiChatSearchLocation? = null,
    searchPart: AiChatSearchPart = AiChatSearchPart(0),
    onMatchLeft: ((Float) -> Unit)? = null,
) {
    val anchor = rememberChatSearchTextAnchor(searchLocation, searchPart)
    val query = searchLocation?.query
    val highlightedText = if (query != null) {
        val matchStyle = aiChatSearchHighlightStyle()
        remember(text, query, matchStyle) { query.highlight(text, matchStyle) }
    } else text
    val firstMatch = if (anchor != null && query != null) {
        remember(text, query) { query.findIn(text.text)?.range?.first }
    } else null
    Text(
        text = highlightedText,
        modifier = modifier.then(
            if (anchor == null) Modifier else Modifier.onGloballyPositioned(anchor::placed),
        ),
        color = color,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { layout ->
            if (anchor != null && firstMatch != null) {
                val line = layout.getLineForOffset(firstMatch)
                anchor.setLineTop(layout.getLineTop(line))
                onMatchLeft?.invoke(layout.getBoundingBox(firstMatch).left)
            }
        },
    )
}

private fun MarkdownCallout.displayTitle(): String {
    if (hasExplicitTitle) return title
    val warning = type in setOf(MarkdownCalloutType.WARNING, MarkdownCalloutType.CAUTION)
    return "${if (warning) "⚠️" else "ℹ️"} $title"
}

@Composable
private fun MarkdownCalloutBlock(
    callout: MarkdownCallout,
    modifier: Modifier = Modifier,
    searchLocation: AiChatSearchLocation? = null,
    blockIndex: Int = 0,
) {
    val isDark = NgTheme.snapshot.isDark
    val (accent, container) = when (callout.type) {
        MarkdownCalloutType.NOTE -> if (isDark) {
            Color(0xFF9EC9FF) to Color(0xFF1C3043)
        } else Color(0xFF35618D) to Color(0xFFDCEBFA)
        MarkdownCalloutType.TIP -> if (isDark) {
            Color(0xFFA5D6A7) to Color(0xFF1D342A)
        } else Color(0xFF2E7D32) to Color(0xFFE8F5E9)
        MarkdownCalloutType.IMPORTANT -> if (isDark) {
            Color(0xFFD1B3FF) to Color(0xFF30233F)
        } else Color(0xFF6B4EA0) to Color(0xFFEDE3F7)
        MarkdownCalloutType.WARNING -> if (isDark) {
            Color(0xFFFFC17A) to Color(0xFF3B2B1F)
        } else Color(0xFFD26A00) to Color(0xFFFFEEDB)
        MarkdownCalloutType.CAUTION -> if (isDark) {
            Color(0xFFFFA8A1) to Color(0xFF412827)
        } else Color(0xFFB3261E) to Color(0xFFF9DEDC)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = container,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.58f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SearchableChatText(
                text = AnnotatedString(callout.displayTitle()),
                color = accent,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                searchLocation = searchLocation,
                searchPart = AiChatSearchPart(blockIndex),
            )
            if (callout.body.isNotBlank()) {
                MarkdownTextBlock(
                    content = callout.body,
                    searchLocation = searchLocation,
                    searchPart = AiChatSearchPart(blockIndex, 1),
                )
            }
        }
    }
}

@Composable
private fun MarkdownTextBlock(
    content: String,
    modifier: Modifier = Modifier,
    textSizeSp: Float = 16f,
    searchLocation: AiChatSearchLocation? = null,
    searchPart: AiChatSearchPart = AiChatSearchPart(0),
) {
    val context = LocalContext.current
    val normalizedContent = remember(content) { normalizeAiChatMarkdown(content) }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(NgInterfaceFontMarkwonPlugin(context))
            .usePlugin(GlideImagesPlugin.create(Glide.with(context)))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .build()
    }
    val anchor = rememberChatSearchTextAnchor(searchLocation, searchPart)
    val query = searchLocation?.query
    val highlighted = if (query != null) {
        val style = aiChatSearchHighlightStyle()
        remember(markwon, normalizedContent, query, style) {
            SpannableString(markwon.toMarkdown(normalizedContent)).apply {
                query.findAllIn(this).forEach { match ->
                    val start = match.range.first
                    val end = match.range.last + 1
                    setSpan(BackgroundColorSpan(style.background.toArgb()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(ForegroundColorSpan(style.color.toArgb()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
    } else null
    val firstMatch = if (anchor != null && query != null && highlighted != null) {
        remember(highlighted, query) { query.findIn(highlighted)?.range?.first }
    } else null
    AndroidView(
        modifier = modifier.then(
            if (anchor == null) Modifier else Modifier.onGloballyPositioned(anchor::placed),
        ),
        factory = { viewContext ->
            android.widget.TextView(viewContext).apply {
                NgThemeRuntimeAssets.applyAppTypeface(context, this)
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
            if (highlighted == null) {
                markwon.setMarkdown(textView, normalizedContent)
            } else {
                markwon.setParsedMarkdown(textView, highlighted)
                if (anchor != null && firstMatch != null) {
                    textView.doOnLayout {
                        textView.layout?.let { layout ->
                            val line = layout.getLineForOffset(firstMatch)
                            anchor.setLineTop((textView.totalPaddingTop + layout.getLineTop(line)).toFloat())
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun MarkdownCodeBlock(
    language: String,
    code: String,
    modifier: Modifier = Modifier,
    searchLocation: AiChatSearchLocation? = null,
    blockIndex: Int = 0,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val codeScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
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
                SearchableChatText(
                    text = AnnotatedString(language.ifBlank { "text" }),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    searchLocation = searchLocation,
                    searchPart = AiChatSearchPart(blockIndex),
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
                    .horizontalScroll(codeScroll)
                    .padding(12.dp)
            ) {
                SearchableChatText(
                    text = AnnotatedString(code),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = NgTheme.fontFamily),
                    color = MaterialTheme.colorScheme.onSurface,
                    searchLocation = searchLocation,
                    searchPart = AiChatSearchPart(blockIndex, 1),
                    onMatchLeft = { left ->
                        scope.launch { codeScroll.scrollTo(left.roundToInt().coerceAtLeast(0)) }
                    },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalGlideComposeApi::class)
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
        GlideImage(
            model = url,
            contentDescription = alt,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp, max = 320.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit,
        ) { request ->
            request.error(R.drawable.image_loading_error)
        }
    }
}

@Composable
private fun MarkdownTableBlock(
    table: MarkdownTable,
    modifier: Modifier = Modifier,
    searchLocation: AiChatSearchLocation? = null,
    blockIndex: Int = 0,
) {
    val columns = table.headers.size
    if (columns == 0) return
    val rows = listOf(table.headers) + table.rows
    val widths = remember(table) { table.columnWidths() }
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
    val headerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    val rowColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.24f)
    val alternateRowColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
    val tableScroll = rememberScrollState()
    val target = searchLocation?.match?.part?.takeIf { it.block == blockIndex }
    if (target != null) {
        val density = LocalDensity.current
        LaunchedEffect(searchLocation, widths, density) {
            val offset = with(density) { widths.take(target.index % columns).sumOf { it.roundToPx() } }
            tableScroll.scrollTo(offset)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(tableScroll)
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(rowColor)
        ) {
            rows.forEachIndexed { rowIndex, row ->
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    repeat(columns) { columnIndex ->
                        val text = row.getOrNull(columnIndex).orEmpty()
                        val isHeader = rowIndex == 0
                        Box(
                            modifier = Modifier
                                .width(widths[columnIndex])
                                .fillMaxHeight()
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
                            SearchableChatText(
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
                                searchLocation = searchLocation,
                                searchPart = AiChatSearchPart(blockIndex, rowIndex * columns + columnIndex),
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
        val language = match.groupValues[1].trim()
        val code = match.groupValues[2].trimEnd()
        if (language.equals("legado-book-report", ignoreCase = true)) {
            appendTextTablesAndImages(renderLegadoBookReportMarkdown(code), blocks)
        } else {
            blocks += ChatMarkdownBlock.Code(
                language = language,
                code = code
            )
        }
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
        val callout = parseMarkdownCallout(lines, index)
        val table = if (callout == null) parseMarkdownTable(lines, index) else null
        if (callout != null) {
            appendInlineTextAndImages(buffer.toString(), blocks)
            buffer.clear()
            blocks += ChatMarkdownBlock.Callout(callout.callout)
            index = callout.nextIndex
        } else if (table != null) {
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
    data class Callout(val callout: MarkdownCallout) : ChatMarkdownBlock
}

internal enum class MarkdownCalloutType {
    NOTE,
    TIP,
    IMPORTANT,
    WARNING,
    CAUTION
}

internal data class MarkdownCallout(
    val type: MarkdownCalloutType,
    val title: String,
    val body: String,
    val hasExplicitTitle: Boolean
)

internal data class MarkdownCalloutParseResult(
    val callout: MarkdownCallout,
    val nextIndex: Int
)

private val markdownCalloutStartRegex = Regex(
    """^\s*>\s*\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]\s*(.*)$""",
    RegexOption.IGNORE_CASE
)

internal fun parseMarkdownCallout(
    lines: List<String>,
    startIndex: Int
): MarkdownCalloutParseResult? {
    val match = lines.getOrNull(startIndex)?.let(markdownCalloutStartRegex::matchEntire)
        ?: return null
    val type = runCatching {
        MarkdownCalloutType.valueOf(match.groupValues[1].uppercase(Locale.ROOT))
    }.getOrNull() ?: return null
    val explicitTitle = match.groupValues[2].trim()
    val title = explicitTitle.ifBlank {
        when (type) {
            MarkdownCalloutType.NOTE -> "提示"
            MarkdownCalloutType.TIP -> "建议"
            MarkdownCalloutType.IMPORTANT -> "重要"
            MarkdownCalloutType.WARNING -> "警告"
            MarkdownCalloutType.CAUTION -> "注意"
        }
    }
    val body = mutableListOf<String>()
    var index = startIndex + 1
    while (index < lines.size) {
        val line = lines[index]
        if (!line.trimStart().startsWith('>')) break
        body += line.trimStart().removePrefix(">").removePrefix(" ")
        index++
    }
    return MarkdownCalloutParseResult(
        callout = MarkdownCallout(
            type = type,
            title = title,
            body = body.joinToString("\n").trim(),
            hasExplicitTitle = explicitTitle.isNotBlank()
        ),
        nextIndex = index
    )
}

private data class MarkdownTable(
    val headers: List<String>,
    val rows: List<List<String>>
)

private data class MarkdownTableParseResult(
    val table: MarkdownTable,
    val nextIndex: Int
)

internal fun characterCardPreviewRow(content: String): List<String>? {
    val table = splitMarkdownBlocks(content).filterIsInstance<ChatMarkdownBlock.Table>()
        .singleOrNull { it.table.headers.firstOrNull()?.trim('*', '`') in setOf("角色名称", "角色名", "姓名") }
        ?.table ?: return null
    val headers = table.headers.map { it.trim('*', '`', ' ') }
    val columns = listOf(
        setOf("角色名称", "角色名", "姓名"), setOf("性别"), setOf("角色定位", "标签"),
        setOf("别称", "别名"), setOf("人物介绍", "简介"), setOf("生图提示词", "立绘提示词"),
    ).map { names -> headers.indexOfFirst { it in names } }
    val row = table.rows.singleOrNull()?.takeIf { it.size == headers.size } ?: return null
    if (columns.any { it < 0 }) return null
    return columns.map(row::get)
}

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

internal fun renderLegadoBookReportMarkdown(payload: String): String {
    val trimmed = payload.trim()
    return if (trimmed.startsWith("<")) {
        renderLegadoBookReportXmlMarkdown(trimmed)
    } else {
        renderLegadoBookReportJsonMarkdown(trimmed)
    }
}

private fun renderLegadoBookReportJsonMarkdown(json: String): String {
    val root = runCatching {
        JsonParser.parseString(json.trim()).takeIf { it.isJsonObject }?.asJsonObject
    }.getOrNull() ?: return "报告解析失败：模型返回的报告格式不完整。"
    if (!root.stringValue("type").isSupportedBookReportType()) {
        return "报告解析失败：不支持的扫书报告类型。"
    }
    val verdict = bookReportVerdict(root.stringValue("verdict"))
    val headline = root.stringValue("headline").bookReportDisplayHeadline()
    val basis = root.objectOrNull("basis")
    val book = basis?.stringValue("book").orEmpty()
    val author = basis?.stringValue("author").orEmpty()
    val status = basis?.stringValue("status").orEmpty()
    val wordCount = basis?.stringValue("word_count").orEmpty()
    val category = basis?.stringValue("category").orEmpty()
    val sampled = basis?.arrayOrNull("sampled").stringList()
    val overview = root.arrayOrNull("overview").stringList()
    val audience = root.arrayOrNull("audience").stringList()
    val readerFeeling = root.stringValue("reader_feeling")
    val pressureIndex = root.objectOrNull("intensity")?.stringValue("pressure_index").orEmpty()
    val pressureLabel = root.objectOrNull("intensity")?.stringValue("pressure_label").orEmpty()
    val pressureText = root.objectOrNull("intensity")?.stringValue("pressure_text").orEmpty()
    val risks = root.arrayOrNull("confirmed_risks").objectList()
    val unknowns = root.arrayOrNull("unknowns").stringList()
    val subjectiveReview = root.stringValue("subjective_review")
        .ifBlank { root.stringValue("subjective-review") }

    return buildString {
        appendLine("## 适读结论")
        appendLine()
        appendLine("> [!${verdict.callout}] 🎯 当前判断")
        appendLine("> **${verdict.label}**${headline.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()}")
        appendLine()
        if (book.isNotBlank() || author.isNotBlank() || status.isNotBlank() || sampled.isNotEmpty()) {
            appendLine("## 基础信息")
            appendLine()
            if (book.isNotBlank() || author.isNotBlank()) {
                appendLine("- **书籍**：${listOf(book, author).filter { it.isNotBlank() }.joinToString("｜")}")
            }
            if (status.isNotBlank()) {
                appendLine("- **状态**：$status")
            }
            if (wordCount.isNotBlank() || category.isNotBlank()) {
                appendLine("- **篇幅/分类**：${listOf(wordCount, category).filter { it.isNotBlank() }.joinToString("｜")}")
            }
            if (sampled.isNotEmpty()) {
                appendLine("- **覆盖**：${sampled.joinToString("、")}")
            }
            appendLine()
        }
        if (overview.isNotEmpty()) {
            appendLine("## 作品速览")
            appendLine()
            overview.take(4).forEach { appendLine("- $it") }
            appendLine()
        }
        if (
            readerFeeling.isNotBlank() ||
            pressureIndex.isNotBlank() ||
            pressureLabel.isNotBlank() ||
            pressureText.isNotBlank()
        ) {
            appendLine("## 阅读感受")
            appendLine()
            if (pressureIndex.isNotBlank() || pressureLabel.isNotBlank()) {
                appendLine("**压抑指数**：${formatPressureRating(pressureIndex, pressureLabel)}")
                appendLine()
            }
            if (pressureText.isNotBlank()) {
                appendLine("- **压力来源**：$pressureText")
            }
            if (readerFeeling.isNotBlank()) {
                appendLine(readerFeeling)
            }
            appendLine()
        }
        if (audience.isNotEmpty()) {
            appendLine("## 作品受众")
            appendLine()
            audience.take(4).forEach { appendLine("- $it") }
            appendLine()
        }
        if (risks.isNotEmpty()) {
            appendLine("## 重点避坑")
            appendLine()
            risks.forEach { item ->
                val level = item.stringValue("level")
                val callout = if (level == "critical" || level == "high") "CAUTION" else "WARNING"
                val title = item.stringValue("title").ifBlank { "风险" }
                val text = item.stringValue("text")
                appendLine("> [!$callout] $title")
                if (text.isNotBlank()) {
                    text.lines().forEach { line -> appendLine("> $line") }
                }
                appendLine()
            }
        }
        appendBookScanBoundary(unknowns)
        if (subjectiveReview.isNotBlank()) {
            appendLine()
            appendBookScanSubjectiveReview(subjectiveReview)
        }
    }.trim().normalizeBookScanUserFacingText()
}

private fun renderLegadoBookReportXmlMarkdown(xml: String): String {
    val root = xml.xmlFirstBlock("book-report") ?: xml
    val type = xml.xmlAttribute("type").ifBlank { root.xmlAttribute("type") }
    if (type.isNotBlank() && !type.isSupportedBookReportType()) {
        return "报告解析失败：不支持的扫书报告类型。"
    }
    val verdict = bookReportVerdict(
        root.xmlFirstTag("verdict").ifBlank { root.xmlAttribute("verdict") }
    )
    val headline = root.xmlFirstTag("headline").bookReportDisplayHeadline()
    val basic = root.xmlFirstBlock("basic").orEmpty()
    val book = basic.xmlFirstTag("book")
    val author = basic.xmlFirstTag("author")
    val status = basic.xmlFirstTag("status")
    val wordCount = basic.xmlFirstTag("word-count").ifBlank { basic.xmlFirstTag("word_count") }
    val category = basic.xmlFirstTag("category")
    val sampled = basic.xmlFirstTag("sampled")
    val positioning = basic.xmlFirstTag("positioning")
    val overview = root.xmlSectionItems("overview")
    val audience = root.xmlSectionItems("audience")
    val feeling = root.xmlFirstBlock("reading-feeling")
        ?: root.xmlFirstBlock("reading_feeling")
        ?: root.xmlFirstTag("reading-feeling").ifBlank { root.xmlFirstTag("reader-feeling") }
    val pressure = feeling.xmlBlocks("pressure").firstOrNull().orEmpty()
    val pressureIndex = pressure.xmlAttribute("index")
    val pressureLabel = pressure.xmlAttribute("label")
    val pressureText = pressure.xmlTextBody()
    val feelingItems = feeling.xmlBlocks("item").map { block ->
        val title = block.xmlFirstTag("title").ifBlank { block.xmlAttribute("title") }
        val text = block.xmlFirstTag("text").ifBlank { block.xmlTextBody() }
        listOf(title, text).filter { it.isNotBlank() }.joinToString("：")
    }.filter { it.isNotBlank() }
    val risks = root.xmlBlocks("risk").map { block ->
        XmlReportItem(
            title = block.xmlFirstTag("title")
                .ifBlank { block.xmlAttribute("title") }
                .ifBlank { block.xmlAttribute("label") },
            text = block.xmlFirstTag("text").ifBlank { block.xmlTextBody() },
            level = block.xmlAttribute("level")
                .ifBlank { block.xmlAttribute("severity").toBookReportRiskLevel() }
                .ifBlank { "medium" }
        )
    }.filter { it.title.isNotBlank() || it.text.isNotBlank() }
    val unknowns = root.xmlBlocks("unknown").map { it.xmlTextBody() }
        .ifEmpty { root.xmlSectionItems("unknowns") }
    val subjectiveReview = root.xmlFirstTag("subjective-review")
        .ifBlank { root.xmlFirstTag("subjective_review") }

    return buildString {
        appendLine("## 适读结论")
        appendLine()
        appendLine("> [!${verdict.callout}] 🎯 当前判断")
        appendLine("> **${verdict.label}**${headline.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()}")
        appendLine()
        if (
            book.isNotBlank() || author.isNotBlank() || status.isNotBlank() ||
            wordCount.isNotBlank() || category.isNotBlank() || sampled.isNotBlank() ||
            positioning.isNotBlank()
        ) {
            appendLine("## 基础信息")
            appendLine()
            if (book.isNotBlank() || author.isNotBlank()) {
                appendLine("- **书籍**：${listOf(book, author).filter { it.isNotBlank() }.joinToString("｜")}")
            }
            if (status.isNotBlank()) appendLine("- **状态**：$status")
            if (wordCount.isNotBlank() || category.isNotBlank()) {
                appendLine("- **篇幅/分类**：${listOf(wordCount, category).filter { it.isNotBlank() }.joinToString("｜")}")
            }
            if (sampled.isNotBlank()) appendLine("- **扫描**：$sampled")
            if (positioning.isNotBlank()) appendLine("- **一句话定位**：$positioning")
            appendLine()
        }
        if (overview.isNotEmpty()) {
            appendLine("## 作品速览")
            appendLine()
            overview.take(6).forEach { appendLine("- $it") }
            appendLine()
        }
        if (pressureIndex.isNotBlank() || pressureLabel.isNotBlank() || pressureText.isNotBlank() || feelingItems.isNotEmpty()) {
            appendLine("## 阅读感受")
            appendLine()
            if (pressureIndex.isNotBlank() || pressureLabel.isNotBlank()) {
                appendLine("**压抑指数**：${formatPressureRating(pressureIndex, pressureLabel)}")
                appendLine()
            }
            if (pressureText.isNotBlank()) appendLine("- **压力来源**：$pressureText")
            feelingItems.take(4).forEach { appendLine("- $it") }
            appendLine()
        }
        if (audience.isNotEmpty()) {
            appendLine("## 作品受众")
            appendLine()
            audience.take(4).forEach { appendLine("- $it") }
            appendLine()
        }
        if (risks.isNotEmpty()) {
            appendLine("## 重点避坑")
            appendLine()
            risks.forEach { item ->
                val callout = if (item.level == "critical" || item.level == "high") "CAUTION" else "WARNING"
                appendLine("> [!$callout] ${item.title.ifBlank { "风险" }}")
                item.text.lines().filter { it.isNotBlank() }.forEach { line -> appendLine("> $line") }
                appendLine()
            }
        }
        appendBookScanBoundary(unknowns)
        if (subjectiveReview.isNotBlank()) {
            appendLine()
            appendBookScanSubjectiveReview(subjectiveReview)
        }
    }.trim().normalizeBookScanUserFacingText()
}

private fun StringBuilder.appendBookScanBoundary(unknowns: List<String>) {
    appendLine("> [!WARNING] 🔎 扫描边界：还有没扫完的地方")
    appendLine("> 这份结论只对当前已经读取或快速浏览过的章节负责；没扫到不等于没有隐藏雷点。")
    if (unknowns.isNotEmpty()) {
        unknowns.take(4).forEach { item ->
            item.lines()
                .filter { it.isNotBlank() }
                .forEach { line -> appendLine("> $line") }
        }
    } else {
        appendLine("> 当前只是快速定位，未完整覆盖全书。")
    }
}

private fun StringBuilder.appendBookScanSubjectiveReview(subjectiveReview: String) {
    appendLine("## 主观锐评")
    appendLine()
    appendLine("> [!WARNING] 老书虫吐槽")
    subjectiveReview.lines()
        .filter { it.isNotBlank() }
        .forEach { line -> appendLine("> $line") }
}

private data class BookReportVerdict(
    val label: String,
    val callout: String
)

private fun bookReportVerdict(value: String): BookReportVerdict {
    return when (value) {
        "try" -> BookReportVerdict(label = "可以试读", callout = "TIP")
        "cautious" -> BookReportVerdict(label = "谨慎试读", callout = "WARNING")
        "reject" -> BookReportVerdict(label = "目前不推荐", callout = "CAUTION")
        "uncertain" -> BookReportVerdict(label = "信息不足", callout = "NOTE")
        else -> BookReportVerdict(label = "初步判断", callout = "IMPORTANT")
    }
}

private fun String.isSupportedBookReportType(): Boolean {
    return this == "quick_scan_report" || this == "continue_scan_report"
}

private fun String.bookReportDisplayHeadline(): String {
    var cleaned = trim()
    while (cleaned.isNotEmpty()) {
        val next = BOOK_REPORT_HEADLINE_VERDICT_PREFIX_REGEX.replace(cleaned, "").trim()
        if (next == cleaned) break
        cleaned = next
    }
    return cleaned
}

internal fun String.normalizeBookScanUserFacingText(): String {
    return replace(BOOK_SCAN_INTERNAL_CHAPTER_RANGE_REGEX) { match ->
        "第${match.groupValues[1]}—${match.groupValues[2]}章"
    }.replace(BOOK_SCAN_INTERNAL_SINGLE_CHAPTER_REGEX) { match ->
        "第${match.groupValues[1]}章"
    }.replace(BOOK_SCAN_SNIPPET_COVERAGE_REGEX, "许多章节只快速浏览了首尾")
        .replace(BOOK_SCAN_SNIPPET_NAVIGATION_REGEX, "快速浏览章节首尾")
        .replace(BOOK_SCAN_MANIFEST_WORD_REGEX, "扫描记录")
        .replace(BOOK_SCAN_BUDGET_WORD_REGEX, "本轮检查")
}

private val BOOK_REPORT_HEADLINE_VERDICT_PREFIX_REGEX = Regex(
    """^(?:可以试读|谨慎试读|目前不推荐|信息不足|初步判断)\s*[：:，,。；;、\-—]\s*"""
)

private val BOOK_SCAN_INTERNAL_CHAPTER_RANGE_REGEX = Regex(
    """(?i)\bch(?:apter)?\s*(\d+)\s*[-~～—至]\s*(\d+)\b"""
)
private val BOOK_SCAN_INTERNAL_SINGLE_CHAPTER_REGEX = Regex(
    """(?i)\bch(?:apter)?\s*(\d+)\b"""
)
private val BOOK_SCAN_SNIPPET_COVERAGE_REGEX = Regex(
    """(?i)(?:大量内容|许多章节|相关章节)?\s*(?:仅|只)?\s*snippet\s*(?:导航)?覆盖"""
)
private val BOOK_SCAN_SNIPPET_NAVIGATION_REGEX = Regex(
    """(?i)snippet\s*(?:导航|筛查|浏览)?"""
)
private val BOOK_SCAN_MANIFEST_WORD_REGEX = Regex("""(?i)\bmanifest\b""")
private val BOOK_SCAN_BUDGET_WORD_REGEX = Regex("""(?i)\bscan_(?:100|300)\b""")

private data class XmlReportItem(
    val title: String,
    val text: String,
    val level: String = "medium"
)

private fun formatPressureRating(indexText: String, label: String): String {
    val index = indexText.trim().toIntOrNull()?.coerceIn(1, 5)
    val stars = index?.let(::pressureStars) ?: "☆☆☆☆☆"
    val score = index?.let { "$it/5" } ?: indexText.trim().ifBlank { "-/5" }
    val normalizedLabel = pressureLevelName(index).ifBlank { label }
    return listOf(stars, score, normalizedLabel)
        .filter { it.isNotBlank() }
        .joinToString("｜")
}

private fun pressureLevelName(index: Int?): String {
    return when (index) {
        1 -> "轻松愉快"
        2 -> "略有压力"
        3 -> "明显压抑"
        4 -> "高度压抑"
        5 -> "极度压抑"
        else -> ""
    }
}

private fun pressureStars(index: Int): String {
    return buildString {
        repeat(index.coerceIn(0, 5)) { append('★') }
        repeat(5 - index.coerceIn(0, 5)) { append('☆') }
    }
}

private fun String.xmlSectionItems(sectionName: String): List<String> {
    val section = xmlFirstBlock(sectionName) ?: return emptyList()
    val itemBlocks = section.xmlBlocks("item")
    if (itemBlocks.isNotEmpty()) {
        return itemBlocks.map { block ->
            val title = block.xmlFirstTag("title").ifBlank { block.xmlAttribute("title") }
            val text = block.xmlFirstTag("text").ifBlank { block.xmlTextBody() }
            listOf(title, text).filter { it.isNotBlank() }.joinToString("：")
        }.filter { it.isNotBlank() }
    }
    return section.lines()
        .map { it.trim().trimStart('-', '•').trim() }
        .filter { it.isNotBlank() }
}

private fun String.xmlFirstTag(name: String): String {
    return xmlFirstBlock(name)?.xmlTextBody().orEmpty()
}

private fun String.xmlFirstBlock(name: String): String? {
    return xmlTagRegex(name).find(this)?.groupValues?.get(1)
}

private fun String.xmlBlocks(name: String): List<String> {
    return xmlTagRegex(name).findAll(this).map { it.value }.toList()
}

private fun String.xmlAttribute(name: String): String {
    return Regex("""\b${Regex.escape(name)}\s*=\s*["']([^"']*)["']""")
        .find(this)
        ?.groupValues
        ?.get(1)
        ?.xmlUnescape()
        ?.trim()
        .orEmpty()
}

private fun String.toBookReportRiskLevel(): String {
    return when (trim().lowercase()) {
        "critical", "hard", "high", "severe", "严重", "硬雷" -> "high"
        "medium", "mid", "warning", "warn", "中", "中等" -> "medium"
        "low", "minor", "轻微" -> "low"
        else -> ""
    }
}

private fun String.xmlTextBody(): String {
    return replace(Regex("""(?is)<title\b[^>]*>.*?</title>"""), "")
        .replace(Regex("""(?is)<text\b[^>]*>"""), "")
        .replace(Regex("""(?is)</text>"""), "")
        .replace(Regex("""(?is)<[^>]+>"""), "")
        .xmlUnescape()
        .trim()
}

private fun xmlTagRegex(name: String): Regex {
    val escaped = Regex.escape(name)
    return Regex("""(?is)<$escaped\b[^>]*>(.*?)</$escaped>""")
}

private fun String.xmlUnescape(): String {
    return replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}

private fun JsonObject.stringValue(name: String): String {
    return runCatching {
        get(name)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.asString
    }.getOrNull().orEmpty().trim()
}

private fun JsonObject.objectOrNull(name: String): JsonObject? {
    return runCatching {
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject
    }.getOrNull()
}

private fun JsonObject.arrayOrNull(name: String): JsonArray? {
    return runCatching {
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray
    }.getOrNull()
}

private fun JsonArray?.stringList(): List<String> {
    return this.orEmptyElements()
        .mapNotNull { element ->
            element.takeIf { it.isJsonPrimitive }
                ?.asJsonPrimitive
                ?.asString
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
}

private fun JsonArray?.objectList(): List<JsonObject> {
    return this.orEmptyElements()
        .mapNotNull { element ->
            when {
                element.isJsonObject -> element.asJsonObject
                element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                    JsonObject().apply {
                        addProperty("title", element.asJsonPrimitive.asString.trim())
                        addProperty("text", element.asJsonPrimitive.asString.trim())
                    }
                }
                else -> null
            }
        }
}

private fun JsonArray?.orEmptyElements(): List<JsonElement> {
    if (this == null) return emptyList()
    return (0 until size()).mapNotNull { index -> get(index) }
}

internal fun buildBookScanContinueEntryContent(
    context: AgentModeEntryContext?,
    memories: List<AgentMemory>,
    loadError: String? = null
): String {
    val bookName = context.payloadString("book_name").ifBlank { context?.title.orEmpty() }
    val summary = summarizeBookScanMemories(
        memories = memories,
        totalChapters = context.payloadInt("total_chapters")
    )
    return buildString {
        appendLine("## 继续排雷")
        appendLine()
        append("《").append(bookName.ifBlank { "这本书" }).append("》")
        appendLine("已有扫描记录。下面只说明目前扫到哪里，以及下一步从哪里继续。")
        appendLine()
        appendLine("### 目前扫到哪里")
        appendLine()
        if (loadError != null) {
            appendLine("- **进度**：暂时无法读取详细进度，你仍可以自己指定要检查的章节。")
        } else {
            summary.progressLines.forEach { line -> appendLine("- $line") }
        }
        appendLine()
        appendLine("### 接下来从哪里开始")
        appendLine()
        summary.nextStepLines.forEach { line -> appendLine("- $line") }
        appendLine()
        appendLine("“完整核对”表示读过该章正文；“快速浏览”只用于寻找可疑章节，不能据此判断那些章节一定没有雷点。")
        appendLine()
        appendLine("<legado-interaction>")
        appendLine("<interaction id=\"book_scan_target\" />")
        appendLine("</legado-interaction>")
    }.trim()
}

private data class BookScanMemorySummary(
    val progressLines: List<String>,
    val nextStepLines: List<String>
)

private data class BookScanChapterRange(
    val startChapter: Int,
    val endChapter: Int
) {
    val chapterCount: Int
        get() = endChapter - startChapter + 1
}

private fun summarizeBookScanMemories(
    memories: List<AgentMemory>,
    totalChapters: Int?
): BookScanMemorySummary {
    val validTotal = totalChapters?.takeIf { it > 0 }
    if (memories.isEmpty()) {
        return BookScanMemorySummary(
            progressLines = listOf("**进度**：没有读到可用的扫描记录。"),
            nextStepLines = listOf("**建议**：重新快速定位，或者自己指定章节。")
        )
    }

    val parsedData = memories.mapNotNull { memory ->
        runCatching {
            JsonParser.parseString(memory.dataJson).takeIf { it.isJsonObject }
        }.getOrNull()?.let { memory to it }
    }
    val structuredFullRanges = parsedData.flatMap { (memory, data) ->
        val keys = if (memory.memoryType == "window_bundle") {
            BOOK_SCAN_WINDOW_RANGE_KEYS
        } else {
            BOOK_SCAN_FULL_COVERAGE_KEYS
        }
        data.bookScanValues(keys).flatMap { it.toBookScanChapterRanges() }
    }
    val allRecordText = memories.joinToString("。") { memory ->
        listOf(memory.title, memory.content).filter { it.isNotBlank() }.joinToString("。")
    }
    val fallbackFullRanges = allRecordText.bookScanManifestSentences()
        .filter { sentence ->
            sentence.contains("正文覆盖") ||
                sentence.contains("完整正文") ||
                sentence.contains("完整核对")
        }
        .flatMap { sentence -> sentence.bookScanChapterRangesFromText().asSequence() }
        .toList()
    val fullRanges = mergeBookScanChapterRanges(
        structuredFullRanges.ifEmpty { fallbackFullRanges }
    )
    val fullCount = fullRanges.sumOf { it.chapterCount }

    val structuredNavigationRanges = parsedData.flatMap { (_, data) ->
        data.bookScanValues(BOOK_SCAN_NAVIGATION_RANGE_KEYS)
            .flatMap { it.toBookScanChapterRanges() }
    }
    val structuredNavigationCount = parsedData.asSequence()
        .flatMap { (_, data) -> data.bookScanValues(BOOK_SCAN_NAVIGATION_COUNT_KEYS).asSequence() }
        .mapNotNull { value ->
            runCatching { value.takeIf { it.isJsonPrimitive }?.asInt }.getOrNull()
        }
        .maxOrNull()
    val fallbackNavigationCount = Regex(
        "(?:snippet|片段|快速浏览|导航覆盖)[^。；]{0,24}?(\\d+)\\+?\\s*章",
        RegexOption.IGNORE_CASE
    ).findAll(allRecordText)
        .mapNotNull { match -> match.groupValues.getOrNull(1)?.toIntOrNull() }
        .maxOrNull()
    val navigationCount = structuredNavigationCount
        ?: mergeBookScanChapterRanges(structuredNavigationRanges).sumOf { it.chapterCount }
            .takeIf { it > 0 }
        ?: fallbackNavigationCount

    val preferredNextRanges = parsedData.flatMap { (_, data) ->
        data.bookScanValues(BOOK_SCAN_PREFERRED_NEXT_RANGE_KEYS)
            .flatMap { it.toBookScanChapterRanges() }
    }
    val structuredGapRanges = parsedData.flatMap { (_, data) ->
        data.bookScanValues(BOOK_SCAN_GAP_RANGE_KEYS)
            .flatMap { it.toBookScanChapterRanges() }
    }
    val structuredNextRanges = preferredNextRanges.ifEmpty { structuredGapRanges }
    val fallbackNextRanges = allRecordText.bookScanManifestSentences()
        .filter { sentence ->
            sentence.contains("补读") ||
                sentence.contains("待查") ||
                sentence.contains("下一") ||
                sentence.contains("建议") ||
                sentence.contains("缺口")
        }
        .flatMap { sentence -> sentence.bookScanChapterRangesFromText().asSequence() }
        .toList()
    val explicitNextRange = mergeBookScanChapterRanges(
        structuredNextRanges.ifEmpty { fallbackNextRanges }
    ).firstOrNull()
    val nextRange = explicitNextRange ?: firstMissingBookScanRange(fullRanges, validTotal)

    val progress = buildList {
        if (validTotal != null) add("**全书**：共 $validTotal 章。")
        if (fullRanges.isNotEmpty()) {
            add(
                "**已完整核对**：${formatBookScanRanges(fullRanges)}，" +
                    "共 $fullCount${validTotal?.let { " / $it" }.orEmpty()} 章。"
            )
        } else {
            add("**已完整核对**：现有记录没有保存清楚具体章号，暂时无法准确统计。")
        }
        if (navigationCount != null) {
            add("**已快速浏览**：约 $navigationCount 章；这些章节只做过初步筛查。")
        }
        if (validTotal != null && fullRanges.isNotEmpty()) {
            val remaining = (validTotal - fullCount).coerceAtLeast(0)
            add("**尚未完整核对**：按现有记录计算约 $remaining 章。")
        }
        if (structuredFullRanges.isEmpty() && fallbackFullRanges.isNotEmpty()) {
            add("**记录说明**：旧记录只留下了部分章号，以上是目前能够确认的范围，实际读过的内容可能更多。")
        }
    }
    val nextSteps = if (nextRange != null) {
        listOf(
            "**优先检查**：${formatBookScanRange(nextRange)}。",
            "**之后**：继续检查其他尚未完整核对的中段和人物线。"
        )
    } else {
        listOf("**优先检查**：现有记录没有保存明确的下一处章号；请选择“我来指定章节”，或从尚未完整核对的中段继续。")
    }
    return BookScanMemorySummary(progressLines = progress, nextStepLines = nextSteps)
}

private fun JsonElement.bookScanValues(keys: Set<String>): List<JsonElement> {
    return when {
        isJsonObject -> asJsonObject.entrySet().flatMap { (key, value) ->
            if (key in keys) listOf(value) else value.bookScanValues(keys)
        }
        isJsonArray -> asJsonArray.flatMap { element -> element.bookScanValues(keys) }
        else -> emptyList()
    }
}

private fun JsonElement.toBookScanChapterRanges(): List<BookScanChapterRange> {
    return when {
        isJsonArray -> {
            val array = asJsonArray
            if (
                array.size() == 2 &&
                array.allElements { element ->
                    element.isJsonPrimitive && element.asJsonPrimitive.isNumber
                }
            ) {
                val startIndex = array[0].asInt
                val endExclusive = array[1].asInt
                listOfNotNull(
                    BookScanChapterRange(startIndex + 1, endExclusive)
                        .takeIf { startIndex >= 0 && endExclusive > startIndex }
                )
            } else {
                array.flatMap { element -> element.toBookScanChapterRanges() }
            }
        }
        isJsonObject -> {
            val obj = asJsonObject
            val range = BOOK_SCAN_RANGE_VALUE_KEYS.asSequence()
                .mapNotNull { key -> obj.get(key) }
                .firstOrNull()
            if (range != null) range.toBookScanChapterRanges()
            else obj.entrySet().flatMap { (_, value) -> value.toBookScanChapterRanges() }
        }
        isJsonPrimitive && asJsonPrimitive.isString -> asString.bookScanChapterRangesFromText()
        else -> emptyList()
    }
}

private fun String.bookScanChapterRangesFromText(): List<BookScanChapterRange> {
    return BOOK_SCAN_CHAPTER_RANGE_REGEX.findAll(this).mapNotNull { match ->
        val start = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
        val end = match.groupValues[2].toIntOrNull() ?: start
        BookScanChapterRange(start, end).takeIf { start > 0 && end >= start }
    }.toList()
}

private fun mergeBookScanChapterRanges(
    ranges: List<BookScanChapterRange>
): List<BookScanChapterRange> {
    val sorted = ranges.sortedWith(
        compareBy(BookScanChapterRange::startChapter, BookScanChapterRange::endChapter)
    )
    if (sorted.isEmpty()) return emptyList()
    val merged = mutableListOf(sorted.first())
    sorted.drop(1).forEach { range ->
        val previous = merged.last()
        if (range.startChapter <= previous.endChapter + 1) {
            merged[merged.lastIndex] = previous.copy(
                endChapter = maxOf(previous.endChapter, range.endChapter)
            )
        } else {
            merged += range
        }
    }
    return merged
}

private fun firstMissingBookScanRange(
    covered: List<BookScanChapterRange>,
    totalChapters: Int?
): BookScanChapterRange? {
    val total = totalChapters ?: return null
    var nextStart = 1
    covered.forEach { range ->
        if (range.startChapter > nextStart) {
            return BookScanChapterRange(nextStart, range.startChapter - 1)
        }
        nextStart = maxOf(nextStart, range.endChapter + 1)
    }
    return BookScanChapterRange(nextStart, total).takeIf { nextStart <= total }
}

private fun formatBookScanRanges(ranges: List<BookScanChapterRange>): String {
    val visible = ranges.take(6).joinToString("、", transform = ::formatBookScanRange)
    return if (ranges.size > 6) "$visible 等 ${ranges.size} 段" else visible
}

private fun formatBookScanRange(range: BookScanChapterRange): String {
    return if (range.startChapter == range.endChapter) {
        "第 ${range.startChapter} 章"
    } else {
        "第 ${range.startChapter}–${range.endChapter} 章"
    }
}

private const val BOOK_SCAN_RANGE_VALUE_KEY_CHAPTER_RANGE = "chapter_range"
private val BOOK_SCAN_WINDOW_RANGE_KEYS = setOf(BOOK_SCAN_RANGE_VALUE_KEY_CHAPTER_RANGE)
private val BOOK_SCAN_FULL_COVERAGE_KEYS = setOf(
    "full_text_coverage",
    "full_coverage",
    "complete_coverage"
)
private val BOOK_SCAN_NAVIGATION_RANGE_KEYS = setOf(
    "navigation_coverage",
    "snippet_coverage",
    "navigation_ranges"
)
private val BOOK_SCAN_NAVIGATION_COUNT_KEYS = setOf(
    "navigation_chapter_count",
    "snippet_chapter_count"
)
private val BOOK_SCAN_PREFERRED_NEXT_RANGE_KEYS = setOf(
    "next_scan_ranges",
    "next_scan_range",
    "next_gap",
    "next_continuous_gap"
)
private val BOOK_SCAN_GAP_RANGE_KEYS = setOf(
    "gaps",
    "uncovered_ranges",
    "missing_ranges"
)
private val BOOK_SCAN_RANGE_VALUE_KEYS = listOf(
    BOOK_SCAN_RANGE_VALUE_KEY_CHAPTER_RANGE,
    "range",
    "chapters"
)
private val BOOK_SCAN_CHAPTER_RANGE_REGEX = Regex(
    "(?:第\\s*)?(\\d+)\\s*(?:[-~～—至]\\s*(\\d+))?\\s*章"
)

private fun String.bookScanManifestSentences(): Sequence<String> {
    return lineSequence()
        .flatMap { line ->
            line.trim()
                .trimStart('-', '•')
                .split('。', '；', ';', '\n')
                .asSequence()
        }
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

private fun JsonArray.allElements(predicate: (JsonElement) -> Boolean): Boolean {
    return (0 until size()).all { index -> predicate(get(index)) }
}

internal fun AgentModeEntryContext?.payloadString(key: String): String {
    return runCatching {
        this?.payload
            ?.get(key)
            ?.takeIf { it.isJsonPrimitive }
            ?.asJsonPrimitive
            ?.asString
            ?.trim()
    }.getOrNull().orEmpty()
}

private fun AgentModeEntryContext?.payloadInt(key: String): Int? {
    return runCatching {
        this?.payload
            ?.get(key)
            ?.takeIf { it.isJsonPrimitive }
            ?.asInt
    }.getOrNull()
}

@Composable
private fun WebSources(activity: AiWebActivity, usageProviderId: String?) {
    activity.externalError?.let { error ->
        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    if (activity.searchFailed) {
        Text(
            text = stringResource(R.string.ai_chat_web_search_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    activity.failedLinks.forEach { url ->
        Text(
            text = stringResource(R.string.ai_chat_web_read_failed, url),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (activity.sources.isEmpty()) return

    val context = LocalContext.current
    var expanded by remember(activity.sources) { mutableStateOf(false) }
    InlineDetailEntry(
        label = stringResource(R.string.ai_chat_web_sources, activity.sources.size),
        expanded = expanded,
        onClick = { expanded = !expanded },
    )
    AnimatedVisibility(visible = expanded) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 6.dp)) {
            val providerLabel = if (activity.hasLegacyChatGptNativeLabel(usageProviderId)) {
                stringResource(R.string.ai_search_source_native, "ChatGPT")
            } else {
                activity.providerLabel
            }
            providerLabel?.let { label ->
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            activity.sources.forEach { source ->
                val url = source.url.webUrl() ?: return@forEach
                Text(
                    text = source.title,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { context.openUrl(url) }
                        .padding(vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

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
            color = colorResource(R.color.ng_surface_card),
            contentColor = colorResource(R.color.ng_on_surface)
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
            color = colorResource(R.color.ng_surface_card),
            contentColor = colorResource(R.color.ng_on_surface)
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
                    onExport = onExport,
                    onFork = onFork,
                    onToggleFavorite = onToggleFavorite,
                    onDelete = onDelete,
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
    onDelete: () -> Unit,
    onCopy: (() -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
) {
    fun dismissThen(action: () -> Unit): () -> Unit = {
        onDismissRequest()
        action()
    }

    NgFunctionMenu(
        expanded = true,
        onDismissRequest = onDismissRequest,
        containerColor = aiChatContentCardColor().takeUnless { NgTheme.snapshot.isEInk },
        actions = listOfNotNull(
            onCopy?.let {
                NgFunctionMenuAction(
                    icon = Icons.Rounded.ContentCopy,
                    text = "复制",
                    onClick = dismissThen(it)
                )
            },
            onRegenerate?.let {
                NgFunctionMenuAction(
                    icon = Icons.Rounded.Refresh,
                    text = "重新生成",
                    onClick = dismissThen(it)
                )
            },
            NgFunctionMenuAction(
                icon = Icons.Rounded.Share,
                text = "导出",
                onClick = dismissThen(onExport)
            ),
            NgFunctionMenuAction(
                icon = Icons.AutoMirrored.Rounded.CallSplit,
                text = "分支",
                onClick = dismissThen(onFork)
            ),
            NgFunctionMenuAction(
                icon = if (message.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                text = if (message.favorite) "取消" else "收藏",
                onClick = dismissThen(onToggleFavorite)
            ),
            NgFunctionMenuAction(
                icon = Icons.Rounded.Delete,
                text = "删除",
                danger = true,
                dividerBefore = true,
                onClick = dismissThen(onDelete)
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
    val title = when (action) {
        PendingMessageAction.REGENERATE -> "重新生成"
        PendingMessageAction.DELETE -> "删除"
    }
    val message = when (action) {
        PendingMessageAction.REGENERATE -> "确定要重新生成此消息吗？"
        PendingMessageAction.DELETE -> "确定要删除这条消息吗？"
    }
    if (NgTheme.snapshot.isEInk) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(message) },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text("确认")
                }
            },
        )
        return
    }

    NgPaneDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = title,
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .widthIn(max = 520.dp),
            variant = NgDialogVariant.CLASSIC_CONFIRMATION,
            titleFontWeight = FontWeight.Normal,
            actions = {
                NgDialogTextActionButton(text = "取消", onClick = onDismiss)
                NgDialogTextActionButton(text = "确认", onClick = onConfirm)
            },
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(bottom = 20.dp),
                color = Color(NgTheme.colors.onSurface),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun ExportSelectionFloatingBar(
    selectedCount: Int,
    selectionAvailable: Boolean,
    backdrop: NgLiquidGlassBackdrop?,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
    onInvert: () -> Unit,
    onConfirm: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        NgFloatingIconToolbar(
            modifier = Modifier.width(264.dp),
            liquidBackdrop = backdrop,
            actions = listOf(
                NgFloatingIconToolbarAction(
                    iconRes = R.drawable.ic_baseline_close,
                    label = stringResource(R.string.cancel),
                    onClick = onCancel,
                ),
                NgFloatingIconToolbarAction(
                    iconRes = R.drawable.ic_select_all,
                    label = stringResource(R.string.select_all),
                    onClick = onSelectAll,
                    enabled = selectionAvailable,
                ),
                NgFloatingIconToolbarAction(
                    iconRes = R.drawable.ic_refresh_black_24dp,
                    label = stringResource(R.string.revert_selection),
                    onClick = onInvert,
                    enabled = selectionAvailable,
                ),
                NgFloatingIconToolbarAction(
                    iconRes = R.drawable.ic_check,
                    label = stringResource(R.string.confirm),
                    onClick = onConfirm,
                    enabled = selectedCount > 0,
                    emphasized = true,
                ),
            ),
        )
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
        content()
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
    val hasMeasuredUsage = usage.calibration != null
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
    NgDrawerGlassAlertDialog(
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
                if (hasMeasuredUsage) {
                    Text(
                        text = "基于最近一次接口 usage 校准；会话累计见下方。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
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

internal data class AiSkillInputPolicy(
    val selectorVisible: Boolean,
    val removable: Boolean,
    val toolError: Boolean
)

internal fun resolveSkillInputPolicy(
    hasActiveSkill: Boolean,
    allowsUserSkills: Boolean,
    fixedEntrySkill: Boolean = false,
    modelSupportsToolCalls: Boolean
): AiSkillInputPolicy {
    val skillLocked = hasActiveSkill && (!allowsUserSkills || fixedEntrySkill)
    return AiSkillInputPolicy(
        selectorVisible = allowsUserSkills && !skillLocked,
        removable = allowsUserSkills && !skillLocked,
        toolError = hasActiveSkill && !modelSupportsToolCalls
    )
}

@Composable
private fun RikkaChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onInputFocused: () -> Unit,
    backdrop: NgLiquidGlassBackdrop?,
    messageInputEnabled: Boolean,
    sendEnabled: Boolean,
    sending: Boolean,
    inputNotice: (@Composable () -> Unit)?,
    inputHint: String?,
    @DrawableRes modelIconRes: Int,
    reasoningEnabled: Boolean,
    webAccess: AiWebAccess?,
    mcpEnabled: Boolean,
    skillEnabled: Boolean,
    mcpControlVisible: Boolean,
    skillControlVisible: Boolean,
    contextAvailable: Boolean,
    suggestionAvailable: Boolean,
    suggestionExpanded: Boolean,
    contextUsage: AiChatContextUsage,
    contextUsageWithTools: Boolean,
    imageAssets: List<String>,
    directImage: Boolean,
    showDirectImageLabel: Boolean,
    imageImporting: Boolean,
    imageAttachmentButton: (@Composable () -> Unit)?,
    onImagePreview: (File) -> Unit,
    onImageRemove: (String) -> Unit,
    onClearDirectImage: () -> Unit,
    attachments: List<AiChatInputAttachment>,
    skills: List<AiChatInputAttachment>,
    skillRemovable: Boolean,
    skillErrorText: String?,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onWebSettingsClick: () -> Unit,
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
    val focusManager = LocalFocusManager.current
    val hasInput = value.isNotBlank() || imageAssets.isNotEmpty() && !directImage
    val canSendDraft = sendEnabled && (!sending || imageAssets.isEmpty() && !directImage)
    val sendPrimary = MaterialTheme.colorScheme.primary
    val isDark = NgTheme.snapshot.isDark
    val sendButtonColor = remember(sendPrimary, isDark) {
        Color(NgSolidButtonPalette.resolveContainer(sendPrimary.toArgb(), Color.White.toArgb(), isDark))
    }
    val composerPadding = if (backdrop == null) {
        PaddingValues(8.dp)
    } else {
        PaddingValues(
            start = AiChatHorizontalPadding,
            top = 10.dp,
            end = AiChatHorizontalPadding,
            bottom = AiChatComposerBottomPadding,
        )
    }
    Surface(color = Color.Transparent) {
        Column(
            modifier = Modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(composerPadding)
        ) {
            AiChatComposerSurface(backdrop) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    CompactSkillStatusRow(
                        skills = skills,
                        removable = skillRemovable,
                        errorText = skillErrorText,
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
                    AiImageThumbnailRow(
                        imageAssets, onImagePreview, onImageRemove,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    if (showDirectImageLabel) {
                        TextButton(onClick = onClearDirectImage) {
                            Text(stringResource(R.string.ai_image_generate) + "  ×")
                        }
                    }
                    if (imageImporting) Text(stringResource(R.string.ai_image_importing),
                        style = MaterialTheme.typography.labelMedium)
                    if (inputNotice != null) {
                        inputNotice()
                    } else {
                        AiChatComposerField(
                            value = value,
                            onValueChange = onValueChange,
                            onInputFocused = onInputFocused,
                            messageInputEnabled = messageInputEnabled,
                            hasSkillError = skillErrorText != null,
                            compact = backdrop != null,
                            inputHint = inputHint,
                        )
                    }
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
                            if (webAccess != null) {
                                AiChatWebButton(webAccess) {
                                    focusManager.clearFocus()
                                    onWebSettingsClick()
                                }
                            }
                            if (mcpControlVisible) {
                                InputDrawableIcon(
                                    iconRes = R.drawable.ic_ai_capability_tool,
                                    active = mcpEnabled,
                                    onClick = onMcpClick
                                )
                            }
                            if (skillControlVisible) {
                                InputDrawableIcon(
                                    iconRes = R.drawable.ic_ai_skill_puzzle,
                                    active = skillEnabled,
                                    onClick = onSkillClick
                                )
                            }
                            if (contextUsageWithTools) {
                                ContextUsageIndicator(usage = contextUsage, onClick = onContextUsageClick)
                            }
                        }
                        if (!contextUsageWithTools) {
                            ContextUsageIndicator(usage = contextUsage, onClick = onContextUsageClick)
                        }
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
                        if (sending && hasInput && canSendDraft) {
                            ChatInputActionButton(
                                icon = Icons.Rounded.ArrowUpward,
                                contentDescription = "Queue message",
                                enabled = messageInputEnabled,
                                color = sendButtonColor,
                                tint = Color.White,
                                onClick = onSend
                            )
                        }
                        imageAttachmentButton?.invoke()
                        ChatInputActionButton(
                            icon = if (sending) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                            contentDescription = if (sending) "Stop" else "Send",
                            enabled = sending || canSendDraft && hasInput,
                            color = when {
                                sending -> MaterialTheme.colorScheme.errorContainer
                                !canSendDraft || !hasInput -> MaterialTheme.colorScheme.surfaceVariant
                                else -> sendButtonColor
                            },
                            tint = when {
                                sending -> MaterialTheme.colorScheme.onErrorContainer
                                !canSendDraft || !hasInput ->
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                else -> Color.White
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
internal fun AiChatComposerSurface(
    backdrop: NgLiquidGlassBackdrop?,
    content: @Composable () -> Unit,
) {
    if (backdrop == null) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = aiChatContentCardColor(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
            content = content,
        )
    } else {
        NgVisualSurface(
            modifier = Modifier.fillMaxWidth(),
            role = NgMaterialRole.CONTROL,
            cornerRadius = AiChatComposerCornerRadius,
            style = NgGlassDefaults.neutralStyle(containerAlpha = 1f),
            liquidBackdrop = backdrop,
        ) {
            content()
        }
    }
}

@Composable
internal fun AiChatComposerField(
    value: String,
    onValueChange: (String) -> Unit,
    onInputFocused: () -> Unit,
    messageInputEnabled: Boolean,
    hasSkillError: Boolean,
    compact: Boolean,
    inputHint: String?,
) {
    var inputFocused by remember { mutableStateOf(false) }
    val modifier = Modifier.fillMaxWidth().onFocusChanged { state ->
        inputFocused = state.isFocused
        if (state.isFocused) onInputFocused()
    }
    val placeholder = @Composable {
        if (!inputFocused) {
            Text(
                text = when {
                    hasSkillError -> "请先切换到支持工具调用的模型"
                    messageInputEnabled -> inputHint ?: "输入消息与 AI 聊天"
                    else -> "可先设置模型和思考深度"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    if (compact) {
        val colors = MaterialTheme.colorScheme
        val style = MaterialTheme.typography.bodyLarge
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            readOnly = !messageInputEnabled,
            maxLines = 5,
            textStyle = style.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp)) {
                    if (value.isEmpty()) {
                        CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) {
                            placeholder()
                        }
                    }
                    innerTextField()
                }
            },
        )
    } else {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            readOnly = !messageInputEnabled,
            placeholder = placeholder,
            maxLines = 5,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
        )
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
    removable: Boolean,
    errorText: String?,
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
                val statusColor = if (errorText != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
                Icon(
                    painter = painterResource(R.drawable.ic_ai_skill_puzzle),
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = errorText ?: "Skill：${skill.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (errorText != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (removable) {
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
    val baseSnapshot = NgTheme.snapshot
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Transparent,
        contentColor = Color(baseSnapshot.colors.onSurface),
        shape = RectangleShape,
    ) {
        NgBottomDrawerSurface(
            modifier = Modifier.fillMaxWidth(),
            contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiChatInternalMcpSheet(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modules: List<McpInternalToolModule>,
    selectedCapabilityIds: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    selectionEnabled: Boolean,
    onDismiss: () -> Unit
) {
    val baseSnapshot = NgTheme.snapshot
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val expandedModuleIds = remember(modules) {
        mutableStateListOf<String>()
    }
    val allCapabilityIds = remember(modules) {
        modules.flatMap { module ->
            module.capabilities.map { capability -> capability.id }
        }.toSet()
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
        contentColor = Color(baseSnapshot.colors.onSurface),
        shape = RectangleShape,
    ) {
        NgBottomDrawerSurface(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (enabled && selectionEnabled) Modifier.fillMaxHeight(0.70f) else Modifier),
            contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 8.dp, bottom = 12.dp)
            ) {
                NgLongDrawerHeader(
                    title = stringResource(R.string.ai_internal_mcp),
                    centerTitle = true,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                NgFormPanel(modifier = Modifier.padding(horizontal = 16.dp)) {
                    AiAssistantSwitchRow(
                        title = stringResource(R.string.ai_internal_mcp_enable),
                        summary = stringResource(R.string.ai_internal_mcp_enable_summary),
                        checked = enabled,
                        onCheckedChange = onEnabledChange,
                    )
                    if (!enabled) return@NgFormPanel

                    NgFormGroupDivider()
                    if (!selectionEnabled) {
                        modules.flatMap { it.capabilities }.forEach { capability ->
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(capability.title, style = MaterialTheme.typography.titleMedium)
                                Text(capability.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        return@NgFormPanel
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, top = 4.dp, end = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.ai_internal_mcp_session_features),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
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
                                onSelectionChange(allCapabilityIds - selectedCapabilityIds)
                            }
                        ) {
                            Text(selectionActionText)
                        }
                    }
                    NgFormGroupDivider()
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        contentPadding = PaddingValues(bottom = 6.dp)
                    ) {
                        modules.forEachIndexed { index, module ->
                            item(key = "mcp-module-${module.id}") {
                                val expanded = module.id in expandedModuleIds
                                Column {
                                    if (index > 0) NgFormGroupDivider()
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
                                        Column {
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
        modifier = Modifier.fillMaxWidth(),
        variant = NgExpandableListVariant.FORM_ROW,
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
        summary = if (capability.requiresUserConfirmation) {
            "${stringResource(R.string.ai_operation_permission_mode_confirm_write)} · " +
                capability.description
        } else {
            capability.description
        },
        selected = selected,
        badges = listOf(NgListBadge(capability.toolNames.size.toString())),
        onToggle = onToggle,
        modifier = Modifier.fillMaxWidth(),
        variant = NgExpandableListVariant.FORM_ROW,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiChatInputAttachmentSheet(
    title: String,
    description: String? = null,
    emptyText: String,
    availableAttachments: List<AiChatInputAttachment>,
    loadedAttachmentIds: Set<String>,
    onAdd: (AiChatInputAttachment) -> Unit,
    onDismiss: () -> Unit,
    centeredHeader: Boolean = false,
) {
    val baseSnapshot = NgTheme.snapshot
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Transparent,
        contentColor = Color(baseSnapshot.colors.onSurface),
        shape = RectangleShape,
    ) {
        NgBottomDrawerSurface(
            modifier = Modifier.fillMaxWidth(),
            contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (centeredHeader) {
                    NgLongDrawerHeader(title = title, centerTitle = true)
                } else {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                val isSkill = attachment.type == AiChatInputAttachmentType.SKILL
                Surface(
                    onClick = { if (!loaded) onAdd(attachment) },
                    enabled = !loaded,
                    shape = RoundedCornerShape(18.dp),
                    color = ngDrawerContentCardColor(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = 14.dp,
                            vertical = if (isSkill) 7.dp else 12.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            painter = if (isSkill) {
                                painterResource(R.drawable.ic_ai_skill_puzzle)
                            } else {
                                rememberVectorPainter(Icons.Rounded.Description)
                            },
                            contentDescription = null,
                            tint = if (isSkill) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(if (isSkill) 20.dp else 24.dp)
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

private val ChatInputIconSize = 22.dp

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
            AiModelLogo(
                iconRes = iconRes,
                contentDescription = stringResource(R.string.ai_model_select),
                modifier = Modifier.size(ChatInputIconSize)
            )
        }
    }
}

@Composable
private fun InputDrawableIcon(
    @DrawableRes iconRes: Int,
    active: Boolean,
    description: String? = null,
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
                contentDescription = description,
                modifier = Modifier.size(ChatInputIconSize),
                tint = when {
                    active -> MaterialTheme.colorScheme.primary
                    NgTheme.snapshot.isDark -> MaterialTheme.colorScheme.onSurface
                    else -> Color.Unspecified
                }
            )
        }
    }
}

@Composable
internal fun AiChatWebButton(access: AiWebAccess, onClick: () -> Unit) {
    val description = when (access) {
        AiWebAccess.Disabled -> stringResource(R.string.ai_chat_web_disabled)
        AiWebAccess.Unavailable -> stringResource(R.string.ai_chat_web_unavailable)
        AiWebAccess.Native -> stringResource(R.string.ai_chat_web_native)
        is AiWebAccess.External -> stringResource(R.string.ai_chat_web_external, access.name)
    }
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val background = aiChatContentCardColor()
    Surface(
        onClick = onClick,
        modifier = Modifier.size(34.dp).semantics { contentDescription = description },
        shape = CircleShape,
        color = Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (access is AiWebAccess.External) {
                Box(Modifier.size(ChatInputIconSize)) {
                    Image(
                        painter = painterResource(access.provider.iconRes),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                    )
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .padding(bottom = 2.dp)
                            .size(12.dp)
                            .background(background, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_cfg_web),
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = accent,
                        )
                    }
                }
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_cfg_web),
                    contentDescription = null,
                    modifier = Modifier.size(ChatInputIconSize).drawWithContent {
                        drawContent()
                        if (access == AiWebAccess.Disabled) {
                            val start = Offset(size.width * 0.18f, size.height * 0.18f)
                            val end = Offset(size.width * 0.82f, size.height * 0.82f)
                            drawLine(background, start, end, 3.5.dp.toPx(), StrokeCap.Round)
                            drawLine(muted, start, end, 1.5.dp.toPx(), StrokeCap.Round)
                        }
                    },
                    tint = if (access == AiWebAccess.Native) accent else muted,
                )
            }
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
        AiModelLogo(
            iconRes = iconRes,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            onDark = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

internal fun AiChatTurnResult.toUiMessage(elapsedMs: Long, modelSelection: AiModelSelection): ChatUiMessage {
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
        content = content.ifBlank {
            if (imageAssetIds.isNotEmpty() || toolReceipts.any { it.isCodexImage() }) ""
            else reasoning?.takeIf { it.isNotBlank() } ?: "模型未返回正文"
        },
        meta = meta,
        reasoning = reasoning,
        toolTrace = toolTrace,
        toolReceipts = toolReceipts,
        imageAssetIds = imageAssetIds,
        memoryTrace = memoryTrace,
        webActivity = webActivity,
        elapsedMs = elapsedMs,
        promptTokens = usage?.promptTokens,
        localPromptTokens = null,
        contextAnchorTokens = contextCalibration?.contextTokens,
        localContextAnchorTokens = contextCalibration?.localContextTokens,
        usageProviderId = modelSelection.providerId,
        usageModelId = modelSelection.modelId,
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
    markdown: String,
): File {
    val file = createChatExportFile(context, "md")
    file.writeText(markdown, Charsets.UTF_8)
    return file
}

private fun exportChatMessagesAsImage(
    context: Context,
    text: String,
): File {
    val file = createChatExportFile(context, "png")
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

private fun buildChatExportMarkdown(
    title: String,
    messages: List<ChatUiMessage>,
    senderLabel: (ChatUiMessage) -> String,
): String {
    return buildString {
        append("# ").append(title).append("\n\n")
        append("*导出时间：")
            .append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            .append("*\n\n")
        messages.forEach { message ->
            append("## ").append(senderLabel(message)).append("\n\n")
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

private fun buildChatExportPlainText(
    title: String,
    messages: List<ChatUiMessage>,
    senderLabel: (ChatUiMessage) -> String,
): String {
    return buildString {
        append(title).append("\n")
        append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        append("\n\n")
        messages.forEach { message ->
            append(senderLabel(message)).append("：\n")
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

private fun <T> MutableList<T>.replaceWith(newItems: List<T>) {
    clear()
    addAll(newItems)
}

internal fun MutableList<AiChatSessionSnapshot>.sortChatSessions() {
    sortWith(
        compareByDescending<AiChatSessionSnapshot> { it.isPinned }
            .thenByDescending { it.updatedAt }
    )
}

internal fun MutableList<JsonObject>.replaceUploadMessages(
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
                        ),
                        message.imageAssetIds,
                        message.directImageAction,
                    ))
                }
                ChatRole.ASSISTANT -> if (message.meta != "error" && message.contextCompaction == null) {
                    add(
                        message.uploadContent?.let { draft ->
                            AiChatContextManager.interruptedReplyMessage(message.id, draft)
                        } ?: chatClient.newAssistantMessage(message.content, message.reasoning)
                    )
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

@Suppress("UNUSED_PARAMETER")
private fun buildContextInputAttachments(contextSources: List<String>): List<AiChatInputAttachment> {
    return emptyList()
}

private fun buildSkillPickerAttachments(): List<AiChatInputAttachment> {
    return AiSkillRegistry.agentSkills()
        .filterNot { it.id == AiSkillRegistry.SKILL_CHARACTER_CARD_GENERATE }
        .map(::toSkillInputAttachment)
}

internal fun resolveEntryAgentMode(entrySource: String?): AgentModeDefinition {
    return when (entrySource) {
        AiChatActivity.ENTRY_BOOK_SCAN -> AgentModeRegistry.bookScan
        AiChatActivity.ENTRY_BOOK_SEARCH -> AgentModeRegistry.bookSearch
        else -> AgentModeRegistry.general
    }
}

internal fun buildModeSkillInputAttachments(
    mode: AgentModeDefinition,
    requestedSkillIds: List<String>,
    pinnedMessages: List<JsonObject> = emptyList()
): List<AiChatInputAttachment> {
    val workflowId = mode.systemWorkflowId
    val systemSkillIds = mode.availableSystemSkillIds
    val systemSkillSetHash = if (workflowId != null) {
        val packages = AiSkillPackageRegistry.systemPackageSet(systemSkillIds)
        if (packages == null && mode.id == AgentModeRegistry.BOOK_SEARCH_ID) return emptyList()
        requireNotNull(packages) {
            "System Skill Package 集合不存在：${systemSkillIds.joinToString()}"
        }.contentHash
    } else {
        ""
    }
    val attachments = when {
        workflowId != null -> buildSkillInputAttachments(
            skillIds = listOf(workflowId),
            pinnedMessages = pinnedMessages,
            skillResolver = { skillId ->
                AiSkillRegistry.systemWorkflow(skillId)?.copy(contentHash = systemSkillSetHash)
            }
        ).map { attachment ->
            attachment.copy(
                availableSkillIds = systemSkillIds,
                availableSkills = systemSkillIds.mapNotNull(AiSkillRegistry::systemWorkflow)
                    .map { skill -> skill.toAvailableSkillSnapshot() }
            )
        }

        mode.allowsUserSkills -> buildSkillInputAttachments(
            skillIds = requestedSkillIds,
            pinnedMessages = pinnedMessages
        )

        else -> emptyList()
    }
    if (workflowId != null) {
        if (mode.id == AgentModeRegistry.BOOK_SEARCH_ID && attachments.isEmpty()) return emptyList()
        check(attachments.size == 1) { "System Workflow 资源不存在：$workflowId" }
        check(
            McpInternalToolCatalog.normalizeCapabilityIds(
                attachments.single().skillRuntime.mcpCapabilities
            ) == McpInternalToolCatalog.normalizeCapabilityIds(mode.fixedMcpCapabilityIds)
        ) {
            "System Workflow 与 Agent Mode 的 MCP capability 快照不一致：$workflowId"
        }
    }
    return attachments
}

internal fun resolveModeManagedCapabilities(
    mode: AgentModeDefinition,
    attachments: List<AiChatInputAttachment>
): List<String> {
    return if (mode.fixedMcpCapabilityIds.isNotEmpty()) {
        McpInternalToolCatalog.normalizeCapabilityIds(mode.fixedMcpCapabilityIds)
    } else {
        McpInternalToolCatalog.normalizeCapabilityIds(
            attachments.flatMap { attachment -> attachment.authorizedSkillCapabilities() }
        )
    }
}

private fun buildSkillInputAttachments(
    skillIds: List<String>,
    pinnedMessages: List<JsonObject> = emptyList(),
    skillResolver: (String) -> io.legado.app.help.ai.AiSkillDefinition? = AiSkillRegistry::get
): List<AiChatInputAttachment> {
    val pinned = AiChatContextManager.activeSkillSnapshot(pinnedMessages)
    return skillIds.asReversed().firstNotNullOfOrNull { skillId ->
        val normalizedId = skillId.trim()
        if (normalizedId.isBlank()) return@firstNotNullOfOrNull null
        val current = skillResolver(normalizedId)
            ?.takeIf { it.scope == AiSkillScope.AGENT }
            ?.let(::toSkillInputAttachment)
        if (pinned != null && pinned.skillId == normalizedId && pinned.prompt.isNotBlank()) {
            val pinnedRuntimeRevision = pinned.runtimeRevision.ifBlank {
                pinned.toRuntimeRevisionToken()
            }
            (current ?: AiChatInputAttachment(
                id = "skill.$normalizedId",
                type = AiChatInputAttachmentType.SKILL,
                title = pinned.title.ifBlank { normalizedId },
                subtitle = "当前会话固定的 Skill 版本",
                prompt = pinned.prompt
            )).copy(
                title = pinned.title.ifBlank { current?.title ?: normalizedId },
                prompt = pinned.prompt,
                skillRuntime = pinned.runtime,
                skillRevision = pinned.revision,
                skillContentHash = pinned.contentHash,
                skillRuntimeRevision = pinnedRuntimeRevision,
                availableSkills = pinned.availableSkills.ifEmpty {
                    current?.availableSkills.orEmpty()
                },
                trustedBuiltIn = isPinnedSkillTrustedBuiltIn(
                    currentTrusted = current?.trustedBuiltIn == true,
                    currentContentHash = current?.skillContentHash.orEmpty(),
                    currentRuntimeRevision = current?.skillRuntimeRevision.orEmpty(),
                    pinnedContentHash = pinned.contentHash,
                    pinnedRuntimeRevision = pinnedRuntimeRevision
                )
            )
        } else {
            current
        }
    }?.let(::listOf).orEmpty()
}

internal fun isPinnedSkillTrustedBuiltIn(
    currentTrusted: Boolean,
    currentContentHash: String,
    currentRuntimeRevision: String,
    pinnedContentHash: String,
    pinnedRuntimeRevision: String
): Boolean {
    return currentTrusted &&
        pinnedContentHash.isNotBlank() &&
        pinnedContentHash == currentContentHash &&
        pinnedRuntimeRevision.isNotBlank() &&
        pinnedRuntimeRevision == currentRuntimeRevision
}

internal fun buildIntentContextInputAttachments(rawAttachments: List<String>): List<AiChatInputAttachment> {
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
        suggestions = skill.suggestions,
        skillRuntime = skill.runtime,
        skillRevision = skill.revision,
        skillContentHash = skill.contentHash,
        skillRuntimeRevision = buildRuntimeRevisionToken(skill.id, skill.revision, skill.contentHash),
        availableSkillIds = listOf(skill.id),
        availableSkills = listOf(skill.toAvailableSkillSnapshot()),
        trustedBuiltIn = skill.builtIn && !skill.userModified
    )
}

internal fun AiChatInputAttachment.activeSkillSnapshot() = AiActiveSkillSnapshot(
    skillId = id.removePrefix("skill."),
    title = title,
    prompt = prompt,
    revision = skillRevision,
    runtimeRevision = skillRuntimeRevision,
    contentHash = skillContentHash,
    runtime = skillRuntime,
    description = subtitle,
    availableSkills = availableSkills,
)

private fun io.legado.app.help.ai.AiSkillDefinition.toAvailableSkillSnapshot(): AiAvailableSkillSnapshot {
    return AiAvailableSkillSnapshot(
        id = id,
        name = name,
        description = summary
    )
}

private fun AiChatInputAttachment.authorizedSkillCapabilities(): List<String> {
    val requested = McpInternalToolCatalog.normalizeCapabilityIds(
        skillRuntime.mcpCapabilities
    )
    if (trustedBuiltIn) return requested
    val granted = AiSkillCapabilityGrantStore.granted(skillRuntimeRevision)
    return requested.filter { capabilityId -> capabilityId in granted }
}

private fun AiActiveSkillSnapshot.toRuntimeRevisionToken(): String {
    return buildRuntimeRevisionToken(skillId, revision, contentHash)
}

private fun buildRuntimeRevisionToken(
    skillId: String,
    revision: String,
    contentHash: String
): String {
    val id = skillId.trim().ifBlank { "skill" }
    val version = revision.trim().ifBlank { "unversioned" }
    return contentHash.trim().takeIf { it.isNotBlank() }
        ?.let { hash -> "$id@$version@$hash" }
        ?: "$id@$version"
}

internal fun List<AiChatInputAttachment>.loadedSkillIds(): List<String> {
    return filter { it.type == AiChatInputAttachmentType.SKILL }
        .map { it.id.removePrefix("skill.").trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

internal fun buildUserUploadContent(
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

private suspend fun buildGlobalMessageSearchResults(
    keyword: AiChatSearchQuery,
    sessions: List<AiChatSessionSnapshot>,
    activeSessionId: String?,
    currentMessages: List<ChatUiMessage>,
    markdown: Markwon,
): List<GlobalMessageSearchResult> {
    val query = keyword.text
    val searchContext = currentCoroutineContext()
    val activeSession = sessions.firstOrNull { it.id == activeSessionId }
    val activeTitle = currentMessages.deriveChatTitle()
        ?: activeSession?.title
        ?: "当前聊天"
    val activeUpdatedAt = activeSession?.updatedAt ?: System.currentTimeMillis()
    val activeResults = currentMessages
        .mapIndexedNotNull { index, message ->
            searchContext.ensureActive()
            if (message.loading) {
                return@mapIndexedNotNull null
            }
            val corpus = message.searchCorpus()
            val start = corpus.indexOf(query, ignoreCase = true)
            if (start < 0) return@mapIndexedNotNull null
            GlobalMessageSearchResult(
                session = activeSession,
                sessionId = activeSessionId,
                sessionTitle = activeTitle,
                messageId = message.id,
                messageIndex = index,
                isUser = message.role == ChatRole.USER,
                snippet = keyword.excerpt(corpus, start until (start + query.length)),
                updatedAt = activeUpdatedAt
            )
        }
    val historyResults = sessions
        .filterNot { it.id == activeSessionId }
        .flatMap { session ->
            session.messages.mapIndexedNotNull { index, message ->
                searchContext.ensureActive()
                val corpus = message.searchCorpus()
                val start = corpus.indexOf(query, ignoreCase = true)
                if (start < 0) return@mapIndexedNotNull null
                GlobalMessageSearchResult(
                    session = session,
                    sessionId = session.id,
                    sessionTitle = session.title,
                    messageId = message.id,
                    messageIndex = index,
                    isUser = message.role == AiChatMessageSnapshot.ROLE_USER,
                    snippet = keyword.excerpt(corpus, start until (start + query.length)),
                    updatedAt = session.updatedAt
                )
            }
        }
    return (activeResults + historyResults)
        .sortedByDescending { it.updatedAt }
        .take(100)
        .map { result ->
            searchContext.ensureActive()
            if (result.isUser) return@map result
            val body = if (result.sessionId == activeSessionId) {
                currentMessages[result.messageIndex].content
            } else {
                result.session?.messages?.get(result.messageIndex)?.content
            }
            if (body == null || !body.contains(query, ignoreCase = true)) return@map result

            // Parse the original body before cutting the excerpt and preserve hits in other fields.
            val parts = ChatSearchSource(result.messageId, isUser = false, content = body)
                .searchableParts(markdown)
            val excerpt = parts.firstNotNullOfOrNull { (_, text) ->
                keyword.findIn(text)?.let { keyword.excerpt(text, it.range) }
            }
            if (excerpt != null) result.copy(snippet = excerpt) else result
        }
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
        toolTrace.orEmpty().joinToString("\n")
    ).joinToString("\n")
}

private fun formatChatSearchTime(timeMillis: Long): String {
    if (timeMillis <= 0L) {
        return "未知时间"
    }
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timeMillis))
}

internal fun List<ChatUiMessage>.deriveChatTitle(): String? {
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

internal fun resolveConversationTitle(
    modeEntryTitle: String?,
    derivedTitle: String?,
    previousTitle: String?
): String {
    return modeEntryTitle?.takeIf { it.isNotBlank() }
        ?: derivedTitle?.takeIf { it.isNotBlank() }
        ?: previousTitle?.takeIf { it.isNotBlank() }
        ?: "新聊天"
}

private fun buildDrawerHistoryGroups(
    sessions: List<AiChatSessionSnapshot>,
    skillPresentations: Map<String, Pair<String, AiSkillPresentation>>
): List<DrawerHistoryGroup> {
    val groupOrder = listOf("置顶", "书籍相关", "书架管理", "普通聊天")
    val items = sessions.map { session ->
        val identity = session.drawerIdentity(skillPresentations)
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

private fun AiChatSessionSnapshot.drawerIdentity(
    skillPresentations: Map<String, Pair<String, AiSkillPresentation>>
): DrawerConversationIdentity {
    val activeSkill = loadedSkillIds.asReversed().firstNotNullOfOrNull { skillId ->
        skillPresentations[skillId.trim().lowercase(Locale.ROOT)]
    }
    val skillName = activeSkill?.first
    val presentation = activeSkill?.second
    val subject = if (presentation?.subjectFromConversation == true) {
        extractChatSubject(drawerCorpus())
    } else {
        null
    }
    val compactTitle = title.toCompactDrawerText()
    val scene = DrawerScene(
        groupTitle = presentation?.conversationGroup ?: "普通聊天",
        title = presentation?.conversationTitle
            ?: skillName
            ?: compactTitle.ifBlank { "普通聊天" }
    )
    val titleText = if (subject != null && scene.title !in subject) {
        "${scene.title} · $subject"
    } else {
        scene.title
    }.toCompactDrawerText(maxLength = 32)
    val preview = messages
        .asReversed()
        .firstOrNull { it.content.isNotBlank() }
        ?.drawerPreview()
        ?.takeIf { it.isNotBlank() && it != title && it != titleText }
        ?: "草稿".takeIf { modeId == AgentModeRegistry.BOOK_SEARCH_ID && modeEntryContext.bookSearchDraft() != null }
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
    messages: List<ChatUiMessage>,
    skillPresentations: Map<String, Pair<String, AiSkillPresentation>>
): List<DrawerFavoriteItem> {
    val activeSession = sessions.firstOrNull { it.id == activeSessionId }
    val titleCache = sessions.associate { session ->
        session.id to session.drawerIdentity(skillPresentations).title
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

internal fun List<ChatUiMessage>.latestPromptUsageAnchor(): AiChatPromptUsageAnchor? {
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

internal fun ChatUiMessage.toSnapshot(): AiChatMessageSnapshot {
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
        toolReceipts = toolReceipts,
        memoryTrace = memoryTrace,
        webActivity = webActivity?.settledOrNull(),
        bookSearch = bookSearch,
        elapsedMs = elapsedMs,
        favorite = favorite,
        deliveryState = deliveryState.snapshotValue,
        uploadContent = uploadContent,
        resolvedInteractionIds = resolvedInteractionIds,
        interactionResultLabels = interactionResultLabels,
        interactionResultSelections = interactionResultSelections,
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
        contextCompactionSummaryCompletionTokens = contextCompaction?.summaryCompletionTokens,
        imageAssetIds = imageAssetIds.takeIf { it.isNotEmpty() },
        directImageAction = directImageAction,
    )
}

internal fun AiChatMessageSnapshot.toUiMessage(): ChatUiMessage {
    return ChatUiMessage(
        id = id.ifBlank { UUID.randomUUID().toString() },
        role = when (role) {
            AiChatMessageSnapshot.ROLE_USER -> ChatRole.USER
            else -> ChatRole.ASSISTANT
        },
        content = content,
        imageAssetIds = imageAssetIds.orEmpty(),
        directImageAction = directImageAction,
        meta = meta,
        reasoning = reasoning,
        toolTrace = toolTrace.orEmpty(),
        toolReceipts = toolReceipts.orEmpty(),
        memoryTrace = memoryTrace.orEmpty(),
        webActivity = webActivity?.settledOrNull(),
        bookSearch = bookSearch?.let { reply ->
            if (reply.lookup?.status == AiBookSourceStatus.RUNNING) {
                reply.copy(lookup = reply.lookup.copy(status = AiBookSourceStatus.INTERRUPTED))
            } else reply
        },
        elapsedMs = elapsedMs,
        favorite = favorite,
        deliveryState = ChatDeliveryState.fromSnapshot(
            deliveryState ?: AiChatMessageSnapshot.DELIVERY_SENT
        ),
        uploadContent = uploadContent,
        resolvedInteractionIds = resolvedInteractionIds,
        interactionResultLabels = interactionResultLabels,
        interactionResultSelections = interactionResultSelections,
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

internal data class ChatUiMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val content: String,
    val meta: String? = null,
    val reasoning: String? = null,
    val toolTrace: List<String> = emptyList(),
    val toolReceipts: List<ToolExecutionReceipt> = emptyList(),
    val memoryTrace: List<AiMemoryTraceItem> = emptyList(),
    val webActivity: AiWebActivity? = null,
    val bookSearch: AiBookSearchReply? = null,
    val elapsedMs: Long? = null,
    val favorite: Boolean = false,
    val loading: Boolean = false,
    val deliveryState: ChatDeliveryState = ChatDeliveryState.SENT,
    val uploadContent: String? = null,
    val resolvedInteractionIds: List<String> = emptyList(),
    val interactionResultLabels: Map<String, String> = emptyMap(),
    val interactionResultSelections: Map<String, Map<String, String>> = emptyMap(),
    val promptTokens: Int? = null,
    val localPromptTokens: Int? = null,
    val contextAnchorTokens: Int? = null,
    val localContextAnchorTokens: Int? = null,
    val usageProviderId: String? = null,
    val usageModelId: String? = null,
    val contextCompaction: AiChatCompactionRecord? = null,
    val imageAssetIds: List<String> = emptyList(),
    val directImageAction: Boolean = false,
    val imageWait: AiChatImageWait? = null,
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
    val isUser: Boolean,
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

internal enum class ChatRole {
    USER,
    ASSISTANT
}

internal enum class ChatDeliveryState(val snapshotValue: String) {
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

internal fun formatContextTokens(tokens: Int): String {
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
