package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AccountsScreen(
    baseCurrency: String,
    accounts: List<Account>,
    debts: List<DebtItem>,
    currencies: List<String>,
    netWorthHistory: List<NetWorthHistoryItem>,
    onAddAccount: (Account, TransactionItem?) -> Unit,
    onUpdateAccount: (Account) -> Unit,
    onDeleteAccount: (Long) -> Unit,
    onTransfer: (Long, Long, Double, (Boolean, String?) -> Unit) -> Unit
) {
    val symbol = CurrencyConverter.symbol(baseCurrency)

    val receivable = remember(debts, baseCurrency) {
        debts.filter { !it.completed && it.debtor == "other" }
            .sumOf { CurrencyConverter.convert(it.totalAmount - it.paidAmount, it.currency, baseCurrency) }
    }

    // Aggregate totals
    val (totalAssets, bucketSums, currencySums, displayAccounts) = remember(accounts, debts, baseCurrency, receivable) {
        var total = 0.0
        val buckets = mutableMapOf("cash" to 0.0, "invest" to 0.0, "fixed" to 0.0)
        val curSums = mutableMapOf<String, Double>()
        val list = accounts.toMutableList()

        for (a in accounts) {
            val conv = CurrencyConverter.convert(a.balance, a.currency, baseCurrency)
            total += conv
            val b = when (a.accountType.lowercase()) {
                "invest" -> "invest"
                "fixed" -> "fixed"
                else -> "cash"
            }
            buckets[b] = (buckets[b] ?: 0.0) + conv
            if (conv > 0.0) {
                curSums[a.currency] = (curSums[a.currency] ?: 0.0) + conv
            }
        }

        // Add Receivable owed to me as cash asset
        if (receivable > 0.0) {
            total += receivable
            buckets["cash"] = (buckets["cash"] ?: 0.0) + receivable
            curSums[baseCurrency] = (curSums[baseCurrency] ?: 0.0) + receivable
        }

        Quadruple(total, buckets, curSums, list)
    }

    val totalDebt = remember(debts, baseCurrency) {
        debts.filter { !it.completed && it.debtor == "me" }
            .sumOf { CurrencyConverter.convert(it.totalAmount - it.paidAmount, it.currency, baseCurrency) }
    }
    val netAssets = totalAssets - totalDebt

    // YoY comparison
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)
    val lastYearRecord = remember(netWorthHistory) {
        netWorthHistory.filter { it.date < "$thisYear-01-01" }.maxByOrNull { it.date }
    }
    val lastYearAssets = lastYearRecord?.assets
    val yoyPct = if (lastYearAssets != null && lastYearAssets > 0.0) {
        ((totalAssets - lastYearAssets) / lastYearAssets) * 100.0
    } else null

    // Sorting state
    var accountSortBy by remember { mutableStateOf("type") }
    var accountSortAsc by remember { mutableStateOf(true) }

    val sortedAccounts = remember(accounts, accountSortBy, accountSortAsc, baseCurrency) {
        val sorted = when (accountSortBy) {
            "type" -> accounts.sortedWith(compareBy({ it.accountType.lowercase() }, { -CurrencyConverter.convert(it.balance, it.currency, baseCurrency) }))
            "amount" -> accounts.sortedBy { CurrencyConverter.convert(it.balance, it.currency, baseCurrency) }
            "name" -> accounts.sortedBy { it.name.lowercase() }
            else -> accounts
        }
        if (!accountSortAsc) sorted.reversed() else sorted
    }

    // Dialog state
    var showAccountDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<Account?>(null) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GrayBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Info Card (Assets, Debt, Net Assets)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    // Assets Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Assets", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = PrimaryBlueDeep)
                            Text(
                                text = "$symbol${"%,.2f".format(totalAssets)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlueDeep
                            )
                        }
                        if (yoyPct != null) {
                            val isPos = yoyPct >= 0
                            Surface(
                                color = if (isPos) GreenLight else RedLight,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${if (isPos) "+" else ""}%.1f%% YoY".format(yoyPct),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPos) Color(0xFF1E8449) else RedDark,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (lastYearAssets != null) {
                        Text("Last Year: $symbol${"%,.2f".format(lastYearAssets)}", fontSize = 10.sp, color = GrayTextMuted)
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = GrayBorder.copy(alpha = 0.6f))

                    // Debt Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Debt", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = RedDark)
                            Text(
                                text = "$symbol${"%,.2f".format(totalDebt)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = RedDark
                            )
                        }
                        if (totalAssets > 0.0 && totalDebt > 0.0) {
                            Surface(
                                color = RedLight,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "%.1f%% Debt Ratio".format((totalDebt / totalAssets) * 100.0),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RedDark,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = GrayBorder.copy(alpha = 0.6f))

                    // Net Assets Row
                    Column {
                        Text("Net Assets", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF16A0DE))
                        Text(
                            text = "$symbol${"%,.2f".format(netAssets)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF16A0DE)
                        )
                    }
                }
            }
        }

        // Asset Allocation & Currency Distribution Pies
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        text = "Asset Allocation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavySidebar
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val allocationSlices = listOf(
                        PieSliceData("Cash", bucketSums["cash"] ?: 0.0, Color(0xFF1F4E79)),
                        PieSliceData("Invest", bucketSums["invest"] ?: 0.0, Color(0xFF5F2A7A)),
                        PieSliceData("Fixed", bucketSums["fixed"] ?: 0.0, Color(0xFF9C5600))
                    ).filter { it.value > 0.0 }

                    FinancialPieChart(
                        slices = allocationSlices,
                        emptyLabel = "No Assets",
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
                        text = "Currency Distribution",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavySidebar
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val palette = listOf(
                        Color(0xFF1F4E79), Color(0xFFC0392B), Color(0xFF27AE60),
                        Color(0xFF8E44AD), Color(0xFFD35400), Color(0xFF16A085),
                        Color(0xFF2C3E50), Color(0xFFE67E22), Color(0xFF2980B9)
                    )
                    val currencySlices = currencySums.entries.mapIndexed { idx, (c, amt) ->
                        PieSliceData(c, amt, palette[idx % palette.size])
                    }

                    FinancialPieChart(
                        slices = currencySlices,
                        emptyLabel = "No Currencies",
                        currencySymbol = symbol
                    )
                }
            }
        }

        // Cash / Invest / Fixed Accounts Breakdown Carousel (Swipeable side-by-side cards)
        item {
            val pagerState = rememberPagerState(pageCount = { 3 })
            val coroutineScope = rememberCoroutineScope()

            // Pre-calculate data for each category
            val cashPalette = listOf(
                Color(0xFF1F4E79), Color(0xFF2E86C1), Color(0xFF3498DB),
                Color(0xFF5DADE2), Color(0xFF1B4F72), Color(0xFF2874A6)
            )
            val cashAccs = accounts.filter { it.accountType.lowercase() == "cash" || it.accountType.isBlank() }
            val cashSlices = mutableListOf<PieSliceData>()
            cashAccs.forEachIndexed { idx, a ->
                val conv = CurrencyConverter.convert(a.balance, a.currency, baseCurrency)
                val color = cashPalette[idx % cashPalette.size]
                if (conv > 0.0) cashSlices.add(PieSliceData(a.name, conv, color))
            }
            if (receivable > 0.0) {
                cashSlices.add(PieSliceData("Receivable", receivable, Color(0xFF16A085)))
            }

            val investPalette = listOf(
                Color(0xFF5F2A7A), Color(0xFF8E44AD), Color(0xFF9B59B6),
                Color(0xFFAF7AC5), Color(0xFF6C3483), Color(0xFF7D3C98)
            )
            val investAccs = accounts.filter { it.accountType.lowercase() == "invest" }
            val investSlices = mutableListOf<PieSliceData>()
            investAccs.forEachIndexed { idx, a ->
                val conv = CurrencyConverter.convert(a.balance, a.currency, baseCurrency)
                val color = investPalette[idx % investPalette.size]
                if (conv > 0.0) investSlices.add(PieSliceData(a.name, conv, color))
            }

            val fixedPalette = listOf(
                Color(0xFF9C5600), Color(0xFFD35400), Color(0xFFE67E22),
                Color(0xFFEB984E), Color(0xFFB9770E), Color(0xFFA04000)
            )
            val fixedAccs = accounts.filter { it.accountType.lowercase() == "fixed" }
            val fixedSlices = mutableListOf<PieSliceData>()
            fixedAccs.forEachIndexed { idx, a ->
                val conv = CurrencyConverter.convert(a.balance, a.currency, baseCurrency)
                val color = fixedPalette[idx % fixedPalette.size]
                if (conv > 0.0) fixedSlices.add(PieSliceData(a.name, conv, color))
            }

            val categoriesMeta = listOf(
                Triple("Cash", bucketSums["cash"] ?: 0.0, Color(0xFF1F4E79)),
                Triple("Invest", bucketSums["invest"] ?: 0.0, Color(0xFF5F2A7A)),
                Triple("Fixed", bucketSums["fixed"] ?: 0.0, Color(0xFF9C5600))
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    // Header with category toggle tabs and left/right arrows
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Category Tabs Pill Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            categoriesMeta.forEachIndexed { index, (name, sum, color) ->
                                val isSelected = pagerState.currentPage == index
                                Surface(
                                    onClick = {
                                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                    },
                                    color = if (isSelected) color.copy(alpha = 0.12f) else Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(16.dp),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, color) else null
                                ) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) color else GrayTextSecondary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Left/Right Navigation controls
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (pagerState.currentPage > 0) {
                                        coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                    }
                                },
                                enabled = pagerState.currentPage > 0,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Previous Category",
                                    tint = if (pagerState.currentPage > 0) NavySidebar else GrayTextMuted
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (pagerState.currentPage < 2) {
                                        coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                    }
                                },
                                enabled = pagerState.currentPage < 2,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Next Category",
                                    tint = if (pagerState.currentPage < 2) NavySidebar else GrayTextMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Horizontal Pager for swiping between Cash, Invest, and Fixed Pie Charts
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        when (page) {
                            0 -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Cash Accounts",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = NavySidebar
                                        )
                                        Text(
                                            text = "$symbol${"%,.2f".format(bucketSums["cash"] ?: 0.0)}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1F4E79)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    FinancialPieChart(
                                        slices = cashSlices,
                                        emptyLabel = "No Cash Accounts",
                                        currencySymbol = symbol
                                    )
                                }
                            }
                            1 -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Investment Accounts",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = NavySidebar
                                        )
                                        Text(
                                            text = "$symbol${"%,.2f".format(bucketSums["invest"] ?: 0.0)}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF5F2A7A)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    FinancialPieChart(
                                        slices = investSlices,
                                        emptyLabel = "No Investment Accounts",
                                        currencySymbol = symbol
                                    )
                                }
                            }
                            2 -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Fixed Asset Accounts",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = NavySidebar
                                        )
                                        Text(
                                            text = "$symbol${"%,.2f".format(bucketSums["fixed"] ?: 0.0)}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF9C5600)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    FinancialPieChart(
                                        slices = fixedSlices,
                                        emptyLabel = "No Fixed Asset Accounts",
                                        currencySymbol = symbol
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Page Indicator Dots
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until 3) {
                            val activeColor = categoriesMeta[i].third
                            val isCurrent = pagerState.currentPage == i
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(if (isCurrent) 8.dp else 6.dp)
                                    .background(
                                        if (isCurrent) activeColor else GrayBorder,
                                        CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }

        // Action Buttons Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        editingAccount = null
                        showAccountDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Account")
                }

                Button(
                    onClick = { showTransferDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleBalance),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Transfer")
                }
            }
        }

        // Account List Header and Sort Bar
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Accounts (${sortedAccounts.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavySidebar
                    )

                    // Sort Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("type" to "Type", "amount" to "Amount", "name" to "Name").forEach { (key, label) ->
                            val isActive = accountSortBy == key
                            FilterChip(
                                selected = isActive,
                                onClick = {
                                    if (isActive) {
                                        accountSortAsc = !accountSortAsc
                                    } else {
                                        accountSortBy = key
                                        accountSortAsc = (key != "amount") // default amount descending
                                    }
                                },
                                label = {
                                    Text(
                                        text = "$label${if (isActive) (if (accountSortAsc) " ▲" else " ▼") else ""}",
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }
            }
        }

        items(sortedAccounts.size) { idx ->
            val account = sortedAccounts[idx]
            val conv = CurrencyConverter.convert(account.balance, account.currency, baseCurrency)
            val typeColor = when (account.accountType.lowercase()) {
                "invest" -> Color(0xFF5F2A7A)
                "fixed" -> Color(0xFF9C5600)
                else -> Color(0xFF1F4E79)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = account.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NavySidebar
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = typeColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = account.accountType.uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = typeColor,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${CurrencyConverter.symbol(account.currency)}${"%,.2f".format(account.balance)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (account.balance >= 0) GreenIncome else RedExpense
                        )
                        Text(
                            text = "≈ $symbol${"%,.2f".format(conv)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryBlue
                        )
                        if (account.notes.isNotBlank()) {
                            Text(
                                text = account.notes,
                                style = MaterialTheme.typography.labelSmall,
                                color = GrayTextSecondary,
                                maxLines = 1
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                editingAccount = account
                                showAccountDialog = true
                            }
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = OrangeEarmarked)
                        }
                        IconButton(
                            onClick = { account.id?.let { onDeleteAccount(it) } }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RedExpense)
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Account Dialog
    if (showAccountDialog) {
        var name by remember { mutableStateOf(editingAccount?.name ?: "") }
        var currency by remember { mutableStateOf(editingAccount?.currency ?: baseCurrency) }
        var balance by remember { mutableStateOf(editingAccount?.balance?.toString() ?: "0.00") }
        var type by remember { mutableStateOf(editingAccount?.accountType ?: "cash") }
        var notes by remember { mutableStateOf(editingAccount?.notes ?: "") }
        var keepBook by remember { mutableStateOf(false) }
        var bookAmount by remember { mutableStateOf("") }
        var bookType by remember { mutableStateOf("income") }
        var bookDesc by remember { mutableStateOf("") }
        var currencyExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text(if (editingAccount != null) "Edit Account" else "Add Account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Account Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Currency Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = "$currency (${CurrencyConverter.symbol(currency)})",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Currency") },
                            trailingIcon = {
                                IconButton(onClick = { currencyExpanded = !currencyExpanded }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Select Currency", tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currencyExpanded = true }
                        )
                        DropdownMenu(
                            expanded = currencyExpanded,
                            onDismissRequest = { currencyExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.75f).heightIn(max = 260.dp)
                        ) {
                            currencies.forEach { c ->
                                DropdownMenuItem(
                                    text = {
                                        Text("$c (${CurrencyConverter.symbol(c)})", fontWeight = if (c == currency) FontWeight.Bold else FontWeight.Normal)
                                    },
                                    onClick = {
                                        currency = c
                                        currencyExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = balance,
                        onValueChange = { balance = it },
                        label = { Text("Balance (${CurrencyConverter.symbol(currency)})") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Account Type
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Account Type", style = MaterialTheme.typography.labelSmall, color = GrayTextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("cash" to "Cash", "invest" to "Invest", "fixed" to "Fixed").forEach { (k, label) ->
                                FilterChip(
                                    selected = type == k,
                                    onClick = { type = k },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (editingAccount == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = keepBook, onCheckedChange = { keepBook = it })
                            Text("Keep Book (record initial transaction)", fontSize = 12.sp)
                        }
                        if (keepBook) {
                            OutlinedTextField(
                                value = bookAmount,
                                onValueChange = { bookAmount = it },
                                label = { Text("Book Amount") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = bookType == "income",
                                    onClick = { bookType = "income" },
                                    label = { Text("Income") }
                                )
                                FilterChip(
                                    selected = bookType == "expense",
                                    onClick = { bookType = "expense" },
                                    label = { Text("Expense") }
                                )
                            }
                            OutlinedTextField(
                                value = bookDesc,
                                onValueChange = { bookDesc = it },
                                label = { Text("Transaction Description") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val bal = balance.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank()) {
                        val acc = Account(
                            id = editingAccount?.id,
                            name = name.trim(),
                            currency = currency,
                            balance = bal,
                            accountType = type,
                            notes = notes.trim()
                        )
                        val bTx = if (keepBook && (bookAmount.toDoubleOrNull() ?: 0.0) != 0.0) {
                            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            TransactionItem(
                                amount = kotlin.math.abs(bookAmount.toDoubleOrNull() ?: 0.0),
                                type = bookType,
                                description = bookDesc.trim(),
                                date = today,
                                currency = currency
                            )
                        } else null

                        if (editingAccount != null) {
                            onUpdateAccount(acc)
                        } else {
                            onAddAccount(acc, bTx)
                        }
                        showAccountDialog = false
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccountDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Transfer Dialog
    if (showTransferDialog) {
        var fromAccount by remember { mutableStateOf(accounts.firstOrNull()) }
        var toAccount by remember { mutableStateOf<Account?>(null) }
        var transferAmount by remember { mutableStateOf("") }
        var transferErr by remember { mutableStateOf<String?>(null) }
        var fromDropdownExpanded by remember { mutableStateOf(false) }
        var toDropdownExpanded by remember { mutableStateOf(false) }

        val eligibleTargets = accounts.filter { it.id != fromAccount?.id && it.currency == fromAccount?.currency }

        AlertDialog(
            onDismissRequest = { showTransferDialog = false },
            title = { Text("Transfer Between Accounts") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Source Account Dropdown
                    Text("Source Account (From):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = fromAccount?.let { "${it.name} (${it.currency}) - ${CurrencyConverter.symbol(it.currency)}${"%,.2f".format(it.balance)}" } ?: "Select Source Account",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { fromDropdownExpanded = !fromDropdownExpanded }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Choose source", tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { fromDropdownExpanded = true }
                        )
                        DropdownMenu(
                            expanded = fromDropdownExpanded,
                            onDismissRequest = { fromDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f).heightIn(max = 260.dp)
                        ) {
                            accounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${acc.name} (${acc.currency}) - ${CurrencyConverter.symbol(acc.currency)}${"%,.2f".format(acc.balance)}", fontSize = 13.sp)
                                    },
                                    onClick = {
                                        fromAccount = acc
                                        toAccount = null
                                        fromDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Destination Account Dropdown
                    Text("Destination Account (To):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    if (eligibleTargets.isEmpty()) {
                        Text("No other accounts found with matching currency (${fromAccount?.currency}).", color = GrayTextMuted, fontSize = 11.sp)
                    } else {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = toAccount?.let { "${it.name} (${it.currency}) - ${CurrencyConverter.symbol(it.currency)}${"%,.2f".format(it.balance)}" } ?: "Select Destination Account",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = { toDropdownExpanded = !toDropdownExpanded }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Choose destination", tint = PurpleBalance, modifier = Modifier.size(18.dp))
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { toDropdownExpanded = true }
                            )
                            DropdownMenu(
                                expanded = toDropdownExpanded,
                                onDismissRequest = { toDropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.85f).heightIn(max = 260.dp)
                            ) {
                                eligibleTargets.forEach { acc ->
                                    DropdownMenuItem(
                                        text = {
                                            Text("${acc.name} (${acc.currency}) - ${CurrencyConverter.symbol(acc.currency)}${"%,.2f".format(acc.balance)}", fontSize = 13.sp)
                                        },
                                        onClick = {
                                            toAccount = acc
                                            toDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = transferAmount,
                        onValueChange = { transferAmount = it },
                        label = { Text("Transfer Amount (${fromAccount?.currency ?: ""})") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (transferErr != null) {
                        Text(transferErr ?: "", color = RedExpense, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val fId = fromAccount?.id
                        val tId = toAccount?.id
                        val amt = transferAmount.toDoubleOrNull() ?: 0.0
                        if (fId != null && tId != null && amt > 0.0) {
                            onTransfer(fId, tId, amt) { success, err ->
                                if (success) {
                                    showTransferDialog = false
                                } else {
                                    transferErr = err
                                }
                            }
                        } else {
                            transferErr = "Please select both accounts and enter a valid amount."
                        }
                    }
                ) {
                    Text("Transfer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTransferDialog = false }) { Text("Cancel") }
            }
        )
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
