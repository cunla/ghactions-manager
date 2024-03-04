package com.dsoftware.ghmanager.ui


import com.dsoftware.ghmanager.actions.ActionKeys
import com.dsoftware.ghmanager.data.JobsLoadingModelListener
import com.dsoftware.ghmanager.data.LogLoadingModelListener
import com.dsoftware.ghmanager.data.WorkflowDataContextService
import com.dsoftware.ghmanager.data.WorkflowRunSelectionContext
import com.dsoftware.ghmanager.i18n.MessagesBundle.message
import com.dsoftware.ghmanager.ui.panels.JobsListPanel
import com.dsoftware.ghmanager.ui.panels.createLogConsolePanel
import com.dsoftware.ghmanager.ui.panels.wfruns.WorkflowRunsListPanel
import com.dsoftware.ghmanager.ui.settings.GhActionsSettingsService
import com.intellij.ide.DataManager
import com.intellij.ide.actions.RefreshAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ClientProperty
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.GHCompletableFutureLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingPanelFactory
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import javax.swing.JComponent
import kotlin.properties.Delegates

class WorkflowToolWindowTabController(
    repositoryMapping: GHGitRepositoryMapping,
    private val ghAccount: GithubAccount,
    private val dataContextRepository: WorkflowDataContextService,
    parentDisposable: Disposable,
    private val toolWindow: ToolWindow,
) {
    val loadingModel: GHCompletableFutureLoadingModel<WorkflowRunSelectionContext>
    val panel: JComponent
    private val settingsService = toolWindow.project.service<GhActionsSettingsService>()
    private val actionManager = ActionManager.getInstance()
    private var checkedDisposable: CheckedDisposable =
        Disposer.newCheckedDisposable(parentDisposable, "WorkflowToolWindowTabController")
    private var contentDisposable by Delegates.observable<Disposable?>(null) { _, oldValue, newValue ->
        if (oldValue != null) Disposer.dispose(oldValue)
        if (newValue != null) Disposer.register(parentDisposable, newValue)
    }

    init {
        LOG.debug("Create WorkflowToolWindowTabController for ${repositoryMapping.remote.url}")
        Disposer.register(parentDisposable, checkedDisposable)
        contentDisposable = Disposable {
            dataContextRepository.clearContext(repositoryMapping)
        }
        loadingModel = GHCompletableFutureLoadingModel<WorkflowRunSelectionContext>(checkedDisposable).apply {
            future = dataContextRepository.acquireContext(
                checkedDisposable,
                repositoryMapping,
                ghAccount,
                toolWindow
            )
        }
        val errorHandler = GHApiLoadingErrorHandler(toolWindow.project, ghAccount) {
            dataContextRepository.clearContext(repositoryMapping)
            loadingModel.future = dataContextRepository.acquireContext(
                checkedDisposable,
                repositoryMapping,
                ghAccount,
                toolWindow
            )
        }
        panel = GHLoadingPanelFactory(
            loadingModel,
            message("panel.workflow-runs.not-loading"),
            message("panel.workflow-runs.loading-error"),
            errorHandler,
        ).create { _, result ->
            val content = createContent(result)
            ClientProperty.put(content, AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true)
            content
        }

    }

    private fun createContent(selectedRunContext: WorkflowRunSelectionContext): JComponent {
        val workflowRunsListLoadingPanel = WorkflowRunsListPanel(checkedDisposable, selectedRunContext)
        val jobLoadingPanel = createJobsPanel(selectedRunContext)
        val logLoadingPanel = createLogPanel(selectedRunContext)

        val runPanel = OnePixelSplitter(
            settingsService.state.jobListAboveLogs,
            "GitHub.Workflows.Component.Jobs",
            if (settingsService.state.jobListAboveLogs) 0.3f else 0.5f
        ).apply {
            background = UIUtil.getListBackground()
            isOpaque = true
            isFocusCycleRoot = true
            firstComponent = jobLoadingPanel
            secondComponent = logLoadingPanel
                .also {
                    (actionManager.getAction("Github.Workflow.Log.List.Reload") as RefreshAction)
                        .registerCustomShortcutSet(it, checkedDisposable)
                }
        }

        return OnePixelSplitter("GitHub.Workflows.Component", 0.3f).apply {
            background = UIUtil.getListBackground()
            isOpaque = true
            isFocusCycleRoot = true
            firstComponent = workflowRunsListLoadingPanel
            secondComponent = runPanel
        }.also {
            DataManager.registerDataProvider(it) { dataId ->
                when {
                    ActionKeys.ACTION_DATA_CONTEXT.`is`(dataId) -> selectedRunContext
                    else -> null
                }
            }
        }
    }


    private fun createLogPanel(selectedRunContext: WorkflowRunSelectionContext): JComponent {
        LOG.debug("Create log panel")
        val model = LogLoadingModelListener(
            selectedRunContext.selectedJobDisposable,
            selectedRunContext.jobLogDataProviderLoadModel,
            selectedRunContext.jobSelectionHolder
        )
        val panel = GHLoadingPanelFactory(
            model.logsLoadingModel,
            message("panel.log.not-loading"),
            message("panel.log.loading-error", selectedRunContext.jobSelectionHolder.selection?.name ?: ""),
            GHApiLoadingErrorHandler(toolWindow.project, ghAccount) {}
        ).create { _, _ ->
            createLogConsolePanel(toolWindow.project, model, selectedRunContext.selectedRunDisposable)
        }
        return panel
    }

    private fun createJobsPanel(selectedRunContext: WorkflowRunSelectionContext): JComponent {
        val jobsLoadingModel = JobsLoadingModelListener(
            selectedRunContext.selectedRunDisposable,
            selectedRunContext.jobDataProviderLoadModel,
            selectedRunContext.runSelectionHolder
        )

        val jobsPanel = GHLoadingPanelFactory(
            jobsLoadingModel.jobsLoadingModel,
            message("panel.jobs.not-loading"),
            message("panel.jobs.loading-error", selectedRunContext.runSelectionHolder.selection?.name ?: ""),
            GHApiLoadingErrorHandler(toolWindow.project, ghAccount) {}
        ).create { _, _ ->
            JobsListPanel(
                checkedDisposable,
                jobsLoadingModel.jobsModel,
                selectedRunContext,
                infoInNewLine = !settingsService.state.jobListAboveLogs,
            )
        }

        return jobsPanel
    }


    companion object {
        val KEY = Key.create<WorkflowToolWindowTabController>("Github.Actions.ToolWindow.Tab.Controller")
        private val LOG = logger<WorkflowToolWindowTabController>()
    }
}