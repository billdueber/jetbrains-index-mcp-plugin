package com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.refactoring

import com.github.hechtcarmel.jetbrainsindexmcpplugin.exceptions.RefactoringConflictException
import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsindexmcpplugin.tools.models.RefactoringResult
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * Abstract base class for all refactoring tools.
 * Provides common utilities for:
 * - Write operations with undo support
 * - Affected files tracking
 * - Error handling for refactoring conflicts
 */
abstract class AbstractRefactoringTool : AbstractMcpTool() {

    /**
     * Executes a refactoring operation within a write command action,
     * with proper undo/redo grouping and file tracking.
     *
     * @param project The current project
     * @param commandName The name of the command for undo/redo
     * @param action The refactoring action to execute, returns a pair of (success, message)
     * @return ToolCallResult with the refactoring result
     */
    protected fun executeRefactoring(
        project: Project,
        commandName: String,
        affectedFilesCollector: MutableSet<String>,
        action: () -> Pair<Boolean, String>
    ): ToolCallResult {
        var success = false
        var message = ""
        var errorMessage: String? = null

        try {
            WriteCommandAction.runWriteCommandAction(
                project,
                commandName,
                "MCP Refactoring",
                {
                    try {
                        val result = action()
                        success = result.first
                        message = result.second
                    } catch (e: RefactoringConflictException) {
                        errorMessage = e.message
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Unknown error during refactoring"
                    }
                }
            )

            // Commit and save all affected documents
            ApplicationManager.getApplication().invokeAndWait {
                PsiDocumentManager.getInstance(project).commitAllDocuments()
                FileDocumentManager.getInstance().saveAllDocuments()
            }
        } catch (e: Exception) {
            return createErrorResult("Refactoring failed: ${e.message}")
        }

        return if (errorMessage != null) {
            createErrorResult("Refactoring failed: $errorMessage")
        } else if (success) {
            createJsonResult(
                RefactoringResult(
                    success = true,
                    affectedFiles = affectedFilesCollector.toList(),
                    changesCount = affectedFilesCollector.size,
                    message = message
                )
            )
        } else {
            createErrorResult(message)
        }
    }

    /**
     * Finds the named element at the given position (or its parent if the element is not named).
     */
    protected fun findNamedElement(
        project: Project,
        file: String,
        line: Int,
        column: Int
    ): PsiNamedElement? {
        val element = findPsiElement(project, file, line, column) ?: return null
        return findNamedElement(element)
    }

    /**
     * Finds the named element from the given PSI element (traverses up if needed).
     */
    protected fun findNamedElement(element: PsiElement): PsiNamedElement? {
        if (element is PsiNamedElement) {
            return element
        }
        return PsiTreeUtil.getParentOfType(element, PsiNamedElement::class.java)
    }

    /**
     * Tracks a file as affected by the refactoring.
     */
    protected fun trackAffectedFile(
        project: Project,
        file: VirtualFile,
        collector: MutableSet<String>
    ) {
        collector.add(getRelativePath(project, file))
    }
}
