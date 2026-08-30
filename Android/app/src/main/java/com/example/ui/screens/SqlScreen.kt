package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.SqlResult
import com.example.ui.theme.*

@Composable
fun SqlScreen(
    sqlResult: SqlResult,
    onExecuteSql: (String) -> Unit
) {
    var sqlText by remember { mutableStateOf("SELECT id, name, currency, balance, account_type FROM accounts;") }

    val snippets = listOf(
        "Accounts" to "SELECT id, name, currency, balance, account_type FROM accounts;",
        "Categories" to "SELECT id, name, type, icon FROM categories;",
        "Transactions" to "SELECT id, amount, currency, base_amount, type, date, description FROM transactions ORDER BY date DESC LIMIT 25;",
        "Debts" to "SELECT id, name, debtor, total_amount, paid_amount, due_date FROM debts;",
        "Settings" to "SELECT * FROM settings;"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GrayBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Snippet Chips
        item {
            val scroll = rememberScrollState()
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                snippets.forEach { (label, q) ->
                    SuggestionChip(
                        onClick = { sqlText = q },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
        }

        // SQL Input Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        text = "SQL Query Console",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavySidebar
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = sqlText,
                        onValueChange = { sqlText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        placeholder = { Text("Enter SQL query...") }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (sqlText.isNotBlank()) {
                                onExecuteSql(sqlText.trim())
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Execute")
                    }
                }
            }
        }

        // Results Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        text = "Query Result",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NavySidebar
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    when (sqlResult) {
                        is SqlResult.Empty -> {
                            Text("Execute a query to see results.", color = GrayTextMuted, fontSize = 12.sp)
                        }
                        is SqlResult.ExecutionSuccess -> {
                            Surface(color = GreenLight, shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = "Success: ${sqlResult.message}",
                                    color = Color(0xFF1E8449),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        is SqlResult.Error -> {
                            Surface(color = RedLight, shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = "Error: ${sqlResult.message}",
                                    color = RedDark,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        is SqlResult.QuerySuccess -> {
                            val res = sqlResult
                            Text("Returned ${res.rows.size} row(s)", fontSize = 11.sp, color = GrayTextSecondary)
                            Spacer(modifier = Modifier.height(6.dp))

                            val tableScroll = rememberScrollState()
                            Column(modifier = Modifier.fillMaxWidth().horizontalScroll(tableScroll)) {
                                // Header
                                Row(
                                    modifier = Modifier
                                        .background(Color(0xFFF1F3F5), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 6.dp)
                                ) {
                                    res.columns.forEach { col ->
                                        Text(
                                            text = col,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = NavySidebar,
                                            modifier = Modifier.width(110.dp)
                                        )
                                    }
                                }

                                // Data rows
                                res.rows.forEach { row ->
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        row.forEach { cell ->
                                            Text(
                                                text = cell ?: "NULL",
                                                fontSize = 11.sp,
                                                color = if (cell == null) GrayTextMuted else NavySidebar,
                                                modifier = Modifier.width(110.dp),
                                                maxLines = 2
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
