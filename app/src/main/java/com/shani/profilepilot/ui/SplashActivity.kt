package com.shani.profilepilot.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shani.profilepilot.CrashReporter
import com.shani.profilepilot.databinding.ActivitySplashBinding

/**
 * Fullscreen splash (Theme.ProfilePilot.Splash — no action bar).
 * Plays a ~900ms scale+fade on the logo, then moves to Home after 2s.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // If the last launch crashed, show the report with a Copy button
        // so Shani can paste it to Jack — the issue comes right in front.
        CrashReporter.readReport(this)?.let { report ->
            MaterialAlertDialogBuilder(this)
                .setTitle("Crash report mil gayi")
                .setMessage(
                    "App pichli dafa is wajah se band hui thi. " +
                        "\"Copy\" dabao aur ye text Jack ko bhejo:\n\n" +
                        report.take(3500)
                )
                .setPositiveButton("Copy") { _, _ ->
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("crash", report))
                    Toast.makeText(this, "Copy ho gaya — Jack ko bhejo", Toast.LENGTH_LONG).show()
                    CrashReporter.clear(this)
                }
                .setNegativeButton("Band karo") { _, _ -> CrashReporter.clear(this) }
                .setCancelable(false)
                .show()
            return
        }

        val logo = binding.logoImage
        val scaleX = ObjectAnimator.ofFloat(logo, "scaleX", 0.7f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(logo, "scaleY", 0.7f, 1.0f)
        val fade = ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f)
        AnimatorSet().apply {
            playTogether(scaleX, scaleY, fade)
            duration = 900
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }, 2000)
    }
}


==========================================
