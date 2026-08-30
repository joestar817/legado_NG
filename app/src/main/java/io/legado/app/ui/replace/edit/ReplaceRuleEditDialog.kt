package io.legado.app.ui.replace.edit

import android.app.Activity.RESULT_OK
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.lib.theme.view.ThemeEditText
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgCompactEditorDialog
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.utils.GSON
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showHelp
import io.legado.app.utils.toastOnUi
import kotlin.math.min
import kotlin.math.roundToInt

class ReplaceRuleEditDialog() : BaseComposeDialogFragment() {

    constructor(id: Long) : this() {
        arguments = Bundle().apply { putLong(ARG_ID, id) }
    }

    private val viewModel by viewModels<ReplaceEditViewModel>()
    private var baseRule by mutableStateOf(ReplaceRule(), referentialEqualityPolicy())
    private var draft by mutableStateOf(ReplaceRuleDraft())
    private var originalDraft = ReplaceRuleDraft()
    private var loaded by mutableStateOf(false)
    private var showDiscardConfirmation by mutableStateOf(false)
    private var focusedEditText: EditText? = null

    private val textEditLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val view = focusedEditText
        if (view == null) {
            toastOnUi(R.string.focus_lost_on_textbox)
            return@registerForActivityResult
        }
        view.requestFocus()
        result.data?.getStringExtra("text")?.let(view::setText)
        result.data?.getIntExtra("cursorPosition", -1)
            ?.takeIf { it in 0..view.text.length }
            ?.let(view::setSelection)
    }

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow()
        dialog?.setCanceledOnTouchOutside(false)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        isCancelable = false
        val isEditing = arguments?.containsKey(ARG_ID) == true
        (view as ComposeView).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    BackHandler { requestDismiss() }
                    ReplaceRuleEditorDialogContent(
                        draft = draft,
                        loading = !loaded,
                        isEditing = isEditing,
                        onDraftChange = { draft = it },
                        onFocused = { focusedEditText = it },
                        onFullEdit = ::openFullEdit,
                        onCopy = {
                            requireContext().sendToClip(GSON.toJson(draft.toRule(baseRule)))
                        },
                        onPaste = {
                            viewModel.pasteRule { pasted -> draft = pasted.toDraft() }
                        },
                        onRegexHelp = { showHelp("regexHelp") },
                        onCancel = ::requestDismiss,
                        onSave = ::save,
                    )
                    if (showDiscardConfirmation) {
                        ReplaceRuleDiscardDialog(
                            onContinueEditing = { showDiscardConfirmation = false },
                            onDiscard = ::dismissDirectly,
                        )
                    }
                }
            }
        }
        viewModel.initData(
            id = arguments?.getLong(ARG_ID, -1L) ?: -1L,
            pattern = arguments?.getString(ARG_PATTERN),
            isRegex = arguments?.getBoolean(ARG_IS_REGEX, false) ?: false,
            scope = arguments?.getString(ARG_SCOPE),
        ) { initial ->
            baseRule = initial.copy()
            originalDraft = initial.toDraft()
            draft = originalDraft
            loaded = true
        }
    }

    private fun openFullEdit() {
        val view = focusedEditText
        if (view == null) {
            toastOnUi(R.string.please_focus_cursor_on_textbox)
            return
        }
        textEditLauncher.launch(
            android.content.Intent(requireActivity(), CodeEditActivity::class.java).apply {
                putExtra("text", view.text.toString())
                putExtra("title", view.tag?.toString().orEmpty())
                putExtra("cursorPosition", view.selectionStart)
            }
        )
    }

    private fun requestDismiss() {
        if (!loaded || draft == originalDraft) {
            dismissDirectly()
        } else {
            showDiscardConfirmation = true
        }
    }

    private fun dismissDirectly() {
        super.dismissAllowingStateLoss()
    }

    private fun save() {
        viewModel.save(draft.toRule(baseRule)) {
            (parentFragment as? Callback)?.onReplaceRuleSaved()
                ?: (activity as? Callback)?.onReplaceRuleSaved()
            dismissDirectly()
        }
    }

    interface Callback {
        fun onReplaceRuleSaved()
    }

    companion object {
        private const val ARG_ID = "id"
        private const val ARG_PATTERN = "pattern"
        private const val ARG_IS_REGEX = "isRegex"
        private const val ARG_SCOPE = "scope"

        fun newRule(
            pattern: String? = null,
            isRegex: Boolean = false,
            scope: String? = null,
        ): ReplaceRuleEditDialog {
            return ReplaceRuleEditDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_PATTERN, pattern)
                    putBoolean(ARG_IS_REGEX, isRegex)
                    putString(ARG_SCOPE, scope)
                }
            }
        }
    }
}

private data class ReplaceRuleDraft(
    val name: String = "",
    val group: String = "",
    val pattern: String = "",
    val isRegex: Boolean = false,
    val replacement: String = "",
    val scopeTitle: Boolean = false,
    val scopeContent: Boolean = true,
    val scope: String = "",
    val excludeScope: String = "",
    val timeout: String = "3000",
)

private fun ReplaceRule.toDraft() = ReplaceRuleDraft(
    name = name,
    group = group.orEmpty(),
    pattern = pattern,
    isRegex = isRegex,
    replacement = replacement,
    scopeTitle = scopeTitle,
    scopeContent = scopeContent,
    scope = scope.orEmpty(),
    excludeScope = excludeScope.orEmpty(),
    timeout = timeoutMillisecond.toString(),
)

private fun ReplaceRuleDraft.toRule(base: ReplaceRule) = base.copy(
    name = name,
    group = group,
    pattern = pattern,
    isRegex = isRegex,
    replacement = replacement,
    scopeTitle = scopeTitle,
    scopeContent = scopeContent,
    scope = scope,
    excludeScope = excludeScope,
    timeoutMillisecond = timeout.ifBlank { "3000" }.toLongOrNull() ?: 3000L,
)

@Composable
private fun ReplaceRuleEditorDialogContent(
    draft: ReplaceRuleDraft,
    loading: Boolean,
    isEditing: Boolean,
    onDraftChange: (ReplaceRuleDraft) -> Unit,
    onFocused: (EditText) -> Unit,
    onFullEdit: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onRegexHelp: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    NgCompactEditorDialog(
        title = stringResource(if (isEditing) R.string.edit_rule else R.string.new_rule),
        titleFontSize = 20.sp,
        titleLineHeight = 24.sp,
        titleFontWeight = FontWeight.Medium,
        titleAction = {
            ReplaceRuleHeaderAction(
                iconRes = R.drawable.ic_code,
                iconSize = 20.dp,
                description = stringResource(R.string.edit_content),
                enabled = !loading,
                onClick = onFullEdit,
            )
            ReplaceRuleHeaderAction(
                iconRes = R.drawable.ic_copy,
                iconSize = 20.dp,
                description = stringResource(R.string.copy_rule),
                enabled = !loading,
                onClick = onCopy,
            )
            ReplaceRuleHeaderAction(
                iconRes = R.drawable.ic_paste,
                iconSize = 16.dp,
                description = stringResource(R.string.paste_rule),
                enabled = !loading,
                onClick = onPaste,
            )
        },
    ) {
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
        } else {
            val maxFormHeight = (LocalConfiguration.current.screenHeightDp.dp - 270.dp)
                .coerceIn(320.dp, 520.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxFormHeight)
                    .padding(start = 4.dp, top = 12.dp, end = 4.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReplaceRuleUnderlineField(
                    label = stringResource(R.string.replace_rule_summary),
                    value = draft.name,
                    onValueChange = { onDraftChange(draft.copy(name = it)) },
                    onFocused = onFocused,
                    fieldHeight = 38.dp,
                    singleLine = true,
                    maxLines = 1,
                )
                ReplaceRuleUnderlineField(
                    label = stringResource(R.string.group),
                    value = draft.group,
                    onValueChange = { onDraftChange(draft.copy(group = it)) },
                    onFocused = onFocused,
                    fieldHeight = 38.dp,
                    singleLine = true,
                    maxLines = 1,
                )
                ReplaceRuleUnderlineField(
                    label = stringResource(R.string.replace_rule),
                    value = draft.pattern,
                    onValueChange = { onDraftChange(draft.copy(pattern = it)) },
                    onFocused = onFocused,
                    fieldHeight = 72.dp,
                    singleLine = false,
                    maxLines = 3,
                )
                ReplaceRuleRegexOption(
                    checked = draft.isRegex,
                    onCheckedChange = { onDraftChange(draft.copy(isRegex = it)) },
                    onHelp = onRegexHelp,
                )
                ReplaceRuleUnderlineField(
                    label = stringResource(R.string.replace_to),
                    value = draft.replacement,
                    onValueChange = { onDraftChange(draft.copy(replacement = it)) },
                    onFocused = onFocused,
                    fieldHeight = 56.dp,
                    singleLine = false,
                    maxLines = 2,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReplaceRuleCheckOption(
                        checked = draft.scopeTitle,
                        label = stringResource(R.string.scope_title),
                        onCheckedChange = {
                            onDraftChange(draft.copy(scopeTitle = it))
                        },
                        modifier = Modifier.weight(1f),
                    )
                    ReplaceRuleCheckOption(
                        checked = draft.scopeContent,
                        label = stringResource(R.string.scope_content),
                        onCheckedChange = {
                            onDraftChange(draft.copy(scopeContent = it))
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                ReplaceRuleUnderlineField(
                    label = stringResource(R.string.replace_scope),
                    value = draft.scope,
                    onValueChange = { onDraftChange(draft.copy(scope = it)) },
                    onFocused = onFocused,
                    fieldHeight = 56.dp,
                    singleLine = false,
                    maxLines = 2,
                )
                ReplaceRuleUnderlineField(
                    label = stringResource(R.string.replace_exclude_scope),
                    value = draft.excludeScope,
                    onValueChange = { onDraftChange(draft.copy(excludeScope = it)) },
                    onFocused = onFocused,
                    fieldHeight = 56.dp,
                    singleLine = false,
                    maxLines = 2,
                )
                ReplaceRuleUnderlineField(
                    label = stringResource(R.string.timeout_millisecond),
                    value = draft.timeout,
                    onValueChange = { onDraftChange(draft.copy(timeout = it)) },
                    onFocused = onFocused,
                    fieldHeight = 38.dp,
                    singleLine = true,
                    maxLines = 1,
                    numberOnly = true,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, end = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NgFormActionButton(
                text = stringResource(R.string.cancel),
                onClick = onCancel,
                appearance = NgFormActionButtonAppearance.DIALOG,
            )
            Spacer(Modifier.size(10.dp))
            NgFormActionButton(
                text = stringResource(R.string.save),
                onClick = onSave,
                enabled = !loading,
                variant = NgButtonVariant.PRIMARY,
                appearance = NgFormActionButtonAppearance.DIALOG,
            )
        }
    }
}

@Composable
private fun ReplaceRuleHeaderAction(
    iconRes: Int,
    iconSize: Dp,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = Color(NgTheme.colors.onSurface).copy(
                alpha = if (enabled) 1f else 0.38f,
            ),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun ReplaceRuleUnderlineField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onFocused: (EditText) -> Unit,
    fieldHeight: Dp,
    singleLine: Boolean,
    maxLines: Int,
    numberOnly: Boolean = false,
) {
    val colors = NgTheme.colors
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    var focused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = Color(colors.primary),
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(fieldHeight)
                .drawBehind {
                    val strokeWidth = (if (focused) 1.5.dp else 1.dp).toPx()
                    val y = size.height - strokeWidth / 2f
                    drawLine(
                        color = Color(if (focused) colors.primary else colors.outline),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = strokeWidth,
                    )
                },
        ) {
            AndroidView(
                factory = { context ->
                    val horizontal = (2 * context.resources.displayMetrics.density).roundToInt()
                    val vertical = (5 * context.resources.displayMetrics.density).roundToInt()
                    ThemeEditText(context).apply {
                        tag = label
                        background = null
                        gravity = Gravity.TOP or Gravity.START
                        includeFontPadding = false
                        setPadding(horizontal, vertical, horizontal, vertical)
                        setTextColor(Color(colors.onSurface).toArgb())
                        setHintTextColor(Color(colors.onSurfaceVariant).toArgb())
                        textSize = 15f
                        isSingleLine = singleLine
                        this.maxLines = maxLines
                        setHorizontallyScrolling(false)
                        if (numberOnly) inputType = InputType.TYPE_CLASS_NUMBER
                        onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
                            focused = hasFocus
                            if (hasFocus) onFocused(view as EditText)
                        }
                        doAfterTextChanged {
                            currentOnValueChange(it?.toString().orEmpty())
                        }
                    }
                },
                update = { editText ->
                    editText.tag = label
                    if (editText.text?.toString() != value) {
                        val oldSelection = editText.selectionStart.coerceAtLeast(0)
                        editText.setText(value)
                        editText.setSelection(min(oldSelection, value.length))
                    }
                },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
private fun ReplaceRuleRegexOption(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onHelp: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReplaceRuleCheckOption(
            checked = checked,
            label = stringResource(R.string.use_regex),
            onCheckedChange = onCheckedChange,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clickable(onClick = onHelp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_help),
                contentDescription = stringResource(R.string.help),
                tint = Color(NgTheme.colors.onSurface),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ReplaceRuleCheckOption(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 4.dp),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReplaceRuleDiscardDialog(
    onContinueEditing: () -> Unit,
    onDiscard: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onContinueEditing) {
        NgDialog(
            title = stringResource(R.string.exit),
            variant = NgDialogVariant.CLASSIC_CONFIRMATION,
            titleFontWeight = FontWeight.Normal,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.continue_editing),
                    onClick = onContinueEditing,
                )
                NgDialogTextActionButton(
                    text = stringResource(R.string.discard_changes),
                    onClick = onDiscard,
                    danger = true,
                )
            },
        ) {
            Text(
                text = stringResource(R.string.exit_no_save),
                color = Color(NgTheme.colors.onSurface),
            )
        }
    }
}
