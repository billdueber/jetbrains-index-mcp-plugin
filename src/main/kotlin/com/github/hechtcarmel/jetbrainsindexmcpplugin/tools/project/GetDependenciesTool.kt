package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.project

import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.DependenciesResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.DependencyInfo
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderEnumerator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class GetDependenciesTool : AbstractMcpTool() {

    override val name = "get_dependencies"

    override val description = """
        Get the list of project dependencies (libraries and their versions).
        Returns all library dependencies across all modules in the project.
    """.trimIndent()

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("project_path") {
                put("type", "string")
                put("description", "Absolute path to the project root. Required when multiple projects are open.")
            }
        }
    }

    override suspend fun execute(project: Project, arguments: JsonObject): ToolCallResult {
        return readAction {
            val dependencies = mutableSetOf<DependencyInfo>()

            // Get dependencies from all modules
            val moduleManager = ModuleManager.getInstance(project)
            moduleManager.modules.forEach { module ->
                OrderEnumerator.orderEntries(module)
                    .librariesOnly()
                    .forEachLibrary { library ->
                        val name = library.name ?: return@forEachLibrary true

                        // Parse library name to extract version
                        // Common formats: "Gradle: group:artifact:version" or "Maven: group:artifact:version"
                        val parsed = parseLibraryName(name)

                        dependencies.add(DependencyInfo(
                            name = parsed.name,
                            version = parsed.version,
                            scope = getScope(module, library.name)
                        ))

                        true
                    }
            }

            createJsonResult(DependenciesResult(
                dependencies = dependencies.toList().sortedBy { it.name }
            ))
        }
    }

    private data class ParsedLibrary(val name: String, val version: String?)

    private fun parseLibraryName(fullName: String): ParsedLibrary {
        // Remove common prefixes
        val name = fullName
            .removePrefix("Gradle: ")
            .removePrefix("Maven: ")
            .removePrefix("Ivy: ")

        // Try to extract version from common patterns
        // Pattern: group:artifact:version or group:artifact:version:classifier
        val parts = name.split(":")
        return when {
            parts.size >= 3 -> {
                val artifactName = "${parts[0]}:${parts[1]}"
                val version = parts[2]
                ParsedLibrary(artifactName, version)
            }
            parts.size == 2 -> {
                ParsedLibrary(name, null)
            }
            else -> {
                // Try to extract version from end with common patterns like "-1.0.0" or ".1.0.0"
                val versionPattern = Regex("[-.]?(\\d+\\.\\d+\\.\\d+[\\w.-]*)$")
                val match = versionPattern.find(name)
                if (match != null) {
                    val version = match.groupValues[1]
                    val baseName = name.substring(0, match.range.first)
                    ParsedLibrary(baseName, version)
                } else {
                    ParsedLibrary(name, null)
                }
            }
        }
    }

    private fun getScope(module: com.intellij.openapi.module.Module, libraryName: String?): String? {
        if (libraryName == null) return null

        try {
            val rootManager = ModuleRootManager.getInstance(module)
            rootManager.orderEntries.forEach { entry ->
                if (entry is LibraryOrderEntry && entry.libraryName == libraryName) {
                    return entry.scope.name
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        return null
    }
}
