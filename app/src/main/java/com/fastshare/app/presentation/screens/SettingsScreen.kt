package com.fastshare.app.presentation.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fastshare.app.presentation.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = hiltViewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SettingsRow(
                title = "Auto discovery",
                subtitle = "Broadcast and scan for nearby devices",
                checked = settings.autoDiscoveryEnabled,
                onCheckedChange = { value ->
                    vm.update { it.copy(autoDiscoveryEnabled = value) }
                },
            )
            SettingsRow(
                title = "Visible to others",
                subtitle = "Allow other devices to see this device",
                checked = settings.discoveryVisible,
                onCheckedChange = { value ->
                    vm.update { it.copy(discoveryVisible = value) }
                },
            )
            SettingsRow(
                title = "Require approval",
                subtitle = "Ask before receiving files",
                checked = settings.approvalPolicy != com.fastshare.app.domain.model.ApprovalPolicy.ACCEPT_ALL,
                onCheckedChange = { value ->
                    vm.update {
                        it.copy(approvalPolicy = if (value)
                            com.fastshare.app.domain.model.ApprovalPolicy.ALWAYS_ASK
                        else com.fastshare.app.domain.model.ApprovalPolicy.ACCEPT_ALL)
                    }
                },
            )
            SettingsRow(
                title = "Encrypt transfers (TLS)",
                subtitle = "Use TLS for all transfers",
                checked = settings.requireTls,
                onCheckedChange = { value -> vm.update { it.copy(requireTls = value) } },
            )
            SettingsRow(
                title = "Dynamic color",
                subtitle = "Match system wallpaper",
                checked = settings.dynamicColor,
                onCheckedChange = { value -> vm.update { it.copy(dynamicColor = value) } },
            )
            SettingsRow(
                title = "Keep screen on",
                subtitle = "While transferring",
                checked = settings.keepScreenOnDuringTransfer,
                onCheckedChange = { value -> vm.update { it.copy(keepScreenOnDuringTransfer = value) } },
            )
            SettingsRow(
                title = "Vibrate on complete",
                subtitle = "Haptic feedback when finished",
                checked = settings.vibrateOnComplete,
                onCheckedChange = { value -> vm.update { it.copy(vibrateOnComplete = value) } },
            )
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(vertical = 10.dp)) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
        }
    }
}
