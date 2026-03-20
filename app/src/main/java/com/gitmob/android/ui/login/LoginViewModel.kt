package com.gitmob.android.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gitmob.android.api.ApiClient
import com.gitmob.android.auth.AccountInfo
import com.gitmob.android.auth.AccountStore
import com.gitmob.android.auth.TokenStorage
import com.gitmob.android.ui.login.LoginUiState.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

sealed class LoginUiState {
    object Idle    : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val login: String) : LoginUiState()
    data class Error(val msg: String)     : LoginUiState()
}

class LoginViewModel(app: Application) : AndroidViewModel(app) {

    private val tokenStorage = TokenStorage(app)
    private val accountStore = AccountStore(app)

    private val _state = MutableStateFlow<LoginUiState>(Idle)
    val state = _state.asStateFlow()

    /** 所有已保存账号（供 LoginScreen 展示账号列表） */
    val savedAccounts: StateFlow<List<AccountInfo>> = accountStore.accounts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun onOAuthError(msg: String) {
        _state.value = Error("授权失败：$msg")
    }

    /** 收到新 token：获取用户信息 → 存入 AccountStore → 同步 TokenStorage → 重建 ApiClient */
    fun onTokenReceived(token: String) {
        viewModelScope.launch {
            _state.value = Loading
            try {
                // 用临时 token 获取用户信息
                tokenStorage.saveToken(token)
                ApiClient.rebuild()
                val user = ApiClient.api.getCurrentUser()

                val info = AccountInfo(
                    login     = user.login,
                    name      = user.name ?: user.login,
                    email     = user.email ?: "${user.login}@users.noreply.github.com",
                    avatarUrl = user.avatarUrl ?: "",
                    token     = token,
                )
                // 存入多账号列表，设为活跃
                accountStore.addOrUpdateAccount(info)
                // 同步到 TokenStorage 兼容字段
                tokenStorage.syncActiveAccount(info)
                ApiClient.rebuild()

                _state.value = Success(user.login)
            } catch (e: Exception) {
                // 清理脏数据：既清 TokenStorage 又恢复 AccountStore 的活跃账号
                tokenStorage.clearActiveAccount()
                // 如果有其他账号，把 active 恢复到第一个有效账号
                val remaining = accountStore.accounts.first()
                if (remaining.isNotEmpty()) {
                    tokenStorage.syncActiveAccount(remaining.first())
                    ApiClient.rebuild()
                }
                _state.value = Error(e.message ?: "认证失败")
            }
        }
    }

    /** 直接切换到已有账号（不需要重新 OAuth） */
    fun switchToAccount(info: AccountInfo) {
        viewModelScope.launch {
            _state.value = Loading
            try {
                accountStore.switchAccount(info.login)
                tokenStorage.syncActiveAccount(info)
                ApiClient.rebuild()
                _state.value = Success(info.login)
            } catch (e: Exception) {
                _state.value = Error(e.message ?: "切换失败")
            }
        }
    }
}
