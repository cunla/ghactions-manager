package com.dsoftware.ghmanager

import com.dsoftware.ghmanager.data.GhActionsService
import com.dsoftware.ghmanager.ui.GhActionsToolWindowFactory
import com.dsoftware.ghmanager.ui.settings.GhActionsSettingsService
import com.dsoftware.ghmanager.ui.settings.GithubActionsManagerSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.common.waitForAppLeakingThreads
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.registerServiceInstance
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.util.concurrency.annotations.RequiresEdt
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.yield
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.TimeUnit

@RunInEdt(writeIntent = true)
@TestApplication
abstract class GitHubActionsManagerBaseTest {

    private val host: GithubServerPath = GithubServerPath.from("github.com")
    protected val toolWindowFactory: GhActionsToolWindowFactory = GhActionsToolWindowFactory()
    private lateinit var testInfo: TestInfo


    @JvmField
    @RegisterExtension
    protected val projectRule: ProjectModelExtension = ProjectModelExtension()
    private lateinit var toolWindowManager: ToolWindowHeadlessManagerImpl
    protected lateinit var toolWindow: ToolWindow

    @BeforeEach
    open fun setUp(testInfo: TestInfo) {
        this.testInfo = testInfo
        toolWindowManager = ToolWindowManager.getInstance(projectRule.project) as ToolWindowHeadlessManagerImpl
        toolWindow = toolWindowManager.doRegisterToolWindow("test")
    }

    @AfterEach
    open fun tearDown() {
        toolWindowManager.unregisterToolWindow("test")
        executeSomeCoroutineTasksAndDispatchAllInvocationEvents(projectRule.project)
        TestApplicationManager.tearDownProjectAndApp(projectRule.project)
        waitForAppLeakingThreads(10, TimeUnit.SECONDS)
    }

    private fun waitForAppLeakingThreads(timeout: Long, timeUnit: TimeUnit) {
        runInEdtAndWait {
            val app = ApplicationManager.getApplication()
            if (app != null && !app.isDisposed) {
                waitForAppLeakingThreads(app, timeout, timeUnit)
            }
        }
    }

    fun mockGhActionsService(repoUrls: Set<String>, accountNames: Collection<String>) {
        val accounts = accountNames.map { GHAccountManager.createAccount(it, host) }
        val repos: Set<GHGitRepositoryMapping> = repoUrls.map {
            mockk<GHGitRepositoryMapping>().apply {
                every { remote.url } returns it
                every { repository.serverPath } returns host
                every { repositoryPath } returns it.replace("http://github.com/", "")
            }
        }.toSet()

        projectRule.project.registerServiceInstance(GhActionsService::class.java, object : GhActionsService {
            override val knownRepositoriesState: StateFlow<Set<GHGitRepositoryMapping>>
                get() = MutableStateFlow(repos)
            override val knownRepositories: Set<GHGitRepositoryMapping>
                get() = repos
            override val accountsState: StateFlow<Collection<GithubAccount>>
                get() = MutableStateFlow(accounts)
        })
    }

    fun mockSettingsService(settings: GithubActionsManagerSettings) {
        val settingsService = mockk<GhActionsSettingsService> {
            every { state } returns settings
        }
        projectRule.project.registerServiceInstance(GhActionsSettingsService::class.java, settingsService)
    }

}

@RequiresEdt
fun executeSomeCoroutineTasksAndDispatchAllInvocationEvents(project: Project) {
    repeat(3) {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        runWithModalProgressBlocking(project, "") {
            yield()
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
}