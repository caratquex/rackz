package com.rackz.stackmyrackz.ui.dashboard

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rackz.stackmyrackz.data.local.TransactionEntity
import com.rackz.stackmyrackz.domain.AmountInputEngine
import com.rackz.stackmyrackz.model.DefaultCategories
import com.rackz.stackmyrackz.model.SyncStatus
import com.rackz.stackmyrackz.model.TransactionType
import com.rackz.stackmyrackz.ui.components.SyncStatusIconButton
import com.rackz.stackmyrackz.ui.components.TransactionTypePill
import com.rackz.stackmyrackz.ui.theme.displaySmallCondensed
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun DashboardRoute(
    onOpenSync: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onShowSnackbar: (message: String, actionLabel: String?, onAction: (() -> Unit)?) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DashboardEvent.TransactionDeleted -> {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onShowSnackbar("Transaction deleted", "Undo") {
                        viewModel.onUndoDelete(event.id)
                    }
                }
                is DashboardEvent.Message -> {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onShowSnackbar(event.text, null, null)
                }
            }
        }
    }

    DashboardScreen(
        uiState = uiState,
        onOpenSync = onOpenSync,
        onOpenAppearance = onOpenAppearance,
        onPreviousMonth = viewModel::onPreviousMonth,
        onNextMonth = viewModel::onNextMonth,
        onResetToCurrentMonth = viewModel::onResetToCurrentMonth,
        onTypeFilterSelected = viewModel::onTypeFilterSelected,
        onCategoryFilterSelected = viewModel::onCategoryFilterSelected,
        onCategoryBreakdownClicked = viewModel::onCategoryBreakdownClicked,
        onOpenHistorySheet = viewModel::onOpenHistorySheet,
        onDismissHistorySheet = viewModel::onDismissHistorySheet,
        onTransactionClicked = viewModel::onTransactionClicked,
        onDismissEditSheet = viewModel::onDismissEditSheet,
        onUpdateTransaction = viewModel::onUpdateTransaction,
        onDeleteTransaction = viewModel::onDeleteTransaction,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onOpenSync: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    onResetToCurrentMonth: () -> Unit = {},
    onTypeFilterSelected: (HistoryTypeFilter) -> Unit = {},
    onCategoryFilterSelected: (String?) -> Unit = {},
    onCategoryBreakdownClicked: (String) -> Unit = {},
    onOpenHistorySheet: () -> Unit = {},
    onDismissHistorySheet: () -> Unit = {},
    onTransactionClicked: (TransactionEntity) -> Unit = {},
    onDismissEditSheet: () -> Unit = {},
    onUpdateTransaction: (
        id: String,
        type: TransactionType,
        amountMinor: Long,
        category: String,
        remark: String,
        date: LocalDate
    ) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteTransaction: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val monthTitle = uiState.currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onPreviousMonth()
                }) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronLeft,
                        contentDescription = "Previous month",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = monthTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onResetToCurrentMonth()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                IconButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onNextMonth()
                }) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Next month",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SyncStatusIconButton(
                    isSyncing = uiState.isSyncing,
                    hasSyncError = uiState.hasSyncError,
                    pendingSyncCount = uiState.pendingSyncCount,
                    onClick = onOpenSync
                )

                IconButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onOpenAppearance()
                }) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = "Appearance",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HeroNetCard(
                    netMinor = uiState.netMinor,
                    savingsRatePercent = uiState.savingsRatePercent
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        label = "Income",
                        amountMinor = uiState.incomeMinor,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Expense",
                        amountMinor = uiState.expenseMinor,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (uiState.categoryBreakdown.isNotEmpty()) {
                item {
                    CategoryBreakdownCard(
                        items = uiState.categoryBreakdown,
                        selectedCategory = if (uiState.typeFilter == HistoryTypeFilter.EXPENSE) {
                            uiState.selectedCategoryFilter
                        } else {
                            null
                        },
                        onCategoryClicked = onCategoryBreakdownClicked
                    )
                }
            }

            if (uiState.transactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No transactions",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item {
                    HistoryFilterBar(
                        typeFilter = uiState.typeFilter,
                        filteredCount = uiState.filteredTransactions.size,
                        onTypeFilterSelected = onTypeFilterSelected,
                        onSeeAllClicked = onOpenHistorySheet
                    )
                }

                if (uiState.availableCategories.size > 1 || uiState.selectedCategoryFilter != null) {
                    item {
                        CategoryFilterChipsRow(
                            categories = uiState.availableCategories,
                            selectedCategory = uiState.selectedCategoryFilter,
                            onCategorySelected = onCategoryFilterSelected
                        )
                    }
                }

                items(
                    items = uiState.recentTransactions,
                    key = { it.id }
                ) { tx ->
                    TransactionItemCard(
                        tx = tx,
                        dateFormatter = dateFormatter,
                        onClick = { onTransactionClicked(tx) }
                    )
                }
            }
        }
    }

    if (uiState.isHistorySheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissHistorySheet,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = monthTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                HistoryFilterBar(
                    typeFilter = uiState.typeFilter,
                    filteredCount = uiState.filteredTransactions.size,
                    onTypeFilterSelected = onTypeFilterSelected,
                    onSeeAllClicked = null
                )

                if (uiState.availableCategories.size > 1 || uiState.selectedCategoryFilter != null) {
                    CategoryFilterChipsRow(
                        categories = uiState.availableCategories,
                        selectedCategory = uiState.selectedCategoryFilter,
                        onCategorySelected = onCategoryFilterSelected
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = uiState.filteredTransactions,
                        key = { it.id }
                    ) { tx ->
                        TransactionItemCard(
                            tx = tx,
                            dateFormatter = dateFormatter,
                            onClick = { onTransactionClicked(tx) }
                        )
                    }
                }
            }
        }
    }

    val editingTx = uiState.editingTransaction
    if (editingTx != null) {
        EditTransactionModalSheet(
            transaction = editingTx,
            expenseCategories = uiState.expenseCategories,
            incomeCategories = uiState.incomeCategories,
            onDismiss = onDismissEditSheet,
            onSave = { type, amountMinor, category, remark, date ->
                onUpdateTransaction(editingTx.id, type, amountMinor, category, remark, date)
            },
            onDelete = { onDeleteTransaction(editingTx.id) }
        )
    }
}

@Composable
private fun HeroNetCard(
    netMinor: Long,
    savingsRatePercent: Int?
) {
    val sign = if (netMinor < 0) "-" else ""
    val numeric = AmountInputEngine.formatMinorCurrency(abs(netMinor), prefix = "")
    val baseStyle = MaterialTheme.typography.displaySmallCondensed.copy(fontWeight = FontWeight.Bold)
    val prefixColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)

    val netText = remember(sign, numeric, prefixColor) {
        buildAnnotatedString {
            if (sign.isNotEmpty()) {
                append(sign)
            }
            pushStyle(
                SpanStyle(
                    fontSize = baseStyle.fontSize * 0.5f,
                    fontWeight = FontWeight.Bold,
                    color = prefixColor,
                    baselineShift = BaselineShift(0.15f)
                )
            )
            append("RM ")
            pop()
            append(numeric)
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onSurface,
        contentColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Net",
                    style = MaterialTheme.typography.labelLarge,
                    color = prefixColor
                )

                if (savingsRatePercent != null) {
                    val badgeBg = if (savingsRatePercent >= 0) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                    val badgeFg = if (savingsRatePercent >= 0) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                    Surface(
                        color = badgeBg,
                        contentColor = badgeFg,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "$savingsRatePercent%",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = netText,
                style = baseStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    amountMinor: Long,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = AmountInputEngine.formatMinorCurrency(amountMinor),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CategoryBreakdownCard(
    items: List<CategorySpendItem>,
    selectedCategory: String?,
    onCategoryClicked: (String) -> Unit
) {
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.inversePrimary,
        MaterialTheme.colorScheme.outline
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(CircleShape),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items.forEachIndexed { index, item ->
                    val segmentColor = palette[index % palette.size]
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(item.fraction.coerceAtLeast(0.02f))
                            .background(segmentColor)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEachIndexed { index, item ->
                    val dotColor = palette[index % palette.size]
                    val isSelected = selectedCategory == item.category
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                                } else {
                                    Color.Transparent
                                }
                            )
                            .clickable { onCategoryClicked(item.category) }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(dotColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = item.category,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${item.percentage}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = AmountInputEngine.formatMinorCurrency(item.amountMinor),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryFilterBar(
    typeFilter: HistoryTypeFilter,
    filteredCount: Int,
    onTypeFilterSelected: (HistoryTypeFilter) -> Unit,
    onSeeAllClicked: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HistoryTypeFilter.entries.forEach { filter ->
                    val selected = typeFilter == filter
                    val bg by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                        label = "HistoryFilterBg"
                    )
                    val fg by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        label = "HistoryFilterFg"
                    )

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(bg)
                            .clickable { onTypeFilterSelected(filter) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = fg
                        )
                    }
                }
            }
        }

        if (onSeeAllClicked != null) {
            Surface(
                onClick = onSeeAllClicked,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Text(
                    text = "All ($filteredCount)",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun CategoryFilterChipsRow(
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories, key = { it }) { category ->
            val selected = selectedCategory == category
            FilterChip(
                selected = selected,
                onClick = { onCategorySelected(category) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                label = { Text(category) }
            )
        }
    }
}

@Composable
private fun TransactionItemCard(
    tx: TransactionEntity,
    dateFormatter: DateTimeFormatter,
    onClick: () -> Unit
) {
    val isExpense = tx.type == TransactionType.EXPENSE
    val prefix = if (isExpense) "-" else "+"
    val isSyncProblem = tx.syncStatus == SyncStatus.FAILED || tx.syncStatus == SyncStatus.CONFLICT
    val badgeContainer = when {
        isSyncProblem -> MaterialTheme.colorScheme.errorContainer
        isExpense -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val badgeTint = when {
        isSyncProblem -> MaterialTheme.colorScheme.onErrorContainer
        isExpense -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val statusIcon = when (tx.syncStatus) {
        SyncStatus.SYNCED -> Icons.Rounded.CloudDone
        SyncStatus.PENDING, SyncStatus.SYNCING -> Icons.Rounded.CloudQueue
        SyncStatus.FAILED -> Icons.Rounded.CloudOff
        SyncStatus.CONFLICT -> Icons.Rounded.WarningAmber
    }
    val amountColor = if (isExpense) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(badgeContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = tx.syncStatus.name,
                    tint = badgeTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.category,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val remark = tx.remark?.takeIf { it.isNotBlank() }
                if (remark != null) {
                    Text(
                        text = remark,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$prefix${AmountInputEngine.formatMinorCurrency(tx.amountMinor)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = amountColor
                )
                Text(
                    text = tx.transactionDate.format(dateFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditTransactionModalSheet(
    transaction: TransactionEntity,
    expenseCategories: List<String>,
    incomeCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: (
        type: TransactionType,
        amountMinor: Long,
        category: String,
        remark: String,
        date: LocalDate
    ) -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type by remember(transaction.id) { mutableStateOf(transaction.type) }
    var amountText by remember(transaction.id) {
        mutableStateOf(
            BigDecimal.valueOf(transaction.amountMinor)
                .movePointLeft(2)
                .toPlainString()
        )
    }
    var category by remember(transaction.id) { mutableStateOf(transaction.category) }
    var remark by remember(transaction.id) { mutableStateOf(transaction.remark.orEmpty()) }
    var date by remember(transaction.id) { mutableStateOf(transaction.transactionDate) }
    var isDatePickerOpen by remember { mutableStateOf(false) }

    val availableCategories = remember(type, category, expenseCategories, incomeCategories) {
        val base = when (type) {
            TransactionType.EXPENSE -> expenseCategories.ifEmpty { DefaultCategories.expenseCategories }
            TransactionType.INCOME -> incomeCategories.ifEmpty { DefaultCategories.incomeCategories }
        }
        if (category in base) base else listOf(category) + base
    }

    val parsedMinor = remember(amountText) {
        parseDecimalToMinor(amountText)
    }
    val canSave = parsedMinor != null && parsedMinor > 0L

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
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TransactionTypePill(
                    selectedType = type,
                    onTypeSelected = { nextType ->
                        if (type != nextType) {
                            type = nextType
                            val nextCategories = when (nextType) {
                                TransactionType.EXPENSE -> expenseCategories.ifEmpty {
                                    DefaultCategories.expenseCategories
                                }
                                TransactionType.INCOME -> incomeCategories.ifEmpty {
                                    DefaultCategories.incomeCategories
                                }
                            }
                            if (category !in nextCategories) {
                                category = nextCategories.first()
                            }
                        }
                    }
                )

                AssistChip(
                    onClick = { isDatePickerOpen = true },
                    shape = CircleShape,
                    label = { Text(formatEditChipDate(date)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { next ->
                    if (isValidAmountInput(next)) {
                        amountText = next
                    }
                },
                prefix = { Text("RM ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                availableCategories.forEach { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = { category = item },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        label = { Text(item) }
                    )
                }
            }

            OutlinedTextField(
                value = remark,
                onValueChange = { remark = it },
                placeholder = { Text("Remark") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onDelete,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Delete"
                    )
                }

                Button(
                    onClick = {
                        val minor = parsedMinor ?: return@Button
                        onSave(type, minor, category, remark, date)
                    },
                    enabled = canSave,
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (isDatePickerOpen) {
        val initialMillis = date
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { isDatePickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            date = Instant.ofEpochMilli(selectedMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                        }
                        isDatePickerOpen = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { isDatePickerOpen = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun isValidAmountInput(value: String): Boolean {
    if (value.isEmpty()) return true
    if (value.count { it == '.' } > 1) return false
    if (!value.all { it in '0'..'9' || it == '.' }) return false
    val parts = value.split('.', limit = 2)
    if (parts[0].length > 9) return false
    if (parts.size == 2 && parts[1].length > 2) return false
    return true
}

private fun parseDecimalToMinor(value: String): Long? {
    val clean = value.trim()
    if (clean.isEmpty() || clean == ".") return null
    val bd = clean.toBigDecimalOrNull() ?: return null
    if (bd <= BigDecimal.ZERO) return null
    return bd.setScale(2, RoundingMode.HALF_UP)
        .movePointRight(2)
        .toLong()
}

private fun formatEditChipDate(date: LocalDate): String {
    val today = LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur"))
    return if (date == today) {
        "Today"
    } else {
        date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
}
