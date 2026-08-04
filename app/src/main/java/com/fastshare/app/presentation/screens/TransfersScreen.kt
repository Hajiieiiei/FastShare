package com.fastshare.app.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fastshare.app.presentation.components.EmptyState
import com.fastshare.app.presentation.components.TransferProgressCard
import com.fastshare.app.presentation.viewmodel.TransferViewModel

@Composable
fun TransfersScreen() {
    val vm: TransferViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Transfers") }) },
    ) { padding ->
        if (state.active.isEmpty()) {
            EmptyState(
                title = "No active transfers",
                subtitle = "Start sending or receiving to see progress here",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.active, key = { it.id }) { session ->
                    TransferProgressCard(
                        session = session,
                        onPause = { vm.pause(session.id) },
                        onResume = { vm.resume(session.id) },
                        onCancel = { vm.cancel(session.id) },
                    )
                }
            }
        }
    }
}
