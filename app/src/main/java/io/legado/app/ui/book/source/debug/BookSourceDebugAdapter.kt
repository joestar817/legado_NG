package io.legado.app.ui.book.source.debug

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.JsonParser
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ItemBookSourceDebugBinding
import io.legado.app.ui.widget.dialog.CodeDialog
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.GSON
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.visible
import java.util.Locale
import kotlin.math.abs

class BookSourceDebugAdapter(context: Context) :
    RecyclerAdapter<BookSourceDebugAdapter.DebugItem, ItemBookSourceDebugBinding>(context) {

    private val logs = mutableListOf<RawLog>()
    private val responses = mutableMapOf<Int, String>()
    private var sourceName: String = ""

    override fun getViewBinding(parent: ViewGroup): ItemBookSourceDebugBinding {
        return ItemBookSourceDebugBinding.inflate(inflater, parent, false)
    }

    fun addLog(state: Int, msg: String) {
        logs.add(RawLog(state, msg, parseElapsedMs(msg)))
        refreshItems()
    }

    fun addResponse(state: Int, msg: String) {
        responses[state] = msg
        if (logs.isNotEmpty()) {
            refreshItems()
        }
    }

    fun setSourceName(name: String) {
        sourceName = name
        if (logs.isNotEmpty()) {
            refreshItems()
        }
    }

    fun clearLogs() {
        logs.clear()
        responses.clear()
        clearItems()
    }

    private fun refreshItems() {
        val newItems = buildItems()
        val oldItems = getItems()
        when {
            oldItems.isEmpty() -> setItems(newItems)
            newItems.isEmpty() -> clearItems()
            else -> {
                val commonSize = minOf(oldItems.size, newItems.size)
                for (index in 0 until commonSize) {
                    if (oldItems[index] != newItems[index]) {
                        setItem(index, newItems[index])
                    }
                }
                when {
                    newItems.size > oldItems.size -> {
                        addItems(oldItems.size, newItems.drop(oldItems.size))
                    }
                    newItems.size < oldItems.size -> {
                        for (index in oldItems.lastIndex downTo newItems.size) {
                            removeItem(index)
                        }
                    }
                }
            }
        }
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemBookSourceDebugBinding,
        item: DebugItem,
        payloads: MutableList<Any>
    ) {
        when (item) {
            is DebugItem.Phase -> bindPhase(binding, item)
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemBookSourceDebugBinding) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var tapCandidate = true
        binding.layoutPhase.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    tapCandidate = true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) {
                        tapCandidate = false
                    }
                }
                MotionEvent.ACTION_CANCEL -> tapCandidate = false
            }
            false
        }
        binding.layoutPhase.setOnClickListener {
            if (tapCandidate) {
                openRawLog(holder)
            }
        }
    }

    private fun openRawLog(holder: ItemViewHolder) {
        val position = holder.layoutPosition
        if (position < 0) return
        (getItem(position) as? DebugItem.Phase)?.let { item ->
            if (item.rawText.isNotBlank()) {
                val activity = context as? AppCompatActivity ?: return@let
                if (item.hasResponse) {
                    activity.showDialogFragment(
                        CodeDialog(
                            code = item.rawText,
                            title = item.title,
                            highlightMode = CodeDialog.HighlightMode.DebugLog
                        )
                    )
                } else {
                    activity.showDialogFragment(TextDialog(item.title, item.rawText))
                }
            }
        }
    }

    private fun bindPhase(binding: ItemBookSourceDebugBinding, item: DebugItem.Phase) {
        val color = statusColor(item.status)
        val lineColor = if (item.status == StepStatus.Pending) {
            Color.parseColor("#D8D8D8")
        } else {
            color
        }
        binding.viewLineTop.background = lineBackground(if (item.index == 0) Color.TRANSPARENT else lineColor)
        binding.viewLineBottom.background = lineBackground(if (item.isLast) Color.TRANSPARENT else lineColor)
        binding.textPhaseMarker.text = stepMarker(item.index)
        binding.textPhaseMarker.setTextColor(if (item.status == StepStatus.Pending) color else Color.WHITE)
        binding.textPhaseMarker.background = markerBackground(color, item.status)
        binding.rotatePhaseLoading.loadingColor = statusColor(StepStatus.Running)
        if (item.status == StepStatus.Running) {
            binding.rotatePhaseLoading.visible()
        } else {
            binding.rotatePhaseLoading.gone()
        }
        binding.textPhaseTitle.text = item.title
        binding.textPhaseDuration.text = item.durationText
        binding.textPhaseDuration.background = durationBackground()
        binding.textPhaseStatus.text = item.status.label
        binding.textPhaseStatus.background = tagBackground(color)
        binding.textPhaseSummary.text = item.summary
        binding.textPhaseMeta.text = item.meta
        binding.textPhaseDetail.text = item.detail
        binding.textPhaseDetail.visible(item.detail.isNotBlank())
        binding.textPhaseHint.visible(item.rawText.isNotBlank())
    }

    private fun buildItems(): List<DebugItem> {
        if (logs.isEmpty()) return emptyList()
        val type = DebugType.from(logs.firstOrNull()?.message.orEmpty())
        val phases = type.phases.map { PhaseState(it.id, it.title) }
        val phaseMap = phases.associateBy { it.id }
        var currentId = phases.first().id
        var failedId: String? = null
        var terminal = false

        logs.forEach { raw ->
            val clean = cleanMessage(raw.message)
            val targetId = detectPhase(type, clean, currentId)
            currentId = targetId
            val phase = phaseMap[targetId] ?: return@forEach
            phase.logs.add(raw)
            phase.status = if (raw.isFailure()) StepStatus.Error else StepStatus.Running
            if (raw.isFailure()) {
                failedId = targetId
                terminal = true
            }
            if (raw.state == 1000) {
                terminal = true
            }
        }
        responses.forEach { (state, response) ->
            responsePhaseId(state, type)?.let { phaseId ->
                phaseMap[phaseId]?.responses?.add(
                    ResponseBody(responseLabel(state, type), response)
                )
            }
        }

        val lastTouchedIndex = phases.indexOfLast { it.logs.isNotEmpty() }
        phases.forEachIndexed { index, phase ->
            phase.status = when {
                failedId != null && phase.id == failedId -> StepStatus.Error
                failedId != null && index < phases.indexOfFirst { it.id == failedId } -> StepStatus.Success
                failedId != null -> StepStatus.Pending
                phase.logs.isEmpty() -> StepStatus.Pending
                terminal || index < lastTouchedIndex -> StepStatus.Success
                else -> StepStatus.Running
            }
        }

        val visiblePhases = phases.filter { it.logs.isNotEmpty() || it.status != StepStatus.Pending }
        val totalDuration = logs.mapNotNull { it.elapsedMs }.maxOrNull()
        return buildList {
            visiblePhases.forEachIndexed { index, phase ->
                val startMs = phase.logs.mapNotNull { it.elapsedMs }.minOrNull()
                val endMs = phase.logs.mapNotNull { it.elapsedMs }.maxOrNull()
                val nextStartMs = visiblePhases.getOrNull(index + 1)
                    ?.logs
                    ?.mapNotNull { it.elapsedMs }
                    ?.minOrNull()
                val phaseDuration = phaseDuration(startMs, endMs, nextStartMs)
                val accumulatedMs = endMs ?: startMs
                val hasResponse = phase.responses.isNotEmpty()
                add(
                    DebugItem.Phase(
                        title = phase.title,
                        status = phase.status,
                        summary = phaseSummary(phase),
                        detail = phaseDetail(phase),
                        rawText = phaseRawText(phase, hasResponse),
                        hasResponse = hasResponse,
                        index = index,
                        isLast = index == visiblePhases.lastIndex,
                        durationText = formatDuration(phaseDuration),
                        meta = phaseMeta(
                            phase = phase,
                            accumulatedMs = accumulatedMs,
                            totalDuration = totalDuration,
                            isLast = index == visiblePhases.lastIndex
                        )
                    )
                )
            }
        }
    }

    private fun phaseDuration(startMs: Long?, endMs: Long?, nextStartMs: Long?): Long? {
        if (startMs == null) return null
        return when {
            endMs != null && endMs > startMs -> endMs - startMs
            nextStartMs != null && nextStartMs > startMs -> nextStartMs - startMs
            else -> 0L
        }
    }

    private fun phaseMeta(
        phase: PhaseState,
        accumulatedMs: Long?,
        totalDuration: Long?,
        isLast: Boolean
    ): String {
        return buildString {
            append("日志 ").append(phase.logs.size).append(" 条")
            if (phase.responses.isNotEmpty()) {
                append(" · 响应 ").append(phase.responses.size).append(" 份")
            }
            accumulatedMs?.let {
                append(" · 累计 ").append(formatDuration(it))
            }
            if (isLast && totalDuration != null) {
                append(" · 总耗时 ").append(formatDuration(totalDuration))
            }
        }
    }

    private fun phaseSummary(phase: PhaseState): String {
        val error = phase.logs.firstOrNull { it.isFailure() }
        if (error != null) return errorSummary(error.message)
        if (phase.id == "entry" && sourceName.isNotBlank()) {
            return "书源：$sourceName"
        }
        val last = phase.logs.lastOrNull()?.message?.let(::cleanMessage).orEmpty()
        return when (phase.status) {
            StepStatus.Success -> "已完成"
            StepStatus.Error -> "执行失败"
            StepStatus.Running -> "正在执行"
            StepStatus.Pending -> "未开始"
        } + compactSummarySuffix(last)
    }

    private fun phaseDetail(phase: PhaseState): String {
        val error = phase.logs.firstOrNull { it.isFailure() }
        if (error != null) return conciseError(error.message)
        val logLines = phase.logs
            .map { cleanMessage(it.message) }
            .filter { it.isNotBlank() }
            .map(::compactDetailMessage)
            .filter { it.isNotBlank() }
            .distinct()
            .takeLast(3)
        return buildString {
            append(logLines.joinToString("\n"))
            if (phase.responses.isNotEmpty()) {
                if (isNotBlank()) append("\n")
                append("接口响应已收录，点击查看原始日志")
            }
        }
    }

    private fun phaseRawText(phase: PhaseState, codeStyle: Boolean): String {
        return buildString {
            appendRawSection("书源", sourceName, codeStyle)
            appendRawSection(
                "调试日志",
                phase.logs.joinToString("\n") { it.message },
                codeStyle
            )
            phase.responses.forEach { response ->
                val body = formatResponseBody(response.body)
                appendRawSection("接口响应：${response.label} · ${body.type}", body.text, codeStyle)
            }
        }
    }

    private fun StringBuilder.appendRawSection(title: String, content: String, codeStyle: Boolean) {
        if (content.isBlank()) return
        if (isNotBlank()) append("\n\n")
        if (codeStyle) {
            append("// ===== ").append(title).append(" =====\n")
        } else {
            append("== ").append(title).append(" ==\n")
        }
        append(content.limitRawSection())
    }

    private fun formatResponseBody(body: String): FormattedBody {
        val content = body.stripResponseLogPrefix()
        val trimmed = content.trim()
        if (trimmed.isBlank()) {
            return FormattedBody("TEXT", "")
        }
        if (trimmed.isProbablyJson()) {
            runCatching {
                GSON.toJson(JsonParser.parseString(trimmed))
            }.getOrNull()?.let {
                return FormattedBody("JSON", it)
            }
        }
        if (trimmed.isProbablyHtml()) {
            return FormattedBody("HTML", formatHtml(trimmed))
        }
        return FormattedBody("TEXT", content)
    }

    private fun String.stripResponseLogPrefix(): String {
        return trimStart().replace(
            Regex("^\\[\\d{2}:\\d{2}\\.\\d{3}]\\s*"),
            ""
        )
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

    private fun String.limitRawSection(): String {
        if (length <= MAX_RAW_SECTION_CHARS) return this
        return buildString {
            append(take(MAX_RAW_SECTION_CHARS))
            append("\n\n// ... 内容过长，已截断，原始长度 ")
            append(length)
            append(" 字符")
        }
    }

    private fun detectPhase(type: DebugType, message: String, currentId: String): String {
        return when {
            message.contains("开始搜索") ||
                    message.contains("开始访问发现页") ||
                    message.contains("开始访问详情页") ||
                    message.contains("开始访目录页") ||
                    message.contains("开始访正文页") -> "entry"

            message.contains("开始解析搜索页") -> "search"
            message.contains("开始解析发现页") -> "explore"
            message.contains("开始解析详情页") ||
                    message.contains("已获取目录链接") ||
                    message.contains("跳过详情页") -> "info"

            message.contains("开始解析目录页") -> "toc"
            message.contains("开始解析正文页") -> "content"

            message.contains("获取书籍列表") ||
                    message.contains("列表大小") ||
                    message.contains("列表为空") ||
                    message.contains("书籍总数") ||
                    message.contains("未获取到书籍") -> when (type) {
                DebugType.Explore -> "exploreList"
                DebugType.Search -> "searchList"
                else -> currentId
            }

            message.contains("获取目录列表") ||
                    message.contains("目录总数") ||
                    message.contains("章节列表为空") ||
                    message.contains("首章信息") -> "toc"

            message.contains("获取章节名称") ||
                    message.contains("获取正文内容") ||
                    message.contains("正文页解析完成") -> "content"

            else -> currentId
        }
    }

    private fun responsePhaseId(state: Int, type: DebugType): String? {
        return when (state) {
            10 -> when (type) {
                DebugType.Explore -> "explore"
                DebugType.Search -> "search"
                else -> null
            }
            20 -> "info"
            30 -> "toc"
            40 -> "content"
            else -> null
        }
    }

    private fun responseLabel(state: Int, type: DebugType): String {
        return when (state) {
            10 -> if (type == DebugType.Explore) "发现页" else "搜索页"
            20 -> "详情页"
            30 -> "目录页"
            40 -> "正文页"
            else -> "响应"
        }
    }

    private fun errorSummary(message: String): String {
        val clean = cleanMessage(message)
        return when {
            clean.contains("未获取到书籍") -> "未获取到书籍"
            clean.contains("SyntaxError", true) -> "JS 语法错误"
            clean.contains("ScriptException", true) -> "脚本执行异常"
            clean.contains("Exception", true) -> clean.lineSequence().firstOrNull().orEmpty()
            else -> clean.lineSequence().firstOrNull().orEmpty()
        }
    }

    private fun conciseError(message: String): String {
        val clean = cleanMessage(message)
        val syntax = Regex("SyntaxError:\\s*([^\\n\\r]+)").find(clean)?.groupValues?.getOrNull(1)
        val line = Regex("line number\\s+(\\d+)", RegexOption.IGNORE_CASE)
            .find(clean)?.groupValues?.getOrNull(1)
            ?: Regex("#(\\d+)\\)").find(clean)?.groupValues?.getOrNull(1)
        return buildString {
            when {
                syntax != null -> append("SyntaxError: ").append(syntax)
                clean.contains("未获取到书籍") -> append("没有从列表规则解析出书籍")
                else -> append(
                    clean.lineSequence()
                        .firstOrNull { it.isNotBlank() && !it.trimStart().startsWith("at ") }
                        ?.trim()
                        .orEmpty()
                )
            }
            if (!line.isNullOrBlank()) {
                append("\n位置：第 ").append(line).append(" 行")
            }
        }
    }

    private fun cleanMessage(message: String): String {
        return message
            .replace(Regex("^\\[\\d{2}:\\d{2}\\.\\d{3}]\\s*"), "")
            .replace(Regex("^[⇒◇┌└≡︾︽]\\s*"), "")
            .trim()
    }

    private fun compactSummarySuffix(message: String): String {
        if (message.isBlank()) return ""
        return when {
            message.startsWith("获取成功:") -> "：获取成功"
            message.startsWith("开始搜索关键字:") -> "：${message}"
            message.startsWith("开始访问") || message.startsWith("开始访") -> "：${message.substringBefore(":")}"
            message.length > 48 -> "：${message.take(45)}..."
            else -> "：$message"
        }
    }

    private fun compactDetailMessage(message: String): String {
        return when {
            message.startsWith("获取成功:") -> {
                val url = message.substringAfter("获取成功:").substringBefore(",{").substringBefore("{")
                "获取成功：${compactUrl(url)}"
            }
            message.startsWith("http://") || message.startsWith("https://") -> ""
            message.startsWith("{") || message.startsWith("\"") || message == "}" -> ""
            message.contains("\"headers\"") || message.contains("\"webView\"") -> ""
            message.length > 80 -> "${message.take(77)}..."
            else -> message
        }
    }

    private fun compactUrl(url: String): String {
        return url
            .replace(Regex("^https?://"), "")
            .let { if (it.length > 48) "${it.take(45)}..." else it }
    }

    private fun parseElapsedMs(message: String): Long? {
        val match = Regex("^\\[(\\d{2}):(\\d{2})\\.(\\d{3})]").find(message) ?: return null
        val minute = match.groupValues[1].toLongOrNull() ?: return null
        val second = match.groupValues[2].toLongOrNull() ?: return null
        val milli = match.groupValues[3].toLongOrNull() ?: return null
        return minute * 60_000L + second * 1_000L + milli
    }

    private fun formatDuration(durationMs: Long?): String {
        durationMs ?: return "-"
        return if (durationMs < 1_000L) {
            "${durationMs}ms"
        } else {
            val seconds = durationMs / 1_000.0
            "${String.format(Locale.US, "%.2f", seconds)}s"
        }
    }

    private fun RawLog.isFailure(): Boolean {
        return state == -1 || message.contains("Exception", true) ||
                message.contains("SyntaxError", true) ||
                message.contains("未获取到书籍")
    }

    private fun stepMarker(index: Int): String {
        return (index + 1).toString()
    }

    private fun statusColor(status: StepStatus): Int {
        return when (status) {
            StepStatus.Success -> Color.parseColor("#2E7D32")
            StepStatus.Running -> Color.parseColor("#1565C0")
            StepStatus.Error -> Color.parseColor("#D93025")
            StepStatus.Pending -> Color.parseColor("#7A7A7A")
        }
    }

    private fun tagBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 5.dpToPx().toFloat()
            setColor(color)
        }
    }

    private fun durationBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 5.dpToPx().toFloat()
            setColor(Color.argb(18, 120, 120, 120))
        }
    }

    private fun markerBackground(color: Int, status: StepStatus): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (status == StepStatus.Pending) {
                setColor(Color.TRANSPARENT)
                setStroke(2.dpToPx(), color)
            } else {
                setColor(color)
            }
        }
    }

    private fun lineBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 1.dpToPx().toFloat()
            setColor(color)
        }
    }

    sealed class DebugItem {
        data class Phase(
            val title: String,
            val status: StepStatus,
            val summary: String,
            val detail: String,
            val rawText: String,
            val hasResponse: Boolean,
            val index: Int,
            val isLast: Boolean,
            val durationText: String,
            val meta: String
        ) : DebugItem()
    }

    private data class RawLog(
        val state: Int,
        val message: String,
        val elapsedMs: Long?
    )

    private data class ResponseBody(
        val label: String,
        val body: String
    )

    private data class FormattedBody(
        val type: String,
        val text: String
    )

    private data class PhaseDefinition(val id: String, val title: String)

    private data class PhaseState(
        val id: String,
        val title: String,
        val logs: MutableList<RawLog> = mutableListOf(),
        val responses: MutableList<ResponseBody> = mutableListOf(),
        var status: StepStatus = StepStatus.Pending
    )

    enum class StepStatus(val label: String) {
        Success("成功"),
        Running("当前"),
        Error("失败"),
        Pending("等待")
    }

    private enum class DebugType(
        val title: String,
        val phases: List<PhaseDefinition>
    ) {
        Search(
            "搜索",
            listOf(
                PhaseDefinition("entry", "入口"),
                PhaseDefinition("search", "搜索页"),
                PhaseDefinition("searchList", "书籍列表"),
                PhaseDefinition("info", "详情页"),
                PhaseDefinition("toc", "目录页"),
                PhaseDefinition("content", "正文页")
            )
        ),
        Explore(
            "发现",
            listOf(
                PhaseDefinition("entry", "入口"),
                PhaseDefinition("explore", "发现页"),
                PhaseDefinition("exploreList", "书籍列表"),
                PhaseDefinition("info", "详情页"),
                PhaseDefinition("toc", "目录页"),
                PhaseDefinition("content", "正文页")
            )
        ),
        Info(
            "详情",
            listOf(
                PhaseDefinition("entry", "入口"),
                PhaseDefinition("info", "详情页"),
                PhaseDefinition("toc", "目录页"),
                PhaseDefinition("content", "正文页")
            )
        ),
        Toc(
            "目录",
            listOf(
                PhaseDefinition("entry", "入口"),
                PhaseDefinition("toc", "目录页"),
                PhaseDefinition("content", "正文页")
            )
        ),
        Content(
            "正文",
            listOf(
                PhaseDefinition("entry", "入口"),
                PhaseDefinition("content", "正文页")
            )
        );

        companion object {
            fun from(message: String): DebugType {
                return when {
                    message.contains("发现页") -> Explore
                    message.contains("详情页") -> Info
                    message.contains("目录页") -> Toc
                    message.contains("正文页") -> Content
                    else -> Search
                }
            }
        }
    }

    private companion object {
        private const val MAX_RAW_SECTION_CHARS = 128 * 1024
        private val htmlVoidTagRegex = Regex(
            "^<(area|base|br|col|embed|hr|img|input|link|meta|param|source|track|wbr)\\b",
            RegexOption.IGNORE_CASE
        )
    }
}
