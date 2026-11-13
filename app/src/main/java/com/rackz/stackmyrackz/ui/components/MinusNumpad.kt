package com.rackz.stackmyrackz.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.rackz.stackmyrackz.domain.CalcOperator

val MINUS_BUTTON_GAP = 3.dp

@Composable
fun MinusNumpad(
    isCalculatorMode: Boolean,
    hasOperators: Boolean,
    canApply: Boolean,
    onDigitPressed: (Char) -> Unit,
    onDecimalPressed: () -> Unit,
    onBackspacePressed: () -> Unit,
    onClearPressed: () -> Unit,
    onOperatorPressed: (CalcOperator) -> Unit,
    onEqualsPressed: () -> Unit,
    onApplyPressed: () -> Unit,
    onToggleCalculatorMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    val effectiveProgress by animateFloatAsState(
        targetValue = if (isCalculatorMode) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "MinusNumpadCalcProgress"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .pointerInput(isCalculatorMode, hasOperators) {
                var accumulatedDrag = 0f
                var hasTriggered = false
                detectVerticalDragGestures(
                    onDragStart = {
                        accumulatedDrag = 0f
                        hasTriggered = false
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDrag += dragAmount
                        if (!hasTriggered) {
                            val shouldExpand = accumulatedDrag < -90f && !isCalculatorMode
                            val shouldCollapse =
                                accumulatedDrag > 90f && isCalculatorMode && !hasOperators
                            if (shouldExpand || shouldCollapse) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onToggleCalculatorMode()
                                hasTriggered = true
                            }
                        }
                    }
                )
            }
    ) {
        if (effectiveProgress > 0.01f) {
            OperatorRow(
                effectiveProgress = effectiveProgress,
                onOperatorPressed = { op ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onOperatorPressed(op)
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(4f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(3f)
            ) {
                val rows = remember {
                    listOf(
                        listOf('7', '8', '9'),
                        listOf('4', '5', '6'),
                        listOf('1', '2', '3')
                    )
                }
                rows.forEach { rowDigits ->
                    ExpandableRow(
                        items = rowDigits,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) { _, digit, interactionSource ->
                        NumpadButton(
                            modifier = Modifier.padding(MINUS_BUTTON_GAP),
                            type = NumpadButtonType.DEFAULT,
                            text = digit.toString(),
                            interactionSource = interactionSource,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onDigitPressed(digit)
                            }
                        )
                    }
                }

                val dotButton: @Composable RowScope.(MutableInteractionSource) -> Unit =
                    { interactionSource ->
                        NumpadButton(
                            modifier = Modifier.padding(MINUS_BUTTON_GAP),
                            type = NumpadButtonType.OPERATOR,
                            text = ".",
                            interactionSource = interactionSource,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onDecimalPressed()
                            }
                        )
                    }

                val zeroButton: @Composable RowScope.(MutableInteractionSource) -> Unit =
                    { interactionSource ->
                        NumpadButton(
                            modifier = Modifier.padding(MINUS_BUTTON_GAP),
                            type = NumpadButtonType.DEFAULT,
                            text = "0",
                            interactionSource = interactionSource,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onDigitPressed('0')
                            }
                        )
                    }

                ExpandableRow(
                    itemCount = 2,
                    baseWeights = listOf(1.5f, 3f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { index, interactionSource ->
                    when (index) {
                        0 -> dotButton(interactionSource)
                        1 -> zeroButton(interactionSource)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
            ) {
                NumpadButton(
                    modifier = Modifier
                        .weight(1f)
                        .padding(MINUS_BUTTON_GAP),
                    type = NumpadButtonType.TERTIARY,
                    icon = Icons.AutoMirrored.Rounded.Backspace,
                    contentDescription = "Backspace",
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onBackspacePressed()
                    },
                    onLongClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onClearPressed()
                    }
                )

                val checkWeight = if (effectiveProgress > 0.01f) {
                    3f - (1f * effectiveProgress)
                } else {
                    3f
                }

                NumpadButton(
                    modifier = Modifier
                        .weight(checkWeight)
                        .padding(MINUS_BUTTON_GAP),
                    type = NumpadButtonType.PRIMARY,
                    icon = Icons.Rounded.Check,
                    contentDescription = "Continue",
                    enabled = canApply,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onApplyPressed()
                    }
                )

                if (effectiveProgress > 0.01f) {
                    NumpadButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(effectiveProgress)
                            .padding(MINUS_BUTTON_GAP)
                            .graphicsLayer(alpha = effectiveProgress),
                        type = NumpadButtonType.OPERATOR,
                        text = "=",
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onEqualsPressed()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.OperatorRow(
    effectiveProgress: Float,
    onOperatorPressed: (CalcOperator) -> Unit
) {
    val operators = remember {
        listOf(
            CalcOperator.DIVIDE,
            CalcOperator.MULTIPLY,
            CalcOperator.ADD,
            CalcOperator.SUBTRACT
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(effectiveProgress.coerceAtLeast(0.01f))
            .clipToBounds(),
        contentAlignment = Alignment.BottomCenter
    ) {
        ExpandableRow(
            items = operators,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .graphicsLayer(alpha = effectiveProgress)
        ) { _, operator, interactionSource ->
            NumpadButton(
                modifier = Modifier.padding(MINUS_BUTTON_GAP),
                type = NumpadButtonType.OPERATOR,
                text = operator.symbol,
                interactionSource = interactionSource,
                onClick = { onOperatorPressed(operator) }
            )
        }
    }
}
