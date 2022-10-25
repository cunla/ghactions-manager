package com.dsoftware.ghmanager.data

import com.dsoftware.ghmanager.ui.settings.GhActionsSettingsService
import com.intellij.collaboration.async.CompletableFutureUtil.submitIOTask
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GHRepositoryPath
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates
import org.jetbrains.plugins.github.util.GithubUrlUtil
import org.jetbrains.plugins.github.util.LazyCancellableBackgroundProcessValue
import java.io.IOException
import java.util.concurrent.CompletableFuture

data class RepositoryCoordinates(
    val serverPath: GithubServerPath, val repositoryPath: GHRepositoryPath
) {
    override fun toString(): String {
        return "$serverPath/$repositoryPath"
    }
}

@Service
class WorkflowDataContextRepository {

    private val repositories =
        mutableMapOf<GHRepositoryCoordinates, LazyCancellableBackgroundProcessValue<WorkflowRunSelectionContext>>()

    @RequiresBackgroundThread
    @Throws(IOException::class)
    fun getContext(
        disposable: Disposable,
        account: GithubAccount,
        repositoryMapping: GHGitRepositoryMapping,
        toolWindow: ToolWindow,
    ): WorkflowRunSelectionContext {
        LOG.debug("Get User and  repository")
        val fullPath = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(repositoryMapping.gitRemoteUrlCoordinates.url)
            ?: throw IllegalArgumentException(
                "Invalid GitHub Repository URL - ${repositoryMapping.gitRemoteUrlCoordinates.url} is not a GitHub repository"
            )
        val repositoryCoordinates = RepositoryCoordinates(account.server, fullPath)
        LOG.debug("Create WorkflowDataLoader")
        val requestExecutor = GithubApiRequestExecutorManager.getInstance().getExecutor(account)
        val singleRunDataLoader = SingleRunDataLoader(requestExecutor)
        requestExecutor.addListener(singleRunDataLoader) {
            singleRunDataLoader.invalidateAllData()
        }
        val listLoader = WorkflowRunListLoader(
            ProgressManager.getInstance(),
            requestExecutor,
            repositoryCoordinates,
            settingsService = GhActionsSettingsService.getInstance(toolWindow.project)
        )

        return WorkflowRunSelectionContext(
            disposable,
            singleRunDataLoader,
            listLoader,
            repositoryMapping,
        )
    }

    @RequiresEdt
    fun acquireContext(
        disposable: Disposable,
        repositoryMapping: GHGitRepositoryMapping,
        account: GithubAccount,
        toolWindow: ToolWindow,
    ): CompletableFuture<WorkflowRunSelectionContext> {
        return repositories.getOrPut(repositoryMapping.ghRepositoryCoordinates) {
            val contextDisposable = Disposer.newDisposable("contextDisposable")
            Disposer.register(disposable, contextDisposable)

            LazyCancellableBackgroundProcessValue.create { indicator ->
                ProgressManager.getInstance().submitIOTask(indicator) {
                    try {
                        getContext(contextDisposable, account, repositoryMapping, toolWindow)
                    } catch (e: Exception) {
                        if (e !is ProcessCanceledException) LOG.warn("Error occurred while creating data context", e)
                        throw e
                    }
                }.successOnEdt { ctx ->
                    Disposer.register(contextDisposable, ctx)
                    ctx
                }
            }
        }.value
    }

    @RequiresEdt
    fun clearContext(repository: GHRepositoryCoordinates) {
        repositories.remove(repository)?.drop()
    }

    companion object {
        private val LOG = thisLogger()

        fun getInstance(project: Project) = project.service<WorkflowDataContextRepository>()
    }
}