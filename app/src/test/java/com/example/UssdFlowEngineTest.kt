package com.example

import com.example.data.model.FlowPayload
import com.example.data.model.UserSettings
import com.example.data.model.UssdFlow
import com.example.data.model.toFlowPayload
import org.junit.Assert.assertEquals
import org.junit.Test

class UssdFlowEngineTest {

    @Test
    fun `flowPayload default automationDelayMs is 50ms`() {
        val payload = FlowPayload()
        assertEquals(50L, payload.automationDelayMs)
    }

    @Test
    fun `userSettings default automationDelayMs is 50ms`() {
        val settings = UserSettings()
        assertEquals(50L, settings.automationDelayMs)
    }

    @Test
    fun `toFlowPayload inherits custom automationDelayMs`() {
        val flow = UssdFlow(
            id = "test-1",
            name = "Test Flow",
            description = "Test Description",
            entryUssdCode = "*100#",
            category = com.example.data.model.FlowCategory.CUSTOM
        )
        val payload = flow.toFlowPayload(automationDelayMs = 250L)
        assertEquals(250L, payload.automationDelayMs)
    }
}
