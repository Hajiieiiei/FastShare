package com.fastshare.app.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage(val tag: String) {
    SYSTEM(""), ENGLISH("en"), PERSIAN("fa"), GERMAN("de"), SPANISH("es"), FRENCH("fr"),
    RUSSIAN("ru"), CHINESE("zh"), ARABIC("ar"), TURKISH("tr");
}

enum class NetworkInterfacePreference { AUTO, WIFI_ONLY, ETHERNET, HOTSPOT }

enum class ApprovalPolicy {
    ALWAYS_ASK,
    TRUSTED_AUTO,
    ACCEPT_ALL
}

enum class AutoCleanupPolicy(val days: Int) { NEVER(0), WEEK(7), MONTH(30), QUARTER(90) }

/** All user-controlled configuration; persisted in DataStore and observed as a StateFlow. */
data class AppSettings(
    val deviceName: String = "",
    val deviceId: String = "",
    val autoDiscoveryEnabled: Boolean = true,
    val discoveryVisible: Boolean = true,
    val interfacePreference: NetworkInterfacePreference = NetworkInterfacePreference.AUTO,
    val transferSpeedLimitKbps: Int = 0,
    val maxParallelStreams: Int = 4,
    val listenPort: Int = 0,
    val approvalPolicy: ApprovalPolicy = ApprovalPolicy.ALWAYS_ASK,
    val requireTls: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val downloadTreeUri: String? = null,
    val organizeBySender: Boolean = false,
    val autoCleanup: AutoCleanupPolicy = AutoCleanupPolicy.NEVER,
    val keepScreenOnDuringTransfer: Boolean = true,
    val vibrateOnComplete: Boolean = true,
    val clipboardAutoApply: Boolean = false,
    val onboardingCompleted: Boolean = false,
) {
    val effectiveStreams: Int get() = maxParallelStreams.coerceIn(1, 8)
    val isThrottled: Boolean get() = transferSpeedLimitKbps > 0
}
