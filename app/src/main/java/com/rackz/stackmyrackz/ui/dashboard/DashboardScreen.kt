package com.rackz.stackmyrackz.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rackz.stackmyrackz.domain.AmountInputEngine
import com.rackz.stackmyrackz.model.SyncStatus
import com.rackz.stackmyrackz.model.TransactionType
import java.time.format.DateTimeFormatter

@Composable
fun DashboardRoute(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(uiState = uiState, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    modifier: Modifier = Modifier
) {
    val monthTitle = uiState.currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    val dateFormatter = DateTimeFormatter.ofPattern("d MMM")

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(monthTitle) }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SummaryMetric(
                            label = "Income",
                            amountMinor = uiState.incomeMinor
                        )
                        SummaryMetric(
                            label = "Expense",
                            amountMinor = uiState.expenseMinor
                        )
                        SummaryMetric(
                            label = "Net",
                            amountMinor = uiState.netMinor
                        )
                    }
                }
            }

            if (uiState.transactions.isEmpty()) {
                item {
                    Text(
                        text = "No transactions yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                items(
                    items = uiState.transactions,
                    key = { it.id }
                ) { tx ->
                    val prefix = if (tx.type == TransactionType.EXPENSE) "-" else "+"
                    ListItem(
                        headlineContent = { Text(tx.category) },
                        supportingContent = tx.remark?.let { remark -> { Text(remark) } },
                        leadingContent = {
                            Icon(
                                imageVector = if (tx.syncStatus == SyncStatus.SYNCED) {
                                    Icons.Outlined.CloudDone
                                } else {
                                    Icons.Outlined.CloudQueue
                                },
                                contentDescription = tx.syncStatus.name
                            )
                        },
                        trailingContent = {
                            Column {
                                Text(
                                    text = "$prefix${AmountInputEngine.formatMinorCurrency(tx.amountMinor)}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = tx.transactionDate.format(dateFormatter),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    amountMinor: Long
) {
    val sign = if (amountMinor < 0) "-" else ""
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "$sign${AmountInputEngine.formatMinorCurrency(kotlin.math.abs(amountMinor))}",
            style = MaterialTheme.typography.titleMedium
        )
    }
}
