package com.ihy2ln.weaverse.di

import com.ihy2ln.weaverse.ai.openrouter.OpenRouterRepository
import com.ihy2ln.weaverse.ai.providers.OpenRouterProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    @Provides
    @Singleton
    fun provideOpenRouterProvider(
        repository: OpenRouterRepository,
    ): OpenRouterProvider = OpenRouterProvider(repository)
}
