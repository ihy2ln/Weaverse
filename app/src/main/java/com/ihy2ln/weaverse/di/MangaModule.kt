package com.ihy2ln.weaverse.di

import com.ihy2ln.weaverse.core.manga.RenderedCatalog
import com.ihy2ln.weaverse.core.manga.RenderedCatalogClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The shared manga sources render script-built catalogs through the app's WebView. */
@Module
@InstallIn(SingletonComponent::class)
abstract class MangaModule {
    @Binds
    abstract fun renderedCatalog(client: RenderedCatalogClient): RenderedCatalog
}
