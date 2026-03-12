package com.gitmob.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gitmob.android.auth.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { LIGHT, DARK, SYSTEM }

class ThemePreference(private val context: Context) {

    private val KEY = stringPreferencesKey("theme_mode")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[KEY]) {
            ThemeMode.DARK.name   -> ThemeMode.DARK
            ThemeMode.SYSTEM.name -> ThemeMode.SYSTEM
            else                  -> ThemeMode.LIGHT   // 默认浅色
        }
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[KEY] = mode.name }
    }
}
