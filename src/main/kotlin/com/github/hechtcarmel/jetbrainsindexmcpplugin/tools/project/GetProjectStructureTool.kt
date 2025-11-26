package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.project

import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.ModuleInfo
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.ProjectStructureResult
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class GetProjectStructureTool : AbstractMcpTool() {

    override val name = "ide_project_structure"

    override val description = """
        Retrieves the project's module structure including all source roots, test roots, and resource directories.
        Use when understanding project layout, locating source directories, or configuring build paths.
        Use at the start of a session to understand the overall project organization.
        Returns project name, base path, and a list of modules with their source, test, and resource roots.
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
            val moduleManager = ModuleManager.getInstance(project)
            val modules = moduleManager.modules.map { module ->
                val rootManager = ModuleRootManager.getInstance(module)

                val sourceRoots = mutableListOf<String>()
                val testRoots = mutableListOf<String>()
                val resourceRoots = mutableListOf<String>()

                rootManager.contentEntries.forEach { contentEntry ->
                    contentEntry.sourceFolders.forEach { sourceFolder ->
                        val relativePath = sourceFolder.file?.let { getRelativePath(project, it) }
                            ?: sourceFolder.url.removePrefix("file://").removePrefix(project.basePath ?: "").removePrefix("/")

                        when {
                            sourceFolder.isTestSource -> testRoots.add(relativePath)
                            sourceFolder.rootType.toString().contains("resource", ignoreCase = true) -> {
                                resourceRoots.add(relativePath)
                            }
                            else -> sourceRoots.add(relativePath)
                        }
                    }
                }

                ModuleInfo(
                    name = module.name,
                    sourceRoots = sourceRoots,
                    testRoots = testRoots,
                    resourceRoots = resourceRoots
                )
            }

            createJsonResult(ProjectStructureResult(
                name = project.name,
                basePath = project.basePath,
                modules = modules
            ))
        }
    }
}
