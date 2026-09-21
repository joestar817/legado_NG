package io.legado.app.help.config

import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookLanguageDetector
import io.legado.app.help.book.isImage

object ReadStyleLanguageBinder {
    fun apply(book: Book, extraSample: String? = null): Boolean {
        if (book.isImage) return false
        val names = ReadBookConfig.configList.map { it.name }
        if (names.isEmpty()) return false
        val currentName = ReadBookConfig.durConfig.name
        val decision = ReadStyleLanguagePolicy.decide(
            rememberedStyleName = book.config.readStyleName,
            languageHint = book.config.languageHint,
            sampleTexts = BookLanguageDetector.bookMetadataSamples(book) + listOfNotNull(extraSample),
            existingStyleNames = names,
            currentStyleName = currentName,
            bindings = ReadStyleLanguageMap.current(),
        )
        var persist = false
        if (decision.scriptClass != null &&
            book.config.scriptClass != decision.scriptClass.storageValue
        ) {
            book.config.scriptClass = decision.scriptClass.storageValue
            persist = true
        }
        if (decision.commitRememberedStyle && book.config.readStyleName != decision.styleNameToSelect) {
            book.config.readStyleName = decision.styleNameToSelect
            persist = true
        }
        val changed = ReadBookConfig.selectStyleByName(decision.styleNameToSelect)
        if (persist) book.save()
        return changed
    }

    fun rememberCurrentStyle(book: Book) {
        if (book.isImage) return
        val name = ReadBookConfig.durConfig.name
        if (book.config.readStyleName != name) {
            book.config.readStyleName = name
            book.save()
        }
    }
}
