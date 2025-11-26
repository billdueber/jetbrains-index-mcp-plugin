package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.navigation

import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.TypeElement
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.TypeHierarchyResult
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.searches.ClassInheritorsSearch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class TypeHierarchyTool : AbstractMcpTool() {

    override val name = "ide_type_hierarchy"

    override val description = """
        Retrieves the complete type hierarchy for a class or interface, showing inheritance relationships.
        Use when exploring class inheritance chains, understanding polymorphism, or finding all subclasses.
        Use when analyzing interface implementations or abstract class extensions.
        Returns the target element with its supertypes (superclass, interfaces) and subtypes (implementing/extending classes).
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
            putJsonObject("className") {
                put("type", "string")
                put("description", "Fully qualified class name (alternative to file/line/column)")
            }
        }
        putJsonArray("required") {
            // Either file/line/column OR className must be provided
        }
    }

    override suspend fun execute(project: Project, arguments: JsonObject): ToolCallResult {
        requireSmartMode(project)

        return readAction {
            val targetClass = resolveTargetClass(project, arguments)
                ?: return@readAction createErrorResult("Could not resolve class. Provide file/line/column or className.")

            val supertypes = getSupertypes(project, targetClass)
            val subtypes = getSubtypes(project, targetClass)

            val element = TypeElement(
                name = targetClass.qualifiedName ?: targetClass.name ?: "unknown",
                file = targetClass.containingFile?.virtualFile?.let { getRelativePath(project, it) },
                kind = getClassKind(targetClass)
            )

            createJsonResult(TypeHierarchyResult(
                element = element,
                supertypes = supertypes,
                subtypes = subtypes
            ))
        }
    }

    private fun resolveTargetClass(project: Project, arguments: JsonObject): PsiClass? {
        // Try className first
        val className = arguments["className"]?.jsonPrimitive?.content
        if (className != null) {
            return findClassByName(project, className)
        }

        // Otherwise use file/line/column
        val file = arguments["file"]?.jsonPrimitive?.content
            ?: return null
        val line = arguments["line"]?.jsonPrimitive?.int
            ?: return null
        val column = arguments["column"]?.jsonPrimitive?.int
            ?: return null

        val element = findPsiElement(project, file, line, column)
            ?: return null

        return findContainingClass(element)
    }

    private fun findClassByName(project: Project, qualifiedName: String): PsiClass? {
        return try {
            val javaPsiFacade = com.intellij.psi.JavaPsiFacade.getInstance(project)
            javaPsiFacade.findClass(qualifiedName, com.intellij.psi.search.GlobalSearchScope.allScope(project))
        } catch (e: Exception) {
            null
        }
    }

    private fun findContainingClass(element: PsiElement): PsiClass? {
        var current: PsiElement? = element
        while (current != null) {
            if (current is PsiClass) {
                return current
            }
            current = current.parent
        }
        return null
    }

    private fun getSupertypes(project: Project, psiClass: PsiClass): List<TypeElement> {
        val supertypes = mutableListOf<TypeElement>()

        // Add superclass
        psiClass.superClass?.let { superClass ->
            if (superClass.qualifiedName != "java.lang.Object") {
                supertypes.add(TypeElement(
                    name = superClass.qualifiedName ?: superClass.name ?: "unknown",
                    file = superClass.containingFile?.virtualFile?.let { getRelativePath(project, it) },
                    kind = getClassKind(superClass)
                ))
            }
        }

        // Add interfaces
        psiClass.interfaces.forEach { iface ->
            supertypes.add(TypeElement(
                name = iface.qualifiedName ?: iface.name ?: "unknown",
                file = iface.containingFile?.virtualFile?.let { getRelativePath(project, it) },
                kind = "INTERFACE"
            ))
        }

        return supertypes
    }

    private fun getSubtypes(project: Project, psiClass: PsiClass): List<TypeElement> {
        return try {
            ClassInheritorsSearch.search(psiClass, false)
                .findAll()
                .take(50) // Limit to prevent huge results
                .map { subClass ->
                    TypeElement(
                        name = subClass.qualifiedName ?: subClass.name ?: "unknown",
                        file = subClass.containingFile?.virtualFile?.let { getRelativePath(project, it) },
                        kind = getClassKind(subClass)
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
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
