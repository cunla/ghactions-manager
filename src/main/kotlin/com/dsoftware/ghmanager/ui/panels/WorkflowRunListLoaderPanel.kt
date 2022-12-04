package com.dsoftware.ghmanager.ui.panels


import com.dsoftware.ghmanager.actions.ActionKeys
import com.dsoftware.ghmanager.api.model.WorkflowRun
import com.dsoftware.ghmanager.data.WorkflowRunListLoader
import com.dsoftware.ghmanager.data.WorkflowRunSelectionContext
import com.dsoftware.ghmanager.ui.ToolbarUtil
import com.intellij.ide.CopyProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.util.Disposer
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ClientProperty
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.ListUtil
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.ui.frame.ProgressStripe
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException
import org.jetbrains.plugins.github.ui.HtmlInfoPanel
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ListModel
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class LoadingErrorHandler(private val resetRunnable: () -> Unit) {
    fun getActionForError(): Action {
        return RetryAction()
    }

    private inner class RetryAction : AbstractAction("Retry") {
        override fun actionPerformed(e: ActionEvent?) = resetRunnable()
    }
}

class WorkflowRunList(model: ListModel<WorkflowRun>) : JBList<WorkflowRun>(model), DataProvider,
    CopyProvider {

    init {
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION

        val renderer = WorkflowRunsListCellRenderer()
        cellRenderer = renderer
        putClientProperty(UIUtil.NOT_IN_HIERARCHY_COMPONENTS, listOf(renderer))

        ScrollingUtil.installActions(this)
        ClientProperty.put(this, AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true)
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val childComponent = ListUtil.getDeepestRendererChildComponentAt(this, event.point)
        if (childComponent !is JComponent) return null
        return childComponent.toolTipText
    }

    override fun getData(dataId: String): Any? = when {
        PlatformDataKeys.COPY_PROVIDER.`is`(dataId) -> this
        ActionKeys.SELECTED_WORKFLOW_RUN.`is`(dataId) -> selectedValue
        ActionKeys.SELECTED_WORKFLOW_RUN_FILEPATH.`is`(dataId) -> selectedValue?.path
        else -> null
    }

    private inner class WorkflowRunsListCellRenderer : ListCellRenderer<WorkflowRun>, JPanel() {

        private val stateIcon = JLabel()
        private val title = JLabel()
        private val info = JLabel()
        private val labels = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
        }

        init {
            border = JBUI.Borders.empty(5, 8)
            layout = MigLayout(
                LC().gridGap("0", "0")
                    .insets("0", "0", "0", "0")
                    .fillX()
            )
            val gapAfter = "${JBUI.scale(5)}px"
            add(stateIcon, CC().gapAfter(gapAfter))
            add(title, CC().growX().pushX().minWidth("pref/2px"))
            add(labels, CC().minWidth("pref/2px").alignX("right").wrap())
            add(info, CC().minWidth("pref/2px").skip(1).spanX(3))
        }

        override fun getListCellRendererComponent(
            list: JList<out WorkflowRun>,
            ghWorkflowRun: WorkflowRun,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            UIUtil.setBackgroundRecursively(this, ListUiUtil.WithTallRow.background(list, isSelected, list.hasFocus()))
            val primaryTextColor = ListUiUtil.WithTallRow.foreground(isSelected, list.hasFocus())
            val secondaryTextColor = ListUiUtil.WithTallRow.secondaryForeground(isSelected, list.hasFocus())

            stateIcon.icon = ToolbarUtil.statusIcon(ghWorkflowRun.status, ghWorkflowRun.conclusion)
            title.apply {
                text = ghWorkflowRun.head_commit.message.split("\n")[0]
                foreground = primaryTextColor
            }

            info.apply {
                val updatedAtLabel = ToolbarUtil.makeTimePretty(ghWorkflowRun.updated_at)
                var action = "pushed by"
                if (ghWorkflowRun.event == "release") {
                    action = "created by"
                }
                text = "${ghWorkflowRun.name} #${ghWorkflowRun.run_number}: " +
                    "$action ${ghWorkflowRun.head_commit.author.name} started $updatedAtLabel"
                foreground = secondaryTextColor
            }
            labels.apply {
                removeAll()
                add(JBLabel(" ${ghWorkflowRun.head_branch} ", UIUtil.ComponentStyle.SMALL).apply {
                    foreground = JBColor(ColorUtil.softer(secondaryTextColor), ColorUtil.softer(secondaryTextColor))
                })
                add(Box.createRigidArea(JBDimension(4, 0)))
            }
            return this
        }
    }

    override fun performCopy(dataContext: DataContext) = TODO("Not yet implemented")
    override fun isCopyEnabled(dataContext: DataContext): Boolean = false
    override fun isCopyVisible(dataContext: DataContext): Boolean = false
}

internal class WorkflowRunListLoaderPanel(
    disposable: Disposable,
    private val context: WorkflowRunSelectionContext,
) : BorderLayoutPanel(), Disposable {
    private val runListComponent: WorkflowRunList = WorkflowRunList(context.runsListModel)
        .apply {
            emptyText.clear()
        }.also {
            installPopup(it)
            ToolbarUtil.installSelectionHolder(it, context.runSelectionHolder)
        }
    private val scrollPane = ScrollPaneFactory.createScrollPane(
        runListComponent,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    ).apply {
        isOpaque = false
        viewport.isOpaque = false
        border = JBUI.Borders.empty()
    }
    private val progressStripe: ProgressStripe
    private val infoPanel = HtmlInfoPanel()
    private val workflowRunsLoader: WorkflowRunListLoader
        get() = context.runsListLoader

    private val errorHandler = LoadingErrorHandler {
        LOG.warn("Error on GitHub Workflow Run list loading, resetting the loader")
        workflowRunsLoader.reset()
    }

    init {
        LOG.debug("Initialize WorkflowRunListLoaderPanel")
        progressStripe = ProgressStripe(
            JBUI.Panels.simplePanel(scrollPane).addToTop(infoPanel).apply {
                isOpaque = false
            }, this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS
        )
        addToCenter(progressStripe)

        workflowRunsLoader.addLoadingStateChangeListener(this) {
            setLoading(workflowRunsLoader.loading)
            updateEmptyText()
        }

        workflowRunsLoader.addErrorChangeListener(this) {
            updateEmptyText()
        }

        setLoading(workflowRunsLoader.loading)
        updateEmptyText()
        val actionsManager = ActionManager.getInstance()
        val actionsGroup = actionsManager.getAction("GHWorkflows.ActionGroup") as ActionGroup
        val actionToolbar = actionsManager
            .createActionToolbar(ActionPlaces.CONTEXT_TOOLBAR, actionsGroup, false)
        actionToolbar.targetComponent = this

        add(actionToolbar.component, BorderLayout.WEST)

        Disposer.register(disposable) {
            Disposer.dispose(this)
        }
        runListComponent.model.addListDataListener(object : ListDataListener {
            override fun intervalAdded(e: ListDataEvent) {
                if (e.type == ListDataEvent.INTERVAL_ADDED) updateEmptyText()
            }

            override fun contentsChanged(e: ListDataEvent) {}
            override fun intervalRemoved(e: ListDataEvent) {
                if (e.type == ListDataEvent.INTERVAL_REMOVED) updateEmptyText()
            }
        })
    }

    private fun updateEmptyText() {
        runListComponent.emptyText.clear()
        if (workflowRunsLoader.loading) {
            runListComponent.emptyText.text = "Loading workflow runs..."
            return
        }
        val error = workflowRunsLoader.error
        if (error == null) {
            runListComponent.emptyText.text = "No workflow run loaded. "
            runListComponent.emptyText.appendSecondaryText("Refresh", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
                workflowRunsLoader.reset()
            }
            infoPanel.setInfo(
                when {
                    workflowRunsLoader.loading -> "Loading workflow runs..."
                    workflowRunsLoader.loadedData.isEmpty() -> "No workflow runs"
                    else -> "${workflowRunsLoader.loadedData.size} workflow runs loaded out of ${workflowRunsLoader.totalCount}"
                }
            )
        } else {
            LOG.warn("Got error when getting workflow-runs: $error")
            runListComponent.emptyText.appendText(
                "Can't load workflow runs - check that the token you set in GitHub settings have sufficient permissions",
                SimpleTextAttributes.ERROR_ATTRIBUTES
            ).appendSecondaryText(
                getLoadingErrorText(workflowRunsLoader.url, error),
                SimpleTextAttributes.ERROR_ATTRIBUTES,
                null
            )

            errorHandler.getActionForError().let {
                runListComponent.emptyText.appendSecondaryText(
                    "\n${it.getValue("Name")}\n",
                    SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                    it
                )
            }
        }

    }


    override fun dispose() {}

    private fun setLoading(isLoading: Boolean) {
        if (isLoading) progressStripe.startLoading() else progressStripe.stopLoading()
    }

    companion object {
        private val LOG = logger<WorkflowRunListLoaderPanel>()
        private val actionManager = ActionManager.getInstance()
        private fun installPopup(list: WorkflowRunList) {
            list.addMouseListener(object : PopupHandler() {
                override fun invokePopup(comp: Component, x: Int, y: Int) {

                    val (place, groupId) = if (ListUtil.isPointOnSelection(list, x, y)) {
                        Pair("GithubWorkflowListPopupSelected", "Github.Workflow.ToolWindow.List.Popup.Selected")
                    } else {
                        Pair("GithubWorkflowListPopup", "Github.Workflow.ToolWindow.List.Popup")
                    }
                    val popupMenu: ActionPopupMenu =
                        actionManager.createActionPopupMenu(
                            place,
                            actionManager.getAction(groupId) as ActionGroup
                        )

                    popupMenu.setTargetComponent(list)
                    popupMenu.component.show(comp, x, y)
                }
            })
        }

        fun createWorkflowRunsListComponent(
            context: WorkflowRunSelectionContext,
            disposable: Disposable,
        ): JComponent {
            return WorkflowRunListLoaderPanel(disposable, context)
        }

        private fun getLoadingErrorText(url: String, error: Throwable, newLineSeparator: String = "\n"): String {
            if (error is GithubStatusCodeException && error.error != null) {
                val githubError = error.error!!
                val builder = StringBuilder("url: $url").append(newLineSeparator)
                    .append(error.message).append(newLineSeparator)
                if (githubError.errors?.isNotEmpty()!!) {
                    builder.append(": ").append(newLineSeparator)
                    for (e in githubError.errors!!) {
                        builder.append(
                            e.message
                                ?: "${e.code} error in ${e.resource} field ${e.field}"
                        ).append(newLineSeparator)
                    }
                }
                val res = builder.toString()
                LOG.warn("Error: $res when getting URL: $url")
                return res
            }

            return error.message?.let { addDotIfNeeded(it) } ?: "Unknown loading error."
        }

        private fun addDotIfNeeded(line: String) = if (line.endsWith('.')) line else "$line."
    }
}