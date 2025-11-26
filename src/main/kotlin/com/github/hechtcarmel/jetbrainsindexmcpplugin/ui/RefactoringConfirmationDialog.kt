package com.github.hechtcarmel.jetbrainsindexmcpplugin.ui

import com.github.hechtcarmel.jetbrainsindexmcpplugin.settings.McpSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * Dialog for confirming refactoring operations before execution.
 * Shows a preview of affected files and allows the user to confirm or cancel.
 */
class RefactoringConfirmationDialog(
    project: Project?,
    private val operationName: String,
    private val description: String,
    private val affectedFiles: List<String>
) : DialogWrapper(project, true) {

    private var dontAskAgainCheckbox: JBCheckBox? = null

    init {
        title = "Confirm Refactoring: $operationName"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout(0, JBUI.scale(10)))
        mainPanel.preferredSize = Dimension(JBUI.scale(500), JBUI.scale(400))

        // Description panel
        val descriptionPanel = JPanel(BorderLayout())
        val descriptionLabel = JBLabel(description)
        descriptionLabel.border = JBUI.Borders.empty(0, 0, 10, 0)
        descriptionPanel.add(descriptionLabel, BorderLayout.NORTH)

        // Affected files label
        val filesLabel = JBLabel("The following ${affectedFiles.size} file(s) will be affected:")
        filesLabel.border = JBUI.Borders.empty(0, 0, 5, 0)
        descriptionPanel.add(filesLabel, BorderLayout.CENTER)

        mainPanel.add(descriptionPanel, BorderLayout.NORTH)

        // Affected files list
        val listModel = DefaultListModel<String>()
        affectedFiles.forEach { listModel.addElement(it) }

        val filesList = JBList(listModel)
        filesList.visibleRowCount = 10

        val scrollPane = JBScrollPane(filesList)
        scrollPane.preferredSize = Dimension(JBUI.scale(480), JBUI.scale(250))
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        // Don't ask again checkbox
        val bottomPanel = JPanel(BorderLayout())
        bottomPanel.border = JBUI.Borders.empty(10, 0, 0, 0)

        dontAskAgainCheckbox = JBCheckBox("Don't ask again for MCP refactoring operations")
        bottomPanel.add(dontAskAgainCheckbox!!, BorderLayout.WEST)

        mainPanel.add(bottomPanel, BorderLayout.SOUTH)

        return mainPanel
    }

    override fun doOKAction() {
        // Save the "don't ask again" preference
        if (dontAskAgainCheckbox?.isSelected == true) {
            try {
                McpSettings.getInstance().confirmWriteOperations = false
            } catch (e: Exception) {
                // Ignore if settings can't be accessed
            }
        }
        super.doOKAction()
    }

    companion object {
        /**
         * Shows the confirmation dialog and returns true if the user confirmed.
         */
        fun confirm(
            project: Project?,
            operationName: String,
            description: String,
            affectedFiles: List<String>
        ): Boolean {
            val dialog = RefactoringConfirmationDialog(
                project,
                operationName,
                description,
                affectedFiles
            )
            return dialog.showAndGet()
        }

        /**
         * Checks if confirmation is required and shows the dialog if needed.
         * Returns true if the operation should proceed.
         */
        fun confirmIfNeeded(
            project: Project?,
            operationName: String,
            description: String,
            affectedFiles: List<String>
        ): Boolean {
            val confirmRequired = try {
                McpSettings.getInstance().confirmWriteOperations
            } catch (e: Exception) {
                false
            }

            if (!confirmRequired) {
                return true
            }

            return confirm(project, operationName, description, affectedFiles)
        }
    }
}
