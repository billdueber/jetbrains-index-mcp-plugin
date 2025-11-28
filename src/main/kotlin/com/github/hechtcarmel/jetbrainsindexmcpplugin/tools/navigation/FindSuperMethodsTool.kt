package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation

import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ParamNames
import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.SchemaConstants
import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ToolNames
import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.MethodInfo
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.SuperMethodInfo
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.SuperMethodsResult
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
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

class FindSuperMethodsTool : AbstractMcpTool() {

    override val name = ToolNames.FIND_SUPER_METHODS

    override val description = """
        Finds the complete inheritance hierarchy for a method - all parent methods it overrides or implements.

        Use this tool when you need to:
        - Find which interface method an implementation overrides
        - Navigate to the original method declaration in a parent class
        - Understand the full inheritance chain for a method with @Override
        - See all levels of method overriding (not just immediate parent)

        The position (line/column) can be anywhere within the method - on the name,
        inside the body, or on the @Override annotation. The tool automatically
        finds the enclosing method.

        Returns the full hierarchy chain ordered from immediate parent (depth=1) to root.

        EXAMPLE: {"file": "src/main/java/com/example/UserServiceImpl.java", "line": 25, "column": 10}
    """.trimIndent()

    override val inputSchema: JsonObject = buildJsonObject {
        put(SchemaConstants.TYPE, SchemaConstants.TYPE_OBJECT)
        putJsonObject(SchemaConstants.PROPERTIES) {
            putJsonObject(ParamNames.PROJECT_PATH) {
                put(SchemaConstants.TYPE, SchemaConstants.TYPE_STRING)
                put(SchemaConstants.DESCRIPTION, SchemaConstants.DESC_PROJECT_PATH)
            }
            putJsonObject(ParamNames.FILE) {
                put(SchemaConstants.TYPE, SchemaConstants.TYPE_STRING)
                put(SchemaConstants.DESCRIPTION, SchemaConstants.DESC_FILE)
            }
            putJsonObject(ParamNames.LINE) {
                put(SchemaConstants.TYPE, SchemaConstants.TYPE_INTEGER)
                put(SchemaConstants.DESCRIPTION, "1-based line number. Can be any line within the method.")
            }
            putJsonObject(ParamNames.COLUMN) {
                put(SchemaConstants.TYPE, SchemaConstants.TYPE_INTEGER)
                put(SchemaConstants.DESCRIPTION, "1-based column number. Can be any position within the method.")
            }
        }
        putJsonArray(SchemaConstants.REQUIRED) {
            add(JsonPrimitive(ParamNames.FILE))
            add(JsonPrimitive(ParamNames.LINE))
            add(JsonPrimitive(ParamNames.COLUMN))
        }
    }

    override suspend fun doExecute(project: Project, arguments: JsonObject): ToolCallResult {
        val file = arguments[ParamNames.FILE]?.jsonPrimitive?.content
            ?: return createErrorResult("Missing required parameter: ${ParamNames.FILE}")
        val line = arguments[ParamNames.LINE]?.jsonPrimitive?.int
            ?: return createErrorResult("Missing required parameter: ${ParamNames.LINE}")
        val column = arguments[ParamNames.COLUMN]?.jsonPrimitive?.int
            ?: return createErrorResult("Missing required parameter: ${ParamNames.COLUMN}")

        requireSmartMode(project)

        return readAction {
            val element = findPsiElement(project, file, line, column)
                ?: return@readAction createErrorResult("No element found at $file:$line:$column")

            val method = findEnclosingMethod(element)
                ?: return@readAction createErrorResult(
                    "No method found at position. Ensure the position is within a method declaration or body."
                )

            method.containingClass
                ?: return@readAction createErrorResult("Method is not inside a class")

            val methodInfo = createMethodInfo(project, method)
            val hierarchy = buildHierarchy(project, method)

            createJsonResult(SuperMethodsResult(
                method = methodInfo,
                hierarchy = hierarchy,
                totalCount = hierarchy.size
            ))
        }
    }

    private fun findEnclosingMethod(element: PsiElement): PsiMethod? {
        if (element is PsiMethod) return element
        return PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
    }

    private fun buildHierarchy(
        project: Project,
        method: PsiMethod,
        visited: MutableSet<String> = mutableSetOf(),
        depth: Int = 1
    ): List<SuperMethodInfo> {
        val hierarchy = mutableListOf<SuperMethodInfo>()

        val superMethods = method.findSuperMethods()

        for (superMethod in superMethods) {
            val key = "${superMethod.containingClass?.qualifiedName}.${superMethod.name}"
            if (key in visited) continue
            visited.add(key)

            hierarchy.add(createSuperMethodInfo(project, superMethod, depth))

            val parentHierarchy = buildHierarchy(project, superMethod, visited, depth + 1)
            hierarchy.addAll(parentHierarchy)
        }

        return hierarchy
    }

    private fun createMethodInfo(project: Project, method: PsiMethod): MethodInfo {
        val containingClass = method.containingClass
        val virtualFile = method.containingFile?.virtualFile

        return MethodInfo(
            name = method.name,
            signature = buildMethodSignature(method),
            containingClass = containingClass?.qualifiedName ?: containingClass?.name ?: "unknown",
            file = virtualFile?.let { getRelativePath(project, it) } ?: "unknown",
            line = getLineNumber(project, method) ?: 0
        )
    }

    private fun createSuperMethodInfo(project: Project, method: PsiMethod, depth: Int): SuperMethodInfo {
        val containingClass = method.containingClass
        val virtualFile = method.containingFile?.virtualFile
        val isInterface = containingClass?.isInterface == true

        return SuperMethodInfo(
            name = method.name,
            signature = buildMethodSignature(method),
            containingClass = containingClass?.qualifiedName ?: containingClass?.name ?: "unknown",
            containingClassKind = getClassKind(containingClass),
            file = virtualFile?.let { getRelativePath(project, it) },
            line = getLineNumber(project, method),
            isInterface = isInterface,
            depth = depth
        )
    }

    private fun buildMethodSignature(method: PsiMethod): String {
        val params = method.parameterList.parameters.joinToString(", ") { param ->
            "${param.type.presentableText} ${param.name}"
        }
        val returnType = method.returnType?.presentableText ?: "void"
        return "${method.name}($params): $returnType"
    }

    private fun getClassKind(psiClass: PsiClass?): String {
        if (psiClass == null) return "UNKNOWN"
        return when {
            psiClass.isInterface -> "INTERFACE"
            psiClass.isEnum -> "ENUM"
            psiClass.isAnnotationType -> "ANNOTATION"
            psiClass.hasModifierProperty("abstract") -> "ABSTRACT_CLASS"
            else -> "CLASS"
        }
    }

    private fun getLineNumber(project: Project, element: PsiElement): Int? {
        val psiFile = element.containingFile ?: return null
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
            ?: return null
        return document.getLineNumber(element.textOffset) + 1
    }
}
