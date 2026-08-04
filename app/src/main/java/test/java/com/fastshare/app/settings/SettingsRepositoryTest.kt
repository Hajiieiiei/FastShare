package com.fastshare.app.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fastshare.app.data.local.datastore.SettingsRepository
import com.fastshare.app.domain.model.ThemeMode
import com.fastshare.app.services.security.CertificateProvider
import com.fastshare.app.services.security.IdentityManager
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {
    private lateinit var context: Context
    private lateinit var identityManager: IdentityManager
    private lateinit var repo: SettingsRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        identityManager = IdentityManager(context, CertificateProvider(context))
        repo = SettingsRepository(context, identityManager)
    }

    @Test
    fun `settings default to system theme`() = runTest {
        val settings = repo.settings.first()
        assertThat(settings.themeMode).isEqualTo(ThemeMode.SYSTEM)
        assertThat(settings.autoDiscoveryEnabled).isTrue()
    }

    @Test
    fun `update persists changes`() = runTest {
        repo.update { it.copy(autoDiscoveryEnabled = false) }
        val updated = repo.settings.first()
        assertThat(updated.autoDiscoveryEnabled).isFalse()
    }
}
