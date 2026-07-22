package edu.mit.media.mysnapshot.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.joda.time.DateTime

/**
 * One user-authored experiment type (issue #31 -- "create your own experiment", built by
 * #33). [json] reuses `ExperimentTypeRegistry`'s existing DTO shape (see
 * [edu.mit.media.mysnapshot.engine.ExperimentTypeRegistry.parseCustomTypeJson]/
 * `toCustomTypeJson`) so a custom type round-trips through exactly the same
 * `SignalRef`/format-kind parsing the bundled `assets/experiment_types.json` config does --
 * no separate schema to keep in sync.
 */
@Entity(tableName = "custom_experiment_types")
data class CustomExperimentTypeEntity(
    @PrimaryKey
    @ColumnInfo(name = "type_key")
    val typeKey: String,
    val json: String,
    @ColumnInfo(name = "created_at")
    val createdAt: DateTime
)
