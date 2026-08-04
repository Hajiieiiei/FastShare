package com.fastshare.app.data.local.datastore

import android.content.Context
import com.fastshare.app.domain.model.AppLanguage
import com.fastshare.app.domain.model.AppSettings
import com.fastshare.app.domain.model.ApprovalPolicy
import com.fastshare.app.domain.model.AutoCleanupPolicy
import com.fastshare.app.domain.model.NetworkInterfacePreference
import com.fastshare.app.domain.model.ThemeMode
import com.fastshare.app.services.security.IdentityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val identityManager: IdentityManager,
) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            deviceName = prefs[KEY_DEVICE_NAME] ?: "",
            deviceId = prefs[KEY_DEVICE_ID] ?: "",
            autoDiscoveryEnabled = prefs[KEY_AUTO_DISCOVERY] ?: true,
            discoveryVisible = prefs[KEY_DISCOVERY_VISIBLE] ?: true,
            interfacePreference = prefs[KEY_INTERFACE]?.let { raw: String ->
                NetworkInterfacePreference.entries.firstOrNull { enum -> enum.name == raw }
            } ?: NetworkInterfacePreference.AUTO,
            transferSpeedLimitKbps = prefs[KEY_SPEED_LIMIT] ?: 0,
            maxParallelStreams = prefs[KEY_PARALLEL_STREAMS] ?: 4,
            listenPort = prefs[KEY_LISTEN_PORT] ?: 0,
            approvalPolicy = prefs[KEY_APPROVAL]?.let { raw ->
                ApprovalPolicy.entries.firstOrNull { it.name == raw }
            } ?: ApprovalPolicy.ALWAYS_ASK,
            requireTls = prefs[KEY_REQUIRE_TLS] ?: true,
            themeMode = prefs[KEY_THEME]?.let { raw ->
                ThemeMode.entries.firstOrNull { it.name == raw }
            } ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: true,
            language = prefs[KEY_LANGUAGE]?.let { raw ->
                AppLanguage.entries.firstOrNull { it.tag == raw }
            } ?: AppLanguage.SYSTEM,
            downloadTreeUri = prefs[KEY_DOWNLOAD_TREE],
            organizeBySender = prefs[KEY_ORGANIZE] ?: false,
            autoCleanup = prefs[KEY_CLEANUP]?.let { raw ->
                AutoCleanupPolicy.entries.firstOrNull { it.name == raw }
            } ?: AutoCleanupPolicy.NEVER,
            keepScreenOnDuringTransfer = prefs[KEY_KEEP_SCREEN] ?: true,
            vibrateOnComplete = prefs[KEY_VIBRATE] ?: true,
            clipboardAutoApply = prefs[KEY_CLIPBOARD_AUTO] ?: false,
            onboardingCompleted = prefs[KEY_ONBOARDED] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: suspend (AppSettings) -> AppSettings) {
        val updated = transform(current())
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_DEVICE_NAME] = updated.deviceName
            prefs[KEY_DEVICE_ID] = updated.deviceId
            prefs[KEY_AUTO_DISCOVERY] = updated.autoDiscoveryEnabled
            prefs[KEY_DISCOVERY_VISIBLE] = updated.discoveryVisible
            prefs[KEY_INTERFACE] = updated.interfacePreference.name
            prefs[KEY_SPEED_LIMIT] = updated.transferSpeedLimitKbps
            prefs[KEY_PARALLEL_STREAMS] = updated.maxParallelStreams
            prefs[KEY_LISTEN_PORT] = updated.listenPort
            prefs[KEY_APPROVAL] = updated.approvalPolicy.name
            prefs[KEY_REQUIRE_TLS] = updated.requireTls
            prefs[KEY_THEME] = updated.themeMode.name
            prefs[KEY_DYNAMIC_COLOR] = updated.dynamicColor
            prefs[KEY_LANGUAGE] = updated.language.tag
            updated.downloadTreeUri?.let { prefs[KEY_DOWNLOAD_TREE] = it } ?: prefs.remove(KEY_DOWNLOAD_TREE)
            prefs[KEY_ORGANIZE] = updated.organizeBySender
            prefs[KEY_CLEANUP] = updated.autoCleanup.name
            prefs[KEY_KEEP_SCREEN] = updated.keepScreenOnDuringTransfer
            prefs[KEY_VIBRATE] = updated.vibrateOnComplete
            prefs[KEY_CLIPBOARD_AUTO] = updated.clipboardAutoApply
            prefs[KEY_ONBOARDED] = updated.onboardingCompleted
        }
    }

    companion object {
        private val KEY_DEVICE_NAME = stringPreferencesKey("device_name")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_AUTO_DISCOVERY = booleanPreferencesKey("auto_discovery")
        private val KEY_DISCOVERY_VISIBLE = booleanPreferencesKey("discovery_visible")
        private val KEY_INTERFACE = stringPreferencesKey("interface_pref")
        private val KEY_SPEED_LIMIT = intPreferencesKey("speed_limit_kbps")
        private val KEY_PARALLEL_STREAMS = intPreferencesKey("parallel_streams")
        private val KEY_LISTEN_PORT = intPreferencesKey("listen_port")
        private val KEY_APPROVAL = stringPreferencesKey("approval_policy")
        private val KEY_REQUIRE_TLS = booleanPreferencesKey("require_tls")
        private val KEY_THEME = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_DOWNLOAD_TREE = stringPreferencesKey("download_tree")
        private val KEY_ORGANIZE = booleanPreferencesKey("organize_by_sender")
        private val KEY_CLEANUP = stringPreferencesKey("auto_cleanup")
        private val KEY_KEEP_SCREEN = booleanPreferencesKey("keep_screen_on")
        private val KEY_VIBRATE = booleanPreferencesKey("vibrate_on_complete")
        private val KEY_CLIPBOARD_AUTO = booleanPreferencesKey("clipboard_auto")
        private val KEY_ONBOARDED = booleanPreferencesKey("onboarded")
    }
}
