package com.github.hechtcarmel.jetbrainsindexmcpplugin.util

import junit.framework.TestCase

class ClientConfigGeneratorUnitTest : TestCase() {

    // ClientType enum tests

    fun testAllClientTypesHaveDisplayNames() {
        ClientConfigGenerator.ClientType.entries.forEach { clientType ->
            assertTrue(
                "ClientType ${clientType.name} should have non-empty displayName",
                clientType.displayName.isNotEmpty()
            )
        }
    }

    fun testExpectedClientTypesExist() {
        val expectedTypes = listOf(
            "CLAUDE_CODE",
            "CLAUDE_DESKTOP",
            "CURSOR",
            "VSCODE",
            "WINDSURF"
        )

        val actualTypes = ClientConfigGenerator.ClientType.entries.map { it.name }

        expectedTypes.forEach { expected ->
            assertTrue("ClientType $expected should exist", actualTypes.contains(expected))
        }
    }

    fun testClientTypeDisplayNames() {
        assertEquals("Claude Code (CLI)", ClientConfigGenerator.ClientType.CLAUDE_CODE.displayName)
        assertEquals("Claude Desktop", ClientConfigGenerator.ClientType.CLAUDE_DESKTOP.displayName)
        assertEquals("Cursor", ClientConfigGenerator.ClientType.CURSOR.displayName)
        assertEquals("VS Code (Generic MCP)", ClientConfigGenerator.ClientType.VSCODE.displayName)
        assertEquals("Windsurf", ClientConfigGenerator.ClientType.WINDSURF.displayName)
    }

    // getAvailableClients tests

    fun testGetAvailableClientsReturnsAllTypes() {
        val clients = ClientConfigGenerator.getAvailableClients()

        assertEquals(
            "Should return all client types",
            ClientConfigGenerator.ClientType.entries.size,
            clients.size
        )
    }

    fun testGetAvailableClientsContainsAllEntries() {
        val clients = ClientConfigGenerator.getAvailableClients()

        ClientConfigGenerator.ClientType.entries.forEach { clientType ->
            assertTrue(
                "Available clients should contain $clientType",
                clients.contains(clientType)
            )
        }
    }

    // getConfigLocationHint tests

    fun testClaudeCodeHintContainsTerminalInstructions() {
        val hint = ClientConfigGenerator.getConfigLocationHint(ClientConfigGenerator.ClientType.CLAUDE_CODE)

        assertTrue("Should mention terminal", hint.contains("terminal"))
        assertTrue("Should mention scope user", hint.contains("--scope user"))
        assertTrue("Should mention scope project", hint.contains("--scope project"))
        assertTrue("Should mention remove command", hint.contains("mcp remove"))
    }

    fun testClaudeDesktopHintContainsConfigPaths() {
        val hint = ClientConfigGenerator.getConfigLocationHint(ClientConfigGenerator.ClientType.CLAUDE_DESKTOP)

        assertTrue("Should mention macOS path", hint.contains("macOS"))
        assertTrue("Should mention Windows path", hint.contains("Windows"))
        assertTrue("Should mention Linux path", hint.contains("Linux"))
        assertTrue("Should mention config file", hint.contains("claude_desktop_config.json"))
    }

    fun testCursorHintContainsConfigPaths() {
        val hint = ClientConfigGenerator.getConfigLocationHint(ClientConfigGenerator.ClientType.CURSOR)

        assertTrue("Should mention mcp.json", hint.contains("mcp.json"))
        assertTrue("Should mention project-local", hint.contains(".cursor"))
        assertTrue("Should mention global", hint.contains("~/.cursor"))
    }

    fun testVSCodeHintContainsSettingsInfo() {
        val hint = ClientConfigGenerator.getConfigLocationHint(ClientConfigGenerator.ClientType.VSCODE)

        assertTrue("Should mention settings", hint.contains("settings"))
        assertTrue("Should mention JSON", hint.contains("JSON") || hint.contains("json"))
    }

    fun testWindsurfHintContainsConfigPath() {
        val hint = ClientConfigGenerator.getConfigLocationHint(ClientConfigGenerator.ClientType.WINDSURF)

        assertTrue("Should mention config file", hint.contains("mcp_config.json"))
        assertTrue("Should mention codeium path", hint.contains(".codeium"))
    }

    fun testAllHintsAreNonEmpty() {
        ClientConfigGenerator.ClientType.entries.forEach { clientType ->
            val hint = ClientConfigGenerator.getConfigLocationHint(clientType)
            assertTrue(
                "Hint for $clientType should be non-empty",
                hint.isNotEmpty()
            )
        }
    }

    // General enum tests

    fun testClientTypeValuesAreUnique() {
        val names = ClientConfigGenerator.ClientType.entries.map { it.name }
        val displayNames = ClientConfigGenerator.ClientType.entries.map { it.displayName }

        assertEquals("Names should be unique", names.size, names.toSet().size)
        assertEquals("Display names should be unique", displayNames.size, displayNames.toSet().size)
    }
}
