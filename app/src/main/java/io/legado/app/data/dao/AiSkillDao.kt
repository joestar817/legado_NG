package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.AiSkill
import kotlinx.coroutines.flow.Flow

@Dao
interface AiSkillDao {

    @Query("select * from aiSkills order by builtIn desc, customOrder asc, name asc")
    fun flowAll(): Flow<List<AiSkill>>

    @get:Query("select * from aiSkills order by builtIn desc, customOrder asc, name asc")
    val all: List<AiSkill>

    @Query("select * from aiSkills where id = :id")
    fun get(id: String): AiSkill?

    @Query("select * from aiSkills where name = :name limit 1")
    fun getByName(name: String): AiSkill?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg skills: AiSkill)

    @Update
    fun update(vararg skills: AiSkill)

    @Delete
    fun delete(vararg skills: AiSkill)

    @Query("delete from aiSkills where id = :id and builtIn = 0")
    fun deleteCustom(id: String): Int
}
