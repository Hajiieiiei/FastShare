package com.fastshare.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastshare.app.data.local.datastore.SettingsRepository
import com.fastshare.app.domain.model.TransferSession
import com.fastshare.app.services.transfer.TransferEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransfersUiState(
    val active: List<TransferSession> = emptyList(),
    val incomingRequests: List<TransferEngine.IncomingRequestUi> = emptyList(),
)

@HiltViewModel
class TransferViewModel @Inject constructor(
    private val transferEngine: TransferEngine,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<TransfersUiState> = transferEngine.sessions
        .map { sessions ->
            TransfersUiState(active = sessions.values.mapNotNull { it.session })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransfersUiState())

    val incomingRequests: StateFlow<List<TransferEngine.IncomingRequestUi>> = transferEngine.incomingRequests

    fun approveIncoming(sessionId: String, accept: Boolean, alwaysAllow: Boolean = false) {
        transferEngine.approveIncoming(sessionId, accept, alwaysAllow)
    }

    fun pause(sessionId: String) { /* engine.pause(sessionId) */ }

    fun resume(sessionId: String) { /* engine.resume(sessionId) */ }

    fun cancel(sessionId: String) { /* engine.cancel(sessionId) */ }

    fun removeSession(sessionId: String) {
        transferEngine.removeSession(sessionId)
    }
}
