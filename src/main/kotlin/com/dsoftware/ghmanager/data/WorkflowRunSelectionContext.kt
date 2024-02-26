package com.dsoftware.ghmanager.data

import com.dsoftware.ghmanager.api.WorkflowRunFilter
import com.dsoftware.ghmanager.api.model.Job
import com.dsoftware.ghmanager.api.model.WorkflowRun
import com.dsoftware.ghmanager.data.providers.JobLogDataProvider
import com.dsoftware.ghmanager.data.providers.SingleRunDataLoader
import com.dsoftware.ghmanager.data.providers.WorkflowRunJobsDataProvider
import com.dsoftware.ghmanager.ui.ToolbarUtil
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CollectionListModel
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GithubUrlUtil
import java.util.concurrent.ScheduledFuture


class WorkflowRunSelectionContext internal constructor(
    parentDisposable: CheckedDisposable,
    val project: Project,
    val account: GithubAccount,
    val dataLoader: SingleRunDataLoader,
    val repositoryMapping: GHGitRepositoryMapping,
    val requestExecutor: GithubApiRequestExecutor,
    val runSelectionHolder: WorkflowRunListSelectionHolder = WorkflowRunListSelectionHolder(),
    val jobSelectionHolder: JobListSelectionHolder = JobListSelectionHolder(),
) : Disposable.Parent {

    private val task: ScheduledFuture<*>
    private val fullPath = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(repositoryMapping.remote.url)
        ?: throw IllegalArgumentException(
            "Invalid GitHub Repository URL - ${repositoryMapping.remote.url} is not a GitHub repository"
        )
    private val selectedWfRun: WorkflowRun?
        get() = runSelectionHolder.selection
    val runsListLoader: WorkflowRunListLoader = WorkflowRunListLoader(
        project,
        this,
        requestExecutor,
        RepositoryCoordinates(account.server, fullPath),
        WorkflowRunFilter(),
    )
    val runsListModel: CollectionListModel<WorkflowRun>
        get() = runsListLoader.workflowRunsListModel

    var selectedRunDisposable = Disposer.newDisposable("Selected run disposable")
    val jobDataProviderLoadModel: SingleValueModel<WorkflowRunJobsDataProvider?> = SingleValueModel(null)
    val jobsDataProvider: WorkflowRunJobsDataProvider?
        get() = selectedWfRun?.let { dataLoader.getJobsDataProvider(it) }
    var selectedJobDisposable = Disposer.newDisposable("Selected job disposable")
    private val selectedJob: Job?
        get() = jobSelectionHolder.selection
    val jobLogDataProviderLoadModel: SingleValueModel<JobLogDataProvider?> = SingleValueModel(null)
    val logDataProvider: JobLogDataProvider?
        get() = selectedJob?.let { dataLoader.getJobLogDataProvider(it) }

    init {
        if (!parentDisposable.isDisposed) {
            Disposer.register(parentDisposable, this)
        }
        runSelectionHolder.addSelectionChangeListener(this) {
            LOG.debug("runSelectionHolder selection change listener")
            setNewJobsProvider()
            setNewLogProvider()
            selectedRunDisposable.dispose()
            selectedRunDisposable = Disposer.newDisposable("Selected run disposable")
        }
        jobSelectionHolder.addSelectionChangeListener(this) {
            LOG.debug("jobSelectionHolder selection change listener")
            setNewLogProvider()
            selectedJobDisposable.dispose()
            selectedJobDisposable = Disposer.newDisposable("Selected job disposable")
        }
        dataLoader.addInvalidationListener(this) {
            LOG.debug("invalidation listener")
            jobDataProviderLoadModel.value = null
            jobDataProviderLoadModel.value = null
            selectedRunDisposable.dispose()
        }
        task = ToolbarUtil.executeTaskAtSettingsFrequency(project) {
            if (selectedWfRun == null) {
                return@executeTaskAtSettingsFrequency
            }
            LOG.info("Checking updated status for $selectedWfRun.id")
            if (selectedWfRun?.status != "completed") {
                jobsDataProvider?.reload()
            }
            if (selectedJob?.status != "completed") {
                logDataProvider?.reload()
            }
        }
    }

    fun getCurrentAccountGHUser(): GHUser {
        return runsListLoader.repoCollaborators.first { user -> user.shortName == account.name }
    }

    private fun setNewJobsProvider() {
        val oldValue = jobDataProviderLoadModel.value
        val newValue = jobsDataProvider
        if (oldValue != newValue && newValue != null && oldValue?.url() != newValue.url()) {
            jobDataProviderLoadModel.value = newValue
            jobSelectionHolder.selection = null
        }
    }

    private fun setNewLogProvider() {
        val oldValue = jobLogDataProviderLoadModel.value
        val newValue = logDataProvider
        if (oldValue != newValue && oldValue?.url() != newValue?.url()) {
            jobLogDataProviderLoadModel.value = newValue
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