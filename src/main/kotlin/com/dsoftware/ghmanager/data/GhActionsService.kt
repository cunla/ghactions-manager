package com.dsoftware.ghmanager.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.EventDispatcher
import com.intellij.util.childScope
import git4idea.remote.hosting.knownRepositories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager
import java.util.EventListener

interface GhActionsService : Disposable {
    val gitHubAccounts: MutableSet<GithubAccount>
    val knownRepositoriesState: StateFlow<Set<GHGitRepositoryMapping>>
    val knownRepositories: Set<GHGitRepositoryMapping>
    val accountsState: StateFlow<Collection<GithubAccount>>
    fun guessAccountForRepository(repo: GHGitRepositoryMapping): GithubAccount? {
        return gitHubAccounts.firstOrNull { it.server.equals(repo.repository.serverPath, true) }
    }

    fun addListener(listener: GhActionsServiceListener)
    interface GhActionsServiceListener : EventListener {
        fun reposChanged(repos: Set<GHGitRepositoryMapping>)
    }
}

open class GhActionsServiceImpl(project: Project, parentCs: CoroutineScope) : GhActionsService, Disposable {
    private val repositoriesManager = project.service<GHHostedRepositoriesManager>()
    private val accountManager = service<GHAccountManager>()
    private val coroutineScope = parentCs.childScope()
    @VisibleForTesting
    internal val ghActionsServiceEventDispatcher =
        EventDispatcher.create(GhActionsService.GhActionsServiceListener::class.java)
    override val gitHubAccounts: MutableSet<GithubAccount> = mutableSetOf()

    init {
        coroutineScope.launch {
            accountsState.collect {
                gitHubAccounts.clear()
                gitHubAccounts.addAll(it)
                ghActionsServiceEventDispatcher.multicaster.reposChanged(knownRepositories)
            }
        }
        coroutineScope.launch {
            knownRepositoriesState.collect {
                ghActionsServiceEventDispatcher.multicaster.reposChanged(it)
            }
        }
    }

    override val knownRepositoriesState: StateFlow<Set<GHGitRepositoryMapping>>
        get() = repositoriesManager.knownRepositoriesState
    override val knownRepositories: Set<GHGitRepositoryMapping>
        get() = repositoriesManager.knownRepositories
    override val accountsState: StateFlow<Collection<GithubAccount>>
        get() = accountManager.accountsState

    override fun addListener(listener: GhActionsService.GhActionsServiceListener) =
        ghActionsServiceEventDispatcher.addListener(listener, this)

    override fun dispose() {
        coroutineScope.cancel()
    }

    companion object {
        private val LOG = logger<GhActionsService>()
    }
}