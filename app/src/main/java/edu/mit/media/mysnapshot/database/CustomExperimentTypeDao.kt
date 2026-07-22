package edu.mit.media.mysnapshot.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomExperimentTypeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CustomExperimentTypeEntity)

    @Query("SELECT * FROM custom_experiment_types ORDER BY created_at DESC")
    fun getAll(): Flow<List<CustomExperimentTypeEntity>>

    @Query("DELETE FROM custom_experiment_types WHERE type_key = :typeKey")
    suspend fun deleteByKey(typeKey: String)

    // How many experiments (past or present) used this custom type -- lets #33/#34 warn
    // before deleting a type that's still referenced by experiment history, rather than
    // leaving those rows pointing at a typeKey that ExperimentType.fromTypeKey can no longer
    // resolve.
    @Query("SELECT COUNT(*) FROM experiments WHERE type = :typeKey")
    suspend fun countByType(typeKey: String): Int
}
