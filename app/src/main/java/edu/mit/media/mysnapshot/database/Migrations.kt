package edu.mit.media.mysnapshot.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2 (issue #31, custom-signal data model): two new nullable columns on `checkins` for
 * a custom-signal experiment type's daily input/output answer, plus the
 * `custom_experiment_types` table for user-authored type definitions. Explicit migration
 * rather than `fallbackToDestructiveMigration()` -- that would wipe every in-progress
 * experiment for existing installs on upgrade, which is unacceptable for a self-experiment
 * app whose whole value is a multi-week data series.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE checkins ADD COLUMN custom_input_value REAL")
        db.execSQL("ALTER TABLE checkins ADD COLUMN custom_output_value REAL")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS custom_experiment_types (
                type_key TEXT NOT NULL PRIMARY KEY,
                json TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
            """.trimIndent()
        )
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
