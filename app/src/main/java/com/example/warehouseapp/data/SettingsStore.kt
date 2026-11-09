package com.example.warehouseapp.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Long = DEFAULT_ACCENT,
    val dynamicColor: Boolean = false,
) {
    companion object { const val DEFAULT_ACCENT = 0xFF1E5BD8 }
}

class SettingsStore(private val context: Context) {
    private val THEME = stringPreferencesKey("theme_mode")
    private val ACCENT = longPreferencesKey("accent")
    private val DYNAMIC = booleanPreferencesKey("dynamic_color")

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            accent = p[ACCENT] ?: AppSettings.DEFAULT_ACCENT,
            dynamicColor = p[DYNAMIC] ?: false,
        )
    }

    suspend fun setTheme(m: ThemeMode) = context.dataStore.edit { it[THEME] = m.name }
    suspend fun setAccent(argb: Long) = context.dataStore.edit { it[ACCENT] = argb }
    suspend fun setDynamic(v: Boolean) = context.dataStore.edit { it[DYNAMIC] = v }
    suspend fun reset() = context.dataStore.edit { it.clear() }
}
