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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Category
import com.example.sync.CurrencyConverter
import com.example.ui.theme.*

@Composable
fun SettingsScreen(
    baseCurrency: String,
    currencies: List<String>,
    categories: List<Category>,
    isEncrypted: Boolean,
    onSetBaseCurrency: (String) -> Unit,
    onAddCurrency: (String) -> Unit,
    onRemoveCurrency: (String) -> Unit,
    onRefreshRates: () -> Unit,
    onAddCategory: (Category) -> Unit,
    onUpdateCategory: (Category) -> Unit,
    onDeleteCategory: (Long) -> Unit,
    onApplyEncryption: (Boolean, String, (Boolean, String?) -> Unit) -> Unit,
    onClearAllData: () -> Unit
) {
    var showAddCurrencyDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var showEncryptionDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GrayBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section 1: Base Currency & Multi-Currency Management
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        text = "Base Currency",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavySidebar
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val supportedBase = listOf("HKD", "USD", "CNY", "EUR", "JPY", "GBP", "CAD", "AUD", "SGD", "TWD")
                    val scroll = rememberScrollState()
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        supportedBase.forEach { c ->
                            FilterChip(
                                selected = baseCurrency == c,
                                onClick = { onSetBaseCurrency(c) },
                                label = { Text(c, fontSize = 11.sp) }
                            )
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 10.dp), color = GrayBorder.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Currencies & Exchange Rates", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NavySidebar)
                        Row {
                            IconButton(onClick = { onRefreshRates() }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh rates", tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { showAddCurrencyDialog = true }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Add, contentDescription = "Add currency", tint = GreenIncome, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    currencies.forEach { cur ->
                        val rate = CurrencyConverter.getRate(cur, baseCurrency)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$cur (${CurrencyConverter.symbol(cur)})",
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                color = NavySidebar
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "1 $cur = %.4f $baseCurrency".format(rate),
                                    fontSize = 11.sp,
                                    color = GrayTextSecondary
                                )
                                if (cur != baseCurrency) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { onRemoveCurrency(cur) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = RedExpense, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Categories Management
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
                            text = "Categories (${categories.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = NavySidebar
                        )
                        Button(
                            onClick = {
                                editingCategory = null
                                showCategoryDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenIncome),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Category", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val expCats = categories.filter { it.type == "expense" }
                    val incCats = categories.filter { it.type == "income" }

                    Text("Expense Categories", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = RedExpense)
                    CategoryChipsGrid(
                        categories = expCats,
                        onEdit = {
                            editingCategory = it
                            showCategoryDialog = true
                        },
                        onDelete = { it.id?.let { id -> onDeleteCategory(id) } }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Income Categories", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = GreenIncome)
                    CategoryChipsGrid(
                        categories = incCats,
                        onEdit = {
                            editingCategory = it
                            showCategoryDialog = true
                        },
                        onDelete = { it.id?.let { id -> onDeleteCategory(id) } }
                    )
                }
            }
        }

        // Section 4: Security & Encryption (AES-256)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        text = "Database Security",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavySidebar
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isEncrypted) "Encryption: Enabled (AES-256-GCM)" else "Encryption: Disabled (Plaintext)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = if (isEncrypted) GreenIncome else OrangeDark
                            )
                            Text(
                                text = if (isEncrypted) "Local database and drive backup are password protected." else "Data is stored without encryption.",
                                fontSize = 10.sp,
                                color = GrayTextMuted
                            )
                        }

                        Button(
                            onClick = { showEncryptionDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isEncrypted) RedExpense else PrimaryBlue),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(if (isEncrypted) "Change / Disable" else "Enable AES-256", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Section 5: Danger Zone
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = RedLight.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("Danger Zone", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = RedDark)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Clear all accounts, transactions, debts, funds and notes from this device.", fontSize = 10.sp, color = GrayTextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = { showClearDataDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = RedExpense),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear All Data", fontSize = 11.sp)
                    }
                }
            }
        }
    }

    // Add Currency Dialog
    if (showAddCurrencyDialog) {
        var curCode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCurrencyDialog = false },
            title = { Text("Add Currency") },
            text = {
                OutlinedTextField(
                    value = curCode,
                    onValueChange = { curCode = it.uppercase() },
                    label = { Text("Currency Code (e.g. CAD, JPY, EUR)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (curCode.isNotBlank()) {
                        onAddCurrency(curCode.trim())
                        showAddCurrencyDialog = false
                    }
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCurrencyDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Add / Edit Category Dialog
    if (showCategoryDialog) {
        var name by remember { mutableStateOf(editingCategory?.name ?: "") }
        var type by remember { mutableStateOf(editingCategory?.type ?: "expense") }
        var icon by remember { mutableStateOf(editingCategory?.icon ?: "📌") }
        var color by remember { mutableStateOf(editingCategory?.color ?: "#1F4E79") }

        val emojiPresets = listOf("🍔", "🚗", "🏠", "🎮", "✈️", "👔", "💊", "🎓", "💰", "📈", "🎁", "🛍️", "🔧", "📱", "🏷️")

        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text(if (editingCategory != null) "Edit Category" else "Add Category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = type == "expense", onClick = { type = "expense" }, label = { Text("Expense") })
                        FilterChip(selected = type == "income", onClick = { type = "income" }, label = { Text("Income") })
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Category Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = icon,
                        onValueChange = { icon = it },
                        label = { Text("Icon (Emoji)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        emojiPresets.forEach { e ->
                            Surface(
                                modifier = Modifier.clickable { icon = e }.padding(4.dp),
                                shape = CircleShape,
                                color = GrayBackground
                            ) {
                                Text(e, modifier = Modifier.padding(6.dp), fontSize = 16.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        val c = Category(
                            id = editingCategory?.id,
                            name = name.trim(),
                            type = type,
                            icon = icon.ifBlank { "📌" },
                            color = color
                        )
                        if (editingCategory != null) {
                            onUpdateCategory(c)
                        } else {
                            onAddCategory(c)
                        }
                        showCategoryDialog = false
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCategoryDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Encryption Settings Dialog
    if (showEncryptionDialog) {
        var enable by remember { mutableStateOf(!isEncrypted) }
        var password by remember { mutableStateOf("") }
        var encError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showEncryptionDialog = false },
            title = { Text("Database Encryption (AES-256)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = enable, onCheckedChange = { enable = it })
                        Text("Enable AES-256 Encryption", fontSize = 13.sp)
                    }

                    if (enable) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Master Password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("Disabling encryption will store database in standard SQLite format.", color = RedDark, fontSize = 11.sp)
                    }

                    if (encError != null) {
                        Text(encError ?: "", color = RedExpense, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onApplyEncryption(enable, password) { success, msg ->
                        if (success) {
                            showEncryptionDialog = false
                        } else {
                            encError = msg
                        }
                    }
                }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEncryptionDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Clear All Data Confirmation Dialog
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Confirm Clear All Data") },
            text = { Text("Are you sure you want to delete all financial records? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllData()
                        showClearDataDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedExpense)
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun CategoryChipsGrid(
    categories: List<Category>,
    onEdit: (Category) -> Unit,
    onDelete: (Category) -> Unit
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(scroll).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        categories.forEach { c ->
            InputChip(
                selected = true,
                onClick = { onEdit(c) },
                label = { Text("${c.icon} ${c.name}", fontSize = 11.sp) },
                trailingIcon = {
                    IconButton(onClick = { onDelete(c) }, modifier = Modifier.size(16.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(12.dp))
                    }
                }
            )
        }
    }
}
