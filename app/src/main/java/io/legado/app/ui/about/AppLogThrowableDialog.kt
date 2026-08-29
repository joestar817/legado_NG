package io.legado.app.ui.about

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.help.CacheManager
import io.legado.app.help.IntentData
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.ui.widget.dialog.ngDialogMaxHeight
import io.legado.app.utils.startActivity

class AppLogThrowableDialog() : BaseComposeDialogFragment() {

    constructor(content: String) : this() {
        arguments = Bundle().apply {
            putString(ARG_CONTENT, IntentData.put(content))
        }
    }

    private var content = ""

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(height = ngDialogMaxHeight())
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        content = arguments?.getString(ARG_CONTENT)
            ?.let { IntentData.get<String>(it) }
            .orEmpty()
        (view as ComposeView).apply {
            layoutParams = layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            setBackgroundResource(R.drawable.ng_bg_dialog)
            clipToOutline = true
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    AppLogThrowableContent(
                        content = displayContent(content),
                        onFullscreen = ::openFullscreen,
                        onClose = { dismissAllowingStateLoss() },
                    )
                }
            }
        }
    }

    private fun displayContent(value: String): String {
        if (value.length < MAX_PREVIEW_LENGTH) return value
        return value.take(MAX_PREVIEW_LENGTH) + "\n\n数据太大，无法全部显示…"
    }

    private fun openFullscreen() {
        val cacheKey = "code_text_${System.currentTimeMillis()}"
        CacheManager.putMemory(cacheKey, content)
        requireContext().startActivity<CodeEditActivity> {
            putExtra("cacheKey", cacheKey)
            putExtra("title", "Log")
            putExtra("languageName", "text.html.basic")
        }
    }

    private companion object {
        const val ARG_CONTENT = "content"
        const val MAX_PREVIEW_LENGTH = 32 * 1024
    }
}

@Composable
private fun AppLogThrowableContent(
    content: String,
    onFullscreen: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    LegacyLogDialogLayout(
        title = "Log",
        actions = {
            LegacyLogToolbarIconAction(
                iconRes = R.drawable.ic_code,
                contentDescription = stringResource(R.string.edit_content),
                color = colorResource(R.color.ng_on_surface),
                onClick = onFullscreen,
            )
            LegacyLogToolbarAction(
                text = stringResource(R.string.close),
                color = Color(context.accentColor),
                onClick = onClose,
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
        ) {
            SelectionContainer {
                Text(
                    text = content,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    style = legacyLogTextStyle(
                        color = colorResource(R.color.ng_on_surface),
                        fontSize = 14.sp,
                    ),
                )
            }
        }
    }
}
