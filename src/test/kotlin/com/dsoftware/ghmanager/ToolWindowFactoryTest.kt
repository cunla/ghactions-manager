package com.dsoftware.ghmanager

import com.dsoftware.ghmanager.i18n.MessagesBundle.message
import com.dsoftware.ghmanager.ui.settings.GithubActionsManagerSettings
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.components.JBPanelWithEmptyText
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.verify
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.util.GHCompatibilityUtil
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import javax.swing.JPanel


class ToolWindowFactoryTest : GitHubActionsManagerBaseTest() {
    private lateinit var requestExecutorFactoryMock: GithubApiRequestExecutor.Factory

    @BeforeEach
    override fun setUp(testInfo: TestInfo) {
        super.setUp(testInfo)
        requestExecutorFactoryMock = mockk<GithubApiRequestExecutor.Factory> {
            every { create(token = any()) } throws Exception("No executor")
        }
        mockkObject(GithubApiRequestExecutor.Factory)
        every { GithubApiRequestExecutor.Factory.getInstance() } returns requestExecutorFactoryMock
    }

    @Test
    fun `test Panel No GitHub Account`() {
        mockGhActionsService(emptySet(), emptySet())
        toolWindowFactory.init(toolWindow)
        executeSomeCoroutineTasksAndDispatchAllInvocationEvents(projectRule.project)

        Assertions.assertEquals(1, toolWindow.contentManager.contentCount)
        Assertions.assertEquals("Workflows", toolWindow.contentManager.contents[0].displayName)
        val component = toolWindow.contentManager.contents[0].component
        Assertions.assertTrue(component is JBPanelWithEmptyText)
        val panel = component as JBPanelWithEmptyText

        Assertions.assertEquals(message("factory.empty-panel.no-account-configured"), panel.emptyText.text)
        val subComponents = panel.emptyText.wrappedFragmentsIterable.map { it as SimpleColoredComponent }.toList()
        Assertions.assertEquals(
            message("factory.empty-panel.no-account-configured"),
            subComponents[0].getCharSequence(true)
        )
        Assertions.assertEquals(message("factory.go.to.github-settings"), subComponents[1].getCharSequence(true))
        Assertions.assertEquals(message("factory.go.to.ghmanager-settings"), subComponents[2].getCharSequence(true))
        verify {
            requestExecutorFactoryMock.create(token = any()) wasNot Called
        }
    }

    @Test
    fun `test GitHub Account exists but no repositories configured`() {
        mockGhActionsService(emptySet(), setOf("account1"))

        executeSomeCoroutineTasksAndDispatchAllInvocationEvents(projectRule.project)

        //Assert
        Assertions.assertEquals(1, toolWindow.contentManager.contentCount)
        val component = toolWindow.contentManager.contents[0].component
        Assertions.assertEquals(
            message("factory.default-tab-title"),
            toolWindow.contentManager.contents[0].displayName
        )
        Assertions.assertTrue(component is JBPanelWithEmptyText)
        val panel = component as JBPanelWithEmptyText
        Assertions.assertEquals(message("factory.empty-panel.no-repos-in-project"), panel.emptyText.text)
        verify {
            requestExecutorFactoryMock.create(token = any()) wasNot Called
        }

    }

    @Test
    fun `test GitHub Account and repos configured shows repo-panel`() {

        mockkStatic(GHCompatibilityUtil::class)
        every { GHCompatibilityUtil.getOrRequestToken(any(), any()) } returns "token"
        mockSettingsService(GithubActionsManagerSettings(useCustomRepos = false))
        mockGhActionsService(setOf("http://github.com/owner/repo"), setOf("account1"))

        executeSomeCoroutineTasksAndDispatchAllInvocationEvents(projectRule.project)
        //Assert
        Assertions.assertEquals(1, toolWindow.contentManager.contentCount)
        val content = toolWindow.contentManager.contents[0]
        Assertions.assertEquals("owner/repo", content.displayName)
        Assertions.assertTrue(content.component is JPanel)
        val panel = content.component as JPanel
        Assertions.assertEquals(1, panel.componentCount)
        verify {
            requestExecutorFactoryMock.create(token = any())
        }


    }

    @Test
    fun `test when using settings custom repos + zero repos`() {
        mockkStatic(GHCompatibilityUtil::class)
        every { GHCompatibilityUtil.getOrRequestToken(any(), any()) } returns "token"
        mockSettingsService(
            GithubActionsManagerSettings(
                useCustomRepos = true,
                customRepos = mutableMapOf(
                    "http://github.com/owner/repo" to
                        GithubActionsManagerSettings.RepoSettings(false, "customName")
                )
            )
        )
        mockGhActionsService(setOf("http://github.com/owner/repo"), setOf("account1"))
        executeSomeCoroutineTasksAndDispatchAllInvocationEvents(projectRule.project)
        // Assert
        val content = toolWindow.contentManager.contents[0]
        Assertions.assertEquals(message("factory.default-tab-title"), content.displayName)
        Assertions.assertEquals(1, toolWindow.contentManager.contentCount)
        val component = toolWindow.contentManager.contents[0].component
        Assertions.assertTrue(component is JBPanelWithEmptyText)
        val panel = component as JBPanelWithEmptyText

        Assertions.assertEquals(message("factory.empty-panel.no-repos-configured"), panel.emptyText.text)
        val subComponents = panel.emptyText.wrappedFragmentsIterable.map { it as SimpleColoredComponent }.toList()
        Assertions.assertEquals(
            message("factory.empty-panel.no-repos-configured"),
            subComponents[0].getCharSequence(true)
        )
        Assertions.assertEquals(message("factory.go.to.ghmanager-settings"), subComponents[1].getCharSequence(true))
        verify {
            requestExecutorFactoryMock.create(token = any()) wasNot Called
        }

    }
}