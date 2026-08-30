package io.legado.app.ui.book.toc.rule

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.model.localBook.TextFile
import io.legado.app.ui.association.ImportTxtTocRuleDialog
import io.legado.app.ui.association.RuleDeleteConfirmDialog
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgCompactEditorDialog
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuVariant
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionRow
import io.legado.app.ui.design.components.compose.NgManagementDrawerPanel
import io.legado.app.ui.design.components.compose.NgManagementDrawerPanelVariant
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.components.compose.NgSwitchControl
import io.legado.app.ui.design.components.compose.NgSwitchControlVariant
import io.legado.app.ui.design.components.compose.ngDraggedItem
import io.legado.app.ui.design.components.compose.ngReorderAfterLongPress
import io.legado.app.ui.design.components.compose.rememberNgLazyReorderState
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.ui.widget.dialog.ngDialogMaxHeight
import io.legado.app.utils.SelectFileContract
import io.legado.app.utils.launch
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.takePersistableReadPermission
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

private const val PICKER_ACTION_IMPORT_LOCAL = 0x57300001
private const val PICKER_ACTION_IMPORT_ONLINE = 0x57300002
private const val PICKER_ACTION_IMPORT_QR = 0x57300003
private const val PICKER_ACTION_IMPORT_DEFAULT = 0x57300004
private const val PICKER_ACTION_HELP = 0x57300005
private const val PICKER_ROW_TOP = 0x57300101
private const val PICKER_ROW_BOTTOM = 0x57300102
private const val PICKER_ROW_DELETE = 0x57300103

/** 阅读器内共用的 TXT 目录规则选择与管理弹窗。 */
class TxtTocRuleDialog() : BaseComposeDialogFragment(),
    TxtTocRuleEditDialog.Callback,
    TxtTocRuleOnlineImportDialog.Callback {

    constructor(tocRegex: String?) : this() {
        arguments = Bundle().apply { putString(ARG_TOC_REGEX, tocRegex) }
    }

    private val viewModel: TxtTocRuleViewModel by viewModels()
    private var rules by mutableStateOf<List<TxtTocRule>>(emptyList())
    var selectedName by mutableStateOf<String?>(null)
    private var durRegex: String? = null
    private var pendingDelete by mutableStateOf<TxtTocRule?>(null)
    private val qrCodeResult = registerForActivityResult(QrCodeResult()) {
        it ?: return@registerForActivityResult
        showDialogFragment(ImportTxtTocRuleDialog(it))
    }
    private val importDoc = registerForActivityResult(SelectFileContract()) { uri ->
        uri?.let {
            it.takePersistableReadPermission()
            showDialogFragment(ImportTxtTocRuleDialog(uri.toString()))
        }
    }

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(height = ngDialogMaxHeight(0.8f))
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        durRegex = arguments?.getString(ARG_TOC_REGEX)
        (view as ComposeView).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    BackHandler { dismissAllowingStateLoss() }
                    TxtTocRulePickerDialogContent(
                        rules = rules,
                        selectedName = selectedName,
                        onSelect = { selectedName = it.name },
                        onAdd = { showDialogFragment(TxtTocRuleEditDialog()) },
                        onImportLocal = {
                            importDoc.launch(arrayOf("text/*", "application/json"))
                        },
                        onImportOnline = {
                            showDialogFragment(TxtTocRuleOnlineImportDialog())
                        },
                        onImportQr = { qrCodeResult.launch() },
                        onImportDefault = viewModel::importDefault,
                        onHelp = { showHelp("txtTocRuleHelp") },
                        onEdit = { showDialogFragment(TxtTocRuleEditDialog(it.id)) },
                        onToggleEnabled = { rule, enabled ->
                            viewModel.update(rule.copy(enable = enabled))
                        },
                        onMoveToTop = { viewModel.toTop(it) },
                        onMoveToBottom = { viewModel.toBottom(it) },
                        onDelete = { pendingDelete = it },
                        onReorder = viewModel::updateOrder,
                        onCancel = { dismissAllowingStateLoss() },
                        onConfirm = ::confirmSelection,
                    )
                    pendingDelete?.let { rule ->
                        RuleDeleteConfirmDialog(
                            itemName = rule.name,
                            onDismiss = { pendingDelete = null },
                            onConfirm = {
                                pendingDelete = null
                                if (selectedName == rule.name) selectedName = null
                                viewModel.del(rule)
                            },
                        )
                    }
                }
            }
        }
        lifecycleScope.launch {
            appDb.txtTocRuleDao.observeAll().catch {
                AppLog.put("TXT目录规则对话框获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect { items ->
                initSelectedName(items)
                rules = items
            }
        }
    }

    private fun initSelectedName(items: List<TxtTocRule>) {
        if (selectedName == null && durRegex != null) {
            selectedName = items.firstOrNull {
                durRegex == it.rule + TextFile.spaceChars + it.replacement
            }?.name.orEmpty()
        }
    }

    private fun confirmSelection() {
        val rule = rules.firstOrNull { it.name == selectedName } ?: return
        (activity as? CallBack)?.onTocRegexDialogResult(
            rule.rule + TextFile.spaceChars + rule.replacement
        )
        dismissAllowingStateLoss()
    }

    override fun saveTxtTocRule(txtTocRule: TxtTocRule) {
        viewModel.save(txtTocRule)
    }

    override fun onTxtTocRuleOnlineImportConfirmed(text: String) {
        showDialogFragment(ImportTxtTocRuleDialog(text))
    }

    interface CallBack {
        fun onTocRegexDialogResult(tocRegex: String) = Unit
    }

    private companion object {
        const val ARG_TOC_REGEX = "tocRegex"
    }
}

@Composable
private fun TxtTocRulePickerDialogContent(
    rules: List<TxtTocRule>,
    selectedName: String?,
    onSelect: (TxtTocRule) -> Unit,
    onAdd: () -> Unit,
    onImportLocal: () -> Unit,
    onImportOnline: () -> Unit,
    onImportQr: () -> Unit,
    onImportDefault: () -> Unit,
    onHelp: () -> Unit,
    onEdit: (TxtTocRule) -> Unit,
    onToggleEnabled: (TxtTocRule, Boolean) -> Unit,
    onMoveToTop: (TxtTocRule) -> Unit,
    onMoveToBottom: (TxtTocRule) -> Unit,
    onDelete: (TxtTocRule) -> Unit,
    onReorder: (List<TxtTocRule>) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val topMenuState = remember { NgPopupToggleState() }
    val topMenuItems = remember {
        listOf(
            NgExpandableActionMenuItem(
                itemId = PICKER_ACTION_IMPORT_LOCAL,
                titleRes = R.string.import_local,
                iconRes = R.drawable.ic_folder_open,
            ),
            NgExpandableActionMenuItem(
                itemId = PICKER_ACTION_IMPORT_ONLINE,
                titleRes = R.string.import_on_line,
                iconRes = R.drawable.ic_outline_cloud_24,
            ),
            NgExpandableActionMenuItem(
                itemId = PICKER_ACTION_IMPORT_QR,
                titleRes = R.string.import_by_qr_code,
                iconRes = R.drawable.ic_scan,
            ),
            NgExpandableActionMenuItem(
                itemId = PICKER_ACTION_IMPORT_DEFAULT,
                titleRes = R.string.import_default_rule,
                iconRes = R.drawable.ic_restore,
            ),
            NgExpandableActionMenuItem(
                itemId = PICKER_ACTION_HELP,
                titleRes = R.string.help,
                iconRes = R.drawable.ic_help,
                dividerBefore = true,
            ),
        )
    }
    NgCompactEditorDialog(
        title = stringResource(R.string.txt_toc_rule),
        modifier = Modifier.fillMaxSize(),
        titleFontSize = 20.sp,
        titleFontWeight = FontWeight.Normal,
        titleAction = {
            PickerHeaderIcon(
                iconRes = R.drawable.ic_add,
                contentDescription = stringResource(R.string.add),
                onClick = onAdd,
            )
            Box {
                PickerHeaderIcon(
                    iconRes = R.drawable.ic_grid_menu,
                    contentDescription = stringResource(R.string.menu),
                    highlighted = topMenuState.expanded,
                    onClick = topMenuState::onAnchorClick,
                )
                NgExpandableActionMenu(
                    expanded = topMenuState.expanded,
                    onDismissRequest = topMenuState::onDismissRequest,
                    items = topMenuItems,
                    variant = NgExpandableActionMenuVariant.SIDE_SLIDE,
                    properties = PopupProperties(focusable = true, clippingEnabled = false),
                    onItemClick = { item ->
                        topMenuState.close()
                        when (item.itemId) {
                            PICKER_ACTION_IMPORT_LOCAL -> onImportLocal()
                            PICKER_ACTION_IMPORT_ONLINE -> onImportOnline()
                            PICKER_ACTION_IMPORT_QR -> onImportQr()
                            PICKER_ACTION_IMPORT_DEFAULT -> onImportDefault()
                            PICKER_ACTION_HELP -> onHelp()
                        }
                    },
                )
            }
        },
    ) {
        TxtTocRulePickerList(
            rules = rules,
            selectedName = selectedName,
            onSelect = onSelect,
            onEdit = onEdit,
            onToggleEnabled = onToggleEnabled,
            onMoveToTop = onMoveToTop,
            onMoveToBottom = onMoveToBottom,
            onDelete = onDelete,
            onReorder = onReorder,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        Spacer(Modifier.height(8.dp))
        NgFormActionRow {
            NgFormActionButton(
                text = stringResource(R.string.cancel),
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            NgFormActionButton(
                text = stringResource(R.string.ok),
                onClick = onConfirm,
                enabled = rules.any { it.name == selectedName },
                modifier = Modifier.weight(1f),
                variant = NgButtonVariant.PRIMARY,
            )
        }
    }
}

@Composable
private fun PickerHeaderIcon(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = Color(
                if (highlighted) NgTheme.colors.primary else NgTheme.colors.onSurface
            ),
        )
    }
}

@Composable
private fun TxtTocRulePickerList(
    rules: List<TxtTocRule>,
    selectedName: String?,
    onSelect: (TxtTocRule) -> Unit,
    onEdit: (TxtTocRule) -> Unit,
    onToggleEnabled: (TxtTocRule, Boolean) -> Unit,
    onMoveToTop: (TxtTocRule) -> Unit,
    onMoveToBottom: (TxtTocRule) -> Unit,
    onDelete: (TxtTocRule) -> Unit,
    onReorder: (List<TxtTocRule>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var orderedRules by remember(rules) { mutableStateOf(rules) }
    val reorderState = rememberNgLazyReorderState(
        onMove = { from, to ->
            if (from in orderedRules.indices && to in orderedRules.indices) {
                orderedRules = orderedRules.toMutableList().apply {
                    add(to, removeAt(from))
                }
            }
        },
        onFinished = { onReorder(orderedRules) },
    )
    NgManagementDrawerPanel(
        modifier = modifier,
        variant = NgManagementDrawerPanelVariant.COMPACT,
    ) {
        if (orderedRules.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.empty),
                    modifier = Modifier.padding(24.dp),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = reorderState.listState,
            ) {
                itemsIndexed(
                    items = orderedRules,
                    key = { _, rule -> rule.id },
                ) { index, rule ->
                    TxtTocRulePickerRow(
                        rule = rule,
                        selected = rule.name == selectedName,
                        showDivider = index != orderedRules.lastIndex,
                        onSelect = { onSelect(rule) },
                        onEdit = { onEdit(rule) },
                        onToggleEnabled = { onToggleEnabled(rule, it) },
                        onMoveToTop = { onMoveToTop(rule) },
                        onMoveToBottom = { onMoveToBottom(rule) },
                        onDelete = { onDelete(rule) },
                        modifier = Modifier.ngDraggedItem(reorderState, rule.id),
                        bodyDragModifier = Modifier.ngReorderAfterLongPress(
                            state = reorderState,
                            key = rule.id,
                            enabled = true,
                            contentDescription = stringResource(R.string.sort),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun TxtTocRulePickerRow(
    rule: TxtTocRule,
    selected: Boolean,
    showDivider: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    bodyDragModifier: Modifier,
) {
    val menuState = remember(rule.id) { NgPopupToggleState() }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .background(
                    if (selected) {
                        Color(NgTheme.colors.selectedContainer).copy(alpha = 0.22f)
                    } else {
                        Color.Transparent
                    }
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .clickable(role = Role.RadioButton, onClick = onSelect),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .border(
                            width = 1.5.dp,
                            color = Color(
                                if (selected) NgTheme.colors.primary else NgTheme.colors.outline
                            ),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(NgTheme.colors.primary)),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(bodyDragModifier)
                    .clickable(role = Role.RadioButton, onClick = onSelect)
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    text = rule.name,
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                rule.example?.takeIf(String::isNotBlank)?.let { example ->
                    Text(
                        text = example,
                        modifier = Modifier.padding(top = 2.dp),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            NgSwitchControl(
                checked = rule.enable,
                onCheckedChange = onToggleEnabled,
                modifier = Modifier.padding(horizontal = 2.dp),
                variant = NgSwitchControlVariant.COMPACT,
            )
            PickerHeaderIcon(
                iconRes = R.drawable.ic_settings,
                contentDescription = stringResource(R.string.edit),
                onClick = onEdit,
            )
            Box {
                PickerHeaderIcon(
                    iconRes = R.drawable.ic_more_vert,
                    contentDescription = stringResource(R.string.menu),
                    highlighted = menuState.expanded,
                    onClick = menuState::onAnchorClick,
                )
                NgExpandableActionMenu(
                    expanded = menuState.expanded,
                    onDismissRequest = menuState::onDismissRequest,
                    items = listOf(
                        NgExpandableActionMenuItem(
                            itemId = PICKER_ROW_TOP,
                            titleRes = R.string.to_top,
                            iconRes = R.drawable.ic_arrow_drop_up,
                        ),
                        NgExpandableActionMenuItem(
                            itemId = PICKER_ROW_BOTTOM,
                            titleRes = R.string.to_bottom,
                            iconRes = R.drawable.ic_arrow_down,
                        ),
                        NgExpandableActionMenuItem(
                            itemId = PICKER_ROW_DELETE,
                            titleRes = R.string.delete,
                            iconRes = R.drawable.ic_book_info_delete,
                            dividerBefore = true,
                            danger = true,
                        ),
                    ),
                    onItemClick = { item ->
                        menuState.close()
                        when (item.itemId) {
                            PICKER_ROW_TOP -> onMoveToTop()
                            PICKER_ROW_BOTTOM -> onMoveToBottom()
                            PICKER_ROW_DELETE -> onDelete()
                        }
                    },
                )
            }
            Spacer(Modifier.width(2.dp))
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 48.dp, end = 10.dp),
                thickness = 0.6.dp,
                color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.24f),
            )
        }
    }
}
