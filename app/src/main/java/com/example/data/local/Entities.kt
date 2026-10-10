package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.ExecutionLog
import com.example.data.model.ExecutionState
import com.example.data.model.ExecutionStepResult
import com.example.data.model.FlowCategory
import com.example.data.model.FlowNode
import com.example.data.model.FlowStatus
import com.example.data.model.SecurityLevel
import com.example.data.model.UssdFlow

import com.example.data.model.ensureVariableKeys

@Entity(tableName = "ussd_flows")
data class FlowEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val entryUssdCode: String,
    val category: String,
    val status: String,
    val securityLevel: String,
    val lastModified: Long,
    val executionCount: Int,
    val successfulCount: Int,
    val failedCount: Int,
    val nodesJson: List<FlowNode>,
    val isTemplate: Boolean,
    val userRole: String = "",
    val network: String = "",
    val icon: String = "",
    val gridBtn: Boolean = false
) {
    fun toDomainModel(): UssdFlow {
        return UssdFlow(
            id = id,
            name = name,
            description = description,
            entryUssdCode = entryUssdCode,
            category = try { FlowCategory.valueOf(category) } catch (e: Exception) { FlowCategory.CUSTOM },
            status = try { FlowStatus.valueOf(status) } catch (e: Exception) { FlowStatus.ACTIVE },
            securityLevel = try { SecurityLevel.valueOf(securityLevel) } catch (e: Exception) { SecurityLevel.STANDARD },
            lastModified = lastModified,
            executionCount = executionCount,
            successfulCount = successfulCount,
            failedCount = failedCount,
            nodes = nodesJson,
            isTemplate = isTemplate,
            userRole = userRole,
            network = network,
            icon = icon,
            gridBtn = gridBtn
        ).ensureVariableKeys()
    }

    companion object {
        fun fromDomainModel(flow: UssdFlow): FlowEntity {
            return FlowEntity(
                id = flow.id,
                name = flow.name,
                description = flow.description,
                entryUssdCode = flow.entryUssdCode,
                category = flow.category.name,
                status = flow.status.name,
                securityLevel = flow.securityLevel.name,
                lastModified = flow.lastModified,
                executionCount = flow.executionCount,
                successfulCount = flow.successfulCount,
                failedCount = flow.failedCount,
                nodesJson = flow.nodes,
                isTemplate = flow.isTemplate,
                userRole = flow.userRole,
                network = flow.network,
                icon = flow.icon,
                gridBtn = flow.gridBtn
            )
        }
    }
}

@Entity(tableName = "execution_logs")
data class ExecutionEntity(
    @PrimaryKey val id: String,
    val flowId: String,
    val flowName: String,
    val timestamp: Long,
    val durationMs: Long,
    val status: String,
    val simSlot: Int,
    val errorMessage: String?,
    val stepResults: List<ExecutionStepResult>
) {
    fun toDomainModel(): ExecutionLog {
        return ExecutionLog(
            id = id,
            flowId = flowId,
            flowName = flowName,
            timestamp = timestamp,
            durationMs = durationMs,
            status = try { ExecutionState.valueOf(status) } catch (e: Exception) { ExecutionState.COMPLETED },
            simSlot = simSlot,
            errorMessage = errorMessage,
            stepResults = stepResults
        )
    }

    companion object {
        fun fromDomainModel(log: ExecutionLog): ExecutionEntity {
            return ExecutionEntity(
                id = log.id,
                flowId = log.flowId,
                flowName = log.flowName,
                timestamp = log.timestamp,
                durationMs = log.durationMs,
                status = log.status.name,
                simSlot = log.simSlot,
                errorMessage = log.errorMessage,
                stepResults = log.stepResults
            )
        }
    }
}

@Entity(tableName = "recent_contacts")
data class RecentContactEntity(
    @PrimaryKey val phoneNumber: String,
    val name: String,
    val category: String,
    val timestamp: Long
) {
    fun toDomainModel(): com.example.data.model.PhoneContact {
        return com.example.data.model.PhoneContact(
            name = name,
            phoneNumber = phoneNumber,
            category = category
        )
    }

    companion object {
        fun fromDomainModel(contact: com.example.data.model.PhoneContact): RecentContactEntity {
            return RecentContactEntity(
                phoneNumber = contact.phoneNumber,
                name = contact.name,
                category = contact.category,
                timestamp = System.currentTimeMillis()
            )
        }
    }
}

