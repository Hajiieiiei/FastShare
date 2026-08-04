package com.fastshare.app.discovery

import com.fastshare.app.data.local.dao.TrustedDeviceDao
import com.fastshare.app.data.local.entity.TrustedDeviceEntity
import com.fastshare.app.data.network.discovery.DeviceRepository
import com.fastshare.app.domain.model.DeviceInfo
import com.fastshare.app.domain.model.DevicePlatform
import com.fastshare.app.domain.model.DeviceType
import com.fastshare.app.domain.model.DiscoverySource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import com.google.common.truth.Truth.assertThat

class DeviceRepositoryTest {
    private val dao = mockk<TrustedDeviceDao>()
    private val repo = DeviceRepository(dao)

    @Test
    fun `upsert adds addressable device`() = runTest {
        coEvery { dao.getAll() } returns emptyList()
        val info = DeviceInfo(
            deviceId = "d1", deviceName = "Pixel", platform = DevicePlatform.ANDROID,
            deviceType = DeviceType.PHONE, ipAddress = "192.168.1.10", port = 53319,
        )
        repo.upsert(info, source = DiscoverySource.MULTICAST)
        assertThat(repo.devices.first()).hasSize(1)
        assertThat(repo.deviceById("d1")?.info?.deviceName).isEqualTo("Pixel")
    }

    @Test
    fun `upsert ignores non-addressable device`() = runTest {
        val info = DeviceInfo(deviceId = "d1", deviceName = "Pixel", ipAddress = "", port = 0)
        repo.upsert(info)
        assertThat(repo.devices.first()).isEmpty()
    }

    @Test
    fun `removeByIp drops matching entries`() = runTest {
        coEvery { dao.getAll() } returns emptyList()
        val info = DeviceInfo(
            deviceId = "d2", deviceName = "Mac", platform = DevicePlatform.MACOS,
            deviceType = DeviceType.LAPTOP, ipAddress = "192.168.1.20", port = 53319,
        )
        repo.upsert(info)
        repo.removeByIp("192.168.1.20")
        assertThat(repo.devices.first()).isEmpty()
    }
}
