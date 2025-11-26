package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.intelligence

import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.CompletionItem
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.CompletionsResult
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class GetCompletionsTool : AbstractMcpTool() {

    override val name = "get_completions"

    override val description = """
        Get code completion suggestions at a specific position in a file.
        Returns a list of completion items based on the context (available methods, fields, classes).
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
            putJsonObject("maxResults") {
                put("type", "integer")
                put("description", "Maximum number of completions to return (default: 50)")
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("file"))
            add(JsonPrimitive("line"))
            add(JsonPrimitive("column"))
        }
    }

    override suspend fun execute(project: Project, arguments: JsonObject): ToolCallResult {
        val file = arguments["file"]?.jsonPrimitive?.content
            ?: return createErrorResult("Missing required parameter: file")
        val line = arguments["line"]?.jsonPrimitive?.int
            ?: return createErrorResult("Missing required parameter: line")
        val column = arguments["column"]?.jsonPrimitive?.int
            ?: return createErrorResult("Missing required parameter: column")
        val maxResults = arguments["maxResults"]?.jsonPrimitive?.int ?: 50

        requireSmartMode(project)

        return readAction {
            val psiFile = getPsiFile(project, file)
                ?: return@readAction createErrorResult("File not found: $file")

            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                ?: return@readAction createErrorResult("Could not get document for file")

            val offset = getOffset(document, line, column)
                ?: return@readAction createErrorResult("Invalid position: line $line, column $column")

            val completions = getCompletionsAt(project, psiFile, offset, maxResults)

            createJsonResult(CompletionsResult(
                completions = completions,
                totalCount = completions.size
            ))
        }
    }

    private fun getCompletionsAt(
        project: Project,
        psiFile: com.intellij.psi.PsiFile,
        offset: Int,
        maxResults: Int
    ): List<CompletionItem> {
        val completions = mutableListOf<CompletionItem>()

        try {
            val element = psiFile.findElementAt(offset) ?: return emptyList()

            // Get the containing class to find available members
            val containingClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)

            if (containingClass != null) {
                // Add methods from the containing class and its supertypes
                containingClass.allMethods.take(maxResults / 2).forEach { method ->
                    completions.add(createMethodCompletion(method))
                }

                // Add fields
                containingClass.allFields.take(maxResults / 4).forEach { field ->
                    completions.add(createFieldCompletion(field))
                }

                // Add inner classes
                containingClass.innerClasses.take(maxResults / 4).forEach { innerClass ->
                    completions.add(createClassCompletion(innerClass))
                }
            }

            // Also try to resolve the reference context for more specific completions
            val reference = element.reference
            if (reference != null) {
                val resolved = reference.resolve()
                if (resolved is PsiClass) {
                    resolved.allMethods.take(maxResults).forEach { method ->
                        if (completions.none { it.text == method.name }) {
                            completions.add(createMethodCompletion(method))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Completion might fail for various reasons
        }

        return completions.take(maxResults)
    }

    private fun createMethodCompletion(method: PsiMethod): CompletionItem {
        val signature = buildString {
            append(method.name)
            append("(")
            append(method.parameterList.parameters.joinToString(", ") {
                "${it.name}: ${it.type.presentableText}"
            })
            append(")")
        }

        return CompletionItem(
            text = signature,
            type = "METHOD",
            detail = method.returnType?.presentableText,
            documentation = null
        )
    }

    private fun createFieldCompletion(field: PsiField): CompletionItem {
        return CompletionItem(
            text = field.name,
            type = "FIELD",
            detail = field.type.presentableText,
            documentation = null
        )
    }

    private fun createClassCompletion(psiClass: PsiClass): CompletionItem {
        return CompletionItem(
            text = psiClass.name ?: "anonymous",
            type = if (psiClass.isInterface) "INTERFACE" else "CLASS",
            detail = psiClass.qualifiedName,
            documentation = null
        )
    }
}
