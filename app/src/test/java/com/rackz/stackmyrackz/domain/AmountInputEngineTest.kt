package com.rackz.stackmyrackz.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountInputEngineTest {

    @Test
    fun initialState_isZeroAndCannotContinue() {
        val state = AmountInputState()
        assertEquals(0L, state.amountMinor)
        assertEquals("RM 0.00", state.formattedAmount)
        assertFalse(state.canContinue)
        assertFalse(state.isCalculatorMode)
        assertNull(state.expressionPreview)
    }

    @Test
    fun digitAndDecimalEntry_formatsThousandsAndRespectsTwoDecimals() {
        var state = AmountInputState()
        "1250".forEach { state = AmountInputEngine.onDigit(state, it) }
        assertEquals("RM 1,250", state.formattedAmount)
        assertEquals(125000L, state.amountMinor)
        assertTrue(state.canContinue)

        state = AmountInputEngine.onDecimalPoint(state)
        assertEquals("RM 1,250.", state.formattedAmount)

        // Second decimal point should be ignored.
        state = AmountInputEngine.onDecimalPoint(state)
        assertEquals("RM 1,250.", state.formattedAmount)

        state = AmountInputEngine.onDigit(state, '5')
        state = AmountInputEngine.onDigit(state, '8')
        // Third decimal digit should be ignored.
        state = AmountInputEngine.onDigit(state, '9')

        assertEquals("RM 1,250.58", state.formattedAmount)
        assertEquals(125058L, state.amountMinor)
    }

    @Test
    fun leadingZeroes_areNormalized() {
        var state = AmountInputState()
        state = AmountInputEngine.onDigit(state, '0')
        state = AmountInputEngine.onDigit(state, '0')
        assertEquals("RM 0", state.formattedAmount)
        assertEquals(0L, state.amountMinor)

        state = AmountInputEngine.onDigit(state, '5')
        assertEquals("RM 5", state.formattedAmount)
        assertEquals(500L, state.amountMinor)

        var decimalZero = AmountInputState()
        decimalZero = AmountInputEngine.onDecimalPoint(decimalZero)
        decimalZero = AmountInputEngine.onDigit(decimalZero, '7')
        assertEquals("RM 0.7", decimalZero.formattedAmount)
        assertEquals(70L, decimalZero.amountMinor)
    }

    @Test
    fun backspace_removesDigitsAndOperatorsCleanly() {
        var state = AmountInputState()
        state = AmountInputEngine.onDigit(state, '4')
        state = AmountInputEngine.onDigit(state, '2')
        state = AmountInputEngine.onBackspace(state)
        assertEquals("RM 4", state.formattedAmount)
        assertEquals(400L, state.amountMinor)

        state = AmountInputEngine.onBackspace(state)
        assertEquals("RM 0.00", state.formattedAmount)
        assertEquals(0L, state.amountMinor)
    }

    @Test
    fun calculatorMode_evaluatesExpressionsWithPrecedence() {
        var state = AmountInputEngine.onToggleCalculatorMode(AmountInputState())
        assertTrue(state.isCalculatorMode)

        // 10 + 5 × 2 = 20.00
        "10".forEach { state = AmountInputEngine.onDigit(state, it) }
        state = AmountInputEngine.onOperator(state, CalcOperator.ADD)
        state = AmountInputEngine.onDigit(state, '5')
        state = AmountInputEngine.onOperator(state, CalcOperator.MULTIPLY)
        state = AmountInputEngine.onDigit(state, '2')

        assertEquals("10 + 5 × 2", state.expressionPreview)
        assertEquals(2000L, state.amountMinor)
        assertEquals("RM 20.00", state.formattedAmount)

        state = AmountInputEngine.onEquals(state)
        assertNull(state.expressionPreview)
        assertEquals(2000L, state.amountMinor)
        assertEquals("RM 20", state.formattedAmount)
    }

    @Test
    fun calculatorMode_divisionByZeroAndNegativeResultsClampSafelyToZero() {
        var state = AmountInputEngine.onToggleCalculatorMode(AmountInputState())
        state = AmountInputEngine.onDigit(state, '9')
        state = AmountInputEngine.onOperator(state, CalcOperator.DIVIDE)
        state = AmountInputEngine.onDigit(state, '0')
        assertEquals(0L, state.amountMinor)
        assertFalse(state.canContinue)

        var negative = AmountInputEngine.onToggleCalculatorMode(AmountInputState())
        negative = AmountInputEngine.onDigit(negative, '5')
        negative = AmountInputEngine.onOperator(negative, CalcOperator.SUBTRACT)
        "12".forEach { negative = AmountInputEngine.onDigit(negative, it) }
        assertEquals(0L, negative.amountMinor)
        assertFalse(negative.canContinue)
    }

    @Test
    fun togglingCalculatorModeOff_evaluatesActiveExpression() {
        var state = AmountInputEngine.onToggleCalculatorMode(AmountInputState())
        "12.50".forEach {
            state = if (it == '.') AmountInputEngine.onDecimalPoint(state)
            else AmountInputEngine.onDigit(state, it)
        }
        state = AmountInputEngine.onOperator(state, CalcOperator.ADD)
        "2.50".forEach {
            state = if (it == '.') AmountInputEngine.onDecimalPoint(state)
            else AmountInputEngine.onDigit(state, it)
        }

        state = AmountInputEngine.onToggleCalculatorMode(state)
        assertFalse(state.isCalculatorMode)
        assertNull(state.expressionPreview)
        assertEquals(1500L, state.amountMinor)
    }

    @Test
    fun formatMinorCurrency_formatsSenAccurately() {
        assertEquals("RM 0.00", AmountInputEngine.formatMinorCurrency(0L))
        assertEquals("RM 0.05", AmountInputEngine.formatMinorCurrency(5L))
        assertEquals("RM 1,234.56", AmountInputEngine.formatMinorCurrency(123456L))
    }
}
