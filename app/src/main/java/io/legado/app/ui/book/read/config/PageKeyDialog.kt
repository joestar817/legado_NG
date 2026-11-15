package io.legado.app.ui.book.read.config

import android.content.Context
import android.view.ViewGroup
import android.view.Window
import androidx.activity.ComponentDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.PageKeyBindings
import io.legado.app.help.config.savePageKeyBindings
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.getPrefString
import io.legado.app.utils.setLayout

class PageKeyDialog(context: Context) : ComponentDialog(context) {
    private var bindings by mutableStateOf(
        PageKeyBindings(
            previous = context.getPrefString(PreferKey.prevKeys).orEmpty(),
            next = context.getPrefString(PreferKey.nextKeys).orEmpty(),
        ),
    )
    private val capture = PageKeyCaptureController { direction, keyCode ->
        saveBindings(bindings.bind(direction, keyCode))
    }

    private fun saveBindings(value: PageKeyBindings) {
        context.savePageKeyBindings(value)
        bindings = value
    }

    override fun onStart() {
        super.onStart()
        window?.run {
            setBackgroundDrawableResource(android.R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
        }
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setOnKeyListener { _, _, event -> capture.onKeyEvent(event) }
        setContentView(
            ComposeView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    NgAppTheme(snapshot = ReadDrawerStyle.themeSnapshot(context), updateSystemBars = false) {
                        PageKeyDialogContent(
                            bindings = bindings,
                            capture = capture,
                            onBindingsChanged = { saveBindings(it) },
                            onClose = { dismiss() },
                        )
                    }
                }
            }
        )
    }
}
