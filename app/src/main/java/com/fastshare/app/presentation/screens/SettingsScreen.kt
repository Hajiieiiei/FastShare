package com.fastshare.app.presentation.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            SettingsRow("Auto discovery", "Broadcast and scan for nearby devices", settings.autoDiscoveryEnabled) { v -> vm.update { it.copy(autoDiscoveryEnabled = v) } }
            SettingsRow("Visible to others", "Allow other devices to see this device", settings.discoveryVisible) { v -> vm.update { it.copy(discoveryVisible = v) } }
            SettingsRow("Require approval", "Ask before receiving files", settings.approvalPolicy != com.fastshare.app.domain.model.ApprovalPolicy.ACCEPT_ALL) { v ->
                vm.update { it.copy(approvalPolicy = if (v) com.fastshare.app.domain.model.ApprovalPolicy.ALWAYS_ASK else com.fastshare.app.domain.model.ApprovalPolicy.ACCEPT_ALL) }
            }
            SettingsRow("Encrypt transfers (TLS)", "Use TLS for all transfers", settings.requireTls) { v -> vm.update { it.copy(requireTls = v) } }
            SettingsRow("Dynamic color", "Match system wallpaper", settings.dynamicColor) { v -> vm.update { it.copy(dynamicColor = v) } }
            SettingsRow("Keep screen on", "While transferring", settings.keepScreenOnDuringTransfer) { v -> vm.update { it.copy(keepScreenOnDuringTransfer = v) } }
            SettingsRow("Vibrate on complete", "Haptic feedback when finished", settings.vibrateOnComplete) { v -> vm.update { it.copy(vibrateOnComplete = v) } }
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(vertical = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
        }
    }
}
