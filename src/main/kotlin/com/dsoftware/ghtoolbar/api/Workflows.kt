package com.dsoftware.ghtoolbar.api

import WorkflowRunJobs
import com.dsoftware.ghtoolbar.api.model.GitHubWorkflow
import com.dsoftware.ghtoolbar.api.model.GitHubWorkflowRuns
import com.dsoftware.ghtoolbar.workflow.RepositoryCoordinates
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.plugins.github.api.GithubApiRequest
import org.jetbrains.plugins.github.api.GithubApiRequest.Get
import org.jetbrains.plugins.github.api.GithubApiRequest.Get.Companion.json
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.request.GithubRequestPagination
import org.jetbrains.plugins.github.api.util.GithubApiSearchQueryBuilder
import org.jetbrains.plugins.github.api.util.GithubApiUrlQueryBuilder


object Workflows : GithubApiRequests.Entity("/repos") {
    private val LOG = logger<Workflows>()
    fun getWorkflowByUrl(url: String) = Get.Json(url, GitHubWorkflow::class.java, null)
        .withOperationName("Get Workflow Description By URL")

    fun getDownloadUrlForWorkflowLog(url: String) = GetRunLogRequest(url)
        .withOperationName("Download Workflow log")

    fun postRerunWorkflow(url: String) = GithubApiRequest.Post.Json(url, Object(), Object::class.java, null)
        .withOperationName("Rerun workflow")

    fun getWorkflowRuns(
        coordinates: RepositoryCoordinates,
        event: String? = null,
        status: String? = null,
        branch: String? = null,
        actor: String? = null,
        pagination: GithubRequestPagination? = null
    ): GithubApiRequest<GitHubWorkflowRuns> {
        val url = GithubApiRequests.getUrl(coordinates.serverPath,
            urlSuffix,
            "/${coordinates.repositoryPath}",
            "/actions",
            "/runs",
            GithubApiUrlQueryBuilder.urlQuery {
                param("q", GithubApiSearchQueryBuilder.searchQuery {
                    qualifier("event", event)
                    qualifier("status", status)
                    qualifier("branch", branch)
                    qualifier("actor", actor)
                })
                param(pagination)
            })
        LOG.debug("Workflows.getWorkflowRuns() url=${url}")
        return get(url)
    }

    fun getWorkflowRunJobs(url: String) = json<WorkflowRunJobs>(url)
        .withOperationName("Get workflow-run jobs")

    fun get(url: String) = json<GitHubWorkflowRuns>(url)
        .withOperationName("search workflow runs")


}
