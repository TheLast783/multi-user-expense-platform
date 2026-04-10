package com.expensetracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY id DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Insert
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @androidx.room.Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE assignedContact = :contactName AND note LIKE '%' || :note || '%' AND status = 'Unpaid' ORDER BY id ASC")
    suspend fun getUnpaidMatchesSync(contactName: String, note: String): List<ExpenseEntity>

    @Query("UPDATE expenses SET assignedContact = 'User' WHERE assignedContact = :contactName")
    suspend fun unassignContact(contactName: String)

    @Query("SELECT * FROM expenses")
    suspend fun getAllExpensesSync(): List<ExpenseEntity>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()
}
