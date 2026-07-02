package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookCharacters",
    indices = [
        Index(value = ["workKey"]),
        Index(value = ["workKey", "name"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = BookCharacterProfile::class,
            parentColumns = ["workKey"],
            childColumns = ["workKey"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BookCharacter(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(defaultValue = "")
    var workKey: String = "",
    @ColumnInfo(defaultValue = "")
    var name: String = "",
    @ColumnInfo(defaultValue = "unknown")
    var gender: String = Gender.UNKNOWN,
    @ColumnInfo(defaultValue = "unknown")
    var roleTag: String = RoleTag.UNKNOWN,
    var identity: String? = null,
    var aliasesJson: String? = null,
    var intro: String? = null,
    var shortIntro: String? = null,
    var avatarUri: String? = null,
    var portraitUri: String? = null,
    var imagePrompt: String? = null,
    @ColumnInfo(defaultValue = "1")
    var enabled: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    var sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "manual")
    var source: String = Source.MANUAL,
    @ColumnInfo(defaultValue = "1")
    var confidence: Float = 1f,
    @ColumnInfo(defaultValue = "0")
    var createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    var updatedAt: Long = System.currentTimeMillis()
) {
    object Gender {
        const val MALE = "male"
        const val FEMALE = "female"
        const val UNKNOWN = "unknown"
    }

    object RoleTag {
        const val MALE_LEAD = "male_lead"
        const val FEMALE_LEAD = "female_lead"
        const val MALE_SUPPORT = "male_support"
        const val FEMALE_SUPPORT = "female_support"
        const val PASSERBY = "passerby"
        const val OTHER = "other"
        const val UNKNOWN = "unknown"
    }

    object Source {
        const val MANUAL = "manual"
        const val AI = "ai"
        const val IMPORTED = "imported"
    }

    fun displayIntro(): String? {
        return intro?.takeIf { it.isNotBlank() }
            ?: shortIntro?.takeIf { it.isNotBlank() }
            ?: identity?.takeIf { it.isNotBlank() }
    }
}
