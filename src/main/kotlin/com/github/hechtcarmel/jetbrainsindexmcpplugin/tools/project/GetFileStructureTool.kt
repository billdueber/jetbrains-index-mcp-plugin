package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.project

import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.FileElement
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.FileStructureResult
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class GetFileStructureTool : AbstractMcpTool() {

    override val name = "ide_file_structure"

    override val description = """
        Parses and returns the structural outline of a source file (classes, methods, fields, and their hierarchy).
        Use when exploring unfamiliar files to understand their organization without reading full source.
        Use when generating documentation or analyzing class composition.
        Returns a tree of FileElements with name, kind (CLASS, METHOD, FIELD, etc.), line numbers, modifiers, and types.
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
        }
        putJsonArray("required") {
            add(JsonPrimitive("file"))
        }
    }

    override suspend fun execute(project: Project, arguments: JsonObject): ToolCallResult {
        val file = arguments["file"]?.jsonPrimitive?.content
            ?: return createErrorResult("Missing required parameter: file")

        requireSmartMode(project)

        return readAction {
            val psiFile = getPsiFile(project, file)
                ?: return@readAction createErrorResult("File not found: $file")

            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)

            val elements = mutableListOf<FileElement>()

            // Find all top-level classes
            val classes = PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java)
                .filter { it.parent == psiFile || it.containingClass == null }

            classes.forEach { psiClass ->
                elements.add(buildClassElement(project, psiClass, document))
            }

            // Also find top-level functions (for Kotlin files)
            PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java)
                .filter { it.containingClass == null }
                .forEach { method ->
                    elements.add(buildMethodElement(project, method, document))
                }

            createJsonResult(FileStructureResult(
                file = file,
                elements = elements
            ))
        }
    }

    private fun buildClassElement(
        project: Project,
        psiClass: PsiClass,
        document: com.intellij.openapi.editor.Document?
    ): FileElement {
        val line = document?.let { doc ->
            doc.getLineNumber(psiClass.textOffset) + 1
        } ?: 0

        val kind = when {
            psiClass.isInterface -> "INTERFACE"
            psiClass.isEnum -> "ENUM"
            psiClass.isAnnotationType -> "ANNOTATION"
            psiClass.isRecord -> "RECORD"
            else -> "CLASS"
        }

        val children = mutableListOf<FileElement>()

        // Add fields
        psiClass.fields.forEach { field ->
            children.add(buildFieldElement(project, field, document))
        }

        // Add methods
        psiClass.methods.forEach { method ->
            children.add(buildMethodElement(project, method, document))
        }

        // Add inner classes
        psiClass.innerClasses.forEach { innerClass ->
            children.add(buildClassElement(project, innerClass, document))
        }

        return FileElement(
            name = psiClass.name ?: "anonymous",
            kind = kind,
            line = line,
            modifiers = getModifiers(psiClass),
            type = psiClass.qualifiedName,
            children = children.takeIf { it.isNotEmpty() }
        )
    }

    private fun buildMethodElement(
        project: Project,
        method: PsiMethod,
        document: com.intellij.openapi.editor.Document?
    ): FileElement {
        val line = document?.let { doc ->
            doc.getLineNumber(method.textOffset) + 1
        } ?: 0

        val kind = if (method.isConstructor) "CONSTRUCTOR" else "METHOD"

        val signature = buildString {
            append(method.name)
            append("(")
            append(method.parameterList.parameters.joinToString(", ") { param ->
                "${param.name}: ${param.type.presentableText}"
            })
            append(")")
        }

        return FileElement(
            name = signature,
            kind = kind,
            line = line,
            modifiers = getModifiers(method),
            type = method.returnType?.presentableText,
            children = null
        )
    }

    private fun buildFieldElement(
        project: Project,
        field: PsiField,
        document: com.intellij.openapi.editor.Document?
    ): FileElement {
        val line = document?.let { doc ->
            doc.getLineNumber(field.textOffset) + 1
        } ?: 0

        return FileElement(
            name = field.name,
            kind = "FIELD",
            line = line,
            modifiers = getModifiers(field),
            type = field.type.presentableText,
            children = null
        )
    }

    private fun getModifiers(element: PsiElement): List<String> {
        val modifiers = mutableListOf<String>()

        if (element is PsiModifierListOwner) {
            val modifierList = element.modifierList
            if (modifierList != null) {
                listOf(
                    "public", "private", "protected",
                    "static", "final", "abstract",
                    "native", "synchronized", "transient", "volatile"
                ).forEach { modifier ->
                    if (modifierList.hasModifierProperty(modifier)) {
                        modifiers.add(modifier)
                    }
                }
            }
        }

        return modifiers
    }
}
