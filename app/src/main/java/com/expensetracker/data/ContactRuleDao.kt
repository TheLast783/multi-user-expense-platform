package com.expensetracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import androidx.room.OnConflictStrategy
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactRuleDao {
    @Query("SELECT * FROM contact_rules ORDER BY id DESC")
    fun getAllRules(): Flow<List<ContactRuleEntity>>

    @Query("SELECT * FROM contact_rules")
    suspend fun getAllRulesSync(): List<ContactRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: ContactRuleEntity)

    @Delete
    suspend fun deleteRule(rule: ContactRuleEntity)

    @Query("DELETE FROM contact_rules")
    suspend fun deleteAllRules()
}
