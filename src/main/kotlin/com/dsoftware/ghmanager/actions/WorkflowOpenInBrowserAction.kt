package com.dsoftware.ghmanager.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.DumbAwareAction

abstract class OpenInBrowserAction
    : DumbAwareAction("Open GitHub Link in Browser", null, AllIcons.Xml.Browsers.Chrome) {

    override fun update(e: AnActionEvent) {
        val data = getData(e.dataContext)
        e.presentation.isEnabledAndVisible = data != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        getData(e.dataContext)?.let { BrowserUtil.browse(it) }
    }

    abstract fun getData(dataContext: DataContext): String?
}

class WorkflowOpenInBrowserAction : OpenInBrowserAction() {

    override fun getData(dataContext: DataContext): String? {
        dataContext.getData(CommonDataKeys.PROJECT) ?: return null
        return dataContext.getData(ActionKeys.SELECTED_WORKFLOW_RUN)?.html_url
    }
}

class JobOpenInBrowserAction : OpenInBrowserAction() {

    override fun getData(dataContext: DataContext): String? {
        dataContext.getData(CommonDataKeys.PROJECT) ?: return null
        return dataContext.getData(ActionKeys.SELECTED_JOB)?.htmlUrl
    }
}

//TODO
class PullRequestOpenInBrowserAction(val url: String) : DumbAwareAction("Open Pull-Request in Browser") {
    override fun actionPerformed(e: AnActionEvent) {
        BrowserUtil.browse(url)
    }
}