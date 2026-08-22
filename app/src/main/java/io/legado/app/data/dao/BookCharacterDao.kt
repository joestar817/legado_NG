package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookCharacterTtsBinding
import io.legado.app.data.entities.BookTtsCastRole
import io.legado.app.data.entities.BookTtsCastRoleContribution
import kotlinx.coroutines.flow.Flow

@Dao
interface BookCharacterDao {

    @Query("select * from bookCharacterProfiles where workKey = :workKey")
    fun getProfile(workKey: String): BookCharacterProfile?

    @Query("select * from bookCharacterProfiles where workKey = :workKey")
    fun flowProfile(workKey: String): Flow<BookCharacterProfile?>

    @Query("select * from bookCharacterProfiles")
    fun getProfiles(): List<BookCharacterProfile>

    @Query("select * from bookCharacters where workKey = :workKey order by sortOrder asc, id asc")
    fun getCharacters(workKey: String): List<BookCharacter>

    @Query("select * from bookCharacters where workKey = :workKey order by sortOrder asc, id asc")
    fun flowCharacters(workKey: String): Flow<List<BookCharacter>>

    @Query("select * from bookCharacterTtsBindings where workKey = :workKey")
    fun getTtsBindings(workKey: String): List<BookCharacterTtsBinding>

    @Query("select * from bookCharacterTtsBindings where workKey = :workKey")
    fun flowTtsBindings(workKey: String): Flow<List<BookCharacterTtsBinding>>

    @Query("select * from bookTtsCastRoles where workKey = :workKey order by occurrenceCount desc, id asc")
    fun getTtsCastRoles(workKey: String): List<BookTtsCastRole>

    @Query("select * from bookTtsCastRoles where workKey = :workKey order by occurrenceCount desc, id asc")
    fun flowTtsCastRoles(workKey: String): Flow<List<BookTtsCastRole>>

    @Query("select * from bookTtsCastRoles where workKey = :workKey and name = :name limit 1")
    fun getTtsCastRoleByName(workKey: String, name: String): BookTtsCastRole?

    @Query("select * from bookTtsCastRoles where id = :id")
    fun getTtsCastRole(id: Long): BookTtsCastRole?

    @Query("select * from bookCharacters where id = :id")
    fun getCharacter(id: Long): BookCharacter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertProfile(profile: BookCharacterProfile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCharacter(character: BookCharacter): Long

    @Update
    fun updateProfile(profile: BookCharacterProfile)

    @Update
    fun updateCharacter(character: BookCharacter)

    @Update
    fun updateCharacters(vararg characters: BookCharacter)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertTtsBinding(binding: BookCharacterTtsBinding)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertTtsCastRole(role: BookTtsCastRole): Long

    @Update
    fun updateTtsCastRole(role: BookTtsCastRole)

    @Query("select * from bookTtsCastRoleContributions where workKey = :workKey")
    fun getTtsCastRoleContributions(workKey: String): List<BookTtsCastRoleContribution>

    @Query("select * from bookTtsCastRoleContributions where workKey = :workKey and chapterIndex = :chapterIndex")
    fun getTtsCastRoleContributions(
        workKey: String,
        chapterIndex: Int
    ): List<BookTtsCastRoleContribution>

    @Query("select * from bookTtsCastRoleContributions where roleId = :roleId")
    fun getTtsCastRoleContributions(roleId: Long): List<BookTtsCastRoleContribution>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertTtsCastRoleContribution(contribution: BookTtsCastRoleContribution)

    @Delete
    fun deleteTtsCastRoleContribution(contribution: BookTtsCastRoleContribution)

    @Query("delete from bookTtsCastRoleContributions where workKey = :workKey and chapterIndex = :chapterIndex")
    fun deleteTtsCastRoleContributions(workKey: String, chapterIndex: Int)

    @Query(
        "delete from bookTtsCastRoleContributions " +
            "where workKey = :workKey and chapterIndex = :chapterIndex " +
            "and (cacheKey != :cacheKey or cacheRevision != :cacheRevision)"
    )
    fun deleteStaleTtsCastRoleContributions(
        workKey: String,
        chapterIndex: Int,
        cacheKey: String,
        cacheRevision: Long
    )

    @Delete
    fun deleteCharacter(character: BookCharacter)

    @Delete
    fun deleteTtsCastRole(role: BookTtsCastRole)

    @Query("delete from bookCharacterTtsBindings where workKey = :workKey and targetType = :targetType and targetId = :targetId and engineId = :engineId")
    fun deleteTtsBinding(workKey: String, targetType: String, targetId: Long, engineId: String)

    @Query("delete from bookCharacterTtsBindings where workKey = :workKey and targetType = :targetType and targetId = :targetId")
    fun deleteTtsBindings(workKey: String, targetType: String, targetId: Long)

    @Query("update bookTtsCastRoles set ignored = 1, updatedAt = :updatedAt where id = :roleId")
    fun markTtsCastRoleIgnored(roleId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("update bookTtsCastRoles set ignored = 0, updatedAt = :updatedAt where id = :roleId")
    fun restoreTtsCastRole(roleId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("delete from bookTtsCastRoles where id = :roleId and ignored = 1 and linkedCharacterId is null")
    fun deleteIgnoredTtsCastRole(roleId: Long): Int

    @Transaction
    fun ignoreTtsCastRole(role: BookTtsCastRole) {
        markTtsCastRoleIgnored(role.id)
    }

    @Transaction
    fun deleteTtsCastRoleWithTts(role: BookTtsCastRole) {
        deleteTtsBindings(
            role.workKey,
            BookCharacterTtsBinding.TargetType.CAST_ROLE,
            role.id
        )
        deleteTtsCastRole(role)
    }

    @Transaction
    fun permanentlyDeleteIgnoredTtsCastRole(roleId: Long): Boolean {
        val role = getTtsCastRole(roleId) ?: return false
        if (!role.ignored || role.linkedCharacterId != null) return false
        deleteTtsBindings(
            role.workKey,
            BookCharacterTtsBinding.TargetType.CAST_ROLE,
            role.id
        )
        return deleteIgnoredTtsCastRole(role.id) > 0
    }

    @Transaction
    fun mergeTtsCastRoleIntoCharacter(role: BookTtsCastRole, characterId: Long) {
        val bindings = getTtsBindings(role.workKey)
        bindings.filter {
            it.targetType == BookCharacterTtsBinding.TargetType.CAST_ROLE &&
                it.targetId == role.id
        }.forEach { castBinding ->
            val hasCharacterBinding = bindings.any {
                it.targetType == BookCharacterTtsBinding.TargetType.CHARACTER &&
                    it.targetId == characterId &&
                    it.engineId == castBinding.engineId
            }
            if (!hasCharacterBinding) {
                upsertTtsBinding(
                    castBinding.copy(
                        targetType = BookCharacterTtsBinding.TargetType.CHARACTER,
                        targetId = characterId,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        deleteTtsBindings(
            role.workKey,
            BookCharacterTtsBinding.TargetType.CAST_ROLE,
            role.id
        )
        deleteTtsCastRole(role)
    }

    @Transaction
    fun deleteCharacterWithTts(character: BookCharacter) {
        // Old builds kept promoted cast roles as linked shadow records. Remove those
        // records instead of unlinking them back into the temporary role pool.
        getTtsCastRoles(character.workKey)
            .filter { it.linkedCharacterId == character.id }
            .forEach { role ->
                deleteTtsBindings(
                    role.workKey,
                    BookCharacterTtsBinding.TargetType.CAST_ROLE,
                    role.id
                )
                deleteTtsCastRole(role)
            }
        deleteTtsBindings(
            character.workKey,
            BookCharacterTtsBinding.TargetType.CHARACTER,
            character.id
        )
        deleteCharacter(character)
        updateCharacterCount(character.workKey)
    }

    @Query("delete from bookCharacterProfiles where workKey = :workKey")
    fun deleteProfile(workKey: String)

    @Query("update bookCharacterProfiles set characterCount = (select count(*) from bookCharacters where workKey = :workKey), updatedAt = :updatedAt where workKey = :workKey")
    fun updateCharacterCount(workKey: String, updatedAt: Long = System.currentTimeMillis())

    @Transaction
    fun getOrCreateProfile(bookName: String, bookAuthor: String, bookUrl: String?): BookCharacterProfile {
        val workKey = BookCharacterProfile.workKey(bookName, bookAuthor)
        val now = System.currentTimeMillis()
        val oldProfile = getProfile(workKey)
        if (oldProfile != null) {
            if (oldProfile.latestBookUrl != bookUrl || oldProfile.bookName != bookName || oldProfile.bookAuthor != bookAuthor) {
                oldProfile.bookName = bookName
                oldProfile.bookAuthor = bookAuthor
                oldProfile.latestBookUrl = bookUrl
                oldProfile.updatedAt = now
                updateProfile(oldProfile)
            }
            return oldProfile
        }
        val profile = BookCharacterProfile(
            workKey = workKey,
            bookName = bookName,
            bookAuthor = bookAuthor,
            latestBookUrl = bookUrl,
            createdAt = now,
            updatedAt = now
        )
        insertProfile(profile)
        return profile
    }
}
