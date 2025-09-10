package com.meowwidget.gd1.ui.decor

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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

        // Preview card container (FrameLayout for future layers: bg -> frame -> text)
        val previewCard = FrameLayout(this).apply {
            setPadding(dp(16), dp(16), dp(16), dp(16))
            minimumHeight = dp(240) // roomy enough for future B5 frames
            background = roundedCard(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(20) }
        }

        // Layer 1: background color/image placeholder (hidden by default)
        val bgLayer = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }
        previewCard.addView(bgLayer)

        // Layer 2: border/frame placeholder (hidden by default)
        val borderLayer = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }
        previewCard.addView(borderLayer)

        // Layer 3: text content
        val previewQuote = TextView(this).apply {
            text = "Meow preview — câu ví dụ hiển thị ở widget"
            setTextColor(0xFF111111.toInt()) // default text color
            textSize = 18f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        previewCard.addView(previewQuote)

        // Action row (align end) with APPLY button styled like system
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val applyBtn = TextView(this).apply {
            text = "ÁP DỤNG" // uppercase label
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(20), dp(10), dp(20), dp(10))
            background = pill(0xFF2F80ED.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
            setOnClickListener {
                // B4.0: skeleton only — no persistence yet
                finish() // will wire save + broadcast in B4.4
            }
        }
        actionRow.addView(applyBtn)

        // Build tree
        content.addView(header)
        content.addView(titlePreview)
        content.addView(previewCard)
        content.addView(actionRow)
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

    private fun roundedCard(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(12).toFloat()
        setColor(color)
    }
}
