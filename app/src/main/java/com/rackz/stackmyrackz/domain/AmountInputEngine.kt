package com.rackz.stackmyrackz.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Supported arithmetic operators when calculator mode is active. */
enum class CalcOperator(val symbol: String) {
    ADD("+"),
    SUBTRACT("-"),
    MULTIPLY("×"),
    DIVIDE("÷")
}

/** Immutable UI state produced by [AmountInputEngine]. */
data class AmountInputState(
    val rawExpression: String = "",
    val amountMinor: Long = 0L,
    val formattedAmount: String = "RM 0.00",
    val expressionPreview: String? = null,
    val isCalculatorMode: Boolean = false
) {
    val canContinue: Boolean get() = amountMinor > 0L
}

/**
 * Pure state machine for MYR transaction amount entry in integer sen.
 * Supports fast numeric entry by default and optional inline arithmetic.
 */
object AmountInputEngine {

    private const val MAX_MINOR_AMOUNT = 999_999_999_99L
    private const val MAX_INTEGER_DIGITS = 9
    private val OPERATOR_CHARS = setOf('+', '-', '×', '÷')
    private val symbols = DecimalFormatSymbols(Locale.US)
    private val integerFormatter = DecimalFormat("#,##0", symbols)
    private val currencyFormatter = DecimalFormat("#,##0.00", symbols)

    fun formatMinorCurrency(amountMinor: Long, prefix: String = "RM "): String {
        val major = BigDecimal.valueOf(amountMinor).movePointLeft(2)
        return "$prefix${currencyFormatter.format(major)}"
    }

    fun onDigit(state: AmountInputState, digit: Char): AmountInputState {
        require(digit in '0'..'9')
        val expr = state.rawExpression
        val currentOperand = currentOperand(expr)

        if (currentOperand.contains('.')) {
            val decimals = currentOperand.substringAfter('.')
            if (decimals.length >= 2) return state
        } else {
            val cleanDigits = currentOperand.trimStart('0')
            if (cleanDigits.length >= MAX_INTEGER_DIGITS && !(currentOperand == "0" && digit == '0')) {
                return state
            }
        }

        val updatedExpr = when {
            currentOperand == "0" && digit == '0' -> expr
            currentOperand == "0" -> expr.dropLast(1) + digit
            else -> expr + digit
        }
        return recompute(state.copy(rawExpression = updatedExpr))
    }

    fun onDecimalPoint(state: AmountInputState): AmountInputState {
        val expr = state.rawExpression
        val currentOperand = currentOperand(expr)
        if (currentOperand.contains('.')) return state

        val updatedExpr = if (currentOperand.isEmpty()) {
            "${expr}0."
        } else {
            "$expr."
        }
        return recompute(state.copy(rawExpression = updatedExpr))
    }

    fun onBackspace(state: AmountInputState): AmountInputState {
        if (state.rawExpression.isEmpty()) return state
        val trimmed = state.rawExpression.trimEnd()
        val updatedExpr = if (trimmed.isNotEmpty() && trimmed.last() in OPERATOR_CHARS) {
            trimmed.dropLast(1).trimEnd()
        } else {
            state.rawExpression.dropLast(1)
        }
        return recompute(state.copy(rawExpression = updatedExpr))
    }

    fun onOperator(state: AmountInputState, operator: CalcOperator): AmountInputState {
        if (!state.isCalculatorMode) return state
        val trimmed = state.rawExpression.trimEnd()
        if (trimmed.isEmpty()) return state

        val updatedExpr = if (trimmed.last() in OPERATOR_CHARS) {
            trimmed.dropLast(1) + operator.symbol + " "
        } else {
            "$trimmed ${operator.symbol} "
        }
        return recompute(state.copy(rawExpression = updatedExpr))
    }

    fun onEquals(state: AmountInputState): AmountInputState {
        if (state.rawExpression.isEmpty()) return state
        val evaluatedMinor = evaluateMinor(state.rawExpression)
        if (evaluatedMinor <= 0L) {
            return state.copy(
                rawExpression = "",
                amountMinor = 0L,
                formattedAmount = "RM 0.00",
                expressionPreview = null
            )
        }
        val normalizedExpr = minorToEditableString(evaluatedMinor)
        return recompute(state.copy(rawExpression = normalizedExpr))
    }

    fun onToggleCalculatorMode(state: AmountInputState): AmountInputState {
        val nextMode = !state.isCalculatorMode
        return if (!nextMode && hasOperator(state.rawExpression)) {
            onEquals(state.copy(isCalculatorMode = false))
        } else {
            state.copy(isCalculatorMode = nextMode)
        }
    }

    fun reset(keepCalculatorMode: Boolean = false, currentMode: Boolean = false): AmountInputState =
        AmountInputState(isCalculatorMode = if (keepCalculatorMode) currentMode else false)

    private fun recompute(state: AmountInputState): AmountInputState {
        val expr = state.rawExpression
        if (expr.isEmpty()) {
            return state.copy(
                amountMinor = 0L,
                formattedAmount = "RM 0.00",
                expressionPreview = null
            )
        }

        val hasOp = hasOperator(expr)
        val minor = evaluateMinor(expr).coerceIn(0L, MAX_MINOR_AMOUNT)
        val formatted = if (!hasOp) {
            formatSingleOperand(expr)
        } else {
            formatMinorCurrency(minor)
        }

        return state.copy(
            amountMinor = minor,
            formattedAmount = formatted,
            expressionPreview = if (hasOp) expr.trim() else null
        )
    }

    private fun currentOperand(expr: String): String {
        val lastOpIndex = expr.indexOfLast { it in OPERATOR_CHARS }
        return if (lastOpIndex == -1) expr else expr.substring(lastOpIndex + 1).trimStart()
    }

    private fun hasOperator(expr: String): Boolean = expr.any { it in OPERATOR_CHARS }

    private fun formatSingleOperand(operand: String): String {
        val parts = operand.split('.', limit = 2)
        val intPart = parts[0].toLongOrNull() ?: 0L
        val formattedInt = integerFormatter.format(intPart)
        return if (parts.size == 2) {
            "RM $formattedInt.${parts[1]}"
        } else {
            "RM $formattedInt"
        }
    }

    private fun minorToEditableString(minor: Long): String {
        val major = BigDecimal.valueOf(minor).movePointLeft(2).stripTrailingZeros()
        return if (major.scale() <= 0) {
            major.toPlainString()
        } else {
            major.toPlainString()
        }
    }

    private fun evaluateMinor(expr: String): Long {
        val tokens = tokenize(expr)
        if (tokens.isEmpty()) return 0L

        // First pass: evaluate × and ÷ left-to-right.
        val additiveTerms = mutableListOf<Any>()
        var index = 0
        var current = tokens[0] as? BigDecimal ?: return 0L
        index = 1
        while (index < tokens.size) {
            val op = tokens[index] as? Char ?: break
            val rhs = tokens.getOrNull(index + 1) as? BigDecimal ?: break
            when (op) {
                '×' -> current = current.multiply(rhs)
                '÷' -> {
                    current = if (rhs.compareTo(BigDecimal.ZERO) == 0) {
                        BigDecimal.ZERO
                    } else {
                        current.divide(rhs, 6, RoundingMode.HALF_UP)
                    }
                }
                '+', '-' -> {
                    additiveTerms.add(current)
                    additiveTerms.add(op)
                    current = rhs
                }
            }
            index += 2
        }
        additiveTerms.add(current)

        // Second pass: evaluate + and - left-to-right.
        var result = additiveTerms[0] as BigDecimal
        var addIdx = 1
        while (addIdx < additiveTerms.size) {
            val op = additiveTerms[addIdx] as Char
            val rhs = additiveTerms[addIdx + 1] as BigDecimal
            result = when (op) {
                '+' -> result.add(rhs)
                '-' -> result.subtract(rhs)
                else -> result
            }
            addIdx += 2
        }

        if (result < BigDecimal.ZERO) return 0L
        return result.setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2)
            .toLong()
            .coerceIn(0L, MAX_MINOR_AMOUNT)
    }

    private fun tokenize(expr: String): List<Any> {
        val result = mutableListOf<Any>()
        val numberBuffer = StringBuilder()

        fun flushNumber() {
            if (numberBuffer.isNotEmpty()) {
                val raw = numberBuffer.toString().let {
                    if (it.endsWith('.')) it.dropLast(1) else it
                }
                val parsed = raw.toBigDecimalOrNull() ?: BigDecimal.ZERO
                result.add(parsed)
                numberBuffer.clear()
            }
        }

        for (ch in expr) {
            when {
                ch in '0'..'9' || ch == '.' -> numberBuffer.append(ch)
                ch in OPERATOR_CHARS -> {
                    flushNumber()
                    if (result.isNotEmpty() && result.last() is BigDecimal) {
                        result.add(ch)
                    }
                }
                ch == ' ' -> Unit
            }
        }
        flushNumber()
        if (result.isNotEmpty() && result.last() is Char) {
            result.removeAt(result.lastIndex)
        }
        return result
    }
}
