package com.dsoftware.ghmanager.api.model

import com.fasterxml.jackson.annotation.JsonFormat
import kotlinx.serialization.Serializable
import java.util.Date

interface ListItem {
    fun isHeader(): Boolean;
}

data class WorkflowTypes(
    val totalCount: Int,
    val workflows: List<WorkflowType> = emptyList()
)

@Serializable
data class WorkflowType(
    val id: Long,
    val name: String,
    val path: String,
    val state: String,
) : ListItem {
    override fun isHeader(): Boolean = true
}

data class PullRequest(
    val id: Int,
    val number: Int,
    val url: String,
)

data class WorkflowRuns(
    val totalCount: Int,
    val workflowRuns: List<WorkflowRun> = emptyList()
)

data class WorkflowRun(
    val id: Long,
    val path: String?,
    val nodeId: String,
    val headBranch: String?,
    val headSha: String?,
    val runNumber: Int,
    val event: String,
    val status: String,
    val conclusion: String?,
    val url: String,
    val htmlUrl: String,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    val createdAt: Date?,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    val updatedAt: Date?,
    val jobsUrl: String,
    val logsUrl: String,
    val checkSuiteUrl: String,
    val artifactsUrl: String,
    val cancelUrl: String,
    val rerunUrl: String,
    val workflowId: Long,
    val workflowUrl: String,
    val name: String,
    val headCommit: GitHubHeadCommit,
    val repository: GitHubRepository,
    val pullRequests: List<PullRequest>? = emptyList(),
) : Comparable<WorkflowRun>, ListItem {

    /**
     * Compare workflows by their updated_at, or created_at (the newest first), or by id run_number both dates are null
     * @param other The other workflow to compare to
     */
    override fun compareTo(other: WorkflowRun): Int {
        return other.updatedAt?.compareTo(this.updatedAt)
            ?: other.createdAt?.compareTo(this.createdAt)
            ?: other.runNumber.compareTo(this.runNumber)
    }

    override fun isHeader(): Boolean = false
}

data class GitHubRepository(
    val id: Int,
    val pullsUrl: String,
    val htmlUrl: String,
)

data class GitHubHeadCommit(
    val id: String,
    val message: String,
    val author: GitHubAuthor
)

data class GitHubAuthor(
    val name: String,
    val email: String
)