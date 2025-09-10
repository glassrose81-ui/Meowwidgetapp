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
import android.widget.HorizontalScrollView
import androidx.appcompat.app.AppCompatActivity
import com.meowwidget.gd1.R

class WidgetDecorActivity : AppCompatActivity() {

    // Preview selection state (highlight only; not persisted in B4.x)
    private var selectedFontBtn: TextView? = null
    private var selectedTextColorBtn: TextView? = null

    private var selectedBorderStyleBtn: TextView? = null
    private var selectedBorderWidthBtn: TextView? = null
    private var selectedBorderColorBtn: TextView? = null

    // Current preview values
    private var borderStyle: String = "none" // none | square | round | pill
    private var borderWidthDp: Int = 2       // 2 or 4
    private var borderColor: Int = 0xFF111111.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root like the system screen
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

        // Header — same look/feel as system screen
        val header = TextView(this).apply {
            text = "Trang trí Widget"
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(16), dp(14), dp(16), dp(14))
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(0xFF2F80ED.toInt()) // system blue
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

        // Preview card — FrameLayout for future layers (bg -> frame -> text)
        val previewCard = FrameLayout(this).apply {
            setPadding(dp(16), dp(16), dp(16), dp(16))
            minimumHeight = dp(240) // roomy enough for B5 later
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(20) }
        }

        // card background drawable so we can sync corner radius with border
        val cardBg = roundedCard(0xFFFFFFFF.toInt(), dp(12))
        previewCard.background = cardBg

        // Layers
        val bgLayer = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }
        val borderLayer = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }
        val previewQuote = TextView(this).apply {
            text = "Đừng so sánh với người khác, hãy so sánh với chính mình của ngày hôm qua"
            setTextColor(0xFF111111.toInt()) // default text color
            textSize = 18f
            gravity = Gravity.CENTER
            // default: SANS + BOLD to match real widget feel
            typeface = Typeface.SANS_SERIF
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        previewCard.addView(bgLayer)
        previewCard.addView(borderLayer)
        previewCard.addView(previewQuote)

        // ===== B4.1: Kiểu chữ & Màu chữ (Preview ONLY) =====

        // Title: Font
        val titleFont = TextView(this).apply {
            text = "Kiểu chữ"
            setTextColor(0xFF111111.toInt())
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(6))
        }

        val fontRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnSans = outlineButton("SANS-SERIF")
        val btnSerif = outlineButton("SERIF")

        // default selection: SANS
        setButtonSelected(btnSans, true)
        selectedFontBtn = btnSans
        previewQuote.typeface = Typeface.SANS_SERIF
        previewQuote.setTypeface(previewQuote.typeface, Typeface.BOLD)

        btnSans.setOnClickListener {
            if (selectedFontBtn !== btnSans) {
                setButtonSelected(selectedFontBtn, false)
                setButtonSelected(btnSans, true)
                selectedFontBtn = btnSans
                previewQuote.typeface = Typeface.SANS_SERIF
                previewQuote.setTypeface(previewQuote.typeface, Typeface.BOLD)
            }
        }
        btnSerif.setOnClickListener {
            if (selectedFontBtn !== btnSerif) {
                setButtonSelected(selectedFontBtn, false)
                setButtonSelected(btnSerif, true)
                selectedFontBtn = btnSerif
                previewQuote.typeface = Typeface.SERIF
                previewQuote.setTypeface(previewQuote.typeface, Typeface.BOLD)
            }
        }

        fontRow.addView(btnSans)
        fontRow.addView(spaceH(dp(10)))
        fontRow.addView(btnSerif)

        // Wrap fontRow to avoid squeezing on small screens
        val fontScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        fontScroll.addView(fontRow)

        // Title: Text color
        val titleColor = TextView(this).apply {
            text = "Màu chữ"
            setTextColor(0xFF111111.toInt())
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(14), 0, dp(6))
        }

        val colorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        data class ColorOpt(val name: String, val value: Int)

        val colors = listOf(
            ColorOpt("ĐEN", 0xFF111111.toInt()),
            ColorOpt("TRẮNG", 0xFFFFFFFF.toInt()),
            ColorOpt("XANH", 0xFF1E88E5.toInt()),
            ColorOpt("ĐỎ", 0xFFC62828.toInt()),
            ColorOpt("HỒNG", 0xFFF48FB1.toInt())
        )

        colors.forEachIndexed { idx, opt ->
            val b = outlineButton(opt.name)
            if (idx == 0) {
                setButtonSelected(b, true) // default select BLACK
                selectedTextColorBtn = b
            }
            b.setOnClickListener {
                if (selectedTextColorBtn !== b) {
                    setButtonSelected(selectedTextColorBtn, false)
                    setButtonSelected(b, true)
                    selectedTextColorBtn = b
                }
                previewQuote.setTextColor(opt.value)
            }
            colorRow.addView(b)
            if (idx != colors.size - 1) colorRow.addView(spaceH(dp(8)))
        }

        // Wrap colorRow to avoid squeezing on small screens
        val colorScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        colorScroll.addView(colorRow)

        // ===== B4.2: Viền khung (Preview ONLY) =====

        val titleBorder = TextView(this).apply {
            text = "Viền khung"
            setTextColor(0xFF111111.toInt())
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(14), 0, dp(6))
        }

        // Row: Style
        val styleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnStyleNone = outlineButton("KHÔNG")
        val btnStyleSquare = outlineButton("VUÔNG")
        val btnStyleRound = outlineButton("BO GÓC")
        val btnStylePill = outlineButton("BO TRÒN")

        // default: KHÔNG
        setButtonSelected(btnStyleNone, true)
        selectedBorderStyleBtn = btnStyleNone
        borderStyle = "none"
        updateBorder(borderLayer, cardBg)

        btnStyleNone.setOnClickListener {
            if (selectedBorderStyleBtn !== btnStyleNone) {
                setButtonSelected(selectedBorderStyleBtn, false)
                setButtonSelected(btnStyleNone, true)
                selectedBorderStyleBtn = btnStyleNone
                borderStyle = "none"
                updateBorder(borderLayer, cardBg)
            }
        }
        btnStyleSquare.setOnClickListener {
            if (selectedBorderStyleBtn !== btnStyleSquare) {
                setButtonSelected(selectedBorderStyleBtn, false)
                setButtonSelected(btnStyleSquare, true)
                selectedBorderStyleBtn = btnStyleSquare
                borderStyle = "square"
                updateBorder(borderLayer, cardBg)
            }
        }
        btnStyleRound.setOnClickListener {
            if (selectedBorderStyleBtn !== btnStyleRound) {
                setButtonSelected(selectedBorderStyleBtn, false)
                setButtonSelected(btnStyleRound, true)
                selectedBorderStyleBtn = btnStyleRound
                borderStyle = "round"
                updateBorder(borderLayer, cardBg)
            }
        }
        btnStylePill.setOnClickListener {
            if (selectedBorderStyleBtn !== btnStylePill) {
                setButtonSelected(selectedBorderStyleBtn, false)
                setButtonSelected(btnStylePill, true)
                selectedBorderStyleBtn = btnStylePill
                borderStyle = "pill"
                updateBorder(borderLayer, cardBg)
            }
        }

        styleRow.addView(btnStyleNone)
        styleRow.addView(spaceH(dp(8)))
        styleRow.addView(btnStyleSquare)
        styleRow.addView(spaceH(dp(8)))
        styleRow.addView(btnStyleRound)
        styleRow.addView(spaceH(dp(8)))
        styleRow.addView(btnStylePill)

        val styleScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        styleScroll.addView(styleRow)

        // Row: Width
        val widthRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnThin = outlineButton("MỎNG")
        val btnThick = outlineButton("DÀY")

        // default: MỎNG
        setButtonSelected(btnThin, true)
        selectedBorderWidthBtn = btnThin
        borderWidthDp = 2
        updateBorder(borderLayer, cardBg)

        btnThin.setOnClickListener {
            if (selectedBorderWidthBtn !== btnThin) {
                setButtonSelected(selectedBorderWidthBtn, false)
                setButtonSelected(btnThin, true)
                selectedBorderWidthBtn = btnThin
                borderWidthDp = 2
                updateBorder(borderLayer, cardBg)
            }
        }
        btnThick.setOnClickListener {
            if (selectedBorderWidthBtn !== btnThick) {
                setButtonSelected(selectedBorderWidthBtn, false)
                setButtonSelected(btnThick, true)
                selectedBorderWidthBtn = btnThick
                borderWidthDp = 4
                updateBorder(borderLayer, cardBg)
            }
        }

        // Row: Border color (reuse text palette)
        val borderColorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        colors.forEachIndexed { idx, opt ->
            val b = outlineButton(opt.name)
            if (idx == 0) {
                setButtonSelected(b, true) // default select BLACK
                selectedBorderColorBtn = b
                borderColor = opt.value
            }
            b.setOnClickListener {
                if (selectedBorderColorBtn !== b) {
                    setButtonSelected(selectedBorderColorBtn, false)
                    setButtonSelected(b, true)
                    selectedBorderColorBtn = b
                }
                borderColor = opt.value
                updateBorder(borderLayer, cardBg)
            }
            borderColorRow.addView(b)
            if (idx != colors.size - 1) borderColorRow.addView(spaceH(dp(8)))
        }

        val borderColorScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        borderColorScroll.addView(borderColorRow)

        // ===== Action row =====
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val applyBtn = TextView(this).apply {
            text = "ÁP DỤNG"
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
                // B4.x: preview only — persistence/wiring will be added in B4.4
                finish()
            }
        }
        actionRow.addView(applyBtn)

        // Build tree
        content.addView(header)
        content.addView(titlePreview)
        content.addView(previewCard)

        content.addView(titleFont)
        content.addView(fontScroll)

        content.addView(titleColor)
        content.addView(colorScroll)

        content.addView(titleBorder)
        content.addView(styleScroll)
        content.addView(widthRow)
        content.addView(borderColorScroll)

        content.addView(actionRow)

        root.addView(content)
        setContentView(root)
    }

    // ===== Helpers =====

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun spaceH(w: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(w, 1)
    }

    private fun pill(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(26).toFloat()
        setColor(bgColor)
    }

    private fun outline(bgColor: Int, strokeColor: Int, strokeDp: Int = 2): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(26).toFloat()
            setColor(bgColor)
            setStroke(dp(strokeDp), strokeColor)
        }

    private fun roundedCard(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius.toFloat()
        setColor(color)
    }

    private fun outlineButton(label: String): TextView =
        TextView(this).apply {
            text = label
            isAllCaps = true
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF2F80ED.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = outline(0x00000000, 0xFF2F80ED.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

    private fun setButtonSelected(btn: TextView?, selected: Boolean) {
        btn ?: return
        if (selected) {
            btn.setTextColor(0xFFFFFFFF.toInt())
            btn.background = pill(0xFF2F80ED.toInt())
        } else {
            btn.setTextColor(0xFF2F80ED.toInt())
            btn.background = outline(0x00000000, 0xFF2F80ED.toInt())
        }
    }

    private fun updateBorder(borderLayer: View, cardBg: GradientDrawable) {
        if (borderStyle == "none") {
            borderLayer.visibility = View.GONE
            // keep card radius default (rounded 12dp)
            cardBg.cornerRadius = dp(12).toFloat()
            return
        }
        val radius = when (borderStyle) {
            "square" -> dp(0)
            "round" -> dp(12)
            "pill" -> dp(26)
            else -> dp(12)
        }.toFloat()

        // sync card background radius with chosen style for consistent look
        cardBg.cornerRadius = radius

        val d = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(0x00000000) // transparent fill
            setStroke(dp(borderWidthDp), borderColor)
        }
        borderLayer.background = d
        borderLayer.visibility = View.VISIBLE
    }
}
