package io.legado.app.ui.book.read

import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.help.config.AppConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.config.ReadConfigDialogSurface
import io.legado.app.ui.book.read.config.ReadConfigDialogTitle
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.replace.edit.ReplaceRuleEditDialog
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment

/**
 * 起效的替换规则
 */
class EffectiveReplacesDialog : DialogFragment(), ReplaceRuleEditDialog.Callback {

    private val viewModel by activityViewModels<ReadBookViewModel>()
    private var effectiveItems by mutableStateOf(emptyList<EffectiveReplaceItem>())
    private var showChineseConverterSelector by mutableStateOf(false)
    private var isEdit = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            setStyle(STYLE_NO_TITLE, 0)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        effectiveItems = buildEffectiveItems()
        val snapshot = ReadDrawerStyle.themeSnapshot(requireContext())
        val chineseModes = resources.getStringArray(R.array.chinese_mode).toList()
        (view as ComposeView).setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                EffectiveReplacesPanel(
                    title = getString(R.string.effective_replaces),
                    emptyLabel = getString(R.string.no_effective_replace_rules),
                    disableLabel = getString(R.string.replace_rule_disable),
                    items = effectiveItems,
                    onItemClick = ::onItemClick,
                    onRemove = ::removeItem,
                )
                if (showChineseConverterSelector) {
                    ChineseConverterSelector(
                        title = getString(R.string.chinese_converter),
                        options = chineseModes,
                        selectedIndex = AppConfig.chineseConverterType,
                        onDismiss = { showChineseConverterSelector = false },
                        onSelected = ::selectChineseConverter,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (isEdit) {
            viewModel.replaceRuleChanged()
        }
    }

    override fun show(manager: FragmentManager, tag: String?) {
        kotlin.runCatching {
            manager.beginTransaction().remove(this).commit()
            super.show(manager, tag)
        }.onFailure {
            AppLog.put("显示对话框失败 tag:$tag", it)
        }
    }

    private fun buildEffectiveItems(): List<EffectiveReplaceItem> {
        val rules = ReadBook.curTextChapter?.effectiveReplaceRules.orEmpty()
        return buildList {
            rules.forEachIndexed { index, rule ->
                add(
                    EffectiveReplaceItem(
                        key = "rule:${rule.id}:$index",
                        rule = rule,
                    )
                )
            }
            if (AppConfig.chineseConverterType > 0) {
                add(
                    EffectiveReplaceItem(
                        key = CHINESE_CONVERTER_KEY,
                        rule = ReplaceRule(0, "繁简转换"),
                        isChineseConverter = true,
                    )
                )
            }
        }
    }

    private fun onItemClick(item: EffectiveReplaceItem) {
        if (item.isChineseConverter) {
            showChineseConverterSelector = true
        } else {
            showDialogFragment(ReplaceRuleEditDialog(item.rule.id))
        }
    }

    override fun onReplaceRuleSaved() {
        isEdit = true
    }

    private fun removeItem(item: EffectiveReplaceItem) {
        isEdit = true
        effectiveItems = effectiveItems.filterNot { it.key == item.key }
        if (item.isChineseConverter) {
            AppConfig.chineseConverterType = 0
        } else {
            item.rule.isEnabled = false
            appDb.replaceRuleDao.insert(item.rule)
        }
    }

    private fun selectChineseConverter(index: Int) {
        if (AppConfig.chineseConverterType != index) {
            AppConfig.chineseConverterType = index
            isEdit = true
        }
        showChineseConverterSelector = false
    }

    private companion object {
        const val CHINESE_CONVERTER_KEY = "chinese_converter"
    }
}

private data class EffectiveReplaceItem(
    val key: String,
    val rule: ReplaceRule,
    val isChineseConverter: Boolean = false,
)

@Composable
private fun EffectiveReplacesPanel(
    title: String,
    emptyLabel: String,
    disableLabel: String,
    items: List<EffectiveReplaceItem>,
    onItemClick: (EffectiveReplaceItem) -> Unit,
    onRemove: (EffectiveReplaceItem) -> Unit,
) {
    val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.72f).dp
    ReadConfigDialogSurface(
        contentPadding = PaddingValues(
            start = 14.dp,
            top = 18.dp,
            end = 14.dp,
            bottom = 12.dp,
        ),
    ) {
        ReadConfigDialogTitle(title)
        if (items.isEmpty()) {
            Text(
                text = emptyLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 18.dp, end = 12.dp, bottom = 8.dp),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            return@ReadConfigDialogSurface
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .heightIn(max = maxListHeight),
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> item.key },
            ) { index, item ->
                EffectiveReplaceRow(
                    item = item,
                    disableLabel = disableLabel,
                    onClick = { onItemClick(item) },
                    onRemove = { onRemove(item) },
                )
                if (index < items.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(0.8.dp)
                            .background(
                                Color(NgTheme.colors.onSurface).copy(alpha = 0.12f)
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun EffectiveReplaceRow(
    item: EffectiveReplaceItem,
    disableLabel: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.rule.name,
            modifier = Modifier.weight(1f),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    role = Role.Button,
                    onClickLabel = disableLabel,
                    onClick = onRemove,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_block_outline),
                contentDescription = disableLabel,
                modifier = Modifier.size(24.dp),
                tint = Color(NgTheme.colors.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun ChineseConverterSelector(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        ReadConfigDialogSurface(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            ) {
                options.forEachIndexed { index, option ->
                    val selected = index == selectedIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) {
                                    Color(NgTheme.colors.primary).copy(alpha = 0.14f)
                                } else {
                                    Color.Transparent
                                }
                            )
                            .clickable(
                                role = Role.RadioButton,
                                onClick = { onSelected(index) },
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = option,
                            modifier = Modifier.weight(1f),
                            color = Color(
                                if (selected) {
                                    NgTheme.colors.secondary
                                } else {
                                    NgTheme.colors.onSurface
                                }
                            ),
                            fontSize = 15.sp,
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color(NgTheme.colors.primary),
                            )
                        }
                    }
                }
            }
        }
    }
}
