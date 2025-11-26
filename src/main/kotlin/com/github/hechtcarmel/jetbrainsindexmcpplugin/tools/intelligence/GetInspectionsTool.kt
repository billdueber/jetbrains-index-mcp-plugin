package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.intelligence

import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.InspectionsResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.ProblemInfo
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementVisitor
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class GetInspectionsTool : AbstractMcpTool() {

    override val name = "ide_analyze_code"

    override val description = """
        Runs IntelliJ's code inspections to detect errors, warnings, and code quality issues in a file.
        Use when checking for compilation errors, potential bugs, or code style violations.
        Use when verifying code changes haven't introduced new problems.
        Returns problems with severity (ERROR, WARNING, WEAK_WARNING, INFO), messages, and precise locations.
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
            putJsonObject("startLine") {
                put("type", "integer")
                put("description", "1-based start line for filtering (optional)")
            }
            putJsonObject("endLine") {
                put("type", "integer")
                put("description", "1-based end line for filtering (optional)")
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("file"))
        }
    }

    override suspend fun execute(project: Project, arguments: JsonObject): ToolCallResult {
        val file = arguments["file"]?.jsonPrimitive?.content
            ?: return createErrorResult("Missing required parameter: file")
        val startLine = arguments["startLine"]?.jsonPrimitive?.int
        val endLine = arguments["endLine"]?.jsonPrimitive?.int

        requireSmartMode(project)

        return readAction {
            val psiFile = getPsiFile(project, file)
                ?: return@readAction createErrorResult("File not found: $file")

            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                ?: return@readAction createErrorResult("Could not get document for file")

            val problems = mutableListOf<ProblemInfo>()

            try {
                val inspectionManager = InspectionManager.getInstance(project)
                val profile = InspectionProjectProfileManager.getInstance(project).currentProfile

                // Get all enabled inspection tools
                val tools = profile.getAllEnabledInspectionTools(project)

                tools.forEach { toolWrapper ->
                    try {
                        val tool = toolWrapper.tool
                        if (tool is LocalInspectionTool) {
                            val holder = ProblemsHolder(inspectionManager, psiFile, false)

                            val visitor = tool.buildVisitor(holder, false)
                            psiFile.accept(visitor)

                            holder.results.forEach { problemDescriptor ->
                                val element = problemDescriptor.psiElement ?: return@forEach
                                val problemOffset = element.textOffset
                                val problemLine = document.getLineNumber(problemOffset) + 1
                                val problemColumn = problemOffset - document.getLineStartOffset(problemLine - 1) + 1

                                // Apply line filter
                                if (startLine != null && problemLine < startLine) return@forEach
                                if (endLine != null && problemLine > endLine) return@forEach

                                val endOffset = element.textOffset + element.textLength
                                val endLineNum = document.getLineNumber(endOffset) + 1
                                val endColumnNum = endOffset - document.getLineStartOffset(endLineNum - 1) + 1

                                val severity = when (problemDescriptor.highlightType) {
                                    ProblemHighlightType.ERROR, ProblemHighlightType.GENERIC_ERROR -> "ERROR"
                                    ProblemHighlightType.WARNING, ProblemHighlightType.GENERIC_ERROR_OR_WARNING -> "WARNING"
                                    ProblemHighlightType.WEAK_WARNING -> "WEAK_WARNING"
                                    else -> "INFO"
                                }

                                problems.add(ProblemInfo(
                                    message = problemDescriptor.descriptionTemplate ?: "Unknown problem",
                                    severity = severity,
                                    file = file,
                                    line = problemLine,
                                    column = problemColumn,
                                    endLine = endLineNum,
                                    endColumn = endColumnNum
                                ))
                            }
                        }
                    } catch (e: Exception) {
                        // Individual inspection might fail
                    }
                }
            } catch (e: Exception) {
                // Analysis might fail, return partial results
            }

            createJsonResult(InspectionsResult(
                problems = problems.distinctBy { "${it.line}:${it.column}:${it.message}" },
                totalCount = problems.size
            ))
        }
    }
}
