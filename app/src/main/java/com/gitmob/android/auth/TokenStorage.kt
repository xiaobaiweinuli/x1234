package com.gitmob.android.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gitmob_prefs")

class TokenStorage(private val context: Context) {
    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val USER_LOGIN   = stringPreferencesKey("user_login")
        val USER_NAME    = stringPreferencesKey("user_name")
        val AVATAR_URL   = stringPreferencesKey("avatar_url")
    }

    val accessToken: Flow<String?> = context.dataStore.data.map { it[Keys.ACCESS_TOKEN] }
    val userLogin: Flow<String?> = context.dataStore.data.map { it[Keys.USER_LOGIN] }
    val userProfile: Flow<Triple<String, String, String>?> = context.dataStore.data.map { prefs ->
        val login  = prefs[Keys.USER_LOGIN] ?: return@map null
        Triple(login, prefs[Keys.USER_NAME] ?: login, prefs[Keys.AVATAR_URL] ?: "")
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
    suspend fun clear() { context.dataStore.edit { it.clear() } }
}
