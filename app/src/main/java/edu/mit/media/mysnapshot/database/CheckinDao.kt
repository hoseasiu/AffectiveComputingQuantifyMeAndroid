package edu.mit.media.mysnapshot.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.OffsetDateTime

@Dao
interface CheckinDao {
    @Insert
    suspend fun insert(checkin: CheckinEntity): Long

    @Update
    suspend fun update(checkin: CheckinEntity)

    @Delete
    suspend fun delete(checkin: CheckinEntity)

    @Query("SELECT * FROM checkins WHERE id = :id")
    fun getById(id: Int): Flow<CheckinEntity?>

    @Query("SELECT * FROM checkins WHERE experiment_id = :experimentId ORDER BY checkin_date DESC")
    fun getCheckinsForExperiment(experimentId: Int): Flow<List<CheckinEntity>>

    @Query("SELECT * FROM checkins WHERE experiment_id = :experimentId AND checkin_date >= :startDate ORDER BY checkin_date ASC")
    fun getCheckinsForExperimentSince(experimentId: Int, startDate: OffsetDateTime): Flow<List<CheckinEntity>>

    @Query("SELECT * FROM checkins ORDER BY checkin_date DESC LIMIT 1")
    fun getLastCheckin(): Flow<CheckinEntity?>

    @Query("DELETE FROM checkins WHERE experiment_id = :experimentId")
    suspend fun deleteForExperiment(experimentId: Int)
}
