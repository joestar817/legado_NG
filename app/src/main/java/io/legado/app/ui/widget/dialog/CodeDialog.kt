package io.legado.app.ui.widget.dialog

import android.graphics.Color.TRANSPARENT
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.help.IntentData
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.design.components.compose.NgCodeHighlightMode
import io.legado.app.ui.design.components.compose.rememberNgCodeVisualTransformation
import io.legado.app.ui.design.components.compose.rememberNgHighlightedCode
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.exportTextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CodeDialog() : DialogFragment() {

    constructor(
        code: String,
        disableEdit: Boolean = true,
        requestId: String? = null,
        title: String? = null,
        highlightMode: HighlightMode = HighlightMode.Default,
        exportCode: String? = null,
        exportFilePrefix: String = "legado-code"
    ) : this() {
        arguments = Bundle().apply {
            putBoolean("disableEdit", disableEdit)
            putString("code", IntentData.put(code))
            exportCode?.let { putString("exportCode", IntentData.put(it)) }
            putString("exportFilePrefix", exportFilePrefix)
            putString("requestId", requestId)
            putString("title", title)
            putString("highlightMode", highlightMode.name)
        }
    }

    private var originalContent = ""
    private var exportOverrideContent: String? = null
    private var editorValue by mutableStateOf(TextFieldValue())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setBackgroundColor(TRANSPARENT)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        originalContent = arguments?.getString("code")
            ?.let { IntentData.get<String>(it) }
            .orEmpty()
        editorValue = TextFieldValue(displayContent(originalContent))
        (view as ComposeView).setContent {
            NgAppTheme(updateSystemBars = false) {
                CodeDialogContent(
                    title = arguments?.getString("title")
                        ?.takeIf(String::isNotBlank)
                        ?: if (isReadOnly()) "code view" else "code edit",
                    value = editorValue,
                    readOnly = isReadOnly(),
                    highlightMode = arguments?.getString("highlightMode")
                        ?.let { runCatching { HighlightMode.valueOf(it) }.getOrNull() }
                        ?: HighlightMode.Default,
                    onValueChange = { editorValue = it },
                    onSave = ::save,
                    onExport = ::export,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(height = ngDialogMaxHeight())
    }

    private fun isReadOnly(): Boolean = arguments?.getBoolean("disableEdit") == true

    private fun save() {
        val requestId = arguments?.getString("requestId")
        (parentFragment as? Callback)?.onCodeSave(editorValue.text, requestId)
            ?: (activity as? Callback)?.onCodeSave(editorValue.text, requestId)
        dismiss()
    }

    private fun export() {
        lifecycleScope.launch {
            val content = withContext(Dispatchers.Default) { exportContent() }
            if (isAdded) {
                requireContext().exportTextContent(
                    content,
                    filePrefix = arguments?.getString("exportFilePrefix") ?: "legado-code",
                )
            }
        }
    }

    private fun displayContent(content: String): String {
        if (!isReadOnly() || content.length <= READ_ONLY_PREVIEW_MAX_LENGTH) return content
        return content.take(READ_ONLY_PREVIEW_MAX_LENGTH) +
            getString(
                R.string.large_text_preview_suffix,
                READ_ONLY_PREVIEW_MAX_LENGTH,
                content.length,
            )
    }

    private fun exportContent(): String {
        val requestId = arguments?.getString("requestId")
        (parentFragment as? ExportCallback)?.onCodeExport(requestId)?.let { return it }
        (activity as? ExportCallback)?.onCodeExport(requestId)?.let { return it }
        exportOverrideContent?.let { return it }
        arguments?.getString("exportCode")?.let {
            return IntentData.get<String>(it).orEmpty().also { content ->
                exportOverrideContent = content
            }
        }
        originalContent.takeIf(String::isNotEmpty)?.let { return it }
        return editorValue.text
    }

    interface Callback {
        fun onCodeSave(code: String, requestId: String?)
    }

    interface ExportCallback {
        fun onCodeExport(requestId: String?): String?
    }

    enum class HighlightMode {
        Default,
        DebugLog,
    }

    private companion object {
        const val READ_ONLY_PREVIEW_MAX_LENGTH = 48 * 1024
    }
}

@Composable
private fun CodeDialogContent(
    title: String,
    value: TextFieldValue,
    readOnly: Boolean,
    highlightMode: CodeDialog.HighlightMode,
    onValueChange: (TextFieldValue) -> Unit,
    onSave: () -> Unit,
    onExport: () -> Unit,
) {
    val context = LocalContext.current
    val accentColor = Color(context.accentColor)
    val dialogShape = RoundedCornerShape(
        dimensionResource(R.dimen.ng_dialog_radius) + 2.dp
    )
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clip(dialogShape),
        color = colorResource(R.color.ng_surface),
        shape = dialogShape,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(start = 16.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = colorResource(R.color.ng_on_surface),
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (readOnly) {
                    TextButton(onClick = onExport) {
                        Text(
                            text = stringResource(R.string.export_content),
                            color = accentColor,
                            fontSize = 14.sp,
                        )
                    }
                } else {
                    IconButton(onClick = onSave) {
                        Icon(
                            painter = painterResource(R.drawable.ic_save),
                            contentDescription = stringResource(R.string.action_save),
                            tint = colorResource(R.color.ng_on_surface),
                        )
                    }
                }
            }
            val sectionShape = RoundedCornerShape(dimensionResource(R.dimen.ng_radius_l))
            val dialogPadding = dimensionResource(R.dimen.ng_dialog_padding)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(
                        start = dialogPadding,
                        end = dialogPadding,
                        bottom = dialogPadding,
                    )
                    .background(colorResource(R.color.ng_surface_panel), sectionShape)
                    .padding(8.dp),
            ) {
                if (readOnly) {
                    val vertical = rememberScrollState()
                    val highlighted = rememberNgHighlightedCode(
                        text = value.text,
                        mode = highlightMode.toNgMode(),
                    )
                    SelectionContainer {
                        Text(
                            text = highlighted,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(vertical),
                            color = colorResource(R.color.ng_on_surface),
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                        )
                    }
                } else {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxSize(),
                        textStyle = TextStyle(
                            color = colorResource(R.color.ng_on_surface),
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                        ),
                        cursorBrush = SolidColor(accentColor),
                        visualTransformation = rememberNgCodeVisualTransformation(
                            mode = highlightMode.toNgMode(),
                        ),
                    )
                }
            }
        }
    }
}

private fun CodeDialog.HighlightMode.toNgMode(): NgCodeHighlightMode {
    return when (this) {
        CodeDialog.HighlightMode.Default -> NgCodeHighlightMode.DEFAULT
        CodeDialog.HighlightMode.DebugLog -> NgCodeHighlightMode.DEBUG_LOG
    }
}
