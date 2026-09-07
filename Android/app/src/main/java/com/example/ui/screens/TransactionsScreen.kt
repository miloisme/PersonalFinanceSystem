package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
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
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TransactionsScreen(
    baseCurrency: String,
    transactions: List<TransactionItem>,
    accounts: List<Account>,
    categories: List<Category>,
    currencies: List<String>,
    filterConditions: List<FilterCondition>,
    onAddCondition: (FilterCondition) -> Unit,
    onRemoveCondition: (Int) -> Unit,
    onClearConditions: () -> Unit,
    onAddTransaction: (TransactionItem) -> Unit,
    onUpdateTransaction: (TransactionItem) -> Unit,
    onDeleteTransaction: (Long) -> Unit
) {
    val symbol = CurrencyConverter.symbol(baseCurrency)
    val catMap = remember(categories) { categories.associateBy { it.id } }
    val accMap = remember(accounts) { accounts.associateBy { it.id } }

    // Filter transactions
    val filteredTransactions = remember(transactions, filterConditions, catMap, accMap) {
        transactions.filter { tx ->
            filterConditions.all { cond ->
                matchCondition(tx, cond, catMap, accMap)
            }
        }
    }

    // Sort transactions
    var sortBy by remember { mutableStateOf("date") }
    var sortAsc by remember { mutableStateOf(false) }

    val displayTransactions = remember(filteredTransactions, sortBy, sortAsc) {
        val sorted = when (sortBy) {
            "date" -> filteredTransactions.sortedBy { it.date }
            "amount" -> filteredTransactions.sortedBy { it.baseAmount }
            "type" -> filteredTransactions.sortedWith(compareBy({ it.type }, { it.baseAmount }))
            else -> filteredTransactions
        }
        if (!sortAsc) sorted.reversed() else sorted
    }

    val totalIncome = remember(filteredTransactions) {
        filteredTransactions.filter { it.type == "income" }.sumOf { it.baseAmount }
    }
    val totalExpense = remember(filteredTransactions) {
        filteredTransactions.filter { it.type == "expense" }.sumOf { it.baseAmount }
    }
    val netBalance = totalIncome - totalExpense

    // Dialog state
    var showTxDialog by remember { mutableStateOf(false) }
    var editingTx by remember { mutableStateOf<TransactionItem?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GrayBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Summary Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Records", style = MaterialTheme.typography.labelSmall, color = GrayTextSecondary)
                        Text("${filteredTransactions.size}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NavySidebar)
                    }
                    Column {
                        Text("Income", style = MaterialTheme.typography.labelSmall, color = GreenIncome)
                        Text("$symbol${"%,.2f".format(totalIncome)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GreenIncome)
                    }
                    Column {
                        Text("Expense", style = MaterialTheme.typography.labelSmall, color = RedExpense)
                        Text("$symbol${"%,.2f".format(totalExpense)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = RedExpense)
                    }
                    Column {
                        Text("Net", style = MaterialTheme.typography.labelSmall, color = PurpleBalance)
                        Text(
                            text = "$symbol${"%,.2f".format(netBalance)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (netBalance >= 0) GreenIncome else RedExpense
                        )
                    }
                }
            }
        }

        // Filter Bar & Action Buttons
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FilterList, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Filters (${filterConditions.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NavySidebar)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(
                                onClick = { showFilterDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("+ Filter", fontSize = 11.sp)
                            }
                            if (filterConditions.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { onClearConditions() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Clear", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Active Filter Chips
                    if (filterConditions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val scrollState = rememberScrollState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(scrollState),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            filterConditions.forEachIndexed { idx, cond ->
                                InputChip(
                                    selected = true,
                                    onClick = { onRemoveCondition(idx) },
                                    label = { Text("${cond.field} ${cond.op} ${cond.value}", fontSize = 11.sp) },
                                    trailingIcon = {
                                        Icon(Icons.Default.Close, contentDescription = "Remove filter", modifier = Modifier.size(14.dp))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add Transaction Button
        item {
            Button(
                onClick = {
                    editingTx = null
                    showTxDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("add_transaction_button"),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Transaction", fontWeight = FontWeight.SemiBold)
            }
        }

        // Transaction List Header & Sort Chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transactions (${displayTransactions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NavySidebar
                )

                // Sort Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("date" to "Date", "amount" to "Amount", "type" to "Type").forEach { (key, label) ->
                        val isActive = sortBy == key
                        FilterChip(
                            selected = isActive,
                            onClick = {
                                if (isActive) {
                                    sortAsc = !sortAsc
                                } else {
                                    sortBy = key
                                    sortAsc = (key != "date" && key != "amount") // date & amount default desc
                                }
                            },
                            label = {
                                Text(
                                    text = "$label${if (isActive) (if (sortAsc) " ▲" else " ▼") else ""}",
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

        // Transaction List Items
        if (displayTransactions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No transactions found.", color = GrayTextMuted, fontSize = 14.sp)
                }
            }
        } else {
            items(displayTransactions.size) { idx ->
                val tx = displayTransactions[idx]
                val cat = catMap[tx.categoryId]
                val acc = accMap[tx.accountId]
                val isIncome = tx.type == "income"

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
                        // Category Icon & Type
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (isIncome) GreenLight else RedLight,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cat?.icon ?: if (isIncome) "💰" else "🛒",
                                fontSize = 20.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Details
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = cat?.name ?: "Uncategorized",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = NavySidebar
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                if (acc != null) {
                                    Surface(
                                        color = GrayBackground,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = acc.name,
                                            fontSize = 10.sp,
                                            color = GrayTextSecondary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            if (tx.description.isNotBlank()) {
                                Text(
                                    text = tx.description,
                                    fontSize = 11.sp,
                                    color = GrayTextSecondary,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = tx.date,
                                fontSize = 10.sp,
                                color = GrayTextMuted
                            )
                        }

                        // Amount & Actions
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${if (isIncome) "+" else "-"}${CurrencyConverter.symbol(tx.currency)}${"%,.2f".format(tx.amount)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isIncome) GreenIncome else RedExpense
                            )
                            if (tx.currency != baseCurrency) {
                                Text(
                                    text = "≈ $symbol${"%,.2f".format(tx.baseAmount)}",
                                    fontSize = 10.sp,
                                    color = GrayTextMuted
                                )
                            }
                            Row {
                                IconButton(
                                    onClick = {
                                        editingTx = tx
                                        showTxDialog = true
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = OrangeEarmarked, modifier = Modifier.size(14.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { tx.id?.let { onDeleteTransaction(it) } },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RedExpense, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Transaction Dialog
    if (showTxDialog) {
        var type by remember { mutableStateOf(editingTx?.type ?: "expense") }
        var amount by remember { mutableStateOf(editingTx?.amount?.toString() ?: "") }
        var currency by remember { mutableStateOf(editingTx?.currency ?: baseCurrency) }
        var currencyExpanded by remember { mutableStateOf(false) }
        var selectedAccount by remember { mutableStateOf(accounts.find { it.id == editingTx?.accountId } ?: accounts.firstOrNull()) }
        var selectedCategory by remember { mutableStateOf(categories.find { it.id == editingTx?.categoryId } ?: categories.firstOrNull { it.type == type }) }
        var date by remember { mutableStateOf(editingTx?.date ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
        var description by remember { mutableStateOf(editingTx?.description ?: "") }

        val rate = remember(currency, baseCurrency) {
            CurrencyConverter.getRate(currency, baseCurrency)
        }

        AlertDialog(
            onDismissRequest = { showTxDialog = false },
            title = { Text(if (editingTx != null) "Edit Transaction" else "Add Transaction") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Type selector
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = type == "expense",
                            onClick = {
                                type = "expense"
                                selectedCategory = categories.firstOrNull { it.type == "expense" }
                            },
                            label = { Text("Expense") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = type == "income",
                            onClick = {
                                type = "income"
                                selectedCategory = categories.firstOrNull { it.type == "income" }
                            },
                            label = { Text("Income") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount (${CurrencyConverter.symbol(currency)})") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Account selection (selecting an account sets a default
                    // currency, but only if the user hasn't overridden it manually)
                    Text("Account:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    val accScroll = rememberScrollState()
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(accScroll),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        accounts.forEach { a ->
                            FilterChip(
                                selected = selectedAccount?.id == a.id,
                                onClick = {
                                    selectedAccount = a
                                    if (editingTx == null && currency == baseCurrency) {
                                        currency = a.currency
                                    }
                                },
                                label = { Text(a.name, fontSize = 11.sp) }
                            )
                        }
                    }

                    // Currency selection
                    Text("Currency:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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

                    // Category selection
                    Text("Category:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    val catScroll = rememberScrollState()
                    val eligibleCats = categories.filter { it.type == type }
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(catScroll),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        eligibleCats.forEach { c ->
                            FilterChip(
                                selected = selectedCategory?.id == c.id,
                                onClick = { selectedCategory = c },
                                label = { Text("${c.icon} ${c.name}", fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val numAmt = amount.toDoubleOrNull() ?: 0.0
                    val baseAmt = numAmt * rate
                    if (currency != baseCurrency) {
                        Text(
                            text = "Exchange Rate: 1 $currency = $rate $baseCurrency → Converted: $symbol${"%,.2f".format(baseAmt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryBlue
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val numAmt = amount.toDoubleOrNull() ?: 0.0
                    if (numAmt > 0.0) {
                        val baseAmt = numAmt * rate
                        val item = TransactionItem(
                            id = editingTx?.id,
                            accountId = selectedAccount?.id,
                            categoryId = selectedCategory?.id,
                            amount = numAmt,
                            currency = currency,
                            exchangeRate = rate,
                            baseAmount = baseAmt,
                            type = type,
                            date = date.trim(),
                            description = description.trim()
                        )
                        if (editingTx != null) {
                            onUpdateTransaction(item)
                        } else {
                            onAddTransaction(item)
                        }
                        showTxDialog = false
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTxDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Add Filter Dialog
    if (showFilterDialog) {
        var field by remember { mutableStateOf("Category") }
        var op by remember { mutableStateOf("=") }
        var value by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("Add Filter Condition") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Field:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Category", "Account", "Type", "Description", "Amount", "Date").forEach { f ->
                            FilterChip(selected = field == f, onClick = { field = f }, label = { Text(f, fontSize = 10.sp) })
                        }
                    }

                    Text("Operator:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("=", "!=", "contains", ">", "<").forEach { o ->
                            FilterChip(selected = op == o, onClick = { op = o }, label = { Text(o, fontSize = 10.sp) })
                        }
                    }

                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text("Value to match") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (value.isNotBlank()) {
                        onAddCondition(FilterCondition(field, op, value.trim()))
                        showFilterDialog = false
                    }
                }) {
                    Text("Add Filter")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFilterDialog = false }) { Text("Cancel") }
            }
        )
    }
}

fun matchCondition(
    tx: TransactionItem,
    cond: FilterCondition,
    catMap: Map<Long?, Category>,
    accMap: Map<Long?, Account>
): Boolean {
    val fieldVal: String = when (cond.field.lowercase()) {
        "category" -> catMap[tx.categoryId]?.name ?: ""
        "account" -> accMap[tx.accountId]?.name ?: ""
        "type" -> tx.type
        "description" -> tx.description
        "amount" -> tx.amount.toString()
        "date" -> tx.date
        else -> ""
    }

    return when (cond.op) {
        "=" -> fieldVal.equals(cond.value, ignoreCase = true)
        "!=" -> !fieldVal.equals(cond.value, ignoreCase = true)
        "contains" -> fieldVal.contains(cond.value, ignoreCase = true)
        ">" -> (fieldVal.toDoubleOrNull() ?: 0.0) > (cond.value.toDoubleOrNull() ?: 0.0)
        "<" -> (fieldVal.toDoubleOrNull() ?: 0.0) < (cond.value.toDoubleOrNull() ?: 0.0)
        else -> true
    }
}
