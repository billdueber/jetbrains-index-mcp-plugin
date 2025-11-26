package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.refactoring

import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.RefactoringResult
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.rename.RenameProcessor
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class RenameSymbolTool : AbstractRefactoringTool() {

    override val name = "rename_symbol"

    override val description = """
        Rename a symbol (variable, method, class, field, etc.) across the entire project.
        Uses IntelliJ's refactoring engine to safely update all references.
        Returns the list of affected files and number of changes made.
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
                put("description", "Path to the file containing the symbol, relative to project root")
            }
            putJsonObject("line") {
                put("type", "integer")
                put("description", "1-based line number where the symbol is located")
            }
            putJsonObject("column") {
                put("type", "integer")
                put("description", "1-based column number where the symbol is located")
            }
            putJsonObject("newName") {
                put("type", "string")
                put("description", "The new name for the symbol")
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("file"))
            add(JsonPrimitive("line"))
            add(JsonPrimitive("column"))
            add(JsonPrimitive("newName"))
        }
    }

    override suspend fun execute(project: Project, arguments: JsonObject): ToolCallResult {
        val file = arguments["file"]?.jsonPrimitive?.content
            ?: return createErrorResult("Missing required parameter: file")
        val line = arguments["line"]?.jsonPrimitive?.int
            ?: return createErrorResult("Missing required parameter: line")
        val column = arguments["column"]?.jsonPrimitive?.int
            ?: return createErrorResult("Missing required parameter: column")
        val newName = arguments["newName"]?.jsonPrimitive?.content
            ?: return createErrorResult("Missing required parameter: newName")

        if (newName.isBlank()) {
            return createErrorResult("newName cannot be blank")
        }

        requireSmartMode(project)

        // Find the element to rename
        val element = readAction {
            findNamedElement(project, file, line, column)
        } ?: return createErrorResult("No renameable symbol found at the specified position")

        val oldName = readAction { element.name } ?: return createErrorResult("Cannot determine current symbol name")

        if (oldName == newName) {
            return createErrorResult("New name is the same as the current name")
        }

        // Collect affected files first
        val affectedFiles = mutableSetOf<String>()

        try {
            readAction {
                // Add the file containing the declaration
                element.containingFile?.virtualFile?.let { vf ->
                    trackAffectedFile(project, vf, affectedFiles)
                }

                // Find all references and track their files
                val references = ReferencesSearch.search(element).findAll()
                for (reference in references) {
                    reference.element.containingFile?.virtualFile?.let { vf ->
                        trackAffectedFile(project, vf, affectedFiles)
                    }
                }
            }

            // Perform the rename using RenameProcessor
            var success = false
            var errorMessage: String? = null
            var changesCount = 0

            ApplicationManager.getApplication().invokeAndWait {
                try {
                    val processor = RenameProcessor(
                        project,
                        element,
                        newName,
                        false, // searchInComments
                        false  // searchTextOccurrences
                    )

                    // Find usages
                    val usages = processor.findUsages()
                    changesCount = usages.size + 1 // +1 for the declaration itself

                    // Run the refactoring
                    processor.run()

                    // Commit and save
                    PsiDocumentManager.getInstance(project).commitAllDocuments()
                    FileDocumentManager.getInstance().saveAllDocuments()

                    success = true
                } catch (e: Exception) {
                    errorMessage = e.message
                }
            }

            return if (success) {
                createJsonResult(
                    RefactoringResult(
                        success = true,
                        affectedFiles = affectedFiles.toList(),
                        changesCount = changesCount,
                        message = "Successfully renamed '$oldName' to '$newName'"
                    )
                )
            } else {
                createErrorResult("Rename failed: ${errorMessage ?: "Unknown error"}")
            }

        } catch (e: Exception) {
            return createErrorResult("Rename failed: ${e.message}")
        }
    }
}
