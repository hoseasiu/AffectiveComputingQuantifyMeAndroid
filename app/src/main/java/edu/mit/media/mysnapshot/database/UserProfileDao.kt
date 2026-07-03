package edu.mit.media.mysnapshot.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Insert
    suspend fun insert(profile: UserProfileEntity)

    @Update
    suspend fun update(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getUserProfile(): Flow<UserProfileEntity?>

    @Query("DELETE FROM user_profile WHERE id = 1")
    suspend fun deleteProfile()
}
