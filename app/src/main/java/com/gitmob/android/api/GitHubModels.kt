package com.gitmob.android.api

import com.google.gson.annotations.SerializedName

data class GHUser(
    val login: String,
    val name: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("public_repos") val publicRepos: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
    val bio: String?,
)

data class GHRepo(
    val id: Long,
    val name: String,
    @SerializedName("full_name") val fullName: String,
    val description: String?,
    val private: Boolean,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("ssh_url") val sshUrl: String,
    @SerializedName("clone_url") val cloneUrl: String,
    @SerializedName("default_branch") val defaultBranch: String,
    @SerializedName("stargazers_count") val stars: Int = 0,
    @SerializedName("forks_count") val forks: Int = 0,
    @SerializedName("open_issues_count") val openIssues: Int = 0,
    @SerializedName("updated_at") val updatedAt: String?,
    @SerializedName("pushed_at") val pushedAt: String?,
    val language: String?,
    val owner: GHOwner,
    val fork: Boolean = false,
)

data class GHOwner(
    val login: String,
    @SerializedName("avatar_url") val avatarUrl: String?,
)

data class GHBranch(
    val name: String,
    val commit: GHBranchCommit,
    val protected: Boolean = false,
)

data class GHBranchCommit(val sha: String, val url: String)

data class GHCommit(
    val sha: String,
    val commit: GHCommitDetail,
    val author: GHOwner?,
    @SerializedName("html_url") val htmlUrl: String,
) {
    val shortSha get() = sha.take(7)
}

data class GHCommitDetail(
    val message: String,
    val author: GHCommitAuthor,
)

data class GHCommitAuthor(
    val name: String,
    val email: String,
    val date: String,
)

data class GHContent(
    val type: String,   // file | dir | symlink
    val name: String,
    val path: String,
    val sha: String,
    val size: Long = 0,
    val content: String? = null,   // base64, only when type==file
    val encoding: String? = null,
    @SerializedName("download_url") val downloadUrl: String?,
    @SerializedName("html_url") val htmlUrl: String?,
)

data class GHCreateRepoRequest(
    val name: String,
    val description: String? = null,
    val private: Boolean = false,
    @SerializedName("auto_init") val autoInit: Boolean = false,
)

data class GHCreateFileRequest(
    val message: String,
    val content: String,  // base64
    val branch: String? = null,
    val sha: String? = null,  // required for update
)

data class GHCreateFileResponse(
    val content: GHContent,
    val commit: GHCommit,
)

data class GHDeleteFileRequest(
    val message: String,
    val sha: String,
    val branch: String? = null,
)

data class GHCreateBranchRequest(
    val ref: String,
    val sha: String,
)

data class GHRef(
    val ref: String,
    val url: String,
    @SerializedName("object") val obj: GHRefObject,
)

data class GHRefObject(val sha: String, val type: String, val url: String)

data class GHTopic(val names: List<String>)

data class GHPullRequest(
    val number: Int,
    val title: String,
    val state: String,
    val body: String?,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("created_at") val createdAt: String,
    val user: GHOwner,
    val head: GHPRBranch,
    val base: GHPRBranch,
)

data class GHPRBranch(val label: String, val ref: String, val sha: String)

data class GHIssue(
    val number: Int,
    val title: String,
    val state: String,
    val body: String?,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("created_at") val createdAt: String,
    val user: GHOwner,
    @SerializedName("pull_request") val pullRequest: Any? = null,
) {
    val isPR get() = pullRequest != null
}

data class GHRelease(
    val id: Long,
    @SerializedName("tag_name") val tagName: String,
    val name: String?,
    val body: String?,
    val draft: Boolean,
    val prerelease: Boolean,
    @SerializedName("published_at") val publishedAt: String?,
    @SerializedName("html_url") val htmlUrl: String,
    val assets: List<GHAsset>,
)

data class GHAsset(
    val name: String,
    val size: Long,
    @SerializedName("browser_download_url") val downloadUrl: String,
)

data class GHSSHKey(
    val id: Long,
    val title: String,
    val key: String,
    @SerializedName("created_at") val createdAt: String,
    val verified: Boolean,
)

data class GHCreateSSHKeyRequest(val title: String, val key: String)

data class GHSearchResult<T>(
    @SerializedName("total_count") val totalCount: Int,
    val items: List<T>,
)

// ── Org ──
data class GHOrg(
    val login: String,
    @SerializedName("avatar_url") val avatarUrl: String?,
    val description: String?,
)

// ── Repo update ──
data class GHUpdateRepoRequest(
    val name: String? = null,
    val description: String? = null,
    val homepage: String? = null,
    @SerializedName("private") val private: Boolean? = null,
    @SerializedName("default_branch") val defaultBranch: String? = null,
)

// ── Topics ──
data class GHTopics(
    val names: List<String>,
)

// ── Branch rename ──
data class GHRenameBranchRequest(
    @SerializedName("new_name") val newName: String,
)

// ── Commit detail (files) ──
data class GHCommitFull(
    val sha: String,
    val commit: GHCommitDetail,
    val author: GHOwner?,
    @SerializedName("html_url") val htmlUrl: String,
    val stats: GHCommitStats?,
    val files: List<GHCommitFile>?,
) {
    val shortSha get() = sha.take(7)
}

data class GHCommitStats(
    val additions: Int,
    val deletions: Int,
    val total: Int,
)

data class GHCommitFile(
    val filename: String,
    val status: String,    // added | removed | modified | renamed
    val additions: Int,
    val deletions: Int,
    val changes: Int,
    val patch: String?,
)

// ── Revert / Merge request ──
data class GHMergeRequest(
    val base: String,
    val head: String,
    @SerializedName("commit_message") val commitMessage: String,
)
