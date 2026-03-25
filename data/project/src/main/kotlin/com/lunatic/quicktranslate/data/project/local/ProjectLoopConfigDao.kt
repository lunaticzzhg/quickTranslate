package com.lunatic.quicktranslate.data.project.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProjectLoopConfigDao {
    @Query("SELECT * FROM project_loop_configs WHERE projectId = :projectId LIMIT 1")
    suspend fun getByProjectId(projectId: Long): ProjectLoopConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProjectLoopConfigEntity)
}
