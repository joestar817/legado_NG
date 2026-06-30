package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "aiSkills")
data class AiSkill(
    @PrimaryKey
    var id: String = "",
    var name: String = "",
    var description: String = "",
    var content: String = "",
    var scope: String = "AGENT",
    @ColumnInfo(defaultValue = "0")
    var builtIn: Boolean = false,
    @ColumnInfo(defaultValue = "1")
    var enabled: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    var userModified: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    var customOrder: Int = 0,
    @ColumnInfo(defaultValue = "0")
    var createdAt: Long = 0,
    @ColumnInfo(defaultValue = "0")
    var updatedAt: Long = 0
)
