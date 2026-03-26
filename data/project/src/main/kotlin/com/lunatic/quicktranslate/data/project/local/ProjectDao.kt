package com.lunatic.quicktranslate.data.project.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAtEpochMs DESC")
    fun observeRecentProjects(): Flow<List<ProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity): Long

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ProjectEntity?

    @Query(
        """
        SELECT * FROM projects
        WHERE mediaUri = :mediaUri OR sourceUri = :mediaUri
        ORDER BY updatedAtEpochMs DESC
        LIMIT 1
        """
    )
    suspend fun getByMediaUri(mediaUri: String): ProjectEntity?

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "UPDATE projects SET subtitleStatus = :subtitleStatus, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id"
    )
    suspend fun updateSubtitleStatus(
        id: Long,
        subtitleStatus: String,
        updatedAtEpochMs: Long
    )

    @Query(
        "UPDATE projects SET mediaUri = :mediaUri, mimeType = :mimeType, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id"
    )
    suspend fun updateMediaSource(
        id: Long,
        mediaUri: String,
        mimeType: String,
        updatedAtEpochMs: Long
    )
}
