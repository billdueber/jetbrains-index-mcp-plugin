package com.github.hechtcarmel.jetbrainsindexmcpplugin.resources

import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ResourceDefinition
import com.intellij.openapi.diagnostic.logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for MCP resources available to AI assistants.
 *
 * The registry manages resources and supports both fixed URIs (e.g., `index://status`)
 * and parameterized URIs (e.g., `file://content/{path}`).
 *
 * ## Built-in Resources
 *
 * - `index://status` - IDE indexing status (dumb/smart mode)
 * - `project://structure` - Project module tree with source roots
 * - `file://content/{path}` - Read file content by path
 * - `symbol://info/{fqn}` - Symbol information by fully qualified name
 *
 * @see McpResource
 * @see McpServerService
 */
class ResourceRegistry {

    companion object {
        private val LOG = logger<ResourceRegistry>()
    }

    private val resources = ConcurrentHashMap<String, McpResource>()

    /**
     * Registers a resource with the registry.
     *
     * @param resource The resource to register
     */
    fun register(resource: McpResource) {
        resources[resource.uri] = resource
        LOG.info("Registered MCP resource: ${resource.uri}")
    }

    /**
     * Removes a resource from the registry.
     *
     * @param uri The URI of the resource to remove
     */
    fun unregister(uri: String) {
        resources.remove(uri)
        LOG.info("Unregistered MCP resource: $uri")
    }

    /**
     * Gets a resource by URI.
     *
     * Supports both exact matching and pattern matching for parameterized URIs.
     * For example, `file://content/src/Main.java` will match the pattern
     * `file://content/{path}`.
     *
     * @param uri The resource URI
     * @return The resource, or null if not found
     */
    fun getResource(uri: String): McpResource? {
        // First try exact match
        resources[uri]?.let { return it }

        // Then try pattern matching for parameterized URIs
        return resources.values.find { resource ->
            matchesPattern(resource.uri, uri)
        }
    }

    /**
     * Returns all registered resources.
     *
     * @return List of all resources
     */
    fun getAllResources(): List<McpResource> {
        return resources.values.toList()
    }

    /**
     * Gets resource definitions for the MCP `resources/list` response.
     *
     * @return List of resource definitions with URI, name, description, and MIME type
     */
    fun getResourceDefinitions(): List<ResourceDefinition> {
        return resources.values.map { resource ->
            ResourceDefinition(
                uri = resource.uri,
                name = resource.name,
                description = resource.description,
                mimeType = resource.mimeType
            )
        }
    }

    /**
     * Registers all built-in resources.
     *
     * This is called automatically during [McpServerService] initialization.
     */
    fun registerBuiltInResources() {
        register(IndexStatusResource())
        register(ProjectStructureResource())
        register(FileContentResource())
        register(SymbolInfoResource())
        LOG.info("Registered ${resources.size} built-in MCP resources")
    }

    private fun matchesPattern(pattern: String, uri: String): Boolean {
        // Simple pattern matching for URIs like "file://content/{path}"
        if (!pattern.contains("{")) {
            return pattern == uri
        }

        val patternParts = pattern.split("/")
        val uriParts = uri.split("/")

        if (patternParts.size > uriParts.size) return false

        for (i in patternParts.indices) {
            val patternPart = patternParts[i]
            val uriPart = if (i < uriParts.size) uriParts[i] else return false

            if (patternPart.startsWith("{") && patternPart.endsWith("}")) {
                // This is a parameter placeholder, any value matches
                continue
            }

            if (patternPart != uriPart) {
                return false
            }
        }

        return true
    }
}
