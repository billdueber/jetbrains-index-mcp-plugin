package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.refactoring

import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.RefactoringResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.SuggestedRename
import com.intellij.lang.LanguageNamesValidation
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.util.containers.MultiMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Universal rename tool that works across all languages supported by JetBrains IDEs.
 *
 * This tool uses IntelliJ's `RenameProcessor` which is language-agnostic and delegates
 * to language-specific `RenamePsiElementProcessor` implementations. This enables:
 * - Java/Kotlin: getter/setter renaming, overriding methods, test classes
 * - Python: function/class/variable renaming
 * - JavaScript/TypeScript: symbol renaming across files
 * - Go: function/type/variable renaming
 * - And more languages via their respective plugins
 *
 * The tool uses a two-phase approach:
 * 1. **Background Phase**: Find element and validate (read action)
 * 2. **EDT Phase**: Execute rename via RenameProcessor (handles all references)
 */
class RenameSymbolTool : AbstractMcpTool() {

    override val name = "ide_refactor_rename"

    override val description = """
        Renames a symbol and updates all references across the project. Supports Ctrl+Z undo.

        SUPPORTED LANGUAGES: Java, Kotlin, Python, JavaScript, TypeScript, Go, and more.

        REQUIRED: file + line + column to identify the symbol, plus newName.

        WARNING: This modifies files. Returns affected files, change count, and suggestedRenames.

        RESPONSE includes `suggestedRenames` array with related elements (getters/setters, overriding methods, test classes) that you may want to rename separately for consistency.

        EXAMPLE: {"file": "src/main/java/com/example/UserService.java", "line": 15, "column": 18, "newName": "CustomerService"}
    """.trimIndent()

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("project_path") {
                put("type", "string")
                put("description", "Absolute path to project root. Only needed when multiple projects are open.")
            }
            putJsonObject("file") {
                put("type", "string")
                put("description", "Path to file relative to project root. REQUIRED.")
            }
            putJsonObject("line") {
                put("type", "integer")
                put("description", "1-based line number where the symbol is located. REQUIRED.")
            }
            putJsonObject("column") {
                put("type", "integer")
                put("description", "1-based column number. REQUIRED.")
            }
            putJsonObject("newName") {
                put("type", "string")
                put("description", "The new name for the symbol. REQUIRED.")
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("file"))
            add(JsonPrimitive("line"))
            add(JsonPrimitive("column"))
            add(JsonPrimitive("newName"))
        }
    }

    /**
     * Data class holding validated rename parameters from Phase 1.
     */
    private data class RenameValidation(
        val element: PsiNamedElement,
        val oldName: String,
        val error: String? = null,
        val suggestedRenames: List<SuggestedRename> = emptyList()
    )

    override suspend fun doExecute(project: Project, arguments: JsonObject): ToolCallResult {
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

        // ═══════════════════════════════════════════════════════════════════════
        // PHASE 1: BACKGROUND - Find element and validate (read action)
        // ═══════════════════════════════════════════════════════════════════════
        val validation = readAction {
            validateAndPrepare(project, file, line, column, newName)
        }

        if (validation.error != null) {
            return createErrorResult(validation.error)
        }

        val element = validation.element
        val oldName = validation.oldName
        val suggestedRenames = validation.suggestedRenames

        // ═══════════════════════════════════════════════════════════════════════
        // PHASE 2: EDT - Execute rename using RenameProcessor
        // ═══════════════════════════════════════════════════════════════════════
        var changesCount = 0
        val affectedFiles = mutableSetOf<String>()
        var errorMessage: String? = null

        withContext(Dispatchers.EDT) {
            try {
                changesCount = executeRename(project, element, newName, affectedFiles)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Unknown error during rename"
            }
        }

        return if (errorMessage != null) {
            createErrorResult("Rename failed: $errorMessage")
        } else {
            val suggestionsNote = if (suggestedRenames.isNotEmpty()) {
                " (${suggestedRenames.size} related element(s) may also need renaming - see suggestedRenames)"
            } else ""

            createJsonResult(
                RefactoringResult(
                    success = true,
                    affectedFiles = affectedFiles.toList(),
                    changesCount = changesCount,
                    message = "Successfully renamed '$oldName' to '$newName'$suggestionsNote",
                    suggestedRenames = suggestedRenames
                )
            )
        }
    }

    /**
     * Validates rename parameters and prepares the element for renaming.
     * Runs in a read action (background thread).
     */
    private fun validateAndPrepare(
        project: Project,
        file: String,
        line: Int,
        column: Int,
        newName: String
    ): RenameValidation {
        val psiElement = findPsiElement(project, file, line, column)
            ?: return RenameValidation(
                element = DummyNamedElement,
                oldName = "",
                error = "No element found at the specified position"
            )

        val namedElement = findNamedElement(psiElement)
            ?: return RenameValidation(
                element = DummyNamedElement,
                oldName = "",
                error = "No renameable symbol found at the specified position"
            )

        val oldName = namedElement.name
            ?: return RenameValidation(
                element = DummyNamedElement,
                oldName = "",
                error = "Element has no name"
            )

        if (oldName == newName) {
            return RenameValidation(
                element = DummyNamedElement,
                oldName = oldName,
                error = "New name is the same as the current name"
            )
        }

        // Validate the new name using language-specific rules
        val validationError = validateNewName(project, namedElement, newName)
        if (validationError != null) {
            return RenameValidation(
                element = DummyNamedElement,
                oldName = oldName,
                error = validationError
            )
        }

        // Check for naming conflicts (would show dialog otherwise)
        val conflictError = checkForConflicts(namedElement, newName)
        if (conflictError != null) {
            return RenameValidation(
                element = DummyNamedElement,
                oldName = oldName,
                error = conflictError
            )
        }

        // Collect suggested renames from automatic renamers (what the popup would have shown)
        val suggestedRenames = collectSuggestedRenames(project, namedElement, newName)

        return RenameValidation(
            element = namedElement,
            oldName = oldName,
            suggestedRenames = suggestedRenames
        )
    }

    /**
     * Collects suggested renames from all applicable AutomaticRenamerFactory instances.
     * This is what the popup dialog would have shown - getters/setters, overriding methods, etc.
     */
    private fun collectSuggestedRenames(
        project: Project,
        element: PsiNamedElement,
        newName: String
    ): List<SuggestedRename> {
        val suggestions = mutableListOf<SuggestedRename>()

        // Empty usage list - we're just collecting suggestions, not performing the rename
        val emptyUsages = mutableListOf<com.intellij.usageView.UsageInfo>()

        for (factory in AutomaticRenamerFactory.EP_NAME.extensionList) {
            if (!factory.isApplicable(element)) continue

            try {
                val renamer = factory.createRenamer(element, newName, emptyUsages)
                if (renamer == null) continue

                // Get the category name from the factory
                val category = factory.optionName?.replace("Rename ", "")?.lowercase() ?: "related"

                // Iterate through elements the renamer would rename
                for (suggestedElement in renamer.elements) {
                    val suggestedNewName = renamer.getNewName(suggestedElement) ?: continue
                    val currentName = suggestedElement.name ?: continue

                    // Don't suggest if it's the same element or same name
                    if (suggestedElement == element || currentName == suggestedNewName) continue

                    val containingFile = suggestedElement.containingFile?.virtualFile ?: continue
                    val document = PsiDocumentManager.getInstance(project)
                        .getDocument(suggestedElement.containingFile) ?: continue
                    val lineNumber = document.getLineNumber(suggestedElement.textOffset) + 1

                    suggestions.add(
                        SuggestedRename(
                            category = category,
                            currentName = currentName,
                            suggestedName = suggestedNewName,
                            file = getRelativePath(project, containingFile),
                            line = lineNumber
                        )
                    )
                }
            } catch (e: Exception) {
                // Some factories may fail for certain elements - skip them
            }
        }

        return suggestions
    }

    /**
     * Checks for naming conflicts that would prevent the rename.
     * Returns an error message if conflicts exist, null otherwise.
     */
    private fun checkForConflicts(element: PsiNamedElement, newName: String): String? {
        val processor = RenamePsiElementProcessor.forElement(element)
        val conflicts = MultiMap<PsiElement, String>()

        // Let the processor find existing name conflicts
        processor.findExistingNameConflicts(element, newName, conflicts)

        if (!conflicts.isEmpty) {
            val conflictMessages = conflicts.values().take(3).joinToString("; ")
            val moreCount = conflicts.values().size - 3
            val suffix = if (moreCount > 0) " (and $moreCount more)" else ""
            return "Name conflict: $conflictMessages$suffix"
        }

        return null
    }

    /**
     * Validates the new name using language-specific identifier rules.
     */
    private fun validateNewName(
        project: Project,
        element: PsiElement,
        newName: String
    ): String? {
        val psiFile = element.containingFile ?: return null
        val language = psiFile.language

        val validator = LanguageNamesValidation.INSTANCE.forLanguage(language)

        if (!validator.isIdentifier(newName, project)) {
            return "'$newName' is not a valid identifier in ${language.displayName}"
        }

        if (validator.isKeyword(newName, project)) {
            return "'$newName' is a reserved keyword in ${language.displayName}"
        }

        return null
    }

    /**
     * Executes the rename using IntelliJ's RenameProcessor.
     * Must be called on EDT.
     *
     * HEADLESS OPERATION:
     * - AutomaticRenamerFactory is NOT used to avoid interactive dialogs
     * - This means related elements (getters/setters, overriding methods) are NOT auto-renamed
     * - The agent should rename related elements separately if needed
     *
     * @return The number of affected files
     */
    private fun executeRename(
        project: Project,
        element: PsiNamedElement,
        newName: String,
        affectedFiles: MutableSet<String>
    ): Int {
        // Get the language-specific processor for this element
        val elementProcessor = RenamePsiElementProcessor.forElement(element)

        // Some elements need substitution (e.g., light elements → real elements)
        val substituted = elementProcessor.substituteElementToRename(element, null)
        val targetElement = (substituted as? PsiNamedElement) ?: element

        // Track the file containing the declaration
        targetElement.containingFile?.virtualFile?.let { vf ->
            affectedFiles.add(getRelativePath(project, vf))
        }

        // Create the RenameProcessor with language-appropriate settings
        // NOTE: We intentionally DON'T search in comments/text occurrences to avoid
        // non-code usage dialogs. The basic rename is more predictable for agents.
        val renameProcessor = RenameProcessor(
            project,
            targetElement,
            newName,
            false,  // searchInComments = false (avoid dialogs)
            false   // searchTextOccurrences = false (avoid dialogs)
        )

        // IMPORTANT: Do NOT add AutomaticRenamerFactory instances!
        // Adding them causes interactive dialogs asking which related elements to rename.
        // For headless/autonomous operation, we skip automatic renaming of:
        // - Getters/setters (Java)
        // - Overriding methods
        // - Test classes
        // - Companion objects (Kotlin)
        // The agent can rename these separately if needed.

        // Disable preview dialog for headless operation
        renameProcessor.setPreviewUsages(false)

        // Execute the rename - this modifies files in place
        renameProcessor.run()

        // Commit documents and save
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        FileDocumentManager.getInstance().saveAllDocuments()

        // Return the count of affected files (usages are handled internally by RenameProcessor)
        return affectedFiles.size
    }

    /**
     * Finds the named element from a PSI element (traverses up if needed).
     */
    private fun findNamedElement(element: PsiElement): PsiNamedElement? {
        if (element is PsiNamedElement && element.name != null) {
            return element
        }
        return PsiTreeUtil.getParentOfType(element, PsiNamedElement::class.java)
    }

    /**
     * Dummy placeholder for error cases to satisfy non-null return type.
     */
    private object DummyNamedElement : PsiNamedElement {
        override fun setName(name: String): PsiElement = this
        override fun getName(): String? = null
        override fun getProject() = throw UnsupportedOperationException()
        override fun getLanguage() = throw UnsupportedOperationException()
        override fun getManager() = throw UnsupportedOperationException()
        override fun getChildren() = throw UnsupportedOperationException()
        override fun getParent() = throw UnsupportedOperationException()
        override fun getFirstChild() = throw UnsupportedOperationException()
        override fun getLastChild() = throw UnsupportedOperationException()
        override fun getNextSibling() = throw UnsupportedOperationException()
        override fun getPrevSibling() = throw UnsupportedOperationException()
        override fun getContainingFile() = throw UnsupportedOperationException()
        override fun getTextRange() = throw UnsupportedOperationException()
        override fun getStartOffsetInParent() = throw UnsupportedOperationException()
        override fun getTextLength() = throw UnsupportedOperationException()
        override fun findElementAt(offset: Int) = throw UnsupportedOperationException()
        override fun findReferenceAt(offset: Int) = throw UnsupportedOperationException()
        override fun getTextOffset() = throw UnsupportedOperationException()
        override fun getText() = throw UnsupportedOperationException()
        override fun textToCharArray() = throw UnsupportedOperationException()
        override fun getNavigationElement() = throw UnsupportedOperationException()
        override fun getOriginalElement() = throw UnsupportedOperationException()
        override fun textMatches(text: CharSequence) = throw UnsupportedOperationException()
        override fun textMatches(element: PsiElement) = throw UnsupportedOperationException()
        override fun textContains(c: Char) = throw UnsupportedOperationException()
        override fun accept(visitor: com.intellij.psi.PsiElementVisitor) = throw UnsupportedOperationException()
        override fun acceptChildren(visitor: com.intellij.psi.PsiElementVisitor) = throw UnsupportedOperationException()
        override fun copy() = throw UnsupportedOperationException()
        override fun add(element: PsiElement) = throw UnsupportedOperationException()
        override fun addBefore(element: PsiElement, anchor: PsiElement?) = throw UnsupportedOperationException()
        override fun addAfter(element: PsiElement, anchor: PsiElement?) = throw UnsupportedOperationException()
        override fun checkAdd(element: PsiElement) = throw UnsupportedOperationException()
        override fun addRange(first: PsiElement, last: PsiElement) = throw UnsupportedOperationException()
        override fun addRangeBefore(first: PsiElement, last: PsiElement, anchor: PsiElement) = throw UnsupportedOperationException()
        override fun addRangeAfter(first: PsiElement, last: PsiElement, anchor: PsiElement) = throw UnsupportedOperationException()
        override fun delete() = throw UnsupportedOperationException()
        override fun checkDelete() = throw UnsupportedOperationException()
        override fun deleteChildRange(first: PsiElement, last: PsiElement) = throw UnsupportedOperationException()
        override fun replace(newElement: PsiElement) = throw UnsupportedOperationException()
        override fun isValid() = false
        override fun isWritable() = false
        override fun getReference() = throw UnsupportedOperationException()
        override fun getReferences() = throw UnsupportedOperationException()
        override fun <T> getCopyableUserData(key: com.intellij.openapi.util.Key<T>) = throw UnsupportedOperationException()
        override fun <T> putCopyableUserData(key: com.intellij.openapi.util.Key<T>, value: T?) = throw UnsupportedOperationException()
        override fun processDeclarations(processor: com.intellij.psi.scope.PsiScopeProcessor, state: com.intellij.psi.ResolveState, lastParent: PsiElement?, place: PsiElement) = throw UnsupportedOperationException()
        override fun getContext() = throw UnsupportedOperationException()
        override fun isPhysical() = false
        override fun getResolveScope() = throw UnsupportedOperationException()
        override fun getUseScope() = throw UnsupportedOperationException()
        override fun getNode() = throw UnsupportedOperationException()
        override fun isEquivalentTo(another: PsiElement?) = false
        override fun getIcon(flags: Int) = throw UnsupportedOperationException()
        override fun <T> getUserData(key: com.intellij.openapi.util.Key<T>): T? = null
        override fun <T> putUserData(key: com.intellij.openapi.util.Key<T>, value: T?) {}
    }
}
