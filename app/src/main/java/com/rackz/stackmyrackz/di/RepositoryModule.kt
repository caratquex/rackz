package com.rackz.stackmyrackz.di

import com.rackz.stackmyrackz.data.repository.AndroidNetworkMonitor
import com.rackz.stackmyrackz.data.repository.NetworkMonitor
import com.rackz.stackmyrackz.data.repository.OfflineFirstTransactionRepository
import com.rackz.stackmyrackz.data.repository.TransactionRepository
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
}
