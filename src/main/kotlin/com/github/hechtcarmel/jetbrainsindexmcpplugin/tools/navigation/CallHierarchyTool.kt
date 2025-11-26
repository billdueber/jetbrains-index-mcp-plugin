package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation

import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.CallElement
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.CallHierarchyResult
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class CallHierarchyTool : AbstractMcpTool() {

    override val name = "ide_call_hierarchy"

    override val description = """
        Analyzes method call relationships to find callers (methods invoking this method) or callees (methods this method invokes).
        Use when tracing execution flow, understanding code dependencies, or analyzing impact of method changes.
        Use when debugging to understand how a method is reached or what it triggers.
        Returns a tree structure with method signatures, file locations, and line numbers.
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
            putJsonObject("direction") {
                put("type", "string")
                put("description", "Direction: 'callers' (methods that call this method) or 'callees' (methods this method calls)")
                putJsonArray("enum") {
                    add(JsonPrimitive("callers"))
                    add(JsonPrimitive("callees"))
                }
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("file"))
            add(JsonPrimitive("line"))
            add(JsonPrimitive("column"))
            add(JsonPrimitive("direction"))
        }
    }

    override suspend fun execute(project: Project, arguments: JsonObject): ToolCallResult {
        val file = arguments["file"]?.jsonPrimitive?.content
            ?: return createErrorResult("Missing required parameter: file")
        val line = arguments["line"]?.jsonPrimitive?.int
            ?: return createErrorResult("Missing required parameter: line")
        val column = arguments["column"]?.jsonPrimitive?.int
            ?: return createErrorResult("Missing required parameter: column")
        val direction = arguments["direction"]?.jsonPrimitive?.content
            ?: return createErrorResult("Missing required parameter: direction")

        if (direction !in listOf("callers", "callees")) {
            return createErrorResult("direction must be 'callers' or 'callees'")
        }

        requireSmartMode(project)

        return readAction {
            val element = findPsiElement(project, file, line, column)
                ?: return@readAction createErrorResult("No element found at position $file:$line:$column")

            val method = findContainingMethod(element)
                ?: return@readAction createErrorResult("No method found at position")

            val methodElement = createCallElement(project, method)

            val calls = if (direction == "callers") {
                findCallers(project, method)
            } else {
                findCallees(project, method)
            }

            createJsonResult(CallHierarchyResult(
                element = methodElement,
                calls = calls
            ))
        }
    }

    private fun findContainingMethod(element: PsiElement): PsiMethod? {
        // First check if we're directly on a method
        if (element is PsiMethod) return element

        // Try to find a named element that might be a method name
        var current: PsiElement? = element
        while (current != null) {
            if (current is PsiMethod) {
                return current
            }
            // Check if parent is a method (we might be on the method name)
            if (current.parent is PsiMethod) {
                return current.parent as PsiMethod
            }
            current = current.parent
        }
        return null
    }

    private fun findCallers(project: Project, method: PsiMethod): List<CallElement> {
        return try {
            MethodReferencesSearch.search(method)
                .findAll()
                .take(50) // Limit results
                .mapNotNull { reference ->
                    val refElement = reference.element
                    val containingMethod = PsiTreeUtil.getParentOfType(refElement, PsiMethod::class.java)

                    if (containingMethod != null && containingMethod != method) {
                        createCallElement(project, containingMethod)
                    } else {
                        null
                    }
                }
                .distinctBy { it.name + it.file + it.line }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun findCallees(project: Project, method: PsiMethod): List<CallElement> {
        val callees = mutableListOf<CallElement>()

        try {
            method.body?.let { body ->
                // Find all method calls within the method body
                PsiTreeUtil.findChildrenOfType(body, com.intellij.psi.PsiMethodCallExpression::class.java)
                    .take(50) // Limit results
                    .forEach { methodCall ->
                        methodCall.resolveMethod()?.let { calledMethod ->
                            val element = createCallElement(project, calledMethod)
                            if (element !in callees) {
                                callees.add(element)
                            }
                        }
                    }
            }
        } catch (e: Exception) {
            // Handle gracefully
        }

        return callees.distinctBy { it.name + it.file + it.line }
    }

    private fun createCallElement(project: Project, method: PsiMethod): CallElement {
        val containingFile = method.containingFile?.virtualFile
        val document = method.containingFile?.let {
            PsiDocumentManager.getInstance(project).getDocument(it)
        }

        val lineNumber = document?.let { doc ->
            doc.getLineNumber(method.textOffset) + 1
        } ?: 0

        val methodName = buildString {
            method.containingClass?.name?.let { className ->
                append(className)
                append(".")
            }
            append(method.name)
            append("(")
            append(method.parameterList.parameters.joinToString(", ") {
                it.type.presentableText
            })
            append(")")
        }

        return CallElement(
            name = methodName,
            file = containingFile?.let { getRelativePath(project, it) } ?: "unknown",
            line = lineNumber
        )
    }
}
