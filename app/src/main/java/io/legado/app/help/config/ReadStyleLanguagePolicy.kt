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
        val mapped = script?.let { bindings.assignedName(it, existingStyleNames) }
        return ReadStyleLanguageDecision(
            styleNameToSelect = mapped ?: currentStyleName,
            scriptClass = script,
            commitRememberedStyle = true,
        )
    }
}
