package edu.mit.media.mysnapshot.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.OffsetDateTime

/**
 * Mirrors AffectiveComputingQuantifyMeDjango's `Experiment` model (models.py). The
 * per-stage JSON fields (`stage_dates`, `stage_target_values`, `stage_restart_count`)
 * are kept as JSON-encoded lists of length `ExperimentEngine.NUM_STAGES + 1` — index 0
 * is the baseline stage, matching the Django schema exactly (see
 * [edu.mit.media.mysnapshot.data.ExperimentStageState] for the decoded shape).
 */
@Entity(tableName = "experiments")
data class ExperimentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val type: String,
    @ColumnInfo(name = "start_time")
    val startTime: OffsetDateTime,
    @ColumnInfo(name = "end_time")
    val endTime: OffsetDateTime? = null,
    @ColumnInfo(name = "current_stage")
    val currentStage: Int = 0,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "is_cancelled")
    val isCancelled: Boolean = false,
    @ColumnInfo(name = "self_efficacy")
    val selfEfficacy: Int,
    @ColumnInfo(name = "app_efficacy")
    val appEfficacy: Int,
    @ColumnInfo(name = "experiment_efficacy")
    val experimentEfficacy: Int,
    @ColumnInfo(name = "result_value")
    val resultValue: Float? = null,
    @ColumnInfo(name = "result_confidence")
    val resultConfidence: Float? = null,
    @ColumnInfo(name = "initial_stage_average")
    val initialStageAverage: Float? = null,
    // JSON array of [startIsoDate, endIsoDate] pairs, length NUM_STAGES + 1
    @ColumnInfo(name = "stage_dates")
    val stageDatesJson: String,
    // JSON array of floats (nullable), length NUM_STAGES + 1
    @ColumnInfo(name = "stage_target_values")
    val stageTargetValuesJson: String,
    // JSON array of ints, length NUM_STAGES + 1
    @ColumnInfo(name = "stage_restart_count")
    val stageRestartCountJson: String
)
