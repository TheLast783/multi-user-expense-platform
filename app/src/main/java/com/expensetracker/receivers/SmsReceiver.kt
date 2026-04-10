package com.expensetracker.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
 
    companion object {
        private val processedIds = mutableSetOf<String>()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val fullBody = messages.joinToString("") { it.displayMessageBody }
            parseAndSaveSms(context, fullBody)
        }
    }

    private fun parseAndSaveSms(context: Context, message: String) {
        val lowerCaseMsg = message.lowercase(Locale.ROOT)
        if (!lowerCaseMsg.contains("debited") && !lowerCaseMsg.contains("spent") && !lowerCaseMsg.contains("paid") && 
            !lowerCaseMsg.startsWith("sent") && !lowerCaseMsg.contains(" sent ") && 
            !lowerCaseMsg.contains("credited") && !lowerCaseMsg.contains("received")) return

        val amountPattern = Pattern.compile("(?i)(rs\\.?|inr)\\s*([0-9,]+\\.?[0-9]*)")
        val matcher = amountPattern.matcher(message)
        val amount = if (matcher.find()) matcher.group(2) ?: "0.00" else "0.00"
        
        val refPattern = Pattern.compile("(?i)(?:ref no|upi ref no|txn id|ref)\\s*[:\\-]?\\s*([a-zA-Z0-9]+)")
        val refMatcher = refPattern.matcher(message)
        val paymentId = if (refMatcher.find()) refMatcher.group(1) ?: "" else ""
        
        // DEDUPLICATION LOCK (Fix: Bug 4)
        val lockId = if (paymentId.isNotEmpty()) paymentId else message.hashCode().toString()
        synchronized(processedIds) {
            if (processedIds.contains(lockId)) return
            processedIds.add(lockId)
            if (processedIds.size > 50) processedIds.clear()
        }

        val isDebited = lowerCaseMsg.contains("debited") || lowerCaseMsg.contains("spent") || lowerCaseMsg.contains("paid") || lowerCaseMsg.startsWith("sent")
        
        val bankPattern = Pattern.compile("(?i)(?:From|By)\\s+([a-zA-Z0-9\\s]+?Bank)")
        val bankMatcher = bankPattern.matcher(message)
        val bankName = if (bankMatcher.find()) bankMatcher.group(1)?.trim() ?: "Unknown Bank" else "Unknown Bank"

        val dateMatcher = Pattern.compile("(?i)On\\s+(\\d{2}/\\d{2}/\\d{2,4})").matcher(message)
        var dateStr = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date())
        if (dateMatcher.find()) dateStr = dateMatcher.group(1) ?: dateStr

        val dialogIntent = Intent(context, com.expensetracker.ui.NoteDialogActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("AMOUNT", amount)
            putExtra("TYPE", if (isDebited) "Debited" else "Credited")
            putExtra("DATE", dateStr)
            putExtra("ORIGINAL_SMS", message)
            putExtra("PAYMENT_ID", paymentId)
            putExtra("BANK", bankName)
        }

        // INSTANT SAVE (Fix: Swipe Problem - Bug 13)
        val db = com.expensetracker.data.AppDatabase.getDatabase(context)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val entity = com.expensetracker.data.ExpenseEntity(
                title = if(isDebited) "Expense" else "Income",
                date = dateStr,
                amount = amount,
                isExpense = isDebited,
                originalSms = message,
                paymentId = paymentId,
                note = "User", // Default note
                type = if(isDebited) "Debited" else "Credited",
                assignedContact = "User", // Default contact
                bank = bankName,
                status = if(isDebited) "Unpaid" else "Paid",
                remainingAmount = amount.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
            )
            val insertedId = db.expenseDao().insertExpense(entity)
            dialogIntent.putExtra("EXPENSE_ID", insertedId)

            val pendingIntent = PendingIntent.getActivity(
                context, 
                lockId.hashCode(), 
                dialogIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val channelId = "expense_alerts"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "Transaction Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Forced popups for new banking transactions"
                    enableLights(true)
                    lightColor = Color.GREEN
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info) 
                .setContentTitle("Transaction Detected: ₹$amount")
                .setContentText("Complete the note to save.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(lockId.hashCode(), notification)
            
            try {
                val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                val email = prefs.getString("user_email", "") ?: ""
                if (email.isNotEmpty()) {
                    val syncList = listOf(com.expensetracker.data.network.ExpenseSyncPayload(
                        entity.title, entity.date, entity.amount, entity.isExpense, 
                        entity.originalSms, entity.paymentId, entity.assignedContact, 
                        entity.note, entity.type, entity.bank, entity.status, entity.remainingAmount
                    ))
                    com.expensetracker.data.network.RetrofitClient.api.syncExpenses(email, syncList)
                }
            } catch (e: Exception) { e.printStackTrace() }

            try {
                context.startActivity(dialogIntent)
            } catch (e: Exception) {}
        }
    }
}
