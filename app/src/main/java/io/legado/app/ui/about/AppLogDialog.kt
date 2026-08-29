package io.legado.app.ui.about

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.design.components.compose.NgLazyListFastScroller
import io.legado.app.ui.design.components.compose.NgLazyListFastScrollerVariant
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.ui.widget.dialog.ngDialogMaxHeight
import io.legado.app.utils.LogUtils
import io.legado.app.utils.exportTextContent
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import java.util.Date

class AppLogDialog : BaseComposeDialogFragment() {

    private var logs by mutableStateOf<List<Triple<Long, String, Throwable?>>>(emptyList())

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(height = ngDialogMaxHeight(0.82f))
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        logs = AppLog.logs
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
                    AppLogDialogContent(
                        logs = logs,
                        emptyMessage = stringResource(
                            if (AppConfig.recordLog) R.string.log_empty
                            else R.string.log_feature_disabled
                        ),
                        onExport = ::export,
                        onClear = ::clear,
                        onOpenThrowable = { throwable ->
                            showDialogFragment(AppLogThrowableDialog(throwable.stackTraceToString()))
                        },
                    )
                }
            }
        }
    }

    private fun export() {
        val currentLogs = AppLog.logs
        if (currentLogs.isEmpty()) {
            requireContext().toastOnUi(R.string.export_content_empty)
            return
        }
        requireContext().exportTextContent(
            currentLogs.joinToString("\n\n") { item ->
                buildString {
                    append(LogUtils.logTimeFormat.format(Date(item.first)))
                    append('\n')
                    append(item.second)
                    item.third?.let {
                        append('\n')
                        append(it.stackTraceToString())
                    }
                }
            },
            filePrefix = "legado-app-log",
        )
    }

    private fun clear() {
        AppLog.clear()
        logs = emptyList()
    }
}

@Composable
private fun AppLogDialogContent(
    logs: List<Triple<Long, String, Throwable?>>,
    emptyMessage: String,
    onExport: () -> Unit,
    onClear: () -> Unit,
    onOpenThrowable: (Throwable) -> Unit,
) {
    val accentColor = Color(LocalContext.current.accentColor)
    LegacyLogDialogLayout(
        title = stringResource(R.string.log),
        actions = {
            if (logs.isNotEmpty()) {
                LegacyLogToolbarAction(
                    text = stringResource(R.string.export_content),
                    color = accentColor,
                    onClick = onExport,
                )
            }
            LegacyLogToolbarAction(
                text = stringResource(R.string.clear),
                color = accentColor,
                onClick = onClear,
            )
        },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (logs.isEmpty()) {
                Text(
                    text = emptyMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    style = legacyLogTextStyle(
                        color = colorResource(R.color.ng_on_surface_variant),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    ),
                    textAlign = TextAlign.Center,
                )
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 10.dp),
                ) {
                    itemsIndexed(
                        items = logs,
                        key = { index, item -> "${item.first}:$index" },
                    ) { _, item ->
                        AppLogItem(item = item, onOpenThrowable = onOpenThrowable)
                    }
                }
                NgLazyListFastScroller(
                    state = listState,
                    itemCount = logs.size,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    variant = NgLazyListFastScrollerVariant.TRACK,
                    trackColor = colorResource(R.color.transparent30),
                    handleColor = accentColor,
                )
            }
        }
    }
}

@Composable
private fun AppLogItem(
    item: Triple<Long, String, Throwable?>,
    onOpenThrowable: (Throwable) -> Unit,
) {
    val shape = RoundedCornerShape(dimensionResource(R.dimen.ng_radius_l))
    val linkColor = Color(LocalContext.current.accentColor)
    val messageDescription = stringResource(R.string.log)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
            .background(colorResource(R.color.ng_surface_card), shape)
            .border(
                0.8.dp,
                colorResource(R.color.ng_card_stroke),
                shape,
            )
            .clickable {
                item.third?.let(onOpenThrowable)
            }
            .padding(12.dp),
    ) {
        Text(
            text = LogUtils.logTimeFormat.format(Date(item.first)),
            modifier = Modifier.fillMaxWidth(),
            style = legacyLogTextStyle(
                color = colorResource(R.color.ng_on_surface_variant),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            ),
        )
        SelectionContainer {
            Text(
                text = linkifyWebText(item.second, linkColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp)
                    .semantics { contentDescription = messageDescription },
                style = legacyLogTextStyle(
                    color = colorResource(R.color.ng_on_surface),
                    fontSize = 14.sp,
                ),
            )
        }
    }
}

private fun linkifyWebText(text: String, linkColor: Color): AnnotatedString {
    val matcher = Patterns.WEB_URL.matcher(text)
    return buildAnnotatedString {
        var start = 0
        while (matcher.find()) {
            append(text.substring(start, matcher.start()))
            val displayUrl = matcher.group().orEmpty()
            val targetUrl = if (displayUrl.contains("://")) displayUrl else "http://$displayUrl"
            withLink(
                LinkAnnotation.Url(
                    url = targetUrl,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                        )
                    ),
                )
            ) {
                append(displayUrl)
            }
            start = matcher.end()
        }
        if (start < text.length) append(text.substring(start))
    }
}
