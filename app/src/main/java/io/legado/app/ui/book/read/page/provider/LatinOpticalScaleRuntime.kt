package io.legado.app.ui.book.read.page.provider

import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookScriptClass
import io.legado.app.help.config.LatinOpticalScale
import io.legado.app.help.config.LatinOpticalScaleStore
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadValueScope

object LatinOpticalScaleRuntime {
    fun forBook(book: Book?): Float {
        if (book?.config?.scriptClass != BookScriptClass.Cjk.storageValue) return 1f
        return LatinOpticalScale.effective(
            scriptClass = book.config.scriptClass,
            manual = LatinOpticalScaleStore.manual(),
            cjkFace = ReadBookConfig.boundScriptFace(ReadValueScope.CJK),
            latinFace = ReadBookConfig.boundScriptFace(ReadValueScope.LATIN),
            estimateFaces = LatinGlyphProbe::estimate,
        )
    }

    fun epubSizeAdjust(book: Book?): Float? {
        if (book?.config?.scriptClass != BookScriptClass.Cjk.storageValue) return null
        return LatinOpticalScale.epubSizeAdjust(
            scriptClass = book.config.scriptClass,
            manual = LatinOpticalScaleStore.manual(),
            cjkFace = ReadBookConfig.boundScriptFace(ReadValueScope.CJK),
            latinFace = ReadBookConfig.boundScriptFace(ReadValueScope.LATIN),
            distinctLatinPath = ReadBookConfig.scriptFontPath(ReadValueScope.LATIN),
            estimateFaces = LatinGlyphProbe::estimate,
        )
    }
}
