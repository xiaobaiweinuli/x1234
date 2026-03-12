package com.gitmob.android.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gitmob.android.api.ApiClient
import com.gitmob.android.auth.TokenStorage
import com.gitmob.android.ui.login.LoginUiState.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val login: String) : LoginUiState()
    data class Error(val msg: String) : LoginUiState()
}

class LoginViewModel(app: Application) : AndroidViewModel(app) {
    private val tokenStorage = TokenStorage(app)
    private val _state = MutableStateFlow<LoginUiState>(Idle)
    val state = _state.asStateFlow()

    fun onOAuthError(msg: String) {
        _state.value = Error("授权失败：$msg")
    }

    fun onTokenReceived(token: String) {
        viewModelScope.launch {
            _state.value = Loading
            try {
                tokenStorage.saveToken(token)
                ApiClient.rebuild()
                val user = ApiClient.api.getCurrentUser()
                tokenStorage.saveUser(user.login, user.name, user.avatarUrl)
                _state.value = Success(user.login)
            } catch (e: Exception) {
                tokenStorage.clear()
                _state.value = Error(e.message ?: "认证失败")
            }
        }
    }
}
