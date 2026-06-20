package io.legado.app.help.ai

import com.google.gson.reflect.TypeToken
import io.legado.app.utils.GSON

object AiPurifyHelper {

    private const val MAX_INPUT_LENGTH = 4000
    private const val MAX_BATCH_INPUT_LENGTH = 8000
    private const val MAX_AUTO_DELETE_RATIO = 0.18f

    suspend fun purify(text: String): AiPurifyResult {
        val source = normalizeSelectedText(text)
        check(source.isNotBlank()) { "选中文本为空" }
        check(source.length <= MAX_INPUT_LENGTH) { "选中文本过长，请分段选择后再净化" }
        val result = AiManager.generateText(
            messages = listOf(
                AiMessage(
                    AiMessage.Role.SYSTEM,
                    """
                    固定协议：
                    1. 只输出净化后的正文。
                    2. 不要解释，不要输出 JSON，不要使用 Markdown。

                    任务说明：
                    ${AiPromptStore.prompt(AiPromptStore.Prompt.PARAGRAPH_PURIFY)}
                    """.trimIndent()
                ),
                AiMessage(AiMessage.Role.USER, source)
            ),
            params = AiTextParams(
                temperature = 0f,
                maxTokens = (source.length * 2 + 64).coerceIn(256, 4096),
                disableThinking = true
            )
        )
        val cleaned = normalizeModelOutput(result.content)
        check(cleaned.isNotBlank()) { "AI 返回空内容" }
        val validation = validate(source, cleaned)
        return AiPurifyResult(
            original = source,
            cleaned = cleaned,
            deletedPreview = validation.deletedPreview,
            canAutoApply = validation.canAutoApply,
            riskReason = validation.riskReason,
            model = result.model
        )
    }

    suspend fun purifyParagraphs(paragraphs: List<String>): List<AiPurifyResult> {
        val inputs = paragraphs
            .mapIndexedNotNull { index, text ->
                val source = normalizeSelectedText(text)
                if (source.isBlank()) {
                    null
                } else {
                    check(source.length <= MAX_INPUT_LENGTH) { "第 ${index + 1} 段过长，请先分段净化" }
                    BatchInput(index + 1, source)
                }
            }
        check(inputs.isNotEmpty()) { "当前章节正文为空" }
        val batchResults = inputs.chunkForBatch().flatMap { purifyBatch(it) }
        return batchResults.mapIndexed { index, result ->
            val input = inputs[index]
            if (result.deletedPreview.isBlank() && input.needsSingleReviewAfterBatchMiss()) {
                runCatching { purify(input.text) }.getOrElse { result }
            } else {
                result
            }
        }
    }

    fun normalizeSelectedText(text: String): String {
        return text.lineSequence()
            .map { it.trim() }
            .joinToString("\n")
            .trim()
    }

    private fun normalizeModelOutput(text: String): String {
        var output = text.trim()
        if (output.startsWith("```")) {
            output = output.lines()
                .drop(1)
                .dropLastWhile { it.trim() == "```" }
                .joinToString("\n")
                .trim()
        }
        return output
    }

    private suspend fun purifyBatch(inputs: List<BatchInput>): List<AiPurifyResult> {
        val payload = GSON.toJson(inputs)
        val sourceLength = inputs.sumOf { it.text.length }
        val result = AiManager.generateText(
            messages = listOf(
                AiMessage(
                    AiMessage.Role.SYSTEM,
                    """
                    固定协议：
                    用户会给你一个 JSON 数组，每项包含 id 和 text。只返回需要净化的段落，每项只包含 id 和 cleaned。
                    未污染或不确定的段落不要返回，客户端会按原文保留。
                    只输出 JSON 数组，不要解释，不要使用 Markdown。

                    任务说明：
                    ${AiPromptStore.prompt(AiPromptStore.Prompt.CHAPTER_OPTIMIZE)}
                    """.trimIndent()
                ),
                AiMessage(AiMessage.Role.USER, payload)
            ),
            params = AiTextParams(
                temperature = 0f,
                maxTokens = (sourceLength * 2 + inputs.size * 48 + 128).coerceIn(512, 8192),
                disableThinking = true
            )
        )
        val outputs = parseBatchOutput(result.content)
        val outputMap = outputs.associateBy { it.id }
        return inputs.map { input ->
            val cleaned = normalizeModelOutput(
                outputMap[input.id]?.cleanedText
                    ?: input.text
            )
            check(cleaned.isNotBlank()) { "AI 返回第 ${input.id} 段为空" }
            val validation = validate(input.text, cleaned)
            AiPurifyResult(
                original = input.text,
                cleaned = cleaned,
                deletedPreview = validation.deletedPreview,
                canAutoApply = validation.canAutoApply,
                riskReason = validation.riskReason,
                model = result.model
            )
        }
    }

    private fun parseBatchOutput(text: String): List<BatchOutput> {
        var output = normalizeModelOutput(text)
        val start = output.indexOf('[')
        val end = output.lastIndexOf(']')
        check(start >= 0 && end > start) { "AI 未返回 JSON 数组" }
        output = output.substring(start, end + 1)
        val type = object : TypeToken<List<BatchOutput>>() {}.type
        return GSON.fromJson<List<BatchOutput>>(output, type)
            ?: error("AI 返回 JSON 解析失败")
    }

    private fun List<BatchInput>.chunkForBatch(): List<List<BatchInput>> {
        val chunks = arrayListOf<List<BatchInput>>()
        val current = arrayListOf<BatchInput>()
        var currentLength = 0
        for (input in this) {
            if (current.isNotEmpty() && currentLength + input.text.length > MAX_BATCH_INPUT_LENGTH) {
                chunks.add(current.toList())
                current.clear()
                currentLength = 0
            }
            current.add(input)
            currentLength += input.text.length
        }
        if (current.isNotEmpty()) {
            chunks.add(current.toList())
        }
        return chunks
    }

    private fun validate(original: String, cleaned: String): ValidationResult {
        if (cleaned == original) {
            return ValidationResult(
                deletedPreview = "",
                canAutoApply = false,
                riskReason = "AI 未删除任何内容"
            )
        }
        if (cleaned.length > original.length) {
            return ValidationResult(
                deletedPreview = "",
                canAutoApply = false,
                riskReason = "AI 输出比原文更长，可能发生改写"
            )
        }
        val deletedChars = ArrayList<Char>()
        var cleanedIndex = 0
        for (char in original) {
            if (cleanedIndex < cleaned.length && char == cleaned[cleanedIndex]) {
                cleanedIndex++
            } else {
                deletedChars.add(char)
            }
        }
        if (cleanedIndex != cleaned.length) {
            return ValidationResult(
                deletedPreview = "",
                canAutoApply = false,
                riskReason = "AI 输出不是原文删除后的结果，可能发生改写"
            )
        }
        val deleted = deletedChars.joinToString("")
        val deleteRatio = deleted.length.toFloat() / original.length.coerceAtLeast(1)
        val riskyChar = deletedChars.firstOrNull { it.isRiskyDeletedChar() }
        val riskReason = when {
            deleteRatio > MAX_AUTO_DELETE_RATIO -> "删除比例偏高，需要确认"
            riskyChar != null -> "删除了普通正文字符「$riskyChar」，需要确认"
            else -> null
        }
        return ValidationResult(
            deletedPreview = deleted.compactPreview(),
            canAutoApply = riskReason == null,
            riskReason = riskReason
        )
    }

    private fun Char.isRiskyDeletedChar(): Boolean {
        if (isWhitespace()) return false
        val block = Character.UnicodeBlock.of(this)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
            || this in "，。！？；：“”‘’、（）《》——……"
    }

    private fun BatchInput.needsSingleReviewAfterBatchMiss(): Boolean {
        if (id == 1 && text.isLikelyChapterTitle()) {
            return false
        }
        return text.hasSuspiciousNoiseMarker()
    }

    private fun String.isLikelyChapterTitle(): Boolean {
        if (length > 40) return false
        return Regex("""^第.{1,12}[章节卷集回部].*""").containsMatchIn(this)
    }

    private fun String.hasSuspiciousNoiseMarker(): Boolean {
        return any { char ->
            val block = Character.UnicodeBlock.of(char)
            block == Character.UnicodeBlock.ENCLOSED_ALPHANUMERICS
                || block == Character.UnicodeBlock.ENCLOSED_CJK_LETTERS_AND_MONTHS
                || char in "∴∵∷∞≧≦≥≤♂♀※☆★○●◎◇◆□■△▲▽▼→←↔↕↖↗↘↙"
        }
    }

    private fun String.compactPreview(maxLength: Int = 160): String {
        val compact = replace("\n", "\\n")
        return if (compact.length <= maxLength) {
            compact
        } else {
            compact.take(maxLength) + "..."
        }
    }

    private data class ValidationResult(
        val deletedPreview: String,
        val canAutoApply: Boolean,
        val riskReason: String?
    )

    private data class BatchInput(
        val id: Int,
        val text: String
    )

    private data class BatchOutput(
        val id: Int,
        val cleaned: String? = null,
        val text: String? = null,
        val content: String? = null
    ) {
        val cleanedText: String?
            get() = cleaned ?: text ?: content
    }
}

data class AiPurifyResult(
    val original: String,
    val cleaned: String,
    val deletedPreview: String,
    val canAutoApply: Boolean,
    val riskReason: String?,
    val model: String?
)
