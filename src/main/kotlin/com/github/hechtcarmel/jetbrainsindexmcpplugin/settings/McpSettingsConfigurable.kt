package com.github.hechtcarmel.jetbrainsindexmcpplugin.settings

import com.github.hechtcarmel.jetbrainsindexmcpplugin.McpBundle
import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class McpSettingsConfigurable : Configurable {

    private var panel: JPanel? = null
    private var maxHistorySizeSpinner: JSpinner? = null
    private var autoScrollCheckBox: JBCheckBox? = null
    private var syncExternalChangesCheckBox: JBCheckBox? = null

    override fun getDisplayName(): String = McpBundle.message("settings.title")

    override fun createComponent(): JComponent {
        maxHistorySizeSpinner = JSpinner(SpinnerNumberModel(100, 10, 10000, 10))
        autoScrollCheckBox = JBCheckBox(McpBundle.message("settings.autoScroll"))
        syncExternalChangesCheckBox = JBCheckBox(McpBundle.message("settings.syncExternalChanges")).apply {
            toolTipText = McpBundle.message("settings.syncExternalChanges.tooltip")
        }

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel(McpBundle.message("settings.maxHistorySize") + ":"), maxHistorySizeSpinner!!, 1, false)
            .addComponent(autoScrollCheckBox!!, 1)
            .addComponent(syncExternalChangesCheckBox!!, 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        return panel!!
    }

    override fun isModified(): Boolean {
        val settings = McpSettings.getInstance()
        return maxHistorySizeSpinner?.value != settings.maxHistorySize ||
            autoScrollCheckBox?.isSelected != settings.autoScroll ||
            syncExternalChangesCheckBox?.isSelected != settings.syncExternalChanges
    }

    override fun apply() {
        val settings = McpSettings.getInstance()
        settings.maxHistorySize = maxHistorySizeSpinner?.value as? Int ?: 100
        settings.autoScroll = autoScrollCheckBox?.isSelected ?: true
        settings.syncExternalChanges = syncExternalChangesCheckBox?.isSelected ?: false
    }

    override fun reset() {
        val settings = McpSettings.getInstance()
        maxHistorySizeSpinner?.value = settings.maxHistorySize
        autoScrollCheckBox?.isSelected = settings.autoScroll
        syncExternalChangesCheckBox?.isSelected = settings.syncExternalChanges
    }

    override fun disposeUIResources() {
        panel = null
        maxHistorySizeSpinner = null
        autoScrollCheckBox = null
        syncExternalChangesCheckBox = null
    }
}
