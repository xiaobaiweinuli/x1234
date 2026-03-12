package com.gitmob.android.api

import retrofit2.Response
import retrofit2.http.*

interface GitHubApi {

    // ── User ──
    @GET("user")
    suspend fun getCurrentUser(): GHUser

    @GET("users/{login}")
    suspend fun getUser(@Path("login") login: String): GHUser

    // ── Repos ──
    @GET("user/repos")
    suspend fun getMyRepos(
        @Query("type") type: String = "owner",
        @Query("sort") sort: String = "updated",
        @Query("per_page") perPage: Int = 50,
        @Query("page") page: Int = 1,
    ): List<GHRepo>

    @GET("repos/{owner}/{repo}")
    suspend fun getRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): GHRepo

    @POST("user/repos")
    suspend fun createRepo(@Body body: GHCreateRepoRequest): GHRepo

    @DELETE("repos/{owner}/{repo}")
    suspend fun deleteRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Response<Unit>

    // ── Contents ──
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String = "",
        @Query("ref") ref: String? = null,
    ): List<GHContent>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Query("ref") ref: String? = null,
    ): GHContent

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun createOrUpdateFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Body body: GHCreateFileRequest,
    ): GHCreateFileResponse

    @HTTP(method = "DELETE", path = "repos/{owner}/{repo}/contents/{path}", hasBody = true)
    suspend fun deleteFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Body body: GHDeleteFileRequest,
    ): Response<Unit>

    // ── Commits ──
    @GET("repos/{owner}/{repo}/commits")
    suspend fun getCommits(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("sha") sha: String? = null,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
    ): List<GHCommit>

    @GET("repos/{owner}/{repo}/commits/{ref}")
    suspend fun getCommit(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref") ref: String,
    ): GHCommit

    // ── Branches ──
    @GET("repos/{owner}/{repo}/branches")
    suspend fun getBranches(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 100,
    ): List<GHBranch>

    @POST("repos/{owner}/{repo}/git/refs")
    suspend fun createBranch(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: GHCreateBranchRequest,
    ): GHRef

    @DELETE("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun deleteBranch(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
    ): Response<Unit>

    // ── Pull Requests ──
    @GET("repos/{owner}/{repo}/pulls")
    suspend fun getPullRequests(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("per_page") perPage: Int = 30,
    ): List<GHPullRequest>

    // ── Issues ──
    @GET("repos/{owner}/{repo}/issues")
    suspend fun getIssues(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("per_page") perPage: Int = 30,
    ): List<GHIssue>

    // ── Releases ──
    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 20,
    ): List<GHRelease>

    // ── SSH Keys ──
    @GET("user/keys")
    suspend fun getSSHKeys(): List<GHSSHKey>

    @POST("user/keys")
    suspend fun addSSHKey(@Body body: GHCreateSSHKeyRequest): GHSSHKey

    @DELETE("user/keys/{keyId}")
    suspend fun deleteSSHKey(@Path("keyId") keyId: Long): Response<Unit>

    // ── Search ──
    @GET("search/repositories")
    suspend fun searchRepos(
        @Query("q") query: String,
        @Query("sort") sort: String = "stars",
        @Query("per_page") perPage: Int = 20,
    ): GHSearchResult<GHRepo>

    // ── Star ──
    @PUT("user/starred/{owner}/{repo}")
    suspend fun starRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Response<Unit>

    @DELETE("user/starred/{owner}/{repo}")
    suspend fun unstarRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Response<Unit>

    @GET("user/starred/{owner}/{repo}")
    suspend fun checkStarred(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Response<Unit>
    // ── Orgs ──
    @GET("user/orgs")
    suspend fun getUserOrgs(@Query("per_page") perPage: Int = 50): List<GHOrg>

    @GET("orgs/{org}/repos")
    suspend fun getOrgRepos(
        @Path("org") org: String,
        @Query("sort") sort: String = "updated",
        @Query("per_page") perPage: Int = 50,
    ): List<GHRepo>

    // ── Repo PATCH (rename / edit) ──
    @PATCH("repos/{owner}/{repo}")
    suspend fun updateRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: GHUpdateRepoRequest,
    ): GHRepo

    // ── Topics ──
    @Headers("Accept: application/vnd.github.mercy-preview+json")
    @GET("repos/{owner}/{repo}/topics")
    suspend fun getTopics(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): GHTopics

    @Headers("Accept: application/vnd.github.mercy-preview+json")
    @PUT("repos/{owner}/{repo}/topics")
    suspend fun replaceTopics(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: GHTopics,
    ): GHTopics

    // ── Branch rename ──
    @POST("repos/{owner}/{repo}/branches/{branch}/rename")
    suspend fun renameBranch(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Body body: GHRenameBranchRequest,
    ): GHBranch

    // ── Commit detail (full with files) ──
    @GET("repos/{owner}/{repo}/commits/{sha}")
    suspend fun getCommitFull(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("sha") sha: String,
    ): GHCommitFull

    // ── Check name availability ──
    @GET("repos/{owner}/{repo}")
    suspend fun checkRepoExists(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): retrofit2.Response<GHRepo>
}
