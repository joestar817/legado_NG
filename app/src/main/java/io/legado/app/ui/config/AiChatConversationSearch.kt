package io.legado.app.ui.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.ai.AiProviderSetting
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgLiquidGlassBackdrop
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgVisualSurface
import kotlinx.coroutines.CompletableDeferred
import kotlin.math.roundToInt

internal val AiChatHorizontalPadding = 8.dp
internal val AiChatToolbarTopPadding = 8.dp
internal val AiChatToolbarCornerRadius = 14.dp

internal class AiChatConversationSearchState(
    val entryIndex: Int,
    val entryOffset: Int,
) {
    var fieldValue by mutableStateOf(TextFieldValue())
        private set
    val query get() = fieldValue.text
    var location by mutableStateOf<AiChatSearchLocation?>(null)
    var focusResults by mutableStateOf(true)
    var hasLocatedMessage = false
    val resultsListState = LazyListState()

    fun updateQuery(value: TextFieldValue) {
        if (value.text.trim() != query.trim()) resultsListState.requestScrollToItem(0)
        fieldValue = value
    }
}

internal data class AiChatSearchPart(val block: Int, val index: Int = 0)

internal data class AiChatSearchMatch(
    val messageId: String,
    val isUser: Boolean,
    val part: AiChatSearchPart,
    val excerpt: String,
    val providerId: String?,
    val modelId: String?,
)

internal data class AiChatSearchMatches(
    val query: AiChatSearchQuery,
    val messages: List<AiChatSearchMatch>,
)

internal class AiChatSearchLocation(
    val match: AiChatSearchMatch,
    val query: AiChatSearchQuery,
) {
    private var messageCoordinates: LayoutCoordinates? = null
    private var textCoordinates: LayoutCoordinates? = null
    private var lineTop = 0f
    val matchOffset = CompletableDeferred<Int>()

    fun recordMessage(coordinates: LayoutCoordinates) {
        messageCoordinates = coordinates
        resolveOffset()
    }

    fun recordMatch(coordinates: LayoutCoordinates, top: Float) {
        textCoordinates = coordinates
        lineTop = top
        resolveOffset()
    }

    private fun resolveOffset() {
        if (matchOffset.isCompleted) return
        val message = messageCoordinates?.takeIf { it.isAttached } ?: return
        val text = textCoordinates?.takeIf { it.isAttached } ?: return
        // Use the message's local coordinates so clipping and the keyboard do not shift the anchor.
        val offset = message.localPositionOf(text, Offset(0f, lineTop)).y
        matchOffset.complete(offset.roundToInt().coerceAtLeast(0))
    }
}

@Composable
internal fun AiChatFloatingToolbarSurface(
    backdrop: NgLiquidGlassBackdrop,
    content: @Composable RowScope.() -> Unit,
) {
    NgVisualSurface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = AiChatHorizontalPadding)
            .padding(top = AiChatToolbarTopPadding),
        role = NgMaterialRole.CONTROL,
        cornerRadius = AiChatToolbarCornerRadius,
        style = NgGlassDefaults.bookDetailStyle(
            containerColor = colorResource(R.color.ng_bookshelf_manage_header_surface),
        ),
        liquidBackdrop = backdrop,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
internal fun AiChatConversationSearchBar(
    search: AiChatConversationSearchState,
    backdrop: NgLiquidGlassBackdrop?,
    resultIndex: Int,
    resultCount: Int,
    onBack: () -> Unit,
    onExit: () -> Unit,
    onEdit: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val location = search.location
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val content: @Composable RowScope.() -> Unit = {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_left_search),
                contentDescription = stringResource(
                    if (location == null) R.string.ai_chat_find_exit else R.string.ai_chat_find_results
                ),
                modifier = Modifier.size(22.dp),
            )
        }
        if (location == null) {
            AiChatConversationSearchField(search, focusRequester, Modifier.weight(1f))
            if (search.query.isNotEmpty()) {
                IconButton(onClick = {
                    search.updateQuery(TextFieldValue())
                    focusRequester.requestFocus()
                    keyboard?.show()
                }) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.clear),
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clickable(onClickLabel = stringResource(R.string.ai_chat_find_edit), onClick = onEdit),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChatSearchIcon()
                Text(
                    text = location.query.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onPrevious, enabled = resultIndex > 0) {
                Icon(
                    Icons.Rounded.ArrowUpward,
                    contentDescription = stringResource(R.string.ai_chat_find_previous),
                    modifier = Modifier.size(18.dp),
                )
            }
            if (resultIndex >= 0) {
                Text(
                    text = stringResource(R.string.ai_chat_find_position, resultIndex + 1, resultCount),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onNext, enabled = resultIndex in 0 until (resultCount - 1)) {
                Icon(
                    Icons.Rounded.ArrowDownward,
                    contentDescription = stringResource(R.string.ai_chat_find_next),
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onExit) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.ai_chat_find_exit),
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
    if (backdrop != null) {
        AiChatFloatingToolbarSurface(backdrop, content)
    } else {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .heightIn(min = 54.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
private fun AiChatConversationSearchField(
    search: AiChatConversationSearchState,
    focusRequester: FocusRequester,
    modifier: Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val hint = stringResource(R.string.ai_chat_find_hint)
    val textColor = LocalContentColor.current
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor)
    LaunchedEffect(search) {
        if (search.focusResults) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    BasicTextField(
        value = search.fieldValue,
        onValueChange = search::updateQuery,
        modifier = modifier
            .focusRequester(focusRequester)
            .semantics { contentDescription = hint },
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            focusManager.clearFocus()
            keyboard?.hide()
        }),
        decorationBox = { innerField ->
            Row(
                modifier = Modifier.heightIn(min = 48.dp).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChatSearchIcon()
                Box(Modifier.weight(1f)) {
                    if (search.query.isEmpty()) {
                        Text(
                            text = hint,
                            style = textStyle,
                            color = textColor.copy(alpha = 0.62f),
                            maxLines = 1,
                        )
                    }
                    innerField()
                }
            }
        },
    )
}

@Composable
private fun ChatSearchIcon() {
    Icon(
        painter = painterResource(R.drawable.ic_search),
        contentDescription = null,
        modifier = Modifier.size(22.dp),
    )
}

@Composable
internal fun AiChatConversationSearchResults(
    search: AiChatConversationSearchState,
    results: AiChatSearchMatches?,
    padding: PaddingValues,
    backdrop: NgLiquidGlassBackdrop?,
    messageModelProviders: Map<String, AiProviderSetting>,
    assistantAvatar: @Composable (Int) -> Unit,
    onOpenMessage: (AiChatSearchMatch, AiChatSearchQuery) -> Unit,
) {
    if (search.query.isBlank()) return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .padding(top = padding.calculateTopPadding() + 12.dp)
            .padding(horizontal = AiChatHorizontalPadding),
    ) {
        val status = when {
            results == null -> R.string.ai_chat_find_searching
            results.messages.isEmpty() -> R.string.ai_chat_find_empty
            else -> null
        }
        if (status != null) {
            Surface(
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                contentColor = colorResource(R.color.ng_on_surface),
            ) {
                Text(
                    text = stringResource(status),
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        } else if (results != null) {
            Text(
                text = pluralStringResource(
                    R.plurals.ai_chat_find_count,
                    results.messages.size,
                    results.messages.size,
                ),
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = search.resultsListState,
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(results.messages, key = { it.messageId }) { match ->
                    val model = if (match.isUser) {
                        null
                    } else {
                        remember(match.providerId, match.modelId, messageModelProviders) {
                            resolveChatMessageModel(match.providerId, match.modelId, messageModelProviders)
                        }
                    }
                    ChatSearchResultCard(
                        match = match,
                        query = results.query,
                        backdrop = backdrop,
                        assistantModel = model,
                        assistantAvatar = assistantAvatar,
                        onClick = { onOpenMessage(match, results.query) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatSearchResultCard(
    match: AiChatSearchMatch,
    query: AiChatSearchQuery,
    backdrop: NgLiquidGlassBackdrop?,
    assistantModel: AiChatMessageModel?,
    assistantAvatar: @Composable (Int) -> Unit,
    onClick: () -> Unit,
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 86.dp).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (assistantModel != null) assistantAvatar(assistantModel.iconRes)
                    Text(
                        text = if (match.isUser) stringResource(R.string.ai_chat_find_you)
                        else assistantModel?.label ?: stringResource(R.string.ai_assistant),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val style = aiChatSearchHighlightStyle()
                val excerpt = remember(match.excerpt, query, style) {
                    query.highlight(AnnotatedString(match.excerpt), style)
                }
                Text(
                    text = excerpt,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
    AiChatSearchResultSurface(backdrop = backdrop, onClick = onClick, content = content)
}

@Composable
internal fun AiChatSearchResultSurface(
    backdrop: NgLiquidGlassBackdrop?,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (backdrop != null) {
        NgVisualSurface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            role = NgMaterialRole.CONTENT,
            cornerRadius = 16.dp,
            style = NgGlassDefaults.neutralStyle(containerAlpha = 0.98f)
                .copy(contentColor = MaterialTheme.colorScheme.onSurface),
            liquidBackdrop = backdrop,
        ) { content() }
    } else {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            content = content,
        )
    }
}

@Composable
internal fun aiChatSearchHighlightStyle() = SpanStyle(
    color = MaterialTheme.colorScheme.onPrimaryContainer,
    background = MaterialTheme.colorScheme.primaryContainer,
)

internal fun AiChatSearchQuery.highlight(value: AnnotatedString, style: SpanStyle): AnnotatedString {
    val matches = findAllIn(value.text).iterator()
    if (!matches.hasNext()) return value
    return AnnotatedString.Builder(value).apply {
        matches.forEach { match -> addStyle(style, match.range.first, match.range.last + 1) }
    }.toAnnotatedString()
}
