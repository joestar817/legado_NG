package io.legado.app.ui.config

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgLazyListFastScroller
import io.legado.app.ui.design.components.compose.NgLazyListFastScrollerVariant
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.theme.NgTheme

internal data class TtsVoiceDrawerGroup(
    val engineId: String,
    val engineName: String,
    val cards: List<TtsVoiceDrawerCard>,
)

/**
 * 发音人卡片的只读展示快照。调用方必须在 IO 线程一次生成，列表滚动期间不得再访问
 * TtsEngineStore 或重新解析语言／Tag。
 */
internal data class TtsVoiceDrawerCard(
    val option: TtsVoiceOption,
    val key: String,
    val selected: Boolean,
    val languageLabels: List<String>,
    val genderLabel: String?,
    val style: String?,
    val tags: List<String>,
)

internal data class TtsVoiceDrawerState(
    val loading: Boolean = true,
    val groups: List<TtsVoiceDrawerGroup> = emptyList(),
    val languageOptions: List<String> = emptyList(),
    val genderOptions: List<String> = emptyList(),
    val fetchError: String? = null,
    val canRetryFetch: Boolean = false,
    val preview: TtsVoicePreviewStatus = TtsVoicePreviewStatus(null, TtsVoicePreviewState.IDLE),
)

internal data class TtsVoiceDrawerTitleAction(
    val text: String,
    @param:DrawableRes val iconRes: Int? = null,
    val onClick: () -> Unit,
)

internal fun TtsVoiceOption.toDrawerCard(selected: Boolean): TtsVoiceDrawerCard {
    val languageLabels = if (systemDefault) emptyList() else {
        TtsVoiceFilterSupport.languageLabels(voice.language)
    }
    val genderLabel = if (systemDefault) null else {
        TtsVoiceFilterSupport.genderLabel(voice.gender)
    }
    val style = voice.style
        ?.takeUnless { systemDefault }
        ?.takeIf { it.isNotBlank() }
    val tags = if (systemDefault) {
        listOf(engine.name.ifBlank { voice.id })
    } else {
        voice.tags.filter { it.isNotBlank() }.distinct().ifEmpty {
            if (style == null) listOf(voice.id) else emptyList()
        }
    }
    return TtsVoiceDrawerCard(
        option = this,
        key = previewKey(),
        selected = selected,
        languageLabels = languageLabels,
        genderLabel = genderLabel,
        style = style,
        tags = tags,
    )
}

/**
 * 听书、默认声音和书籍角色共用的发音人抽屉主体。调用方只负责主题、标题操作和保存逻辑。
 */
@Composable
internal fun TtsVoiceSelectionDrawerContent(
    title: String,
    searchHint: String,
    emptyText: String,
    state: TtsVoiceDrawerState,
    titleAction: TtsVoiceDrawerTitleAction? = null,
    enableLongPressPreview: Boolean = false,
    onSelect: (TtsVoiceOption) -> Unit,
    onPreview: (TtsVoiceOption) -> Unit,
    onRetryFetch: (() -> Unit)? = null,
) {
    val drawerHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    var query by remember { mutableStateOf("") }
    var selectedLanguages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedGenders by remember { mutableStateOf<Set<String>>(emptySet()) }
    var filtersExpanded by remember { mutableStateOf(false) }
    val voiceListState = rememberLazyListState()
    val filtersActive = query.isNotBlank() ||
        selectedLanguages.isNotEmpty() || selectedGenders.isNotEmpty()
    val filteredGroups = remember(
        state.groups,
        query,
        selectedLanguages,
        selectedGenders,
    ) {
        state.groups.mapNotNull { group ->
            val cards = group.cards.filter { card ->
                val languageMatch = selectedLanguages.isEmpty() ||
                    card.languageLabels.any { it in selectedLanguages }
                val genderMatch = selectedGenders.isEmpty() ||
                    card.genderLabel?.let { it in selectedGenders } == true
                card.option.matchesName(query) && languageMatch && genderMatch
            }
            group.takeIf { cards.isNotEmpty() }?.copy(cards = cards)
        }
    }
    val filteredItemCount = filteredGroups.sumOf { group -> group.cards.size + 1 }
    val hasIconTitleAction = titleAction?.iconRes != null
    val voiceCardShape = remember { RoundedCornerShape(18.dp) }
    val selectionColor = Color(LocalContext.current.accentColor)

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
            BackHandler(enabled = filtersExpanded) {
                filtersExpanded = false
            }
            NgLongDrawerHeader(
                title = title,
                actionIconRes = titleAction?.iconRes ?: R.drawable.ic_tts_params_grid,
                actionContentDescription = if (hasIconTitleAction) {
                    titleAction.text
                } else {
                    "筛选发音人"
                },
                actionActive = !hasIconTitleAction && (filtersExpanded || filtersActive),
                onActionClick = if (hasIconTitleAction) {
                    titleAction.onClick
                } else {
                    { filtersExpanded = !filtersExpanded }
                },
                trailingActionText = titleAction?.text.takeUnless { hasIconTitleAction },
                onTrailingActionClick = titleAction?.onClick.takeUnless { hasIconTitleAction },
                secondaryActionIconRes = R.drawable.ic_tts_params_grid.takeIf {
                    hasIconTitleAction
                },
                secondaryActionContentDescription = "筛选发音人",
                secondaryActionActive = hasIconTitleAction && (filtersExpanded || filtersActive),
                onSecondaryActionClick = if (hasIconTitleAction) {
                    { filtersExpanded = !filtersExpanded }
                } else {
                    null
                },
            )
            if (filtersExpanded) {
                TtsVoiceFilterPanel(
                    query = query,
                    onQueryChange = { query = it },
                    searchHint = searchHint,
                    languageOptions = state.languageOptions,
                    selectedLanguages = selectedLanguages,
                    onLanguageToggle = { selectedLanguages = selectedLanguages.toggled(it) },
                    genderOptions = state.genderOptions,
                    selectedGenders = selectedGenders,
                    onGenderToggle = { selectedGenders = selectedGenders.toggled(it) },
                )
            }
            when {
                state.loading -> TtsVoiceDrawerMessage(
                    text = "正在获取发音人…",
                    showProgress = true,
                )
                state.groups.isEmpty() && state.fetchError != null && onRetryFetch != null -> {
                    TtsVoiceDrawerRetryMessage(state.fetchError, onRetryFetch)
                }
                state.groups.isEmpty() && state.fetchError != null -> {
                    TtsVoiceDrawerMessage(state.fetchError)
                }
                state.groups.isEmpty() && state.canRetryFetch && onRetryFetch != null -> {
                    TtsVoiceDrawerRetryMessage("尚未获取到发音人", onRetryFetch)
                }
                filteredGroups.isEmpty() -> {
                    TtsVoiceDrawerMessage(if (filtersActive) "没有匹配的发音人" else emptyText)
                }
                else -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    LazyColumn(
                        state = voiceListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        filteredGroups.forEach { group ->
                            item(key = "header:${group.engineId}") {
                                Text(
                                    text = group.engineName,
                                    color = Color(NgTheme.colors.primary),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(
                                        start = 4.dp,
                                        top = 8.dp,
                                        bottom = 2.dp,
                                    ),
                                )
                            }
                            items(
                                items = group.cards,
                                key = { it.key },
                                contentType = { "voice" },
                            ) { card ->
                                TtsVoiceSelectionCard(
                                    card = card,
                                    previewState = state.preview.takeIf {
                                        it.key == card.key
                                    }?.state ?: TtsVoicePreviewState.IDLE,
                                    shape = voiceCardShape,
                                    selectionColor = selectionColor,
                                    enableLongPressPreview = enableLongPressPreview,
                                    onSelect = { onSelect(card.option) },
                                    onPreview = { onPreview(card.option) },
                                )
                            }
                        }
                    }
                    NgLazyListFastScroller(
                        state = voiceListState,
                        itemCount = filteredItemCount,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp),
                        variant = NgLazyListFastScrollerVariant.FLOATING_HANDLE,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TtsVoiceFilterPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    searchHint: String,
    languageOptions: List<String>,
    selectedLanguages: Set<String>,
    onLanguageToggle: (String) -> Unit,
    genderOptions: List<String>,
    selectedGenders: Set<String>,
    onGenderToggle: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(NgTheme.colors.inputContainer))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        TtsVoiceSearchField(query, onQueryChange, searchHint)
        if (languageOptions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "语言",
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 12.sp,
                    lineHeight = 28.sp,
                    modifier = Modifier.width(40.dp),
                )
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    languageOptions.forEach { option ->
                        TtsVoiceFilterChip(
                            label = option,
                            selected = option in selectedLanguages,
                            onClick = { onLanguageToggle(option) },
                        )
                    }
                }
            }
        }
        if (genderOptions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "性别",
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 12.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier.width(40.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    genderOptions.forEach { option ->
                        val selected = option in selectedGenders
                        val isMale = option == "男"
                        Box(
                            modifier = Modifier
                                .size(width = 34.dp, height = 24.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) Color(NgTheme.colors.selectedContainer)
                                    else Color(NgTheme.colors.surface)
                                )
                                .clickable { onGenderToggle(option) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (isMale) R.drawable.ic_tts_gender_male
                                    else R.drawable.ic_tts_gender_female
                                ),
                                contentDescription = option,
                                tint = when {
                                    !selected -> Color(NgTheme.colors.onSurfaceVariant)
                                    isMale -> colorResource(R.color.ng_tts_gender_male)
                                    else -> colorResource(R.color.ng_tts_gender_female)
                                },
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TtsVoiceSelectionCard(
    card: TtsVoiceDrawerCard,
    previewState: TtsVoicePreviewState,
    shape: RoundedCornerShape,
    selectionColor: Color,
    enableLongPressPreview: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
) {
    val cardClickModifier = if (enableLongPressPreview) {
        Modifier.combinedClickable(onClick = onSelect, onLongClick = onPreview)
    } else {
        Modifier.clickable(onClick = onSelect)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .clip(shape)
            .background(Color(NgTheme.colors.inputContainer))
            .then(cardClickModifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 70.dp)
                .padding(start = 18.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                TtsVoiceCardHeader(card)
                TtsVoiceCardTags(card.tags)
            }
            TtsVoiceCardPreview(previewState, onPreview)
        }
        if (card.selected) {
            Box(
                modifier = Modifier.matchParentSize(),
            ) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(selectionColor),
                )
            }
        }
    }
}

@Composable
private fun TtsVoiceCardHeader(card: TtsVoiceDrawerCard) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = card.option.voice.name,
            color = Color(NgTheme.colors.onSurface),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        when (card.genderLabel) {
            "男" -> TtsVoiceGenderIcon(
                iconRes = R.drawable.ic_tts_gender_male,
                colorRes = R.color.ng_tts_gender_male,
            )
            "女" -> TtsVoiceGenderIcon(
                iconRes = R.drawable.ic_tts_gender_female,
                colorRes = R.color.ng_tts_gender_female,
            )
        }
        card.languageLabels.forEach { label ->
            TtsVoiceTagChip(
                text = label,
                contentColorRes = R.color.ng_tts_language,
                containerColorRes = R.color.ng_tts_language_container,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        card.style?.let {
            TtsVoiceTagChip(
                text = it,
                contentColorRes = R.color.ng_tts_tag_blue,
                containerColorRes = R.color.ng_tts_tag_blue_container,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun TtsVoiceCardTags(tags: List<String>) {
    if (tags.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .padding(top = 1.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tags.forEachIndexed { index, tag ->
            val colors = ttsVoiceTagPalette(index)
            TtsVoiceTagChip(
                text = tag,
                contentColorRes = colors.first,
                containerColorRes = colors.second,
            )
        }
    }
}

@Composable
private fun TtsVoiceCardPreview(
    previewState: TtsVoicePreviewState,
    onPreview: () -> Unit,
) {
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
}

@Composable
private fun TtsVoiceSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
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
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focused = it.isFocused },
            singleLine = true,
            textStyle = TextStyle(
                color = Color(NgTheme.colors.onSurface),
                fontSize = 14.sp,
            ),
            cursorBrush = SolidColor(Color(NgTheme.colors.primary)),
            decorationBox = { inner ->
                if (query.isEmpty() && !focused) {
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
private fun TtsVoiceFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
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
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun TtsVoiceGenderIcon(iconRes: Int, colorRes: Int) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = colorResource(colorRes),
        modifier = Modifier
            .padding(start = 8.dp)
            .size(18.dp),
    )
}

@Composable
private fun TtsVoiceTagChip(
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
private fun TtsVoiceDrawerMessage(text: String, showProgress: Boolean = false) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Color(NgTheme.colors.primary),
                    strokeWidth = 2.5.dp,
                )
                Spacer(Modifier.height(12.dp))
            }
            Text(
                text = text,
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun TtsVoiceDrawerRetryMessage(text: String, onRetry: () -> Unit) {
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

private fun ttsVoiceTagPalette(index: Int): Pair<Int, Int> = when (index % 5) {
    0 -> R.color.ng_tts_tag_blue to R.color.ng_tts_tag_blue_container
    1 -> R.color.ng_tts_tag_purple to R.color.ng_tts_tag_purple_container
    2 -> R.color.ng_tts_tag_orange to R.color.ng_tts_tag_orange_container
    3 -> R.color.ng_tts_tag_green to R.color.ng_tts_tag_green_container
    else -> R.color.ng_tts_tag_pink to R.color.ng_tts_tag_pink_container
}

private fun Set<String>.toggled(value: String): Set<String> =
    if (value in this) this - value else this + value
