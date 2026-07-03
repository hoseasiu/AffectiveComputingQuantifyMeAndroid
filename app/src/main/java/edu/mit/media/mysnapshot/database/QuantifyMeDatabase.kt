package edu.mit.media.mysnapshot.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ExperimentEntity::class,
        CheckinEntity::class,
        UserProfileEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class QuantifyMeDatabase : RoomDatabase() {
    abstract fun experimentDao(): ExperimentDao
    abstract fun checkinDao(): CheckinDao
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        @Volatile
        private var instance: QuantifyMeDatabase? = null

        fun getInstance(context: Context): QuantifyMeDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    QuantifyMeDatabase::class.java,
                    "quantify_me_database"
                ).build().also { instance = it }
            }
        }
    }
}
