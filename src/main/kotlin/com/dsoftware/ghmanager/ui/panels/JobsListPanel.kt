package com.dsoftware.ghmanager.ui.panels


import com.dsoftware.ghmanager.api.model.Job
import com.dsoftware.ghmanager.api.model.Status
import com.dsoftware.ghmanager.api.model.WorkflowRunJobs
import com.dsoftware.ghmanager.data.WorkflowRunSelectionContext
import com.dsoftware.ghmanager.i18n.MessagesBundle.message
import com.dsoftware.ghmanager.ui.ToolbarUtil
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ListUtil
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.plugins.github.ui.HtmlInfoPanel
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.ScrollPaneConstants


class JobsListPanel(
    parentDisposable: Disposable,
    jobValueModel: SingleValueModel<WorkflowRunJobs?>,
    private val runSelectionContext: WorkflowRunSelectionContext,
    private val infoInNewLine: Boolean
) : BorderLayoutPanel(), Disposable {
    private val topInfoPanel = HtmlInfoPanel()
    private val jobsListModel = CollectionListModel<Job>()

    init {
        Disposer.register(parentDisposable, this)
        jobsListModel.removeAll()
        jobValueModel.addAndInvokeListener { updatePanel(it) }
        val scrollPane = ScrollPaneFactory.createScrollPane(
            createListComponent(),
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        ).apply {
            isOpaque = false
            viewport.isOpaque = false
            border = JBUI.Borders.empty()
        }

        isOpaque = false
        add(topInfoPanel, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
    }

    private fun updatePanel(newJobs: WorkflowRunJobs?) {
        jobsListModel.removeAll()
        topInfoPanel.setInfo("")
        newJobs?.let {
            jobsListModel.add(it.jobs)
            topInfoPanel.setInfo(infoTitleString(it.jobs))
        }
    }

    private fun infoTitleString(jobs: List<Job>): String {
        val statusCounter = jobs.groupingBy { job -> job.status }.eachCount()
        val conclusionCounter = jobs.groupingBy { job -> job.conclusion }.eachCount()

        val (statusCount, status) = if (statusCounter.containsKey(Status.QUEUED.value)) {
            Pair(statusCounter[Status.QUEUED.value]!!, "queued")
        } else if (statusCounter.containsKey(Status.IN_PROGRESS.value)) {
            Pair(statusCounter[Status.IN_PROGRESS.value]!!, "in progress")
        } else {
            Pair(statusCounter[Status.COMPLETED.value] ?: jobs.size, "completed")
        }
        val res = message("panel.jobs.info.title", statusCount, jobs.size, status)
        if (conclusionCounter.containsKey("failure")) {
            return res + message("panel.jobs.info.jobs-failed", conclusionCounter["failure"]!!)
        }
        return res
    }

    private fun createListComponent(): JobListComponent {
        return JobListComponent(jobsListModel, infoInNewLine).apply {
            emptyText.text = message("panel.jobs.info.no-jobs")
        }.also {
            installPopup(it)
            ToolbarUtil.installSelectionHolder(it, runSelectionContext.jobSelectionHolder)
        }
    }

    private fun installPopup(list: JobListComponent) {
        val actionManager = ActionManager.getInstance()
        list.addMouseListener(object : PopupHandler() {
            override fun invokePopup(comp: Component, x: Int, y: Int) {
                val (place, groupId) = if (ListUtil.isPointOnSelection(list, x, y)) {
                    Pair("JobListPopupSelected", "Github.ToolWindow.JobList.Popup.Selected")
                } else {
                    Pair("JobListPopup", "Github.ToolWindow.JobList.Popup")
                }
                val popupMenu: ActionPopupMenu = actionManager.createActionPopupMenu(
                    place, actionManager.getAction(groupId) as ActionGroup,
                )

                popupMenu.setTargetComponent(list)
                popupMenu.component.show(comp, x, y)
            }
        })
    }

    override fun dispose() {}

}

