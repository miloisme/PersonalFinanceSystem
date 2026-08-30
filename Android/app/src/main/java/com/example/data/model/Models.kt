package com.example.data.model

data class Account(
    val id: Long? = null,
    val name: String = "",
    val currency: String = "CNY",
    val balance: Double = 0.0,
    val accountType: String = "cash", // "cash", "invest", "fixed"
    val notes: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
)

data class Category(
    val id: Long? = null,
    val name: String = "",
    val type: String = "expense", // "expense", "income"
    val icon: String = "",
    val color: String = "#4CAF50"
)

data class TransactionItem(
    val id: Long? = null,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val amount: Double = 0.0,
    val type: String = "expense", // "expense", "income"
    val description: String = "",
    val date: String = "", // "YYYY-MM-DD"
    val currency: String = "",
    val exchangeRate: Double = 1.0,
    val createdAt: String = "",
    // Joined display fields
    val categoryName: String? = null,
    val categoryIcon: String? = null,
    val categoryColor: String? = null,
    val accountName: String? = null,
    val accountCurrency: String? = null,
    val baseAmount: Double = 0.0
)

data class DebtItem(
    val id: Long? = null,
    val name: String = "",
    val type: String = "loan",
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val interestRate: Double = 0.0,
    val currency: String = "CNY",
    val startDate: String = "",
    val dueDate: String = "",
    val completed: Boolean = false,
    val debtor: String = "me", // "me" (I owe = debt) or "other" (owed to me = credit/receivable)
    val notes: String = "",
    val createdAt: String = ""
)

data class EarmarkedFund(
    val id: Long? = null,
    val name: String = "",
    val amount: Double = 0.0,
    val categories: List<Long> = emptyList()
)

data class BudgetForecastItem(
    val month: String = "", // "YYYY-MM"
    val assets: Double? = null,
    val liabilities: Double? = null,
    val income: Double? = null,
    val expense: Double? = null
)

data class NoteItem(
    val id: Long? = null,
    val content: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
)

data class NetWorthHistoryItem(
    val date: String = "", // "YYYY-MM-DD"
    val assets: Double = 0.0,
    val liabilities: Double = 0.0
)

data class FilterCondition(
    val field: String, // "Category", "Account", "Type", "Description", "Amount", "Date"
    val op: String = "=", // "=", "!=", "contains", ">", "<"
    val value: String = ""
)
