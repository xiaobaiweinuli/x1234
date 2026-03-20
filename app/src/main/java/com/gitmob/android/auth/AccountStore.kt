package com.gitmob.android.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 单个账号的完整信息（token 明文存储，与现有单账号模式一致）
 */
data class AccountInfo(
    val login: String,
    val name: String,
    val email: String,
    val avatarUrl: String,
    val token: String,
) {
    val displayName: String get() = name.ifBlank { login }
}

/**
 * 多账号存储管理器
 *
 * 数据模型：
 *   accounts_json  → List<AccountInfo> JSON（所有已授权账号）
 *   active_login   → 当前活跃账号的 login
 *
 * 与 TokenStorage 协同工作：
 *   TokenStorage 保留单账号字段作为"当前活跃账号"的镜像，供其他组件兼容读取。
 *   AccountStore 管理完整的多账号列表。
 */
class AccountStore(private val context: Context) {

    private val gson = Gson()
    private val listType = object : TypeToken<List<AccountInfo>>() {}.type

    private object Keys {
        val ACCOUNTS_JSON  = stringPreferencesKey("accounts_json")
        val ACTIVE_LOGIN   = stringPreferencesKey("active_login")
    }

    /** 所有已保存账号的流 */
    val accounts: Flow<List<AccountInfo>> = context.dataStore.data.map { prefs ->
        val json = prefs[Keys.ACCOUNTS_JSON] ?: return@map emptyList()
        try { gson.fromJson(json, listType) ?: emptyList() }
        catch (_: Exception) { emptyList() }
    }

    /** 当前活跃账号的 login */
    val activeLogin: Flow<String?> = context.dataStore.data.map { it[Keys.ACTIVE_LOGIN] }

    /** 当前活跃账号信息 */
    val activeAccount: Flow<AccountInfo?> = context.dataStore.data.map { prefs ->
        val login = prefs[Keys.ACTIVE_LOGIN] ?: return@map null
        val json  = prefs[Keys.ACCOUNTS_JSON] ?: return@map null
        try {
            val list: List<AccountInfo> = gson.fromJson(json, listType) ?: return@map null
            list.firstOrNull { it.login == login }
        } catch (_: Exception) { null }
    }

    /** 添加或更新账号（login 相同则覆盖），并设为活跃账号 */
    suspend fun addOrUpdateAccount(info: AccountInfo) {
        context.dataStore.edit { prefs ->
            val current: List<AccountInfo> = try {
                gson.fromJson(prefs[Keys.ACCOUNTS_JSON] ?: "[]", listType) ?: emptyList()
            } catch (_: Exception) { emptyList() }

            val updated = current.filter { it.login != info.login } + info
            prefs[Keys.ACCOUNTS_JSON] = gson.toJson(updated)
            prefs[Keys.ACTIVE_LOGIN]  = info.login
        }
    }

    /** 切换活跃账号，返回目标账号信息（不存在则返回 null） */
    suspend fun switchAccount(login: String): AccountInfo? {
        val list = accounts.first()
        val target = list.firstOrNull { it.login == login } ?: return null
        context.dataStore.edit { it[Keys.ACTIVE_LOGIN] = login }
        return target
    }

    /**
     * 移除账号。
     * @return 移除后的剩余账号列表
     */
    suspend fun removeAccount(login: String): List<AccountInfo> {
        var remaining: List<AccountInfo> = emptyList()
        context.dataStore.edit { prefs ->
            val current: List<AccountInfo> = try {
                gson.fromJson(prefs[Keys.ACCOUNTS_JSON] ?: "[]", listType) ?: emptyList()
            } catch (_: Exception) { emptyList() }

            remaining = current.filter { it.login != login }
            prefs[Keys.ACCOUNTS_JSON] = gson.toJson(remaining)

            // 如果移除的是活跃账号，切换到第一个剩余账号（或清空）
            val activeWasRemoved = prefs[Keys.ACTIVE_LOGIN] == login
            if (activeWasRemoved) {
                if (remaining.isNotEmpty()) {
                    prefs[Keys.ACTIVE_LOGIN] = remaining.first().login
                } else {
                    prefs.remove(Keys.ACTIVE_LOGIN)
                }
            }
        }
        return remaining
    }

    /** 获取指定账号的 token（用于撤销操作） */
    suspend fun getToken(login: String): String? =
        accounts.first().firstOrNull { it.login == login }?.token

    /** 清空所有账号数据 */
    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.ACCOUNTS_JSON)
            prefs.remove(Keys.ACTIVE_LOGIN)
        }
    }
}
