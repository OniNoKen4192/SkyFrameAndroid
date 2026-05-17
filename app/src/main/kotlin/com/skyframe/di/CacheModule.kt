package com.skyframe.di

import com.skyframe.data.cache.WeatherCache
import com.skyframe.domain.WeatherResponse
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {

    @Provides
    @Singleton
    fun provideWeatherResponseCache(): WeatherCache<WeatherResponse> = WeatherCache()
}
