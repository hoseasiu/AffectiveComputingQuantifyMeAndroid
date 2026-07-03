package edu.mit.media.mysnapshot.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExperimentDao {
    @Insert
    suspend fun insert(experiment: ExperimentEntity): Long

    @Update
    suspend fun update(experiment: ExperimentEntity)

    @Delete
    suspend fun delete(experiment: ExperimentEntity)

    @Query("SELECT * FROM experiments WHERE id = :id")
    fun getById(id: Int): Flow<ExperimentEntity?>

    @Query("SELECT * FROM experiments WHERE is_active = 1 LIMIT 1")
    fun getCurrentExperiment(): Flow<ExperimentEntity?>

    // The most recently started experiment, active or not. This is the on-device
    // equivalent of the old `Experiment.CURRENT_EXPERIMENT_PREF` SharedPreferences pointer:
    // it keeps pointing at a just-completed experiment (so the results screen can still be
    // reached) until a newer experiment is started or this one is cancelled.
    @Query("SELECT * FROM experiments ORDER BY start_time DESC LIMIT 1")
    fun getLatestExperiment(): Flow<ExperimentEntity?>

    @Query("SELECT * FROM experiments ORDER BY start_time DESC")
    fun getAllExperiments(): Flow<List<ExperimentEntity>>

    @Query("SELECT * FROM experiments WHERE is_active = 0 OR is_cancelled = 1 ORDER BY start_time DESC")
    fun getCompletedExperiments(): Flow<List<ExperimentEntity>>

    @Query("DELETE FROM experiments WHERE id = :id")
    suspend fun deleteById(id: Int)
}
