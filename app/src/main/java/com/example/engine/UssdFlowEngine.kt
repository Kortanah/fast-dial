package com.example.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.model.EngineState
import com.example.data.model.FlowEngineStatus
import com.example.data.model.FlowPayload
import com.example.data.model.Step
import com.example.data.model.StepType
import com.example.service.UssdAccessibilityBridge
import com.example.service.UssdAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UssdFlowEngine(
    private val context: Context
) : UssdAccessibilityBridge {

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _status = MutableStateFlow(FlowEngineStatus())
    val status: Flow<FlowEngineStatus> = _status.asStateFlow()

    private var currentPayload: FlowPayload? = null
    private var currentStepIndex = 0
    private var lastProcessedPrompt = ""
    private var isEngineRunning = false

    private var manualWaitJob: Job? = null
    private var probeJob: Job? = null
    private var dialogDisappearedJob: Job? = null

    private var telephonyManager: android.telephony.TelephonyManager? = null
    private var callStateListenerRegistered = false
    private var isCallActive = false

    @Suppress("DEPRECATION")
    private val callStateListener = object : android.telephony.PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            super.onCallStateChanged(state, phoneNumber)
            when (state) {
                android.telephony.TelephonyManager.CALL_STATE_OFFHOOK -> {
                    log("Call state: OFFHOOK (dialing/active).")
                    isCallActive = true
                }
                android.telephony.TelephonyManager.CALL_STATE_RINGING -> {
                    log("Call state: RINGING.")
                }
                android.telephony.TelephonyManager.CALL_STATE_IDLE -> {
                    log("Call state: IDLE.")
                    if (isCallActive) {
                        isCallActive = false
                        log("Call ended/resolved.")
                    }
                }
            }
        }
    }

    private fun registerCallStateListener() {
        if (callStateListenerRegistered) return
        try {
            telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            @Suppress("DEPRECATION")
            telephonyManager?.listen(callStateListener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
            callStateListenerRegistered = true
            isCallActive = false
            log("Registered telephony call state listener.")
        } catch (e: Exception) {
            log("Warning: Could not register telephony call state listener: ${e.message}")
        }
    }

    private fun unregisterCallStateListener() {
        if (!callStateListenerRegistered) return
        try {
            @Suppress("DEPRECATION")
            telephonyManager?.listen(callStateListener, android.telephony.PhoneStateListener.LISTEN_NONE)
            log("Unregistered telephony call state listener.")
        } catch (e: Exception) {
            // ignore
        }
        callStateListenerRegistered = false
    }

    init {
        UssdAccessibilityService.bridge = this
    }

    suspend fun start(payload: FlowPayload): Boolean = withContext(Dispatchers.Main) {
        if (isEngineRunning && _status.value.state != EngineState.IDLE &&
            _status.value.state != EngineState.DONE && _status.value.state != EngineState.ERROR) {
            log("Cannot start flow: Engine is already running in state ${_status.value.state}")
            return@withContext false
        }

        UssdAccessibilityService.refreshServiceState(context)
        if (!UssdAccessibilityService.isServiceActive.value) {
            val errMsg = "Accessibility Service is not enabled. Please enable 'USSD Flow Automation Service' in Android Settings."
            updateStatus(
                EngineState.ERROR,
                0,
                payload.steps.size,
                "",
                "Error: Accessibility Service Offline",
                errMsg
            )
            return@withContext false
        }

        currentPayload = payload
        currentStepIndex = 0
        lastProcessedPrompt = ""
        isEngineRunning = true

        val logs = mutableListOf("▶ Initiating USSD Flow '${payload.name}' (${payload.ussdCode})")
        _status.value = FlowEngineStatus(
            state = EngineState.DIALING,
            currentStepIndex = 0,
            totalSteps = payload.steps.size,
            message = "Dialing USSD code ${payload.ussdCode}...",
            logs = logs
        )

        val dialSuccess = dialUssdCode(payload.ussdCode, payload.simSlot)
        if (!dialSuccess) {
            val errMsg = "Failed to launch phone dialer for ${payload.ussdCode}"
            updateStatus(EngineState.ERROR, 0, payload.steps.size, "", "Dial Error", errMsg)
            return@withContext false
        }

        val timeoutSec = payload.timeoutSeconds.coerceIn(3, 60)
        val timeoutMs = timeoutSec * 1000L

        updateStatus(
            EngineState.WAITING_FOR_CALL_OR_USSD,
            0,
            payload.steps.size,
            "",
            "Launched call intent. Waiting for USSD dialog or SIM selection (up to ${timeoutSec}s)..."
        )

        scope.launch {
            val dialogDetected = waitForCallOrUssdDialog(timeoutMs = timeoutMs)
            if (!dialogDetected && isEngineRunning) {
                failStep("USSD_DIALOG_TIMEOUT: No USSD dialog detected within ${timeoutSec} seconds.")
            }
        }

        return@withContext true
    }

    private suspend fun waitForCallOrUssdDialog(timeoutMs: Long): Boolean {
        val startTime = System.currentTimeMillis()
        val pollInterval = 500L
        var isSimPickerActive = false

        log("State: WAITING_FOR_CALL_OR_USSD. Monitoring accessibility events & active windows (timeout: ${timeoutMs / 1000}s)...")

        while (isEngineRunning && (System.currentTimeMillis() - startTime) < timeoutMs) {
            val currentState = _status.value.state
            if (currentState != EngineState.WAITING_FOR_CALL_OR_USSD && currentState != EngineState.WAITING_FOR_USSD_DIALOG) {
                return true
            }

            val service = UssdAccessibilityService.instance
            if (service != null && UssdAccessibilityService.isServiceActive.value) {
                service.evaluateScreen()

                val isSimPicker = service.isSimPickerVisible()
                val isValidUssd = service.isValidUssdDialogVisible()

                if (isSimPicker) {
                    if (!isSimPickerActive) {
                        isSimPickerActive = true
                        log("SIM picker dialog detected. Execution paused, waiting for user SIM selection...")
                    }
                    updateStatus(
                        EngineState.WAITING_FOR_CALL_OR_USSD,
                        0,
                        currentPayload?.steps?.size ?: 0,
                        "",
                        "SIM picker detected. Please select a SIM card..."
                    )
                } else if (isValidUssd) {
                    val root = service.getDialogRoot()
                    val promptText = service.extractPromptText(root)
                    val hasEditable = service.hasActionableField(root)

                    if (promptText == lastProcessedPrompt ||
                        (_status.value.state != EngineState.WAITING_FOR_CALL_OR_USSD && _status.value.state != EngineState.WAITING_FOR_USSD_DIALOG)) {
                        return true
                    }

                    log("Valid USSD dialog detected! Transitioning to PROCESSING_USSD.")
                    lastProcessedPrompt = promptText
                    updateStatus(
                        EngineState.PROCESSING_USSD,
                        0,
                        currentPayload?.steps?.size ?: 0,
                        promptText,
                        "USSD Dialog detected. Processing USSD automation..."
                    )

                    updateStatus(
                        EngineState.WAITING_FOR_PROMPT,
                        0,
                        currentPayload?.steps?.size ?: 0,
                        promptText,
                        "USSD Dialog active. Executing flow automation..."
                    )

                    processCurrentStep(promptText, hasEditable)
                    startProbeLoop()
                    return true
                } else {
                    if (isSimPickerActive) {
                        log("SIM selection completed. Resuming wait for USSD dialog...")
                        isSimPickerActive = false
                    }
                    val elapsed = System.currentTimeMillis() - startTime
                    val remainingSecs = ((timeoutMs - elapsed) / 1000).coerceAtLeast(0)
                    updateStatus(
                        EngineState.WAITING_FOR_USSD_DIALOG,
                        0,
                        currentPayload?.steps?.size ?: 0,
                        "",
                        "Waiting for USSD dialog... (${remainingSecs}s remaining)"
                    )
                }
            } else {
                updateStatus(
                    EngineState.WAITING_FOR_USSD_DIALOG,
                    0,
                    currentPayload?.steps?.size ?: 0,
                    "",
                    "Waiting for USSD Accessibility Service & Telephony Window..."
                )
            }

            delay(pollInterval)
        }

        return false
    }

    fun stop() {
        manualWaitJob?.cancel()
        probeJob?.cancel()
        dialogDisappearedJob?.cancel()
        isEngineRunning = false
        unregisterCallStateListener()

        updateStatus(
            EngineState.ERROR,
            currentStepIndex,
            currentPayload?.steps?.size ?: 0,
            "",
            "Flow stopped by user.",
            "Execution cancelled by user."
        )
    }

    override fun onScreenTextChanged(promptText: String, hasInputField: Boolean) {
        if (!isEngineRunning) return

        val state = _status.value.state
        if (state != EngineState.WAITING_FOR_PROMPT &&
            state != EngineState.WAITING_FOR_MANUAL &&
            state != EngineState.WAITING_FOR_CALL_OR_USSD &&
            state != EngineState.WAITING_FOR_USSD_DIALOG) {
            return
        }

        // Dedupe screen text per step
        if (promptText == lastProcessedPrompt) {
            return
        }

        if (state == EngineState.WAITING_FOR_CALL_OR_USSD || state == EngineState.WAITING_FOR_USSD_DIALOG) {
            if (promptText.isBlank()) return
            val service = UssdAccessibilityService.instance
            if (service != null && service.isSimPickerVisible()) return
            if (service != null && !service.isValidUssdDialogVisible()) return

            log("Valid USSD dialog detected via real-time event! Transitioning to PROCESSING_USSD.")
            lastProcessedPrompt = promptText
            updateStatus(
                EngineState.PROCESSING_USSD,
                0,
                currentPayload?.steps?.size ?: 0,
                promptText,
                "USSD Dialog detected. Processing USSD automation..."
            )
            scope.launch {
                if (!isEngineRunning) return@launch
                updateStatus(
                    EngineState.WAITING_FOR_PROMPT,
                    0,
                    currentPayload?.steps?.size ?: 0,
                    promptText,
                    "USSD Dialog active. Executing flow automation..."
                )
                processCurrentStep(promptText, hasInputField)
                startProbeLoop()
            }
            return
        }

        if (state == EngineState.WAITING_FOR_MANUAL) {
            log("Manual step completed by user (new screen prompt received).")
            manualWaitJob?.cancel()
            advanceStep()
            return
        }

        if (state == EngineState.WAITING_FOR_PROMPT) {
            lastProcessedPrompt = promptText
            probeJob?.cancel()
            processCurrentStep(promptText, hasInputField)
        }
    }

    override fun onSendButtonClicked() {
        if (!isEngineRunning) return
        if (_status.value.state == EngineState.WAITING_FOR_MANUAL) {
            log("Manual send button click detected. Continuing flow.")
            manualWaitJob?.cancel()
            advanceStep()
        }
    }

    override fun onDialogDisappeared() {
        if (!isEngineRunning) return
        if (_status.value.state == EngineState.WAITING_FOR_MANUAL) {
            // Debounce dialog disappearance (~1s) during manual step
            dialogDisappearedJob?.cancel()
            dialogDisappearedJob = scope.launch {
                delay(1000)
                if (isEngineRunning && _status.value.state == EngineState.WAITING_FOR_MANUAL) {
                    log("USSD dialog closed during manual step.")
                    advanceStep()
                }
            }
        }
    }

    private fun processCurrentStep(promptText: String, hasInputField: Boolean) {
        val payload = currentPayload ?: return
        val steps = payload.steps

        if (currentStepIndex >= steps.size) {
            // Flow completed cleanly
            isEngineRunning = false
            updateStatus(
                EngineState.DONE,
                steps.size,
                steps.size,
                promptText,
                "USSD Flow completed successfully!",
                null
            )
            return
        }

        val step = steps[currentStepIndex]
        updateStatus(
            EngineState.EXECUTING_STEP,
            currentStepIndex + 1,
            steps.size,
            promptText,
            "Executing Step ${currentStepIndex + 1}/${steps.size} [${step.type.name}]"
        )

        scope.launch {
            try {
                kotlinx.coroutines.withTimeout(20000L) {
                    if (step.type == StepType.AUTO || step.type == StepType.INPUT) {
                        val service = UssdAccessibilityService.instance
                        val hasValidInput = service?.isValidUssdInputDialog() ?: hasInputField
                        if (!hasValidInput) {
                            failStep("No input field present — carrier returned an unexpected or error response: '$promptText'")
                            return@withTimeout
                        }
                    }

                    when (step.type) {
                        StepType.AUTO -> {
                            val rawValue = step.actionValue ?: ""
                            val substitutedValue = substituteVariables(rawValue, payload.userInputs)

                            val service = UssdAccessibilityService.instance
                            if (service == null) {
                                failStep("FAILED_TO_INJECT: Accessibility Service instance not available.")
                                return@withTimeout
                            }

                            if (!service.isValidUssdInputDialog()) {
                                // Wait up to 3 seconds for active dialog if transition is in progress
                                var retryCount = 0
                                while (retryCount < 6 && !service.isValidUssdInputDialog()) {
                                    delay(500)
                                    retryCount++
                                }
                                if (!service.isValidUssdInputDialog()) {
                                    failStep("FAILED_TO_INJECT: No valid USSD dialog found to perform text injection.")
                                    return@withTimeout
                                }
                            }

                            log("Step ${currentStepIndex + 1} (AUTO): Injecting '$substitutedValue'")
                            val injected = service.injectText(substitutedValue)
                            if (!injected) {
                                failStep("FAILED_TO_INJECT: Failed to inject text '$substitutedValue' into USSD field after all retry strategies.")
                                return@withTimeout
                            }

                            val clicked = service.clickSendButton()
                            if (!clicked) {
                                failStep("FAILED_TO_INJECT: Failed to click Send/OK button for step ${currentStepIndex + 1}.")
                                return@withTimeout
                            }

                            advanceStep()
                        }

                        StepType.INPUT -> {
                            val key = step.variableKey ?: ""
                            val valueToSend = lookupVariable(key, payload.userInputs)
                                ?: step.actionValue?.takeIf { it.isNotBlank() }

                            if (valueToSend == null) {
                                failStep("FAILED_TO_INJECT: Missing user input variable value for key '$key' at step ${currentStepIndex + 1}")
                                return@withTimeout
                            }

                            val service = UssdAccessibilityService.instance
                            if (service == null) {
                                failStep("FAILED_TO_INJECT: Accessibility Service instance not available.")
                                return@withTimeout
                            }

                            if (!service.isValidUssdInputDialog()) {
                                var retryCount = 0
                                while (retryCount < 6 && !service.isValidUssdInputDialog()) {
                                    delay(500)
                                    retryCount++
                                }
                                if (!service.isValidUssdInputDialog()) {
                                    failStep("FAILED_TO_INJECT: No valid USSD dialog found to inject variable '$key'.")
                                    return@withTimeout
                                }
                            }

                            log("Step ${currentStepIndex + 1} (INPUT): Injecting variable '$key' = '$valueToSend'")
                            val injected = service.injectText(valueToSend)
                            if (!injected) {
                                failStep("FAILED_TO_INJECT: Failed to inject variable value '$valueToSend' into field after all retry strategies.")
                                return@withTimeout
                            }

                            val clicked = service.clickSendButton()
                            if (!clicked) {
                                failStep("FAILED_TO_INJECT: Failed to click Send button after entering '$key'.")
                                return@withTimeout
                            }

                            advanceStep()
                        }

                        StepType.MANUAL -> {
                            val timeout = step.waitTime ?: 40000L
                            updateStatus(
                                EngineState.WAITING_FOR_MANUAL,
                                currentStepIndex + 1,
                                steps.size,
                                promptText,
                                "Paused for manual input (Timeout ${timeout / 1000}s)..."
                            )

                            manualWaitJob = scope.launch {
                                delay(timeout)
                                if (isEngineRunning && _status.value.state == EngineState.WAITING_FOR_MANUAL) {
                                    log("Manual step timed out after ${timeout / 1000}s. Auto-advancing.")
                                    advanceStep()
                                }
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                failStep("Step timed out waiting for accessibility service response")
            }
        }
    }

    private fun advanceStep() {
        // Reset dedupe key for the next step transition so repeat prompts are accepted
        lastProcessedPrompt = ""
        currentStepIndex++

        val payload = currentPayload
        if (payload != null && currentStepIndex >= payload.steps.size) {
            isEngineRunning = false
            unregisterCallStateListener()
            updateStatus(
                EngineState.DONE,
                payload.steps.size,
                payload.steps.size,
                _status.value.currentPromptText,
                "USSD Flow Execution Complete!",
                null
            )
        } else {
            updateStatus(
                EngineState.WAITING_FOR_PROMPT,
                currentStepIndex + 1,
                payload?.steps?.size ?: 0,
                _status.value.currentPromptText,
                "Waiting for next carrier menu prompt..."
            )
            startProbeLoop()
        }
    }

    private fun startProbeLoop() {
        probeJob?.cancel()
        probeJob = scope.launch {
            log("Starting probe loop for carrier USSD prompt...")
            val timeoutSec = currentPayload?.timeoutSeconds?.coerceIn(5, 120) ?: 40
            val maxAttempts = ((timeoutSec * 1000L) / 300L).toInt()
            var probeAttempts = 0
            while (isEngineRunning && _status.value.state == EngineState.WAITING_FOR_PROMPT && probeAttempts < maxAttempts) {
                delay(300)
                probeAttempts++
                val service = UssdAccessibilityService.instance
                if (service != null && UssdAccessibilityService.isServiceActive.value) {
                    service.evaluateScreen()
                }
            }
            if (isEngineRunning && _status.value.state == EngineState.WAITING_FOR_PROMPT && probeAttempts >= maxAttempts) {
                failStep("Timeout waiting for USSD prompt from carrier after $timeoutSec seconds.")
            }
        }
    }

    private fun failStep(reason: String) {
        isEngineRunning = false
        probeJob?.cancel()
        manualWaitJob?.cancel()
        unregisterCallStateListener()

        log("❌ Flow Failed: $reason")
        updateStatus(
            EngineState.ERROR,
            currentStepIndex + 1,
            currentPayload?.steps?.size ?: 0,
            _status.value.currentPromptText,
            "Execution Failed",
            reason
        )
    }

    private fun dialUssdCode(code: String, simSlot: Int = 1): Boolean {
        return try {
            val cleanCode = code.ifBlank { "*170#" }
            val encodedCode = cleanCode.replace("#", Uri.encode("#"))
            val uri = Uri.parse("tel:$encodedCode")
 
            val intent = Intent(Intent.ACTION_CALL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            registerCallStateListener()
            true
        } catch (e: Exception) {
            try {
                val cleanCode = code.ifBlank { "*170#" }
                val encodedCode = cleanCode.replace("#", Uri.encode("#"))
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$encodedCode")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                registerCallStateListener()
                true
            } catch (ex: Exception) {
                ex.printStackTrace()
                false
            }
        }
    }

    private fun substituteVariables(template: String, userInputs: Map<String, String>): String {
        var result = template
        for ((key, value) in userInputs) {
            result = result.replace("{{$key}}", value, ignoreCase = true)
            result = result.replace("{$key}", value, ignoreCase = true)
        }
        return result
    }

    private fun lookupVariable(key: String, userInputs: Map<String, String>): String? {
        if (userInputs.containsKey(key)) return userInputs[key]
        val match = userInputs.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
        return match?.value
    }

    private fun log(message: String) {
        val currentLogs = _status.value.logs.toMutableList()
        currentLogs.add(message)
        _status.value = _status.value.copy(logs = currentLogs)
    }

    private fun updateStatus(
        state: EngineState,
        stepIndex: Int,
        totalSteps: Int,
        promptText: String,
        message: String,
        errorMessage: String? = null
    ) {
        val currentLogs = _status.value.logs.toMutableList()
        currentLogs.add("[$state] $message")

        _status.value = FlowEngineStatus(
            state = state,
            currentStepIndex = stepIndex,
            totalSteps = totalSteps,
            currentPromptText = promptText,
            message = message,
            errorMessage = errorMessage,
            logs = currentLogs
        )
    }
}
