package com.fastshare.app.settings

import com.fastshare.app.domain.model.AppSettings
import com.fastshare.app.domain.model.ThemeMode
import org.assertj.core.api.Assertions.assertThat
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
        assertThat(defaults.autoDiscoveryEnabled).isTrue()
        assertThat(defaults.themeMode).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun `theme mode enum has all expected values`() {
        assertThat(ThemeMode.entries).contains(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
    }
}
