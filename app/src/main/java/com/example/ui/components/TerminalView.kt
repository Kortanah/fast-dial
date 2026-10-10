package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ExecutionState
import com.example.engine.ExecutionEngineState

@Composable
fun TerminalView(
    state: ExecutionEngineState,
    entryUssdCode: String = "*170#",
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("execution_status_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Status Title & Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Execution Log & Status",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (state.state) {
                            ExecutionState.IDLE -> "Ready to execute flow"
                            ExecutionState.PAUSED -> "Flow execution paused"
                            ExecutionState.RUNNING -> "Executing step ${state.currentStepIndex} of ${state.totalSteps}"
                            ExecutionState.COMPLETED -> "Flow completed successfully"
                            ExecutionState.FAILED -> "Execution stopped / error"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // State Badge Indicator
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when (state.state) {
                        ExecutionState.RUNNING -> Color(0xFFE8F5E9)
                        ExecutionState.COMPLETED -> Color(0xFFE8F5E9)
                        ExecutionState.FAILED -> Color(0xFFFFEBEE)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.state == ExecutionState.RUNNING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF2E7D32)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = if (state.state == ExecutionState.RUNNING) "RUNNING" else state.state.name,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = when (state.state) {
                                ExecutionState.RUNNING -> Color(0xFF2E7D32)
                                ExecutionState.COMPLETED -> Color(0xFF2E7D32)
                                ExecutionState.FAILED -> Color(0xFFC62828)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Log Console - Monospaced Plain Writing Lines Format
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF16161A), // Soft dark slate console background
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 200.dp)
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (state.logs.isEmpty() && state.stepResults.isEmpty()) {
                            Text(
                                text = "[SYSTEM] Ready. Click 'Run Flow' to start execution.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFF90A4AE)
                                )
                            )
                        } else {
                            // Render state logs with smaller, softer gray font
                            state.logs.forEach { logLine ->
                                Text(
                                    text = logLine,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.5.sp,
                                        color = if (logLine.contains("ERROR", true) || logLine.contains("Failed", true)) Color(0xFFEF5350)
                                        else if (logLine.contains("SUCCESS", true) || logLine.contains("Done", true)) Color(0xFF66BB6A)
                                        else Color(0xFFB0BEC5)
                                    )
                                )
                            }

                            // Render step results as log text if logs empty
                            if (state.logs.isEmpty()) {
                                state.stepResults.forEach { result ->
                                    Text(
                                        text = "> Step ${result.stepIndex} [${result.nodeTitle}]: Status=${result.status.name} ${if (result.sentPayload.isNotBlank()) "Action=${result.sentPayload}" else ""}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.5.sp,
                                            color = Color(0xFF81C784)
                                        )
                                    )
                                    if (result.receivedResponse.isNotBlank()) {
                                        Text(
                                            text = "  Screen: ${result.receivedResponse.take(80).replace("\n", " ")}...",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = Color(0xFF78909C)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

