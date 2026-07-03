package edu.mit.media.mysnapshot.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey
    val id: Int = 1, // Single-row table
    val timezone: String? = null,
    val dateOfBirth: String? = null,
    val race: String? = null,
    val gender: String? = null,
    val baselineHappiness: Int? = null,
    val baselineStress: Int? = null,
    val baselineActivity: String? = null,
    val baselineSleepQuality: Int? = null
)
