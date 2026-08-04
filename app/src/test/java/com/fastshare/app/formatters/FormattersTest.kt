package com.fastshare.app.formatters

import com.fastshare.app.core.util.formatBytes
import com.fastshare.app.core.util.formatDuration
import com.fastshare.app.core.util.formatFingerprint
import com.fastshare.app.core.util.formatPercent
import com.fastshare.app.core.util.formatSpeed
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class FormattersTest {
    @Test
    fun `bytes format chooses correct unit`() {
        assertThat(500L.formatBytes()).isEqualTo("500 B")
        assertThat(1024L.formatBytes(Locale.US)).isEqualTo("1.00 KB")
        assertThat((1024L * 1024).formatBytes(Locale.US)).isEqualTo("1.00 MB")
        assertThat((1024L * 1024 * 1024).formatBytes(Locale.US)).isEqualTo("1.00 GB")
    }

    @Test
    fun `speed format appends per second`() {
        assertThat((1024L * 1024).formatSpeed(Locale.US)).isEqualTo("1.00 MB/s")
    }

    @Test
    fun `duration format pads correctly`() {
        assertThat(0L.formatDuration()).isEqualTo("--:--")
        assertThat(65_000L.formatDuration()).isEqualTo("01:05")
        assertThat(3_661_000L.formatDuration()).isEqualTo("1:01:01")
    }

    @Test
    fun `percent clamps and formats`() {
        assertThat(0.5f.formatPercent()).isEqualTo("50%")
        assertThat(1.5f.formatPercent()).isEqualTo("100%")
        assertThat((-0.1f).formatPercent()).isEqualTo("0%")
    }

    @Test
    fun `fingerprint formats as groups`() {
        val raw = "A1B2C3D4E5F67890"
        assertThat(raw.formatFingerprint(groups = 4)).isEqualTo("A1B2 C3D4 E5F6 7890")
    }
}
