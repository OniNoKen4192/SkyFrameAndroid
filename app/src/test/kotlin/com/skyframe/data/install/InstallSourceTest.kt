package com.skyframe.data.install

import android.content.Context
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InstallSourceTest {

    private fun contextWithDeprecatedInstaller(installer: String?): Context {
        val pm = mockk<PackageManager>()
        @Suppress("DEPRECATION")
        every { pm.getInstallerPackageName(any()) } returns installer

        val ctx = mockk<Context>()
        every { ctx.packageManager } returns pm
        every { ctx.packageName } returns "com.skyframe"
        return ctx
    }

    @Test
    fun `returns true when installer is Play Store package`() {
        // JVM-test environment has Build.VERSION.SDK_INT = 0, so the helper takes
        // the deprecated getInstallerPackageName branch. That's the path we mock.
        assertTrue(InstallSource.isFromPlayStore(contextWithDeprecatedInstaller("com.android.vending")))
    }

    @Test
    fun `returns false when installer is null sideload`() {
        assertFalse(InstallSource.isFromPlayStore(contextWithDeprecatedInstaller(null)))
    }

    @Test
    fun `returns false when installer is some other package`() {
        assertFalse(InstallSource.isFromPlayStore(contextWithDeprecatedInstaller("com.amazon.venezia")))
    }

    @Test
    fun `returns false when PackageManager throws IllegalArgumentException`() {
        val pm = mockk<PackageManager>()
        @Suppress("DEPRECATION")
        every { pm.getInstallerPackageName(any()) } throws IllegalArgumentException("bad package")

        val ctx = mockk<Context>()
        every { ctx.packageManager } returns pm
        every { ctx.packageName } returns "com.skyframe"

        assertFalse(InstallSource.isFromPlayStore(ctx))
    }
}
