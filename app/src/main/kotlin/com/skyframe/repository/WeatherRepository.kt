package com.skyframe.repository

import com.skyframe.data.nws.WeatherNormalizer
import com.skyframe.domain.WeatherResponse
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class WeatherState {
    data object Idle : WeatherState()
    data object Loading : WeatherState()
    data class Success(val response: WeatherResponse) : WeatherState()
    data class Error(val message: String) : WeatherState()
}

@Singleton
class WeatherRepository @Inject constructor(
    private val normalizer: WeatherNormalizer,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
) {
    private val _state = MutableStateFlow<WeatherState>(WeatherState.Idle)
    val state: StateFlow<WeatherState> = _state.asStateFlow()

    private var pollingJob: Job? = null

    /**
     * Starts a polling loop on the supplied scope. Cadence is driven by
     * the response's meta.nextRefreshAt (typically ~90s after fetchedAt).
     * Idempotent; calling repeatedly does not stack timers.
     */
    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                refreshInternal(forceRefresh = false)
                val current = _state.value
                val delayMs = if (current is WeatherState.Success) {
                    (current.response.meta.nextRefreshAt.toEpochMilliseconds() -
                        kotlinx.datetime.Clock.System.now().toEpochMilliseconds())
                        .coerceAtLeast(15_000L)  // floor at 15s
                } else {
                    30_000L  // back off when in error state
                }
                delay(delayMs)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /** Pull-to-refresh: skip cache and fetch fresh data immediately. */
    fun refresh() {
        scope.launch { refreshInternal(forceRefresh = true) }
    }

    private suspend fun refreshInternal(forceRefresh: Boolean) {
        _state.value = WeatherState.Loading
        try {
            val response = normalizer.load(forceRefresh = forceRefresh)
            _state.value = WeatherState.Success(response)
        } catch (t: Throwable) {
            _state.value = WeatherState.Error(t.message ?: "Unknown error")
        }
    }
}
