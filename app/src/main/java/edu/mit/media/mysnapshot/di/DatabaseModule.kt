package edu.mit.media.mysnapshot.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import edu.mit.media.mysnapshot.database.QuantifyMeDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Singleton
    @Provides
    fun provideQuantifyMeDatabase(@ApplicationContext context: Context): QuantifyMeDatabase {
        return QuantifyMeDatabase.getInstance(context)
    }
}
