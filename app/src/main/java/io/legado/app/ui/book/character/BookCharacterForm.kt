package io.legado.app.ui.book.character

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookTtsCastRole
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject

data class BookCharacterFormValue(
    val name: String,
    val aliases: List<String>,
    val gender: String,
    val roleTag: String,
    val intro: String?
)

internal fun initialCharacterFormValue(
    character: BookCharacter?,
    castRole: BookTtsCastRole?
): BookCharacterFormValue {
    if (character != null) {
        return BookCharacterFormValue(
            name = character.name,
            aliases = character.aliases(),
            gender = character.gender.takeIf { it in BookCharacterLabels.genderValues }
                ?: BookCharacter.Gender.UNKNOWN,
            roleTag = character.roleTag.takeIf { it in BookCharacterLabels.roleValues }
                ?: BookCharacter.RoleTag.UNKNOWN,
            intro = character.displayIntro(),
        )
    }
    if (castRole != null) {
        return BookCharacterFormValue(
            name = castRole.name,
            aliases = GSON.fromJsonObject<List<String>>(castRole.aliasesJson)
                .getOrNull()
                .orEmpty(),
            gender = castRole.gender.takeIf { it in BookCharacterLabels.genderValues }
                ?: BookCharacter.Gender.UNKNOWN,
            roleTag = defaultPromotedRoleTag(castRole.gender),
            intro = null,
        )
    }
    return BookCharacterFormValue(
        name = "",
        aliases = emptyList(),
        gender = BookCharacter.Gender.UNKNOWN,
        roleTag = BookCharacter.RoleTag.UNKNOWN,
        intro = null,
    )
}

internal fun parseCharacterAliases(raw: String): List<String> = raw
    .split(",", "，", "/", "、")
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()

object BookCharacterEditor {
    fun save(
        workKey: String,
        current: BookCharacter?,
        castRole: BookTtsCastRole?,
        value: BookCharacterFormValue
    ): Long {
        val now = System.currentTimeMillis()
        val mergedAliases = mergePromotedAliases(
            canonicalName = value.name,
            formAliases = value.aliases,
            castRole = castRole
        )
        val item = (current ?: BookCharacter(workKey = workKey, createdAt = now)).apply {
            name = value.name
            gender = value.gender
            roleTag = value.roleTag
            identity = null
            aliasesJson = mergedAliases.takeIf { it.isNotEmpty() }?.let(GSON::toJson)
            intro = value.intro
            shortIntro = null
            updatedAt = now
        }
        return appDb.runInTransaction<Long> {
            val savedId = if (item.id == 0L) {
                appDb.bookCharacterDao.insertCharacter(item)
            } else {
                appDb.bookCharacterDao.updateCharacter(item)
                item.id
            }
            castRole?.let { appDb.bookCharacterDao.mergeTtsCastRoleIntoCharacter(it, savedId) }
            appDb.bookCharacterDao.updateCharacterCount(workKey, now)
            savedId
        }
    }
}

internal fun mergePromotedAliases(
    canonicalName: String,
    formAliases: List<String>,
    castRole: BookTtsCastRole?
): List<String> = buildList {
    addAll(formAliases)
    castRole?.let { role ->
        add(role.name)
        addAll(
            GSON.fromJsonObject<List<String>>(role.aliasesJson)
                .getOrNull()
                .orEmpty()
        )
    }
}.map(String::trim)
    .filter { it.isNotBlank() && !it.equals(canonicalName.trim(), ignoreCase = true) }
    .distinctBy { it.lowercase() }

private fun BookCharacter.aliases(): List<String> {
    return aliasesJson?.let { GSON.fromJsonObject<List<String>>(it).getOrNull() }.orEmpty()
}

internal fun defaultPromotedRoleTag(gender: String): String = when (gender) {
    BookCharacter.Gender.MALE -> BookCharacter.RoleTag.MALE_LEAD
    BookCharacter.Gender.FEMALE -> BookCharacter.RoleTag.FEMALE_LEAD
    else -> BookCharacter.RoleTag.UNKNOWN
}
