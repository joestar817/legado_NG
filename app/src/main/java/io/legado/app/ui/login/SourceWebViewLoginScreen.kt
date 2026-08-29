package io.legado.app.ui.login

import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgEditorTopBar
import io.legado.app.ui.design.components.compose.NgEditorTopBarAction
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun SourceWebViewLoginScreen(
    title: String,
    webView: WebView,
    progress: Int,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(NgTheme.colors.background))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        NgEditorTopBar(
            title = title,
            onBack = onBack,
            actions = listOf(
                NgEditorTopBarAction(
                    icon = painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.ok),
                    onClick = onConfirm,
                )
            ),
        )
        if (progress in 0..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp),
                color = Color(NgTheme.colors.primary),
                trackColor = Color.Transparent,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp),
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { context ->
                    FrameLayout(context).apply {
                        (webView.parent as? ViewGroup)?.removeView(webView)
                        addView(
                            webView,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
    DisposableEffect(webView) {
        onDispose {
            (webView.parent as? ViewGroup)?.removeView(webView)
        }
    }
}
