package com.rackz.stackmyrackz.ui.wealth

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rackz.stackmyrackz.domain.AmountInputEngine
import com.rackz.stackmyrackz.ui.components.SyncStatusIconButton
import com.rackz.stackmyrackz.ui.theme.ExpressiveShapes
import com.rackz.stackmyrackz.ui.theme.displaySmallCondensed
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun WealthRoute(
    onOpenSync: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onShowSnackbar: (message: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: WealthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is WealthEvent.Message -> {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onShowSnackbar(event.text)
                }
            }
        }
    }

    WealthScreen(
        uiState = uiState,
        onOpenSync = onOpenSync,
        onOpenAppearance = onOpenAppearance,
        onPreviousMonth = viewModel::onPreviousMonth,
        onNextMonth = viewModel::onNextMonth,
        onResetToCurrentMonth = viewModel::onResetToCurrentMonth,
        onChartRangeSelected = viewModel::onChartRangeSelected,
        onOpenEditSheet = viewModel::onOpenEditSheet,
        onDismissEditSheet = viewModel::onDismissEditSheet,
        onSaveMonthlyWealth = viewModel::onSaveMonthlyWealth,
        onOpenReminderSheet = viewModel::onOpenReminderSheet,
        onDismissReminderSheet = viewModel::onDismissReminderSheet,
        onSaveReminderConfig = viewModel::onSaveReminderConfig,
        onShowSnackbar = onShowSnackbar,
        modifier = modifier
    )
}

@Composable
fun WealthScreen(
    uiState: WealthUiState,
    onOpenSync: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    onResetToCurrentMonth: () -> Unit = {},
    onChartRangeSelected: (WealthChartRange) -> Unit = {},
    onOpenEditSheet: (YearMonth?) -> Unit = {},
    onDismissEditSheet: () -> Unit = {},
    onSaveMonthlyWealth: (
        savingsSavedMinor: Long,
        savingsTargetMinor: Long,
        investmentSavedMinor: Long,
        investmentTargetMinor: Long,
        note: String?
    ) -> Unit = { _, _, _, _, _ -> },
    onOpenReminderSheet: () -> Unit = {},
    onDismissReminderSheet: () -> Unit = {},
    onSaveReminderConfig: (
        enabled: Boolean,
        frequency: String,
        dayOfMonth: Int,
        dayOfWeek: Int,
        hour: Int,
        minute: Int
    ) -> Unit = { _, _, _, _, _, _ -> },
    onShowSnackbar: (message: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH) }
    val monthTitle = uiState.currentMonth.format(monthFormatter)

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
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onPreviousMonth()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronLeft,
                            contentDescription = "Previous month",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = monthTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(CircleShape)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onResetToCurrentMonth()
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )

                Surface(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onNextMonth()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "Next month",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onOpenReminderSheet()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.reminderEnabled) {
                            Icons.Rounded.NotificationsActive
                        } else {
                            Icons.Rounded.NotificationsNone
                        },
                        contentDescription = "Wealth reminders",
                        tint = if (uiState.reminderEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                SyncStatusIconButton(
                    isSyncing = uiState.isSyncing,
                    hasSyncError = uiState.hasSyncError,
                    pendingSyncCount = uiState.pendingSyncCount,
                    onClick = onOpenSync,
                    modifier = Modifier.size(36.dp)
                )

                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onOpenAppearance()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = "Appearance",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Surface(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onOpenEditSheet(uiState.currentMonth)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 2.dp,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = if (uiState.hasCurrentMonthEntry) "Update Month" else "Log Month",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HeroWealthCard(
                    cumulativeTotalMinor = uiState.cumulativeTotalMinor,
                    combinedGoalPercent = uiState.combinedGoalPercent
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WealthGoalCard(
                        title = "Savings",
                        savedMinor = uiState.savingsSavedMinor,
                        targetMinor = uiState.savingsTargetMinor,
                        progress = uiState.savingsProgress,
                        percent = uiState.savingsPercent,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        accentContentColor = MaterialTheme.colorScheme.onSecondary,
                        cardShape = ExpressiveShapes.splitPairCardShape(isStart = true),
                        badgeShape = ExpressiveShapes.Sunny,
                        onClick = { onOpenEditSheet(uiState.currentMonth) },
                        modifier = Modifier.weight(1f)
                    )
                    WealthGoalCard(
                        title = "Investment",
                        savedMinor = uiState.investmentSavedMinor,
                        targetMinor = uiState.investmentTargetMinor,
                        progress = uiState.investmentProgress,
                        percent = uiState.investmentPercent,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        accentContentColor = MaterialTheme.colorScheme.onTertiary,
                        cardShape = ExpressiveShapes.splitPairCardShape(isStart = false),
                        badgeShape = ExpressiveShapes.Sunny,
                        onClick = { onOpenEditSheet(uiState.currentMonth) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                WealthGrowthChartCard(
                    snapshots = uiState.chartSnapshots,
                    selectedMonth = uiState.currentMonth,
                    chartRange = uiState.chartRange,
                    onRangeSelected = onChartRangeSelected
                )
            }

            item {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                val rows = uiState.snapshotRows
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rows.forEachIndexed { index, snapshot ->
                        WealthSnapshotRow(
                            snapshot = snapshot,
                            isSelectedMonth = snapshot.month == uiState.currentMonth,
                            shape = ExpressiveShapes.segmentedItemShape(index, rows.size),
                            onClick = { onOpenEditSheet(snapshot.month) }
                        )
                    }
                }
            }
        }
    }

    if (uiState.isEditSheetOpen) {
        EditMonthlyWealthModalSheet(
            month = uiState.currentMonth,
            initialSavingsSavedMinor = uiState.savingsSavedMinor,
            initialSavingsTargetMinor = uiState.savingsTargetMinor,
            initialInvestmentSavedMinor = uiState.investmentSavedMinor,
            initialInvestmentTargetMinor = uiState.investmentTargetMinor,
            initialNote = uiState.note,
            onDismiss = onDismissEditSheet,
            onSave = onSaveMonthlyWealth
        )
    }

    if (uiState.isReminderSheetOpen) {
        ReminderConfigModalSheet(
            initialEnabled = uiState.reminderEnabled,
            initialFrequency = uiState.reminderFrequency,
            initialDayOfMonth = uiState.reminderDayOfMonth,
            initialDayOfWeek = uiState.reminderDayOfWeek,
            initialHour = uiState.reminderHour,
            initialMinute = uiState.reminderMinute,
            onDismiss = onDismissReminderSheet,
            onPermissionDenied = {
                onShowSnackbar("Allow notifications in Settings to enable Wealth reminders")
            },
            onSave = onSaveReminderConfig
        )
    }
}

@Composable
private fun HeroWealthCard(
    cumulativeTotalMinor: Long,
    combinedGoalPercent: Int?
) {
    val numeric = AmountInputEngine.formatMinorCurrency(cumulativeTotalMinor, prefix = "")
    val baseStyle = MaterialTheme.typography.displaySmallCondensed.copy(fontWeight = FontWeight.Bold)
    val prefixColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)

    val totalText = remember(numeric, prefixColor) {
        buildAnnotatedString {
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
        shape = RoundedCornerShape(32.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(156.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 38.dp, y = (-30).dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.06f),
                        shape = ExpressiveShapes.Sunny10
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Total Wealth",
                        style = MaterialTheme.typography.labelLarge,
                        color = prefixColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = totalText,
                        style = baseStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (combinedGoalPercent != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = ExpressiveShapes.Sunny,
                        modifier = Modifier.size(58.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$combinedGoalPercent%",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WealthGoalCard(
    title: String,
    savedMinor: Long,
    targetMinor: Long,
    progress: Float,
    percent: Int,
    containerColor: Color,
    contentColor: Color,
    accentColor: Color,
    accentContentColor: Color,
    cardShape: Shape,
    badgeShape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "${title}Progress"
    )

    Surface(
        onClick = onClick,
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = cardShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(accentColor, badgeShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accentContentColor,
                        maxLines = 1
                    )
                }
            }

            Column {
                Text(
                    text = AmountInputEngine.formatMinorCurrency(savedMinor),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "of ${AmountInputEngine.formatMinorCurrency(targetMinor)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.14f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                        .clip(CircleShape)
                        .background(accentColor)
                )
            }
        }
    }
}

@Composable
private fun WealthGrowthChartCard(
    snapshots: List<WealthMonthlySnapshot>,
    selectedMonth: YearMonth,
    chartRange: WealthChartRange,
    onRangeSelected: (WealthChartRange) -> Unit
) {
    val defaultIndex = remember(snapshots, selectedMonth) {
        snapshots.indexOfFirst { it.month == selectedMonth }
            .takeIf { it >= 0 }
            ?: (snapshots.lastIndex).coerceAtLeast(0)
    }
    var inspectedIndex by remember(snapshots, selectedMonth, chartRange) {
        mutableIntStateOf(defaultIndex)
    }
    val activeSnapshot = snapshots.getOrNull(inspectedIndex.coerceIn(0, (snapshots.size - 1).coerceAtLeast(0)))
    val shortMonthFormatter = remember { DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH) }
    val savingsColor = MaterialTheme.colorScheme.primary
    val investmentColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val dotBorderColor = MaterialTheme.colorScheme.surfaceContainerLow

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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Wealth Growth",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (activeSnapshot != null) {
                        Text(
                            text = "${activeSnapshot.month.format(shortMonthFormatter)} · ${
                                AmountInputEngine.formatMinorCurrency(activeSnapshot.cumulativeTotalMinor)
                            }",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        WealthChartRange.entries.forEach { range ->
                            val selected = range == chartRange
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .clickable { onRangeSelected(range) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = range.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (snapshots.isNotEmpty()) {
                val maxSeriesValue = snapshots.maxOf {
                    maxOf(it.cumulativeSavingsMinor, it.cumulativeInvestmentMinor)
                }.coerceAtLeast(10_000L)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp)
                        .pointerInput(snapshots.size) {
                            fun updateIndexForX(x: Float) {
                                if (snapshots.size <= 1 || size.width <= 0) return
                                val fraction = (x / size.width.toFloat()).coerceIn(0f, 1f)
                                val idx = (fraction * (snapshots.size - 1)).roundToInt()
                                    .coerceIn(0, snapshots.lastIndex)
                                inspectedIndex = idx
                            }
                            detectTapGestures { offset -> updateIndexForX(offset.x) }
                        }
                        .pointerInput(snapshots.size) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                if (snapshots.size <= 1 || size.width <= 0) return@detectHorizontalDragGestures
                                val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val idx = (fraction * (snapshots.size - 1)).roundToInt()
                                    .coerceIn(0, snapshots.lastIndex)
                                inspectedIndex = idx
                            }
                        }
                ) {
                    val horizontalPad = 8.dp.toPx()
                    val topPad = 14.dp.toPx()
                    val bottomPad = 12.dp.toPx()
                    val chartWidth = (size.width - horizontalPad * 2f).coerceAtLeast(1f)
                    val chartHeight = (size.height - topPad - bottomPad).coerceAtLeast(1f)
                    val baselineY = topPad + chartHeight

                    for (step in 0..3) {
                        val y = topPad + (chartHeight / 3f) * step
                        drawLine(
                            color = gridColor,
                            start = Offset(horizontalPad, y),
                            end = Offset(horizontalPad + chartWidth, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    fun buildPoints(selector: (WealthMonthlySnapshot) -> Long): List<Offset> {
                        val count = snapshots.size
                        return snapshots.mapIndexed { idx, snap ->
                            val x = if (count <= 1) {
                                horizontalPad + chartWidth / 2f
                            } else {
                                horizontalPad + (idx.toFloat() / (count - 1).toFloat()) * chartWidth
                            }
                            val ratio = (selector(snap).toFloat() / maxSeriesValue.toFloat())
                                .coerceIn(0f, 1f)
                            val y = baselineY - ratio * chartHeight
                            Offset(x, y)
                        }
                    }

                    fun drawSmoothSeries(points: List<Offset>, lineColor: Color) {
                        if (points.isEmpty()) return
                        if (points.size == 1) {
                            drawCircle(color = lineColor, radius = 5.dp.toPx(), center = points.first())
                            return
                        }
                        val linePath = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) {
                                val prev = points[i - 1]
                                val curr = points[i]
                                val midX = (prev.x + curr.x) / 2f
                                cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                            }
                        }
                        val fillPath = Path().apply {
                            addPath(linePath)
                            lineTo(points.last().x, baselineY)
                            lineTo(points.first().x, baselineY)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    lineColor.copy(alpha = 0.22f),
                                    lineColor.copy(alpha = 0.01f)
                                ),
                                startY = topPad,
                                endY = baselineY
                            )
                        )
                        drawPath(
                            path = linePath,
                            color = lineColor,
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    val savingsPoints = buildPoints { it.cumulativeSavingsMinor }
                    val investmentPoints = buildPoints { it.cumulativeInvestmentMinor }

                    val safeIndex = inspectedIndex.coerceIn(0, snapshots.lastIndex)
                    val activeX = savingsPoints[safeIndex].x
                    drawLine(
                        color = guideColor,
                        start = Offset(activeX, topPad),
                        end = Offset(activeX, baselineY),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    drawSmoothSeries(investmentPoints, investmentColor)
                    drawSmoothSeries(savingsPoints, savingsColor)

                    listOf(
                        investmentPoints[safeIndex] to investmentColor,
                        savingsPoints[safeIndex] to savingsColor
                    ).forEach { (pt, color) ->
                        drawCircle(color = dotBorderColor, radius = 6.dp.toPx(), center = pt)
                        drawCircle(color = color, radius = 4.dp.toPx(), center = pt)
                    }
                }

                val firstLabel = snapshots.first().month.format(shortMonthFormatter)
                val midLabel = snapshots[snapshots.size / 2].month.format(shortMonthFormatter)
                val lastLabel = snapshots.last().month.format(shortMonthFormatter)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = firstLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (snapshots.size >= 3) {
                        Text(
                            text = midLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (snapshots.size > 1) {
                        Text(
                            text = lastLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChartLegendItem(
                        label = "Savings",
                        valueMinor = activeSnapshot?.cumulativeSavingsMinor ?: 0L,
                        color = savingsColor,
                        shape = ExpressiveShapes.Sunny
                    )
                    ChartLegendItem(
                        label = "Investment",
                        valueMinor = activeSnapshot?.cumulativeInvestmentMinor ?: 0L,
                        color = investmentColor,
                        shape = ExpressiveShapes.Cookie9Sided
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartLegendItem(
    label: String,
    valueMinor: Long,
    color: Color,
    shape: Shape
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(color, shape)
        )
        Text(
            text = "$label · ${AmountInputEngine.formatMinorCurrency(valueMinor)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WealthSnapshotRow(
    snapshot: WealthMonthlySnapshot,
    isSelectedMonth: Boolean,
    shape: Shape,
    onClick: () -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH) }
    val hasInvestmentLead = snapshot.investmentSavedMinor > snapshot.savingsSavedMinor
    val badgeShape = if (hasInvestmentLead) ExpressiveShapes.Cookie9Sided else ExpressiveShapes.Sunny
    val badgeBg = when {
        isSelectedMonth -> MaterialTheme.colorScheme.primaryContainer
        hasInvestmentLead -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val badgeFg = when {
        isSelectedMonth -> MaterialTheme.colorScheme.onPrimaryContainer
        hasInvestmentLead -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = if (isSelectedMonth) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = shape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(badgeBg, badgeShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasInvestmentLead) {
                        Icons.Rounded.ShowChart
                    } else {
                        Icons.Rounded.Savings
                    },
                    contentDescription = null,
                    tint = badgeFg,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = snapshot.month.format(formatter),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = AmountInputEngine.formatMinorCurrency(snapshot.cumulativeTotalMinor),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (snapshot.hasRecordedEntry) {
                        "+${AmountInputEngine.formatMinorCurrency(snapshot.totalMonthlySavedMinor)}"
                    } else {
                        "No entry"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (snapshot.hasRecordedEntry) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditMonthlyWealthModalSheet(
    month: YearMonth,
    initialSavingsSavedMinor: Long,
    initialSavingsTargetMinor: Long,
    initialInvestmentSavedMinor: Long,
    initialInvestmentTargetMinor: Long,
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (
        savingsSavedMinor: Long,
        savingsTargetMinor: Long,
        investmentSavedMinor: Long,
        investmentTargetMinor: Long,
        note: String?
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH) }

    var savingsSavedText by remember(month) {
        mutableStateOf(formatMinorToEditableDecimal(initialSavingsSavedMinor))
    }
    var savingsTargetText by remember(month) {
        mutableStateOf(formatMinorToEditableDecimal(initialSavingsTargetMinor))
    }
    var investmentSavedText by remember(month) {
        mutableStateOf(formatMinorToEditableDecimal(initialInvestmentSavedMinor))
    }
    var investmentTargetText by remember(month) {
        mutableStateOf(formatMinorToEditableDecimal(initialInvestmentTargetMinor))
    }
    var noteText by remember(month) { mutableStateOf(initialNote) }

    val parsedSavingsSaved = remember(savingsSavedText) { parseNonNegativeMinor(savingsSavedText) }
    val parsedSavingsTarget = remember(savingsTargetText) { parseNonNegativeMinor(savingsTargetText) }
    val parsedInvestmentSaved = remember(investmentSavedText) { parseNonNegativeMinor(investmentSavedText) }
    val parsedInvestmentTarget = remember(investmentTargetText) { parseNonNegativeMinor(investmentTargetText) }

    val canSave = parsedSavingsSaved != null &&
        parsedSavingsTarget != null &&
        parsedInvestmentSaved != null &&
        parsedInvestmentTarget != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = month.format(monthFormatter),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Targets carry forward to upcoming months.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(MaterialTheme.colorScheme.secondary, ExpressiveShapes.Sunny)
                    )
                    Text(
                        text = "Savings",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = savingsSavedText,
                        onValueChange = { if (isValidWealthDecimalInput(it)) savingsSavedText = it },
                        label = { Text("Saved") },
                        prefix = { Text("RM ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = savingsTargetText,
                        onValueChange = { if (isValidWealthDecimalInput(it)) savingsTargetText = it },
                        label = { Text("Monthly Target") },
                        prefix = { Text("RM ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(MaterialTheme.colorScheme.tertiary, ExpressiveShapes.Cookie9Sided)
                    )
                    Text(
                        text = "Investment",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = investmentSavedText,
                        onValueChange = { if (isValidWealthDecimalInput(it)) investmentSavedText = it },
                        label = { Text("Invested") },
                        prefix = { Text("RM ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = investmentTargetText,
                        onValueChange = { if (isValidWealthDecimalInput(it)) investmentTargetText = it },
                        label = { Text("Monthly Target") },
                        prefix = { Text("RM ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                placeholder = { Text("Optional note") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val ss = parsedSavingsSaved ?: return@Button
                    val st = parsedSavingsTarget ?: return@Button
                    val iv = parsedInvestmentSaved ?: return@Button
                    val it = parsedInvestmentTarget ?: return@Button
                    onSave(ss, st, iv, it, noteText)
                },
                enabled = canSave,
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReminderConfigModalSheet(
    initialEnabled: Boolean,
    initialFrequency: String,
    initialDayOfMonth: Int,
    initialDayOfWeek: Int,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onPermissionDenied: () -> Unit,
    onSave: (
        enabled: Boolean,
        frequency: String,
        dayOfMonth: Int,
        dayOfWeek: Int,
        hour: Int,
        minute: Int
    ) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var enabled by remember { mutableStateOf(initialEnabled) }
    var frequency by remember { mutableStateOf(initialFrequency) }
    var dayOfMonth by remember { mutableIntStateOf(initialDayOfMonth.coerceIn(1, 28)) }
    var dayOfWeek by remember { mutableIntStateOf(initialDayOfWeek.coerceIn(1, 7)) }
    var hour by remember { mutableIntStateOf(initialHour.coerceIn(0, 23)) }
    var minute by remember { mutableIntStateOf(initialMinute.coerceIn(0, 59)) }
    var isTimeDialogOpen by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            enabled = true
        } else {
            enabled = false
            onPermissionDenied()
        }
    }

    fun handleToggleEnabled(desired: Boolean) {
        if (!desired) {
            enabled = false
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                enabled = true
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            enabled = true
        }
    }

    val formattedTime = remember(hour, minute) {
        LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Wealth Reminders",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { handleToggleEnabled(!enabled) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, ExpressiveShapes.Sunny),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Check-in reminder",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Notify to update monthly savings & investments",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = ::handleToggleEnabled
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("MONTHLY" to "Monthly", "WEEKLY" to "Weekly").forEach { (key, label) ->
                        val selected = frequency == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable { frequency = key }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }

            if (frequency == "MONTHLY") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Day of month (1–28)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val quickDays = listOf(1, 5, 10, 15, 20, 25, 28)
                        quickDays.forEach { day ->
                            FilterChip(
                                selected = dayOfMonth == day,
                                onClick = { dayOfMonth = day },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                label = { Text("Day $day") }
                            )
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Day of week",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DayOfWeek.entries.forEach { dow ->
                            FilterChip(
                                selected = dayOfWeek == dow.value,
                                onClick = { dayOfWeek = dow.value },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                label = { Text(dow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)) }
                            )
                        }
                    }
                }
            }

            Surface(
                onClick = { isTimeDialogOpen = true },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Reminder time",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Button(
                onClick = {
                    onSave(
                        enabled,
                        frequency,
                        dayOfMonth,
                        dayOfWeek,
                        hour,
                        minute
                    )
                },
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
                Text("Save Reminder", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (isTimeDialogOpen) {
        val timePickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute
        )
        AlertDialog(
            onDismissRequest = { isTimeDialogOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        hour = timePickerState.hour
                        minute = timePickerState.minute
                        isTimeDialogOpen = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { isTimeDialogOpen = false }) {
                    Text("Cancel")
                }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }
}

private fun formatMinorToEditableDecimal(amountMinor: Long): String =
    BigDecimal.valueOf(amountMinor.coerceAtLeast(0L))
        .movePointLeft(2)
        .stripTrailingZeros()
        .toPlainString()
        .ifEmpty { "0" }

private fun isValidWealthDecimalInput(value: String): Boolean {
    if (value.isEmpty()) return true
    if (value.count { it == '.' } > 1) return false
    if (!value.all { it in '0'..'9' || it == '.' }) return false
    val parts = value.split('.', limit = 2)
    if (parts[0].length > 9) return false
    if (parts.size == 2 && parts[1].length > 2) return false
    return true
}

private fun parseNonNegativeMinor(value: String): Long? {
    val clean = value.trim()
    if (clean.isEmpty()) return 0L
    if (clean == ".") return null
    val bd = clean.toBigDecimalOrNull() ?: return null
    if (bd < BigDecimal.ZERO) return null
    return bd.setScale(2, RoundingMode.HALF_UP)
        .movePointRight(2)
        .toLong()
}
