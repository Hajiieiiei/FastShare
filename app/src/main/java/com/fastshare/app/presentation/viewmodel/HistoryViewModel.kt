package com.fastshare.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastshare.app.data.repository.TransferRepository
import com.fastshare.app.domain.model.HistoryStats
import com.fastshare.app.domain.model.TransferRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val records: List<TransferRecord> = emptyList(),
    val stats: HistoryStats = HistoryStats(),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val transferRepository: TransferRepository,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = combine(
        transferRepository.history, transferRepository.stats,
    ) { records, stats ->
        HistoryUiState(records = records, stats = stats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun clearHistory() = viewModelScope.launch { transferRepository.clearHistory() }

    fun deleteRecord(id: String) = viewModelScope.launch { transferRepository.deleteRecord(id) }
}
