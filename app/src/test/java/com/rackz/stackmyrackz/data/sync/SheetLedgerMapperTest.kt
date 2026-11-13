package com.rackz.stackmyrackz.data.sync

import com.rackz.stackmyrackz.data.local.TransactionEntity
import com.rackz.stackmyrackz.model.SyncStatus
import com.rackz.stackmyrackz.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class SheetLedgerMapperTest {

    @Test
    fun resolveExistingTabName_handlesStandardAndPartialTabs() {
        val tabs = listOf(
            "Notes",
            "Dashboard",
            "Sep 2026(Partial)",
            "Oct 2026",
            "Nov 2026",
            "Dec 2026"
        )

        assertEquals(
            "Sep 2026(Partial)",
            SheetLedgerMapper.resolveExistingTabName(YearMonth.of(2026, 9), tabs)
        )
        assertEquals(
            "Nov 2026",
            SheetLedgerMapper.resolveExistingTabName(YearMonth.of(2026, 11), tabs)
        )
        assertNull(
            SheetLedgerMapper.resolveExistingTabName(YearMonth.of(2027, 1), tabs)
        )
        assertEquals("Jan 2027", SheetLedgerMapper.formatMonthTabName(YearMonth.of(2027, 1)))
    }

    @Test
    fun parseSheetDate_handlesIsoLegacySlashSerialAndBlankCarryForward() {
        val nov = YearMonth.of(2026, 11)
        val first = SheetLedgerMapper.parseSheetDate("1/11", nov, previousDate = null)
        assertEquals(LocalDate.of(2026, 11, 1), first)

        val carried = SheetLedgerMapper.parseSheetDate("", nov, previousDate = first)
        assertEquals(LocalDate.of(2026, 11, 1), carried)

        val iso = SheetLedgerMapper.parseSheetDate("2026-11-13", nov, previousDate = first)
        assertEquals(LocalDate.of(2026, 11, 13), iso)

        // Serial 46339 in Google Sheets = 2026-11-13
        val serial = SheetLedgerMapper.parseSheetDate("46339", nov, previousDate = first)
        assertEquals(LocalDate.of(2026, 11, 13), serial)
    }

    @Test
    fun parseAndFormatAmounts_useExactSenWithoutCurrencyConversion() {
        assertEquals(1850L, SheetLedgerMapper.parseAmountMinor("18.50"))
        assertEquals(123456L, SheetLedgerMapper.parseAmountMinor("$1,234.56"))
        assertEquals(50000L, SheetLedgerMapper.parseAmountMinor("RM 500"))
        assertNull(SheetLedgerMapper.parseAmountMinor("0"))
        assertEquals("18.50", SheetLedgerMapper.formatMajorAmount(1850L))
    }

    @Test
    fun parseSheetRows_generatesDeterministicLegacyIdsAndPreservesAppUuids() {
        val month = YearMonth.of(2026, 11)
        val raw = listOf(
            listOf("1/11", "Expense", "Groceries & Food", "Lunch", "14.50", "note"),
            listOf("", "Expense", "Transportation", "MRT", "3.20"),
            listOf(
                "2026-11-13",
                "Income",
                "Salary",
                "Bonus",
                "500.00",
                "",
                "uuid-1234",
                "2026-11-13T10:00:00Z",
                "Racks"
            )
        )

        val parsedA = SheetLedgerMapper.parseSheetRows(month, raw)
        val parsedB = SheetLedgerMapper.parseSheetRows(month, raw)

        assertEquals(3, parsedA.size)
        assertFalse(parsedA[0].isAppCreated)
        assertTrue(parsedA[0].id.startsWith("legacy-2026-11-10-"))
        assertEquals(parsedA[0].id, parsedB[0].id)

        assertEquals(LocalDate.of(2026, 11, 1), parsedA[1].transactionDate)
        assertTrue(parsedA[2].isAppCreated)
        assertEquals("uuid-1234", parsedA[2].id)
        assertEquals(TransactionType.INCOME, parsedA[2].type)
        assertEquals(50_000L, parsedA[2].amountMinor)
    }

    @Test
    fun toSheetRowValuesAndFindNextAvailableRowNumber_skipExistingLedgerRows() {
        val raw = listOf(
            listOf("1/11", "Expense", "Groceries & Food", "Coffee", "12.00"),
            listOf("", "", "", "", ""),
            listOf("2/11", "Income", "Salary", "", "3000.00")
        )

        assertEquals(11, SheetLedgerMapper.findNextAvailableRowNumber(raw, reservedRows = emptySet()))
        assertEquals(13, SheetLedgerMapper.findNextAvailableRowNumber(raw, reservedRows = setOf(11)))

        val entity = TransactionEntity(
            id = "tx-abc",
            type = TransactionType.EXPENSE,
            amountMinor = 2590L,
            currency = "MYR",
            category = "For Weiwei",
            remark = "Flowers",
            transactionDate = LocalDate.of(2026, 11, 13),
            createdAt = Instant.parse("2026-11-13T08:00:00Z"),
            updatedAt = Instant.parse("2026-11-13T08:00:00Z"),
            syncStatus = SyncStatus.PENDING
        )

        assertEquals(
            listOf(
                "2026-11-13",
                "Expense",
                "For Weiwei",
                "Flowers",
                "25.90",
                "keep note",
                "tx-abc",
                "2026-11-13T08:00:00Z",
                "Racks"
            ),
            SheetLedgerMapper.toSheetRowValues(entity, existingNotes = "keep note")
        )
    }
}
