package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.example.data.model.DebtItem
import com.example.sync.CurrencyConverter
import com.example.ui.components.FinancialPieChart
import com.example.ui.components.PieSliceData
import com.example.ui.components.formatCompact
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DebtsScreen(
    baseCurrency: String,
    debts: List<DebtItem>,
    currencies: List<String>,
    onAddDebt: (DebtItem) -> Unit,
    onUpdateDebt: (DebtItem) -> Unit,
    onDeleteDebt: (Long) -> Unit
) {
    val symbol = CurrencyConverter.symbol(baseCurrency)
    val todayIso = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    // Split debts vs credits
    val myDebts = remember(debts) { debts.filter { it.debtor == "me" } }
    val myCredits = remember(debts) { debts.filter { it.debtor == "other" } }

    val totalDebtRemaining = remember(myDebts, baseCurrency) {
        myDebts.filter { !it.completed }
            .sumOf { CurrencyConverter.convert(it.totalAmount - it.paidAmount, it.currency, baseCurrency) }
    }
    val totalCreditRemaining = remember(myCredits, baseCurrency) {
        myCredits.filter { !it.completed }
            .sumOf { CurrencyConverter.convert(it.totalAmount - it.paidAmount, it.currency, baseCurrency) }
    }

    var showDebtDialog by remember { mutableStateOf(false) }
    var editingDebt by remember { mutableStateOf<DebtItem?>(null) }
    var defaultDebtor by remember { mutableStateOf("me") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GrayBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Summary Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Debt (I Owe)", style = MaterialTheme.typography.labelSmall, color = RedDark, fontWeight = FontWeight.Bold)
                        Text("$symbol${"%,.2f".format(totalDebtRemaining)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = RedExpense)
                        Text("${myDebts.count { !it.completed }} active loans", fontSize = 10.sp, color = GrayTextMuted)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Receivable (Owed to Me)", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1E8449), fontWeight = FontWeight.Bold)
                        Text("$symbol${"%,.2f".format(totalCreditRemaining)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = GreenIncome)
                        Text("${myCredits.count { !it.completed }} active credits", fontSize = 10.sp, color = GrayTextMuted)
                    }
                }
            }
        }

        // Composition Pies
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        text = "Debt Composition (I Owe)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = RedDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val palette = listOf(Color(0xFFC0392B), Color(0xFFE74C3C), Color(0xFFD35400), Color(0xFFE67E22), Color(0xFF9C5600))
                    val groupedDebts = myDebts.filter { !it.completed }
                        .groupBy { it.name.trim().ifBlank { "Unnamed" } }
                        .mapValues { entry ->
                            entry.value.sumOf { d ->
                                CurrencyConverter.convert(d.totalAmount - d.paidAmount, d.currency, baseCurrency)
                            }
                        }
                        .filter { it.value > 0.0 }

                    val debtSlices = groupedDebts.entries.mapIndexed { idx, (name, rem) ->
                        PieSliceData(name, rem, palette[idx % palette.size])
                    }

                    FinancialPieChart(
                        slices = debtSlices,
                        emptyLabel = "No Active Debts",
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
                        text = "Credit Composition (Owed to Me)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GreenIncome
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val palette = listOf(Color(0xFF27AE60), Color(0xFF2ECC71), Color(0xFF16A085), Color(0xFF1ABC9C), Color(0xFF2980B9))
                    val groupedCredits = myCredits.filter { !it.completed }
                        .groupBy { it.name.trim().ifBlank { "Unnamed" } }
                        .mapValues { entry ->
                            entry.value.sumOf { d ->
                                CurrencyConverter.convert(d.totalAmount - d.paidAmount, d.currency, baseCurrency)
                            }
                        }
                        .filter { it.value > 0.0 }

                    val creditSlices = groupedCredits.entries.mapIndexed { idx, (name, rem) ->
                        PieSliceData(name, rem, palette[idx % palette.size])
                    }

                    FinancialPieChart(
                        slices = creditSlices,
                        emptyLabel = "No Active Receivables",
                        currencySymbol = symbol
                    )
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
                        defaultDebtor = "me"
                        editingDebt = null
                        showDebtDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = RedDark),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Debt (I Owe)", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        defaultDebtor = "other"
                        editingDebt = null
                        showDebtDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenIncome),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Credit", fontSize = 12.sp)
                }
            }
        }

        // Section: Debts (I Owe)
        item {
            Text("Debts I Owe (${myDebts.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = RedDark)
        }
        items(myDebts.size) { idx ->
            DebtCardItem(
                debt = myDebts[idx],
                baseCurrency = baseCurrency,
                todayIso = todayIso,
                onEdit = {
                    editingDebt = it
                    showDebtDialog = true
                },
                onDelete = { it.id?.let { id -> onDeleteDebt(id) } },
                onToggleComplete = { d, comp -> onUpdateDebt(d.copy(completed = comp)) }
            )
        }

        // Section: Credits (Owed to Me)
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Receivable Owed to Me (${myCredits.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GreenIncome)
        }
        items(myCredits.size) { idx ->
            DebtCardItem(
                debt = myCredits[idx],
                baseCurrency = baseCurrency,
                todayIso = todayIso,
                onEdit = {
                    editingDebt = it
                    showDebtDialog = true
                },
                onDelete = { it.id?.let { id -> onDeleteDebt(id) } },
                onToggleComplete = { d, comp -> onUpdateDebt(d.copy(completed = comp)) }
            )
        }
    }

    // Add / Edit Debt Dialog
    if (showDebtDialog) {
        var name by remember { mutableStateOf(editingDebt?.name ?: "") }
        var debtor by remember { mutableStateOf(editingDebt?.debtor ?: defaultDebtor) }
        var totalAmount by remember { mutableStateOf(editingDebt?.totalAmount?.toString() ?: "") }
        var paidAmount by remember { mutableStateOf(editingDebt?.paidAmount?.toString() ?: "0") }
        var currency by remember { mutableStateOf(editingDebt?.currency ?: baseCurrency) }
        var dueDate by remember { mutableStateOf(editingDebt?.dueDate ?: "") }
        var interestRate by remember { mutableStateOf(editingDebt?.interestRate?.toString() ?: "0.0") }
        var completed by remember { mutableStateOf(editingDebt?.completed ?: false) }
        var notes by remember { mutableStateOf(editingDebt?.notes ?: "") }

        AlertDialog(
            onDismissRequest = { showDebtDialog = false },
            title = { Text(if (editingDebt != null) "Edit Record" else "Add Debt / Credit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = debtor == "me",
                            onClick = { debtor = "me" },
                            label = { Text("Debt (I Owe)") }
                        )
                        FilterChip(
                            selected = debtor == "other",
                            onClick = { debtor = "other" },
                            label = { Text("Credit (Owed to Me)") }
                        )
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name / Person / Institution") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = totalAmount,
                            onValueChange = { totalAmount = it },
                            label = { Text("Total Amount") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = paidAmount,
                            onValueChange = { paidAmount = it },
                            label = { Text("Paid Amount") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dueDate,
                            onValueChange = { dueDate = it },
                            label = { Text("Due Date (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.weight(1.2f)
                        )
                        OutlinedTextField(
                            value = interestRate,
                            onValueChange = { interestRate = it },
                            label = { Text("Interest %") },
                            singleLine = true,
                            modifier = Modifier.weight(0.8f)
                        )
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = completed, onCheckedChange = { completed = it })
                        Text("Mark as Completed (Fully settled)", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val tot = totalAmount.toDoubleOrNull() ?: 0.0
                    val paid = paidAmount.toDoubleOrNull() ?: 0.0
                    val rate = interestRate.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && tot > 0.0) {
                        val item = DebtItem(
                            id = editingDebt?.id,
                            name = name.trim(),
                            debtor = debtor,
                            totalAmount = tot,
                            paidAmount = paid,
                            currency = currency,
                            dueDate = dueDate.trim(),
                            interestRate = rate,
                            completed = completed,
                            notes = notes.trim()
                        )
                        if (editingDebt != null) {
                            onUpdateDebt(item)
                        } else {
                            onAddDebt(item)
                        }
                        showDebtDialog = false
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDebtDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun DebtCardItem(
    debt: DebtItem,
    baseCurrency: String,
    todayIso: String,
    onEdit: (DebtItem) -> Unit,
    onDelete: (DebtItem) -> Unit,
    onToggleComplete: (DebtItem, Boolean) -> Unit
) {
    val remaining = debt.totalAmount - debt.paidAmount
    val progress = if (debt.totalAmount > 0) (debt.paidAmount / debt.totalAmount).toFloat() else 0f
    val isOverdue = !debt.completed && debt.dueDate.isNotBlank() && debt.dueDate < todayIso
    val isReceivable = debt.debtor == "other"
    val symbol = CurrencyConverter.symbol(debt.currency)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                when {
                                    debt.completed -> GrayTextMuted
                                    isReceivable -> GreenIncome
                                    isOverdue -> RedExpense
                                    else -> PrimaryBlue
                                },
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = debt.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = NavySidebar
                    )
                    if (debt.completed) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(color = GrayBackground, shape = RoundedCornerShape(4.dp)) {
                            Text("DONE", fontSize = 9.sp, color = GrayTextMuted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }

                Row {
                    IconButton(onClick = { onEdit(debt) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = OrangeEarmarked, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = { onDelete(debt) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RedExpense, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isReceivable) GreenIncome else PrimaryBlue,
                trackColor = GrayBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Paid: $symbol${"%,.2f".format(debt.paidAmount)} / $symbol${"%,.2f".format(debt.totalAmount)} (%.0f%%)".format(progress * 100f),
                    fontSize = 10.sp,
                    color = GrayTextSecondary
                )
                Text(
                    text = "Remaining: $symbol${"%,.2f".format(remaining)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (isReceivable) GreenIncome else RedExpense
                )
            }

            if (debt.dueDate.isNotBlank() || debt.interestRate > 0.0) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (debt.dueDate.isNotBlank()) {
                        Text(
                            text = "Due: ${debt.dueDate} ${if (isOverdue) "(Overdue!)" else ""}",
                            fontSize = 10.sp,
                            color = if (isOverdue) RedExpense else GrayTextMuted
                        )
                    }
                    if (debt.interestRate > 0.0) {
                        Text("Rate: ${debt.interestRate}%", fontSize = 10.sp, color = GrayTextMuted)
                    }
                }
            }
        }
    }
}
