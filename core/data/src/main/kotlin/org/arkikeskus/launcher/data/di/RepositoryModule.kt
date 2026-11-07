package org.arkikeskus.launcher.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.arkikeskus.launcher.data.AppRepository
import org.arkikeskus.launcher.data.AppRepositoryImpl
import javax.inject.Singleton

/** The one repository with an interface (see [AppRepository]); everything else is a concrete class. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAppRepository(impl: AppRepositoryImpl): AppRepository
}
