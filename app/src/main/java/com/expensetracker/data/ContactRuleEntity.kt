package com.expensetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contact_rules")
data class ContactRuleEntity @JvmOverloads constructor(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val email: String,
    val frequencyType: String,
    val frequencyValue: String,
    val currentCount: Int = 0,
    val isEnabled: Boolean
)
