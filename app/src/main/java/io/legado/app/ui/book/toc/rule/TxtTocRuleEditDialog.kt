package io.legado.app.ui.book.toc.rule

import android.app.Application
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.exception.NoStackTraceException
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgCompactEditorDialog
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.widget.code.CodeView
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.ui.widget.code.addJsonPattern
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getClipText
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import kotlin.math.min
import kotlin.math.roundToInt

class TxtTocRuleEditDialog() : BaseComposeDialogFragment() {

    constructor(id: Long?) : this() {
        id ?: return
        arguments = Bundle().apply { putLong(ARG_ID, id) }
    }

    private val viewModel by viewModels<ViewModel>()
    private val callback get() = (parentFragment as? Callback) ?: activity as? Callback
    private var rule by mutableStateOf(TxtTocRule(), referentialEqualityPolicy())
    private var originalFields = RuleFields()
    private var loaded by mutableStateOf(false)
    private var showDiscardConfirmation by mutableStateOf(false)

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
                    TxtTocRuleEditorDialogContent(
                        rule = rule,
                        loading = !loaded,
                        isEditing = isEditing,
                        onRuleChange = { rule = it },
                        onCancel = ::requestDismiss,
                        onCopy = { requireContext().sendToClip(GSON.toJson(rule)) },
                        onPaste = {
                            viewModel.pasteRule { pasted ->
                                rule = rule.copy(
                                    name = pasted.name,
                                    rule = pasted.rule,
                                    replacement = pasted.replacement,
                                    example = pasted.example,
                                )
                            }
                        },
                        onSave = ::save,
                    )
                    if (showDiscardConfirmation) {
                        TxtTocRuleDiscardDialog(
                            onContinueEditing = { showDiscardConfirmation = false },
                            onDiscard = ::dismissDirectly,
                        )
                    }
                }
            }
        }
        viewModel.initData(arguments?.takeIf { it.containsKey(ARG_ID) }?.getLong(ARG_ID)) {
            val initialRule = it?.copy() ?: TxtTocRule()
            originalFields = initialRule.fields()
            rule = initialRule
            loaded = true
        }
    }

    private fun requestDismiss() {
        if (!loaded || rule.fields() == originalFields) {
            dismissDirectly()
        } else {
            showDiscardConfirmation = true
        }
    }

    private fun dismissDirectly() {
        super.dismissAllowingStateLoss()
    }

    private fun save() {
        if (!checkValid(rule)) return
        callback?.saveTxtTocRule(rule)
        dismissDirectly()
    }

    private fun checkValid(rule: TxtTocRule): Boolean {
        if (rule.name.isEmpty()) {
            toastOnUi("名称不能为空")
            return false
        }
        try {
            Pattern.compile(rule.rule, Pattern.MULTILINE)
        } catch (ex: PatternSyntaxException) {
            AppLog.put("正则语法错误或不支持(txt)：${ex.localizedMessage}", ex, true)
            return false
        }
        return true
    }

    class ViewModel(application: Application) : BaseViewModel(application) {

        private var tocRule: TxtTocRule? = null

        fun initData(id: Long?, finally: (TxtTocRule?) -> Unit) {
            tocRule?.let {
                finally(it)
                return
            }
            execute {
                if (id != null) tocRule = appDb.txtTocRuleDao.get(id)
            }.onFinally {
                finally(tocRule)
            }
        }

        fun pasteRule(success: (TxtTocRule) -> Unit) {
            execute(context = Dispatchers.Main) {
                val text = context.getClipText()
                if (text.isNullOrBlank()) {
                    throw NoStackTraceException("剪贴板为空")
                }
                GSON.fromJsonObject<TxtTocRule>(text).getOrNull()
                    ?: throw NoStackTraceException("格式不对")
            }.onSuccess {
                success(it)
            }.onError {
                context.toastOnUi(it.localizedMessage ?: "Error")
                it.printOnDebug()
            }
        }
    }

    interface Callback {
        fun saveTxtTocRule(txtTocRule: TxtTocRule)
    }

    companion object {
        private const val ARG_ID = "id"
    }
}

private data class RuleFields(
    val name: String = "",
    val rule: String = "",
    val replacement: String = "",
    val example: String? = null,
)

private fun TxtTocRule.fields() = RuleFields(name, rule, replacement, example)

@Composable
private fun TxtTocRuleEditorDialogContent(
    rule: TxtTocRule,
    loading: Boolean,
    isEditing: Boolean,
    onRuleChange: (TxtTocRule) -> Unit,
    onCancel: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onSave: () -> Unit,
) {
    NgCompactEditorDialog(
        title = stringResource(if (isEditing) R.string.edit_rule else R.string.new_rule),
        titleFontSize = 20.sp,
        titleLineHeight = 24.sp,
        titleFontWeight = FontWeight.Medium,
        titleAction = {
            TxtTocRuleHeaderAction(
                iconRes = R.drawable.ic_copy,
                iconSize = 20.dp,
                contentDescription = stringResource(R.string.copy_rule),
                enabled = !loading,
                onClick = onCopy,
            )
            TxtTocRuleHeaderAction(
                iconRes = R.drawable.ic_paste,
                iconSize = 16.dp,
                contentDescription = stringResource(R.string.paste_rule),
                enabled = !loading,
                onClick = onPaste,
            )
        },
    ) {
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
                    .padding(start = 4.dp, top = 12.dp, end = 4.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TxtTocRuleUnderlineField(
                    label = stringResource(R.string.name),
                    value = rule.name,
                    onValueChange = { onRuleChange(rule.copy(name = it)) },
                    fieldHeight = 38.dp,
                    singleLine = true,
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                TxtTocRuleUnderlineField(
                    label = stringResource(R.string.regex),
                    value = rule.rule,
                    onValueChange = { onRuleChange(rule.copy(rule = it)) },
                    fieldHeight = 100.dp,
                    singleLine = false,
                    maxLines = 4,
                )
                TxtTocRuleCodeField(
                    label = stringResource(R.string.replace_to_js),
                    value = rule.replacement,
                    onValueChange = { onRuleChange(rule.copy(replacement = it)) },
                )
                TxtTocRuleUnderlineField(
                    label = stringResource(R.string.example),
                    value = rule.example.orEmpty(),
                    onValueChange = { onRuleChange(rule.copy(example = it)) },
                    fieldHeight = 42.dp,
                    singleLine = false,
                    maxLines = 2,
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
private fun TxtTocRuleHeaderAction(
    iconRes: Int,
    iconSize: androidx.compose.ui.unit.Dp,
    contentDescription: String,
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
            contentDescription = contentDescription,
            tint = Color(NgTheme.colors.onSurface).copy(
                alpha = if (enabled) 1f else 0.38f,
            ),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun TxtTocRuleUnderlineField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    fieldHeight: androidx.compose.ui.unit.Dp,
    singleLine: Boolean,
    maxLines: Int,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colors = NgTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = Color(colors.primary),
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Normal,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(fieldHeight),
            singleLine = singleLine,
            maxLines = maxLines,
            textStyle = TextStyle(
                color = Color(colors.onSurface),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Normal,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            keyboardOptions = keyboardOptions,
            cursorBrush = SolidColor(Color(colors.primary)),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(fieldHeight)
                        .drawBehind {
                            val strokeWidth = (if (focused) 1.5.dp else 1.dp).toPx()
                            val y = size.height - strokeWidth / 2f
                            drawLine(
                                color = Color(
                                    if (focused) colors.primary else colors.outline
                                ),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = strokeWidth,
                            )
                        }
                        .padding(
                            start = 2.dp,
                            top = if (singleLine) 0.dp else 5.dp,
                            end = 2.dp,
                            bottom = if (singleLine) 0.dp else 5.dp,
                        ),
                    contentAlignment = if (singleLine) {
                        Alignment.CenterStart
                    } else {
                        Alignment.TopStart
                    },
                ) {
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun TxtTocRuleCodeField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
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
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
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
                    CodeView(context).apply {
                        background = null
                        gravity = Gravity.TOP or Gravity.START
                        includeFontPadding = false
                        setPadding(horizontal, vertical, horizontal, vertical)
                        setTextColor(Color(colors.onSurface).toArgb())
                        setHintTextColor(Color(colors.onSurfaceVariant).toArgb())
                        textSize = 15f
                        minLines = 1
                        maxLines = 3
                        setHorizontallyScrolling(false)
                        addJsonPattern()
                        addJsPattern()
                        onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                            focused = hasFocus
                        }
                        doAfterTextChanged { currentOnValueChange(it?.toString().orEmpty()) }
                    }
                },
                update = { codeView ->
                    if (codeView.text?.toString() != value) {
                        val oldSelection = codeView.selectionStart.coerceAtLeast(0)
                        if (value.isEmpty()) {
                            codeView.setText("")
                        } else {
                            codeView.setTextHighlighted(value)
                        }
                        codeView.setSelection(min(oldSelection, value.length))
                    }
                },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
private fun TxtTocRuleDiscardDialog(
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
