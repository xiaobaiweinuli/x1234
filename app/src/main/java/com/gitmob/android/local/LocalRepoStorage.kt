package com.gitmob.android.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gitmob.android.auth.TokenStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class LocalRepoStorage(private val context: Context) {

    private val tokenStorage = TokenStorage(context)
    private val gson = Gson()

    val repos: Flow<List<LocalRepo>> = tokenStorage.localReposJson.map { json ->
        try {
            val type = object : TypeToken<List<LocalRepo>>() {}.type
            gson.fromJson<List<LocalRepo>>(json, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private suspend fun save(list: List<LocalRepo>) {
        tokenStorage.saveLocalReposJson(gson.toJson(list))
    }

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

    private suspend fun loadNow(): List<LocalRepo> {
        return try {
            var result: List<LocalRepo> = emptyList()
            tokenStorage.localReposJson.collect {
                val type = object : TypeToken<List<LocalRepo>>() {}.type
                result = gson.fromJson<List<LocalRepo>>(it, type) ?: emptyList()
                return@collect
            }
            result
        } catch (_: Exception) { emptyList() }
    }

    fun newId(): String = UUID.randomUUID().toString()
}
