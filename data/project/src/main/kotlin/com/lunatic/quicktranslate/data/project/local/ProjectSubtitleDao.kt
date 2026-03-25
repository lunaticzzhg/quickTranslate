package com.lunatic.quicktranslate.data.project.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ProjectSubtitleDao {
    @Query("SELECT * FROM project_subtitles WHERE projectId = :projectId ORDER BY sequenceIndex ASC")
    suspend fun getByProjectId(projectId: Long): List<ProjectSubtitleEntity>

    @Query("DELETE FROM project_subtitles WHERE projectId = :projectId")
    suspend fun deleteByProjectId(projectId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ProjectSubtitleEntity>)

    @Transaction
    suspend fun replaceByProjectId(
        projectId: Long,
        entities: List<ProjectSubtitleEntity>
    ) {
        deleteByProjectId(projectId)
        if (entities.isNotEmpty()) {
            insertAll(entities)
        }
    }
}
