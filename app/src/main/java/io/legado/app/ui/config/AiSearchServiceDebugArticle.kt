package io.legado.app.ui.config

import android.text.SpannableString
import android.text.Spanned
import android.text.SpannedString
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.help.ai.AiExternalSearchDebugResult
import io.legado.app.help.ai.webUrl
import io.legado.app.help.config.NgThemeRuntimeAssets
import io.legado.app.ui.design.components.compose.NgFloatingTitleToolbar
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.theme.NgInterfaceFontMarkwonPlugin
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.openUrl
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.ext.tables.TablePlugin
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SearchDebugArticleScreen(
    record: AiSearchFetchRecord,
    onBack: () -> Unit,
    onShowResponse: (AiExternalSearchDebugResult<*>) -> Unit,
) {
    val context = LocalContext.current
    val result = record.result
    val page = result?.data
    val failure = result?.error?.displayMessage(context)
    val renderer = rememberDebugMarkdownRenderer(record.address)
    val sections = debugMarkdownSections(page?.text, renderer)
    val colors = NgTheme.colors
    val cardColor = colorResource(R.color.ng_surface_card)
    val radius = NgTheme.shapes.mediumDp.dp
    NgFloatingTitleToolbar(
        title = stringResource(R.string.ai_search_debug_article),
        onBack = onBack,
        modifier = Modifier.padding(horizontal = 16.dp),
        trailingContent = if (result == null) null else {
            {
                NgFormActionButton(
                    text = stringResource(R.string.ai_search_debug_view_response),
                    onClick = { onShowResponse(result) },
                    appearance = NgFormActionButtonAppearance.TEXT,
                    buttonHeight = 34.dp,
                    textSize = 13.sp,
                )
            }
        },
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        item(key = "header") {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = radius, topEnd = radius))
                    .background(cardColor).padding(start = 16.dp, end = 16.dp, top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = page?.source?.title?.takeUnless { it == record.address }
                        ?: record.titleHint.ifBlank {
                            stringResource(when {
                                failure != null -> R.string.ai_search_debug_failed
                                page != null -> R.string.ai_search_debug_untitled
                                else -> R.string.ai_search_debug_article
                            })
                        },
                    color = Color(colors.onSurface),
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                SearchDebugSource(record.siteName, record.address)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchDebugFetchTags(result)
                    Spacer(Modifier.weight(1f))
                    record.address.webUrl()?.let { url ->
                        TextButton(
                            onClick = { context.openUrl(url) },
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) { Text(stringResource(R.string.ai_search_debug_open_page), fontSize = 13.sp) }
                    }
                }
                HorizontalDivider(color = Color(colors.outlineVariant))
                val status = when {
                    failure != null -> failure
                    result == null -> stringResource(R.string.ai_chat_web_reading)
                    sections == null -> stringResource(R.string.ai_search_debug_preparing_article)
                    else -> null
                }
                if (status != null) {
                    Text(
                        status,
                        modifier = Modifier.padding(top = 6.dp),
                        color = Color(if (failure == null) colors.onSurfaceVariant else colors.error),
                        fontSize = 14.sp,
                    )
                }
            }
        }
        itemsIndexed(sections.orEmpty(), key = { index, _ -> index }) { index, section ->
            AndroidView(
                modifier = Modifier.fillMaxWidth().background(cardColor)
                    .padding(start = 16.dp, end = 16.dp, top = if (index == 0) 14.dp else 0.dp),
                factory = { viewContext ->
                    TextView(viewContext).apply {
                        includeFontPadding = false
                        movementMethod = LinkMovementMethod.getInstance()
                        NgThemeRuntimeAssets.applyAppTypeface(viewContext, this)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        setLineSpacing(0f, 1.18f)
                    }
                },
                update = { textView ->
                    textView.setTextColor(colors.onSurface)
                    textView.setLinkTextColor(colors.primary)
                    if (textView.tag !== section) {
                        // TablePlugin attaches its row-height invalidator when Markwon sets the text.
                        renderer.setParsedMarkdown(textView, section)
                        textView.tag = section
                    }
                },
            )
        }
        item(key = "footer") {
            Spacer(
                Modifier.fillMaxWidth().height(16.dp)
                    .clip(RoundedCornerShape(bottomStart = radius, bottomEnd = radius)).background(cardColor),
            )
        }
    }
}

@Composable
private fun rememberDebugMarkdownRenderer(address: String): Markwon {
    val context = LocalContext.current
    return remember(context, address) {
        val base = address.toHttpUrlOrNull()
        Markwon.builder(context)
            .usePlugin(NgInterfaceFontMarkwonPlugin(context))
            .usePlugin(TablePlugin.create(context))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver { view, link ->
                        base?.resolve(link)?.toString().webUrl()?.let { view.context.openUrl(it) }
                    }
                }
            })
            .build()
    }
}

@Composable
private fun debugMarkdownSections(content: String?, renderer: Markwon): List<Spanned>? {
    if (content == null) return null
    val sections by produceState<List<Spanned>?>(null, renderer, content) {
        value = withContext(Dispatchers.Default) {
            val parsed = renderer.toMarkdown(content)
            (if (parsed.isBlank()) SpannableString(content) else parsed).debugSections()
        }
    }
    return sections
}

// Parse the complete document once. Bound the size of each TextView even for long responses.
private fun Spanned.debugSections(): List<Spanned> = buildList {
    val blockSize = 12 * 1024
    var start = 0
    while (start < length) {
        var end = (start + blockSize).coerceAtMost(length)
        if (end < length) {
            val lineEnd = (end - 1 downTo start + blockSize / 2).firstOrNull { this@debugSections[it] == '\n' }
            if (lineEnd != null) end = lineEnd + 1
            else if (Character.isHighSurrogate(this@debugSections[end - 1])) end--
        }
        add(SpannedString.valueOf(subSequence(start, end)))
        start = end
    }
}
