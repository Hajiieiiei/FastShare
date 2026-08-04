package com.fastshare.app.presentation.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fastshare.app.presentation.viewmodel.DiscoveryViewModel
import com.fastshare.app.presentation.components.DeviceCard
import java.util.UUID

@Composable
fun SendScreen(onClose: () -> Unit) {
    val vm: DiscoveryViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val selectedUris = remember { mutableStateListOf<android.net.Uri>() }
    var selectedDeviceId by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        selectedUris.clear()
        selectedUris.addAll(uris)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send Files") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Button(
                onClick = { launcher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(if (selectedUris.isEmpty()) "Pick Files" else "${selectedUris.size} files selected")
            }
            Text(
                "Choose a destination",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.devices, key = { it.info.deviceId }) { device ->
                    DeviceCard(
                        device = device,
                        selected = selectedDeviceId == device.info.deviceId,
                        onClick = { selectedDeviceId = device.info.deviceId },
                    )
                }
            }
        }
    }
}
