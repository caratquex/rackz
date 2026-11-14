package com.rackz.stackmyrackz.di

import com.rackz.stackmyrackz.data.preferences.DataStoreSyncPreferencesRepository
import com.rackz.stackmyrackz.data.preferences.SyncPreferencesRepository
import com.rackz.stackmyrackz.data.remote.AuthTokenProvider
import com.rackz.stackmyrackz.data.remote.GoogleAuthTokenProvider
import com.rackz.stackmyrackz.data.remote.OkHttpSheetsApiClient
import com.rackz.stackmyrackz.data.remote.SheetsRemoteDataSource
import com.rackz.stackmyrackz.data.repository.AndroidNetworkMonitor
import com.rackz.stackmyrackz.data.repository.NetworkMonitor
import com.rackz.stackmyrackz.data.repository.OfflineFirstTransactionRepository
import com.rackz.stackmyrackz.data.repository.OfflineFirstWealthRepository
import com.rackz.stackmyrackz.data.repository.TransactionRepository
import com.rackz.stackmyrackz.data.repository.WealthRepository
import com.rackz.stackmyrackz.data.sync.SyncScheduler
import com.rackz.stackmyrackz.data.sync.WealthReminderScheduler
import com.rackz.stackmyrackz.data.sync.WorkManagerSyncScheduler
import com.rackz.stackmyrackz.data.sync.WorkManagerWealthReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(
        impl: OfflineFirstTransactionRepository
    ): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(
        impl: AndroidNetworkMonitor
    ): NetworkMonitor

    @Binds
    @Singleton
    abstract fun bindSyncPreferencesRepository(
        impl: DataStoreSyncPreferencesRepository
    ): SyncPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindAuthTokenProvider(
        impl: GoogleAuthTokenProvider
    ): AuthTokenProvider

    @Binds
    @Singleton
    abstract fun bindSheetsRemoteDataSource(
        impl: OkHttpSheetsApiClient
    ): SheetsRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindSyncScheduler(
        impl: WorkManagerSyncScheduler
    ): SyncScheduler

    @Binds
    @Singleton
    abstract fun bindWealthRepository(
        impl: OfflineFirstWealthRepository
    ): WealthRepository

    @Binds
    @Singleton
    abstract fun bindWealthReminderScheduler(
        impl: WorkManagerWealthReminderScheduler
    ): WealthReminderScheduler
}
