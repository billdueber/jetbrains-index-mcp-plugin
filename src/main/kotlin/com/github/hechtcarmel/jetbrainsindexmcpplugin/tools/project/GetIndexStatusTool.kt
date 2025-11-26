package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.project

import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.IndexStatusResult
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class GetIndexStatusTool : AbstractMcpTool() {

    override val name = "ide_index_status"

    override val description = """
        Checks the IDE's indexing status to determine if code intelligence features are available.
        Use before calling other tools to verify the IDE is ready for semantic operations.
        Use when operations fail due to indexing to check if the IDE is still processing files.
        Returns isDumbMode (true = indexing in progress, limited functionality) and isIndexing flags.
    """.trimIndent()

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("project_path") {
                put("type", "string")
                put("description", "Absolute path to the project root. Required when multiple projects are open.")
            }
        }
        put("required", buildJsonArray { })
    }

    override suspend fun execute(project: Project, arguments: JsonObject): ToolCallResult {
        val dumbService = DumbService.getInstance(project)
        val isDumb = dumbService.isDumb

        return createJsonResult(IndexStatusResult(
            isDumbMode = isDumb,
            isIndexing = isDumb,
            indexingProgress = null
        ))
    }
}
