package com.amaya.intelligence.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateRepositoryTest {
    @Test
    fun comparesReleaseVersions() {
        assertTrue(UpdateRepository.isVersionNewer("1.0.1", "1.0.0"))
        assertTrue(UpdateRepository.isVersionNewer("1.0.0", "1.0.0-rc1"))
        assertFalse(UpdateRepository.isVersionNewer("1.0.0", "1.0.0"))
        assertFalse(UpdateRepository.isVersionNewer("1.0.0", "1.0.1"))
        assertFalse(UpdateRepository.isVersionNewer("1.0.0-beta", "1.0.0"))
    }

    @Test
    fun `picks the APK matching the device ABI`() {
        val assets = listOf(
            "amaya-v1.0.1-armeabi-v7a.apk" to "https://example.com/v7a.apk",
            "amaya-v1.0.1-arm64-v8a.apk" to "https://example.com/arm64.apk"
        )

        // arm64 device: must not be handed the 32-bit APK just because it is listed first.
        assertEquals(
            "https://example.com/arm64.apk",
            UpdateRepository.selectApkUrl(assets, listOf("arm64-v8a", "armeabi-v7a"))
        )
        // 32-bit-only device cannot run arm64.
        assertEquals(
            "https://example.com/v7a.apk",
            UpdateRepository.selectApkUrl(assets, listOf("armeabi-v7a"))
        )
    }

    @Test
    fun `falls back to a universal APK and never to a foreign ABI`() {
        val universal = listOf("amaya-v1.0.1.apk" to "https://example.com/universal.apk")
        assertEquals(
            "https://example.com/universal.apk",
            UpdateRepository.selectApkUrl(universal, listOf("arm64-v8a"))
        )

        // Only an x86 build exists: an arm device must get nothing, not an unrunnable APK.
        val x86Only = listOf("amaya-v1.0.1-x86_64.apk" to "https://example.com/x86.apk")
        assertEquals("", UpdateRepository.selectApkUrl(x86Only, listOf("arm64-v8a")))

        assertEquals("", UpdateRepository.selectApkUrl(emptyList(), listOf("arm64-v8a")))
    }
}
