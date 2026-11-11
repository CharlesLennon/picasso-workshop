package com.picassoworkshop.app.update

import org.junit.Assert.*
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun testVersionComparison() {
        assertTrue(AppUpdateManager.compareVersions("1.0.1", "1.0.0") > 0)
        assertTrue(AppUpdateManager.compareVersions("v1.1.0", "1.0.9") > 0)
        assertTrue(AppUpdateManager.compareVersions("2.0.0", "1.9.9") > 0)
        assertEquals(0, AppUpdateManager.compareVersions("1.0.0", "v1.0.0"))
        assertTrue(AppUpdateManager.compareVersions("1.0.0", "1.0.1") < 0)
    }
}
