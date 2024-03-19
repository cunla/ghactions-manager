package com.dsoftware.ghmanager.actions

import com.dsoftware.ghmanager.api.model.PullRequest
import com.intellij.collaboration.async.CompletableFutureUtil
import com.intellij.collaboration.async.CompletableFutureUtil.composeOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.github.GithubIcons
import org.jetbrains.plugins.github.pullrequest.GHPRToolWindowController
import org.jetbrains.plugins.github.pullrequest.data.SimpleGHPRIdentifier
import java.util.concurrent.CompletableFuture

/**
 * Open a pull request from the current job.
 *
 * Based on the discussions of https://youtrack.jetbrains.com/issue/IDEA-318999/API-to-open-a-pull-request-from-plugin
 * this action duplicates the necessary logic happening
 * in [org.jetbrains.plugins.github.pullrequest.GHPRToolWindowController]
 * and in [org.jetbrains.plugins.github.pullrequest.action.GHPRCreatePullRequestAction]
 */
class OpenPullRequestInToolwindowAction : DumbAwareAction(
    "Open Pull-Request in Toolwindow",
    null,
    GithubIcons.PullRequestsToolWindow
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val pr = pullRequest(e) ?: return

        project.service<GHPRToolWindowController>().activate().composeOnEdt {
            it.repositoryContentController
        }.successOnEdt {
            it.viewPullRequest(SimpleGHPRIdentifier(pr.id.toString(), pr.number.toLong()))
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = pullRequest(e) != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    /**
     * Pull request
     *
     */
    private fun pullRequest(e: AnActionEvent): PullRequest? {
        val run = e.getData(ActionKeys.SELECTED_WORKFLOW_RUN) ?: return null
        return run.pull_requests?.firstOrNull()
    }

    /**
     * This is a copy of [CompletableFutureUtil.composeOnEdt] which is not available before
     * 231.7665.28, while the plugin is supposed to run on 231.4840.387+
     *
     * In fact [CompletableFutureUtil.composeOnEdt] raises an error about this call:
     *
     * > * 'composeOnEdt(java.util.concurrent.CompletableFuture<T>, com.intellij.openapi.application.ModalityState, kotlin.jvm.functions.Function1<? super T,? extends java.util.concurrent.CompletableFuture<R>>)'
     * >   is available only since 231.7665.28 but the module is targeted for 231.4840.387+.
     * >   It may lead to compatibility problems with IDEs prior to 231.7665.28. Note that
     * >   this method might have had a different full signature in the previous IDEs.
     */
    fun <T, R> CompletableFuture<T>.composeOnEdt(modalityState: ModalityState? = null,
                                                 handler: (T) -> CompletableFuture<R>
    ): CompletableFuture<R> =
        thenComposeAsync({ handler(it) }, CompletableFutureUtil.getEDTExecutor(modalityState))
}