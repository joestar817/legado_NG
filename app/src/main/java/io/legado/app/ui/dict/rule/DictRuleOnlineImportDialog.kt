package io.legado.app.ui.dict.rule

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.utils.ACache
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.splitNotBlank

class DictRuleOnlineImportDialog : BaseComposeDialogFragment() {

    private val callback get() = activity as? Callback
    private val cacheUrls = mutableStateListOf<String>()
    private lateinit var cache: ACache

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        cache = ACache.get(cacheDir = false)
        cacheUrls.addAll(
            cache.getAsString(CACHE_KEY)
                ?.splitNotBlank(",")
                .orEmpty()
        )
        (view as ComposeView).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    DictRuleOnlineImportDialogContent(
                        history = cacheUrls,
                        onDeleteHistory = ::deleteHistory,
                        onCancel = ::dismissAllowingStateLoss,
                        onConfirm = ::confirm,
                    )
                }
            }
        }
    }

    private fun deleteHistory(url: String) {
        cacheUrls.remove(url)
        saveHistory()
    }

    private fun confirm(text: String) {
        if (text.isAbsUrl() && text !in cacheUrls) {
            cacheUrls.add(0, text)
            saveHistory()
        }
        dismissAllowingStateLoss()
        callback?.onDictRuleOnlineImportConfirmed(text)
    }

    private fun saveHistory() {
        cache.put(CACHE_KEY, cacheUrls.joinToString(","))
    }

    interface Callback {
        fun onDictRuleOnlineImportConfirmed(text: String)
    }

    companion object {
        private const val CACHE_KEY = "dictRuleUrls"
    }
}

@Composable
private fun DictRuleOnlineImportDialogContent(
    history: List<String>,
    onDeleteHistory: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf("") }
    NgDialog(
        title = stringResource(R.string.import_on_line),
        variant = NgDialogVariant.STANDARD,
        titleFontWeight = FontWeight.Normal,
        actions = {
            NgDialogTextActionButton(
                text = stringResource(R.string.cancel),
                onClick = onCancel,
            )
            NgDialogTextActionButton(
                text = stringResource(R.string.ok),
                onClick = { onConfirm(value) },
            )
        },
    ) {
        DictRuleUrlHistoryField(
            value = value,
            history = history,
            onValueChange = { value = it },
            onDeleteHistory = onDeleteHistory,
            onConfirm = { onConfirm(value) },
        )
    }
}

@Composable
private fun DictRuleUrlHistoryField(
    value: String,
    history: List<String>,
    onValueChange: (String) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = NgTheme.colors
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var expanded by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val filteredHistory = history.filter {
        value.isBlank() || it.contains(value, ignoreCase = true)
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
        expanded = history.isNotEmpty()
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) expanded = true
                },
            singleLine = true,
            textStyle = TextStyle(
                color = Color(colors.onSurface),
                fontSize = 16.sp,
                lineHeight = 22.sp,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onConfirm() }),
            cursorBrush = SolidColor(Color(colors.primary)),
            decorationBox = { innerTextField ->
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
                        }
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = "url",
                            color = Color(colors.onSurfaceVariant),
                            fontSize = 16.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
        DropdownMenu(
            expanded = expanded && focused && filteredHistory.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(),
            properties = PopupProperties(focusable = false),
        ) {
            filteredHistory.forEach { url ->
                DropdownMenuItem(
                    text = {
                        Text(text = url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    onClick = {
                        onValueChange(url)
                        expanded = false
                    },
                    trailingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_book_info_delete),
                            contentDescription = stringResource(R.string.delete),
                            tint = Color(colors.onSurfaceVariant),
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { onDeleteHistory(url) }
                                .padding(8.dp),
                        )
                    },
                )
            }
        }
    }
}
