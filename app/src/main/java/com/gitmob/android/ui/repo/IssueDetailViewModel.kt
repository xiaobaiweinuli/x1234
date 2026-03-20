package com.gitmob.android.ui.repo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.gitmob.android.api.*
import com.gitmob.android.auth.TokenStorage
import com.gitmob.android.data.RepoRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class IssueDetailState(
    val issue: GHIssue? = null,
    val comments: List<GHComment> = emptyList(),
    val loading: Boolean = false,
    val commentsLoading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val userLogin: String = "",
    val toast: String? = null,
    val isRepoOwner: Boolean = false,
    val subscription: GHIssueSubscription? = null,
    val subscriptionLoading: Boolean = false,
)

class IssueDetailViewModel(
    app: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(app) {

    val owner: String = savedStateHandle["owner"] ?: ""
    val repoName: String = savedStateHandle["repo"] ?: ""
    val issueNumber: Int = savedStateHandle["issueNumber"] ?: 0

    private val repository = RepoRepository()
    private val tokenStorage = TokenStorage(app)
    private val _state = MutableStateFlow(IssueDetailState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            tokenStorage.userProfile.collect { profile ->
                if (profile != null) {
                    _state.update {
                        it.copy(
                            userLogin = profile.first,
                            isRepoOwner = profile.first == owner
                        )
                    }
                }
            }
        }
        loadIssueDetail()
    }

    fun loadIssueDetail(forceRefresh: Boolean = false) = viewModelScope.launch {
        _state.update { it.copy(loading = true, refreshing = forceRefresh, error = null) }
        try {
            val issue = repository.getIssue(owner, repoName, issueNumber)
            _state.update { it.copy(issue = issue, loading = false, refreshing = false) }
            loadComments(forceRefresh)
            loadSubscription()
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    error = e.message ?: "加载失败"
                )
            }
        }
    }

    /**
     * 加载Issue订阅状态
     */
    fun loadSubscription() = viewModelScope.launch {
        _state.update { it.copy(subscriptionLoading = true) }
        try {
            val subscription = repository.getIssueSubscription(owner, repoName, issueNumber)
            _state.update { it.copy(subscription = subscription, subscriptionLoading = false) }
        } catch (e: Exception) {
            _state.update { it.copy(subscription = null, subscriptionLoading = false) }
        }
    }

    fun loadComments(forceRefresh: Boolean = false) = viewModelScope.launch {
        _state.update { it.copy(commentsLoading = true) }
        try {
            val comments = repository.getIssueComments(owner, repoName, issueNumber)
            _state.update { it.copy(comments = comments, commentsLoading = false) }
        } catch (_: Exception) {
            _state.update { it.copy(commentsLoading = false) }
        }
    }

    fun createComment(body: String) = viewModelScope.launch {
        try {
            val comment = repository.createIssueComment(owner, repoName, issueNumber, body)
            _state.update {
                it.copy(
                    comments = it.comments + comment,
                    toast = "评论已发表"
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(toast = "发表失败：${e.message}") }
        }
    }

    fun updateComment(commentId: Long, body: String) = viewModelScope.launch {
        try {
            val updated = repository.updateIssueComment(owner, repoName, commentId, body)
            _state.update {
                it.copy(
                    comments = it.comments.map { c -> if (c.id == commentId) updated else c },
                    toast = "评论已更新"
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(toast = "更新失败：${e.message}") }
        }
    }

    fun deleteComment(commentId: Long) = viewModelScope.launch {
        try {
            val success = repository.deleteIssueComment(owner, repoName, commentId)
            if (success) {
                _state.update {
                    it.copy(
                        comments = it.comments.filter { c -> c.id != commentId },
                        toast = "评论已删除"
                    )
                }
            } else {
                _state.update { it.copy(toast = "删除失败") }
            }
        } catch (e: Exception) {
            _state.update { it.copy(toast = "删除失败：${e.message}") }
        }
    }

    fun updateIssue(title: String? = null, body: String? = null, state: String? = null, stateReason: String? = null) = viewModelScope.launch {
        try {
            val updated = repository.updateIssue(
                owner, repoName, issueNumber,
                GHUpdateIssueRequest(title = title, body = body, state = state, stateReason = stateReason)
            )
            _state.update { it.copy(issue = updated, toast = "议题已更新") }
        } catch (e: Exception) {
            _state.update { it.copy(toast = "更新失败：${e.message}") }
        }
    }

    /**
     * 订阅Issue
     */
    fun subscribe() = viewModelScope.launch {
        try {
            val subscription = repository.subscribeIssue(owner, repoName, issueNumber)
            _state.update { it.copy(subscription = subscription, toast = "已订阅") }
        } catch (e: Exception) {
            _state.update { it.copy(toast = "订阅失败：${e.message}") }
        }
    }

    /**
     * 取消订阅Issue
     */
    fun unsubscribe() = viewModelScope.launch {
        try {
            val success = repository.unsubscribeIssue(owner, repoName, issueNumber)
            if (success) {
                _state.update { 
                    it.copy(
                        subscription = it.subscription?.copy(subscribed = false),
                        toast = "已取消订阅"
                    ) 
                }
            } else {
                _state.update { it.copy(toast = "取消订阅失败") }
            }
        } catch (e: Exception) {
            _state.update { it.copy(toast = "取消订阅失败：${e.message}") }
        }
    }

    /**
     * 切换订阅状态
     */
    fun toggleSubscription() = viewModelScope.launch {
        val currentSubscription = _state.value.subscription
        if (currentSubscription?.subscribed == true) {
            unsubscribe()
        } else {
            subscribe()
        }
    }

    fun deleteIssue(onSuccess: () -> Unit) = viewModelScope.launch {
        try {
            val success = repository.deleteIssue(owner, repoName, issueNumber)
            if (success) {
                _state.update { it.copy(toast = "议题已删除") }
                onSuccess()
            } else {
                _state.update { it.copy(toast = "删除失败") }
            }
        } catch (e: Exception) {
            _state.update { it.copy(toast = "删除失败：${e.message}") }
        }
    }

    fun clearToast() = _state.update { it.copy(toast = null) }

    companion object {
        fun factory(owner: String, repo: String, issueNumber: Int): androidx.lifecycle.ViewModelProvider.Factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(
                    modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.CreationExtras,
                ): T {
                    val app = checkNotNull(extras[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                    val handle = SavedStateHandle(
                        mapOf(
                            "owner" to owner,
                            "repo" to repo,
                            "issueNumber" to issueNumber
                        )
                    )
                    return IssueDetailViewModel(app, handle) as T
                }
            }
    }
}
