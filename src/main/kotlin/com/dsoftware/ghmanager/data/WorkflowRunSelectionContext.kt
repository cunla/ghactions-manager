package com.dsoftware.ghmanager.data

import com.dsoftware.ghmanager.api.WorkflowRunFilter
import com.dsoftware.ghmanager.api.model.ListItem
import com.dsoftware.ghmanager.api.model.WorkflowRun
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CollectionListModel
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener


class WorkflowRunSelectionContext internal constructor(
    parentDisposable: CheckedDisposable,
    val project: Project,
    private val account: GithubAccount,
    val dataLoader: SingleRunDataLoader,
    val runsListLoader: WorkflowRunListLoader,
    val repositoryMapping: GHGitRepositoryMapping,
    val requestExecutor: GithubApiRequestExecutor,
    val runSelectionHolder: WorkflowRunListSelectionHolder = WorkflowRunListSelectionHolder(),
    val jobSelectionHolder: JobListSelectionHolder = JobListSelectionHolder(),
) : Disposable.Parent {
    private val frequency: Long = runsListLoader.frequency()
    private val task: ScheduledFuture<*>
    val runsListModel = CollectionListModel<ListItem>()
    private val selectedWfRun: WorkflowRun?
        get() = runSelectionHolder.selection
    val jobDataProviderLoadModel: SingleValueModel<WorkflowRunJobsDataProvider?> = SingleValueModel(null)
    val logDataProviderLoadModel: SingleValueModel<WorkflowRunLogsDataProvider?> = SingleValueModel(null)
    var selectedRunDisposable = Disposer.newDisposable("Selected run disposable")
    val logsDataProvider: WorkflowRunLogsDataProvider?
        get() = selectedWfRun?.let { dataLoader.getLogsDataProvider(it) }
    val jobsDataProvider: WorkflowRunJobsDataProvider?
        get() = selectedWfRun?.let { dataLoader.getJobsDataProvider(it) }

    init {
        if (!parentDisposable.isDisposed) {
            Disposer.register(parentDisposable, this)
        }
        runSelectionHolder.addSelectionChangeListener(parentDisposable) {
            LOG.debug("runSelectionHolder selection change listener")
            setNewJobsProvider()
            setNewLogProvider()
            selectedRunDisposable.dispose()
            selectedRunDisposable = Disposer.newDisposable("Selected run disposable")
        }
        dataLoader.addInvalidationListener(this) {
            LOG.debug("invalidation listener")
            jobDataProviderLoadModel.value = null
            logDataProviderLoadModel.value = null
            selectedRunDisposable.dispose()
        }
        task = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay({
            if (selectedWfRun == null) {
                return@scheduleWithFixedDelay
            }
            LOG.info("Checking updated status for $selectedWfRun.id")
            val status = selectedWfRun?.status
            if (selectedWfRun != null && status != "completed") {
                jobsDataProvider?.reload()
                logsDataProvider?.reload()
            }
        }, 1, frequency, TimeUnit.SECONDS)
        runsListLoader.listModel.addListDataListener(object : ListDataListener {
            override fun intervalAdded(e: ListDataEvent) {
                if (e.type == ListDataEvent.INTERVAL_ADDED) update()
            }

            override fun contentsChanged(e: ListDataEvent) {}
            override fun intervalRemoved(e: ListDataEvent) {
                if (e.type == ListDataEvent.INTERVAL_REMOVED) update()
            }

            fun update() {
                val runsByWorkflowId = runsListLoader.listModel.items.groupBy { it.workflowId }
                val workflowMap = runsListLoader.workflowTypes.associateBy { it.id }
                val result = mutableListOf<ListItem>()
                runsByWorkflowId.forEach { (workflowId, runs) ->
                    val workflow = workflowMap[workflowId]
                    if (workflow != null) {
                        result.add(workflow)
                        result.addAll(runs)
                    }
                }
                runsListModel.replaceAll(result)
            }

        })
    }

    fun getCurrentAccountGHUser(): GHUser {
        return runsListLoader.repoCollaborators.first { user -> user.shortName == account.name }
    }

    private fun setNewJobsProvider() {
        val oldValue = jobDataProviderLoadModel.value
        val newValue = jobsDataProvider
        if (newValue != oldValue) {
            jobSelectionHolder.selection = null
        }
        if (oldValue != newValue && newValue != null && oldValue?.url() != newValue.url()) {
            jobDataProviderLoadModel.value = newValue
        }
    }

    private fun setNewLogProvider() {
        val oldValue = logDataProviderLoadModel.value
        val newValue = logsDataProvider
        if (oldValue != newValue && newValue != null && oldValue?.url() != newValue.url()) {
            logDataProviderLoadModel.value = newValue
        }
    }

    fun resetAllData() {
        runsListLoader.reset()
        runsListLoader.loadMore(true)
        dataLoader.invalidateAllData()
    }

    companion object {
        private val LOG = logger<WorkflowRunSelectionContext>()
    }

    override fun dispose() {}

    override fun beforeTreeDispose() {
        task.cancel(true)
    }

    fun updateFilter(filter: WorkflowRunFilter) {
        runsListLoader.setFilter(filter)
        resetAllData()
    }
}