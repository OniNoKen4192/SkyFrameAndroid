package com.skyframe.di

import com.skyframe.data.nws.NwsHttpClient
import com.skyframe.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(settings: SettingsRepository): HttpClient {
        // At app start the email may not yet be configured (first run). Fall back
        // to a generic UA; the SettingsRepository swap happens after onboarding.
        val email = runBlocking { settings.snapshot().email.ifBlank { "unconfigured@skyframe.local" } }
        return NwsHttpClient.create(userAgent = "SkyFrame/0.1.0 ($email)")
    }
}
