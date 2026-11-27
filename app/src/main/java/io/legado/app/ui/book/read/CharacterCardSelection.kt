package io.legado.app.ui.book.read

import io.legado.app.data.entities.BookCharacter
import io.legado.app.ui.book.character.aliases

/**
 * 正文选中文字与已有角色卡的匹配规则。
 *
 * 只接受单个名字（允许被引号/括号包裹），优先匹配角色名，再匹配别称；
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
        return characters.firstOrNull { comparisonKey(it.name) == selectedKey }
            ?: characters.firstOrNull { character ->
                character.matchesAlias(selectedKey)
            }
    }

    fun generatedSaveConflict(
        name: String,
        aliases: List<String>,
        existing: BookCharacter?,
        characters: List<BookCharacter>,
    ): BookCharacter? {
        val otherCards = characters.filterNot { it.id == existing?.id }
        if (existing == null || comparisonKey(name) != comparisonKey(existing.name)) {
            match(name, otherCards)?.let { return it }
        }
        val previousAliases = existing?.aliases().orEmpty().mapTo(hashSetOf(), ::comparisonKey)
        return aliases.firstNotNullOfOrNull { alias ->
            if (comparisonKey(alias) in previousAliases) null else match(alias, otherCards)
        }
    }

    fun normalizeSelectedName(text: String): String? {
        val name = unwrappedName(text)
        if (name.isBlank() || name.length > 24 || name.contains('\n')) return null
        if (name.any { it in sentencePunctuation }) return null
        return name
    }

    private fun BookCharacter.matchesAlias(selectedKey: String): Boolean {
        return aliases().any { comparisonKey(it) == selectedKey }
    }

    internal fun unwrappedName(value: String): String = value.trim().trim(*boundaryChars).trim()

    private fun comparisonKey(value: String): String {
        return unwrappedName(value)
            .replace(Regex("\\s+"), " ")
            .lowercase()
    }
}
