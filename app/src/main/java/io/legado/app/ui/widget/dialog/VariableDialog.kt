package io.legado.app.ui.widget.dialog

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.ui.design.components.compose.NgCompactEditorDialog
import io.legado.app.ui.design.components.compose.NgFormDensity
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.components.compose.NgInfoTooltipButton
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme

/** 源变量／书籍变量等共享输入弹窗。 */
class VariableDialog() : BaseComposeDialogFragment() {

    constructor(title: String, key: String, variable: String?, comment: String) : this() {
        arguments = Bundle().apply {
            putString(ARG_TITLE, title)
            putString(ARG_KEY, key)
            putString(ARG_VARIABLE, variable)
            putString(ARG_COMMENT, comment)
        }
    }

    private var key = ""
    private var title = ""
    private var variable by mutableStateOf("")
    private var comment = ""

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(marginDp = 16)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val args = arguments ?: run {
            dismissAllowingStateLoss()
            return
        }
        title = args.getString(ARG_TITLE).orEmpty()
        key = args.getString(ARG_KEY).orEmpty()
        variable = args.getString(ARG_VARIABLE).orEmpty()
        comment = args.getString(ARG_COMMENT).orEmpty()
        (view as ComposeView).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    VariableDialogContent(
                        title = title,
                        variable = variable,
                        comment = comment,
                        onVariableChange = { variable = it },
                        onSave = ::save,
                    )
                }
            }
        }
    }

    private fun save() {
        callback?.setVariable(key, variable)
        dismissAllowingStateLoss()
    }

    private val callback: Callback?
        get() = (parentFragment as? Callback) ?: (activity as? Callback)

    interface Callback {
        fun setVariable(key: String, variable: String?)
    }

    private companion object {
        const val ARG_TITLE = "title"
        const val ARG_KEY = "key"
        const val ARG_VARIABLE = "variable"
        const val ARG_COMMENT = "comment"
    }
}

@Composable
private fun VariableDialogContent(
    title: String,
    variable: String,
    comment: String,
    onVariableChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    NgCompactEditorDialog(
        title = title,
        titleAction = {
            IconButton(
                onClick = onSave,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_save),
                    contentDescription = stringResource(R.string.action_save),
                    modifier = Modifier.size(22.dp),
                    tint = Color(NgTheme.colors.primary),
                )
            }
        },
    ) {
        VariableEditorContent(variable, comment, onVariableChange)
    }
}

internal enum class VariableEditorContentVariant {
    DIALOG,
    DRAWER,
}

/** 默认弹窗保持原样；抽屉使用加高输入和按需显示的说明。 */
@Composable
internal fun VariableEditorContent(
    variable: String,
    comment: String,
    onVariableChange: (String) -> Unit,
    variant: VariableEditorContentVariant = VariableEditorContentVariant.DIALOG,
) {
    val drawer = variant == VariableEditorContentVariant.DRAWER
    Text(
        text = stringResource(R.string.variable_value),
        modifier = Modifier.fillMaxWidth(),
        color = Color(NgTheme.colors.primary),
        fontSize = 13.sp,
        lineHeight = 17.sp,
    )
    NgFormField(
        label = "",
        value = variable,
        onValueChange = onVariableChange,
        density = NgFormDensity.COMPACT,
        variant = if (drawer) NgFormFieldVariant.DRAWER_UNDERLINE else NgFormFieldVariant.PLAIN_UNDERLINE,
        trailingContent = if (drawer) {
            {
                Box(
                    modifier = Modifier.fillMaxHeight().padding(bottom = 2.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    NgInfoTooltipButton(
                        text = comment,
                        contentDescription = stringResource(R.string.variable_comment),
                    )
                }
            }
        } else null,
    )
    if (!drawer) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.variable_comment),
            modifier = Modifier.fillMaxWidth(),
            color = Color(NgTheme.colors.primary),
            fontSize = 13.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(4.dp))
        SelectionContainer {
            Text(
                text = comment,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 56.dp)
                    .verticalScroll(rememberScrollState()),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}
