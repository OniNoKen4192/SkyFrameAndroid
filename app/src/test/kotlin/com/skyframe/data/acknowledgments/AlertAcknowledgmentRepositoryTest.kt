package com.skyframe.data.acknowledgments

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AlertAcknowledgmentRepositoryTest {

    @TempDir
    lateinit var tempDir: File

    private fun newRepo(): AlertAcknowledgmentRepository {
        val ds = PreferenceDataStoreFactory.create(produceFile = { File(tempDir, "ack.preferences_pb") })
        return AlertAcknowledgmentRepository(ds)
    }

    @Test
    fun `dismissed set starts empty`() = runTest {
        assertEquals(emptySet<String>(), newRepo().snapshot())
    }

    @Test
    fun `dismiss adds id to set`() = runTest {
        val repo = newRepo()
        repo.dismiss("urn:oid:abc")
        assertTrue("urn:oid:abc" in repo.snapshot())
    }

    @Test
    fun `prune retains only ids still active`() = runTest {
        val repo = newRepo()
        repo.dismiss("a")
        repo.dismiss("b")
        repo.dismiss("c")
        repo.pruneTo(setOf("b", "c", "d"))
        val remaining = repo.snapshot()
        assertEquals(setOf("b", "c"), remaining)
        assertFalse("a" in remaining)
        assertFalse("d" in remaining)
    }
}
