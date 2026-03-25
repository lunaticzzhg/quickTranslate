package com.lunatic.quicktranslate.data.project.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProjectPlaybackStateDao {
    @Query("SELECT * FROM project_playback_states WHERE projectId = :projectId LIMIT 1")
    suspend fun getByProjectId(projectId: Long): ProjectPlaybackStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProjectPlaybackStateEntity)
}
