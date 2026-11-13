package com.rackz.stackmyrackz.data.sync

import com.rackz.stackmyrackz.data.local.TransactionEntity
import com.rackz.stackmyrackz.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

/** Parsed representation of a ledger row from columns B:J (row 10 onward) of a monthly tab. */
data class SheetLedgerRow(
    val rowNumber: Int,
    val id: String,
    val isAppCreated: Boolean,
    val type: TransactionType,
    val amountMinor: Long,
    val category: String,
    val remark: String?,
    val notes: String?,
    val transactionDate: LocalDate,
    val updatedAt: Instant?,
    val revision: String
)

/** Pure Kotlin mapper between Room [TransactionEntity] and Google Sheets monthly ledger rows (B:J). */
object SheetLedgerMapper {

    const val FIRST_LEDGER_ROW = 10
    const val MAX_LEDGER_ROW = 500
    const val SOURCE_RACKS = "Racks"

    private const val COL_DATE = 0
    private const val COL_TYPE = 1
    private const val COL_CATEGORY = 2
    private const val COL_REMARK = 3
    private const val COL_AMOUNT = 4
    private const val COL_NOTES = 5
    private const val COL_ID = 6
    private const val COL_UPDATED_AT = 7

    private val SHEETS_SERIAL_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)
    private val MONTH_TAB_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    private val FULL_MONTH_TAB_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    private val FULL_SLASH_DATE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ENGLISH)
    private val SHORT_YEAR_SLASH_FORMATTER: DateTimeFormatter =
        DateTimeFormatterBuilder()
            .appendPattern("d/M/")
            .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
            .toFormatter(Locale.ENGLISH)
    private val TEXT_DATE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    private val MONTH_PREFIX_REGEX =
        Regex("^([A-Za-z]+)\\s+(\\d{4})", RegexOption.IGNORE_CASE)
    private val DAY_MONTH_SLASH_REGEX = Regex("^(\\d{1,2})/(\\d{1,2})$")

    fun formatMonthTabName(month: YearMonth): String = month.format(MONTH_TAB_FORMATTER)

    fun resolveExistingTabName(month: YearMonth, existingTabs: List<String>): String? {
        val standardName = formatMonthTabName(month)
        existingTabs.firstOrNull { it.equals(standardName, ignoreCase = true) }?.let { return it }

        if (month == YearMonth.of(2026, 9)) {
            existingTabs.firstOrNull {
                it.equals("Sep 2026(Partial)", ignoreCase = true)
            }?.let { return it }
        }

        existingTabs.firstOrNull { tab ->
            parseMonthFromTabTitle(tab) == month
        }?.let { return it }

        return null
    }

    fun parseMonthFromTabTitle(title: String): YearMonth? {
        val trimmed = title.trim()
        runCatching { return YearMonth.parse(trimmed, MONTH_TAB_FORMATTER) }
        runCatching { return YearMonth.parse(trimmed, FULL_MONTH_TAB_FORMATTER) }

        val match = MONTH_PREFIX_REGEX.find(trimmed) ?: return null
        val monthWord = match.groupValues[1]
        val year = match.groupValues[2].toIntOrNull() ?: return null
        val parsedMonth = parseEnglishMonthName(monthWord) ?: return null
        return YearMonth.of(year, parsedMonth)
    }

    fun parseSheetDate(
        raw: String,
        fallbackMonth: YearMonth,
        previousDate: LocalDate?
    ): LocalDate {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return previousDate ?: fallbackMonth.atDay(1)
        }

        runCatching { return LocalDate.parse(trimmed) }

        val serialDouble = trimmed.toDoubleOrNull()
        if (serialDouble != null && serialDouble >= 20_000.0 && serialDouble <= 100_000.0) {
            return SHEETS_SERIAL_EPOCH.plusDays(serialDouble.toLong())
        }

        val dayMonthMatch = DAY_MONTH_SLASH_REGEX.matchEntire(trimmed)
        if (dayMonthMatch != null) {
            val day = dayMonthMatch.groupValues[1].toIntOrNull()
            val monthValue = dayMonthMatch.groupValues[2].toIntOrNull()
            if (day != null && monthValue != null && monthValue in 1..12) {
                val month = Month.of(monthValue)
                val maxDay = month.length(fallbackMonth.isLeapYear)
                return LocalDate.of(fallbackMonth.year, month, day.coerceIn(1, maxDay))
            }
        }

        runCatching { return LocalDate.parse(trimmed, FULL_SLASH_DATE_FORMATTER) }
        runCatching { return LocalDate.parse(trimmed, SHORT_YEAR_SLASH_FORMATTER) }
        runCatching { return LocalDate.parse(trimmed, TEXT_DATE_FORMATTER) }

        return previousDate ?: fallbackMonth.atDay(1)
    }

    fun parseAmountMinor(raw: String): Long? {
        val cleaned = raw
            .replace("RM", "", ignoreCase = true)
            .replace("MYR", "", ignoreCase = true)
            .replace("$", "")
            .replace(",", "")
            .trim()
        if (cleaned.isEmpty()) return null

        val decimal = runCatching { BigDecimal(cleaned) }.getOrNull() ?: return null
        if (decimal <= BigDecimal.ZERO) return null
        return decimal.movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    fun formatMajorAmount(amountMinor: Long): String =
        BigDecimal.valueOf(amountMinor)
            .movePointLeft(2)
            .setScale(2, RoundingMode.HALF_UP)
            .toPlainString()

    fun parseTransactionType(raw: String): TransactionType? = when (raw.trim().lowercase(Locale.ENGLISH)) {
        "expense", "expenses" -> TransactionType.EXPENSE
        "income" -> TransactionType.INCOME
        else -> null
    }

    fun parseSheetRows(
        month: YearMonth,
        rawRows: List<List<String>>,
        startRowNumber: Int = FIRST_LEDGER_ROW
    ): List<SheetLedgerRow> {
        val parsed = mutableListOf<SheetLedgerRow>()
        var previousDate: LocalDate? = null

        rawRows.forEachIndexed { offset, row ->
            val rowNumber = startRowNumber + offset
            val rawDate = row.getOrElse(COL_DATE) { "" }.trim()
            val rawType = row.getOrElse(COL_TYPE) { "" }.trim()
            val category = row.getOrElse(COL_CATEGORY) { "" }.trim()
            val remark = row.getOrElse(COL_REMARK) { "" }.trim().takeIf { it.isNotEmpty() }
            val rawAmount = row.getOrElse(COL_AMOUNT) { "" }.trim()
            val notes = row.getOrElse(COL_NOTES) { "" }.trim().takeIf { it.isNotEmpty() }
            val rawId = row.getOrElse(COL_ID) { "" }.trim()
            val rawUpdatedAt = row.getOrElse(COL_UPDATED_AT) { "" }.trim()

            val type = parseTransactionType(rawType) ?: return@forEachIndexed
            val amountMinor = parseAmountMinor(rawAmount) ?: return@forEachIndexed
            if (category.isEmpty()) return@forEachIndexed

            val transactionDate = parseSheetDate(rawDate, month, previousDate)
            previousDate = transactionDate

            val isAppCreated = rawId.isNotEmpty()
            val id = if (isAppCreated) {
                rawId
            } else {
                buildDeterministicLegacyId(
                    month = month,
                    rowNumber = rowNumber,
                    transactionDate = transactionDate,
                    type = type,
                    amountMinor = amountMinor,
                    category = category,
                    remark = remark
                )
            }

            val updatedAt = rawUpdatedAt
                .takeIf { it.isNotEmpty() }
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }

            val revision = computeRevision(
                transactionDate = transactionDate,
                type = type,
                amountMinor = amountMinor,
                category = category,
                remark = remark
            )

            parsed += SheetLedgerRow(
                rowNumber = rowNumber,
                id = id,
                isAppCreated = isAppCreated,
                type = type,
                amountMinor = amountMinor,
                category = category,
                remark = remark,
                notes = notes,
                transactionDate = transactionDate,
                updatedAt = updatedAt,
                revision = revision
            )
        }

        return parsed
    }

    fun toSheetRowValues(
        entity: TransactionEntity,
        existingNotes: String? = null
    ): List<String> = listOf(
        entity.transactionDate.toString(),
        entity.type.label,
        entity.category,
        entity.remark.orEmpty(),
        formatMajorAmount(entity.amountMinor),
        existingNotes.orEmpty(),
        entity.id,
        entity.updatedAt.toString(),
        SOURCE_RACKS
    )

    fun computeRevision(entity: TransactionEntity): String = computeRevision(
        transactionDate = entity.transactionDate,
        type = entity.type,
        amountMinor = entity.amountMinor,
        category = entity.category,
        remark = entity.remark
    )

    fun computeRevision(
        transactionDate: LocalDate,
        type: TransactionType,
        amountMinor: Long,
        category: String,
        remark: String?
    ): String {
        val payload = listOf(
            transactionDate.toString(),
            type.name,
            amountMinor.toString(),
            category.trim(),
            remark?.trim().orEmpty()
        ).joinToString("|")
        return sha256Hex(payload).take(16)
    }

    fun findNextAvailableRowNumber(
        rawRows: List<List<String>>,
        reservedRows: Set<Int>,
        startRowNumber: Int = FIRST_LEDGER_ROW
    ): Int {
        rawRows.forEachIndexed { offset, row ->
            val rowNumber = startRowNumber + offset
            if (rowNumber in reservedRows) return@forEachIndexed
            val ledgerCellsEmpty = (0..5).all { col -> row.getOrElse(col) { "" }.isBlank() }
            val idEmpty = row.getOrElse(COL_ID) { "" }.isBlank()
            if (ledgerCellsEmpty && idEmpty) {
                return rowNumber
            }
        }

        var candidate = startRowNumber + rawRows.size
        while (candidate in reservedRows) {
            candidate++
        }
        return candidate
    }

    private fun buildDeterministicLegacyId(
        month: YearMonth,
        rowNumber: Int,
        transactionDate: LocalDate,
        type: TransactionType,
        amountMinor: Long,
        category: String,
        remark: String?
    ): String {
        val fingerprint = sha256Hex(
            "$month|$rowNumber|$transactionDate|${type.name}|$amountMinor|$category|${remark.orEmpty()}"
        ).take(10)
        return "legacy-$month-$rowNumber-$fingerprint"
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun parseEnglishMonthName(token: String): Month? {
        val normalized = token.trim().lowercase(Locale.ENGLISH)
        return when {
            normalized.startsWith("jan") -> Month.JANUARY
            normalized.startsWith("feb") -> Month.FEBRUARY
            normalized.startsWith("mar") -> Month.MARCH
            normalized.startsWith("apr") -> Month.APRIL
            normalized == "may" -> Month.MAY
            normalized.startsWith("jun") -> Month.JUNE
            normalized.startsWith("jul") -> Month.JULY
            normalized.startsWith("aug") -> Month.AUGUST
            normalized.startsWith("sep") -> Month.SEPTEMBER
            normalized.startsWith("oct") -> Month.OCTOBER
            normalized.startsWith("nov") -> Month.NOVEMBER
            normalized.startsWith("dec") -> Month.DECEMBER
            else -> null
        }
    }
}
