package com.github.hechtcarmel.jetbrainsindexmcpplugin.resources

import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ResourceContent
import com.intellij.openapi.project.Project

/**
 * Interface for MCP (Model Context Protocol) resources that can be read by AI assistants.
 *
 * MCP resources provide read-only access to project state and data. Unlike tools,
 * resources don't perform actions - they return information about the current state.
 *
 * ## Built-in Resources
 *
 * The plugin provides these built-in resources:
 * - `index://status` - IDE indexing status
 * - `project://structure` - Project module tree
 * - `file://content/{path}` - File content by path
 * - `symbol://info/{fqn}` - Symbol information by fully qualified name
 *
 * ## Implementing a Custom Resource
 *
 * ```kotlin
 * class MyResource : McpResource {
 *     override val uri = "my://resource"
 *     override val name = "My Resource"
 *     override val description = "Returns custom data"
 *     override val mimeType = "application/json"
 *
 *     override suspend fun read(project: Project): ResourceContent {
 *         val data = // ... gather data
 *         return ResourceContent(
 *             uri = uri,
 *             mimeType = mimeType,
 *             text = Json.encodeToString(data)
 *         )
 *     }
 * }
 * ```
 *
 * @see ResourceRegistry
 * @see ResourceContent
 */
interface McpResource {
    /**
     * The unique URI identifying this resource.
     *
     * URIs can be fixed (e.g., `index://status`) or use patterns with parameters
     * (e.g., `file://content/{path}`). The [ResourceRegistry] handles pattern matching
     * for parameterized URIs.
     */
    val uri: String

    /**
     * Human-readable name for the resource.
     *
     * Shown to users in resource listings.
     */
    val name: String

    /**
     * Description of what the resource provides.
     *
     * Helps AI assistants understand when to use this resource.
     */
    val description: String

    /**
     * MIME type of the resource content.
     *
     * Common values: `application/json`, `text/plain`
     */
    val mimeType: String

    /**
     * Reads and returns the resource content.
     *
     * @param project The project context
     * @return A [ResourceContent] with the resource data
     */
    suspend fun read(project: Project): ResourceContent
}
