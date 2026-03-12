package com.gitmob.android.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gitmob_prefs")

/** 主题模式：0=浅色(默认) 1=深色 2=跟随系统 */
enum class ThemeMode(val value: Int) {
    LIGHT(0), DARK(1), SYSTEM(2);
    companion object {
        fun fromInt(v: Int) = entries.firstOrNull { it.value == v } ?: LIGHT
    }
}

class TokenStorage(private val context: Context) {

    private object Keys {
        val ACCESS_TOKEN  = stringPreferencesKey("access_token")
        val USER_LOGIN    = stringPreferencesKey("user_login")
        val USER_NAME     = stringPreferencesKey("user_name")
        val AVATAR_URL    = stringPreferencesKey("avatar_url")
        val THEME_MODE    = intPreferencesKey("theme_mode")
        val ROOT_ENABLED  = booleanPreferencesKey("root_enabled")
        val LOCAL_REPOS   = stringPreferencesKey("local_repos_json")   // JSON 列表
        val BOOKMARKS     = stringPreferencesKey("file_bookmarks_json") // 自定义书签
        val LOG_LEVEL     = intPreferencesKey("log_level")
    }

    val accessToken: Flow<String?> = context.dataStore.data.map { it[Keys.ACCESS_TOKEN] }
    val userLogin:   Flow<String?> = context.dataStore.data.map { it[Keys.USER_LOGIN] }

    val userProfile: Flow<Triple<String, String, String>?> = context.dataStore.data.map { prefs ->
        val login = prefs[Keys.USER_LOGIN] ?: return@map null
        Triple(login, prefs[Keys.USER_NAME] ?: login, prefs[Keys.AVATAR_URL] ?: "")
    }

    /** 默认浅色（LIGHT=0） */
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        ThemeMode.fromInt(it[Keys.THEME_MODE] ?: ThemeMode.LIGHT.value)
    }

    val rootEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.ROOT_ENABLED] ?: false
    }

    val localReposJson: Flow<String> = context.dataStore.data.map {
        it[Keys.LOCAL_REPOS] ?: "[]"
    }

    val logLevel: Flow<Int> = context.dataStore.data.map { it[Keys.LOG_LEVEL] ?: 1 } // 默认 DEBUG=1

    val bookmarksJson: Flow<String> = context.dataStore.data.map {
        it[Keys.BOOKMARKS] ?: "[]"
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { it[Keys.ACCESS_TOKEN] = token }
    }
    suspend fun saveUser(login: String, name: String?, avatarUrl: String?) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_LOGIN] = login
            prefs[Keys.USER_NAME]  = name ?: login
            prefs[Keys.AVATAR_URL] = avatarUrl ?: ""
        }
    }
    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.value }
    }
    suspend fun setRootEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ROOT_ENABLED] = enabled }
    }

    suspend fun saveLocalReposJson(json: String) {
        context.dataStore.edit { it[Keys.LOCAL_REPOS] = json }
    }

    suspend fun setLogLevel(level: Int) {
        context.dataStore.edit { it[Keys.LOG_LEVEL] = level }
    }

    suspend fun saveBookmarksJson(json: String) {
        context.dataStore.edit { it[Keys.BOOKMARKS] = json }
    }

    suspend fun clear() { context.dataStore.edit { it.clear() } }
}
