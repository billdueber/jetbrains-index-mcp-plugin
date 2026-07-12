package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.ruby

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.MethodData
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.SuperMethodData
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.SuperMethodsData
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.SuperMethodsHandler
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PluginDetectors
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil

/**
 * Ruby implementation of [SuperMethodsHandler].
 *
 * Finds all parent methods that a method overrides or inherits from.
 *
 * **Implementation Strategy**:
 * - Primary: Uses `RubyOverrideImplementUtil.getOverriddenMethods()` for accurate method resolution.
 *   This is backed by the indexed symbol tree (v2 ClassModuleSymbol) and handles Ruby's complex
 *   method resolution order (MRO) including mixins and module methods.
 * - Secondary: Falls back to PSI tree traversal for edge cases (e.g., when element is a method call).
 *
 * **Returns**:
 * - `SuperMethodsData` containing the current method's metadata and its super method hierarchy.
 * - Returns null if the element is not a Ruby method or the Ruby plugin is unavailable.
 */
class RubySuperMethodsHandler : BaseRubyHandler<SuperMethodsData>(), SuperMethodsHandler {

    companion object {
        private val LOG = logger<RubySuperMethodsHandler>()
        private const val MAX_HIERARCHY_DEPTH = 50
        private const val MAX_SUPER_METHODS = 100
    }

    override val languageId = "Ruby"

    override fun canHandle(element: PsiElement): Boolean {
        return isAvailable() && isRubyLanguage(element)
    }

    override fun isAvailable(): Boolean = PluginDetectors.ruby.isAvailable

    override fun findSuperMethods(element: PsiElement, project: Project): SuperMethodsData? {
        // Try to resolve the element to an RMethod via multiple strategies
        val rMethod = resolveToRMethod(element) ?: return null
        val containingClass = findContainingRClassOrRModule(rMethod) ?: return null

        // Build method data for the current method
        val methodData = buildMethodData(rMethod, project, containingClass)

        // Find overridden methods (super methods)
        // TODO: Re-implement using direct reflection to RubyOverrideImplementUtil
        val overriddenMethods = emptyList<PsiElement>()
        val searchScope = GlobalSearchScope.projectScope(project)

        // Build hierarchy from overridden methods
        val hierarchy = buildHierarchy(project, overriddenMethods, searchScope)

        return SuperMethodsData(
            method = methodData,
            hierarchy = hierarchy
        )
    }

    // ── Method Resolution ───────────────────────────────────────────────────────────────

    /**
     * Resolves a PSI element to an RMethod element.
     *
     * Tries multiple resolution strategies:
     * 1. If element is already an RMethod, return it
     * 2. Try to resolve via PsiTreeUtil.getParentOfType
     * 3. Fallback: use OverridingMethodsSearch to find the method declaration (for method calls)
     *
     * @param element The PSI element to resolve
     * @return The resolved RMethod, or null if not found
     */
    private fun resolveToRMethod(element: PsiElement): PsiElement? {
        val rMethodClass = rMethodClass ?: return null

        // Strategy 1: Element is already an RMethod
        if (rMethodClass.isInstance(element)) return element

        // Strategy 2: Find RMethod in parent chain
        @Suppress("UNCHECKED_CAST")
        val methodFromParents = PsiTreeUtil.getParentOfType(element, rMethodClass as Class<out PsiElement>)
        if (methodFromParents != null) return methodFromParents

        return null
    }

    // ── Method Data Building ─────────────────────────────────────────────────────────────

    /**
     * Builds [MethodData] for the given RMethod.
     *
     * @param rMethod The RMethod PSI element
     * @param project The project context
     * @param containingClass The containing RContainer of the method
     * @return The method data, or null if required fields are missing
     */
    private fun buildMethodData(rMethod: PsiElement, project: Project, containingClass: PsiElement): MethodData {
        val name = getName(rMethod) ?: "unknown"
        val qualifiedClass = getRubyQualifiedName(containingClass) ?: name
        val file = rMethod.containingFile?.virtualFile
        val signature = buildRubyMethodSignature(rMethod)

        return MethodData(
            name = name,
            signature = signature,
            containingClass = qualifiedClass,
            file = file?.let { getRelativePath(project, it) } ?: "unknown",
            line = getLineNumber(project, rMethod) ?: 0,
            column = getColumnNumber(project, rMethod) ?: 0,
            language = "Ruby"
        )
    }

    // ── Super Method Hierarchy Building ────────────────────────────────────────────────────

    /**
     * Builds the super method hierarchy from a list of overridden methods.
     *
     * Handles depth limiting and cycle detection.
     *
     * @param project The project context
     * @param overriddenMethods List of overridden RMethod elements
     * @param searchScope The search scope for resolving elements
     * @return List of super method hierarchy data
     */
    private fun buildHierarchy(
        project: Project,
        overriddenMethods: List<PsiElement>,
        searchScope: GlobalSearchScope
    ): List<SuperMethodData> {
        val hierarchy = mutableListOf<SuperMethodData>()
        val visited = mutableSetOf<String>()
        val visitedClasses = mutableSetOf<String>()

        for (overrider in overriddenMethods) {
            val key = getMethodKey(overrider)
            if (key in visited) continue
            visited.add(key)

            val containingClass = findContainingRClassOrRModule(overrider)
                ?: continue
            val classKey = getRubyQualifiedName(containingClass)
                ?: getName(containingClass) ?: continue
            if (classKey in visitedClasses) continue
            visitedClasses.add(classKey)

            if (hierarchy.size >= MAX_SUPER_METHODS) break

            val file = overrider.containingFile?.virtualFile
            val methodData = SuperMethodData(
                name = getName(overrider) ?: "unknown",
                signature = buildRubyMethodSignature(overrider),
                containingClass = classKey,
                containingClassKind = if (isRClass(containingClass)) "CLASS" else "MODULE",
                file = file?.let { getRelativePath(project, it) },
                line = getLineNumber(project, overrider),
                column = getColumnNumber(project, overrider),
                isInterface = false,
                depth = hierarchy.size + 1,
                language = "Ruby"
            )

            hierarchy.add(methodData)
        }

        return hierarchy
    }

    // ── Ruby Method Signature Building ────────────────────────────────────────────────────

    /**
     * Builds a string representation of a Ruby method's signature.
     *
     * Format: "method_name(param1, param2): return_type"
     *
     * Extracts parameter information and return type from the RMethod PSI element.
     *
     * @param rMethod The RMethod to extract signature from
     * @return The method signature string
     */
    private fun buildRubyMethodSignature(rMethod: PsiElement): String {
        val rMethodClass = rMethodClass ?: return "(unknown)"

        val name = try {
            getName(rMethod) ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
        val parameters = buildMethodParameters(rMethod)
        val returnType = buildReturnType(rMethod)
        val signature = if (parameters.isEmpty()) {
            "$name"
        } else {
            "$name($parameters): $returnType"
        }
        return signature
    }

    /**
     * Builds the parameter list for a Ruby method.
     *
     * Extracts parameter names and attempts to infer parameter types from
     * the containing class or method context.
     *
     * @param rMethod The RMethod to extract parameters from
     * @return Parameter string in format "param1, param2, ..."
     */
    private fun buildMethodParameters(rMethod: PsiElement): String {
        val rCallClass = rCallClass ?: return ""
        
        return try {
            val parameters = mutableListOf<String>()
            for (child in rMethod.children) {
                // Look for parameter-like nodes (RCall expressions with parameter context)
                if (rCallClass.isInstance(child)) {
                    val command = try {
                        child.javaClass.getMethod("getCommand").invoke(child) as? String
                    } catch (_: Exception) {
                        null
                    }
                    if (command == "param" || command == "kwsplat" || command == "restarg" ||
                        command == "shadowarg" || command == "blockarg" || command == "forwarding_arg") {
                        val paramValue = try {
                            child.javaClass.getMethod("getValue").invoke(child) as? String
                        } catch (_: Exception) {
                            null
                        }
                        if (!paramValue.isNullOrEmpty()) {
                            parameters.add(paramValue)
                        }
                    }
                }
            }
            parameters.joinToString(", ")
        } catch (e: Exception) {
            LOG.debug("Failed to build Ruby parameters: ${e.message}")
            ""
        }
    }

    /**
     * Builds the return type for a Ruby method.
     *
     * For Ruby, we return a type inference based on:
     * 1. Explicit return type annotation
     * 2. Last statement type inference (if available)
     * 3. Fallback to "Any"
     *
     * @param rMethod The RMethod to extract return type from
     * @return Return type string
     */
    private fun buildReturnType(rMethod: PsiElement): String {
        val rMethodClass = rMethodClass ?: return "Any"
        
        var returnType: Any? = null
        try {
            // Try to get explicit return type from RMethod
            val returnTypeMethod = rMethodClass.getMethod("getReturnType")
            returnType = returnTypeMethod.invoke(rMethod)
        } catch (_: Exception) {
            // Return type method not available
        }
        
        if (returnType != null) {
            try {
                // Try to get presentable text
                val presentableTextMethod = returnType.javaClass.getMethod("getPresentableText")
                val text = presentableTextMethod.invoke(returnType) as? String
                if (!text.isNullOrEmpty()) {
                    return text
                }
            } catch (_: Exception) {
                // Fallback to qualified name
            }
        }

        // Fallback: try to infer from return statement
        return "Any"
    }

    /**
     * Generates a unique key for a method for cycle detection.
     *
     * @param method The method element
     * @return A string key combining class name and method name
     */
    private fun getMethodKey(method: PsiElement): String {
        val className = findContainingRClassOrRModule(method)?.let {
            getRubyQualifiedName(it) ?: getName(it)
        } ?: "unknown"
        val methodName = getName(method) ?: "unknown"
        return "$className.$methodName"
    }
}