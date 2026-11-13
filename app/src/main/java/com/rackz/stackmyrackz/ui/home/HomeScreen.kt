package com.rackz.stackmyrackz.ui.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rackz.stackmyrackz.domain.CalcOperator
import com.rackz.stackmyrackz.model.TransactionType
import com.rackz.stackmyrackz.ui.components.MinusNumpad
import com.rackz.stackmyrackz.ui.components.SyncStatusIconButton
import com.rackz.stackmyrackz.ui.components.TransactionTypePill
import com.rackz.stackmyrackz.ui.theme.RacksTheme
import com.rackz.stackmyrackz.ui.theme.colorButton
import com.rackz.stackmyrackz.ui.theme.googleSansFlex
import com.rackz.stackmyrackz.ui.theme.titleLargeCondensed
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun HomeRoute(
    onShowSnackbar: (String) -> Unit,
    onOpenSync: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
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
        onClearPressed = viewModel::onClearPressed,
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
        onOpenSync = onOpenSync,
        onOpenAppearance = onOpenAppearance,
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
    onClearPressed: () -> Unit = {},
    onOpenSync: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val hasOperators = remember(uiState.amountInput.rawExpression) {
        uiState.amountInput.rawExpression.any { it in "+-×÷" }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            color = colorButton,
            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TransactionTypePill(
                        selectedType = uiState.transactionType,
                        onTypeSelected = { type ->
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onTypeSelected(type)
                        }
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val isCalc = uiState.amountInput.isCalculatorMode
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onToggleCalculatorMode()
                            },
                            colors = if (isCalc) {
                                IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Calculate,
                                contentDescription = "Calculator mode"
                            )
                        }

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

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    val preview = uiState.amountInput.expressionPreview
                    AnimatedVisibility(
                        visible = preview != null,
                        enter = fadeIn(tween(150)) + expandVertically(tween(150)),
                        exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = preview.orEmpty(),
                                style = MaterialTheme.typography.titleLargeCondensed,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    val amountText = uiState.amountInput.formattedAmount
                    val numericPart = amountText.removePrefix("RM ").trimStart()
                    val baseSize = when {
                        numericPart.length <= 7 -> 72.sp
                        numericPart.length <= 10 -> 56.sp
                        numericPart.length <= 13 -> 44.sp
                        else -> 34.sp
                    }
                    val prefixColor = MaterialTheme.colorScheme.onSurfaceVariant
                    val amountFont = remember { googleSansFlex(weight = 300, width = 95f, isRounded = true) }

                    val annotatedAmount = remember(numericPart, baseSize, prefixColor) {
                        buildAnnotatedString {
                            pushStyle(
                                SpanStyle(
                                    fontSize = baseSize * 0.42f,
                                    fontWeight = FontWeight.Bold,
                                    color = prefixColor,
                                    baselineShift = BaselineShift(0.18f)
                                )
                            )
                            append("RM ")
                            pop()
                            pushStyle(
                                SpanStyle(
                                    fontFamily = amountFont,
                                    fontSize = baseSize,
                                    fontWeight = FontWeight.Light
                                )
                            )
                            append(numericPart)
                            pop()
                        }
                    }

                    Text(
                        text = annotatedAmount,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        MinusNumpad(
            isCalculatorMode = uiState.amountInput.isCalculatorMode,
            hasOperators = hasOperators,
            canApply = uiState.amountInput.canContinue,
            onDigitPressed = onDigitPressed,
            onDecimalPressed = onDecimalPressed,
            onBackspacePressed = onBackspacePressed,
            onClearPressed = onClearPressed,
            onOperatorPressed = onOperatorPressed,
            onEqualsPressed = onEqualsPressed,
            onApplyPressed = onOpenSheet,
            onToggleCalculatorMode = onToggleCalculatorMode,
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .padding(top = 8.dp, bottom = 8.dp)
        )
    }

    if (uiState.isSheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissSheet,
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
                    Text(
                        text = uiState.amountInput.formattedAmount,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    AssistChip(
                        onClick = onOpenDatePicker,
                        shape = CircleShape,
                        label = { Text(formatChipDate(uiState.selectedDate)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.CalendarToday,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    uiState.availableCategories.forEach { category ->
                        val selected = uiState.selectedCategory == category
                        FilterChip(
                            selected = selected,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onCategorySelected(category)
                            },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            label = { Text(category) }
                        )
                    }
                }

                OutlinedTextField(
                    value = uiState.remark,
                    onValueChange = onRemarkChanged,
                    placeholder = { Text("Remark") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = onSaveTransaction,
                    enabled = uiState.canSave,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
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
