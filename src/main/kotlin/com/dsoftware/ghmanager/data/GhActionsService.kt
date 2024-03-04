package com.dsoftware.ghmanager.data

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import git4idea.remote.hosting.knownRepositories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager

interface GhActionsService {
    val coroutineScope: CoroutineScope
    val knownRepositoriesState: StateFlow<Set<GHGitRepositoryMapping>>
    val knownRepositories: Set<GHGitRepositoryMapping>
    val accountsState: StateFlow<Collection<GithubAccount>>
}

open class GhActionsServiceImpl(project: Project) : GhActionsService {
    override val coroutineScope = CoroutineScope(SupervisorJob())
    private val repositoriesManager = project.service<GHHostedRepositoriesManager>()
    private val accountManager = service<GHAccountManager>()

    override val knownRepositoriesState: StateFlow<Set<GHGitRepositoryMapping>>
        get() = repositoriesManager.knownRepositoriesState
    override val knownRepositories: Set<GHGitRepositoryMapping>
        get() = repositoriesManager.knownRepositories
    override val accountsState: StateFlow<Collection<GithubAccount>>
        get() = accountManager.accountsState
}