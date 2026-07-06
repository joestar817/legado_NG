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

    @Delete
    fun deleteCharacter(character: BookCharacter)

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
