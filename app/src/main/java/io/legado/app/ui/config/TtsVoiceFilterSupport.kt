package io.legado.app.ui.config

import io.legado.app.help.tts.TtsVoice

object TtsVoiceFilterSupport {

    fun availableLanguageLabels(voices: List<TtsVoice>): List<String> {
        return sortLanguageLabels(
            voices.flatMap { languageLabels(it.language) }.distinct()
        )
    }

    fun languageLabels(language: String?): List<String> {
        val raw = language?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val values = raw
            .replace('，', ',')
            .replace('、', ',')
            .replace(';', ',')
            .replace('；', ',')
            .replace('/', ',')
            .replace('|', ',')
            .replace('+', ',')
            .split(',', ' ', '\n', '\t')
            .mapNotNull(::languageLabel)
            .distinct()
        return values.ifEmpty {
            languageLabel(raw)?.let(::listOf).orEmpty()
        }
    }

    fun genderLabel(gender: String?): String? {
        return when (gender?.takeIf { it.isNotBlank() }?.lowercase()) {
            "male", "man" -> "男"
            "female", "woman" -> "女"
            else -> null
        }
    }

    private fun sortLanguageLabels(labels: List<String>): List<String> {
        val preferredOrder = listOf("中", "英", "日", "韩", "法", "德", "西", "葡", "粤", "吴")
        return labels.sortedWith(
            compareBy<String> {
                preferredOrder.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
            }.thenBy { it }
        )
    }

    private fun languageLabel(language: String?): String? {
        val code = language?.takeIf { it.isNotBlank() }
            ?.substringBefore("-")
            ?.substringBefore("_")
            ?.lowercase()
            ?: return null
        return when (code) {
            "zh", "cmn" -> "中"
            "yue" -> "粤"
            "wuu" -> "吴"
            "en" -> "英"
            "ja", "jp", "jpn" -> "日"
            "ko", "kr", "kor" -> "韩"
            "fr", "fra", "fre" -> "法"
            "de", "deu", "ger" -> "德"
            "es", "esp", "spa" -> "西"
            "pt", "por" -> "葡"
            else -> code.uppercase()
        }
    }
}
