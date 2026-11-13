package com.rackz.stackmyrackz.di

import android.content.Context
import androidx.room.Room
import com.rackz.stackmyrackz.data.local.RacksDatabase
import com.rackz.stackmyrackz.data.local.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideRacksDatabase(@ApplicationContext context: Context): RacksDatabase =
        Room.databaseBuilder(
            context,
            RacksDatabase::class.java,
            "racks.db"
        ).build()

    @Provides
    fun provideTransactionDao(database: RacksDatabase): TransactionDao =
        database.transactionDao()
}
