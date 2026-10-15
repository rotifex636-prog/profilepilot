package com.shani.profilepilot

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash reporter (v1.2 diagnostic).
 *
 * If the app crashes, the full stack trace + device info is written to
 * files/crash_last.txt BEFORE the app dies. On the next launch,
 * SplashActivity shows the report with a Copy button so Shani can paste
 * it to Jack — no logcat needed.
 */
object CrashReporter {

    private const val FILE = "crash_last.txt"

    fun install(context: android.content.Context) {
        val app = context.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val info = buildString {
                    append("Time: ")
                        .append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                        .append("\n")
                    append("Thread: ").append(t.name).append("\n")
                    append("Android: ").append(android.os.Build.VERSION.RELEASE)
                        .append(" (SDK ").append(android.os.Build.VERSION.SDK_INT).append(")\n")
                    append("Device: ").append(android.os.Build.MANUFACTURER)
                        .append(" ").append(android.os.Build.MODEL).append("\n")
                    val pkg = app.packageManager.getPackageInfo(app.packageName, 0)
                    append("App: ").append(pkg.versionName)
                        .append(" (").append(pkg.versionCode).append(")\n\n")
                    append(sw.toString())
                }
                File(app.filesDir, FILE).writeText(info)
            } catch (_: Exception) {
                // Never let the reporter itself crash the crash handling.
            }
            prev?.uncaughtException(t, e)
        }
    }

    fun readReport(context: android.content.Context): String? {
        val f = File(context.filesDir, FILE)
        return if (f.exists()) f.readText() else null
    }

    fun clear(context: android.content.Context) {
        try {
            File(context.filesDir, FILE).delete()
        } catch (_: Exception) {
        }
    }
}


