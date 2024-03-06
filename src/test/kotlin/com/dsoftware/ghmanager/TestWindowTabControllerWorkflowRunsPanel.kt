package com.dsoftware.ghmanager

import com.dsoftware.ghmanager.api.model.WorkflowRun
import com.dsoftware.ghmanager.api.model.WorkflowRuns
import com.dsoftware.ghmanager.api.model.WorkflowType
import com.dsoftware.ghmanager.api.model.WorkflowTypes
import com.dsoftware.ghmanager.data.WorkflowDataContextService
import com.dsoftware.ghmanager.data.WorkflowRunSelectionContext
import com.dsoftware.ghmanager.ui.panels.wfruns.WorkflowRunsListPanel
import com.intellij.openapi.components.service
import com.intellij.ui.OnePixelSplitter
import io.mockk.MockKMatcherScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.verify
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.github.api.GithubApiRequest
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GithubBranch
import org.jetbrains.plugins.github.api.data.GithubResponsePage
import org.jetbrains.plugins.github.api.data.GithubUserWithPermissions
import org.jetbrains.plugins.github.util.GHCompatibilityUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import javax.swing.JPanel

class TestWindowTabControllerWorkflowRunsPanel : GitHubActionsManagerBaseTest() {
    private lateinit var executorMock: GithubApiRequestExecutor

    init {
        mockkStatic(GHCompatibilityUtil::class)
        every { GHCompatibilityUtil.getOrRequestToken(any(), any()) } returns "token"
    }

    @BeforeEach
    override fun setUp(testInfo: TestInfo) {
        super.setUp(testInfo)
        executorMock = mockk<GithubApiRequestExecutor>(relaxed = true) {}
        mockkObject(GithubApiRequestExecutor.Factory)
        every { GithubApiRequestExecutor.Factory.getInstance() } returns mockk<GithubApiRequestExecutor.Factory> {
            every { create(token = any()) } returns executorMock
        }
        executeSomeCoroutineTasksAndDispatchAllInvocationEvents(projectRule.project)
    }

    @Test
    fun `test repo with different workflow-runs`() = runBlocking {
        val workflowRunsList = listOf(
            createWorkflowRun(id = 1, status = "in_progress"),
            createWorkflowRun(id = 2, status = "completed"),
            createWorkflowRun(id = 2, status = "queued"),
        )
        mockGithubApiRequestExecutor(workflowRunsList)
        mockGhActionsService(setOf("http://github.com/owner/repo"), setOf("account1"))

        // act
        executeSomeCoroutineTasksAndDispatchAllInvocationEvents(projectRule.project)

        // assert
        val workflowRunsListPanel = assertTabsAndPanels()
        TestCase.assertEquals(workflowRunsList.size, workflowRunsListPanel.runListComponent.model.size)
    }

    //    // todo: fix this test
    @Test
    fun `test repo without workflow-runs`() = runBlocking {
        mockGithubApiRequestExecutor(emptyList())
        mockGhActionsService(setOf("http://github.com/owner/repo"), setOf("account1"))

        // act
        executeSomeCoroutineTasksAndDispatchAllInvocationEvents(projectRule.project)

        // assert
        val workflowRunsListPanel = assertTabsAndPanels()
        TestCase.assertEquals(0, workflowRunsListPanel.runListComponent.model.size)
    }


    fun mockGithubApiRequestExecutor(
        workflowRunsList: Collection<WorkflowRun>,
        collaborators: Collection<String> = emptyList(),
        branches: Collection<String> = emptyList(),
        workflowTypes: Collection<WorkflowType> = emptyList(),
    ) {
        val collaboratorsResponse = GithubResponsePage(collaborators.map {
            val user = mockk<GithubUserWithPermissions> {
                every { login }.returns(it)
            }
            user
        })
        val branchesResponse = GithubResponsePage(branches.map {
            val branch = mockk<GithubBranch> {
                every { name }.returns(it)
            }
            branch
        })
        val workflowTypesResponse = WorkflowTypes(workflowTypes.size, workflowTypes.toList())
        executorMock.apply {
            every {// workflow runs
                execute(any(), matchApiRequestUrl<WorkflowRuns>("/actions/runs")).hint(WorkflowRuns::class)
            } returns WorkflowRuns(workflowRunsList.size, workflowRunsList.toList())
            every {// collaborators
                execute(any(), matchApiRequestUrl<GithubResponsePage<GithubUserWithPermissions>>("/collaborators"))
            } returns collaboratorsResponse
            every { // branches
                execute(any(), matchApiRequestUrl<GithubResponsePage<GithubBranch>>("/branches"))
            } returns branchesResponse
            every { // workflow types
                execute(any(), matchApiRequestUrl<WorkflowTypes>("/actions/workflows"))
            } returns workflowTypesResponse
        }

    }

    fun assertTabsAndPanels(): WorkflowRunsListPanel {
        TestCase.assertEquals(1, toolWindow.contentManager.contentCount)
        val content = toolWindow.contentManager.contents[0]
        TestCase.assertEquals("owner/repo", content.displayName)
        TestCase.assertTrue(content.component is JPanel)
        val tabWrapPanel = content.component as JPanel
        TestCase.assertEquals(1, tabWrapPanel.componentCount)
        val workflowDataContextService = projectRule.project.service<WorkflowDataContextService>()
        TestCase.assertEquals(1, workflowDataContextService.repositories.size)
        verify(atLeast = 1) {
            executorMock.execute(any(), matchApiRequestUrl<WorkflowRuns>("/actions/runs")).hint(WorkflowRuns::class)
            executorMock.execute(
                any(), matchApiRequestUrl<GithubResponsePage<GithubUserWithPermissions>>("/collaborators")
            )
            executorMock.execute(any(), matchApiRequestUrl<GithubResponsePage<GithubBranch>>("/branches"))
            executorMock.execute(any(), matchApiRequestUrl<WorkflowTypes>("/actions/workflows"))
        }
        TestCase.assertEquals(1, (tabWrapPanel.components[0] as JPanel).componentCount)
        TestCase.assertTrue((tabWrapPanel.components[0] as JPanel).components[0] is OnePixelSplitter)
        val splitterComponent = ((tabWrapPanel.components[0] as JPanel).components[0] as OnePixelSplitter)
        TestCase.assertEquals(3, splitterComponent.componentCount)
        TestCase.assertTrue(splitterComponent.firstComponent is WorkflowRunsListPanel)
        TestCase.assertTrue(splitterComponent.secondComponent is OnePixelSplitter)
        return splitterComponent.firstComponent as WorkflowRunsListPanel
    }

    private fun <T> MockKMatcherScope.matchApiRequestUrl(url: String) =
        match<GithubApiRequest<T>> { it.url.contains(url) }

}
