package com.github.hechtcarmel.jetbrainsindexmcpplugin.server

import com.github.hechtcarmel.jetbrainsindexmcpplugin.McpConstants
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.ToolRegistry
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.ide.BuiltInServerManager

/**
 * Application-level service managing the MCP server infrastructure.
 *
 * This service manages:
 * - Tool registry for MCP tools
 * - JSON-RPC handler for message processing
 *
 * Uses HTTP+SSE transport for compatibility with MCP clients.
 */
@Service(Service.Level.APP)
class McpServerService : Disposable {

    private val toolRegistry: ToolRegistry = ToolRegistry()
    private val jsonRpcHandler: JsonRpcHandler

    companion object {
        private val LOG = logger<McpServerService>()

        fun getInstance(): McpServerService = service()
    }

    init {
        LOG.info("Initializing MCP Server Service (Protocol: ${McpConstants.MCP_PROTOCOL_VERSION})")
        jsonRpcHandler = JsonRpcHandler(toolRegistry)

        // Register built-in tools
        toolRegistry.registerBuiltInTools()

        LOG.info("MCP Server Service initialized with HTTP+SSE transport")
    }

    fun getToolRegistry(): ToolRegistry = toolRegistry

    fun getJsonRpcHandler(): JsonRpcHandler = jsonRpcHandler

    /**
     * Returns the SSE endpoint URL for MCP connections.
     * Clients should connect to this URL to establish SSE stream.
     */
    fun getServerUrl(): String {
        val port = BuiltInServerManager.getInstance().port
        return "http://localhost:$port${McpConstants.MCP_ENDPOINT_PATH}/sse"
    }

    /**
     * Returns the IDE's built-in server port.
     */
    fun getServerPort(): Int {
        return BuiltInServerManager.getInstance().port
    }

    /**
     * Returns information about the server status.
     */
    fun getServerInfo(): ServerStatusInfo {
        return ServerStatusInfo(
            name = McpConstants.SERVER_NAME,
            version = McpConstants.SERVER_VERSION,
            protocolVersion = McpConstants.MCP_PROTOCOL_VERSION,
            sseUrl = getServerUrl(),
            postUrl = "http://localhost:${getServerPort()}${McpConstants.MCP_ENDPOINT_PATH}",
            port = getServerPort(),
            registeredTools = toolRegistry.getAllTools().size
        )
    }

    override fun dispose() {
        LOG.info("Disposing MCP Server Service")
    }
}

/**
 * Data class containing server status information.
 */
data class ServerStatusInfo(
    val name: String,
    val version: String,
    val protocolVersion: String,
    val sseUrl: String,
    val postUrl: String,
    val port: Int,
    val registeredTools: Int
)
