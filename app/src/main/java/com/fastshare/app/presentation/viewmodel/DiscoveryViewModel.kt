package com.fastshare.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastshare.app.data.local.datastore.SettingsRepository
import com.fastshare.app.data.network.discovery.DeviceRepository
import com.fastshare.app.domain.model.AppSettings
import com.fastshare.app.domain.model.DiscoveredDevice
import com.fastshare.app.services.discovery.DiscoveryCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiscoveryUiState(
    val isScanning: Boolean = false,
    val devices: List<DiscoveredDevice> = emptyList(),
    val connectionInfo: com.fastshare.app.core.network.NetworkUtils.ConnectionSummary? = null,
    val selfName: String = "",
    val selfId: String = "",
)

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val discoveryCoordinator: DiscoveryCoordinator,
    private val deviceRepository: DeviceRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _isScanning = MutableStateFlow(false)
    private val _connection = MutableStateFlow<com.fastshare.app.core.network.NetworkUtils.ConnectionSummary?>(null)

    val uiState: StateFlow<DiscoveryUiState> = combine(
        _isScanning, deviceRepository.devices, settingsRepository.settings, _connection,
    ) { scanning, devices, settings, conn ->
        DiscoveryUiState(
            isScanning = scanning,
            devices = devices.values.sortedByDescending { it.lastSeenAt },
            connectionInfo = conn,
            selfName = settings.deviceName,
            selfId = settings.deviceId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoveryUiState())

    fun startDiscovery() {
        _isScanning.value = true
        viewModelScope.launch {
            discoveryCoordinator.start()
        }
    }

    fun refreshConnectionInfo(context: android.content.Context) {
        _connection.value = com.fastshare.app.core.network.NetworkUtils.connectionSummary(context)
    }

    fun stopDiscovery() {
        _isScanning.value = false
        discoveryCoordinator.stop()
    }

    override fun onCleared() {
        stopDiscovery()
        super.onCleared()
    }
}
