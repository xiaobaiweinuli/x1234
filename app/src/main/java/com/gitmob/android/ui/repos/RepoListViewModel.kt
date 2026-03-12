package com.gitmob.android.ui.repos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gitmob.android.api.GHRepo
import com.gitmob.android.auth.TokenStorage
import com.gitmob.android.data.RepoRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RepoListState(
    val repos: List<GHRepo> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val userLogin: String = "",
    val userAvatar: String = "",
    val searchQuery: String = "",
    val filterPrivate: Boolean? = null, // null=all true=private false=public
)

class RepoListViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = RepoRepository()
    private val tokenStorage = TokenStorage(app)
    private val _state = MutableStateFlow(RepoListState())
    val state = _state.asStateFlow()

    val filteredRepos = _state.map { s ->
        s.repos.filter { r ->
            (s.searchQuery.isEmpty() || r.name.contains(s.searchQuery, ignoreCase = true) ||
                (r.description?.contains(s.searchQuery, ignoreCase = true) == true)) &&
            (s.filterPrivate == null || r.private == s.filterPrivate)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            tokenStorage.userProfile.collect { profile ->
                if (profile != null) {
                    _state.update { it.copy(userLogin = profile.first, userAvatar = profile.third) }
                }
            }
        }
        loadRepos()
    }

    fun loadRepos() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val repos = repo.getMyRepos()
                _state.update { it.copy(repos = repos, loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    fun setSearch(q: String) = _state.update { it.copy(searchQuery = q) }
    fun setFilter(private: Boolean?) = _state.update { it.copy(filterPrivate = private) }
}
