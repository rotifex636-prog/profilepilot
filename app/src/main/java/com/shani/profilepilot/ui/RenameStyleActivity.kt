package com.shani.profilepilot.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.shani.profilepilot.databinding.ActivityRenameStyleBinding
import com.shani.profilepilot.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RenameStyleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRenameStyleBinding
    private var accountId: Long = -1

    private val swatchColors = listOf(
        "#1877F2", "#22C55E", "#EF4444", "#F59E0B",
        "#8B5CF6", "#EC4899", "#14B8A6", "#6B7280"
    )
    private val emojis = listOf("😀", "😎", "🔥", "⭐", "💼", "👨‍👩‍👧", "💰", "🎮", "📱", "🚀")

    private var selectedColor = "#1877F2"
    private var selectedEmoji = "😀"

    private val swatchButtons = mutableListOf<MaterialButton>()
    private val emojiViews = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRenameStyleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        accountId = intent.getLongExtra("account_id", -1)
        if (accountId == -1L) {
            finish()
            return
        }

        buildSwatches()
        buildEmojis()

        lifecycleScope.launch {
            val account = withContext(Dispatchers.IO) {
                AppDatabase.get(this@RenameStyleActivity).accountDao().byId(accountId)
            }
            if (account == null) {
                finish()
                return@launch
            }
            binding.nameInput.setText(account.name)
            if (swatchColors.contains(account.colorHex)) selectColor(account.colorHex)
            if (emojis.contains(account.emoji)) selectEmoji(account.emoji)
        }

        binding.saveButton.setOnClickListener {
            val name = binding.nameInput.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Naam likho", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.get(this@RenameStyleActivity)
                    val account = db.accountDao().byId(accountId)
                    if (account != null) {
                        db.accountDao().update(
                            account.copy(
                                name = name,
                                colorHex = selectedColor,
                                emoji = selectedEmoji
                            )
                        )
                    }
                }
                Toast.makeText(this@RenameStyleActivity, "Save ho gaya ✓", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun buildSwatches() {
        val d = resources.displayMetrics.density
        swatchColors.forEach { hex ->
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            val size = (40 * d).toInt()
            btn.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = (10 * d).toInt()
            }
            // Circular swatch: programmatic oval drawable tinted with the color
            // (avoids depending on a scaffold @drawable/swatch_bg).
            btn.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(hex))
            }
            btn.backgroundTintList = null
            btn.strokeWidth = 0
            btn.text = ""
            btn.insetTop = 0
            btn.insetBottom = 0
            btn.setOnClickListener { selectColor(hex) }
            swatchButtons.add(btn)
            binding.swatchRow.addView(btn)
        }
    }

    private fun selectColor(hex: String) {
        selectedColor = hex
        val d = resources.displayMetrics.density
        swatchButtons.forEachIndexed { i, btn ->
            val isSel = swatchColors[i] == hex
            btn.strokeWidth = if (isSel) (3 * d).toInt() else 0
            btn.strokeColor = ColorStateList.valueOf(Color.WHITE)
        }
    }

    private fun buildEmojis() {
        val d = resources.displayMetrics.density
        emojis.forEach { e ->
            val tv = TextView(this).apply {
                text = e
                textSize = 26f
                gravity = Gravity.CENTER
                val size = (52 * d).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size)
                setOnClickListener { selectEmoji(e) }
            }
            emojiViews.add(tv)
            binding.emojiRow.addView(tv)
        }
    }

    private fun selectEmoji(e: String) {
        selectedEmoji = e
        val d = resources.displayMetrics.density
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke((2 * d).toInt(), Color.WHITE)
            setColor(Color.TRANSPARENT)
        }
        emojiViews.forEachIndexed { i, tv ->
            tv.background = if (emojis[i] == e) ring else null
        }
    }
}


==========================================
