package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "bookCharacterTtsBindings",
    primaryKeys = ["workKey", "targetType", "targetId"],
    indices = [
        Index(value = ["workKey"]),
        Index(value = ["workKey", "targetType", "targetId"], unique = true)
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
data class BookCharacterTtsBinding(
    @ColumnInfo(defaultValue = "")
    var workKey: String = "",
    @ColumnInfo(defaultValue = "character")
    var targetType: String = TargetType.CHARACTER,
    @ColumnInfo(defaultValue = "0")
    var targetId: Long = 0L,
    @ColumnInfo(defaultValue = "")
    var engineId: String = "",
    var voiceId: String? = null,
    @ColumnInfo(defaultValue = "{}")
    var emotionStyleMapJson: String = "{}",
    @ColumnInfo(defaultValue = "0")
    var createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    var updatedAt: Long = System.currentTimeMillis()
) {
    object TargetType {
        const val NARRATOR = "narrator"
        const val CHARACTER = "character"
        const val DIALOGUE_MALE = "dialogue_male"
        const val DIALOGUE_FEMALE = "dialogue_female"
    }

    companion object {
        fun narrator(workKey: String): BookCharacterTtsBinding {
            return BookCharacterTtsBinding(
                workKey = workKey,
                targetType = TargetType.NARRATOR,
                targetId = 0L
            )
        }

        fun character(workKey: String, characterId: Long): BookCharacterTtsBinding {
            return BookCharacterTtsBinding(
                workKey = workKey,
                targetType = TargetType.CHARACTER,
                targetId = characterId
            )
        }

        fun dialogueMale(workKey: String): BookCharacterTtsBinding {
            return BookCharacterTtsBinding(
                workKey = workKey,
                targetType = TargetType.DIALOGUE_MALE,
                targetId = 0L
            )
        }

        fun dialogueFemale(workKey: String): BookCharacterTtsBinding {
            return BookCharacterTtsBinding(
                workKey = workKey,
                targetType = TargetType.DIALOGUE_FEMALE,
                targetId = 0L
            )
        }
    }
}
