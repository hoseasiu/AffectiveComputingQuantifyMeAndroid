package edu.mit.media.mysnapshot.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import org.joda.time.DateTime

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
    val checkinDate: DateTime,
    @ColumnInfo(name = "did_follow_instructions")
    val didFollowInstructions: Int,
    val happiness: Int,
    val stress: Int,
    val productivity: Int,
    @ColumnInfo(name = "leisure_time")
    val leisureTime: Int,
    // Added in MIGRATION_1_2 (#31): the answer to a custom-signal experiment type's
    // input/output question, in that signal's native unit. Null for every built-in-only
    // checkin and for days a custom question was skipped/missed.
    @ColumnInfo(name = "custom_input_value")
    val customInputValue: Float? = null,
    @ColumnInfo(name = "custom_output_value")
    val customOutputValue: Float? = null
)
