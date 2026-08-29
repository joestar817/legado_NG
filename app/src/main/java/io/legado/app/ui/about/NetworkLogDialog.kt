package io.legado.app.ui.about

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonParser
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.NetworkLog
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.design.components.compose.NgLazyListFastScroller
import io.legado.app.ui.design.components.compose.NgLazyListFastScrollerVariant
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.widget.dialog.CodeDialog
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.ui.widget.dialog.ngDialogMaxHeight
import io.legado.app.utils.GSON
import io.legado.app.utils.showDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NetworkLogDialog : BaseComposeDialogFragment(), CodeDialog.ExportCallback {

    private var logs by mutableStateOf<List<NetworkLog.Entry>>(emptyList())
    private var loading by mutableStateOf(false)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
    private val exportEntries = mutableMapOf<String, NetworkLog.Entry>()

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(height = ngDialogMaxHeight(0.86f))
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        logs = NetworkLog.logs
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
                    NetworkLogDialogContent(
                        logs = logs,
                        loading = loading,
                        emptyMessage = stringResource(
                            if (AppConfig.recordNetworkLog) R.string.log_empty
                            else R.string.log_feature_disabled
                        ),
                        onClear = ::clear,
                        onItemClick = ::openDetail,
                        formatTime = ::formatTime,
                    )
                }
            }
        }
    }

    private fun clear() {
        NetworkLog.clear()
        exportEntries.clear()
        logs = emptyList()
    }

    private fun openDetail(item: NetworkLog.Entry) {
        lifecycleScope.launch {
            loading = true
            try {
                val requestId = "network_log_${item.id}_${item.time}"
                exportEntries[requestId] = item
                val detail = withContext(Dispatchers.Default) {
                    item.formatDetail(preview = true)
                }
                if (isAdded) {
                    showDialogFragment(
                        CodeDialog(
                            code = detail,
                            requestId = requestId,
                            title = "Network",
                            highlightMode = CodeDialog.HighlightMode.DebugLog,
                        )
                    )
                }
            } finally {
                loading = false
            }
        }
    }

    override fun onCodeExport(requestId: String?): String? {
        return exportEntries[requestId]?.formatDetail(preview = false)
    }

    override fun onDestroyView() {
        exportEntries.clear()
        super.onDestroyView()
    }

    private fun formatTime(time: Long): String = synchronized(timeFormat) {
        timeFormat.format(Date(time))
    }

    private fun NetworkLog.Entry.formatDetail(preview: Boolean): String {
        return buildString {
            appendSection(
                "Overview",
                buildString {
                    append("#").append(id).append(' ')
                    append('[').append(formatTime(time)).append("] ")
                    append(type).append(' ')
                    append(method).append(' ')
                    statusCode?.let { append(it).append(' ') }
                    tookMs?.let { append(it).append("ms ") }
                    append(url)
                    if (error != null) {
                        append("\nERROR: ").append(error.lineSequence().firstOrNull())
                    }
                    append("\n").append(source)
                }
            )
            appendSection("Request headers", requestHeaders)
            appendSection("Request body · ${requestBody.bodyType()}", requestBody.formatBody(preview))
            appendSection("Response headers", responseHeaders)
            appendSection(
                "Response body · ${responseBody.bodyType()}",
                responseBody.formatBody(preview),
            )
            appendSection("Error", error)
        }
    }

    private fun StringBuilder.appendSection(title: String, value: String?) {
        if (value.isNullOrBlank()) return
        if (isNotBlank()) append("\n\n")
        append("// ===== ").append(title).append(" =====\n")
        append(value)
    }

    private fun String?.bodyType(): String {
        val value = this?.trim().orEmpty()
        return when {
            value.isBlank() -> "TEXT"
            value.isProbablyJson() -> "JSON"
            value.isProbablyHtml() -> "HTML"
            else -> "TEXT"
        }
    }

    private fun String?.formatBody(preview: Boolean): String? {
        val value = this?.trim() ?: return null
        if (value.isBlank()) return null
        if (preview && value.length > BODY_PREVIEW_MAX_LENGTH) {
            return value.take(BODY_PREVIEW_MAX_LENGTH) + getString(
                R.string.large_text_preview_suffix,
                BODY_PREVIEW_MAX_LENGTH,
                value.length,
            )
        }
        if (!preview && value.length > BODY_PRETTY_MAX_LENGTH) return value
        if (value.isProbablyJson()) {
            runCatching { GSON.toJson(JsonParser.parseString(value)) }
                .getOrNull()
                ?.let { return it }
        }
        if (value.isProbablyHtml()) return formatHtml(value)
        return this
    }

    private fun String.isProbablyJson(): Boolean = startsWith("{") || startsWith("[")

    private fun String.isProbablyHtml(): Boolean {
        val lower = lowercase(Locale.ROOT)
        return lower.startsWith("<!doctype") ||
            lower.startsWith("<html") ||
            Regex("^<[a-zA-Z][\\s\\S]*>").containsMatchIn(this)
    }

    private fun formatHtml(html: String): String {
        val normalized = html.replace(Regex(">\\s*<"), ">\n<")
        var indent = 0
        return normalized.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString("\n") { line ->
                if (line.startsWith("</") && indent > 0) indent--
                val formatted = "${"  ".repeat(indent)}$line"
                if (line.opensHtmlTag()) indent++
                formatted
            }
    }

    private fun String.opensHtmlTag(): Boolean {
        return startsWith("<") &&
            !startsWith("</") &&
            !startsWith("<!") &&
            !startsWith("<?") &&
            !endsWith("/>") &&
            !contains("</") &&
            !htmlVoidTagRegex.containsMatchIn(this)
    }

    private companion object {
        const val BODY_PREVIEW_MAX_LENGTH = 32 * 1024
        const val BODY_PRETTY_MAX_LENGTH = 128 * 1024
        val htmlVoidTagRegex = Regex(
            "^<(area|base|br|col|embed|hr|img|input|link|meta|param|source|track|wbr)\\b",
            RegexOption.IGNORE_CASE,
        )
    }
}

@Composable
private fun NetworkLogDialogContent(
    logs: List<NetworkLog.Entry>,
    loading: Boolean,
    emptyMessage: String,
    onClear: () -> Unit,
    onItemClick: (NetworkLog.Entry) -> Unit,
    formatTime: (Long) -> String,
) {
    val accentColor = Color(LocalContext.current.accentColor)
    LegacyLogDialogLayout(
        title = stringResource(R.string.network_request_log),
        actions = {
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
                    contentPadding = PaddingValues(top = 2.dp, bottom = 2.dp),
                ) {
                    items(
                        items = logs,
                        key = { item -> "${item.id}:${item.time}" },
                    ) { item ->
                        NetworkLogItem(
                            item = item,
                            timeText = formatTime(item.time),
                            onClick = { onItemClick(item) },
                        )
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
            LegacyRotateLoading(
                visible = loading,
                color = accentColor,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun NetworkLogItem(
    item: NetworkLog.Entry,
    timeText: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(dimensionResource(R.dimen.ng_radius_l))
    var lastClickTime by remember(item.id, item.time) { mutableLongStateOf(0L) }
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
                val now = SystemClock.elapsedRealtime()
                if (now - lastClickTime >= 700L) {
                    lastClickTime = now
                    onClick()
                }
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier
                    .widthIn(min = 52.dp)
                    .height(22.dp),
                color = networkTypeColor(item.type),
                shape = RoundedCornerShape(5.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = item.type,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        style = legacyLogTextStyle(
                            color = Color.White,
                            fontSize = 12.sp,
                        ),
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = timeText,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                style = legacyLogTextStyle(
                    color = colorResource(R.color.ng_on_surface_variant),
                    fontSize = 13.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.method,
                modifier = Modifier.padding(start = 6.dp),
                style = legacyLogTextStyle(
                    color = colorResource(R.color.ng_on_surface_variant),
                    fontSize = 13.sp,
                ),
                maxLines = 1,
            )
            NetworkStatus(item)
            item.tookMs?.let { took ->
                Text(
                    text = "${took}ms",
                    modifier = Modifier.padding(start = 8.dp),
                    style = legacyLogTextStyle(
                        color = colorResource(R.color.ng_on_surface_variant),
                        fontSize = 13.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
        Text(
            text = item.url,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            style = legacyLogTextStyle(
                color = colorResource(R.color.ng_on_surface),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.source.isNotBlank()) {
                Text(
                    text = item.source,
                    modifier = Modifier.weight(1f),
                    style = legacyLogTextStyle(
                        color = colorResource(R.color.ng_on_surface_variant),
                        fontSize = 12.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(R.string.network_log_id, item.id),
                modifier = Modifier
                    .padding(start = 8.dp)
                    .widthIn(min = 38.dp),
                style = legacyLogTextStyle(
                    color = colorResource(R.color.ng_on_surface_variant),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                ),
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NetworkStatus(item: NetworkLog.Entry) {
    val statusCode = item.statusCode
    val error = item.error != null
    if (statusCode == null && !error) return
    val color = if (!error && statusCode != null && statusCode in 200..399) {
        Color(0xFF34A853)
    } else {
        Color(0xFFD93025)
    }
    Row(
        modifier = Modifier.padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape),
        )
        Text(
            text = statusCode?.toString() ?: "ERR",
            modifier = Modifier.padding(start = 4.dp),
            style = legacyLogTextStyle(
                color = color,
                fontSize = 13.sp,
            ),
            maxLines = 1,
        )
    }
}

private fun networkTypeColor(type: String): Color = when (type) {
    "OkHttp" -> Color(0xFF2E7D32)
    "WebView" -> Color(0xFF1565C0)
    "JS" -> Color(0xFF6A1B9A)
    else -> Color(0xFF5F6368)
}
