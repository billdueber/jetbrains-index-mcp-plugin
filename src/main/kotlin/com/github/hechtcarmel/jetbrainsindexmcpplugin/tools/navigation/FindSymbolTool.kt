package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation

import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ParamNames
import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.SchemaConstants
import com.github.hechtcarmel.jetbrainsindexmcpplugin.constants.ToolNames
import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.FindSymbolResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.SymbolMatch
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class FindSymbolTool : AbstractMcpTool() {

    companion object {
        private const val DEFAULT_LIMIT = 25
        private const val MAX_LIMIT = 100
    }

    override val name = ToolNames.FIND_SYMBOL

    override val description = """
        Searches for code symbols (classes, interfaces, methods, fields) by name using the IDE's semantic index.

        Use this tool when you need to:
        - Find a class or interface by name (e.g., find "UserService")
        - Locate methods across the codebase (e.g., find all "findById" methods)
        - Discover fields or constants by name
        - Navigate to code when you know the symbol name but not the file location

        Supports fuzzy matching:
        - Substring: "Service" matches "UserService", "OrderService"
        - CamelCase: "USvc" matches "UserService", "US" matches "UserService"

        EXAMPLE: {"query": "UserService"}
        EXAMPLE: {"query": "findById"}
        EXAMPLE: {"query": "USvc", "includeLibraries": true}
    """.trimIndent()

    override val inputSchema: JsonObject = buildJsonObject {
        put(SchemaConstants.TYPE, SchemaConstants.TYPE_OBJECT)
        putJsonObject(SchemaConstants.PROPERTIES) {
            putJsonObject(ParamNames.PROJECT_PATH) {
                put(SchemaConstants.TYPE, SchemaConstants.TYPE_STRING)
                put(SchemaConstants.DESCRIPTION, SchemaConstants.DESC_PROJECT_PATH)
            }
            putJsonObject(ParamNames.QUERY) {
                put(SchemaConstants.TYPE, SchemaConstants.TYPE_STRING)
                put(SchemaConstants.DESCRIPTION, "Search pattern. Supports substring and camelCase matching.")
            }
            putJsonObject(ParamNames.INCLUDE_LIBRARIES) {
                put(SchemaConstants.TYPE, SchemaConstants.TYPE_BOOLEAN)
                put(SchemaConstants.DESCRIPTION, "Include symbols from library dependencies. Default: false.")
            }
            putJsonObject(ParamNames.LIMIT) {
                put(SchemaConstants.TYPE, SchemaConstants.TYPE_INTEGER)
                put(SchemaConstants.DESCRIPTION, "Maximum results to return. Default: 25, Max: 100.")
            }
        }
        putJsonArray(SchemaConstants.REQUIRED) {
            add(JsonPrimitive(ParamNames.QUERY))
        }
    }

    override suspend fun execute(project: Project, arguments: JsonObject): ToolCallResult {
        val query = arguments[ParamNames.QUERY]?.jsonPrimitive?.content
            ?: return createErrorResult("Missing required parameter: ${ParamNames.QUERY}")
        val includeLibraries = arguments[ParamNames.INCLUDE_LIBRARIES]?.jsonPrimitive?.boolean ?: false
        val limit = (arguments[ParamNames.LIMIT]?.jsonPrimitive?.int ?: DEFAULT_LIMIT)
            .coerceIn(1, MAX_LIMIT)

        if (query.isBlank()) {
            return createErrorResult("Query cannot be empty")
        }

        requireSmartMode(project)

        val scope = if (includeLibraries) {
            GlobalSearchScope.allScope(project)
        } else {
            GlobalSearchScope.projectScope(project)
        }

        return readAction {
            val matches = searchAllSymbols(project, query, scope, limit)

            createJsonResult(FindSymbolResult(
                symbols = matches,
                totalCount = matches.size,
                query = query
            ))
        }
    }

    private fun searchAllSymbols(
        project: Project,
        query: String,
        scope: GlobalSearchScope,
        limit: Int
    ): List<SymbolMatch> {
        val cache = PsiShortNamesCache.getInstance(project)
        val matches = mutableListOf<SymbolMatch>()

        // Search classes (includes interfaces, enums, etc.)
        val classNames = cache.allClassNames.filter { matchesQuery(it, query) }
        for (className in classNames) {
            if (matches.size >= limit) break
            val classes = cache.getClassesByName(className, scope)
            for (psiClass in classes) {
                if (matches.size >= limit) break
                val virtualFile = psiClass.containingFile?.virtualFile ?: continue
                matches.add(SymbolMatch(
                    name = psiClass.name ?: className,
                    qualifiedName = psiClass.qualifiedName,
                    kind = getClassKind(psiClass),
                    file = getRelativePath(project, virtualFile),
                    line = getLineNumber(project, psiClass) ?: 1,
                    containerName = null
                ))
            }
        }

        // Search methods
        if (matches.size < limit) {
            val methodNames = cache.allMethodNames.filter { matchesQuery(it, query) }
            for (methodName in methodNames) {
                if (matches.size >= limit) break
                val methods = cache.getMethodsByName(methodName, scope)
                for (method in methods) {
                    if (matches.size >= limit) break
                    val virtualFile = method.containingFile?.virtualFile ?: continue
                    val containingClass = method.containingClass
                    matches.add(SymbolMatch(
                        name = method.name,
                        qualifiedName = "${containingClass?.qualifiedName}.${method.name}",
                        kind = "METHOD",
                        file = getRelativePath(project, virtualFile),
                        line = getLineNumber(project, method) ?: 1,
                        containerName = containingClass?.name
                    ))
                }
            }
        }

        // Search fields
        if (matches.size < limit) {
            val fieldNames = cache.allFieldNames.filter { matchesQuery(it, query) }
            for (fieldName in fieldNames) {
                if (matches.size >= limit) break
                val fields = cache.getFieldsByName(fieldName, scope)
                for (field in fields) {
                    if (matches.size >= limit) break
                    val virtualFile = field.containingFile?.virtualFile ?: continue
                    val containingClass = field.containingClass
                    matches.add(SymbolMatch(
                        name = field.name ?: fieldName,
                        qualifiedName = "${containingClass?.qualifiedName}.${field.name}",
                        kind = "FIELD",
                        file = getRelativePath(project, virtualFile),
                        line = getLineNumber(project, field) ?: 1,
                        containerName = containingClass?.name
                    ))
                }
            }
        }

        // Sort by relevance (exact matches first, then by edit distance)
        return matches.sortedWith(compareBy(
            { !it.name.equals(query, ignoreCase = true) },
            { levenshteinDistance(it.name.lowercase(), query.lowercase()) }
        ))
    }

    private fun matchesQuery(name: String, query: String): Boolean {
        // Exact containment (case-insensitive)
        if (name.contains(query, ignoreCase = true)) return true

        // CamelCase matching: "USvc" matches "UserService"
        if (matchesCamelCase(name, query)) return true

        return false
    }

    internal fun matchesCamelCase(name: String, query: String): Boolean {
        var queryIndex = 0
        for (char in name) {
            if (queryIndex >= query.length) return true
            if (char.equals(query[queryIndex], ignoreCase = true)) {
                queryIndex++
            }
        }
        return queryIndex >= query.length
    }

    internal fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + if (s1[i - 1] == s2[j - 1]) 0 else 1
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    private fun getLineNumber(project: Project, element: PsiElement): Int? {
        val psiFile = element.containingFile ?: return null
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return null
        return document.getLineNumber(element.textOffset) + 1
    }

    private fun getClassKind(psiClass: PsiClass): String {
        return when {
            psiClass.isInterface -> "INTERFACE"
            psiClass.isEnum -> "ENUM"
            psiClass.isAnnotationType -> "ANNOTATION"
            psiClass.isRecord -> "RECORD"
            psiClass.hasModifierProperty("abstract") -> "ABSTRACT_CLASS"
            else -> "CLASS"
        }
    }
}
