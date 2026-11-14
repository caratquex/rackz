package com.rackz.stackmyrackz.di

import android.content.Context
import androidx.room.Room
import com.rackz.stackmyrackz.data.local.RacksDatabase
import com.rackz.stackmyrackz.data.local.TransactionDao
import com.rackz.stackmyrackz.data.local.WealthDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
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
        )
            .addMigrations(RacksDatabase.MIGRATION_1_2, RacksDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun provideTransactionDao(database: RacksDatabase): TransactionDao =
        database.transactionDao()

    @Provides
    fun provideWealthDao(database: RacksDatabase): WealthDao =
        database.wealthDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
}
