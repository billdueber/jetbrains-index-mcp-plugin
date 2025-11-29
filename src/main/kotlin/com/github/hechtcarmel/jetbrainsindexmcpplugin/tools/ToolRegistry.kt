package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools

import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolDefinition
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.intelligence.GetDiagnosticsTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.FindUsagesTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.FindDefinitionTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.project.GetIndexStatusTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.JavaPluginDetector
import com.intellij.openapi.diagnostic.logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for MCP tools available to AI assistants.
 *
 * The registry manages the lifecycle of tools and provides thread-safe access
 * for tool lookup and definition generation.
 *
 * ## Built-in Tools
 *
 * The registry automatically registers built-in tools based on IDE capabilities.
 *
 * ### Universal Tools (All JetBrains IDEs)
 *
 * These tools work in all JetBrains IDEs (IntelliJ, PyCharm, WebStorm, GoLand, etc.):
 *
 * - `ide_find_references` - Find all usages of a symbol
 * - `ide_find_definition` - Find symbol definition location
 * - `ide_diagnostics` - Analyze code for problems and available intentions
 * - `ide_index_status` - Check indexing status
 *
 * ### Java-Specific Tools (IntelliJ IDEA & Android Studio Only)
 *
 * These tools require the Java plugin and are only available in IntelliJ IDEA
 * and Android Studio:
 *
 * - `ide_type_hierarchy` - Get class inheritance hierarchy
 * - `ide_call_hierarchy` - Analyze method call relationships
 * - `ide_find_implementations` - Find interface/method implementations
 * - `ide_find_symbol` - Search for symbols by name
 * - `ide_find_super_methods` - Find methods that a method overrides
 * - `ide_refactor_rename` - Rename symbol
 * - `ide_refactor_safe_delete` - Safely delete element
 *
 * ## Custom Tool Registration
 *
 * Custom tools can be registered programmatically using [register].
 *
 * @see McpTool
 * @see McpServerService
 * @see JavaPluginDetector
 */
class ToolRegistry {

    companion object {
        private val LOG = logger<ToolRegistry>()
    }

    private val tools = ConcurrentHashMap<String, McpTool>()

    /**
     * Registers a tool with the registry.
     *
     * If a tool with the same name already exists, it will be replaced.
     *
     * @param tool The tool to register
     */
    fun register(tool: McpTool) {
        tools[tool.name] = tool
        LOG.info("Registered MCP tool: ${tool.name}")
    }

    /**
     * Removes a tool from the registry.
     *
     * @param toolName The name of the tool to remove
     */
    fun unregister(toolName: String) {
        tools.remove(toolName)
        LOG.info("Unregistered MCP tool: $toolName")
    }

    /**
     * Gets a tool by name.
     *
     * @param name The tool name (e.g., `ide_find_references`)
     * @return The tool, or null if not found
     */
    fun getTool(name: String): McpTool? {
        return tools[name]
    }

    /**
     * Returns all registered tools.
     *
     * @return List of all tools
     */
    fun getAllTools(): List<McpTool> {
        return tools.values.toList()
    }

    /**
     * Gets tool definitions for the MCP `tools/list` response.
     *
     * @return List of tool definitions with name, description, and schema
     */
    fun getToolDefinitions(): List<ToolDefinition> {
        return tools.values.map { tool ->
            ToolDefinition(
                name = tool.name,
                description = tool.description,
                inputSchema = tool.inputSchema
            )
        }
    }

    /**
     * Registers all built-in tools.
     *
     * This is called automatically during [McpServerService] initialization.
     * Tools are registered conditionally based on IDE capabilities:
     * - Universal tools are always registered
     * - Java-specific tools are only registered when the Java plugin is available
     */
    fun registerBuiltInTools() {
        // Universal tools - work in all JetBrains IDEs
        registerUniversalTools()

        // Java-specific tools - only available when Java plugin is present
        if (JavaPluginDetector.isJavaPluginAvailable) {
            registerJavaTools()
        }

        LOG.info("Registered ${tools.size} built-in MCP tools (Java plugin available: ${JavaPluginDetector.isJavaPluginAvailable})")
    }

    /**
     * Registers universal tools that work in all JetBrains IDEs.
     *
     * These tools use only platform APIs (com.intellij.modules.platform)
     * and do not depend on Java-specific PSI classes.
     */
    private fun registerUniversalTools() {
        // Navigation tools (universal)
        register(FindUsagesTool())
        register(FindDefinitionTool())

        // Intelligence tools
        register(GetDiagnosticsTool())

        // Project tools
        register(GetIndexStatusTool())

        LOG.info("Registered universal tools (available in all JetBrains IDEs)")
    }

    /**
     * Registers Java-specific tools that require the Java plugin.
     *
     * These tools use Java-specific PSI classes (PsiClass, PsiMethod, JavaPsiFacade, etc.)
     * and are only available in IntelliJ IDEA and Android Studio.
     *
     * IMPORTANT: This method must only be called after checking [JavaPluginDetector.isJavaPluginAvailable]
     */
    private fun registerJavaTools() {
        // Import Java-specific tools dynamically to avoid class loading errors
        // when Java plugin is not available
        try {
            val javaToolClasses = listOf(
                "com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.TypeHierarchyTool",
                "com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.CallHierarchyTool",
                "com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.FindImplementationsTool",
                "com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.FindSymbolTool",
                "com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation.FindSuperMethodsTool",
                "com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.refactoring.RenameSymbolTool",
                "com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.refactoring.SafeDeleteTool"
            )

            for (className in javaToolClasses) {
                val toolClass = Class.forName(className)
                val tool = toolClass.getDeclaredConstructor().newInstance() as McpTool
                register(tool)
            }

            LOG.info("Registered Java-specific tools (IntelliJ IDEA / Android Studio)")
        } catch (e: Exception) {
            LOG.warn("Failed to register Java-specific tools: ${e.message}")
        }
    }
}
