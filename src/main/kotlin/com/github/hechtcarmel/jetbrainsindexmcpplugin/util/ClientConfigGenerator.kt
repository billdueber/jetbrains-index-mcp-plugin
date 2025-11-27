package com.github.hechtcarmel.jetbrainsindexmcpplugin.util

import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.McpServerService

/**
 * Generates MCP client configuration snippets for various AI coding assistants.
 *
 * This utility generates ready-to-use configuration for:
 * - Claude Code (CLI)
 * - Claude Desktop
 * - Cursor
 * - VS Code (generic MCP)
 * - Windsurf
 *
 * All configurations use the HTTP+SSE transport to connect to the IDE's built-in web server.
 */
object ClientConfigGenerator {

    /**
     * Supported MCP client types.
     */
    enum class ClientType(val displayName: String) {
        CLAUDE_CODE("Claude Code (CLI)"),
        CLAUDE_DESKTOP("Claude Desktop"),
        CURSOR("Cursor"),
        VSCODE("VS Code (Generic MCP)"),
        WINDSURF("Windsurf")
    }

    /**
     * Generates the MCP configuration for the specified client type.
     *
     * @param clientType The type of MCP client to generate configuration for
     * @param serverName Optional custom name for the server (defaults to "intellij-index")
     * @return The configuration string in the appropriate format for the client
     */
    fun generateConfig(clientType: ClientType, serverName: String = "intellij-index"): String {
        val serverUrl = McpServerService.getInstance().getServerUrl()

        return when (clientType) {
            ClientType.CLAUDE_CODE -> generateClaudeCodeConfig(serverUrl, serverName)
            ClientType.CLAUDE_DESKTOP -> generateClaudeDesktopConfig(serverUrl, serverName)
            ClientType.CURSOR -> generateCursorConfig(serverUrl, serverName)
            ClientType.VSCODE -> generateVSCodeConfig(serverUrl, serverName)
            ClientType.WINDSURF -> generateWindsurfConfig(serverUrl, serverName)
        }
    }

    /**
     * Generates Claude Code CLI command.
     *
     * Run this command in your terminal to add the MCP server.
     * Use --scope user for global or --scope project for project-local.
     */
    private fun generateClaudeCodeConfig(serverUrl: String, serverName: String): String {
        return "claude mcp add --transport http $serverName $serverUrl --scope user"
    }

    /**
     * Generates Claude Desktop configuration.
     *
     * Add this to ~/Library/Application Support/Claude/claude_desktop_config.json (macOS)
     * or %APPDATA%\Claude\claude_desktop_config.json (Windows)
     */
    private fun generateClaudeDesktopConfig(serverUrl: String, serverName: String): String {
        return """
{
  "mcpServers": {
    "$serverName": {
      "url": "$serverUrl"
    }
  }
}
        """.trimIndent()
    }

    /**
     * Generates Cursor MCP configuration.
     *
     * Add this to .cursor/mcp.json in your project root or globally at
     * ~/.cursor/mcp.json
     */
    private fun generateCursorConfig(serverUrl: String, serverName: String): String {
        return """
{
  "mcpServers": {
    "$serverName": {
      "url": "$serverUrl"
    }
  }
}
        """.trimIndent()
    }

    /**
     * Generates generic VS Code MCP configuration.
     *
     * Add to your VS Code settings.json or workspace configuration.
     */
    private fun generateVSCodeConfig(serverUrl: String, serverName: String): String {
        return """
{
  "mcp.servers": {
    "$serverName": {
      "transport": "sse",
      "url": "$serverUrl"
    }
  }
}
        """.trimIndent()
    }

    /**
     * Generates Windsurf MCP configuration.
     *
     * Add this to ~/.codeium/windsurf/mcp_config.json
     */
    private fun generateWindsurfConfig(serverUrl: String, serverName: String): String {
        return """
{
  "mcpServers": {
    "$serverName": {
      "serverUrl": "$serverUrl"
    }
  }
}
        """.trimIndent()
    }

    /**
     * Returns a human-readable description of where to add the configuration
     * for the specified client type.
     */
    fun getConfigLocationHint(clientType: ClientType): String {
        return when (clientType) {
            ClientType.CLAUDE_CODE -> """
                Run this command in your terminal:
                • --scope user: Adds globally for all projects
                • --scope project: Adds to current project only

                To remove: claude mcp remove intellij-index
            """.trimIndent()

            ClientType.CLAUDE_DESKTOP -> """
                Add to your Claude Desktop configuration file:
                • macOS: ~/Library/Application Support/Claude/claude_desktop_config.json
                • Windows: %APPDATA%\Claude\claude_desktop_config.json
                • Linux: ~/.config/Claude/claude_desktop_config.json
            """.trimIndent()

            ClientType.CURSOR -> """
                Add to your Cursor MCP configuration:
                • Project-local: .cursor/mcp.json in your project root
                • Global: ~/.cursor/mcp.json
            """.trimIndent()

            ClientType.VSCODE -> """
                Add to your VS Code settings:
                • Open Settings (JSON) and add the configuration
                • Or add to .vscode/settings.json in your project
            """.trimIndent()

            ClientType.WINDSURF -> """
                Add to your Windsurf MCP configuration:
                • Config file: ~/.codeium/windsurf/mcp_config.json
            """.trimIndent()
        }
    }

    /**
     * Returns all available client types for UI display.
     */
    fun getAvailableClients(): List<ClientType> = ClientType.entries
}
