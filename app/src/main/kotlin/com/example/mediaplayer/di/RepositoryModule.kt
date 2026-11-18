package com.example.mediaplayer.di

import com.example.mediaplayer.data.repository.StubCatalogRepository
import com.example.mediaplayer.domain.repository.CatalogRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindCatalog(impl: StubCatalogRepository): CatalogRepository
}
