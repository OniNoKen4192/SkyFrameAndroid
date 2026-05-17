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
        // userAgentProvider is invoked at every NWS request so the email always
        // reflects the current SettingsRepository state - after onboarding
        // completes mid-session, the next request already uses the real email.
        return NwsHttpClient.create(userAgentProvider = {
            val email = runBlocking { settings.snapshot().email }
                .ifBlank { "unconfigured@skyframe.local" }
            "SkyFrame/0.1.1 ($email)"
        })
    }
}
