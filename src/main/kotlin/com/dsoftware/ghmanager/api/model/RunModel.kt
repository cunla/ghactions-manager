package com.dsoftware.ghmanager.api.model

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import kotlinx.datetime.Instant


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
    @JsonDeserialize(using = InstantDeserializer::class)
    val createdAt: Instant?,
    @JsonDeserialize(using = InstantDeserializer::class)
    val updatedAt: Instant?,
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
) : Comparable<WorkflowRun> {
    override fun equals(other: Any?): Boolean {
        if (other == null || other !is WorkflowRun) return false
        return this.id == other.id
    }

    /**
     * Compare workflows by their updated_at, or created_at (the newest first), or by id run_number both dates are null
     * @param other The other workflow to compare to
     */
    override fun compareTo(other: WorkflowRun): Int {
        return if (other.updatedAt != null && this.updatedAt != null) {
            other.updatedAt.compareTo(this.updatedAt)
        } else if (other.createdAt != null && this.createdAt != null) {
            other.createdAt.compareTo(this.createdAt)
        } else {
            other.runNumber.compareTo(this.runNumber)
        }
    }
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