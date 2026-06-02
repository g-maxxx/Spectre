package dev.thomasbuilds.spectre.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val showStaleWifi: Boolean = false,
  val showStaleBluetooth: Boolean = false
) {
  companion object {
    val DEFAULTS = Settings()
  }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "spectre_settings")

class SettingsRepository(
  context: Context
) {
  private val store = context.applicationContext.dataStore

  val settings: Flow<Settings> =
    store.data.map { prefs ->
      Settings(
        themeMode =
          prefs[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: Settings.DEFAULTS.themeMode,
        showStaleWifi = prefs[KEY_SHOW_STALE_WIFI] ?: Settings.DEFAULTS.showStaleWifi,
        showStaleBluetooth = prefs[KEY_SHOW_STALE_BT] ?: Settings.DEFAULTS.showStaleBluetooth
      )
    }

  suspend fun setThemeMode(mode: ThemeMode) {
    store.edit { it[KEY_THEME] = mode.name }
  }

  suspend fun setShowStaleWifi(show: Boolean) {
    store.edit { it[KEY_SHOW_STALE_WIFI] = show }
  }

  suspend fun setShowStaleBluetooth(show: Boolean) {
    store.edit { it[KEY_SHOW_STALE_BT] = show }
  }

  suspend fun resetToDefaults() {
    store.edit { it.clear() }
  }

  private companion object {
    val KEY_THEME = stringPreferencesKey("theme_mode")
    val KEY_SHOW_STALE_WIFI = booleanPreferencesKey("show_stale_wifi")
    val KEY_SHOW_STALE_BT = booleanPreferencesKey("show_stale_bluetooth")
  }
}
