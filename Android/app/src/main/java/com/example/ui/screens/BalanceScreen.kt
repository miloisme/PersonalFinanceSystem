package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.sync.CurrencyConverter
import com.example.ui.components.*
import com.example.ui.theme.*
import java.util.*

val MONTH_NAMES = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

@Composable
fun BalanceScreen(
    baseCurrency: String,
    selectedYear: Int,
    transactions: List<TransactionItem>,
    categories: List<Category>,
    onSelectYear: (Int) -> Unit
) {
    val symbol = CurrencyConverter.symbol(baseCurrency)
    val availableYears = remember(transactions) {
        val ySet = transactions.mapNotNull { it.date.take(4).toIntOrNull() }.toSet()
        if (ySet.isEmpty()) listOf(selectedYear) else ySet.sorted()
    }

    val balanceData = remember(selectedYear, transactions, categories, baseCurrency) {
        computeBalanceData(selectedYear, transactions, categories, baseCurrency)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GrayBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Year Selector Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Year:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NavySidebar
                    )

                    availableYears.forEach { y ->
                        FilterChip(
                            selected = selectedYear == y,
                            onClick = { onSelectYear(y) },
                            label = { Text(y.toString()) }
                        )
                    }
                }
            }
        }

        // Composite Chart: Monthly Income / Expense / Balance
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        text = "Monthly Income / Expense / Balance ($selectedYear)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavySidebar
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    CompositeIncomeExpenseBalanceChart(
                        labels = MONTH_NAMES,
                        incomeList = balanceData.monthlyIncome,
                        expenseList = balanceData.monthlyExpense,
                        balanceList = balanceData.monthlyBalance,
                        currencySymbol = symbol
                    )
                }
            }
        }

        // Breakdown Pies (Income & Expense)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        text = "Income Breakdown by Category",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GreenIncome
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FinancialPieChart(
                        slices = balanceData.incomePieSlices,
                        emptyLabel = "No Income in $selectedYear",
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
                        text = "Expense Breakdown by Category",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = RedExpense
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FinancialPieChart(
                        slices = balanceData.expensePieSlices,
                        emptyLabel = "No Expense in $selectedYear",
                        currencySymbol = symbol
                    )
                }
            }
        }

        // Monthly Detail by Category Matrix Table
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        text = "Monthly Detail by Category ($selectedYear)",
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
                        // Header Row
                        Row(
                            modifier = Modifier
                                .background(Color(0xFFF8F9FA), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Category", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(130.dp), color = NavySidebar)
                            MONTH_NAMES.forEach { m ->
                                Text(m, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.width(65.dp), color = NavySidebar)
                            }
                            Text("Year Total", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(85.dp), color = NavySidebar)
                            Text("Monthly Avg", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(85.dp), color = NavySidebar)
                            Text("Trend", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(90.dp), color = NavySidebar)
                        }

                        // Income Category Rows
                        balanceData.incomeRows.forEach { row ->
                            MatrixDataRow(row = row, symbol = symbol, color = GreenIncome)
                        }

                        // Total Income Summary Row
                        SummaryMatrixRow(
                            title = "Total Income",
                            monthlyValues = balanceData.monthlyIncome,
                            total = balanceData.yearTotalIncome,
                            avg = balanceData.avgMonthlyIncome,
                            symbol = symbol,
                            bgColor = GreenLight,
                            textColor = Color(0xFF1E8449),
                            trendColor = GreenIncome
                        )

                        // Expense Category Rows
                        balanceData.expenseRows.forEach { row ->
                            MatrixDataRow(row = row, symbol = symbol, color = RedExpense)
                        }

                        // Total Expense Summary Row
                        SummaryMatrixRow(
                            title = "Total Expense",
                            monthlyValues = balanceData.monthlyExpense,
                            total = balanceData.yearTotalExpense,
                            avg = balanceData.avgMonthlyExpense,
                            symbol = symbol,
                            bgColor = RedLight,
                            textColor = RedDark,
                            trendColor = RedExpense
                        )

                        // Balance Summary Row
                        SummaryMatrixRow(
                            title = "Balance",
                            monthlyValues = balanceData.monthlyBalance,
                            total = balanceData.yearTotalBalance,
                            avg = balanceData.avgMonthlyBalance,
                            symbol = symbol,
                            bgColor = PurpleLight,
                            textColor = Color(0xFF6C3483),
                            trendColor = PurpleBalance
                        )

                        // Saving Rate Summary Row
                        Row(
                            modifier = Modifier
                                .background(TealLight, RoundedCornerShape(2.dp))
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Saving Rate", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(130.dp), color = TealSavings)
                            balanceData.savingRates.forEach { sr ->
                                Text(
                                    text = if (sr != null) "%.1f%%".format(sr) else "--",
                                    fontSize = 10.sp,
                                    color = TealSavings,
                                    modifier = Modifier.width(65.dp)
                                )
                            }
                            Text(
                                text = "%.1f%%".format(balanceData.annualSavingRate),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = TealSavings,
                                modifier = Modifier.width(85.dp)
                            )
                            Text("-", fontSize = 11.sp, color = GrayTextMuted, modifier = Modifier.width(85.dp))
                            Text("-", fontSize = 11.sp, color = GrayTextMuted, modifier = Modifier.width(90.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MatrixDataRow(
    row: CategoryMatrixRow,
    symbol: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .border(0.5.dp, GrayBorder.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(row.categoryName, fontSize = 11.sp, modifier = Modifier.width(130.dp), color = color, maxLines = 1)
        row.monthlyAmounts.forEach { amt ->
            Text(
                text = if (amt > 0) formatCompact(amt) else "--",
                fontSize = 10.sp,
                color = color,
                modifier = Modifier.width(65.dp)
            )
        }
        Text("$symbol${formatCompact(row.total)}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = color, modifier = Modifier.width(85.dp))
        Text("$symbol${formatCompact(row.avg)}", fontSize = 10.sp, color = color, modifier = Modifier.width(85.dp))
        SparklineTrend(values = row.monthlyAmounts, color = color, modifier = Modifier.width(90.dp).height(20.dp))
    }
}

@Composable
fun SummaryMatrixRow(
    title: String,
    monthlyValues: List<Double?>,
    total: Double,
    avg: Double,
    symbol: String,
    bgColor: Color,
    textColor: Color,
    trendColor: Color
) {
    Row(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(130.dp), color = textColor)
        monthlyValues.forEach { amt ->
            Text(
                text = if (amt != null) formatCompact(amt) else "--",
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                color = textColor,
                modifier = Modifier.width(65.dp)
            )
        }
        Text("$symbol${formatCompact(total)}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = textColor, modifier = Modifier.width(85.dp))
        Text("$symbol${formatCompact(avg)}", fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = textColor, modifier = Modifier.width(85.dp))
        SparklineTrend(values = monthlyValues.map { it ?: 0.0 }, color = trendColor, modifier = Modifier.width(90.dp).height(20.dp))
    }
}

data class CategoryMatrixRow(
    val categoryId: Long,
    val categoryName: String,
    val color: String,
    val monthlyAmounts: List<Double>,
    val total: Double,
    val avg: Double
)

data class BalanceComputedData(
    val monthlyIncome: List<Double?>,
    val monthlyExpense: List<Double?>,
    val monthlyBalance: List<Double?>,
    val savingRates: List<Double?>,
    val yearTotalIncome: Double,
    val yearTotalExpense: Double,
    val yearTotalBalance: Double,
    val avgMonthlyIncome: Double,
    val avgMonthlyExpense: Double,
    val avgMonthlyBalance: Double,
    val annualSavingRate: Double,
    val incomeRows: List<CategoryMatrixRow>,
    val expenseRows: List<CategoryMatrixRow>,
    val incomePieSlices: List<PieSliceData>,
    val expensePieSlices: List<PieSliceData>
)

fun computeBalanceData(
    year: Int,
    transactions: List<TransactionItem>,
    categories: List<Category>,
    baseCurrency: String
): BalanceComputedData {
    val txInYear = transactions.filter { it.date.startsWith(year.toString()) }
    val catMap = categories.associateBy { it.id }

    val monthlyInc = DoubleArray(12)
    val monthlyExp = DoubleArray(12)
    val monthlyCount = IntArray(12)

    val incCatMap = mutableMapOf<Long, DoubleArray>()
    val expCatMap = mutableMapOf<Long, DoubleArray>()

    for (t in txInYear) {
        val m = t.date.drop(5).take(2).toIntOrNull() ?: continue
        if (m in 1..12) {
            val idx = m - 1
            monthlyCount[idx]++
            val amt = t.baseAmount
            val cId = t.categoryId ?: 0L
            if (t.type == "income") {
                monthlyInc[idx] += amt
                val arr = incCatMap.getOrPut(cId) { DoubleArray(12) }
                arr[idx] += amt
            } else {
                monthlyExp[idx] += amt
                val arr = expCatMap.getOrPut(cId) { DoubleArray(12) }
                arr[idx] += amt
            }
        }
    }

    val latestMonth = if (year == Calendar.getInstance().get(Calendar.YEAR)) {
        Calendar.getInstance().get(Calendar.MONTH) + 1
    } else 12

    fun createRows(catMapData: Map<Long, DoubleArray>, defaultColor: String): List<CategoryMatrixRow> {
        val list = mutableListOf<CategoryMatrixRow>()
        catMapData.forEach { (cid, arr) ->
            val cat = catMap[cid]
            val name = if (cat != null) "${cat.icon} ${cat.name}".trim() else "Other"
            val total = arr.sum()
            val avg = if (latestMonth > 0) total / latestMonth else 0.0
            list.add(
                CategoryMatrixRow(
                    categoryId = cid,
                    categoryName = name,
                    color = cat?.color ?: defaultColor,
                    monthlyAmounts = arr.toList(),
                    total = total,
                    avg = avg
                )
            )
        }
        return list.sortedByDescending { it.total }
    }

    val incomeRows = createRows(incCatMap, "#27AE60")
    val expenseRows = createRows(expCatMap, "#E74C3C")

    val incDisp = monthlyInc.mapIndexed { idx, v -> if (monthlyCount[idx] > 0) v else null }
    val expDisp = monthlyExp.mapIndexed { idx, v -> if (monthlyCount[idx] > 0) v else null }
    val balDisp = (0 until 12).map { idx ->
        if (incDisp[idx] != null && expDisp[idx] != null) incDisp[idx]!! - expDisp[idx]!! else null
    }

    val totalIncYear = monthlyInc.take(latestMonth).sum()
    val totalExpYear = monthlyExp.take(latestMonth).sum()
    val totalBalYear = totalIncYear - totalExpYear

    val savingRates = (0 until 12).map { idx ->
        val inV = incDisp[idx]
        val balV = balDisp[idx]
        if (inV != null && balV != null && inV > 0.0) {
            (balV / inV) * 100.0
        } else null
    }
    val annualSr = if (totalIncYear > 0.0) (totalBalYear / totalIncYear) * 100.0 else 0.0

    val incomePies = incomeRows.mapIndexed { idx, r ->
        PieSliceData(r.categoryName, r.total, Color(0xFF27AE60).copy(alpha = maxOf(0.4f, 1f - (idx * 0.12f))))
    }
    val expensePies = expenseRows.mapIndexed { idx, r ->
        PieSliceData(r.categoryName, r.total, Color(0xFFE74C3C).copy(alpha = maxOf(0.4f, 1f - (idx * 0.12f))))
    }

    return BalanceComputedData(
        monthlyIncome = incDisp,
        monthlyExpense = expDisp,
        monthlyBalance = balDisp,
        savingRates = savingRates,
        yearTotalIncome = totalIncYear,
        yearTotalExpense = totalExpYear,
        yearTotalBalance = totalBalYear,
        avgMonthlyIncome = if (latestMonth > 0) totalIncYear / latestMonth else 0.0,
        avgMonthlyExpense = if (latestMonth > 0) totalExpYear / latestMonth else 0.0,
        avgMonthlyBalance = if (latestMonth > 0) totalBalYear / latestMonth else 0.0,
        annualSavingRate = annualSr,
        incomeRows = incomeRows,
        expenseRows = expenseRows,
        incomePieSlices = incomePies,
        expensePieSlices = expensePies
    )
}
