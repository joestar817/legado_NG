package io.legado.app.ui.book.character

import android.content.Context
import android.net.Uri
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookTtsCastRole
import io.legado.app.ui.book.read.CharacterCardSelection
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject

data class BookCharacterFormValue(
    val name: String,
    val aliases: List<String>,
    val gender: String,
    val roleTag: String,
    val intro: String?,
    val imagePrompt: String? = null,
    val portraitUri: String? = null,
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
            imagePrompt = character.imagePrompt,
            portraitUri = character.portraitUri,
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
        context: Context,
        workKey: String,
        current: BookCharacter?,
        castRole: BookTtsCastRole?,
        value: BookCharacterFormValue,
        generatedFor: Book? = null,
    ): Long {
        val portraitChanged = value.portraitUri != current?.portraitUri
        val imported = value.portraitUri?.takeIf { portraitChanged }?.let {
            BookCharacterPortraits.copyFrom(context, it)
        }
        val now = System.currentTimeMillis()
        val mergedAliases = mergePromotedAliases(value.name, value.aliases, castRole)
        val dao = appDb.bookCharacterDao
        val (savedId, previousPortrait) = try {
            appDb.runInTransaction<Pair<Long, String?>> {
                val stored = current?.let {
                    val character = dao.getCharacter(it.id)
                    if (generatedFor != null && character != it) {
                        error(context.getString(R.string.ai_character_card_existing_changed))
                    }
                    checkNotNull(character).also { check(it.workKey == workKey) }
                }
                if (generatedFor != null) {
                    check(BookCharacterProfile.workKey(generatedFor.name, generatedFor.author) == workKey)
                    val conflict = CharacterCardSelection.generatedSaveConflict(
                        name = value.name,
                        aliases = mergedAliases,
                        existing = stored,
                        characters = dao.getCharacters(workKey),
                    )
                    check(conflict == null) {
                        context.getString(R.string.ai_character_card_name_conflict, conflict?.name)
                    }
                    dao.getOrCreateProfile(generatedFor.name, generatedFor.author, generatedFor.bookUrl)
                }
                val item = (stored?.copy() ?: BookCharacter(
                    workKey = workKey,
                    createdAt = now,
                    source = if (generatedFor == null) BookCharacter.Source.MANUAL else BookCharacter.Source.AI,
                )).apply {
                    name = value.name
                    gender = value.gender
                    roleTag = value.roleTag
                    identity = null
                    aliasesJson = mergedAliases.takeIf { it.isNotEmpty() }?.let(GSON::toJson)
                    intro = value.intro
                    imagePrompt = value.imagePrompt?.trim()?.ifBlank { null }
                    if (portraitChanged) portraitUri = imported?.let { Uri.fromFile(it).toString() }
                    shortIntro = null
                    updatedAt = now
                }
                val id = when {
                    item.id > 0L -> {
                        dao.updateCharacter(item)
                        item.id
                    }
                    castRole != null -> dao.insertCharacter(item)
                    else -> dao.insertManualCharacter(item)
                }
                castRole?.let { dao.mergeTtsCastRoleIntoCharacter(it, id) }
                dao.updateCharacterCount(workKey, now)
                id to stored?.portraitUri?.takeIf { portraitChanged }
            }
        } catch (error: Exception) {
            imported?.delete()
            throw error
        }
        runCatching { BookCharacterPortraits.discardIfUnreferenced(context, previousPortrait) }
        return savedId
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

internal fun BookCharacter.aliases(): List<String> {
    return aliasesJson?.let { GSON.fromJsonObject<List<String?>>(it).getOrNull() }
        ?.filterNotNull().orEmpty()
}

internal fun defaultPromotedRoleTag(gender: String): String = when (gender) {
    BookCharacter.Gender.MALE -> BookCharacter.RoleTag.MALE_LEAD
    BookCharacter.Gender.FEMALE -> BookCharacter.RoleTag.FEMALE_LEAD
    else -> BookCharacter.RoleTag.UNKNOWN
}
