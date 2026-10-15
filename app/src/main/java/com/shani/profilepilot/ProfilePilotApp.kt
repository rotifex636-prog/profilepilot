package com.shani.profilepilot

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.shani.profilepilot.data.SettingsManager

class ProfilePilotApp : Application() {

    override fun onCreate() {
        // FIRST: install the crash reporter so any startup crash is captured.
        CrashReporter.install(this)
        super.onCreate()
        // Synchronous SharedPreferences read — never blocks, never crashes.
        // (v1.0 used DataStore + runBlocking here and died on launch.)
        val theme = try {
            SettingsManager.get(this).getTheme()
        } catch (_: Exception) {
            SettingsManager.THEME_DARK_PRO
        }
        AppCompatDelegate.setDefaultNightMode(
            if (theme == SettingsManager.THEME_DARK_PRO) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}


==========================================
