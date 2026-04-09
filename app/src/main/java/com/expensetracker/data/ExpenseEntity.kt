package com.expensetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String, 
    val date: String,
    val amount: String,
    val isExpense: Boolean,
    val originalSms: String,
    val paymentId: String? = null,
    val assignedContact: String = "User",
    val note: String = "",
    val type: String = "Debited",
    val bank: String = "Unknown Bank",
    val status: String = "Unpaid",
    val remainingAmount: Double = 0.0
)
