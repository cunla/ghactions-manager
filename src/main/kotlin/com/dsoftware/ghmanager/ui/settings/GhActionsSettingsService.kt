package com.dsoftware.ghmanager.ui.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

data class GithubActionsManagerSettings(
    var useCustomRepos: Boolean = false,
    var customRepos: MutableMap<String, RepoSettings> = mutableMapOf(),
    var jobListAboveLogs: Boolean = true,
    var frequency: Int = 30,
    var pageSize: Int = 30,
    var useGitHubSettings: Boolean = true,
    var apiToken: String = "",
) {
    data class RepoSettings(
        var included: Boolean = true,
        var customName: String = ""
    )
}

@Service(Service.Level.PROJECT)
@State(
    name = "GhActionsManagerSettings",
    storages = [
        Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)
    ],
    reportStatistic = false,
)
class GhActionsSettingsService : PersistentStateComponent<GithubActionsManagerSettings>, Disposable {
    private var state = GithubActionsManagerSettings()

    override fun getState(): GithubActionsManagerSettings {
        return state
    }

    override fun loadState(state: GithubActionsManagerSettings) {
        this.state = state
    }

    override fun dispose() {}
}