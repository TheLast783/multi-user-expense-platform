package com.expensetracker.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.AppDatabase
import com.expensetracker.data.ContactRuleEntity
import com.expensetracker.data.ExpenseEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val expenseDao = db.expenseDao()
    private val ruleDao = db.contactRuleDao()
    private val prefs = application.getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)

    private fun getEmail(): String = prefs.getString("user_email", "") ?: ""

    val expenses: StateFlow<List<ExpenseEntity>> = expenseDao.getAllExpenses()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val rules: StateFlow<List<ContactRuleEntity>> = ruleDao.getAllRules()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        refreshDataFromServer()
    }

    fun refreshDataFromServer() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val email = getEmail()
                if (email.isEmpty()) return@launch

                // 1. UPLOAD LOCAL DATA FIRST (Fix: Bug - Data Loss on Login)
                val localExpenses = expenseDao.getAllExpensesSync()
                if (localExpenses.isNotEmpty()) {
                    val uploadPayloads = localExpenses.map {
                        com.expensetracker.data.network.ExpenseSyncPayload(
                            it.title, it.date, it.amount, it.isExpense, it.originalSms, 
                            it.paymentId, it.assignedContact, it.note, it.type, it.bank, it.status, it.remainingAmount
                        )
                    }
                    com.expensetracker.data.network.RetrofitClient.api.syncExpenses(email, uploadPayloads)
                }

                val localRules = ruleDao.getAllRulesSync()
                if (localRules.isNotEmpty()) {
                    val uploadRules = localRules.map {
                        com.expensetracker.data.network.RuleSyncPayload(
                            it.name, it.email, it.frequencyType, it.frequencyValue, it.currentCount, it.isEnabled
                        )
                    }
                    com.expensetracker.data.network.RetrofitClient.api.syncRules(email, uploadRules)
                }

                // 2. NOW DOWNLOAD FRESH DATA
                val remoteExpenses = com.expensetracker.data.network.RetrofitClient.api.fetchExpenses(email)
                if (remoteExpenses.isNotEmpty()) {
                    expenseDao.deleteAllExpenses()
                    expenseDao.insertExpenses(remoteExpenses.map {
                        ExpenseEntity(
                            title = it.title, date = it.date, amount = it.amount, isExpense = it.isExpense,
                            originalSms = it.originalSms, paymentId = it.paymentId, note = it.note,
                            type = it.type, assignedContact = it.assignedContact, bank = it.bank,
                            status = it.status, remainingAmount = it.remainingAmount
                        )
                    })
                }

                val remoteRules = com.expensetracker.data.network.RetrofitClient.api.fetchRules(email)
                if (remoteRules.isNotEmpty()) {
                    ruleDao.deleteAllRules()
                    ruleDao.insertRules(remoteRules.map {
                        ContactRuleEntity(
                            name = it.name, email = it.email, frequencyType = it.frequencyType,
                            frequencyValue = it.frequencyValue, currentCount = it.currentCount, isEnabled = it.isEnabled
                        )
                    })
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun syncExpensesOnly() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val email = getEmail()
                if (email.isEmpty()) return@launch
                val local = expenseDao.getAllExpensesSync()
                if (local.isNotEmpty()) {
                    val payloads = local.map {
                        com.expensetracker.data.network.ExpenseSyncPayload(
                            it.title, it.date, it.amount, it.isExpense, it.originalSms,
                            it.paymentId, it.assignedContact, it.note, it.type, it.bank, it.status, it.remainingAmount
                        )
                    }
                    com.expensetracker.data.network.RetrofitClient.api.syncExpenses(email, payloads)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun clearAllLocalData() {
        viewModelScope.launch(Dispatchers.IO) {
            expenseDao.deleteAllExpenses()
            ruleDao.deleteAllRules()
        }
    }

    private fun syncRulesToServer() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allRules = ruleDao.getAllRulesSync()
                val email = getEmail()
                if (email.isEmpty()) return@launch

                val payloads = allRules.map {
                    com.expensetracker.data.network.RuleSyncPayload(
                        it.name, it.email, it.frequencyType, it.frequencyValue, it.currentCount, it.isEnabled
                    )
                }
                com.expensetracker.data.network.RetrofitClient.api.syncRules(email, payloads)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun updateRule(rule: ContactRuleEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            ruleDao.insertRule(rule) // insertRule has OnConflictStrategy.REPLACE
            syncRulesToServer()
        }
    }

    fun addRule(name: String, email: String, freqType: String, freqVal: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val rule = ContactRuleEntity(
                name = name,
                email = email,
                frequencyType = freqType,
                frequencyValue = freqVal,
                isEnabled = true
            )
            ruleDao.insertRule(rule)
            syncRulesToServer()
        }
    }

    fun toggleRule(rule: ContactRuleEntity, isEnabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            ruleDao.insertRule(rule.copy(isEnabled = isEnabled))
            syncRulesToServer()
        }
    }

    fun deleteRule(rule: ContactRuleEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val email = getEmail()
                if (email.isNotEmpty()) {
                    com.expensetracker.data.network.RetrofitClient.api.deleteRule(email, rule.name)
                }
            } catch (e: Exception) { e.printStackTrace() }
            
            expenseDao.unassignContact(rule.name) // Rescue their financial history to User
            ruleDao.deleteRule(rule)
        }
    }

    fun sendManualInvoice(rule: ContactRuleEntity, context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val email = getEmail()
                val response = com.expensetracker.data.network.RetrofitClient.api.sendInvoice(
                    email,
                    com.expensetracker.data.network.InvoicePayload(rule.name, rule.email)
                )
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    if (response.status == "error") {
                        android.widget.Toast.makeText(context, response.message, android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        android.widget.Toast.makeText(context, "Mail sent securely to ${rule.name}!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Failed to send mail: Server Error", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun adminClearCloudDatabase(context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val email = getEmail()
                if (email.isNotEmpty()) {
                    com.expensetracker.data.network.RetrofitClient.api.adminClearDatabase(email)
                    clearAllLocalData()
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Cloud Database Wiped Successfully", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
