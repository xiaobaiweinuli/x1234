package com.gitmob.android.local

import android.content.Context
import com.google.gson.Gson
import com.gitmob.android.auth.TokenStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

class LocalRepoStorage(private val context: Context) {

    private val tokenStorage = TokenStorage(context)
    private val gson = Gson()

    // Array<LocalRepo>::class.java 替代 TypeToken<List<LocalRepo>>，R8 安全
    private fun parseRepos(json: String?): List<LocalRepo> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson(json, Array<LocalRepo>::class.java)?.toList() ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    val repos: Flow<List<LocalRepo>> = tokenStorage.localReposJson.map { json ->
        parseRepos(json)
    }

    private suspend fun save(list: List<LocalRepo>) {
        tokenStorage.saveLocalReposJson(gson.toJson(list))
    }

    private suspend fun loadNow(): List<LocalRepo> =
        parseRepos(tokenStorage.localReposJson.first())

    suspend fun addOrUpdate(repo: LocalRepo) {
        val current = loadNow()
        val idx = current.indexOfFirst { it.id == repo.id }
        val updated = if (idx >= 0) current.toMutableList().also { it[idx] = repo }
                      else current + repo
        save(updated)
    }

    suspend fun remove(id: String) {
        val current = loadNow()
        save(current.filter { it.id != id })
    }

    suspend fun update(id: String, block: (LocalRepo) -> LocalRepo) {
        val current = loadNow()
        val updated = current.map { if (it.id == id) block(it) else it }
        save(updated)
    }

    fun newId(): String = UUID.randomUUID().toString()
}
