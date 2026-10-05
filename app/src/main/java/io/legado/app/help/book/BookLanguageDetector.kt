package io.legado.app.help.book

import io.legado.app.data.entities.Book

enum class BookScriptClass(val storageValue: String) {
    Cjk("cjk"),
    Latin("latin"),
    Other("other");

    companion object {
        fun fromStorage(value: String?): BookScriptClass? =
            entries.firstOrNull { it.storageValue == value }
    }
}

object BookLanguageDetector {
    private val cjkPrimary = setOf(
        "zh", "zho", "chi", "cmn", "yue", "wuu", "nan", "hak", "lzh", "gan", "hsn",
        "ja", "jpn", "ko", "kor",
    )
    private val latinPrimary = setOf(
        "en", "fr", "de", "es", "it", "pt", "nl", "sv", "da", "no", "nb", "nn", "fi",
        "pl", "cs", "sk", "hu", "ro", "tr", "id", "ms", "vi", "ca", "eu", "gl", "cy",
        "ga", "is", "lt", "lv", "et", "sl", "hr", "bs", "sq", "af", "sw", "tl", "fil",
        "la", "eo", "mt", "lb", "rm", "br", "gd", "oc", "ast", "fy",
    )
    private val cjkScripts = setOf("hani", "hans", "hant", "jpan", "kore", "hang", "hrkt", "kana", "hira")
    private val latinScripts = setOf("latn")
    private val otherScripts = setOf(
        "cyrl", "arab", "hebr", "thai", "deva", "grek", "armn", "geor", "ethi", "beng",
    )
    private val languageTagPrimary = Regex("[a-z]{2,3}")

    fun detect(metadataLanguage: String?, samples: List<String>): BookScriptClass? {
        val fromSample = classifyScript(samples.joinToString("\n"))
        if (fromSample != null) return fromSample
        return fromLanguageTag(metadataLanguage)
    }

    fun detect(book: Book, extraSample: String? = null): BookScriptClass? {
        return detect(
            metadataLanguage = book.config.languageHint,
            samples = bookMetadataSamples(book) + listOfNotNull(extraSample),
        )
    }

    fun bookMetadataSamples(book: Book): List<String> = listOfNotNull(
        book.name,
        book.author,
        book.kind,
        book.customTag,
        book.intro,
        book.customIntro,
    )

    fun fromLanguageTag(tag: String?): BookScriptClass? {
        val normalized = tag?.trim()?.lowercase()?.replace('_', '-') ?: return null
        if (normalized.isEmpty()) return null
        val parts = normalized.split('-').filter { it.isNotEmpty() }
        val primary = parts.firstOrNull() ?: return null
        val scriptSub = parts.drop(1).firstOrNull { it.length == 4 }
        when (scriptSub) {
            in cjkScripts -> return BookScriptClass.Cjk
            in latinScripts -> return BookScriptClass.Latin
            in otherScripts -> return BookScriptClass.Other
        }
        when (primary) {
            in cjkPrimary -> return BookScriptClass.Cjk
            in latinPrimary -> return BookScriptClass.Latin
        }
        if (primary.matches(languageTagPrimary)) return BookScriptClass.Other
        return null
    }

    fun classifyScript(text: String): BookScriptClass? {
        var cjk = 0
        var latin = 0
        var otherLetters = 0
        for (ch in text) {
            if (!ch.isLetter()) continue
            when {
                ch.isCjkLike() -> cjk++
                ch.isLatinLetter() -> latin++
                else -> otherLetters++
            }
        }
        val letters = cjk + latin + otherLetters
        if (cjk >= 4 && (cjk >= latin || cjk >= 8)) return BookScriptClass.Cjk
        if (letters < 12) return null
        if (cjk * 5 >= letters) return BookScriptClass.Cjk
        if (latin > otherLetters && cjk * 20 < letters) return BookScriptClass.Latin
        return BookScriptClass.Other
    }

    private fun Char.isCjkLike(): Boolean {
        val block = Character.UnicodeBlock.of(this) ?: return false
        val name = block.toString()
        return name.contains("CJK") ||
            name.contains("HIRAGANA") ||
            name.contains("KATAKANA") ||
            name.contains("HANGUL") ||
            name.contains("BOPOMOFO") ||
            name.contains("IDEOGRAPH")
    }

    private fun Char.isLatinLetter(): Boolean {
        if (!isLetter()) return false
        val block = Character.UnicodeBlock.of(this) ?: return false
        return block.toString().contains("LATIN")
    }
}
