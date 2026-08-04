package com.fastshare.app.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fastshare.app.presentation.components.ConnectionStatusChip
import com.fastshare.app.presentation.components.DeviceCard
import com.fastshare.app.presentation.components.EmptyState
import com.fastshare.app.presentation.components.ScanningPulse
import com.fastshare.app.presentation.components.SectionHeader
import com.fastshare.app.presentation.viewmodel.DiscoveryViewModel

@Composable
fun HomeScreen(
    onSend: () -> Unit,
    onReceive: () -> Unit,
    onScanQr: () -> Unit,
    onShowQr: () -> Unit,
    onManualConnect: () -> Unit,
    onSendText: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTrusted: () -> Unit,
) {
    val vm: DiscoveryViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.refreshConnectionInfo(context)
        vm.startDiscovery()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("FastShare", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            state.selfName.ifBlank { "This device" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenTrusted) { Icon(Icons.Outlined.Devices, "Trusted devices") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ConnectionStatusChip(
                    isConnected = state.connectionInfo?.isWifi == true,
                    networkName = state.connectionInfo?.ssid,
                )
                if (state.isScanning) {
                    Text(
                        "Scanning…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onSend,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Outlined.Upload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Send Files")
                }
                FilledTonalButton(
                    onClick = onReceive,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Receive Files")
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickAction(Icons.Outlined.QrCodeScanner, "Scan QR", onScanQr)
                QuickAction(Icons.Outlined.QrCode, "My QR", onShowQr)
                QuickAction(Icons.Outlined.Edit, "Manual", onManualConnect)
                QuickAction(Icons.Outlined.TextFields, "Text", onSendText)
            }

            SectionHeader(
                text = "Nearby Devices",
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                if (state.devices.isNotEmpty()) {
                    Text(
                        "${state.devices.size} online",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.devices.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ScanningPulse(active = state.isScanning)
                        EmptyState(
                            icon = Icons.Outlined.Devices,
                            title = "No devices nearby",
                            subtitle = "Make sure both devices are on the same Wi-Fi",
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.devices, key = { it.info.deviceId }) { device ->
                        DeviceCard(
                            device = device,
                            onClick = { /* select -> go to Send flow */ },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        FilledTonalButton(onClick = onClick, shape = RoundedCornerShape(12.dp)) {
            Icon(icon, contentDescription = label)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
