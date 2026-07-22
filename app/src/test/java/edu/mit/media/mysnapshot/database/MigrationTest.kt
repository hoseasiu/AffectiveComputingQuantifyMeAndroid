package edu.mit.media.mysnapshot.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies MIGRATION_1_2 (issue #31) preserves existing `experiments`/`checkins` rows and adds
 * the new `checkins.custom_input_value`/`custom_output_value` columns and the
 * `custom_experiment_types` table without data loss. `app/schemas/.../1.json` is a real
 * exported snapshot of the pre-#31 schema (generated once, by hand, from the
 * `ExperimentEntity`/`CheckinEntity` shape as it existed before this change -- see
 * `room.schemaLocation` in app/build.gradle), which [MigrationTestHelper.createDatabase]
 * needs to build a genuine version-1 database to migrate from.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class MigrationTest {

    private val testDb = "migration-1-2-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        QuantifyMeDatabase::class.java
    )

    @Test
    fun migrate1To2_preservesExistingRowsAndAddsNewColumnsAndTable() {
        helper.createDatabase(testDb, 1).apply {
            execSQL(
                """
                INSERT INTO experiments (
                    id, type, start_time, current_stage, is_active, is_cancelled,
                    self_efficacy, app_efficacy, experiment_efficacy,
                    stage_dates, stage_target_values, stage_restart_count
                ) VALUES (
                    1, 'leisurehappiness', '2024-01-01T00:00:00.000+00:00', 0, 1, 0,
                    3, 3, 3, '[]', '[]', '[]'
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO checkins (
                    id, experiment_id, checkin_date, did_follow_instructions,
                    happiness, stress, productivity, leisure_time
                ) VALUES (
                    1, 1, '2024-01-02T00:00:00.000+00:00', 1, 5, 2, 4, 60
                )
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(testDb, 2, true, MIGRATION_1_2)

        val experimentCursor = migrated.query("SELECT * FROM experiments WHERE id = 1")
        assertEquals(1, experimentCursor.count)
        experimentCursor.moveToFirst()
        assertEquals(
            "leisurehappiness",
            experimentCursor.getString(experimentCursor.getColumnIndexOrThrow("type"))
        )
        experimentCursor.close()

        val checkinCursor = migrated.query("SELECT * FROM checkins WHERE id = 1")
        assertEquals(1, checkinCursor.count)
        checkinCursor.moveToFirst()
        assertEquals(5, checkinCursor.getInt(checkinCursor.getColumnIndexOrThrow("happiness")))
        assertEquals(60, checkinCursor.getInt(checkinCursor.getColumnIndexOrThrow("leisure_time")))
        assertTrue(checkinCursor.isNull(checkinCursor.getColumnIndexOrThrow("custom_input_value")))
        assertTrue(checkinCursor.isNull(checkinCursor.getColumnIndexOrThrow("custom_output_value")))
        checkinCursor.close()

        val tableCursor = migrated.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'custom_experiment_types'"
        )
        assertEquals(1, tableCursor.count)
        tableCursor.close()

        migrated.close()
    }

    @Test
    fun migrate1To2_customExperimentTypesTableAcceptsWrites() {
        helper.createDatabase(testDb, 1).close()

        val migrated = helper.runMigrationsAndValidate(testDb, 2, true, MIGRATION_1_2)

        migrated.execSQL(
            """
            INSERT INTO custom_experiment_types (type_key, json, created_at)
            VALUES ('coffeefocus', '{"typeKey":"coffeefocus"}', '2024-01-01T00:00:00.000+00:00')
            """.trimIndent()
        )
        val cursor = migrated.query("SELECT * FROM custom_experiment_types WHERE type_key = 'coffeefocus'")
        assertEquals(1, cursor.count)
        cursor.close()

        migrated.close()
    }
}
