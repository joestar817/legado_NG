package io.legado.app.ui.config

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.legado.app.R
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.ui.design.components.NgStatusTagSpec
import io.legado.app.ui.design.components.NgStatusTagVariant
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.components.compose.NgManagementLeadingIcon
import io.legado.app.ui.design.components.compose.NgManagementListCard
import io.legado.app.ui.design.components.compose.ngDrawerContentCardColor
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme

/**
 * 多角色 TTS 引擎的共用 Compose 抽屉主体。
 *
 * 设置页和听书页只负责各自的主题宿主与保存逻辑，标题操作、搜索和卡片渲染必须共用这里。
 */
@Composable
internal fun TtsEngineSelectionDrawerContent(
    title: String,
    searchHint: String,
    emptyText: String,
    engines: List<TtsEngineSetting>,
    selectedEngineId: String?,
    loading: Boolean = false,
    contentCardStyle: NgDrawerContentCardStyle = NgDrawerContentCardStyle.LEGACY,
    onSelect: (TtsEngineSetting) -> Unit,
    onClear: (() -> Unit)? = null,
) {
    val drawerHeight = (LocalConfiguration.current.screenHeightDp * 0.68f).dp
    var query by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val filteredEngines = engines.filter { engine ->
        query.isBlank() || engine.name.contains(query.trim(), ignoreCase = true)
    }
    BackHandler(enabled = searchExpanded) {
        searchExpanded = false
        query = ""
    }
    LaunchedEffect(searchExpanded) {
        if (searchExpanded) searchFocusRequester.requestFocus()
    }

    NgBottomDrawerSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(drawerHeight),
        contentCardStyle = contentCardStyle,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            NgLongDrawerHeader(
                title = title,
                actionIconRes = if (!selectedEngineId.isNullOrBlank() && onClear != null) {
                    R.drawable.ic_clear
                } else {
                    null
                },
                actionContentDescription = "清除选择",
                onActionClick = onClear.takeIf { !selectedEngineId.isNullOrBlank() },
                secondaryActionIconRes = R.drawable.ic_search,
                secondaryActionContentDescription = if (searchExpanded) "收起搜索" else "搜索",
                secondaryActionActive = searchExpanded || query.isNotBlank(),
                onSecondaryActionClick = {
                    searchExpanded = !searchExpanded
                    if (!searchExpanded) query = ""
                },
            )
            if (searchExpanded) {
                TtsEngineSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    hint = searchHint,
                    focusRequester = searchFocusRequester,
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                )
            }
            when {
                loading -> TtsEngineDrawerMessage("正在加载引擎…")
                filteredEngines.isEmpty() -> TtsEngineDrawerMessage(emptyText)
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filteredEngines, key = { it.id }) { engine ->
                        NgManagementListCard(
                            title = engine.name,
                            detailTags = engineSelectionTags(engine),
                            selected = engine.id == selectedEngineId,
                            onClick = { onSelect(engine) },
                            leading = {
                                NgManagementLeadingIcon(
                                    iconRes = R.drawable.ic_ai_capability_tts,
                                    contentDescription = "TTS 引擎",
                                    tint = Color(NgTheme.colors.primary),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 设置页使用的 Compose 抽屉宿主；主体与听书页共用 [TtsEngineSelectionDrawerContent]。 */
class TtsEngineSelectionSheet(
    private val context: Context,
    private val title: CharSequence,
    private val searchHint: CharSequence,
    private val emptyText: CharSequence,
    engines: List<TtsEngineSetting>,
    private val selectedEngineId: String?,
    private val onSelect: (TtsEngineSetting) -> Unit,
    private val onClear: (() -> Unit)? = null,
    loading: Boolean = false,
) {
    private var dialog: BottomSheetDialog? = null
    private var drawerEngines by mutableStateOf(engines)
    private var drawerLoading by mutableStateOf(loading)

    fun updateEngines(engines: List<TtsEngineSetting>) {
        drawerEngines = engines
        drawerLoading = false
    }

    fun show() {
        val bottomSheet = BottomSheetDialog(context)
        dialog = bottomSheet
        val contentView = ComposeView(context).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    TtsEngineSelectionDrawerContent(
                        title = title.toString(),
                        searchHint = searchHint.toString(),
                        emptyText = emptyText.toString(),
                        engines = drawerEngines,
                        selectedEngineId = selectedEngineId,
                        loading = drawerLoading,
                        contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
                        onSelect = { engine ->
                            onSelect(engine)
                            dismiss()
                        },
                        onClear = onClear?.let { clear ->
                            {
                                clear()
                                dismiss()
                            }
                        },
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
        bottomSheet.setOnDismissListener { dialog = null }
        bottomSheet.show()
    }

    fun dismiss() {
        dialog?.dismiss()
    }
}

@Composable
private fun TtsEngineSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ngDrawerContentCardColor())
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
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused },
            singleLine = true,
            textStyle = TextStyle(
                color = Color(NgTheme.colors.onSurface),
                fontSize = 14.sp,
            ),
            cursorBrush = SolidColor(Color(NgTheme.colors.primary)),
            decorationBox = { inner ->
                if (query.isEmpty() && !isFocused) {
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
private fun TtsEngineDrawerMessage(
    text: String,
    showProgress: Boolean = false,
) {
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

private fun engineSelectionTags(engine: TtsEngineSetting): List<NgStatusTagSpec> = listOf(
    NgStatusTagSpec(
        text = if (engine.enabled) "已启用" else "已禁用",
        variant = if (engine.enabled) NgStatusTagVariant.SUCCESS else NgStatusTagVariant.WARNING,
    ),
    NgStatusTagSpec(
        text = if (engine.type == TtsEngineType.SCRIPT) "脚本" else "系统",
        variant = NgStatusTagVariant.INFO,
    ),
    NgStatusTagSpec(
        text = engine.effectiveVoices().size.takeIf { it > 0 }
            ?.let { "$it 个发音人" }
            ?: "未获取",
        variant = NgStatusTagVariant.INFO,
    ),
)
