package com.gitmob.android.data

import android.util.Base64
import com.gitmob.android.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RepoRepository {
    private val api get() = ApiClient.api

    suspend fun getMyRepos(page: Int = 1) = withContext(Dispatchers.IO) {
        api.getMyRepos(page = page)
    }

    suspend fun getRepo(owner: String, repo: String) = withContext(Dispatchers.IO) {
        api.getRepo(owner, repo)
    }

    suspend fun createRepo(name: String, desc: String?, private: Boolean, autoInit: Boolean) =
        withContext(Dispatchers.IO) {
            api.createRepo(GHCreateRepoRequest(name, desc, private, autoInit))
        }

    suspend fun deleteRepo(owner: String, repo: String) = withContext(Dispatchers.IO) {
        api.deleteRepo(owner, repo)
    }

    suspend fun getContents(owner: String, repo: String, path: String, ref: String?) =
        withContext(Dispatchers.IO) { api.getContents(owner, repo, path, ref) }

    suspend fun getFileContent(owner: String, repo: String, path: String, ref: String?): String =
        withContext(Dispatchers.IO) {
            val file = api.getFile(owner, repo, path, ref)
            val raw = (file.content ?: "").replace("\n", "")
            String(Base64.decode(raw, Base64.DEFAULT))
        }

    suspend fun createFile(
        owner: String, repo: String, path: String,
        content: String, message: String, branch: String?,
    ) = withContext(Dispatchers.IO) {
        val encoded = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
        api.createOrUpdateFile(owner, repo, path, GHCreateFileRequest(message, encoded, branch))
    }

    suspend fun updateFile(
        owner: String, repo: String, path: String,
        content: String, message: String, sha: String, branch: String?,
    ) = withContext(Dispatchers.IO) {
        val encoded = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
        api.createOrUpdateFile(owner, repo, path, GHCreateFileRequest(message, encoded, branch, sha))
    }

    suspend fun getBranches(owner: String, repo: String) = withContext(Dispatchers.IO) {
        api.getBranches(owner, repo)
    }

    suspend fun createBranch(owner: String, repo: String, name: String, fromSha: String) =
        withContext(Dispatchers.IO) {
            api.createBranch(owner, repo, GHCreateBranchRequest("refs/heads/$name", fromSha))
        }

    suspend fun getCommits(owner: String, repo: String, sha: String?, page: Int) =
        withContext(Dispatchers.IO) { api.getCommits(owner, repo, sha, page = page) }

    suspend fun getPullRequests(owner: String, repo: String) = withContext(Dispatchers.IO) {
        api.getPullRequests(owner, repo)
    }

    suspend fun getIssues(owner: String, repo: String) = withContext(Dispatchers.IO) {
        api.getIssues(owner, repo)
    }

    suspend fun getReleases(owner: String, repo: String) = withContext(Dispatchers.IO) {
        api.getReleases(owner, repo)
    }

    suspend fun getCurrentUser() = withContext(Dispatchers.IO) { api.getCurrentUser() }

    suspend fun getSSHKeys() = withContext(Dispatchers.IO) { api.getSSHKeys() }

    suspend fun addSSHKey(title: String, key: String) = withContext(Dispatchers.IO) {
        api.addSSHKey(GHCreateSSHKeyRequest(title, key))
    }

    suspend fun starRepo(owner: String, repo: String) = withContext(Dispatchers.IO) {
        api.starRepo(owner, repo)
    }

    suspend fun unstarRepo(owner: String, repo: String) = withContext(Dispatchers.IO) {
        api.unstarRepo(owner, repo)
    }

    suspend fun isStarred(owner: String, repo: String): Boolean = withContext(Dispatchers.IO) {
        api.checkStarred(owner, repo).code() == 204
    }

    suspend fun searchRepos(query: String) = withContext(Dispatchers.IO) {
        api.searchRepos(query)
    }
}
