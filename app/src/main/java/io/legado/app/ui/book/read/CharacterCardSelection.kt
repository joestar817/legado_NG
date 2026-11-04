package io.legado.app.ui.book.read

import io.legado.app.data.entities.BookCharacter
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject

/**
 * 正文选中文字与已有角色卡的匹配规则。
 *
 * 只接受单个名字（允许被引号/括号包裹），并同时匹配角色名和别称；
 * 句子、含标点的长文本不会被当成角色，避免误触「查看角色卡」。
 */
internal object CharacterCardSelection {

    private val boundaryChars = charArrayOf(
        '“', '”', '"', '‘', '’', '「', '」', '『', '』',
        '（', '）', '(', ')', '【', '】', '[', ']',
    )

    private val sentencePunctuation = "，。！？；：、,.!?;:"

    fun match(selectedText: String, characters: List<BookCharacter>): BookCharacter? {
        val selectedKey = normalizeSelectedName(selectedText)?.let(::comparisonKey) ?: return null
        if (selectedKey.isEmpty()) return null
        return characters.firstOrNull { character ->
            character.matches(selectedKey)
        }
    }

    fun normalizeSelectedName(text: String): String? {
        val name = text.trim()
            .trim(*boundaryChars)
            .trim()
        if (name.isBlank() || name.length > 24 || name.contains('\n')) return null
        if (name.any { it in sentencePunctuation }) return null
        return name
    }

    private fun BookCharacter.matches(selectedKey: String): Boolean {
        if (comparisonKey(name) == selectedKey) return true
        val aliases = aliasesJson
            ?.let { GSON.fromJsonObject<List<String>>(it).getOrNull() }
            .orEmpty()
        return aliases.any { comparisonKey(it) == selectedKey }
    }

    private fun comparisonKey(value: String): String {
        return value.trim()
            .trim(*boundaryChars)
            .replace(Regex("\\s+"), " ")
            .lowercase()
    }
}
