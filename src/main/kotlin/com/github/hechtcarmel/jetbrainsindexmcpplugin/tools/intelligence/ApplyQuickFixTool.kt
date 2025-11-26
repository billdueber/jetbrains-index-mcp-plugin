package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.intelligence

import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.RefactoringResult
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
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

class ApplyQuickFixTool : AbstractMcpTool() {

    override val name = "ide_apply_quick_fix"

    override val description = """
        Applies a quick fix or intention action using its ID obtained from ide_list_quick_fixes.
        Use after calling ide_list_quick_fixes to automatically fix errors, warnings, or apply code improvements.
        Use for automated code corrections like adding imports, fixing typos, or applying suggested changes.
        Returns success status, affected files, and a description of the applied fix.
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
            putJsonObject("fixId") {
                put("type", "string")
                put("description", "The fix ID from get_quick_fixes")
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("file"))
            add(JsonPrimitive("line"))
            add(JsonPrimitive("column"))
            add(JsonPrimitive("fixId"))
        }
    }

    override suspend fun execute(project: Project, arguments: JsonObject): ToolCallResult {
        val file = arguments["file"]?.jsonPrimitive?.content
            ?: return createErrorResult("Missing required parameter: file")
        val line = arguments["line"]?.jsonPrimitive?.int
            ?: return createErrorResult("Missing required parameter: line")
        val column = arguments["column"]?.jsonPrimitive?.int
            ?: return createErrorResult("Missing required parameter: column")
        val fixId = arguments["fixId"]?.jsonPrimitive?.content
            ?: return createErrorResult("Missing required parameter: fixId")

        requireSmartMode(project)

        // Get the cached quick fix
        val cachedFix = GetQuickFixesTool.getCachedQuickFix(fixId)
            ?: return createErrorResult("Quick fix not found or expired. Call get_quick_fixes again to get fresh fix IDs.")

        // Verify the file matches
        if (cachedFix.file != file) {
            return createErrorResult("Fix ID was generated for a different file")
        }

        return try {
            val psiFile = getPsiFile(project, file)
                ?: return createErrorResult("File not found: $file")

            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                ?: return createErrorResult("Could not get document for file")

            val offset = getOffset(document, line, column)
                ?: return createErrorResult("Invalid position: line $line, column $column")

            // Create a temporary editor for the fix application
            val editor = EditorFactory.getInstance().createEditor(document, project)
                ?: return createErrorResult("Could not create editor")

            try {
                editor.caretModel.moveToOffset(offset)

                // Check if the action is still available
                if (!cachedFix.action.isAvailable(project, editor, psiFile)) {
                    return createErrorResult("Quick fix is no longer available at this position")
                }

                // Apply the fix within a write action
                var success = false
                var errorMessage: String? = null

                WriteCommandAction.runWriteCommandAction(project, "Apply Quick Fix: ${cachedFix.action.text}", null, {
                    try {
                        cachedFix.action.invoke(project, editor, psiFile)
                        PsiDocumentManager.getInstance(project).commitDocument(document)
                        FileDocumentManager.getInstance().saveDocument(document)
                        success = true
                    } catch (e: Exception) {
                        errorMessage = e.message
                    }
                })

                if (success) {
                    createJsonResult(RefactoringResult(
                        success = true,
                        affectedFiles = listOf(file),
                        changesCount = 1,
                        message = "Applied quick fix: ${cachedFix.action.text}"
                    ))
                } else {
                    createErrorResult("Failed to apply quick fix: ${errorMessage ?: "Unknown error"}")
                }
            } finally {
                EditorFactory.getInstance().releaseEditor(editor)
            }
        } catch (e: Exception) {
            createErrorResult("Error applying quick fix: ${e.message}")
        }
    }
}
