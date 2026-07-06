package io.legado.app.ui.about

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.JsonParser
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogNgRecyclerViewBinding
import io.legado.app.databinding.ItemNetworkLogBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.NetworkLog
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.widget.dialog.CodeDialog
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.ui.widget.dialog.ngDialogMaxHeight
import io.legado.app.utils.GSON
import io.legado.app.utils.applyTint
import io.legado.app.utils.debounce
import io.legado.app.utils.dpToPx
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.tintTitle
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NetworkLogDialog : BaseDialogFragment(R.layout.dialog_ng_recycler_view),
    Toolbar.OnMenuItemClickListener,
    CodeDialog.ExportCallback {

    private val binding by viewBinding(DialogNgRecyclerViewBinding::bind)
    private val adapter by lazy { NetworkLogAdapter(requireContext()) }
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
    private val exportEntries = mutableMapOf<String, NetworkLog.Entry>()

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(height = ngDialogMaxHeight(0.86f))
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.run {
            view.setBackgroundResource(R.drawable.ng_bg_dialog)
            toolBar.setTitle(R.string.network_request_log)
            toolBar.inflateMenu(R.menu.network_log)
            toolBar.menu.applyTint(requireContext())
            toolBar.menu.tintTitle(R.id.menu_clear, requireContext().accentColor)
            toolBar.setOnMenuItemClickListener(this@NetworkLogDialog)
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.clipToPadding = false
            recyclerView.setPadding(0, 2.dpToPx(), 0, 2.dpToPx())
            recyclerView.adapter = adapter
        }
        val logs = NetworkLog.logs
        adapter.setItems(logs)
        updateEmptyState(logs)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_clear -> {
                NetworkLog.clear()
                adapter.clearItems()
                exportEntries.clear()
                updateEmptyState(emptyList())
            }
        }
        return true
    }

    private fun updateEmptyState(logs: List<NetworkLog.Entry>) {
        val hasLogs = logs.isNotEmpty()
        binding.recyclerView.visibility = if (hasLogs) View.VISIBLE else View.GONE
        binding.tvMsg.visibility = if (hasLogs) View.GONE else View.VISIBLE
        binding.tvMsg.setText(
            if (AppConfig.recordNetworkLog) {
                R.string.log_empty
            } else {
                R.string.log_feature_disabled
            }
        )
    }

    override fun onDestroyView() {
        exportEntries.clear()
        super.onDestroyView()
    }

    override fun onCodeExport(requestId: String?): String? {
        val item = exportEntries[requestId] ?: return null
        return item.formatDetail(preview = false)
    }

    private fun formatTime(time: Long): String {
        return synchronized(timeFormat) {
            timeFormat.format(Date(time))
        }
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
            appendSection(
                "Request body · ${requestBody.bodyType()}",
                requestBody.formatBody(preview)
            )
            appendSection("Response headers", responseHeaders)
            appendSection(
                "Response body · ${responseBody.bodyType()}",
                responseBody.formatBody(preview)
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
            return value.take(BODY_PREVIEW_MAX_LENGTH) +
                    getString(
                        R.string.large_text_preview_suffix,
                        BODY_PREVIEW_MAX_LENGTH,
                        value.length
                    )
        }
        if (!preview && value.length > BODY_PRETTY_MAX_LENGTH) {
            return value
        }
        if (value.isProbablyJson()) {
            runCatching {
                GSON.toJson(JsonParser.parseString(value))
            }.getOrNull()?.let {
                return it
            }
        }
        if (value.isProbablyHtml()) {
            return formatHtml(value)
        }
        return this
    }

    private fun String.isProbablyJson(): Boolean {
        return startsWith("{") || startsWith("[")
    }

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
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n") { line ->
                if (line.startsWith("</") && indent > 0) {
                    indent--
                }
                val formatted = "${"  ".repeat(indent)}$line"
                if (line.opensHtmlTag()) {
                    indent++
                }
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

    inner class NetworkLogAdapter(context: Context) :
        RecyclerAdapter<NetworkLog.Entry, ItemNetworkLogBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemNetworkLogBinding {
            return ItemNetworkLogBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemNetworkLogBinding,
            item: NetworkLog.Entry,
            payloads: MutableList<Any>
        ) {
            binding.textId.text = context.getString(R.string.network_log_id, item.id)
            binding.textType.text = item.type
            binding.textType.background = tagBackground(item.type)
            binding.textTime.text = formatTime(item.time)
            binding.textMethod.text = item.method
            bindStatus(binding, item)
            binding.textTook.text = item.tookMs?.let { "${it}ms" }.orEmpty()
            binding.textTook.visibility = if (item.tookMs == null) View.GONE else View.VISIBLE
            binding.textUrl.text = item.url
            binding.textSource.text = item.source
            binding.textSource.visibility = if (item.source.isBlank()) View.GONE else View.VISIBLE
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemNetworkLogBinding) {
            val openDetail = debounce(wait = 700L, leading = true, trailing = false) {
                getItem(holder.layoutPosition)?.let { item ->
                    lifecycleScope.launch {
                        this@NetworkLogDialog.binding.rotateLoading.visibility = View.VISIBLE
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
                                        highlightMode = CodeDialog.HighlightMode.DebugLog
                                    )
                                )
                            }
                        } finally {
                            if (isAdded) {
                                this@NetworkLogDialog.binding.rotateLoading.visibility = View.GONE
                            }
                        }
                    }
                }
            }
            binding.root.setOnClickListener {
                openDetail()
            }
        }

        private fun bindStatus(binding: ItemNetworkLogBinding, item: NetworkLog.Entry) {
            val statusCode = item.statusCode
            val error = item.error != null
            if (statusCode == null && !error) {
                binding.layoutStatus.visibility = View.GONE
                return
            }
            val color = when {
                error -> "#D93025".toColorInt()
                statusCode in 200..399 -> "#34A853".toColorInt()
                else -> "#D93025".toColorInt()
            }
            binding.layoutStatus.visibility = View.VISIBLE
            binding.viewStatusDot.background = dotBackground(color)
            binding.textStatus.setTextColor(color)
            binding.textStatus.text = statusCode?.toString() ?: "ERR"
        }

        private fun tagBackground(type: String): GradientDrawable {
            val color = when (type) {
                "OkHttp" -> "#2E7D32".toColorInt()
                "WebView" -> "#1565C0".toColorInt()
                "JS" -> "#6A1B9A".toColorInt()
                else -> "#5F6368".toColorInt()
            }
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 5.dpToPx().toFloat()
                setColor(color)
            }
        }

        private fun dotBackground(color: Int): GradientDrawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        }
    }

    private companion object {
        const val BODY_PREVIEW_MAX_LENGTH = 32 * 1024
        const val BODY_PRETTY_MAX_LENGTH = 128 * 1024
        private val htmlVoidTagRegex = Regex(
            "^<(area|base|br|col|embed|hr|img|input|link|meta|param|source|track|wbr)\\b",
            RegexOption.IGNORE_CASE
        )
    }
}
