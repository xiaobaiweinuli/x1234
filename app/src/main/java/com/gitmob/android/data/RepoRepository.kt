package com.gitmob.android.data

import android.util.Base64
import com.gitmob.android.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RepoRepository {

    private val api get() = ApiClient.api

    // ─── 内存缓存 ─────────────────────────────────────────────────────────────
    private var reposCache: List<GHRepo>? = null
    private var reposCacheTime: Long = 0
    private val CACHE_TTL = 5 * 60 * 1000L           // 5 分钟（列表）
    private val DETAIL_TTL = 60 * 1000L               // 60 秒（详情）

    private data class Entry<T>(val data: T, val ts: Long = System.currentTimeMillis()) {
        fun valid(ttl: Long = 60_000L) = System.currentTimeMillis() - ts < ttl
    }
    private val repoDetailCache = java.util.concurrent.ConcurrentHashMap<String, Entry<GHRepo>>()
    private val branchCache     = java.util.concurrent.ConcurrentHashMap<String, Entry<List<GHBranch>>>()
    private val commitCache     = java.util.concurrent.ConcurrentHashMap<String, Entry<List<GHCommit>>>()

    // ─── 用户 / 仓库 ───

    suspend fun getMyRepos(forceRefresh: Boolean = false): List<GHRepo> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && reposCache != null && (now - reposCacheTime) < CACHE_TTL) {
            return@withContext reposCache!!
        }
        val repos = api.getMyRepos()
        reposCache = repos
        reposCacheTime = now
        repos
    }

    fun invalidateReposCache() {
        reposCache = null
        reposCacheTime = 0
    }

    suspend fun getOrgRepos(org: String): List<GHRepo> = withContext(Dispatchers.IO) {
        api.getOrgRepos(org)
    }

    suspend fun getUserOrgs(): List<GHOrg> = withContext(Dispatchers.IO) {
        api.getUserOrgs()
    }

    suspend fun getRepo(owner: String, repo: String, forceRefresh: Boolean = false): GHRepo = withContext(Dispatchers.IO) {
        val key = "$owner/$repo"
        if (!forceRefresh) repoDetailCache[key]?.takeIf { it.valid(DETAIL_TTL) }?.data?.let { return@withContext it }
        val result = api.getRepo(owner, repo)
        repoDetailCache[key] = Entry(result)
        result
    }

    suspend fun createRepo(body: GHCreateRepoRequest): GHRepo = withContext(Dispatchers.IO) {
        invalidateReposCache()
        api.createRepo(body)
    }

    suspend fun updateRepo(owner: String, repo: String, body: GHUpdateRepoRequest): GHRepo = withContext(Dispatchers.IO) {
        invalidateReposCache()
        api.updateRepo(owner, repo, body)
    }

    suspend fun deleteRepo(owner: String, repo: String): Boolean = withContext(Dispatchers.IO) {
        val resp = api.deleteRepo(owner, repo)
        invalidateReposCache()
        resp.isSuccessful
    }

    suspend fun getTopics(owner: String, repo: String): List<String> = withContext(Dispatchers.IO) {
        api.getTopics(owner, repo).names
    }

    suspend fun replaceTopics(owner: String, repo: String, topics: List<String>): List<String> = withContext(Dispatchers.IO) {
        api.replaceTopics(owner, repo, GHTopics(topics)).names
    }

    // ─── Contents / Files ───

    suspend fun getContents(owner: String, repo: String, path: String, ref: String): List<GHContent> =
        withContext(Dispatchers.IO) { api.getContents(owner, repo, path.ifEmpty { "" }, ref) }

    suspend fun getFileContent(owner: String, repo: String, path: String, ref: String): String =
        withContext(Dispatchers.IO) {
            val f = api.getFile(owner, repo, path, ref)
            val encoded = f.content ?: return@withContext ""
            String(Base64.decode(encoded.replace("\n", ""), Base64.DEFAULT), Charsets.UTF_8)
        }

    // ─── Commits ───

    suspend fun getCommits(owner: String, repo: String, sha: String, forceRefresh: Boolean = false): List<GHCommit> =
        withContext(Dispatchers.IO) {
            val key = "$owner/$repo/$sha"
            if (!forceRefresh) commitCache[key]?.takeIf { it.valid(30_000L) }?.data?.let { return@withContext it }
            val result = api.getCommits(owner, repo, sha)
            commitCache[key] = Entry(result)
            result
        }

    suspend fun getCommitDetail(owner: String, repo: String, sha: String): GHCommitFull =
        withContext(Dispatchers.IO) { api.getCommitFull(owner, repo, sha) }

    // ─── Branches ───

    suspend fun getBranches(owner: String, repo: String, forceRefresh: Boolean = false): List<GHBranch> =
        withContext(Dispatchers.IO) {
            val key = "$owner/$repo"
            if (!forceRefresh) branchCache[key]?.takeIf { it.valid(DETAIL_TTL) }?.data?.let { return@withContext it }
            val result = api.getBranches(owner, repo)
            branchCache[key] = Entry(result)
            result
        }

    suspend fun createBranch(owner: String, repo: String, name: String, fromSha: String): GHRef =
        withContext(Dispatchers.IO) {
            api.createBranch(owner, repo, GHCreateBranchRequest("refs/heads/$name", fromSha))
        }

    suspend fun deleteBranch(owner: String, repo: String, branch: String): Boolean =
        withContext(Dispatchers.IO) { api.deleteBranch(owner, repo, branch).isSuccessful }

    suspend fun renameBranch(owner: String, repo: String, oldName: String, newName: String): GHBranch =
        withContext(Dispatchers.IO) {
            api.renameBranch(owner, repo, oldName, GHRenameBranchRequest(newName))
        }

    // ─── Star ───

    suspend fun isStarred(owner: String, repo: String): Boolean = withContext(Dispatchers.IO) {
        api.checkStarred(owner, repo).isSuccessful
    }

    suspend fun starRepo(owner: String, repo: String) = withContext(Dispatchers.IO) {
        api.starRepo(owner, repo)
    }

    suspend fun unstarRepo(owner: String, repo: String) = withContext(Dispatchers.IO) {
        api.unstarRepo(owner, repo)
    }

    // ─── PR / Issues ───

    suspend fun getPRs(owner: String, repo: String): List<GHPullRequest> =
        withContext(Dispatchers.IO) { api.getPullRequests(owner, repo) }

    suspend fun getIssues(owner: String, repo: String): List<GHIssue> =
        withContext(Dispatchers.IO) { api.getIssues(owner, repo) }
}
