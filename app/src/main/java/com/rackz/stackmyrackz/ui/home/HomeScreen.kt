package com.rackz.stackmyrackz.ui.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rackz.stackmyrackz.domain.CalcOperator
import com.rackz.stackmyrackz.model.TransactionType
import com.rackz.stackmyrackz.ui.theme.RacksTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun HomeRoute(
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.TransactionSaved -> {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onShowSnackbar(event.message)
                }
            }
        }
    }

    HomeScreen(
        uiState = uiState,
        onTypeSelected = viewModel::onTypeSelected,
        onDigitPressed = viewModel::onDigitPressed,
        onDecimalPressed = viewModel::onDecimalPressed,
        onBackspacePressed = viewModel::onBackspacePressed,
        onOperatorPressed = viewModel::onOperatorPressed,
        onEqualsPressed = viewModel::onEqualsPressed,
        onToggleCalculatorMode = viewModel::onToggleCalculatorMode,
        onOpenSheet = viewModel::onOpenSheet,
        onDismissSheet = viewModel::onDismissSheet,
        onCategorySelected = viewModel::onCategorySelected,
        onRemarkChanged = viewModel::onRemarkChanged,
        onOpenDatePicker = viewModel::onOpenDatePicker,
        onDismissDatePicker = viewModel::onDismissDatePicker,
        onDateSelected = viewModel::onDateSelected,
        onSaveTransaction = viewModel::onSaveTransaction,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onTypeSelected: (TransactionType) -> Unit,
    onDigitPressed: (Char) -> Unit,
    onDecimalPressed: () -> Unit,
    onBackspacePressed: () -> Unit,
    onOperatorPressed: (CalcOperator) -> Unit,
    onEqualsPressed: () -> Unit,
    onToggleCalculatorMode: () -> Unit,
    onOpenSheet: () -> Unit,
    onDismissSheet: () -> Unit,
    onCategorySelected: (String) -> Unit,
    onRemarkChanged: (String) -> Unit,
    onOpenDatePicker: () -> Unit,
    onDismissDatePicker: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onSaveTransaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("Racks") },
            actions = {
                IconButton(
                    onClick = {},
                    modifier = Modifier.semantics {
                        contentDescription = if (uiState.pendingSyncCount > 0) {
                            "${uiState.pendingSyncCount} pending sync"
                        } else {
                            "Synced"
                        }
                    }
                ) {
                    if (uiState.pendingSyncCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge { Text(uiState.pendingSyncCount.toString()) }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudQueue,
                                contentDescription = null
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.CloudDone,
                            contentDescription = null
                        )
                    }
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                TransactionType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = uiState.transactionType == type,
                        onClick = { onTypeSelected(type) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = TransactionType.entries.size
                        )
                    ) {
                        Text(type.label)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.End
            ) {
                val preview = uiState.amountInput.expressionPreview
                if (preview != null) {
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconToggleButton(
                        checked = uiState.amountInput.isCalculatorMode,
                        onCheckedChange = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onToggleCalculatorMode()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Calculate,
                            contentDescription = "Calculator mode"
                        )
                    }

                    val amountText = uiState.amountInput.formattedAmount
                    val amountStyle = when {
                        amountText.length <= 9 -> MaterialTheme.typography.displayLarge
                        amountText.length <= 12 -> MaterialTheme.typography.displayMedium
                        amountText.length <= 15 -> MaterialTheme.typography.displaySmall
                        else -> MaterialTheme.typography.headlineLarge
                    }

                    Text(
                        text = amountText,
                        style = amountStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.5f))

            KeypadGrid(
                isCalculatorMode = uiState.amountInput.isCalculatorMode,
                onDigitPressed = { digit ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onDigitPressed(digit)
                },
                onDecimalPressed = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onDecimalPressed()
                },
                onBackspacePressed = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onBackspacePressed()
                },
                onOperatorPressed = { operator ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onOperatorPressed(operator)
                },
                onEqualsPressed = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onEqualsPressed()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onOpenSheet,
                enabled = uiState.amountInput.canContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Continue")
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (uiState.isSheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissSheet,
            sheetState = sheetState
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
                    Text(
                        text = uiState.amountInput.formattedAmount,
                        style = MaterialTheme.typography.headlineMedium
                    )

                    AssistChip(
                        onClick = onOpenDatePicker,
                        label = { Text(formatChipDate(uiState.selectedDate)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.CalendarToday,
                                contentDescription = null
                            )
                        }
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    uiState.availableCategories.forEach { category ->
                        FilterChip(
                            selected = uiState.selectedCategory == category,
                            onClick = { onCategorySelected(category) },
                            label = { Text(category) }
                        )
                    }
                }

                OutlinedTextField(
                    value = uiState.remark,
                    onValueChange = onRemarkChanged,
                    label = { Text("Remark") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = onSaveTransaction,
                    enabled = uiState.canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("Save")
                }
            }
        }
    }

    if (uiState.isDatePickerOpen) {
        val initialMillis = uiState.selectedDate
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = onDismissDatePicker,
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            val date = Instant.ofEpochMilli(selectedMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            onDateSelected(date)
                        } else {
                            onDismissDatePicker()
                        }
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDatePicker) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun KeypadGrid(
    isCalculatorMode: Boolean,
    onDigitPressed: (Char) -> Unit,
    onDecimalPressed: () -> Unit,
    onBackspacePressed: () -> Unit,
    onOperatorPressed: (CalcOperator) -> Unit,
    onEqualsPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(3f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val rows = listOf(
                listOf('1', '2', '3'),
                listOf('4', '5', '6'),
                listOf('7', '8', '9')
            )
            rows.forEach { rowDigits ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowDigits.forEach { digit ->
                        KeypadDigitButton(
                            label = digit.toString(),
                            onClick = { onDigitPressed(digit) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KeypadDigitButton(
                    label = ".",
                    onClick = onDecimalPressed,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                KeypadDigitButton(
                    label = "0",
                    onClick = { onDigitPressed('0') },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                FilledTonalButton(
                    onClick = onBackspacePressed,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Backspace,
                        contentDescription = "Backspace"
                    )
                }
            }
        }

        AnimatedVisibility(visible = isCalculatorMode) {
            Column(
                modifier = Modifier
                    .width(72.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    CalcOperator.DIVIDE,
                    CalcOperator.MULTIPLY,
                    CalcOperator.SUBTRACT,
                    CalcOperator.ADD
                ).forEach { op ->
                    OutlinedButton(
                        onClick = { onOperatorPressed(op) },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Text(
                            text = op.symbol,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
                Button(
                    onClick = onEqualsPressed,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Text(
                        text = "=",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadDigitButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

private fun formatChipDate(date: LocalDate): String {
    val today = LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur"))
    return if (date == today) {
        "Today"
    } else {
        date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    RacksTheme {
        HomeScreen(
            uiState = HomeUiState(),
            onTypeSelected = {},
            onDigitPressed = {},
            onDecimalPressed = {},
            onBackspacePressed = {},
            onOperatorPressed = {},
            onEqualsPressed = {},
            onToggleCalculatorMode = {},
            onOpenSheet = {},
            onDismissSheet = {},
            onCategorySelected = {},
            onRemarkChanged = {},
            onOpenDatePicker = {},
            onDismissDatePicker = {},
            onDateSelected = {},
            onSaveTransaction = {}
        )
    }
}
