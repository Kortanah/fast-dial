package com.example.engine

import com.example.data.model.FlowNode
import com.example.data.model.NodeType
import com.example.data.model.SecurityAuditIssue
import com.example.data.model.SecurityLevel
import com.example.data.model.UssdFlow

object SecurityScanner {

    fun scanFlow(flow: UssdFlow): List<SecurityAuditIssue> {
        val issues = mutableListOf<SecurityAuditIssue>()

        // Check 1: Sensitive node with plain-text numeric values
        flow.nodes.forEach { node ->
            if (node.type == NodeType.INPUT_VALUE) {
                val value = node.inputValue.trim()
                val isLikelyPin = (value.length in 4..6 && value.all { it.isDigit() }) ||
                        node.title.contains("pin", ignoreCase = true) ||
                        node.title.contains("password", ignoreCase = true) ||
                        node.title.contains("secret", ignoreCase = true)

                if (isLikelyPin && !node.isSensitive) {
                    issues.add(
                        SecurityAuditIssue(
                            flowId = flow.id,
                            flowName = flow.name,
                            nodeId = node.id,
                            issueType = "Unencrypted Sensitive Input",
                            recommendation = "Mark node '${node.title}' as Sensitive & enable AES Vault Encryption."
                        )
                    )
                }
            }

            // Check 2: Raw USSD string containing inline PINs (e.g. *170*1*1*1234#)
            if (node.type == NodeType.USSD_DIAL) {
                val parts = node.ussdCode.split("*", "#").filter { it.isNotEmpty() }
                if (parts.size >= 4) {
                    issues.add(
                        SecurityAuditIssue(
                            flowId = flow.id,
                            flowName = flow.name,
                            nodeId = node.id,
                            issueType = "Inline USSD Secrets Detected",
                            recommendation = "Split hardcoded USSD '${node.ussdCode}' into separate Input Nodes for PIN protection."
                        )
                    )
                }
            }
        }

        // Check 3: Flow with sensitive nodes but low security level
        val hasSensitiveNodes = flow.nodes.any { it.isSensitive }
        if (hasSensitiveNodes && flow.securityLevel == SecurityLevel.STANDARD) {
            issues.add(
                SecurityAuditIssue(
                    flowId = flow.id,
                    flowName = flow.name,
                    nodeId = "flow_security",
                    issueType = "Sub-Optimal Flow Protection",
                    recommendation = "Upgrade flow '${flow.name}' security level to PIN Protected or Vault Encrypted."
                )
            )
        }

        return issues
    }

    fun autoVaultFlow(flow: UssdFlow): UssdFlow {
        val vaultedNodes = flow.nodes.map { node ->
            val isLikelyPin = (node.inputValue.length in 4..6 && node.inputValue.all { it.isDigit() }) ||
                    node.title.contains("pin", ignoreCase = true)
            if (isLikelyPin || node.type == NodeType.INPUT_VALUE && node.title.contains("pin", ignoreCase = true)) {
                node.copy(
                    isSensitive = true,
                    inputValue = if (node.inputValue.all { it.isDigit() }) "••••" else node.inputValue
                )
            } else {
                node
            }
        }
        return flow.copy(
            nodes = vaultedNodes,
            securityLevel = SecurityLevel.VAULT_ENCRYPTED
        )
    }
}
