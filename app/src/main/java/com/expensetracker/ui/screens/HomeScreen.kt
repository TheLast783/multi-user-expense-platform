package com.expensetracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expensetracker.data.ExpenseEntity
import com.expensetracker.ui.theme.*
import com.expensetracker.ui.viewmodels.MainViewModel
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(viewModel: MainViewModel = viewModel()) {
    var showDialog by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }
    
    val transactions by viewModel.expenses.collectAsState()

    var totalCredited by remember { mutableStateOf(0.0) }
    var totalDebited by remember { mutableStateOf(0.0) }
    var netBalance by remember { mutableStateOf(0.0) }

    LaunchedEffect(transactions) {
        var credited = 0.0
        var debited = 0.0
        for (tx in transactions) {
            val amountStr = tx.amount.replace(Regex("[^0-9.]"), "")
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            if (tx.type == "Credited" || tx.type == "Received") {
                credited += amount
            } else {
                debited += amount
            }
        }
        totalCredited = credited
        totalDebited = debited
        netBalance = credited - debited
    }

    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Actual Total Balance", color = TextSecondary, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${if (netBalance >= 0) "+" else "-"} ₹${String.format(java.util.Locale.US, "%.2f", kotlin.math.abs(netBalance))}",
                        color = if (netBalance >= 0) AscentColor else DescentColor,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Total Received", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("+ ₹${String.format(java.util.Locale.US, "%.2f", totalCredited)}", color = AscentColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Total Sent", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("- ₹${String.format(java.util.Locale.US, "%.2f", totalDebited)}", color = DescentColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Recent Transactions (From DB)",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(500)) + slideInVertically(animationSpec = tween(500))
            ) {
                if (transactions.isEmpty()) {
                    Text("No transactions yet! Send a bank SMS to test.", color = TextSecondary)
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(transactions) { tx ->
                            TransactionItem(tx)
                        }
                    }
                }
            }
        }

    }
}

@Composable
fun TransactionItem(tx: ExpenseEntity) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { /* Show details */ },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(DarkPrimary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.AttachMoney, contentDescription = null, tint = DarkSecondary)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val displayTag = if(tx.type == "Debited") "Sent" else "Received"
                    val statusTag = if (tx.status == "Sent") "[Archived]" else "[${tx.status}]"
                    Text(text = "[$displayTag] ${tx.bank} $statusTag", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = if(tx.status == "Sent") Color(0xFFB794F4) else Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Assigned: ${tx.assignedContact}", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Text(text = "${tx.date} | ID: ${tx.paymentId ?: "N/A"}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                val amountColor = if (tx.status == "Sent") Color(0xFFB794F4) else { if (tx.type == "Debited" || tx.type == "Sent") DescentColor else AscentColor }
                Text(
                    text = tx.amount,
                    style = MaterialTheme.typography.bodyLarge,
                    color = amountColor,
                    fontWeight = FontWeight.Bold
                )
            }
            if (tx.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Note: ${tx.note}", style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
            }
        }
    }
}
