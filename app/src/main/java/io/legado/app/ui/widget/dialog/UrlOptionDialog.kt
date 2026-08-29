package io.legado.app.ui.widget.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgFormDensity
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormSelectField
import io.legado.app.ui.design.components.compose.NgFormSelectOption
import io.legado.app.ui.design.components.compose.NgFormSwitchRow
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.GSON
import io.legado.app.utils.setLayout

/** URL 参数编辑器的 Compose 宿主，保留原 Dialog 调用接口供各类源编辑页复用。 */
class UrlOptionDialog(
    context: Context,
    private val success: (String) -> Unit,
) : Dialog(context) {

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
        window?.setBackgroundDrawableResource(R.color.transparent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            ComposeView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setContent {
                    NgAppTheme(updateSystemBars = false) {
                        UrlOptionDialogContent(
                            onDismiss = ::dismiss,
                            onConfirm = { input ->
                                success(GSON.toJson(input.toUrlOption()))
                                dismiss()
                            },
                        )
                    }
                }
            }
        )
    }
}

private data class UrlOptionInput(
    val useWebView: Boolean = false,
    val method: String = "",
    val charset: String = "",
    val headers: String = "",
    val body: String = "",
    val type: String = "",
    val retry: String = "",
    val webJs: String = "",
    val js: String = "",
    val bodyJs: String = "",
    val dnsIp: String = "",
)

private fun UrlOptionInput.toUrlOption(): AnalyzeUrl.UrlOption {
    return AnalyzeUrl.UrlOption().apply {
        useWebView(this@toUrlOption.useWebView)
        setMethod(method)
        setCharset(charset)
        setHeaders(headers)
        setBody(body)
        setRetry(retry)
        setType(type)
        setWebJs(webJs)
        setJs(js)
        // 保留旧实现的实际行为：bodyJs 最终覆盖 js 字段。
        setJs(bodyJs)
        setDnsIp(dnsIp)
    }
}

@Composable
private fun UrlOptionDialogContent(
    onDismiss: () -> Unit,
    onConfirm: (UrlOptionInput) -> Unit,
) {
    var input by remember { mutableStateOf(UrlOptionInput()) }
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss,
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        NgDialog(
            title = stringResource(R.string.url_option),
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
            variant = NgDialogVariant.FORM_EDITOR,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.ok),
                    onClick = { onConfirm(input) },
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NgFormSwitchRow(
                    title = "useWebView",
                    checked = input.useWebView,
                    onCheckedChange = { input = input.copy(useWebView = it) },
                    density = NgFormDensity.COMPACT,
                )
                val arrow = painterResource(R.drawable.ic_arrow_drop_down)
                NgFormSelectField(
                    label = "method",
                    selectedValue = input.method,
                    options = listOf("POST", "GET").map { NgFormSelectOption(it, it) },
                    onValueChange = { input = input.copy(method = it) },
                    arrowIcon = arrow,
                    density = NgFormDensity.COMPACT,
                )
                NgFormSelectField(
                    label = "charset",
                    selectedValue = input.charset,
                    options = AppConst.charsets.map { NgFormSelectOption(it, it) },
                    onValueChange = { input = input.copy(charset = it) },
                    arrowIcon = arrow,
                    density = NgFormDensity.COMPACT,
                )
                UrlOptionField("headers", input.headers) { input = input.copy(headers = it) }
                UrlOptionField("body", input.body) { input = input.copy(body = it) }
                UrlOptionField("type", input.type) { input = input.copy(type = it) }
                UrlOptionField(
                    label = "retry",
                    value = input.retry,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                ) { input = input.copy(retry = it) }
                UrlOptionField("webJs", input.webJs) { input = input.copy(webJs = it) }
                UrlOptionField("js", input.js) { input = input.copy(js = it) }
                UrlOptionField("bodyJs", input.bodyJs) { input = input.copy(bodyJs = it) }
                UrlOptionField("dnsIp", input.dnsIp) { input = input.copy(dnsIp = it) }
            }
        }
    }
}

@Composable
private fun UrlOptionField(
    label: String,
    value: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onValueChange: (String) -> Unit,
) {
    NgFormField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        density = NgFormDensity.COMPACT,
        keyboardOptions = keyboardOptions,
    )
}
