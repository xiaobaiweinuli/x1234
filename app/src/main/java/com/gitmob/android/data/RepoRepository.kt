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
    // 目录内容缓存：key = "owner/repo/ref/path"，TTL = 2 分钟
    private val contentsCache   = java.util.concurrent.ConcurrentHashMap<String, Entry<List<GHContent>>>()
    private val CONTENTS_TTL    = 2 * 60 * 1000L
    // 文件内容缓存：key = "owner/repo/ref/path"，TTL = 5 分钟（文件内容变化更少）
    private val fileContentCache = java.util.concurrent.ConcurrentHashMap<String, Entry<String>>()
    private val FILE_TTL         = 5 * 60 * 1000L

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

    fun invalidateCommitCache(owner: String, repo: String, sha: String) {
        commitCache.remove("$owner/$repo/$sha")
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

    /**
     * 获取目录内容，带 2 分钟内存缓存。
     * 切换路径/分支时命中缓存，避免重复请求。
     * forceRefresh = true 可强制跳过缓存（下拉刷新场景）。
     */
    suspend fun getContents(
        owner: String, repo: String, path: String, ref: String,
        forceRefresh: Boolean = false,
    ): List<GHContent> = withContext(Dispatchers.IO) {
        val key = "$owner/$repo/$ref/${path.ifEmpty { "__root__" }}"
        if (!forceRefresh) {
            contentsCache[key]?.takeIf { it.valid(CONTENTS_TTL) }?.data?.let {
                return@withContext it
            }
        }
        val result = api.getContents(owner, repo, path.ifEmpty { "" }, ref)
        contentsCache[key] = Entry(result)
        result
    }

    /** 使 contentsCache 中该仓库的所有路径失效（push/commit 后调用） */
    fun invalidateContentsCache(owner: String, repo: String) {
        contentsCache.keys.removeAll { it.startsWith("$owner/$repo/") }
    }

    /**
     * 获取文件内容（Base64 解码），带 5 分钟内存缓存。
     */
    suspend fun getFileContent(
        owner: String, repo: String, path: String, ref: String,
        forceRefresh: Boolean = false,
    ): String = withContext(Dispatchers.IO) {
        val key = "$owner/$repo/$ref/$path"
        if (!forceRefresh) {
            fileContentCache[key]?.takeIf { it.valid(FILE_TTL) }?.data?.let {
                return@withContext it
            }
        }
        val f = api.getFile(owner, repo, path, ref)
        val encoded = f.content ?: return@withContext ""
        val decoded = String(Base64.decode(encoded.replace("\n", ""), Base64.DEFAULT), Charsets.UTF_8)
        fileContentCache[key] = Entry(decoded)
        decoded
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

    // ─── 服务端 Reset / Revert（通过 GitHub Git Data API，无需本地 git）──────

    /**
     * 回滚：强制将分支指针移动到目标 SHA（等同于 git reset --hard + git push -f）
     * ⚠ 会重写提交历史，之后的提交从该分支消失
     */
    suspend fun resetBranchToCommit(
        owner: String, repo: String, branch: String, sha: String,
    ) = withContext(Dispatchers.IO) {
        api.updateRef(owner, repo, branch, GHUpdateRefRequest(sha, force = true))
    }

    /**
     * 撤销（Revert）：在当前 HEAD 上创建一个新提交，将内容恢复到目标提交的父提交状态。
     * 不重写历史，保留所有提交记录，适合有保护规则的主分支。
     *
     * 步骤：
     *  1. 获取目标提交的父 SHA
     *  2. 获取父提交的 tree SHA（文件快照）
     *  3. 以当前 HEAD 为 parent，用父提交的 tree 创建新 commit
     *  4. 快进更新分支指针
     */
    suspend fun revertCommit(
        owner: String, repo: String, branch: String,
        targetSha: String, currentHeadSha: String, message: String,
    ): GHCreateCommitResponse = withContext(Dispatchers.IO) {
        val targetGit = api.getGitCommit(owner, repo, targetSha)
        val parentSha = targetGit.parents.firstOrNull()?.sha
            ?: error("该提交没有父提交（初始提交），无法 revert")
        val parentGit = api.getGitCommit(owner, repo, parentSha)
        val newCommit = api.createCommit(
            owner, repo,
            GHCreateCommitRequest(
                message = message,
                tree    = parentGit.tree.sha,
                parents = listOf(currentHeadSha),
            )
        )
        api.updateRef(owner, repo, branch, GHUpdateRefRequest(newCommit.sha, force = false))
        newCommit
    }

    // ─── PR / Issues ───

    suspend fun getPRs(owner: String, repo: String): List<GHPullRequest> =
        withContext(Dispatchers.IO) { api.getPullRequests(owner, repo) }

    suspend fun getIssues(owner: String, repo: String): List<GHIssue> =
        withContext(Dispatchers.IO) { api.getIssues(owner, repo) }
}
