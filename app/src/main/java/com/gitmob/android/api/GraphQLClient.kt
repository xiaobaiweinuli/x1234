package com.gitmob.android.api

import com.gitmob.android.util.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * GitHub GraphQL API 客户端
 *
 * 优势：一次请求获取仓库全量概况（repo + stars + branches count + defaultBranch + topics + languages +
 * README existence + PR count + Issue count + release count），
 * 替代原来 loadAll() 中的 5-6 个并发 REST 请求。
 *
 * 端点：POST https://api.github.com/graphql
 */
object GraphQLClient {

    private const val TAG = "GraphQL"
    private const val ENDPOINT = "https://api.github.com/graphql"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // ── 仓库概况查询 ────────────────────────────────────────────────────────
    // 一次请求替代：getRepo + getTopics + getBranches(count) + README check
    private val REPO_OVERVIEW_QUERY = """
        query RepoOverview(${'$'}owner: String!, ${'$'}name: String!) {
          repository(owner: ${'$'}owner, name: ${'$'}name) {
            id
            name
            nameWithOwner
            description
            url
            isPrivate
            isFork
            isArchived
            stargazerCount
            forkCount
            openGraphImageUrl
            defaultBranchRef {
              name
            }
            primaryLanguage {
              name
              color
            }
            languages(first: 5, orderBy: {field: SIZE, direction: DESC}) {
              nodes { name color }
            }
            repositoryTopics(first: 10) {
              nodes { topic { name } }
            }
            refs(refPrefix: "refs/heads/", first: 0) {
              totalCount
            }
            openIssues: issues(states: OPEN) { totalCount }
            openPRs:    pullRequests(states: OPEN) { totalCount }
            releases    { totalCount }
            object(expression: "HEAD:README.md") { id }
            diskUsage
            pushedAt
            updatedAt
            licenseInfo { spdxId name }
            watchers { totalCount }
          }
        }
    """.trimIndent()

    // ── 提交历史 + 文件变更（减少 per-file REST 请求） ─────────────────────
    private val COMMIT_HISTORY_QUERY = """
        query CommitHistory(${'$'}owner: String!, ${'$'}name: String!, ${'$'}branch: String!, ${'$'}count: Int!) {
          repository(owner: ${'$'}owner, name: ${'$'}name) {
            ref(qualifiedName: ${'$'}branch) {
              target {
                ... on Commit {
                  history(first: ${'$'}count) {
                    nodes {
                      oid
                      messageHeadline
                      committedDate
                      author { name email avatarUrl }
                      additions
                      deletions
                    }
                  }
                }
              }
            }
          }
        }
    """.trimIndent()

    /**
     * 查询仓库概况，返回原始 JSON 节点（由 RepoRepository 解析映射到现有数据类）。
     * @return JSONObject of repository node，或 null（失败/降级到 REST）
     */
    suspend fun queryRepoOverview(token: String, owner: String, name: String): JSONObject? =
        withContext(Dispatchers.IO) {
            try {
                val vars = JSONObject().put("owner", owner).put("name", name)
                val body = JSONObject()
                    .put("query", REPO_OVERVIEW_QUERY)
                    .put("variables", vars)
                val resp = post(token, body.toString())
                resp?.optJSONObject("data")?.optJSONObject("repository")
            } catch (e: Exception) {
                LogManager.w(TAG, "GraphQL 仓库概况失败，降级 REST: ${e.message}")
                null
            }
        }

    /**
     * 查询提交历史（GraphQL 比 REST 少一半请求）。
     */
    suspend fun queryCommitHistory(
        token: String, owner: String, name: String,
        branch: String, count: Int = 30,
    ): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val vars = JSONObject()
                .put("owner", owner).put("name", name)
                .put("branch", "refs/heads/$branch").put("count", count)
            val body = JSONObject()
                .put("query", COMMIT_HISTORY_QUERY)
                .put("variables", vars)
            val resp = post(token, body.toString())
            resp?.optJSONObject("data")
                ?.optJSONObject("repository")
                ?.optJSONObject("ref")
                ?.optJSONObject("target")
                ?.optJSONObject("history")
        } catch (e: Exception) {
            LogManager.w(TAG, "GraphQL 提交历史失败，降级 REST: ${e.message}")
            null
        }
    }

    // ── 内部 HTTP ────────────────────────────────────────────────────────────

    private fun post(token: String, jsonBody: String): JSONObject? {
        val req = Request.Builder()
            .url(ENDPOINT)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $token")
            .header("Accept",        "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) {
            LogManager.w(TAG, "GraphQL HTTP ${resp.code}")
            return null
        }
        val text = resp.body?.string() ?: return null
        val json = JSONObject(text)
        // 检查 errors 字段
        val errors = json.optJSONArray("errors")
        if (errors != null && errors.length() > 0) {
            LogManager.w(TAG, "GraphQL errors: ${errors.getJSONObject(0).optString("message")}")
        }
        return json
    }
}
