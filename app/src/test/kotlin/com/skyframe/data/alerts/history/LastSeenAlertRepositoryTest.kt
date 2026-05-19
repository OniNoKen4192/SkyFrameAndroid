package com.skyframe.data.alerts.history

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LastSeenAlertRepositoryTest {

    @TempDir
    lateinit var tempDir: File

    private fun newRepo(): LastSeenAlertRepository {
        val ds = PreferenceDataStoreFactory.create(produceFile = { File(tempDir, "ls.preferences_pb") })
        return LastSeenAlertRepository(ds)
    }

    @Test
    fun `initial read returns empty set`() = runTest {
        assertEquals(emptySet<String>(), newRepo().read())
    }

    @Test
    fun `write then read returns the same set`() = runTest {
        val repo = newRepo()
        repo.write(setOf("alert-1", "alert-2"))
        assertEquals(setOf("alert-1", "alert-2"), repo.read())
    }

    @Test
    fun `overwrite replaces previous set entirely`() = runTest {
        val repo = newRepo()
        repo.write(setOf("alert-1", "alert-2"))
        repo.write(setOf("alert-3"))
        // alert-1 and alert-2 are gone after overwrite
        assertEquals(setOf("alert-3"), repo.read())
    }
}
