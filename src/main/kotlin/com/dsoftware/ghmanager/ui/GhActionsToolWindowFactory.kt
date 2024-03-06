package com.dsoftware.ghmanager.ui

import ai.grazie.utils.applyIf
import com.dsoftware.ghmanager.data.GhActionsService
import com.dsoftware.ghmanager.data.WorkflowDataContextService
import com.dsoftware.ghmanager.i18n.MessagesBundle.message
import com.dsoftware.ghmanager.ui.settings.GhActionsSettingsService
import com.dsoftware.ghmanager.ui.settings.GithubActionsManagerSettings
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import java.awt.BorderLayout
import javax.swing.JPanel


class GhActionsToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun init(toolWindow: ToolWindow) {
        val ghActionsService = toolWindow.project.service<GhActionsService>()
        ghActionsService.addListener(object : GhActionsService.GhActionsServiceListener {
            override fun reposChanged(repos: Set<GHGitRepositoryMapping>) {
                updateRepos(toolWindow, repos)
            }
        })
    }

    @VisibleForTesting
    internal fun updateRepos(toolWindow: ToolWindow, repoSet: Set<GHGitRepositoryMapping>) {
        val settingsService: GhActionsSettingsService = toolWindow.project.service<GhActionsSettingsService>()
        repoSet.forEach { repo ->
            settingsService.state.customRepos.putIfAbsent(
                repo.remote.url,
                GithubActionsManagerSettings.RepoSettings()
            )
        }
        createToolWindowContent(toolWindow.project, toolWindow)
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        return true
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val ghActionsService = toolWindow.project.service<GhActionsService>()
        val settingsService: GhActionsSettingsService = toolWindow.project.service<GhActionsSettingsService>()
        val projectRepos = ghActionsService.knownRepositories


        ApplicationManager.getApplication().invokeLater {
            toolWindow.contentManager.removeAllContents(true)
            val countRepos = projectRepos.count {
                settingsService.state.customRepos[it.remote.url]?.included ?: false
            }
            if ((ghActionsService.gitHubAccounts.isEmpty() && settingsService.state.useGitHubSettings)
                || (!settingsService.state.useGitHubSettings && settingsService.state.apiToken == "")
            ) {
                addEmptyTextTabToWindow(
                    toolWindow,
                    message("factory.empty-panel.no-account-configured"),
                    showGithubSettings = true,
                    showGhmanagerSettings = true,
                )
            } else if (projectRepos.isEmpty()) {
                addEmptyTextTabToWindow(
                    toolWindow,
                    message("factory.empty-panel.no-repos-in-project"),
                    showGithubSettings = false,
                    showGhmanagerSettings = false,
                )
            } else if (settingsService.state.useCustomRepos && countRepos == 0) {
                addEmptyTextTabToWindow(
                    toolWindow,
                    message("factory.empty-panel.no-repos-configured"),
                    showGithubSettings = false,
                    showGhmanagerSettings = true,
                )
            } else {
                createRepoWorkflowsPanels(toolWindow, projectRepos)
            }
        }
    }


    companion object {
        private val LOG = logger<GhActionsToolWindowFactory>()
        private fun addEmptyTextTabToWindow(
            toolWindow: ToolWindow,
            text: String,
            showGithubSettings: Boolean,
            showGhmanagerSettings: Boolean,
        ) = with(toolWindow.contentManager) {
            LOG.debug("Adding empty text tab to window: $text with githubSettings=$showGithubSettings, ghmanagerSettings=$showGhmanagerSettings")
            val disposable = Disposer.newDisposable("GitHubWorkflow tab disposable")
            Disposer.register(toolWindow.disposable, disposable)
            val emptyTextPanel = JBPanelWithEmptyText()
            emptyTextPanel.apply {
                emptyText.appendText(text)
            }.applyIf(showGithubSettings) {
                emptyTextPanel.emptyText.appendLine(
                    message("factory.go.to.github-settings"),
                    SimpleTextAttributes.LINK_ATTRIBUTES,
                    ActionUtil.createActionListener(
                        "ShowGithubSettings",
                        emptyTextPanel,
                        ActionPlaces.UNKNOWN
                    )
                )
            }.applyIf(showGhmanagerSettings) {
                emptyTextPanel.emptyText.appendLine(
                    message("factory.go.to.ghmanager-settings"),
                    SimpleTextAttributes.LINK_ATTRIBUTES,
                    ActionUtil.createActionListener(
                        "Github.Actions.Manager.Settings.Open",
                        emptyTextPanel,
                        ActionPlaces.UNKNOWN
                    )
                )
            }.apply {
                addContent(
                    factory.createContent(emptyTextPanel, message("factory.default-tab-title"), false).apply {
                        isCloseable = false
                        setDisposer(disposable)
                    }
                )
            }
        }

        private fun createRepoWorkflowsPanels(toolWindow: ToolWindow, knownRepositories: Set<GHGitRepositoryMapping>) {
            val ghActionsService = toolWindow.project.service<GhActionsService>()
            val settingsService: GhActionsSettingsService = toolWindow.project.service<GhActionsSettingsService>()
            val actionManager = ActionManager.getInstance()
            val parentDisposable = Disposer.newDisposable("GitHubWorkflow tab disposable")
            Disposer.register(toolWindow.disposable, parentDisposable)
            toolWindow.setAdditionalGearActions(DefaultActionGroup(actionManager.getAction("Github.Actions.Manager.Settings.Open")))
            val dataContextRepository = WorkflowDataContextService.getInstance(toolWindow.project)
            knownRepositories.filter {
                !settingsService.state.useCustomRepos
                    || (settingsService.state.customRepos[it.remote.url]?.included ?: false)
            }.forEach { repo ->
                val ghAccount = ghActionsService.guessAccountForRepository(repo)
                if (ghAccount != null) {
                    LOG.info("adding panel for repo: ${repo.repositoryPath}, ${ghAccount.name}")
                    val repoSettings = settingsService.state.customRepos[repo.remote.url]!!
                    val tab = toolWindow.contentManager.factory.createContent(
                        JPanel(null), repo.repositoryPath, false
                    ).apply {
                        isCloseable = false
                        val disposable = Disposer.newDisposable("gha-manager ${repo.repositoryPath} tab disposable")
                        Disposer.register(parentDisposable, disposable)
                        setDisposer(disposable)
                        displayName = repoSettings.customName.ifEmpty { repo.repositoryPath }
                    }
                    val controller = WorkflowToolWindowTabController(
                        repo, ghAccount, dataContextRepository, tab.disposer!!, toolWindow,
                    )
                    tab.component.apply {
                        layout = BorderLayout()
                        background = UIUtil.getListBackground()
                        removeAll()
                        add(controller.panel, BorderLayout.CENTER)
                        revalidate()
                        repaint()
                    }
                    tab.putUserData(WorkflowToolWindowTabController.KEY, controller)
                    toolWindow.contentManager.addContent(tab)
                } else {
                    addEmptyTextTabToWindow(
                        toolWindow,
                        message("factory.empty-panel.no-account-for-repo", repo.repository),
                        showGithubSettings = true,
                        showGhmanagerSettings = true
                    )
                }
            }
            toolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
                override fun selectionChanged(event: ContentManagerEvent) {
                    val content = event.content
                    val controller = content.getUserData(WorkflowToolWindowTabController.KEY)
                    LOG.debug("Got selectionChanged event: ${content.displayName}: controller=${controller != null}, isSelected=${content.isSelected}")
                    controller?.apply {
                        this.loadingModel.result?.runsListLoader?.refreshRuns = content.isSelected
                    }
                }
            })
            ToolbarUtil.executeTaskAtCustomFrequency(toolWindow.project, 5) {
                toolWindow.contentManager.contents.forEach {
                    val controller = it.getUserData(WorkflowToolWindowTabController.KEY)
                    controller?.apply {
                        this.loadingModel.result?.runsListLoader?.refreshRuns = it.isSelected
                    }
                }
            }
        }
    }
}