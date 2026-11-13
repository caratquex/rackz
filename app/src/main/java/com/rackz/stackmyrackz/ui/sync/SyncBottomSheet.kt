package com.rackz.stackmyrackz.ui.sync

import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.common.AccountPicker
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SyncBottomSheetRoute(
    onDismiss: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    viewModel: SyncViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!email.isNullOrBlank()) {
                viewModel.onAccountSelected(email)
            }
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onConsentGranted()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SyncEvent.LaunchConsent -> consentLauncher.launch(event.intent)
                is SyncEvent.Message -> onShowSnackbar(event.text)
            }
        }
    }

    SyncBottomSheet(
        uiState = uiState,
        onChooseGoogleAccount = {
            val intent = AccountPicker.newChooseAccountIntent(
                AccountPicker.AccountChooserOptions.Builder()
                    .setAllowableAccountsTypes(listOf("com.google"))
                    .build()
            )
            accountPickerLauncher.launch(intent)
        },
        onDisconnectAccount = { viewModel.onAccountSelected(null) },
        onSaveSpreadsheetId = viewModel::onSpreadsheetIdSaved,
        onSaveManualToken = viewModel::onManualTokenSaved,
        onSyncNow = viewModel::onSyncNow,
        onDismiss = onDismiss
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncBottomSheet(
    uiState: SyncUiState,
    onChooseGoogleAccount: () -> Unit,
    onDisconnectAccount: () -> Unit,
    onSaveSpreadsheetId: (String) -> Unit,
    onSaveManualToken: (String?) -> Unit,
    onSyncNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var spreadsheetInput by remember(uiState.spreadsheetId) {
        mutableStateOf(uiState.spreadsheetId)
    }
    var showTokenField by remember { mutableStateOf(false) }
    var tokenInput by remember(uiState.manualAccessToken) {
        mutableStateOf(uiState.manualAccessToken.orEmpty())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Google Sheets",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                SyncStatusPill(
                    isSyncing = uiState.isSyncing,
                    pendingCount = uiState.pendingCount,
                    hasSyncedOnce = uiState.lastSyncedAtEpochMillis != null
                )
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onChooseGoogleAccount)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = uiState.accountEmail ?: "Google Account",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (uiState.accountEmail != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (uiState.accountEmail != null) {
                        IconButton(
                            onClick = onDisconnectAccount,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Disconnect",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onChooseGoogleAccount,
                            shape = CircleShape
                        ) {
                            Text("Connect")
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = spreadsheetInput,
                        onValueChange = { spreadsheetInput = it },
                        label = { Text("Spreadsheet ID") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showTokenField = !showTokenField }) {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (showTokenField) "Hide Token" else "Access Token")
                        }

                        if (spreadsheetInput.trim() != uiState.spreadsheetId) {
                            FilledTonalButton(
                                onClick = { onSaveSpreadsheetId(spreadsheetInput) },
                                shape = CircleShape
                            ) {
                                Text("Save ID")
                            }
                        }
                    }

                    if (showTokenField) {
                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = {
                                tokenInput = it
                                onSaveManualToken(it)
                            },
                            label = { Text("OAuth Access Token") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            val lastError = uiState.lastSyncError
            val lastSynced = uiState.lastSyncedAtEpochMillis
            if (!lastError.isNullOrBlank()) {
                Text(
                    text = lastError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            } else if (lastSynced != null) {
                Text(
                    text = "Last synced ${formatSyncTime(lastSynced)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            Button(
                onClick = {
                    if (spreadsheetInput.trim() != uiState.spreadsheetId) {
                        onSaveSpreadsheetId(spreadsheetInput)
                    }
                    onSyncNow()
                },
                enabled = !uiState.isSyncing,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (uiState.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isSyncing) "Syncing" else "Sync Now",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun SyncStatusPill(
    isSyncing: Boolean,
    pendingCount: Int,
    hasSyncedOnce: Boolean
) {
    val label = when {
        isSyncing -> "Syncing"
        pendingCount > 0 -> "$pendingCount Pending"
        hasSyncedOnce -> "Synced"
        else -> "Local"
    }
    val container = when {
        isSyncing || hasSyncedOnce && pendingCount == 0 -> MaterialTheme.colorScheme.primaryContainer
        pendingCount > 0 -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when {
        isSyncing || hasSyncedOnce && pendingCount == 0 -> MaterialTheme.colorScheme.onPrimaryContainer
        pendingCount > 0 -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = content
        )
    }
}

private fun formatSyncTime(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.of("Asia/Kuala_Lumpur"))
        .format(formatter)
}
