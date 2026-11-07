package com.europartes.mobile.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * An encrypted, account-scoped self-assessment draft. The questionnaire version is part of the
 * identity so a new server version can never reopen a previous version's answers.
 */
@Entity(
    tableName = "self_assessment_drafts",
    indices = [
        Index(
            value = ["ownerUserId", "questionnaireCode", "questionnaireVersion", "period"],
            unique = true,
        ),
    ],
)
data class SelfAssessmentDraftEntity(
    @PrimaryKey val draftKey: String,
    val ownerUserId: Int,
    val questionnaireCode: String,
    val questionnaireVersion: Int,
    val period: String,
    val encryptedPayload: String,
    val updatedAtEpochMs: Long,
)

@Dao
interface SelfAssessmentDraftDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(draft: SelfAssessmentDraftEntity)

    @Query(
        "SELECT * FROM self_assessment_drafts WHERE ownerUserId = :ownerUserId " +
            "AND questionnaireCode = :questionnaireCode " +
            "AND questionnaireVersion = :questionnaireVersion AND period = :period LIMIT 1",
    )
    suspend fun find(
        ownerUserId: Int,
        questionnaireCode: String,
        questionnaireVersion: Int,
        period: String,
    ): SelfAssessmentDraftEntity?

    @Query(
        "SELECT * FROM self_assessment_drafts WHERE ownerUserId = :ownerUserId " +
            "AND questionnaireCode = :questionnaireCode " +
            "AND questionnaireVersion = :questionnaireVersion AND period = :period LIMIT 1",
    )
    fun observe(
        ownerUserId: Int,
        questionnaireCode: String,
        questionnaireVersion: Int,
        period: String,
    ): Flow<SelfAssessmentDraftEntity?>

    @Query(
        "DELETE FROM self_assessment_drafts WHERE ownerUserId = :ownerUserId " +
            "AND questionnaireCode = :questionnaireCode " +
            "AND questionnaireVersion = :questionnaireVersion AND period = :period",
    )
    suspend fun delete(
        ownerUserId: Int,
        questionnaireCode: String,
        questionnaireVersion: Int,
        period: String,
    )
}
