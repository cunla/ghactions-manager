package com.dsoftware.ghtoolbar.ui.panels


import com.dsoftware.ghtoolbar.actions.ActionKeys
import com.dsoftware.ghtoolbar.api.model.GitHubWorkflowRun
import com.dsoftware.ghtoolbar.data.WorkflowRunListLoader
import com.dsoftware.ghtoolbar.ui.LoadingErrorHandler
import com.dsoftware.ghtoolbar.ui.ToolbarUtil
import com.dsoftware.ghtoolbar.workflow.WorkflowRunListSelectionHolder
import com.dsoftware.ghtoolbar.workflow.WorkflowRunSelectionContext
import com.intellij.ide.CopyProvider
import com.intellij.ide.actions.RefreshAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.util.Disposer
import com.intellij.ui.*
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.ui.*
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.ui.frame.ProgressStripe
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException
import org.jetbrains.plugins.github.pullrequest.data.GHListLoader
import org.jetbrains.plugins.github.ui.HtmlInfoPanel
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import javax.swing.event.ListSelectionEvent

class WorkflowRunList(model: ListModel<GitHubWorkflowRun>) : JBList<GitHubWorkflowRun>(model), DataProvider,
    CopyProvider {

    init {
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION

        val renderer = WorkflowRunsListCellRenderer()
        cellRenderer = renderer
        putClientProperty(UIUtil.NOT_IN_HIERARCHY_COMPONENTS, listOf(renderer))

        ScrollingUtil.installActions(this)
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val childComponent = ListUtil.getDeepestRendererChildComponentAt(this, event.point)
        if (childComponent !is JComponent) return null
        return childComponent.toolTipText
    }

    override fun getData(dataId: String): Any? = when {
        PlatformDataKeys.COPY_PROVIDER.`is`(dataId) -> this
        ActionKeys.SELECTED_WORKFLOW_RUN.`is`(dataId) -> selectedValue
        else -> null
    }

    private inner class WorkflowRunsListCellRenderer : ListCellRenderer<GitHubWorkflowRun>, JPanel() {

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
            list: JList<out GitHubWorkflowRun>,
            ghWorkflowRun: GitHubWorkflowRun,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            UIUtil.setBackgroundRecursively(this, ListUiUtil.WithTallRow.background(list, isSelected, list.hasFocus()))
            val primaryTextColor = ListUiUtil.WithTallRow.foreground(isSelected, list.hasFocus())
            val secondaryTextColor = ListUiUtil.WithTallRow.secondaryForeground(list, isSelected)

            stateIcon.icon = ToolbarUtil.statusIcon(ghWorkflowRun.status, ghWorkflowRun.conclusion)
            title.apply {
                text = ghWorkflowRun.head_commit.message
                foreground = primaryTextColor
            }

            info.apply {
                val updatedAtLabel = ToolbarUtil.makeTimePretty(ghWorkflowRun.updated_at)
                var action = "pushed by"
                if (ghWorkflowRun.event == "release") {
                    action = "created by"
                }
                text = "${ghWorkflowRun.name} #${ghWorkflowRun.run_number}: " +
                    "$action ${ghWorkflowRun.head_commit.author.name} " +
                    "on $updatedAtLabel"
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

    override fun performCopy(dataContext: DataContext) {
        TODO("Not yet implemented")
    }

    override fun isCopyEnabled(dataContext: DataContext): Boolean {
        return false
    }

    override fun isCopyVisible(dataContext: DataContext): Boolean {
        return false
    }
}

internal class WorkflowRunListLoaderPanel(
    disposable: Disposable,
    private val workflowRunsLoader: WorkflowRunListLoader,
    listReloadAction: RefreshAction,
    private val contentComponent: JComponent,
    private val loadAllAfterFirstScroll: Boolean = false
) : BorderLayoutPanel(), Disposable {

    private val progressStripe: ProgressStripe

    private var userScrolled = false
    private val scrollPane = ScrollPaneFactory.createScrollPane(
        contentComponent,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    ).apply {
        isOpaque = false
        viewport.isOpaque = false
        border = JBUI.Borders.empty()
        verticalScrollBar.model.addChangeListener {
            if (!userScrolled && verticalScrollBar.value > 0) userScrolled = true
            potentiallyLoadMore()
        }
    }

    private val infoPanel = HtmlInfoPanel()

    var errorHandler: LoadingErrorHandler? = null

    init {
        LOG.debug("Initialize WorkflowRunListLoaderPanel")
        progressStripe = ProgressStripe(
            JBUI.Panels.simplePanel(scrollPane).addToTop(infoPanel).apply {
                isOpaque = false
            },
            this,
            ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS
        )
        addToCenter(progressStripe)

        workflowRunsLoader.addLoadingStateChangeListener(this) { updateInfoPanels() }

        workflowRunsLoader.addErrorChangeListener(this) { updateInfoPanels() }
        workflowRunsLoader.addDataListener(this,
            object : GHListLoader.ListDataListener {
                override fun onDataAdded(startIdx: Int) {
                    updateInfoPanels()
                }

                override fun onDataRemoved(data: Any) {
                    updateInfoPanels()
                }
            }
        )

        setLoading(workflowRunsLoader.loading)
        updateInfoPanels()
        errorHandler = LoadingErrorHandler {
            LOG.warn("Error on GitHub Workflow Run list loading, resetting the loader")
            workflowRunsLoader.reset()
        }
        listReloadAction.registerCustomShortcutSet(this, disposable)
        val actionsGroup = DefaultActionGroup()
        actionsGroup.add(listReloadAction)
        val actionToolbar = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.CONTEXT_TOOLBAR, actionsGroup, false)
        actionToolbar.targetComponent = this

        add(actionToolbar.component, BorderLayout.WEST)

        Disposer.register(disposable) {
            Disposer.dispose(this)
        }
    }

    private fun updateInfoPanels() {
        setLoading(workflowRunsLoader.loading)
        val emptyText = (contentComponent as? ComponentWithEmptyText)?.emptyText ?: return
        emptyText.clear()
        if (workflowRunsLoader.loading) {
            infoPanel.setInfo(null)
            emptyText.text = "Loading..."
            return
        }
        val error = workflowRunsLoader.error
        if (error == null) {
            val msg = when {
                workflowRunsLoader.loadedData.isEmpty() -> "No workflow loaded"
                else -> "${workflowRunsLoader.loadedData.size} workflow runs loaded"
            }
            //language=HTML
            infoPanel.setInfo(
                """<html lang="en"><body>$msg</body></html>""".trimMargin(),
                HtmlInfoPanel.Severity.INFO
            )
            emptyText.text = "Nothing loaded. "
            emptyText.appendSecondaryText("Refresh", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
                workflowRunsLoader.reset()
            }
        } else {
            emptyText.appendText(
                if (workflowRunsLoader.loadedData.isEmpty()) "Can't load list" else "Can't load full list",
                SimpleTextAttributes.ERROR_ATTRIBUTES
            ).appendSecondaryText(
                getLoadingErrorText(error), SimpleTextAttributes.ERROR_ATTRIBUTES, null)

            errorHandler?.getActionForError()?.let {
                emptyText.appendSecondaryText(" ${it.getValue("Name")}", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, it)
            }
        }

    }

    private fun potentiallyLoadMore() {
        val canLoadMore = workflowRunsLoader.canLoadMore()
        val isScrollAtThreshold = isScrollAtThreshold()
        LOG.debug(
            "Potentially loading more workflow-runs: canLoadMore=${canLoadMore} &&" +
                "((userScrolled=$userScrolled && loadAllAfterFirstScroll=$loadAllAfterFirstScroll) || isScrollAtThreshold=$isScrollAtThreshold) "
        )
        if (canLoadMore && ((userScrolled && loadAllAfterFirstScroll) || isScrollAtThreshold)) {
            workflowRunsLoader.loadMore()
        }
    }

    private fun isScrollAtThreshold(): Boolean {
        val verticalScrollBar = scrollPane.verticalScrollBar
        val visibleAmount = verticalScrollBar.visibleAmount
        val value = verticalScrollBar.value
        val maximum = verticalScrollBar.maximum
        if (maximum == 0) return false
        val scrollFraction = (visibleAmount + value) / maximum.toFloat()
        if (scrollFraction < 0.5) return false
        return true
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

        private fun installWorkflowRunSelectionSaver(
            list: WorkflowRunList,
            listSelectionHolder: WorkflowRunListSelectionHolder,
        ) {
            var savedSelection: GitHubWorkflowRun? = null

            list.selectionModel.addListSelectionListener { e: ListSelectionEvent ->
                if (!e.valueIsAdjusting) {
                    val selectedIndex = list.selectedIndex
                    if (selectedIndex >= 0 && selectedIndex < list.model.size) {
                        listSelectionHolder.selection = list.model.getElementAt(selectedIndex)
                        savedSelection = null
                    }
                }
            }

            list.model.addListDataListener(object : ListDataListener {
                override fun intervalAdded(e: ListDataEvent) {
                    if (e.type == ListDataEvent.INTERVAL_ADDED)
                        (e.index0..e.index1).find { list.model.getElementAt(it) == savedSelection }
                            ?.run {
                                ApplicationManager.getApplication().invokeLater { ScrollingUtil.selectItem(list, this) }
                            }
                }

                override fun contentsChanged(e: ListDataEvent) {}
                override fun intervalRemoved(e: ListDataEvent) {
                    if (e.type == ListDataEvent.INTERVAL_REMOVED) savedSelection = listSelectionHolder.selection
                }
            })
        }

        fun createWorkflowRunsListComponent(
            context: WorkflowRunSelectionContext,
            disposable: Disposable,
        ): JComponent {
            val list = WorkflowRunList(context.dataContext.runsListModel).apply {
                emptyText.clear()
            }.also {
                it.addFocusListener(object : FocusListener {
                    override fun focusGained(e: FocusEvent?) {
                        if (it.selectedIndex < 0 && !it.isEmpty) it.selectedIndex = 0
                    }

                    override fun focusLost(e: FocusEvent?) {}
                })

                installPopup(it)
                installWorkflowRunSelectionSaver(it, context.runSelectionHolder)
            }

            val listReloadAction = actionManager.getAction("Github.Workflow.List.Reload") as RefreshAction

            return WorkflowRunListLoaderPanel(disposable, context.dataContext.runsListLoader, listReloadAction, list)
        }

        private fun getLoadingErrorText(error: Throwable, newLineSeparator: String = "\n"): String {
            if (error is GithubStatusCodeException && error.error != null) {
                val githubError = error.error!!
                val builder = StringBuilder(error.message).append(newLineSeparator)
                if (githubError.errors?.isNotEmpty()!!) {
                    builder.append(": ").append(newLineSeparator)
                    for (e in githubError.errors!!) {
                        builder.append(
                            e.message
                                ?: "${e.code} error in ${e.resource} field ${e.field}"
                        ).append(newLineSeparator)
                    }
                }
                return builder.toString()
            }

            return error.message?.let { addDotIfNeeded(it) } ?: "Unknown loading error."
        }

        private fun addDotIfNeeded(line: String) = if (line.endsWith('.')) line else "$line."
    }
}