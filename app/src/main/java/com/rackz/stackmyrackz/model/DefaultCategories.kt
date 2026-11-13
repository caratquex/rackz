package com.rackz.stackmyrackz.model

/** Default transaction categories until live sheet audit populates dynamic categories. */
object DefaultCategories {
    val expenseCategories: List<String> = listOf(
        "Groceries & Food",
        "Transportation",
        "Utilities & Bills",
        "For Weiwei",
        "Dining Out",
        "Shopping",
        "Entertainment",
        "Health & Medical",
        "Personal Care",
        "Subscriptions",
        "Miscellaneous"
    )

    val incomeCategories: List<String> = listOf(
        "Salary",
        "Family Allowance",
        "Bonus",
        "Freelance",
        "Investment",
        "Other"
    )

    fun forType(type: TransactionType): List<String> = when (type) {
        TransactionType.EXPENSE -> expenseCategories
        TransactionType.INCOME -> incomeCategories
    }
}
