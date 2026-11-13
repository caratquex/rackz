package com.rackz.stackmyrackz.model

/** Transaction direction supported by the ledger. */
enum class TransactionType(val label: String) {
    EXPENSE("Expense"),
    INCOME("Income")
}
