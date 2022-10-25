package com.dsoftware.ghmanager.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.github.util.GithubUtil

class ShowGithubSettingsAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        ShowSettingsUtil.getInstance().showSettingsDialog(
            e.project, GithubUtil.SERVICE_DISPLAY_NAME
        )
    }
}