package edu.mit.media.mysnapshot.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.time.OffsetDateTime

@Entity(
    tableName = "checkins",
    foreignKeys = [
        ForeignKey(
            entity = ExperimentEntity::class,
            parentColumns = ["id"],
            childColumns = ["experiment_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CheckinEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "experiment_id")
    val experimentId: Int,
    @ColumnInfo(name = "checkin_date")
    val checkinDate: OffsetDateTime,
    @ColumnInfo(name = "did_follow_instructions")
    val didFollowInstructions: Int,
    val happiness: Int,
    val stress: Int,
    val productivity: Int,
    @ColumnInfo(name = "leisure_time")
    val leisureTime: Int
)
