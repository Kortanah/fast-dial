package com.example.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow

@Dao
interface FlowDao {
    @Query("SELECT * FROM ussd_flows ORDER BY lastModified DESC")
    fun getAllFlows(): Flow<List<FlowEntity>>

    @Query("SELECT * FROM ussd_flows")
    suspend fun getAllFlowsOnce(): List<FlowEntity>

    @Query("SELECT * FROM ussd_flows WHERE id = :flowId")
    suspend fun getFlowById(flowId: String): FlowEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateFlow(flow: FlowEntity)

    @Query("DELETE FROM ussd_flows WHERE id = :flowId")
    suspend fun deleteFlowById(flowId: String)

    @Query("UPDATE ussd_flows SET executionCount = executionCount + 1, successfulCount = successfulCount + :successInc, failedCount = failedCount + :failedInc WHERE id = :flowId")
    suspend fun incrementExecutionStats(flowId: String, successInc: Int, failedInc: Int)
}

@Dao
interface ExecutionDao {
    @Query("SELECT * FROM execution_logs ORDER BY timestamp DESC")
    fun getAllExecutionLogs(): Flow<List<ExecutionEntity>>

    @Query("SELECT * FROM execution_logs WHERE flowId = :flowId ORDER BY timestamp DESC")
    fun getExecutionLogsForFlow(flowId: String): Flow<List<ExecutionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecutionLog(log: ExecutionEntity)

    @Query("DELETE FROM execution_logs")
    suspend fun clearAllLogs()
}

@Dao
interface RecentContactDao {
    @Query("SELECT * FROM recent_contacts ORDER BY timestamp DESC LIMIT 3")
    fun getAllRecentContacts(): Flow<List<RecentContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: RecentContactEntity)

    @Query("DELETE FROM recent_contacts WHERE phoneNumber NOT IN (SELECT phoneNumber FROM recent_contacts ORDER BY timestamp DESC LIMIT 3)")
    suspend fun trimOldContacts()
}

@Database(entities = [FlowEntity::class, ExecutionEntity::class, RecentContactEntity::class], version = 6, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun flowDao(): FlowDao
    abstract fun executionDao(): ExecutionDao
    abstract fun recentContactDao(): RecentContactDao
}

