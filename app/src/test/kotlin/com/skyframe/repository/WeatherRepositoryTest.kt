package com.skyframe.repository

import app.cash.turbine.test
import com.skyframe.data.nws.WeatherNormalizer
import com.skyframe.domain.WeatherResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WeatherRepositoryTest {

    @Test
    fun `state starts in Idle`() = runTest {
        val normalizer = mockk<WeatherNormalizer>()
        val repo = WeatherRepository(normalizer, TestScope(UnconfinedTestDispatcher()))
        repo.state.test {
            assertEquals(WeatherState.Idle, awaitItem())
        }
    }

    @Test
    fun `refresh emits Loading then Success`() = runTest {
        val normalizer = mockk<WeatherNormalizer>()
        val response = mockk<WeatherResponse>(relaxed = true)
        coEvery { normalizer.load(forceRefresh = true) } returns response
        val repo = WeatherRepository(normalizer, TestScope(UnconfinedTestDispatcher()))

        repo.refresh()

        repo.state.test {
            val emitted = awaitItem()
            assertTrue(emitted is WeatherState.Success, "expected Success, got $emitted")
            assertEquals(response, (emitted as WeatherState.Success).response)
        }
    }

    @Test
    fun `refresh emits Error on exception`() = runTest {
        val normalizer = mockk<WeatherNormalizer>()
        coEvery { normalizer.load(forceRefresh = true) } throws RuntimeException("boom")
        val repo = WeatherRepository(normalizer, TestScope(UnconfinedTestDispatcher()))

        repo.refresh()

        repo.state.test {
            val emitted = awaitItem()
            assertTrue(emitted is WeatherState.Error)
            assertTrue((emitted as WeatherState.Error).message.contains("boom"))
        }
    }
}
