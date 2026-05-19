package com.skyframe.data.updates

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VersionCompareTest {

    @Test
    fun `equal versions return false`() {
        assertFalse(VersionCompare.isNewer("0.3.0", "0.3.0"))
    }

    @Test
    fun `single major version newer returns true`() {
        assertTrue(VersionCompare.isNewer("1.0.0", "0.9.0"))
    }

    @Test
    fun `single patch newer returns true`() {
        assertTrue(VersionCompare.isNewer("0.3.1", "0.3.0"))
    }

    @Test
    fun `older version returns false`() {
        assertFalse(VersionCompare.isNewer("0.2.0", "0.3.0"))
    }

    @Test
    fun `longer version with extra trailing zero is not newer`() {
        // 0.3 and 0.3.0 are equivalent
        assertFalse(VersionCompare.isNewer("0.3.0", "0.3"))
        assertFalse(VersionCompare.isNewer("0.3", "0.3.0"))
    }

    @Test
    fun `longer version with nonzero extra segment is newer`() {
        // 0.3.0 > 0.3 only if 0.3.0 has more than just trailing zeros
        assertTrue(VersionCompare.isNewer("0.3.1", "0.3"))
        assertFalse(VersionCompare.isNewer("0.3", "0.3.1"))
    }

    @Test
    fun `v-prefix is stripped from both sides`() {
        assertTrue(VersionCompare.isNewer("v0.3.1", "0.3.0"))
        assertTrue(VersionCompare.isNewer("0.3.1", "v0.3.0"))
        assertTrue(VersionCompare.isNewer("v0.3.1", "v0.3.0"))
    }

    @Test
    fun `non-numeric segments compare as zero`() {
        // Releases occasionally use "0.3.0-beta" - just drop the suffix
        // segment, treat as 0.3.0 for comparison purposes.
        assertFalse(VersionCompare.isNewer("0.3.0-beta", "0.3.0"))
    }
}
