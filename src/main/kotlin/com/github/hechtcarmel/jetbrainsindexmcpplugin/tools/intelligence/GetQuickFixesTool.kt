package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.intelligence

import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.QuickFixInfo
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.QuickFixesResult
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.UUID

class GetQuickFixesTool : AbstractMcpTool() {

    override val name = "get_quick_fixes"

    override val description = """
        Get available quick fixes at a specific position in a file.
        Returns a list of fixes with unique IDs that can be applied using apply_quick_fix.
    """.trimIndent()

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("project_path") {
                put("type", "string")
                put("description", "Absolute path to the project root. Required when multiple projects are open.")
            }
            putJsonObject("file") {
                put("type", "string")
                put("description", "Path to the file relative to project root")
            }
            putJsonObject("line") {
                put("type", "integer")
                put("description", "1-based line number")
            }
            putJsonObject("column") {
                put("type", "integer")
                put("description", "1-based column number")
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("file"))
            add(JsonPrimitive("line"))
            add(JsonPrimitive("column"))
        }
    }

    companion object {
        // Cache of quick fixes keyed by generated ID - for later retrieval by ApplyQuickFixTool
        private val quickFixCache = mutableMapOf<String, CachedQuickFix>()

        data class CachedQuickFix(
            val action: IntentionAction,
            val file: String,
            val offset: Int,
            val timestamp: Long = System.currentTimeMillis()
        )

        fun getCachedQuickFix(fixId: String): CachedQuickFix? {
            // Clean up old entries (older than 5 minutes)
            val cutoff = System.currentTimeMillis() - 5 * 60 * 1000
            quickFixCache.entries.removeIf { it.value.timestamp < cutoff }
            return quickFixCache[fixId]
        }

        fun cacheQuickFix(action: IntentionAction, file: String, offset: Int): String {
            val fixId = UUID.randomUUID().toString().take(8)
            quickFixCache[fixId] = CachedQuickFix(action, file, offset)
            return fixId
        }
    }

    override suspend fun execute(project: Project, arguments: JsonObject): ToolCallResult {
        val file = arguments["file"]?.jsonPrimitive?.content
            ?: return createErrorResult("Missing required parameter: file")
        val line = arguments["line"]?.jsonPrimitive?.int
            ?: return createErrorResult("Missing required parameter: line")
        val column = arguments["column"]?.jsonPrimitive?.int
            ?: return createErrorResult("Missing required parameter: column")

        requireSmartMode(project)

        return readAction {
            val psiFile = getPsiFile(project, file)
                ?: return@readAction createErrorResult("File not found: $file")

            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                ?: return@readAction createErrorResult("Could not get document for file")

            val offset = getOffset(document, line, column)
                ?: return@readAction createErrorResult("Invalid position: line $line, column $column")

            val fixes = mutableListOf<QuickFixInfo>()

            try {
                // Get highlights at the position
                DaemonCodeAnalyzerEx.processHighlights(
                    document,
                    project,
                    HighlightSeverity.INFORMATION,
                    offset,
                    offset + 1
                ) { highlightInfo ->
                    highlightInfo.findRegisteredQuickFix<IntentionAction> { descriptor, _ ->
                        val action = descriptor.action
                        if (action.isAvailable(project, null, psiFile)) {
                            val fixId = cacheQuickFix(action, file, offset)
                            fixes.add(QuickFixInfo(
                                id = fixId,
                                name = action.text,
                                description = action.familyName.takeIf { it != action.text }
                            ))
                        }
                        null
                    }
                    true
                }

                // Also check for intention actions at the element
                val element = psiFile.findElementAt(offset)
                if (element != null) {
                    com.intellij.codeInsight.intention.IntentionManager.getInstance()
                        .getAvailableIntentions()
                        .filter { it.isAvailable(project, null, psiFile) }
                        .take(20) // Limit general intentions
                        .forEach { action ->
                            val fixId = cacheQuickFix(action, file, offset)
                            fixes.add(QuickFixInfo(
                                id = fixId,
                                name = action.text,
                                description = action.familyName.takeIf { it != action.text }
                            ))
                        }
                }
            } catch (e: Exception) {
                // Quick fix discovery might fail
            }

            createJsonResult(QuickFixesResult(
                fixes = fixes.distinctBy { it.name }
            ))
        }
    }
}
