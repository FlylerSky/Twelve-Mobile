package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.twelvePrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "twelve_settings")

@Singleton
class ThemePreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val themeKey = stringPreferencesKey("theme_mode")

    val themeMode: Flow<ThemeMode> = context.twelvePrefsDataStore.data.map { prefs ->
        ThemeMode.fromKey(prefs[themeKey])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.twelvePrefsDataStore.edit { prefs ->
            prefs[themeKey] = mode.storageKey
        }
    }
}
