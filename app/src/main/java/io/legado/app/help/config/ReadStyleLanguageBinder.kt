package io.legado.app.help.config

import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookLanguageDetector
import io.legado.app.help.book.isImage

/**
 * 默认预设只在新书还没有记住名字时用一次。
 * 已经记住，或用户刚选过，正文重载（含转屏）不再改预设。
 */
internal fun allowContentStyleDefault(readStyleName: String?, userPinned: Boolean): Boolean {
    if (userPinned) return false
    return readStyleName.isNullOrBlank()
}

object ReadStyleLanguageBinder {
    fun apply(book: Book, extraSample: String? = null): Boolean {
        if (book.isImage || ReadBookConfig.onlyThisBook || ReadBookConfig.explicitStyleSelection) {
            return false
        }
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
        if (name.isBlank() || book.config.readStyleName == name) return
        book.config.readStyleName = name
        book.save()
    }
}
