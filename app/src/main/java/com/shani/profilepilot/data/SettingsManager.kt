package com.shani.profilepilot.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * App-level preferences.
 *
 * Uses plain SharedPreferences (synchronous, bulletproof at startup).
 * NOTE: DataStore was deliberately removed — its async read inside
 * Application.onCreate caused instant crashes on some devices (v1.0 bug).
 * A single theme string does not need DataStore.
 */
class SettingsManager private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: SettingsManager? = null

        const val THEME_DARK_PRO = "dark_pro"
        const val THEME_LIGHT_CLEAN = "light_clean"

        fun get(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext)
                    .also { instance = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences("profilepilot_settings", Context.MODE_PRIVATE)

    private val themeState =
        MutableStateFlow(prefs.getString("theme", THEME_DARK_PRO) ?: THEME_DARK_PRO)

    /** "dark_pro" (default) or "light_clean" — matches the preview's theme switcher. */
    val themeFlow: Flow<String> = themeState

    fun getTheme(): String = themeState.value

    fun setTheme(v: String) {
        prefs.edit().putString("theme", v).apply()
        themeState.value = v
    }
}


==========================================
