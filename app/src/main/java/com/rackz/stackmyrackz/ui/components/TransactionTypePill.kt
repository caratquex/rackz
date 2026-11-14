package com.rackz.stackmyrackz.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.rackz.stackmyrackz.model.TransactionType
import kotlin.math.roundToInt

/**
 * Material 3 Expressive segmented pill selector with a spring-animated sliding thumb
 * that tracks both selection changes and live horizontal swipe gestures.
 */
@Composable
fun TransactionTypePill(
    selectedType: TransactionType,
    onTypeSelected: (TransactionType) -> Unit,
    modifier: Modifier = Modifier,
    dragProgressOffset: Float = 0f
) {
    val baseFraction = if (selectedType == TransactionType.EXPENSE) 0f else 1f
    val targetFraction = (baseFraction + dragProgressOffset.coerceIn(-0.35f, 0.35f))
        .coerceIn(0f, 1f)

    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "TypePillSlide"
    )

    val thumbColor by animateColorAsState(
        targetValue = if (selectedType == TransactionType.INCOME) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "TypePillBg"
    )

    val expenseTextColor by animateColorAsState(
        targetValue = if (selectedType == TransactionType.EXPENSE) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ExpenseLabelColor"
    )

    val incomeTextColor by animateColorAsState(
        targetValue = if (selectedType == TransactionType.INCOME) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "IncomeLabelColor"
    )

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Layout(
            modifier = Modifier.padding(4.dp),
            content = {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(thumbColor)
                )
                TransactionType.entries.forEach { type ->
                    val labelColor = if (type == TransactionType.EXPENSE) {
                        expenseTextColor
                    } else {
                        incomeTextColor
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTypeSelected(type) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = type.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = labelColor
                        )
                    }
                }
            }
        ) { measurables, constraints ->
            val gapPx = 4.dp.roundToPx()
            val optionMeasurables = measurables.drop(1)
            val maxSlotWidth = ((constraints.maxWidth - gapPx * (optionMeasurables.size - 1)) /
                optionMeasurables.size).coerceAtLeast(1)
            val slotWidth = optionMeasurables.maxOf { it.maxIntrinsicWidth(constraints.maxHeight) }
                .coerceIn(1, maxSlotWidth)
            val slotHeight = optionMeasurables.maxOf { it.maxIntrinsicHeight(slotWidth) }
                .coerceIn(1, constraints.maxHeight.coerceAtLeast(1))

            val fixed = Constraints.fixed(slotWidth, slotHeight)
            val optionPlaceables = optionMeasurables.map { it.measure(fixed) }
            val thumbPlaceable = measurables.first().measure(fixed)

            val totalWidth = slotWidth * optionPlaceables.size +
                gapPx * (optionPlaceables.size - 1).coerceAtLeast(0)

            layout(totalWidth, slotHeight) {
                val thumbX = ((slotWidth + gapPx) * animatedFraction).roundToInt()
                thumbPlaceable.placeRelative(thumbX, 0)
                optionPlaceables.forEachIndexed { index, placeable ->
                    placeable.placeRelative(index * (slotWidth + gapPx), 0)
                }
            }
        }
    }
}
