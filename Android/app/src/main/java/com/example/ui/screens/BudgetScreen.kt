package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.sync.CurrencyConverter
import com.example.ui.components.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

const val RECEIVABLE_SPECIAL_ID = -1L

@Composable
fun BudgetScreen(
    baseCurrency: String,
    accounts: List<Account>,
    debts: List<DebtItem>,
    funds: List<EarmarkedFund>,
    categories: List<Category>,
    transactions: List<TransactionItem>,
    budgetAccountIds: Set<Long>,
    forecast: Map<String, BudgetForecastItem>,
    onToggleAccount: (Long, Boolean) -> Unit,
    onAddFund: (String, Double, List<Long>) -> Unit,
    onUpdateFund: (Long, String, Double, List<Long>) -> Unit,
    onDeleteFund: (Long) -> Unit,
    onSetForecastCell: (String, String, Double?) -> Unit
) {
    val symbol = CurrencyConverter.symbol(baseCurrency)

    // Total receivable owed to me
    val totalReceivable = remember(debts, baseCurrency) {
        debts.filter { !it.completed && it.debtor == "other" }
            .sumOf { CurrencyConverter.convert(it.totalAmount - it.paidAmount, it.currency, baseCurrency) }
    }

    // Available assets calculation
    val availableAssets = remember(accounts, budgetAccountIds, totalReceivable, baseCurrency) {
        var sum = 0.0
        for (a in accounts) {
            if (budgetAccountIds.contains(a.id)) {
                sum += CurrencyConverter.convert(a.balance, a.currency, baseCurrency)
            }
        }
        if (budgetAccountIds.contains(RECEIVABLE_SPECIAL_ID)) {
            sum += totalReceivable
        }
        sum
    }

    val totalEarmarked = remember(funds) { funds.sumOf { it.amount } }
    val netAvailable = availableAssets - totalEarmarked

    // Dialog state for budget accounts selection
    var showBudgetAccountsDialog by remember { mutableStateOf(false) }

    // Dialog state for fund
    var showFundDialog by remember { mutableStateOf(false) }
    var editingFund by remember { mutableStateOf<EarmarkedFund?>(null) }

    // Dialog state for forecast cell edit
    var editingForecastCell by remember { mutableStateOf<Pair<String, String>?>(null) }

    // 12-month projection series
    val forecastSeries = remember(accounts, debts, transactions, forecast, baseCurrency) {
        computeForecastSeries(
            accounts = accounts,
            debts = debts,
            transactions = transactions,
            forecast = forecast,
            baseCurrency = baseCurrency
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GrayBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Summary Cards Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryStatCard(
                    title = "Available Assets",
                    amount = availableAssets,
                    symbol = symbol,
                    color = GreenIncome,
                    modifier = Modifier.weight(1f)
                )
                SummaryStatCard(
                    title = "Earmarked Funds",
                    amount = totalEarmarked,
                    symbol = symbol,
                    color = OrangeEarmarked,
                    modifier = Modifier.weight(1f)
                )
                SummaryStatCard(
                    title = "Net Available",
                    amount = netAvailable,
                    symbol = symbol,
                    color = PrimaryBlue,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Section 1: Select Accounts & Earmarked Funds
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Budget Accounts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = NavySidebar
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${budgetAccountIds.size} accounts included • $symbol${"%,.2f".format(availableAssets)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = PrimaryBlueDeep,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    OutlinedButton(
                        onClick = { showBudgetAccountsDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Select Accounts", fontSize = 12.sp)
                    }
                }
            }
        }

        // Section 2: Earmarked Funds
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Earmarked Funds",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = NavySidebar
                        )
                        Button(
                            onClick = {
                                editingFund = null
                                showFundDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenIncome),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Fund", fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (funds.isEmpty()) {
                        Text(
                            text = "No earmarked funds configured.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GrayTextMuted
                        )
                    } else {
                        val catMap = categories.associateBy { it.id }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            funds.forEach { fund ->
                                val linkedCatNames = fund.categories.mapNotNull { catMap[it]?.name }
                                val avgMonthlyExp = computeAvgMonthlyExpense(fund.categories, transactions)
                                val coverageMonths = if (avgMonthlyExp > 0.0) fund.amount / avgMonthlyExp else null

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(GrayBackground, RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = fund.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = NavySidebar
                                        )
                                        Text(
                                            text = "Linked: ${if (linkedCatNames.isNotEmpty()) linkedCatNames.joinToString(", ") else "None"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = GrayTextSecondary
                                        )
                                        Text(
                                            text = "Coverage: ${if (coverageMonths != null) "%.1f months".format(coverageMonths) else "—"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (coverageMonths != null && coverageMonths >= 6.0) GreenIncome else OrangeEarmarked
                                        )
                                    }
                                    Text(
                                        text = "$symbol${"%,.2f".format(fund.amount)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = OrangeEarmarked
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            editingFund = fund
                                            showFundDialog = true
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = OrangeEarmarked, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { fund.id?.let { onDeleteFund(it) } },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RedExpense, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Future Forecast Charts
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        text = "12-Month Future Forecast: Assets & Liabilities",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavySidebar
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FinancialTrendLineChart(
                        labels = forecastSeries.labels,
                        line1Data = forecastSeries.assets,
                        line1Color = PrimaryBlueDeep,
                        line1Label = "Assets",
                        line2Data = forecastSeries.liabilities,
                        line2Color = OrangeDark,
                        line2Label = "Liabilities",
                        currencySymbol = symbol
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        text = "Forecast Balance = Income − Expense (Surplus / Deficit)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavySidebar
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ForecastBalanceBarChart(
                        labels = forecastSeries.labels,
                        balanceList = forecastSeries.balances,
                        currencySymbol = symbol
                    )
                }
            }
        }

        // Section 4: Editable Forecast Table
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        text = "Forecast Table (Tap cells to override future estimates)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavySidebar
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(scrollState)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier
                                .background(Color(0xFFF8F9FA), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Month", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(75.dp), color = NavySidebar)
                            Text("Assets", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(95.dp), color = PrimaryBlueDeep)
                            Text("Liabilities", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(95.dp), color = OrangeDark)
                            Text("Income", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(90.dp), color = GreenIncome)
                            Text("Expense", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(90.dp), color = RedExpense)
                        }

                        // Data rows
                        forecastSeries.labels.indices.forEach { i ->
                            val m = forecastSeries.labels[i]
                            val isCurrentMonth = i == 0

                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .border(0.5.dp, GrayBorder.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(m, fontSize = 11.sp, modifier = Modifier.width(75.dp), color = NavySidebar)

                                // Assets
                                Text(
                                    text = "$symbol${formatCompact(forecastSeries.assets.getOrNull(i) ?: 0.0)}",
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .width(95.dp)
                                        .clickable(enabled = !isCurrentMonth) {
                                            editingForecastCell = Pair(m, "assets")
                                        },
                                    color = PrimaryBlueDeep
                                )

                                // Liabilities
                                Text(
                                    text = "$symbol${formatCompact(forecastSeries.liabilities.getOrNull(i) ?: 0.0)}",
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .width(95.dp)
                                        .clickable(enabled = !isCurrentMonth) {
                                            editingForecastCell = Pair(m, "liabilities")
                                        },
                                    color = OrangeDark
                                )

                                // Income
                                Text(
                                    text = "$symbol${formatCompact(forecastSeries.incomes.getOrNull(i) ?: 0.0)}",
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .width(90.dp)
                                        .clickable(enabled = !isCurrentMonth) {
                                            editingForecastCell = Pair(m, "income")
                                        },
                                    color = GreenIncome
                                )

                                // Expense
                                Text(
                                    text = "$symbol${formatCompact(forecastSeries.expenses.getOrNull(i) ?: 0.0)}",
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .width(90.dp)
                                        .clickable(enabled = !isCurrentMonth) {
                                            editingForecastCell = Pair(m, "expense")
                                        },
                                    color = RedExpense
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Fund Dialog
    if (showFundDialog) {
        var name by remember { mutableStateOf(editingFund?.name ?: "") }
        var amount by remember { mutableStateOf(editingFund?.amount?.toString() ?: "") }
        var selectedCats by remember { mutableStateOf(editingFund?.categories?.toSet() ?: emptySet()) }

        AlertDialog(
            onDismissRequest = { showFundDialog = false },
            title = { Text(if (editingFund != null) "Edit Earmarked Fund" else "Add Earmarked Fund") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Fund Name (e.g. Emergency Fund)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount ($symbol)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Linked Expense Categories:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                    val expCats = categories.filter { it.type == "expense" }
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp)) {
                        expCats.forEach { c ->
                            val id = c.id ?: return@forEach
                            val isSel = selectedCats.contains(id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCats = if (isSel) selectedCats - id else selectedCats + id
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = isSel, onCheckedChange = { checked ->
                                    selectedCats = if (checked) selectedCats + id else selectedCats - id
                                })
                                Text("${c.icon} ${c.name}", fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && amt > 0.0) {
                        if (editingFund != null) {
                            editingFund?.id?.let { onUpdateFund(it, name, amt, selectedCats.toList()) }
                        } else {
                            onAddFund(name, amt, selectedCats.toList())
                        }
                        showFundDialog = false
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFundDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Budget Accounts Multi-Select Dialog
    if (showBudgetAccountsDialog) {
        AlertDialog(
            onDismissRequest = { showBudgetAccountsDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Budget Accounts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Quick Action Buttons (Select All / Deselect All)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                accounts.forEach { it.id?.let { id -> onToggleAccount(id, true) } }
                                onToggleAccount(RECEIVABLE_SPECIAL_ID, true)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Select All", fontSize = 12.sp)
                        }
                        TextButton(
                            onClick = {
                                accounts.forEach { it.id?.let { id -> onToggleAccount(id, false) } }
                                onToggleAccount(RECEIVABLE_SPECIAL_ID, false)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear All", fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = GrayBorder, thickness = 0.5.dp)

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(accounts.size) { idx ->
                            val acc = accounts[idx]
                            val conv = CurrencyConverter.convert(acc.balance, acc.currency, baseCurrency)
                            val isChecked = budgetAccountIds.contains(acc.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { acc.id?.let { onToggleAccount(it, !isChecked) } }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked -> acc.id?.let { onToggleAccount(it, checked) } }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = acc.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = NavySidebar
                                    )
                                    Text(
                                        text = "${acc.accountType.uppercase()} • ${CurrencyConverter.symbol(acc.currency)}${"%,.2f".format(acc.balance)}",
                                        fontSize = 11.sp,
                                        color = GrayTextSecondary
                                    )
                                }
                                Text(
                                    text = "$symbol${"%,.2f".format(conv)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryBlueDeep
                                )
                            }
                        }

                        item {
                            // Receivable Item
                            val isRecChecked = budgetAccountIds.contains(RECEIVABLE_SPECIAL_ID)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleAccount(RECEIVABLE_SPECIAL_ID, !isRecChecked) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isRecChecked,
                                    onCheckedChange = { checked -> onToggleAccount(RECEIVABLE_SPECIAL_ID, checked) }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Receivable (Owed to Me)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = GreenIncome
                                    )
                                    Text(
                                        text = "Credits / Repayments",
                                        fontSize = 11.sp,
                                        color = GreenIncome.copy(alpha = 0.8f)
                                    )
                                }
                                Text(
                                    text = "$symbol${"%,.2f".format(totalReceivable)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = GreenIncome
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showBudgetAccountsDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // Override forecast cell dialog
    if (editingForecastCell != null) {
        val (month, field) = editingForecastCell!!
        var cellVal by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { editingForecastCell = null },
            title = { Text("Set $field for $month") },
            text = {
                OutlinedTextField(
                    value = cellVal,
                    onValueChange = { cellVal = it },
                    label = { Text("Amount ($symbol)") },
                    placeholder = { Text("Leave empty to clear override") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val num = cellVal.toDoubleOrNull()
                    onSetForecastCell(month, field, num)
                    editingForecastCell = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingForecastCell = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SummaryStatCard(
    title: String,
    amount: Double,
    symbol: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = GrayTextSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$symbol${"%,.2f".format(amount)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

fun computeAvgMonthlyExpense(catIds: List<Long>, transactions: List<TransactionItem>): Double {
    if (catIds.isEmpty()) return 0.0
    val cal = Calendar.getInstance()
    val endStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    cal.add(Calendar.MONTH, -11)
    val startStr = SimpleDateFormat("yyyy-MM-01", Locale.getDefault()).format(cal.time)

    val expenseSum = transactions.filter {
        it.type == "expense" && it.categoryId != null && catIds.contains(it.categoryId) && it.date in startStr..endStr
    }.sumOf { it.baseAmount }

    return expenseSum / 12.0
}

data class ForecastSeriesData(
    val labels: List<String>,
    val assets: List<Double?>,
    val liabilities: List<Double?>,
    val incomes: List<Double?>,
    val expenses: List<Double?>,
    val balances: List<Double?>
)

fun computeForecastSeries(
    accounts: List<Account>,
    debts: List<DebtItem>,
    transactions: List<TransactionItem>,
    forecast: Map<String, BudgetForecastItem>,
    baseCurrency: String
): ForecastSeriesData {
    val labels = mutableListOf<String>()
    val cal = Calendar.getInstance()
    for (i in 0 until 12) {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        labels.add("%04d-%02d".format(y, m))
        cal.add(Calendar.MONTH, 1)
    }

    // Live asset total includes account balances and uncompleted receivables (debtor == "other")
    val liveAssets = accounts.sumOf { CurrencyConverter.convert(it.balance, it.currency, baseCurrency) } +
        debts.filter { !it.completed && it.debtor == "other" }
            .sumOf { CurrencyConverter.convert(it.totalAmount - it.paidAmount, it.currency, baseCurrency) }
    val liveLiab = debts.filter { !it.completed && it.debtor == "me" }
        .sumOf { CurrencyConverter.convert(it.totalAmount - it.paidAmount, it.currency, baseCurrency) }

    val assetsList = mutableListOf<Double?>()
    val liabList = mutableListOf<Double?>()
    val incList = mutableListOf<Double?>()
    val expList = mutableListOf<Double?>()
    val balList = mutableListOf<Double?>()

    var prevA: Double = liveAssets
    var prevL: Double = liveLiab

    for (i in labels.indices) {
        val m = labels[i]
        val ov = forecast[m]
        val isCurrent = (i == 0)

        val inc = ov?.income ?: if (isCurrent) {
            transactions.filter { it.date.startsWith(m) && it.type == "income" }.sumOf { it.baseAmount }
        } else 0.0

        val exp = ov?.expense ?: if (isCurrent) {
            transactions.filter { it.date.startsWith(m) && it.type == "expense" }.sumOf { it.baseAmount }
        } else 0.0

        val bal = inc - exp

        val a = ov?.assets ?: if (isCurrent) liveAssets else (prevA + bal)
        val l = ov?.liabilities ?: if (isCurrent) liveLiab else prevL

        prevA = a
        prevL = l

        assetsList.add(a)
        liabList.add(l)
        incList.add(inc)
        expList.add(exp)
        balList.add(bal)
    }

    return ForecastSeriesData(
        labels = labels,
        assets = assetsList,
        liabilities = liabList,
        incomes = incList,
        expenses = expList,
        balances = balList
    )
}
