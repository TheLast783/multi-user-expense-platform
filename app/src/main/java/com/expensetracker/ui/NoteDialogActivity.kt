package com.expensetracker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.expensetracker.data.AppDatabase
import com.expensetracker.data.ExpenseEntity
import com.expensetracker.ui.theme.DarkPrimary
import com.expensetracker.ui.theme.DarkSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NoteDialogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val amount = intent.getStringExtra("AMOUNT") ?: ""
        val type = intent.getStringExtra("TYPE") ?: "Debited"
        val date = intent.getStringExtra("DATE") ?: ""
        val originalSms = intent.getStringExtra("ORIGINAL_SMS") ?: ""
        val paymentId = intent.getStringExtra("PAYMENT_ID") ?: ""
        val bank = intent.getStringExtra("BANK") ?: "Unknown Bank"

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(surface = DarkSurface, primary = DarkPrimary)) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = DarkSurface,
                    modifier = Modifier.padding(16.dp).wrapContentHeight()
                ) {
                    NoteDialogContent(
                        amount = amount,
                        type = type,
                        onSave = { note ->
                            saveTransaction(note)
                        },
                        onCancel = {
                            // Fix: Bug 4 & 5 - If dismissed, assign to User
                            saveTransaction("User")
                        }
                    )
                }
            }
        }
    }

    private fun saveTransaction(note: String) {
        val expenseId = intent.getLongExtra("EXPENSE_ID", -1L)
        val amount = intent.getStringExtra("AMOUNT") ?: ""
        val type = intent.getStringExtra("TYPE") ?: "Debited"
        val date = intent.getStringExtra("DATE") ?: ""
        val originalSms = intent.getStringExtra("ORIGINAL_SMS") ?: ""
        val paymentId = intent.getStringExtra("PAYMENT_ID") ?: ""
        val bank = intent.getStringExtra("BANK") ?: "Unknown Bank"

        val db = AppDatabase.getDatabase(applicationContext)
        val expenseDao = db.expenseDao()
        val ruleDao = db.contactRuleDao()
        
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val rules = ruleDao.getAllRulesSync()
            var assignedContact = "User"
            
            for (rule in rules) {
                if (rule.name.isNotBlank() && note.contains(rule.name.trim(), ignoreCase = true)) {
                    assignedContact = rule.name
                    break
                }
            }

            val amountNumeric = amount.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
            var finalStatus = if (type == "Credited") "Paid" else "Unpaid"
            var finalRemaining = amountNumeric
            val syncList = mutableListOf<com.expensetracker.data.network.ExpenseSyncPayload>()

            if (finalStatus == "Paid" && assignedContact != "User") {
                val unpaids = expenseDao.getUnpaidMatchesSync(assignedContact, note.trim())
                var paymentLeft = finalRemaining
                for (unpaid in unpaids) {
                    if (paymentLeft <= 0.0) break
                    if (unpaid.note == note.trim()) {
                        val updatedUnpaid = if (unpaid.remainingAmount <= paymentLeft) {
                            paymentLeft -= unpaid.remainingAmount
                            unpaid.copy(remainingAmount = 0.0, status = "Paid")
                        } else {
                            val newRemaining = unpaid.remainingAmount - paymentLeft
                            paymentLeft = 0.0
                            unpaid.copy(remainingAmount = newRemaining)
                        }
                        expenseDao.updateExpense(updatedUnpaid)
                        syncList.add(com.expensetracker.data.network.ExpenseSyncPayload(updatedUnpaid.title, updatedUnpaid.date, updatedUnpaid.amount, updatedUnpaid.isExpense, updatedUnpaid.originalSms, updatedUnpaid.paymentId, updatedUnpaid.assignedContact, updatedUnpaid.note, updatedUnpaid.type, updatedUnpaid.bank, updatedUnpaid.status, updatedUnpaid.remainingAmount))
                    }
                }
                finalRemaining = paymentLeft
                if (finalRemaining <= 0.0) finalStatus = "Paid"
            }

            val entity = ExpenseEntity(
                id = if (expenseId != -1L) expenseId.toInt() else 0,
                title = if(type == "Debited") "Expense" else "Income",
                date = date, amount = amount, isExpense = type == "Debited",
                originalSms = originalSms, paymentId = paymentId,
                note = note.trim(), type = type, assignedContact = assignedContact,
                bank = bank, status = finalStatus, remainingAmount = finalRemaining
            )
            
            if (expenseId != -1L) {
                expenseDao.updateExpense(entity)
            } else {
                expenseDao.insertExpense(entity)
            }
            
            syncList.add(com.expensetracker.data.network.ExpenseSyncPayload(entity.title, entity.date, entity.amount, entity.isExpense, entity.originalSms, entity.paymentId, entity.assignedContact, entity.note, entity.type, entity.bank, entity.status, entity.remainingAmount))
            
            try {
                val prefs = getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
                val email = prefs.getString("user_email", "") ?: ""
                if (email.isNotEmpty()) {
                    com.expensetracker.data.network.RetrofitClient.api.syncExpenses(email, syncList)
                }
            } catch (e: Exception) { e.printStackTrace() }

            withContext(Dispatchers.Main) {
                finish()
            }
        }
    }
}

@Composable
fun NoteDialogContent(amount: String, type: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var note by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(24.dp)) {
        Text("New Transaction Detected", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "$type: ₹$amount", style = MaterialTheme.typography.bodyLarge, color = if(type == "Credited") Color.Green else Color.Red)
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = note,
            onValueChange = { 
                note = it 
                isError = false
            },
            label = { Text("Note (Mandatory)") },
            isError = isError,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = DarkPrimary, 
                focusedLabelColor = DarkPrimary
            )
        )
        if (isError) {
            Text("Note cannot be empty", color = Color.Red, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) {
                Text("Dismiss", color = Color.Gray)
            }
            Button(
                onClick = { 
                    if (note.trim().isEmpty()) {
                        isError = true
                    } else {
                        onSave(note)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary)
            ) {
                Text("Save", color = Color.White)
            }
        }
    }
}
