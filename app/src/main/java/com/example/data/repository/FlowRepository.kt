package com.example.data.repository

import com.example.data.local.ExecutionDao
import com.example.data.local.ExecutionEntity
import com.example.data.local.FlowDao
import com.example.data.local.FlowEntity
import com.example.data.model.ExecutionLog
import com.example.data.model.SecurityAuditIssue
import com.example.data.model.UssdFlow
import com.example.data.model.ensureVariableKeys
import com.example.engine.SecurityScanner
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FlowRepository(
    private val flowDao: FlowDao,
    private val executionDao: ExecutionDao
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val jsonAdapter = moshi.adapter(UssdFlow::class.java)

    val allFlows: Flow<List<UssdFlow>> = flowDao.getAllFlows().map { entities ->
        entities.map { it.toDomainModel() }
    }

    val executionLogs: Flow<List<ExecutionLog>> = executionDao.getAllExecutionLogs().map { entities ->
        entities.map { it.toDomainModel() }
    }

    suspend fun getFlowById(id: String): UssdFlow? {
        return flowDao.getFlowById(id)?.toDomainModel()
    }

    suspend fun saveFlow(flow: UssdFlow) {
        val migrated = flow.ensureVariableKeys()
        flowDao.insertOrUpdateFlow(FlowEntity.fromDomainModel(migrated))
    }

    suspend fun deleteFlow(id: String) {
        flowDao.deleteFlowById(id)
    }

    suspend fun duplicateFlow(flow: UssdFlow) {
        val newFlow = flow.copy(
            id = UUID.randomUUID().toString(),
            name = "${flow.name} (c)",
            executionCount = 0,
            successfulCount = 0,
            failedCount = 0,
            lastModified = System.currentTimeMillis()
        )
        saveFlow(newFlow)
    }

    suspend fun recordExecutionLog(log: ExecutionLog) {
        executionDao.insertExecutionLog(ExecutionEntity.fromDomainModel(log))
        val isSuccess = log.status == com.example.data.model.ExecutionState.COMPLETED
        flowDao.incrementExecutionStats(
            flowId = log.flowId,
            successInc = if (isSuccess) 1 else 0,
            failedInc = if (isSuccess) 0 else 1
        )
    }

    suspend fun migrateVariableKeysInDatabase() {
        try {
            val entities = flowDao.getAllFlowsOnce()
            for (entity in entities) {
                val flow = entity.toDomainModel()
                if (flow.nodes.any { it.variableKey.isBlank() }) {
                    val migrated = flow.ensureVariableKeys()
                    flowDao.insertOrUpdateFlow(FlowEntity.fromDomainModel(migrated))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FlowRepository", "Error migrating variable keys: ${e.message}")
        }
    }

    suspend fun seedDefaultDataIfEmpty() {
        // Removed hardcoded default templates seeding
    }

    fun exportFlowToJson(flow: UssdFlow): String {
        return jsonAdapter.toJson(flow)
    }

    fun exportAllFlowsToJson(flows: List<UssdFlow>): String {
        val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, UssdFlow::class.java)
        val adapter = moshi.adapter<List<UssdFlow>>(listType)
        return adapter.toJson(flows)
    }

    fun importFlowFromJsonString(jsonString: String): UssdFlow? {
        return try {
            jsonAdapter.fromJson(jsonString)?.ensureVariableKeys()
        } catch (e: Exception) {
            null
        }
    }

    fun importAllFlowsFromJsonString(jsonString: String): List<UssdFlow> {
        return try {
            val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, UssdFlow::class.java)
            val adapter = moshi.adapter<List<UssdFlow>>(listType)
            adapter.fromJson(jsonString)?.map { it.ensureVariableKeys() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun importFlowFromJson(jsonString: String): UssdFlow? {
        return try {
            val flow = jsonAdapter.fromJson(jsonString) ?: return null
            val newFlow = flow.copy(
                id = UUID.randomUUID().toString(),
                name = "${flow.name} (IM)",
                lastModified = System.currentTimeMillis()
            )
            saveFlow(newFlow)
            newFlow
        } catch (e: Exception) {
            null
        }
    }

    suspend fun importAllFlowsFromJson(jsonString: String): Boolean {
        return try {
            val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, UssdFlow::class.java)
            val adapter = moshi.adapter<List<UssdFlow>>(listType)
            val flows = adapter.fromJson(jsonString) ?: return false
            flows.forEach { flow ->
                val newFlow = flow.copy(
                    id = UUID.randomUUID().toString(),
                    name = if (flow.name.endsWith("(IM)")) flow.name else "${flow.name} (IM)",
                    lastModified = System.currentTimeMillis()
                )
                saveFlow(newFlow)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun runSecurityScanOnFlows(flows: List<UssdFlow>): List<SecurityAuditIssue> {
        return flows.flatMap { SecurityScanner.scanFlow(it) }
    }
}
