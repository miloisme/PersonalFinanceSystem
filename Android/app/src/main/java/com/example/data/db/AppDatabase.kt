package com.example.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.data.model.*
import com.example.sync.CurrencyConverter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AppDatabase(private val context: Context, val dbPath: String = "finance.db") {
    @Volatile
    private var dbHelper: DatabaseHelper? = null
    var cryptoKey: ByteArray? = null

    private val db: SQLiteDatabase
        @Synchronized
        get() {
            var helper = dbHelper
            if (helper == null) {
                helper = DatabaseHelper(context, dbPath)
                dbHelper = helper
            }
            val database = helper.writableDatabase
            helper.ensureSchema(database)
            return database
        }

    @Synchronized
    fun close() {
        try {
            dbHelper?.close()
        } catch (e: Exception) {
            // ignore
        }
        dbHelper = null
    }

    @Synchronized
    fun reopen() {
        close()
        val helper = DatabaseHelper(context, dbPath)
        dbHelper = helper
        val database = helper.writableDatabase
        helper.ensureSchema(database)
    }

    // --- Settings ---
    fun getSetting(key: String, default: String = ""): String {
        return try {
            val cursor = db.rawQuery("SELECT value FROM settings WHERE key=?", arrayOf(key))
            cursor.use {
                if (it.moveToFirst()) it.getString(0) ?: default else default
            }
        } catch (e: Exception) {
            default
        }
    }

    fun setSetting(key: String, value: String) {
        try {
            val cv = ContentValues().apply {
                put("key", key)
                put("value", value)
            }
            db.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun getBaseCurrency(): String = getSetting("base_currency", "HKD")

    fun setBaseCurrency(currency: String) {
        setSetting("base_currency", currency)
    }

    fun getCurrencies(): List<String> {
        val raw = getSetting("currencies", "")
        if (raw.isNotBlank()) {
            val list = raw.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
            if (list.isNotEmpty()) return list
        }
        val defaults = CurrencyConverter.supportedCurrencies
        setSetting("currencies", defaults.joinToString(","))
        return defaults
    }

    fun addCurrency(code: String) {
        val cur = code.trim().uppercase()
        if (cur.isEmpty()) return
        val current = getCurrencies().toMutableList()
        if (!current.contains(cur)) {
            current.add(cur)
            setSetting("currencies", current.joinToString(","))
        }
    }

    fun removeCurrency(code: String) {
        val cur = code.trim().uppercase()
        val current = getCurrencies().filter { it != cur }
        setSetting("currencies", current.joinToString(","))
    }

    // --- Accounts ---
    fun getAccounts(): List<Account> {
        val list = mutableListOf<Account>()
        val cursor = db.rawQuery("SELECT * FROM accounts ORDER BY created_at DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    Account(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        currency = it.getString(it.getColumnIndexOrThrow("currency")),
                        balance = it.getDouble(it.getColumnIndexOrThrow("balance")),
                        accountType = it.getString(it.getColumnIndexOrThrow("account_type")),
                        notes = it.getString(it.getColumnIndexOrThrow("notes")) ?: "",
                        createdAt = it.getString(it.getColumnIndexOrThrow("created_at")) ?: "",
                        updatedAt = it.getString(it.getColumnIndexOrThrow("updated_at")) ?: ""
                    )
                )
            }
        }
        return list
    }

    fun addAccount(account: Account): Long {
        val cv = ContentValues().apply {
            put("name", account.name)
            put("currency", account.currency)
            put("balance", account.balance)
            put("account_type", account.accountType)
            put("notes", account.notes)
        }
        return db.insert("accounts", null, cv)
    }

    fun updateAccount(account: Account) {
        if (account.id == null) return
        val cv = ContentValues().apply {
            put("name", account.name)
            put("currency", account.currency)
            put("balance", account.balance)
            put("account_type", account.accountType)
            put("notes", account.notes)
            put("updated_at", currentTimestamp())
        }
        db.update("accounts", cv, "id=?", arrayOf(account.id.toString()))
    }

    fun deleteAccount(accountId: Long) {
        db.delete("accounts", "id=?", arrayOf(accountId.toString()))
    }

    fun transferBetweenAccounts(fromId: Long, toId: Long, amount: Double) {
        db.beginTransaction()
        try {
            val cursor = db.rawQuery("SELECT id, balance, currency FROM accounts WHERE id IN (?, ?)", arrayOf(fromId.toString(), toId.toString()))
            val map = mutableMapOf<Long, Pair<Double, String>>()
            cursor.use {
                while (it.moveToNext()) {
                    map[it.getLong(0)] = Pair(it.getDouble(1), it.getString(2))
                }
            }
            val fromAcc = map[fromId] ?: throw IllegalArgumentException("Source account not found")
            val toAcc = map[toId] ?: throw IllegalArgumentException("Target account not found")
            if (fromAcc.second != toAcc.second) {
                throw IllegalArgumentException("Transfer currency mismatch: ${fromAcc.second} vs ${toAcc.second}")
            }
            if (fromAcc.first < amount) {
                throw IllegalArgumentException("Insufficient funds in source account")
            }

            db.execSQL("UPDATE accounts SET balance = balance - ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", arrayOf(amount, fromId))
            db.execSQL("UPDATE accounts SET balance = balance + ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", arrayOf(amount, toId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // --- Categories ---
    fun getCategories(typeFilter: String? = null): List<Category> {
        val list = mutableListOf<Category>()
        val sql = if (typeFilter != null) "SELECT * FROM categories WHERE type=? ORDER BY id" else "SELECT * FROM categories ORDER BY type, id"
        val args = if (typeFilter != null) arrayOf(typeFilter) else null
        val cursor = db.rawQuery(sql, args)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    Category(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        type = it.getString(it.getColumnIndexOrThrow("type")),
                        icon = it.getString(it.getColumnIndexOrThrow("icon")) ?: "",
                        color = it.getString(it.getColumnIndexOrThrow("color")) ?: "#4CAF50"
                    )
                )
            }
        }
        return list
    }

    fun getCategoryById(id: Long): Category? {
        val cursor = db.rawQuery("SELECT * FROM categories WHERE id=?", arrayOf(id.toString()))
        return cursor.use {
            if (it.moveToFirst()) {
                Category(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    name = it.getString(it.getColumnIndexOrThrow("name")),
                    type = it.getString(it.getColumnIndexOrThrow("type")),
                    icon = it.getString(it.getColumnIndexOrThrow("icon")) ?: "",
                    color = it.getString(it.getColumnIndexOrThrow("color")) ?: "#4CAF50"
                )
            } else null
        }
    }

    fun addCategory(category: Category): Long {
        val cv = ContentValues().apply {
            put("name", category.name)
            put("type", category.type)
            put("icon", category.icon)
            put("color", category.color)
        }
        return db.insert("categories", null, cv)
    }

    fun updateCategory(category: Category) {
        if (category.id == null) return
        val cv = ContentValues().apply {
            put("name", category.name)
            put("type", category.type)
            put("icon", category.icon)
            put("color", category.color)
        }
        db.update("categories", cv, "id=?", arrayOf(category.id.toString()))
    }

    fun deleteCategory(categoryId: Long) {
        db.delete("categories", "id=?", arrayOf(categoryId.toString()))
    }

    // --- Transactions ---
    fun getTransactions(accountId: Long? = null, startDate: String? = null, endDate: String? = null): List<TransactionItem> {
        val list = mutableListOf<TransactionItem>()
        val sb = StringBuilder(
            """
            SELECT t.*, c.name as category_name, c.icon as category_icon, c.color as category_color,
                   a.name as account_name, a.currency as account_currency
            FROM transactions t
            LEFT JOIN categories c ON t.category_id = c.id
            LEFT JOIN accounts a ON t.account_id = a.id
            WHERE 1=1
            """.trimIndent()
        )
        val params = mutableListOf<String>()
        if (accountId != null) {
            sb.append(" AND t.account_id=?")
            params.add(accountId.toString())
        }
        if (!startDate.isNullOrEmpty()) {
            sb.append(" AND t.date>=?")
            params.add(startDate)
        }
        if (!endDate.isNullOrEmpty()) {
            sb.append(" AND t.date<=?")
            params.add(endDate)
        }
        sb.append(" ORDER BY t.date DESC, t.created_at DESC")

        val base = getBaseCurrency()
        val cursor = db.rawQuery(sb.toString(), if (params.isEmpty()) null else params.toTypedArray())
        cursor.use {
            while (it.moveToNext()) {
                val accCur = it.getString(it.getColumnIndexOrThrow("account_currency")) ?: base
                var cur = it.getString(it.getColumnIndexOrThrow("currency")) ?: ""
                if (cur.isEmpty()) cur = accCur
                var rate = it.getDouble(it.getColumnIndexOrThrow("exchange_rate"))
                if (rate <= 0.0) {
                    rate = CurrencyConverter.getRate(cur, base)
                }
                val amt = it.getDouble(it.getColumnIndexOrThrow("amount"))
                val baseAmt = amt * rate

                list.add(
                    TransactionItem(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        accountId = if (it.isNull(it.getColumnIndexOrThrow("account_id"))) null else it.getLong(it.getColumnIndexOrThrow("account_id")),
                        categoryId = if (it.isNull(it.getColumnIndexOrThrow("category_id"))) null else it.getLong(it.getColumnIndexOrThrow("category_id")),
                        amount = amt,
                        type = it.getString(it.getColumnIndexOrThrow("type")),
                        description = it.getString(it.getColumnIndexOrThrow("description")) ?: "",
                        date = it.getString(it.getColumnIndexOrThrow("date")) ?: "",
                        currency = cur,
                        exchangeRate = rate,
                        createdAt = it.getString(it.getColumnIndexOrThrow("created_at")) ?: "",
                        categoryName = it.getString(it.getColumnIndexOrThrow("category_name")),
                        categoryIcon = it.getString(it.getColumnIndexOrThrow("category_icon")),
                        categoryColor = it.getString(it.getColumnIndexOrThrow("category_color")),
                        accountName = it.getString(it.getColumnIndexOrThrow("account_name")),
                        accountCurrency = accCur,
                        baseAmount = baseAmt
                    )
                )
            }
        }
        return list
    }

    fun addTransaction(transaction: TransactionItem): Long {
        db.beginTransaction()
        try {
            var accCur = getBaseCurrency()
            if (transaction.accountId != null && transaction.accountId > 0) {
                val curCursor = db.rawQuery("SELECT currency FROM accounts WHERE id=?", arrayOf(transaction.accountId.toString()))
                curCursor.use {
                    if (it.moveToFirst()) accCur = it.getString(0)
                }
            }
            val cur = if (transaction.currency.isNotEmpty()) transaction.currency else accCur
            val rate = if (transaction.exchangeRate > 0) transaction.exchangeRate else CurrencyConverter.getRate(cur, getBaseCurrency())
            val delta = CurrencyConverter.convert(transaction.amount, cur, accCur)

            val cv = ContentValues().apply {
                if (transaction.accountId != null && transaction.accountId > 0) {
                    put("account_id", transaction.accountId)
                }
                if (transaction.categoryId != null && transaction.categoryId > 0) {
                    put("category_id", transaction.categoryId)
                }
                put("amount", transaction.amount)
                put("type", transaction.type)
                put("description", transaction.description)
                put("date", transaction.date)
                put("currency", cur)
                put("exchange_rate", rate)
            }
            val newId = db.insert("transactions", null, cv)

            if (transaction.accountId != null && transaction.accountId > 0) {
                if (transaction.type == "income") {
                    db.execSQL("UPDATE accounts SET balance = balance + ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", arrayOf(delta, transaction.accountId))
                } else {
                    db.execSQL("UPDATE accounts SET balance = balance - ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", arrayOf(delta, transaction.accountId))
                }
            }

            db.setTransactionSuccessful()
            return newId
        } finally {
            db.endTransaction()
        }
    }

    fun updateTransaction(transaction: TransactionItem) {
        if (transaction.id == null) return
        val cur = if (transaction.currency.isNotEmpty()) transaction.currency else getBaseCurrency()
        val rate = if (transaction.exchangeRate > 0) transaction.exchangeRate else CurrencyConverter.getRate(cur, getBaseCurrency())

        val cv = ContentValues().apply {
            if (transaction.accountId != null && transaction.accountId > 0) {
                put("account_id", transaction.accountId)
            } else {
                putNull("account_id")
            }
            if (transaction.categoryId != null && transaction.categoryId > 0) {
                put("category_id", transaction.categoryId)
            } else {
                putNull("category_id")
            }
            put("amount", transaction.amount)
            put("type", transaction.type)
            put("description", transaction.description)
            put("date", transaction.date)
            put("currency", cur)
            put("exchange_rate", rate)
        }
        db.update("transactions", cv, "id=?", arrayOf(transaction.id.toString()))
    }

    fun deleteTransaction(transactionId: Long) {
        db.beginTransaction()
        try {
            val cursor = db.rawQuery("SELECT account_id, amount, type, currency FROM transactions WHERE id=?", arrayOf(transactionId.toString()))
            cursor.use {
                if (it.moveToFirst()) {
                    val accId = if (it.isNull(0)) null else it.getLong(0)
                    val amt = it.getDouble(1)
                    val type = it.getString(2)
                    val cur = it.getString(3) ?: getBaseCurrency()

                    if (accId != null && accId > 0) {
                        var accCur = getBaseCurrency()
                        val accCurCursor = db.rawQuery("SELECT currency FROM accounts WHERE id=?", arrayOf(accId.toString()))
                        accCurCursor.use { ac ->
                            if (ac.moveToFirst()) accCur = ac.getString(0)
                        }
                        val delta = CurrencyConverter.convert(amt, cur, accCur)
                        if (type == "income") {
                            db.execSQL("UPDATE accounts SET balance = balance - ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", arrayOf(delta, accId))
                        } else {
                            db.execSQL("UPDATE accounts SET balance = balance + ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", arrayOf(delta, accId))
                        }
                    }
                }
            }
            db.delete("transactions", "id=?", arrayOf(transactionId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getTransactionYears(): List<Int> {
        val years = mutableSetOf<Int>()
        val cursor = db.rawQuery("SELECT DISTINCT substr(date, 1, 4) FROM transactions WHERE date IS NOT NULL AND date != ''", null)
        cursor.use {
            while (it.moveToNext()) {
                val str = it.getString(0)
                str?.toIntOrNull()?.let { y -> years.add(y) }
            }
        }
        if (years.isEmpty()) {
            years.add(Calendar.getInstance().get(Calendar.YEAR))
        }
        return years.sorted()
    }

    fun getMonthlySummary(year: Int, month: Int): Triple<Double, Double, Double> {
        val pattern = "%04d-%02d%%".format(year, month)
        var income = 0.0
        var expense = 0.0
        val cursorInc = db.rawQuery("SELECT COALESCE(SUM(amount * COALESCE(exchange_rate, 1.0)), 0) FROM transactions WHERE type='income' AND date LIKE ?", arrayOf(pattern))
        cursorInc.use { if (it.moveToFirst()) income = it.getDouble(0) }
        val cursorExp = db.rawQuery("SELECT COALESCE(SUM(amount * COALESCE(exchange_rate, 1.0)), 0) FROM transactions WHERE type='expense' AND date LIKE ?", arrayOf(pattern))
        cursorExp.use { if (it.moveToFirst()) expense = it.getDouble(0) }
        return Triple(income, expense, income - expense)
    }

    fun getMonthTransactionCount(year: Int, month: Int): Int {
        val pattern = "%04d-%02d%%".format(year, month)
        val cursor = db.rawQuery("SELECT COUNT(*) FROM transactions WHERE date LIKE ?", arrayOf(pattern))
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    // --- Debts & Receivables ---
    fun getDebts(): List<DebtItem> {
        val list = mutableListOf<DebtItem>()
        val cursor = db.rawQuery("SELECT * FROM debts ORDER BY completed ASC, due_date ASC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    DebtItem(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        type = it.getString(it.getColumnIndexOrThrow("type")) ?: "loan",
                        totalAmount = it.getDouble(it.getColumnIndexOrThrow("total_amount")),
                        paidAmount = it.getDouble(it.getColumnIndexOrThrow("paid_amount")),
                        interestRate = it.getDouble(it.getColumnIndexOrThrow("interest_rate")),
                        currency = it.getString(it.getColumnIndexOrThrow("currency")) ?: "CNY",
                        startDate = it.getString(it.getColumnIndexOrThrow("start_date")) ?: "",
                        dueDate = it.getString(it.getColumnIndexOrThrow("due_date")) ?: "",
                        completed = it.getInt(it.getColumnIndexOrThrow("completed")) == 1,
                        debtor = it.getString(it.getColumnIndexOrThrow("debtor")) ?: "me",
                        notes = it.getString(it.getColumnIndexOrThrow("note")) ?: "",
                        createdAt = it.getString(it.getColumnIndexOrThrow("created_at")) ?: ""
                    )
                )
            }
        }
        return list
    }

    fun addDebt(debt: DebtItem): Long {
        val cv = ContentValues().apply {
            put("name", debt.name)
            put("type", debt.type)
            put("total_amount", debt.totalAmount)
            put("paid_amount", debt.paidAmount)
            put("interest_rate", debt.interestRate)
            put("currency", debt.currency)
            put("start_date", debt.startDate)
            put("due_date", debt.dueDate)
            put("completed", if (debt.completed) 1 else 0)
            put("debtor", debt.debtor)
            put("note", debt.notes)
        }
        return db.insert("debts", null, cv)
    }

    fun updateDebt(debt: DebtItem) {
        if (debt.id == null) return
        val cv = ContentValues().apply {
            put("name", debt.name)
            put("type", debt.type)
            put("total_amount", debt.totalAmount)
            put("paid_amount", debt.paidAmount)
            put("interest_rate", debt.interestRate)
            put("currency", debt.currency)
            put("start_date", debt.startDate)
            put("due_date", debt.dueDate)
            put("completed", if (debt.completed) 1 else 0)
            put("debtor", debt.debtor)
            put("note", debt.notes)
        }
        db.update("debts", cv, "id=?", arrayOf(debt.id.toString()))
    }

    fun deleteDebt(debtId: Long) {
        db.delete("debts", "id=?", arrayOf(debtId.toString()))
    }

    // --- Net Worth History ---
    fun getNetWorthHistory(): List<NetWorthHistoryItem> {
        val list = mutableListOf<NetWorthHistoryItem>()
        val cursor = db.rawQuery("SELECT date, assets, liabilities FROM net_worth_history ORDER BY date", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    NetWorthHistoryItem(
                        date = it.getString(0),
                        assets = it.getDouble(1),
                        liabilities = it.getDouble(2)
                    )
                )
            }
        }
        return list
    }

    fun upsertNetWorth(dateStr: String, assets: Double, liabilities: Double) {
        val cv = ContentValues().apply {
            put("date", dateStr)
            put("assets", assets)
            put("liabilities", liabilities)
        }
        db.insertWithOnConflict("net_worth_history", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun upsertNetWorthIfMissing(dateStr: String, assets: Double, liabilities: Double) {
        val cursor = db.rawQuery("SELECT 1 FROM net_worth_history WHERE date=?", arrayOf(dateStr))
        val exists = cursor.use { it.moveToFirst() }
        if (!exists) {
            upsertNetWorth(dateStr, assets, liabilities)
        }
    }

    fun getLatestNetWorthBefore(dateStr: String): NetWorthHistoryItem? {
        val cursor = db.rawQuery("SELECT date, assets, liabilities FROM net_worth_history WHERE date < ? ORDER BY date DESC LIMIT 1", arrayOf(dateStr))
        return cursor.use {
            if (it.moveToFirst()) {
                NetWorthHistoryItem(
                    date = it.getString(0),
                    assets = it.getDouble(1),
                    liabilities = it.getDouble(2)
                )
            } else null
        }
    }

    // --- Budget & Earmarked Funds ---
    fun getBudgetAccountIds(): Set<Long>? {
        val raw = getSetting("budget_account_ids", "")
        if (raw.isEmpty()) return null
        return try {
            raw.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun setBudgetAccountIds(ids: Set<Long>) {
        setSetting("budget_account_ids", ids.joinToString(","))
    }

    fun getFunds(): List<EarmarkedFund> {
        val list = mutableListOf<EarmarkedFund>()
        val cursor = db.rawQuery("SELECT * FROM earmarked_funds ORDER BY id", null)
        cursor.use {
            while (it.moveToNext()) {
                val catStr = it.getString(it.getColumnIndexOrThrow("categories")) ?: ""
                val cats = catStr.split(",").mapNotNull { c -> c.trim().toLongOrNull() }
                list.add(
                    EarmarkedFund(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        amount = it.getDouble(it.getColumnIndexOrThrow("amount")),
                        categories = cats
                    )
                )
            }
        }
        return list
    }

    fun addFund(name: String, amount: Double, categories: List<Long>): Long {
        val cv = ContentValues().apply {
            put("name", name)
            put("amount", amount)
            put("categories", categories.joinToString(","))
        }
        return db.insert("earmarked_funds", null, cv)
    }

    fun updateFund(id: Long, name: String, amount: Double, categories: List<Long>) {
        val cv = ContentValues().apply {
            put("name", name)
            put("amount", amount)
            put("categories", categories.joinToString(","))
        }
        db.update("earmarked_funds", cv, "id=?", arrayOf(id.toString()))
    }

    fun deleteFund(id: Long) {
        db.delete("earmarked_funds", "id=?", arrayOf(id.toString()))
    }

    // --- Forecast ---
    fun getForecast(): Map<String, BudgetForecastItem> {
        val map = mutableMapOf<String, BudgetForecastItem>()
        val cursor = db.rawQuery("SELECT * FROM budget_forecast", null)
        cursor.use {
            while (it.moveToNext()) {
                val month = it.getString(it.getColumnIndexOrThrow("month"))
                val assets = if (it.isNull(it.getColumnIndexOrThrow("assets"))) null else it.getDouble(it.getColumnIndexOrThrow("assets"))
                val liabilities = if (it.isNull(it.getColumnIndexOrThrow("liabilities"))) null else it.getDouble(it.getColumnIndexOrThrow("liabilities"))
                val income = if (it.isNull(it.getColumnIndexOrThrow("income"))) null else it.getDouble(it.getColumnIndexOrThrow("income"))
                val expense = if (it.isNull(it.getColumnIndexOrThrow("expense"))) null else it.getDouble(it.getColumnIndexOrThrow("expense"))
                map[month] = BudgetForecastItem(month, assets, liabilities, income, expense)
            }
        }
        return map
    }

    fun setForecastCell(month: String, field: String, value: Double?) {
        val existing = getForecast()[month] ?: BudgetForecastItem(month)
        val cv = ContentValues().apply {
            put("month", month)
            val a = if (field == "assets") value else existing.assets
            val l = if (field == "liabilities") value else existing.liabilities
            val inc = if (field == "income") value else existing.income
            val exp = if (field == "expense") value else existing.expense
            if (a != null) put("assets", a) else putNull("assets")
            if (l != null) put("liabilities", l) else putNull("liabilities")
            if (inc != null) put("income", inc) else putNull("income")
            if (exp != null) put("expense", exp) else putNull("expense")
        }
        db.insertWithOnConflict("budget_forecast", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteForecast(month: String) {
        db.delete("budget_forecast", "month=?", arrayOf(month))
    }

    // --- Notes ---
    fun getNotes(): List<NoteItem> {
        val list = mutableListOf<NoteItem>()
        val cursor = db.rawQuery("SELECT * FROM notes ORDER BY updated_at DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    NoteItem(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        content = it.getString(it.getColumnIndexOrThrow("content")) ?: "",
                        createdAt = it.getString(it.getColumnIndexOrThrow("created_at")) ?: "",
                        updatedAt = it.getString(it.getColumnIndexOrThrow("updated_at")) ?: ""
                    )
                )
            }
        }
        return list
    }

    fun addNote(content: String): Long {
        val cv = ContentValues().apply {
            put("content", content)
        }
        return db.insert("notes", null, cv)
    }

    fun updateNote(id: Long, content: String) {
        val cv = ContentValues().apply {
            put("content", content)
            put("updated_at", currentTimestamp())
        }
        db.update("notes", cv, "id=?", arrayOf(id.toString()))
    }

    fun deleteNote(id: Long) {
        db.delete("notes", "id=?", arrayOf(id.toString()))
    }

    // --- SQL Console ---
    fun executeSql(sql: String): SqlResult {
        val trimmed = sql.trim()
        if (trimmed.isEmpty()) return SqlResult.Empty
        val lower = trimmed.lowercase()
        val isSelect = lower.startsWith("select") || lower.startsWith("pragma") || lower.startsWith("explain")
        return if (isSelect) {
            val cursor = db.rawQuery(trimmed, null)
            cursor.use {
                val cols = it.columnNames.toList()
                val rows = mutableListOf<List<String>>()
                while (it.moveToNext()) {
                    val row = mutableListOf<String>()
                    for (i in 0 until it.columnCount) {
                        row.add(if (it.isNull(i)) "NULL" else it.getString(i))
                    }
                    rows.add(row)
                }
                SqlResult.QuerySuccess(cols, rows)
            }
        } else {
            db.execSQL(trimmed)
            SqlResult.ExecutionSuccess("SQL statement executed successfully.")
        }
    }

    // --- Clear All ---
    fun clearAll() {
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM transactions")
            db.execSQL("DELETE FROM accounts")
            db.execSQL("DELETE FROM debts")
            db.execSQL("DELETE FROM net_worth_history")
            db.execSQL("DELETE FROM earmarked_funds")
            db.execSQL("DELETE FROM budget_forecast")
            db.execSQL("DELETE FROM notes")
            db.execSQL("DELETE FROM categories")
            dbHelper?.seedCategories(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun currentTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    private class DatabaseHelper(context: Context, name: String) :
        SQLiteOpenHelper(context, name, null, 1) {

        override fun onCreate(db: SQLiteDatabase) {
            ensureSchema(db)
        }

        override fun onOpen(db: SQLiteDatabase) {
            super.onOpen(db)
            ensureSchema(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            ensureSchema(db)
        }

        fun ensureSchema(db: SQLiteDatabase) {
            try {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS accounts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        currency TEXT DEFAULT 'CNY',
                        balance REAL DEFAULT 0,
                        account_type TEXT DEFAULT 'cash',
                        notes TEXT DEFAULT '',
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        type TEXT DEFAULT 'expense',
                        icon TEXT DEFAULT '',
                        color TEXT DEFAULT '#4CAF50'
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        account_id INTEGER,
                        category_id INTEGER,
                        amount REAL DEFAULT 0,
                        type TEXT DEFAULT 'expense',
                        description TEXT DEFAULT '',
                        date DATE DEFAULT CURRENT_DATE,
                        currency TEXT DEFAULT '',
                        exchange_rate REAL DEFAULT 0,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS debts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        type TEXT DEFAULT 'loan',
                        total_amount REAL DEFAULT 0,
                        paid_amount REAL DEFAULT 0,
                        interest_rate REAL DEFAULT 0,
                        currency TEXT DEFAULT 'CNY',
                        start_date DATE DEFAULT CURRENT_DATE,
                        due_date DATE DEFAULT CURRENT_DATE,
                        completed INTEGER DEFAULT 0,
                        debtor TEXT DEFAULT 'me',
                        note TEXT DEFAULT '',
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS settings (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS net_worth_history (
                        date TEXT PRIMARY KEY,
                        assets REAL DEFAULT 0,
                        liabilities REAL DEFAULT 0
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS earmarked_funds (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        amount REAL DEFAULT 0,
                        categories TEXT DEFAULT ''
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS budget_forecast (
                        month TEXT PRIMARY KEY,
                        assets REAL,
                        liabilities REAL,
                        income REAL,
                        expense REAL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS notes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        content TEXT DEFAULT '',
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """.trimIndent()
                )

                // Add missing columns if database was created by an older version or migration
                addColumnIfNotExists(db, "debts", "debtor", "TEXT DEFAULT 'me'")
                addColumnIfNotExists(db, "debts", "note", "TEXT DEFAULT ''")
                addColumnIfNotExists(db, "debts", "paid_amount", "REAL DEFAULT 0")
                addColumnIfNotExists(db, "debts", "interest_rate", "REAL DEFAULT 0")
                addColumnIfNotExists(db, "debts", "currency", "TEXT DEFAULT 'CNY'")
                addColumnIfNotExists(db, "transactions", "currency", "TEXT DEFAULT ''")
                addColumnIfNotExists(db, "transactions", "exchange_rate", "REAL DEFAULT 0")
                addColumnIfNotExists(db, "accounts", "account_type", "TEXT DEFAULT 'cash'")
                addColumnIfNotExists(db, "accounts", "notes", "TEXT DEFAULT ''")

                seedCategories(db)
            } catch (e: Exception) {
                // schema check fallback
            }
        }

        private fun addColumnIfNotExists(db: SQLiteDatabase, table: String, column: String, typeDef: String) {
            try {
                val cursor = db.rawQuery("PRAGMA table_info($table)", null)
                var exists = false
                cursor.use {
                    while (it.moveToNext()) {
                        val colName = it.getString(1)
                        if (colName.equals(column, ignoreCase = true)) {
                            exists = true
                            break
                        }
                    }
                }
                if (!exists) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN $column $typeDef")
                }
            } catch (e: Exception) {
                // Ignore alter errors if table doesn't exist yet
            }
        }

        fun seedCategories(db: SQLiteDatabase) {
            val countCursor = db.rawQuery("SELECT COUNT(*) FROM categories", null)
            val count = countCursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
            if (count > 0) return

            val defaultCategories = listOf(
                arrayOf("Salary", "income", "💰", "#4CAF50"),
                arrayOf("Bonus", "income", "🎁", "#8BC34A"),
                arrayOf("Investment Income", "income", "📈", "#00BCD4"),
                arrayOf("Other Income", "income", "💵", "#009688"),
                arrayOf("Food & Dining", "expense", "🍜", "#FF5722"),
                arrayOf("Transportation", "expense", "🚗", "#FF9800"),
                arrayOf("Shopping", "expense", "🛒", "#E91E63"),
                arrayOf("Housing", "expense", "🏠", "#9C27B0"),
                arrayOf("Entertainment", "expense", "🎮", "#673AB7"),
                arrayOf("Healthcare", "expense", "🏥", "#F44336"),
                arrayOf("Education", "expense", "📚", "#3F51B5"),
                arrayOf("Other Expense", "expense", "💸", "#795548")
            )
            for (cat in defaultCategories) {
                val cv = ContentValues().apply {
                    put("name", cat[0])
                    put("type", cat[1])
                    put("icon", cat[2])
                    put("color", cat[3])
                }
                db.insert("categories", null, cv)
            }
        }
    }
}

sealed class SqlResult {
    object Empty : SqlResult()
    data class QuerySuccess(val columns: List<String>, val rows: List<List<String>>) : SqlResult()
    data class ExecutionSuccess(val message: String) : SqlResult()
    data class Error(val message: String) : SqlResult()
}
