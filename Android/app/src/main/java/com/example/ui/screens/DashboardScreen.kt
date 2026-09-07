package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Composable
fun DashboardScreen(
    baseCurrency: String,
    accounts: List<Account>,
    debts: List<DebtItem>,
    transactions: List<TransactionItem>,
    netWorthHistory: List<NetWorthHistoryItem>,
    startDate: String,
    endDate: String,
    period: String,
    onRangeChanged: (String, String, String) -> Unit,
    onUpdateNetWorth: (String, Double, Double) -> Unit
) {
    val symbol = CurrencyConverter.symbol(baseCurrency)
    val todayIso = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    // Generate period labels
    val periodData = remember(startDate, endDate, period, transactions, netWorthHistory, accounts, debts) {
        computeDashboardPeriods(
            startDate = startDate,
            endDate = endDate,
            period = period,
            transactions = transactions,
            history = netWorthHistory,
            accounts = accounts,
            debts = debts,
            baseCurrency = baseCurrency
        )
    }

    var editItem by remember { mutableStateOf<DashboardPeriodRow?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GrayBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Date & Period Filter Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )

                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { onRangeChanged(it, endDate, period) },
                        label = { Text("Start (YYYY-MM)", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1.1f),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )

                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { onRangeChanged(startDate, it, period) },
                        label = { Text("End (YYYY-MM)", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1.1f),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )

                    // Period Toggle (Month / Year)
                    FilterChip(
                        selected = period == "month",
                        onClick = { onRangeChanged(startDate, endDate, "month") },
                        label = { Text("M", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = period == "year",
                        onClick = { onRangeChanged(startDate, endDate, "year") },
                        label = { Text("Y", fontSize = 11.sp) }
                    )
                }
            }
        }

        // Card 1: Assets & Liabilities Trend
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        text = "Assets & Liabilities Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavySidebar
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FinancialTrendLineChart(
                        labels = periodData.labels,
                        line1Data = periodData.assetsList,
                        line1Color = PrimaryBlueDeep,
                        line1Label = "Assets",
                        line2Data = periodData.liabList,
                        line2Color = OrangeDark,
                        line2Label = "Liabilities",
                        currencySymbol = symbol
                    )
                }
            }
        }

        // Card 2: Upcoming Debt & Credit Due Dates
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = OrangeEarmarked,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Upcoming Debt & Credit Due Dates",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NavySidebar
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    val upcoming = debts.filter { !it.completed && it.dueDate.isNotBlank() }
                        .sortedBy { it.dueDate }
                        .take(6)

                    if (upcoming.isEmpty()) {
                        Text(
                            text = "No upcoming due dates.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GrayTextSecondary
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            upcoming.forEach { d ->
                                val isOverdue = d.dueDate < todayIso && d.debtor == "me"
                                val isReceivable = d.debtor == "other"
                                val remaining = d.totalAmount - d.paidAmount

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isOverdue) RedLight.copy(alpha = 0.4f) else GrayBackground,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(
                                                    when {
                                                        isReceivable -> GreenIncome
                                                        isOverdue -> RedExpense
                                                        else -> PrimaryBlue
                                                    },
                                                    CircleShape
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${d.name} ${if (isReceivable) "(Owed to me)" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isOverdue) RedDark else NavySidebar
                                        )
                                    }
                                    Text(
                                        text = "${d.dueDate} (${CurrencyConverter.symbol(d.currency)}${formatCompact(remaining)} left)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isOverdue) RedExpense else GrayTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Card 3: Income vs Expense Trend
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        text = "Income vs Expense",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavySidebar
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FinancialTrendLineChart(
                        labels = periodData.labels,
                        line1Data = periodData.incomeList,
                        line1Color = GreenIncome,
                        line1Label = "Income",
                        line2Data = periodData.expenseList,
                        line2Color = RedExpense,
                        line2Label = "Expense",
                        currencySymbol = symbol
                    )
                }
            }
        }

        // Card 4: Balance (Income - Expense) Trend
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        text = "Balance (Income - Expense)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavySidebar
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FinancialTrendLineChart(
                        labels = periodData.labels,
                        line1Data = periodData.balanceList,
                        line1Color = PurpleBalance,
                        line1Label = "Balance",
                        currencySymbol = symbol
                    )
                }
            }
        }

        // Card 5: Period Summary Table (Horizontal scroll on mobile)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    var summarySortBy by remember { mutableStateOf("period") }
                    var summarySortAsc by remember { mutableStateOf(true) }

                    val sortedRows = remember(periodData.rows, summarySortBy, summarySortAsc) {
                        val sorted = when (summarySortBy) {
                            "period" -> periodData.rows.sortedBy { it.periodLabel }
                            "income" -> periodData.rows.sortedBy { it.income ?: 0.0 }
                            "expense" -> periodData.rows.sortedBy { it.expense ?: 0.0 }
                            "balance" -> periodData.rows.sortedBy { it.balance ?: 0.0 }
                            "assets" -> periodData.rows.sortedBy { it.assets ?: 0.0 }
                            else -> periodData.rows
                        }
                        if (!summarySortAsc) sorted.reversed() else sorted
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Period Summary (${sortedRows.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = NavySidebar
                        )

                        // Sort Chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                "period" to "Period",
                                "income" to "Inc",
                                "expense" to "Exp",
                                "balance" to "Bal",
                                "assets" to "A/L"
                            ).forEach { (key, label) ->
                                val isActive = summarySortBy == key
                                FilterChip(
                                    selected = isActive,
                                    onClick = {
                                        if (isActive) {
                                            summarySortAsc = !summarySortAsc
                                        } else {
                                            summarySortBy = key
                                            summarySortAsc = (key == "period") // period defaults asc, amounts default desc
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = "$label${if (isActive) (if (summarySortAsc) " ▲" else " ▼") else ""}",
                                            fontSize = 10.sp,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    modifier = Modifier.height(26.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(scrollState)
                    ) {
                        // Header Row
                        Row(
                            modifier = Modifier
                                .background(Color(0xFFF8F9FA), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Period", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(80.dp), color = NavySidebar)
                            Text("Income", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(90.dp), color = GreenIncome)
                            Text("Expense", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(90.dp), color = RedExpense)
                            Text("Balance", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(90.dp), color = PurpleBalance)
                            Text("Assets / Liabilities", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(180.dp), color = NavySidebar)
                        }

                        // Data Rows
                        sortedRows.forEach { row ->
                            Row(
                                modifier = Modifier
                                    .clickable { editItem = row }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .border(0.5.dp, GrayBorder.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(row.periodLabel, fontSize = 11.sp, modifier = Modifier.width(80.dp), color = NavySidebar)
                                Text(
                                    text = if (row.income != null) "$symbol${"%,.2f".format(row.income)}" else "--",
                                    fontSize = 11.sp,
                                    color = GreenIncome,
                                    modifier = Modifier.width(90.dp)
                                )
                                Text(
                                    text = if (row.expense != null) "$symbol${"%,.2f".format(row.expense)}" else "--",
                                    fontSize = 11.sp,
                                    color = RedExpense,
                                    modifier = Modifier.width(90.dp)
                                )
                                Text(
                                    text = if (row.balance != null) "$symbol${"%,.2f".format(row.balance)}" else "--",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if ((row.balance ?: 0.0) >= 0) GreenIncome else RedExpense,
                                    modifier = Modifier.width(90.dp)
                                )
                                val astStr = if (row.assets != null) "$symbol${formatCompact(row.assets)}" else "--"
                                val libStr = if (row.liabilities != null) "$symbol${formatCompact(row.liabilities)}" else "--"
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.width(180.dp)
                                ) {
                                    Text(
                                        text = "$astStr / $libStr",
                                        fontSize = 11.sp,
                                        color = NavySidebar,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit snapshot",
                                        tint = GrayTextMuted,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Net Worth Snapshot Dialog
    if (editItem != null) {
        val item = editItem!!
        var editAssets by remember { mutableStateOf(item.assets?.toString() ?: "") }
        var editLiab by remember { mutableStateOf(item.liabilities?.toString() ?: "") }

        AlertDialog(
            onDismissRequest = { editItem = null },
            title = { Text("Edit Snapshot (${item.periodLabel})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editAssets,
                        onValueChange = { editAssets = it },
                        label = { Text("Assets ($symbol)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editLiab,
                        onValueChange = { editLiab = it },
                        label = { Text("Liabilities ($symbol)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val a = editAssets.toDoubleOrNull() ?: (item.assets ?: 0.0)
                    val l = editLiab.toDoubleOrNull() ?: (item.liabilities ?: 0.0)
                    val dateStr = if (item.periodLabel.length == 4) "${item.periodLabel}-01-01" else "${item.periodLabel}-01"
                    onUpdateNetWorth(dateStr, a, l)
                    editItem = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editItem = null }) { Text("Cancel") }
            }
        )
    }
}

data class DashboardPeriodRow(
    val periodLabel: String,
    val income: Double?,
    val expense: Double?,
    val balance: Double?,
    val assets: Double?,
    val liabilities: Double?
)

data class DashboardPeriodData(
    val labels: List<String>,
    val incomeList: List<Double?>,
    val expenseList: List<Double?>,
    val balanceList: List<Double?>,
    val assetsList: List<Double?>,
    val liabList: List<Double?>,
    val rows: List<DashboardPeriodRow>
)

fun computeDashboardPeriods(
    startDate: String,
    endDate: String,
    period: String,
    transactions: List<TransactionItem>,
    history: List<NetWorthHistoryItem>,
    accounts: List<Account>,
    debts: List<DebtItem>,
    baseCurrency: String
): DashboardPeriodData {
    val labels = mutableListOf<String>()
    val cal = Calendar.getInstance()
    val now = cal.time
    val nowY = SimpleDateFormat("yyyy", Locale.getDefault()).format(now).toInt()
    val nowM = SimpleDateFormat("MM", Locale.getDefault()).format(now).toInt()

    val startCal = Calendar.getInstance().apply {
        try {
            val parts = (if (startDate.isNotBlank()) startDate else "2024-01").split("-")
            val y = parts[0].toInt()
            val m = if (parts.size > 1) parts[1].toInt() else 1
            set(y, m - 1, 1)
        } catch (e: Exception) {
            set(nowY, 0, 1)
        }
    }

    val endCal = Calendar.getInstance().apply {
        try {
            val parts = (if (endDate.isNotBlank()) endDate else "2026-12").split("-")
            val y = parts[0].toInt()
            val m = if (parts.size > 1) parts[1].toInt() else 12
            set(y, m - 1, 1)
        } catch (e: Exception) {
            set(nowY, 11, 1)
        }
    }

    if (period == "year") {
        var y = startCal.get(Calendar.YEAR)
        val endY = endCal.get(Calendar.YEAR)
        while (y <= endY) {
            labels.add(y.toString())
            y++
        }
    } else {
        val curr = startCal.clone() as Calendar
        while (!curr.after(endCal)) {
            val y = curr.get(Calendar.YEAR)
            val m = curr.get(Calendar.MONTH) + 1
            labels.add("%04d-%02d".format(y, m))
            curr.add(Calendar.MONTH, 1)
        }
    }

    val historyMap = history.associateBy {
        if (period == "year") it.date.take(4) else it.date.take(7)
    }

    // Guarantee the chart range always includes the current period so the
    // dashboard actually shows a data point for it (even if the stored end
    // date is in the past).
    val curLabel = if (period == "year") nowY.toString() else "%04d-%02d".format(nowY, nowM)
    if (labels.isNotEmpty() && labels.last() < curLabel) {
        val extra = endCal.clone() as Calendar
        while (true) {
            extra.add(Calendar.MONTH, 1)
            val y = extra.get(Calendar.YEAR)
            val m = extra.get(Calendar.MONTH) + 1
            labels.add(if (period == "year") y.toString() else "%04d-%02d".format(y, m))
            if (labels.last() >= curLabel) break
        }
    }

    // Live asset total includes account balances and uncompleted receivables (debtor == "other")
    val liveAssets = accounts.sumOf { CurrencyConverter.convert(it.balance, it.currency, baseCurrency) } +
        debts.filter { !it.completed && it.debtor == "other" }
            .sumOf { CurrencyConverter.convert(it.totalAmount - it.paidAmount, it.currency, baseCurrency) }
    val liveLiab = debts.filter { !it.completed && it.debtor == "me" }
        .sumOf { CurrencyConverter.convert(it.totalAmount - it.paidAmount, it.currency, baseCurrency) }

    val incomeList = mutableListOf<Double?>()
    val expenseList = mutableListOf<Double?>()
    val balanceList = mutableListOf<Double?>()
    val assetsList = mutableListOf<Double?>()
    val liabList = mutableListOf<Double?>()
    val rows = mutableListOf<DashboardPeriodRow>()

    for (label in labels) {
        val txInPeriod = transactions.filter {
            if (period == "year") it.date.startsWith(label) else it.date.startsWith(label)
        }
        val inc = if (txInPeriod.isEmpty()) null else txInPeriod.filter { it.type == "income" }.sumOf { it.baseAmount }
        val exp = if (txInPeriod.isEmpty()) null else txInPeriod.filter { it.type == "expense" }.sumOf { it.baseAmount }
        val bal = if (inc != null && exp != null) inc - exp else null

        val currentLabel = if (period == "year") nowY.toString() else "%04d-%02d".format(nowY, nowM)
        val snap = historyMap[label]
        // Current period uses the live total so it always matches the Accounts/Budget screens;
        // past periods read the stored net_worth_history snapshot.
        val ast = if (label == currentLabel) liveAssets else snap?.assets
        val lib = if (label == currentLabel) liveLiab else snap?.liabilities

        incomeList.add(inc)
        expenseList.add(exp)
        balanceList.add(bal)
        assetsList.add(ast)
        liabList.add(lib)

        rows.add(
            DashboardPeriodRow(
                periodLabel = label,
                income = inc,
                expense = exp,
                balance = bal,
                assets = ast,
                liabilities = lib
            )
        )
    }

    return DashboardPeriodData(
        labels = labels,
        incomeList = incomeList,
        expenseList = expenseList,
        balanceList = balanceList,
        assetsList = assetsList,
        liabList = liabList,
        rows = rows
    )
}
