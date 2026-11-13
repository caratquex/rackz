package com.rackz.stackmyrackz.ui.sync

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rackz.stackmyrackz.data.preferences.SyncPreferencesRepository
import com.rackz.stackmyrackz.data.preferences.SyncPreferencesState
import com.rackz.stackmyrackz.data.repository.TransactionRepository
import com.rackz.stackmyrackz.data.sync.SheetsSyncEngine
import com.rackz.stackmyrackz.data.sync.SyncOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncUiState(
    val spreadsheetId: String = "",
    val accountEmail: String? = null,
    val manualAccessToken: String? = null,
    val lastSyncedAtEpochMillis: Long? = null,
    val lastSyncError: String? = null,
    val isSyncing: Boolean = false,
    val pendingCount: Int = 0
)

sealed interface SyncEvent {
    data class LaunchConsent(val intent: Intent) : SyncEvent
    data class Message(val text: String) : SyncEvent
}

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncPreferences: SyncPreferencesRepository,
    transactionRepository: TransactionRepository,
    private val syncEngine: SheetsSyncEngine
) : ViewModel() {

    val uiState: StateFlow<SyncUiState> = combine(
        syncPreferences.state,
        transactionRepository.observePendingSyncCount()
    ) { prefs: SyncPreferencesState, pendingCount: Int ->
        SyncUiState(
            spreadsheetId = prefs.spreadsheetId,
            accountEmail = prefs.accountEmail,
            manualAccessToken = prefs.manualAccessToken,
            lastSyncedAtEpochMillis = prefs.lastSyncedAtEpochMillis,
            lastSyncError = prefs.lastSyncError,
            isSyncing = prefs.isSyncing,
            pendingCount = pendingCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SyncUiState()
    )

    private val _events = MutableSharedFlow<SyncEvent>()
    val events = _events.asSharedFlow()

    fun onAccountSelected(email: String?) {
        viewModelScope.launch {
            syncPreferences.setAccountEmail(email)
            if (!email.isNullOrBlank()) {
                runSyncInternal()
            }
        }
    }

    fun onSpreadsheetIdSaved(rawInput: String) {
        viewModelScope.launch {
            syncPreferences.setSpreadsheetId(rawInput)
        }
    }

    fun onManualTokenSaved(token: String?) {
        viewModelScope.launch {
            syncPreferences.setManualAccessToken(token)
        }
    }

    fun onConsentGranted() {
        viewModelScope.launch {
            runSyncInternal()
        }
    }

    fun onSyncNow() {
        viewModelScope.launch {
            runSyncInternal()
        }
    }

    private suspend fun runSyncInternal() {
        when (val outcome = syncEngine.syncAll()) {
            is SyncOutcome.Success -> {
                val msg = buildString {
                    append("Synced")
                    if (outcome.conflictCount > 0) {
                        append(" · ${outcome.conflictCount} conflict")
                    }
                }
                _events.emit(SyncEvent.Message(msg))
            }
            is SyncOutcome.ConsentRequired -> {
                _events.emit(SyncEvent.LaunchConsent(outcome.intent))
            }
            is SyncOutcome.NotConfigured -> {
                _events.emit(SyncEvent.Message(outcome.reason))
            }
            is SyncOutcome.Error -> {
                _events.emit(SyncEvent.Message(outcome.message))
            }
        }
    }
}
