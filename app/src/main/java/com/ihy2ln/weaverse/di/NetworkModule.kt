package com.ihy2ln.weaverse.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        // requestTimeoutMillis is a hard cap on the *entire* request, including however long a
        // real generation legitimately streams for — setting that short cuts long generations
        // off mid-stream; setting it long (as an earlier version of this did, 300s for both)
        // means a truly dead connection with zero bytes ever arriving stays a silent spinner for
        // up to 5 minutes before anything tells the user it failed. socketTimeoutMillis is the
        // right tool for that: it resets on every byte received, so it only fires on genuine
        // inactivity, not on a slow-but-progressing stream — a moderate value here lets long
        // generations run indefinitely while still failing fast when nothing is coming back at
        // all (a dead route, a server that accepted the connection but never responds, etc.).
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 90_000
            requestTimeoutMillis = 600_000
        }
    }
}
