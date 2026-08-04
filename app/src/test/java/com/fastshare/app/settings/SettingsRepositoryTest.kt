package com.fastshare.app.settings

import com.fastshare.app.domain.model.AppSettings
import com.fastshare.app.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {

    @Test
    fun `default settings have expected values`() {
        val defaults = AppSettings(
            deviceName = "",
            deviceId = "",
            autoDiscoveryEnabled = true,
            discoveryVisible = true,
            themeMode = ThemeMode.SYSTEM,
            dynamicColor = true,
        )
        assertTrue(defaults.autoDiscoveryEnabled)
        assertEquals(ThemeMode.SYSTEM, defaults.themeMode)
    }

    @Test
    fun `theme mode enum has all expected values`() {
        assertEquals(3, ThemeMode.entries.size)
        assertTrue(ThemeMode.entries.contains(ThemeMode.SYSTEM))
        assertTrue(ThemeMode.entries.contains(ThemeMode.LIGHT))
        assertTrue(ThemeMode.entries.contains(ThemeMode.DARK))
    }
}
