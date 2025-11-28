package com.github.hechtcarmel.jetbrainsindexmcpplugin.settings

import junit.framework.TestCase

class McpSettingsUnitTest : TestCase() {

    // State default values tests

    fun testStateDefaultValues() {
        val state = McpSettings.State()

        assertEquals("Default maxHistorySize should be 100", 100, state.maxHistorySize)
        assertTrue("Default autoScroll should be true", state.autoScroll)
        assertTrue("Default showTimestamps should be true", state.showTimestamps)
        assertTrue("Default confirmWriteOperations should be true", state.confirmWriteOperations)
        assertFalse("Default logToFile should be false", state.logToFile)
        assertEquals("Default logFilePath should be empty", "", state.logFilePath)
    }

    // State mutability tests

    fun testStateMaxHistorySizeMutable() {
        val state = McpSettings.State()
        state.maxHistorySize = 200

        assertEquals(200, state.maxHistorySize)
    }

    fun testStateAutoScrollMutable() {
        val state = McpSettings.State()
        state.autoScroll = false

        assertFalse(state.autoScroll)
    }

    fun testStateShowTimestampsMutable() {
        val state = McpSettings.State()
        state.showTimestamps = false

        assertFalse(state.showTimestamps)
    }

    fun testStateConfirmWriteOperationsMutable() {
        val state = McpSettings.State()
        state.confirmWriteOperations = false

        assertFalse(state.confirmWriteOperations)
    }

    fun testStateLogToFileMutable() {
        val state = McpSettings.State()
        state.logToFile = true

        assertTrue(state.logToFile)
    }

    fun testStateLogFilePathMutable() {
        val state = McpSettings.State()
        state.logFilePath = "/var/log/mcp.log"

        assertEquals("/var/log/mcp.log", state.logFilePath)
    }

    // State custom constructor tests

    fun testStateCustomConstructor() {
        val state = McpSettings.State(
            maxHistorySize = 500,
            autoScroll = false,
            showTimestamps = false,
            confirmWriteOperations = false,
            logToFile = true,
            logFilePath = "/tmp/test.log"
        )

        assertEquals(500, state.maxHistorySize)
        assertFalse(state.autoScroll)
        assertFalse(state.showTimestamps)
        assertFalse(state.confirmWriteOperations)
        assertTrue(state.logToFile)
        assertEquals("/tmp/test.log", state.logFilePath)
    }

    // State copy tests

    fun testStateCopy() {
        val original = McpSettings.State(maxHistorySize = 50)
        val copy = original.copy(maxHistorySize = 150)

        assertEquals(50, original.maxHistorySize)
        assertEquals(150, copy.maxHistorySize)
        assertEquals(original.autoScroll, copy.autoScroll)
    }

    // State equals and hashCode tests

    fun testStateEquals() {
        val state1 = McpSettings.State()
        val state2 = McpSettings.State()

        assertEquals(state1, state2)
    }

    fun testStateNotEqualsWhenDifferent() {
        val state1 = McpSettings.State(maxHistorySize = 100)
        val state2 = McpSettings.State(maxHistorySize = 200)

        assertFalse(state1 == state2)
    }

    fun testStateHashCode() {
        val state1 = McpSettings.State()
        val state2 = McpSettings.State()

        assertEquals(state1.hashCode(), state2.hashCode())
    }

    // McpSettings instance tests

    fun testMcpSettingsInitialization() {
        val settings = McpSettings()

        // Should have default state
        assertNotNull(settings.state)
        assertEquals(100, settings.maxHistorySize)
        assertTrue(settings.autoScroll)
    }

    fun testMcpSettingsPropertyDelegation() {
        val settings = McpSettings()

        settings.maxHistorySize = 250
        settings.autoScroll = false
        settings.showTimestamps = false
        settings.confirmWriteOperations = false
        settings.logToFile = true
        settings.logFilePath = "/custom/path.log"

        assertEquals(250, settings.maxHistorySize)
        assertFalse(settings.autoScroll)
        assertFalse(settings.showTimestamps)
        assertFalse(settings.confirmWriteOperations)
        assertTrue(settings.logToFile)
        assertEquals("/custom/path.log", settings.logFilePath)
    }

    fun testMcpSettingsLoadState() {
        val settings = McpSettings()
        val newState = McpSettings.State(
            maxHistorySize = 75,
            autoScroll = false,
            showTimestamps = false,
            confirmWriteOperations = true,
            logToFile = true,
            logFilePath = "/loaded/path.log"
        )

        settings.loadState(newState)

        assertEquals(75, settings.maxHistorySize)
        assertFalse(settings.autoScroll)
        assertFalse(settings.showTimestamps)
        assertTrue(settings.confirmWriteOperations)
        assertTrue(settings.logToFile)
        assertEquals("/loaded/path.log", settings.logFilePath)
    }

    fun testMcpSettingsGetStateReturnsCurrentState() {
        val settings = McpSettings()
        settings.maxHistorySize = 300

        val state = settings.state

        assertEquals(300, state.maxHistorySize)
    }

    // Edge case tests

    fun testMaxHistorySizeZero() {
        val state = McpSettings.State(maxHistorySize = 0)
        assertEquals(0, state.maxHistorySize)
    }

    fun testMaxHistorySizeNegative() {
        val state = McpSettings.State(maxHistorySize = -1)
        assertEquals(-1, state.maxHistorySize)
    }

    fun testLogFilePathWithSpecialCharacters() {
        val path = "/path/with spaces/and-dashes/file.log"
        val state = McpSettings.State(logFilePath = path)
        assertEquals(path, state.logFilePath)
    }

    fun testLogFilePathWithUnicode() {
        val path = "/日本語/パス/ファイル.log"
        val state = McpSettings.State(logFilePath = path)
        assertEquals(path, state.logFilePath)
    }
}
