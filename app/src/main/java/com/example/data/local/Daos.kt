package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeProject(id: Long): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: Long): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: Long)

    @Query("UPDATE projects SET lastProcessedMs = :processedMs, status = :status, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE id = :projectId")
    suspend fun updateProcessingState(
        projectId: Long,
        processedMs: Long,
        status: String,
        errorMessage: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE projects SET detectedLangCode = :detectedLang, updatedAt = :updatedAt WHERE id = :projectId")
    suspend fun updateDetectedLang(
        projectId: Long,
        detectedLang: String,
        updatedAt: Long = System.currentTimeMillis()
    )
}

@Dao
interface SegmentDao {
    @Query("SELECT * FROM segments WHERE projectId = :projectId ORDER BY orderIndex ASC, startTimeMs ASC")
    fun observeSegments(projectId: Long): Flow<List<SegmentEntity>>

    @Query("SELECT * FROM segments WHERE projectId = :projectId ORDER BY orderIndex ASC, startTimeMs ASC")
    suspend fun getSegmentsOnce(projectId: Long): List<SegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegment(segment: SegmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<SegmentEntity>): List<Long>

    @Update
    suspend fun updateSegment(segment: SegmentEntity)

    @Query("DELETE FROM segments WHERE id = :segmentId")
    suspend fun deleteSegmentById(segmentId: Long)

    @Query("DELETE FROM segments WHERE projectId = :projectId")
    suspend fun deleteAllForProject(projectId: Long)

    @Query("SELECT COUNT(*) FROM segments WHERE projectId = :projectId")
    suspend fun countForProject(projectId: Long): Int

    @Transaction
    suspend fun replaceAllForProject(projectId: Long, newSegments: List<SegmentEntity>): List<Long> {
        deleteAllForProject(projectId)
        return insertSegments(newSegments)
    }
}

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translations WHERE projectId = :projectId AND targetLangCode = :targetLangCode")
    fun observeTranslations(projectId: Long, targetLangCode: String): Flow<List<TranslationEntity>>

    @Query("SELECT * FROM translations WHERE projectId = :projectId")
    suspend fun getAllForProjectOnce(projectId: Long): List<TranslationEntity>

    @Query("SELECT * FROM translations WHERE projectId = :projectId AND targetLangCode = :targetLangCode")
    suspend fun getForProjectAndLangOnce(projectId: Long, targetLangCode: String): List<TranslationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTranslation(translation: TranslationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTranslations(translations: List<TranslationEntity>)

    @Query("DELETE FROM translations WHERE segmentId = :segmentId")
    suspend fun deleteForSegment(segmentId: Long)
}

@Dao
interface GlossaryDao {
    @Query("SELECT * FROM glossary_rules ORDER BY createdAt DESC")
    fun observeAllRules(): Flow<List<GlossaryRuleEntity>>

    @Query("SELECT * FROM glossary_rules ORDER BY createdAt DESC")
    suspend fun getAllRulesOnce(): List<GlossaryRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: GlossaryRuleEntity): Long

    @Query("DELETE FROM glossary_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)
}

@Dao
interface LanguagePackDao {
    @Query("SELECT * FROM language_packs ORDER BY category ASC, displayName ASC")
    fun observeAllPacks(): Flow<List<LanguagePackEntity>>

    @Query("SELECT * FROM language_packs")
    suspend fun getAllPacksOnce(): List<LanguagePackEntity>

    @Query("SELECT * FROM language_packs WHERE id = :id")
    suspend fun getPackById(id: String): LanguagePackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPack(pack: LanguagePackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPacks(packs: List<LanguagePackEntity>)

    @Query("UPDATE language_packs SET status = :status, downloadedBytes = :downloadedBytes, errorMessage = :errorMessage, localPath = :localPath, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePackState(
        id: String,
        status: String,
        downloadedBytes: Long,
        localPath: String? = null,
        errorMessage: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    )
}
