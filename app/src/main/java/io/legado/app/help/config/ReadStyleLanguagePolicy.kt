package io.legado.app.help.config

import io.legado.app.help.book.BookLanguageDetector
import io.legado.app.help.book.BookScriptClass

data class ReadStyleLanguageDecision(
    val styleNameToSelect: String,
    val scriptClass: BookScriptClass? = null,
    val commitRememberedStyle: Boolean = false,
)

object ReadStyleLanguagePolicy {
    fun decide(
        rememberedStyleName: String?,
        languageHint: String?,
        sampleTexts: List<String>,
        existingStyleNames: List<String>,
        currentStyleName: String,
        bindings: LanguageStyleBindings,
    ): ReadStyleLanguageDecision {
        val remembered = rememberedStyleName?.trim()?.takeIf { it.isNotEmpty() }
        val script = BookLanguageDetector.detect(languageHint, sampleTexts)
        if (remembered != null) {
            val target = remembered.takeIf { it in existingStyleNames } ?: currentStyleName
            return ReadStyleLanguageDecision(
                styleNameToSelect = target,
                scriptClass = script,
            )
        }
        if (script == null) {
            //语言未知时不提交记忆，等正文加载后再次识别并纠正
            return ReadStyleLanguageDecision(styleNameToSelect = currentStyleName)
        }
        val mapped = bindings.assignedName(script, existingStyleNames)
        return ReadStyleLanguageDecision(
            styleNameToSelect = mapped ?: currentStyleName,
            scriptClass = script,
            commitRememberedStyle = true,
        )
    }
}
