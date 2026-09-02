package io.legado.app.ui.config

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.help.DirectLinkUpload
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgCompactEditorDialog
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogDivider
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgDialogValueRow
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.components.compose.NgFormMultilineField
import io.legado.app.ui.design.components.compose.NgFormMultilineFieldVariant
import io.legado.app.ui.design.components.compose.NgFormSwitchSettingRow
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getClipText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi

/** 直链上传规则编辑器；规则存储、测试请求和默认规则来源保持原实现。 */
class DirectLinkUploadConfig : BaseComposeDialogFragment() {

    private var rule by mutableStateOf(DirectLinkUpload.getRule())
    private var showDefaultRules by mutableStateOf(false)
    private var testResult by mutableStateOf<String?>(null)
    private var testing by mutableStateOf(false)

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        (view as ComposeView).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    DirectLinkUploadEditorDialogContent(
                        rule = rule,
                        testing = testing,
                        onRuleChange = { rule = it },
                        onCopy = ::copyRule,
                        onPaste = ::pasteRule,
                        onImportDefault = { showDefaultRules = true },
                        onTest = ::testRule,
                        onCancel = { dismissAllowingStateLoss() },
                        onSave = ::saveRule,
                    )
                    if (showDefaultRules) {
                        DirectLinkDefaultRuleDialog(
                            rules = DirectLinkUpload.defaultRules,
                            onDismiss = { showDefaultRules = false },
                            onSelect = {
                                rule = it.copy()
                                showDefaultRules = false
                            },
                        )
                    }
                    testResult?.let { result ->
                        DirectLinkTestResultDialog(
                            result = result,
                            onDismiss = { testResult = null },
                            onCopy = { requireContext().sendToClip(result) },
                        )
                    }
                }
            }
        }
    }

    private fun copyRule() {
        validatedRule()?.let { requireContext().sendToClip(GSON.toJson(it)) }
    }

    private fun pasteRule() {
        runCatching {
            val text = requireContext().getClipText()?.toString()
                ?.takeIf(String::isNotBlank)
                ?: error("剪贴板为空")
            GSON.fromJsonObject<DirectLinkUpload.Rule>(text).getOrThrow()
        }.onSuccess {
            rule = it
        }.onFailure {
            toastOnUi("剪贴板为空或格式不对")
        }
    }

    private fun saveRule() {
        validatedRule()?.let {
            DirectLinkUpload.putConfig(it)
            dismissAllowingStateLoss()
        }
    }

    private fun testRule() {
        if (testing) return
        val testingRule = validatedRule() ?: return
        testing = true
        execute {
            DirectLinkUpload.upLoad("test.json", "{}", "application/json", testingRule)
        }.onError {
            testResult = it.localizedMessage ?: "ERROR"
        }.onSuccess {
            testResult = it
        }.onFinally {
            testing = false
        }
    }

    private fun validatedRule(): DirectLinkUpload.Rule? {
        if (rule.uploadUrl.isBlank()) {
            toastOnUi("上传Url不能为空")
            return null
        }
        if (rule.downloadUrlRule.isBlank()) {
            toastOnUi("下载Url规则不能为空")
            return null
        }
        if (rule.summary.isBlank()) {
            toastOnUi("注释不能为空")
            return null
        }
        return rule.copy()
    }
}

@Composable
private fun DirectLinkUploadEditorDialogContent(
    rule: DirectLinkUpload.Rule,
    testing: Boolean,
    onRuleChange: (DirectLinkUpload.Rule) -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onImportDefault: () -> Unit,
    onTest: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    NgCompactEditorDialog(
        title = stringResource(R.string.direct_link_upload_config),
        titleFontSize = 20.sp,
        titleLineHeight = 24.sp,
        titleFontWeight = FontWeight.Medium,
        titleAction = {
            DirectLinkHeaderAction(
                iconRes = R.drawable.ic_copy,
                iconSize = 20.dp,
                description = stringResource(R.string.copy_rule),
                onClick = onCopy,
            )
            DirectLinkHeaderAction(
                iconRes = R.drawable.ic_paste,
                iconSize = 16.dp,
                description = stringResource(R.string.paste_rule),
                onClick = onPaste,
            )
            DirectLinkHeaderAction(
                iconRes = R.drawable.ic_import,
                iconSize = 19.dp,
                description = stringResource(R.string.import_default_rule),
                onClick = onImportDefault,
            )
        },
    ) {
        val maxFormHeight = (LocalConfiguration.current.screenHeightDp.dp - 250.dp)
            .coerceIn(320.dp, 500.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxFormHeight)
                .padding(start = 4.dp, top = 12.dp, end = 4.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NgFormMultilineField(
                value = rule.uploadUrl,
                onValueChange = { onRuleChange(rule.copy(uploadUrl = it)) },
                label = stringResource(R.string.upload_url),
                minHeight = 82.dp,
                maxHeight = 116.dp,
                minLines = 3,
                maxLines = 5,
                variant = NgFormMultilineFieldVariant.DIALOG_UNDERLINE,
            )
            NgFormMultilineField(
                value = rule.downloadUrlRule,
                onValueChange = { onRuleChange(rule.copy(downloadUrlRule = it)) },
                label = stringResource(R.string.download_url_rule),
                minHeight = 98.dp,
                maxHeight = 142.dp,
                minLines = 4,
                maxLines = 6,
                variant = NgFormMultilineFieldVariant.DIALOG_UNDERLINE,
            )
            NgFormField(
                label = stringResource(R.string.summary),
                value = rule.summary,
                onValueChange = { onRuleChange(rule.copy(summary = it)) },
                variant = NgFormFieldVariant.LABELED_UNDERLINE,
            )
            NgFormSwitchSettingRow(
                title = stringResource(R.string.is_compress),
                checked = rule.compress,
                onCheckedChange = { onRuleChange(rule.copy(compress = it)) },
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NgFormActionButton(
                text = stringResource(R.string.test),
                onClick = onTest,
                enabled = !testing,
                appearance = NgFormActionButtonAppearance.DIALOG,
            )
            Spacer(Modifier.weight(1f))
            NgFormActionButton(
                text = stringResource(R.string.cancel),
                onClick = onCancel,
                appearance = NgFormActionButtonAppearance.DIALOG,
            )
            Spacer(Modifier.size(10.dp))
            NgFormActionButton(
                text = stringResource(R.string.save),
                onClick = onSave,
                variant = NgButtonVariant.PRIMARY,
                appearance = NgFormActionButtonAppearance.DIALOG,
            )
        }
    }
}

@Composable
private fun DirectLinkHeaderAction(
    iconRes: Int,
    iconSize: Dp,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = Color(NgTheme.colors.onSurface),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun DirectLinkDefaultRuleDialog(
    rules: List<DirectLinkUpload.Rule>,
    onDismiss: () -> Unit,
    onSelect: (DirectLinkUpload.Rule) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.import_default_rule),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = 520.dp),
            variant = NgDialogVariant.STANDARD,
            titleFontWeight = FontWeight.Medium,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    secondary = true,
                )
            },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                rules.forEachIndexed { index, defaultRule ->
                    NgDialogValueRow(
                        title = defaultRule.summary,
                        value = "",
                        onClick = { onSelect(defaultRule) },
                    )
                    if (index < rules.lastIndex) NgDialogDivider()
                }
            }
        }
    }
}

@Composable
private fun DirectLinkTestResultDialog(
    result: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.direct_link_upload_test_result),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = 520.dp),
            variant = NgDialogVariant.LONG_CONTENT,
            titleFontWeight = FontWeight.Medium,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.copy_text),
                    onClick = onCopy,
                )
                NgDialogTextActionButton(
                    text = stringResource(R.string.ok),
                    onClick = onDismiss,
                )
            },
        ) {
            SelectionContainer {
                Text(
                    text = result,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}
