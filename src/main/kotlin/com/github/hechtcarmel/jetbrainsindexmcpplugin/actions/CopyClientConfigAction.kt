package com.github.hechtcarmel.jetbrainsindexmcpplugin.actions

import com.github.hechtcarmel.jetbrainsindexmcpplugin.McpBundle
import com.github.hechtcarmel.jetbrainsindexmcpplugin.McpConstants
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.ClientConfigGenerator
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.ClientConfigGenerator.ClientType
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import java.awt.datatransfer.StringSelection

/**
 * Action that allows users to copy MCP client configuration for various AI assistants.
 *
 * Shows a popup to select the client type, then copies the appropriate configuration
 * to the clipboard and shows a notification with the file location hint.
 */
class CopyClientConfigAction : AnAction(
    McpBundle.message("toolWindow.copyConfig"),
    "Copy MCP client configuration to clipboard",
    null
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project

        val popup = createClientSelectionPopup { selectedClient ->
            copyConfigToClipboard(selectedClient, project)
        }

        popup.showInBestPositionFor(e.dataContext)
    }

    private fun createClientSelectionPopup(onSelected: (ClientType) -> Unit): ListPopup {
        val clients = ClientConfigGenerator.getAvailableClients()

        val step = object : BaseListPopupStep<ClientType>(
            McpBundle.message("config.selectClient"),
            clients
        ) {
            override fun getTextFor(value: ClientType): String = value.displayName

            override fun onChosen(selectedValue: ClientType, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) {
                    onSelected(selectedValue)
                }
                return FINAL_CHOICE
            }
        }

        return JBPopupFactory.getInstance().createListPopup(step)
    }

    private fun copyConfigToClipboard(clientType: ClientType, project: com.intellij.openapi.project.Project?) {
        val config = ClientConfigGenerator.generateConfig(clientType)
        val locationHint = ClientConfigGenerator.getConfigLocationHint(clientType)

        CopyPasteManager.getInstance().setContents(StringSelection(config))

        val message = McpBundle.message("notification.configCopied", clientType.displayName, locationHint)

        NotificationGroupManager.getInstance()
            .getNotificationGroup(McpConstants.NOTIFICATION_GROUP_ID)
            .createNotification(
                McpBundle.message("notification.configCopiedTitle"),
                message,
                NotificationType.INFORMATION
            )
            .notify(project)
    }
}
