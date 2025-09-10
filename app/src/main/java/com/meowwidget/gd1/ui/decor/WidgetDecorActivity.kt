package com.meowwidget.gd1.ui.decor

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.meowwidget.gd1.R

class WidgetDecorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root screen uses the same background as the system screen
        val root = ScrollView(this).apply {
            setBackgroundResource(R.drawable.bg_settings_cotton)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Header — match look/feel of the system screen
        val header = TextView(this).apply {
            text = "Trang trí Widget"
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(16), dp(14), dp(16), dp(14))
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(0xFF2F80ED.toInt()) // system blue tone
            }
            if (Build.VERSION.SDK_INT >= 21) elevation = dp(2).toFloat()
        }

        // Section title: Preview
        val titlePreview = TextView(this).apply {
            text = "Preview"
            setTextColor(0xFF111111.toInt())
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(18), 0, dp(8))
        }

        // Preview card container (rounded)
        val previewCard = LinearLayout(this).apply {
            
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(0xFFFFFFFF.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(20) }
        }

        // Preview text
        val previewQuote = TextView(this).apply {
            
            text = "Meow preview — câu ví dụ hiển thị ở widget"
            setTextColor(0xFF111111.toInt()) // default text color
            textSize = 18f
            gravity = Gravity.CENTER
        }
        previewCard.addView(previewQuote)

        // Apply button (disabled logic for now — will wire in B4.4)
        val applyBtn = TextView(this).apply {
            text = "Áp dụng"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(12), dp(20), dp(12))
            background = pill(bgColor = 0xFF2F80ED.toInt())
            setOnClickListener {
                // B4.0: skeleton only — no persistence yet
                finish() // close screen for now; will wire save + broadcast in B4.4
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }

        // Build tree
        content.addView(header)
        content.addView(titlePreview)
        content.addView(previewCard)
        content.addView(applyBtn)
        root.addView(content)
        setContentView(root)
    }

    // Helpers
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun pill(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(26).toFloat()
        setColor(bgColor)
    }
}
