package com.meowwidget.gd1.ui.decor

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.Rect
import android.graphics.drawable.NinePatchDrawable
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
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.meowwidget.gd1.MeowQuoteWidget
import com.meowwidget.gd1.R

class WidgetDecorActivity : AppCompatActivity() {

    // === SharedPreferences keys (same pref file as widget) ===
    private val PREF = "meow_settings"
    private val KEY_DECOR_FONT = "decor_font"                 // "sans" | "serif"
    private val KEY_DECOR_TEXT_COLOR = "decor_text_color"     // Int (ARGB)
    private val KEY_DECOR_BORDER_STYLE = "decor_border_style" // "none"|"square"|"round"|"pill"
    private val KEY_DECOR_BORDER_WIDTH = "decor_border_width" // Int dp (2|4)
    private val KEY_DECOR_BORDER_COLOR = "decor_border_color" // Int (ARGB)
    private val KEY_DECOR_BG_COLOR = "decor_bg_color"         // Int (ARGB) or -1 = transparent
    private val KEY_DECOR_ICON = "decor_icon_key"
    private val KEY_DECOR_FRAME = "decor_frame_key"

    // ===== B5 (Decor Preview only in bước 1) =====
    private val KEY_DECOR_BG_MODE  = "decor_bg_mode"   // "none" | "image"
    private val KEY_DECOR_BG_IMAGE = "decor_bg_image"  // "bg_01".."bg_11"

    // Trạng thái chọn nền ảnh (chỉ preview)
    private var selectedBgKey: String? = null
    private var selectedBgThumb: ImageView? = null

    // ===== B5.2: Icon (preview only; chưa lưu) =====
    private var selectedIconKey: String? = null
    private var selectedIconThumb: ImageView? = null
    // [MEOW_FRAME] state — khung hình
private var selectedFrameSlug: String? = null
private var selectedFrameThumb: ImageView? = null


    // Preview selection state (highlight only; not persisted in B4.x)
    private var selectedFontBtn: TextView? = null
    private var selectedTextColorBtn: TextView? = null

    private var selectedBorderStyleBtn: TextView? = null
    private var selectedBorderWidthBtn: TextView? = null
    private var selectedBorderColorBtn: TextView? = null

    private var selectedBgBtn: TextView? = null

    // Current preview values
    private var fontFamily: String = "sans"
    private var textColor: Int = 0xFF111111.toInt()
    private var borderStyle: String = "none" // none | square | round | pill
    private var borderWidthDp: Int = 2       // 2 or 4
    private var borderColor: Int = 0xFF111111.toInt()
    private var bgColorOrNull: Int? = null   // null = transparent

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
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(18), 0, dp(8))
        }

        // Preview card — "border is max": no outer card background
        val previewCard = FrameLayout(this).apply {
            setPadding(0, 0, 0, 0) // border sits at edge
            minimumHeight = dp(240)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(20) }
        }

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
        // B5 foundation: frame image layer (inside border)
        val frameImageLayer = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_XY
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            visibility = View.GONE // default hidden; will be used in B5
        }
        updateFrameImageClip(frameImageLayer)
// [MEOW_FRAME] khung hình (9‑patch) — dưới chữ, trên nền
val frameOverlayLayer = View(this).apply {
    layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
    )
    visibility = View.GONE
}
        
        // Content layer (text)
        val contentLayer = FrameLayout(this).apply {
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val previewQuote = TextView(this).apply {
            text = "Đừng so sánh với người khác, hãy so sánh với chính mình của ngày hôm qua"
            setTextColor(textColor)
            textSize = 18f
            gravity = Gravity.CENTER
            typeface = Typeface.SANS_SERIF
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        contentLayer.addView(previewQuote)
        // B5 foundation: icon layer (top-most, defaults hidden)
        val iconLayer = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(32), dp(32), Gravity.TOP or Gravity.END
            ).apply {
                topMargin = dp(0)
                rightMargin = dp(16)
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            visibility = View.GONE // default hidden; will be used in B5
        }

        // Add in stacking order: bg -> border -> frameImage -> content -> icon
        previewCard.addView(bgLayer)
        previewCard.addView(frameImageLayer)
        previewCard.addView(borderLayer)
        previewCard.addView(frameOverlayLayer)
        previewCard.addView(contentLayer)
        previewCard.addView(iconLayer)

        // ===== B4.1: Kiểu chữ & Màu chữ (Preview ONLY) =====

        val titleFont = TextView(this).apply {
            text = "Kiểu chữ"
            setTextColor(0xFF111111.toInt())
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(6))
        }
        val fontRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnSans = outlineButton("SANS-SERIF")
        val btnSerif = outlineButton("SERIF")
        setButtonSelected(btnSans, true)
        selectedFontBtn = btnSans
        previewQuote.typeface = Typeface.SANS_SERIF
        previewQuote.setTypeface(previewQuote.typeface, Typeface.BOLD)
        fontFamily = "sans"
        btnSans.setOnClickListener {
            if (selectedFontBtn !== btnSans) {
                setButtonSelected(selectedFontBtn, false)
                setButtonSelected(btnSans, true)
                selectedFontBtn = btnSans
                previewQuote.typeface = Typeface.SANS_SERIF
                previewQuote.setTypeface(previewQuote.typeface, Typeface.BOLD)
                fontFamily = "sans"
            }
        }
        btnSerif.setOnClickListener {
            if (selectedFontBtn !== btnSerif) {
                setButtonSelected(selectedFontBtn, false)
                setButtonSelected(btnSerif, true)
                selectedFontBtn = btnSerif
                previewQuote.typeface = Typeface.SERIF
                previewQuote.setTypeface(previewQuote.typeface, Typeface.BOLD)
                fontFamily = "serif"
            }
        }
        fontRow.addView(btnSans)
        fontRow.addView(spaceH(dp(10)))
        fontRow.addView(btnSerif)
        val fontScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        fontScroll.addView(fontRow)

        val titleColor = TextView(this).apply {
            text = "Màu chữ"
            setTextColor(0xFF111111.toInt())
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(14), 0, dp(6))
        }
        val colorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
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
                setButtonSelected(b, true)
                selectedTextColorBtn = b
            }
            b.setOnClickListener {
                if (selectedTextColorBtn !== b) {
                    setButtonSelected(selectedTextColorBtn, false)
                    setButtonSelected(b, true)
                    selectedTextColorBtn = b
                }
                textColor = opt.value
                previewQuote.setTextColor(textColor)
            }
            colorRow.addView(b)
            if (idx != colors.size - 1) colorRow.addView(spaceH(dp(8)))
        }
        val colorScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        colorScroll.addView(colorRow)

        // ===== B4.2: Viền khung (Preview ONLY) =====

        val titleBorder = TextView(this).apply {
            text = "Viền khung"
            setTextColor(0xFF111111.toInt())
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(14), 0, dp(6))
        }

        // Row: Style
        val styleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnStyleNone = outlineButton("KHÔNG")
        val btnStyleSquare = outlineButton("VUÔNG")
        val btnStyleRound = outlineButton("BO GÓC")
        val btnStylePill = outlineButton("BO TRÒN")
        setButtonSelected(btnStyleNone, true)
        selectedBorderStyleBtn = btnStyleNone
        borderStyle = "none"
        updateBorder(borderLayer)
        updateFrameImageClip(frameImageLayer)
        updateBackground(bgLayer) // keep bg radius in sync
        btnStyleNone.setOnClickListener {
            if (selectedBorderStyleBtn !== btnStyleNone) {
                setButtonSelected(selectedBorderStyleBtn, false)
                setButtonSelected(btnStyleNone, true)
                selectedBorderStyleBtn = btnStyleNone
                borderStyle = "none"
                updateBorder(borderLayer)
        updateFrameImageClip(frameImageLayer)
                updateBackground(bgLayer)
            }
        }
        btnStyleSquare.setOnClickListener {
            if (selectedBorderStyleBtn !== btnStyleSquare) {
                setButtonSelected(selectedBorderStyleBtn, false)
                setButtonSelected(btnStyleSquare, true)
                selectedBorderStyleBtn = btnStyleSquare
                borderStyle = "square"
                updateBorder(borderLayer)
        updateFrameImageClip(frameImageLayer)
                updateBackground(bgLayer)
            }
        }
        btnStyleRound.setOnClickListener {
            if (selectedBorderStyleBtn !== btnStyleRound) {
                setButtonSelected(selectedBorderStyleBtn, false)
                setButtonSelected(btnStyleRound, true)
                selectedBorderStyleBtn = btnStyleRound
                borderStyle = "round"
                updateBorder(borderLayer)
        updateFrameImageClip(frameImageLayer)
                updateBackground(bgLayer)
            }
        }
        btnStylePill.setOnClickListener {
            if (selectedBorderStyleBtn !== btnStylePill) {
                setButtonSelected(selectedBorderStyleBtn, false)
                setButtonSelected(btnStylePill, true)
                selectedBorderStyleBtn = btnStylePill
                borderStyle = "pill"
                updateBorder(borderLayer)
        updateFrameImageClip(frameImageLayer)
                updateBackground(bgLayer)
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

        // Row: Width (MỎNG / DÀY) — 12dp spacing above
        val widthRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        val btnThin = outlineButton("MỎNG")
        val btnThick = outlineButton("DÀY")
        setButtonSelected(btnThin, true)
        selectedBorderWidthBtn = btnThin
        borderWidthDp = 2
        updateBorder(borderLayer)
        updateFrameImageClip(frameImageLayer)
        btnThin.setOnClickListener {
            if (selectedBorderWidthBtn !== btnThin) {
                setButtonSelected(selectedBorderWidthBtn, false)
                setButtonSelected(btnThin, true)
                selectedBorderWidthBtn = btnThin
                borderWidthDp = 2
                updateBorder(borderLayer)
        updateFrameImageClip(frameImageLayer)
            }
        }
        btnThick.setOnClickListener {
            if (selectedBorderWidthBtn !== btnThick) {
                setButtonSelected(selectedBorderWidthBtn, false)
                setButtonSelected(btnThick, true)
                selectedBorderWidthBtn = btnThick
                borderWidthDp = 4
                updateBorder(borderLayer)
        updateFrameImageClip(frameImageLayer)
            }
        }
        widthRow.addView(btnThin)
        widthRow.addView(spaceH(dp(8)))
        widthRow.addView(btnThick)

        // Row: Border color (reuse text palette) — 12dp spacing from widthRow
        val borderColorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        colors.forEachIndexed { idx, opt ->
            val b = outlineButton(opt.name)
            if (idx == 0) {
                setButtonSelected(b, true)
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
                updateBorder(borderLayer)
        updateFrameImageClip(frameImageLayer)
            }
            borderColorRow.addView(b)
            if (idx != colors.size - 1) borderColorRow.addView(spaceH(dp(8)))
        }
        val borderColorScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        borderColorScroll.addView(borderColorRow)

        // ===== B4.3: Nền (Preview ONLY) =====
        val titleBg = TextView(this).apply {
            text = "Nền"
            setTextColor(0xFF111111.toInt())
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(14), 0, dp(6))
        }

        data class BgOpt(val name: String, val color: Int, val isTransparent: Boolean = false)
        val bgOpts = listOf(
            BgOpt("TRONG SUỐT", 0x00000000, true),
            BgOpt("KEM ẤM", 0xFFFFF8E1.toInt()),
            BgOpt("BE XÁM", 0xFFF5F5F7.toInt()),
            BgOpt("HỒNG NHẠT", 0xFFFCE4EC.toInt()),
            BgOpt("XANH MINT", 0xFFE6F7F2.toInt()),
            BgOpt("XANH THAN", 0xFF263238.toInt())
        )

        val bgRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bgOpts.forEachIndexed { idx, opt ->
            val b = outlineButton(opt.name)
            if (idx == 0) {
                setButtonSelected(b, true) // default: transparent
                selectedBgBtn = b
                bgColorOrNull = null
                bgLayer.visibility = View.GONE
            }
            b.setOnClickListener {
                if (selectedBgBtn !== b) {
                    setButtonSelected(selectedBgBtn, false)
                    setButtonSelected(b, true)
                    selectedBgBtn = b
                }
                if (opt.isTransparent) {
                    bgColorOrNull = null
                    bgLayer.visibility = View.GONE
                } else {
                    bgColorOrNull = opt.color
                    updateBackground(bgLayer)
                }
            }
            bgRow.addView(b)
            if (idx != bgOpts.size - 1) bgRow.addView(spaceH(dp(8)))
        }
        val bgScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        bgScroll.addView(bgRow)

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
            text = "ÁP DỤNG 🐾"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(20), dp(10), dp(20), dp(10))
            background = pill(0xFF2F80ED.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(24) }
            setOnClickListener {
                // === B4.4: Save selections & broadcast update, stay on screen ===
                val sp = getSharedPreferences(PREF, MODE_PRIVATE)
                val editor = sp.edit()
                    .putString(KEY_DECOR_FONT, fontFamily)
                    .putInt(KEY_DECOR_TEXT_COLOR, textColor)
                    .putString(KEY_DECOR_BORDER_STYLE, borderStyle)
                    .putInt(KEY_DECOR_BORDER_WIDTH, borderWidthDp)
                    .putInt(KEY_DECOR_BORDER_COLOR, borderColor)
                    .putInt(KEY_DECOR_BG_COLOR, bgColorOrNull ?: -1) // -1 = transparent
                // B5: Lưu nền ảnh theo trạng thái + fallback
                run {
                    val currentKey = sp.getString(KEY_DECOR_BG_IMAGE, null)
                    val mode = if (frameImageLayer.visibility == View.VISIBLE) "image" else "none"
                    val keyToSave = selectedBgKey ?: currentKey
                    if (mode == "image" && keyToSave != null) {
                        editor.putString(KEY_DECOR_BG_MODE, "image")
                        editor.putString(KEY_DECOR_BG_IMAGE, keyToSave)
                    } else {
                        editor.putString(KEY_DECOR_BG_MODE, "none")
                        editor.remove(KEY_DECOR_BG_IMAGE)
                    }
                }
                                // B5.X: persist icon key for widget
                if (selectedIconKey != null) {
                    editor.putString(KEY_DECOR_ICON, selectedIconKey)
                } else {
                    editor.remove(KEY_DECOR_ICON)
                }
// [MEOW_FRAME] persist frame slug
run {
    val slug = selectedFrameSlug?.trim()
    if (slug.isNullOrEmpty()) {
        editor.remove(KEY_DECOR_FRAME)
    } else {
        editor.putString(KEY_DECOR_FRAME, slug)
    }
}                
editor.apply()

                // Broadcast widget update
                val mgr = AppWidgetManager.getInstance(this@WidgetDecorActivity)
                val ids = mgr.getAppWidgetIds(
                    ComponentName(this@WidgetDecorActivity, MeowQuoteWidget::class.java)
                )
                sendBroadcast(Intent(this@WidgetDecorActivity, MeowQuoteWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                })

                // Toast and remain on this screen
                Toast.makeText(this@WidgetDecorActivity, "Widget đã hoàn thành", Toast.LENGTH_SHORT).show()
            }
        }
        actionRow.addView(applyBtn)

        // Build tree — ensure visual spacing order
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

        content.addView(titleBg)
        content.addView(bgScroll)

        // ===== B5.1: Nền ảnh (Preview ONLY; chưa lưu) =====
        val titleBgImage = TextView(this).apply {
            text = "Nền ảnh"
            setTextColor(0xFF111111.toInt())
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(14), 0, dp(6))
        }
        val thumbScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val thumbRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        // Nút KHÔNG ẢNH: bỏ nền ảnh, chỉ dùng màu nền ở trên
        val btnNoImage = outlineButton("KHÔNG ẢNH").apply {
            setOnClickListener {
                selectedBgThumb?.background = outline(0x00000000, 0xFFBDBDBD.toInt(), 1)
                selectedBgThumb = null
                selectedBgKey = null
                frameImageLayer.setImageDrawable(null)
                frameImageLayer.visibility = View.GONE
                updateFrameImageClip(frameImageLayer)
            }
        }
        thumbRow.addView(btnNoImage)
        thumbRow.addView(spaceH(dp(8)))

        // Tạo 11 thumbnail theo mẫu tên "bg_01_thumb" .. "bg_11_thumb"; preview dùng "bg_XX_full"
        // B5-auto: tự nhận thumbnail "bg_XX_thumb" liền mạch từ 01; preview dùng "bg_XX_full"
        run {
            var i = 1
            while (true) {
                val key = "bg_%02d".format(i)
                val thumbName = key + "_thumb"
                val fullName = key + "_full"
                val thumbId = resources.getIdentifier(thumbName, "drawable", packageName)
                val fullId = resources.getIdentifier(fullName, "drawable", packageName)
                if (thumbId == 0) break
                val iv = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply { rightMargin = dp(8) }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageResource(thumbId)
                    background = outline(0x00000000, 0xFFBDBDBD.toInt(), 1)
                    setOnClickListener { v ->
                        val self = v as ImageView
                        // Bỏ viền cũ
                        selectedBgThumb?.background = outline(0x00000000, 0xFFBDBDBD.toInt(), 1)
                        // Chọn mới
                        selectedBgThumb = self
                        selectedBgKey = key
                        self.background = outline(0x00000000, 0xFF2F80ED.toInt(), 2)
                        // Đổi xem trước bằng ảnh full
                        if (fullId != 0) {
                            frameImageLayer.setImageResource(fullId)
                            frameImageLayer.scaleType = ImageView.ScaleType.CENTER_CROP
                            frameImageLayer.visibility = View.VISIBLE
                            updateFrameImageClip(frameImageLayer)
                        }
                    }
                }
                thumbRow.addView(iv)
                i++
            }
        }
        thumbScroll.addView(thumbRow)

        content.addView(titleBgImage)
        content.addView(thumbScroll)
        // ===== B6: Khung hình (nâng cao) =====
val titleFrame = TextView(this).apply {
    text = "Khung hình (nâng cao)"
    setTextColor(0xFF111111.toInt())
    textSize = 20f
    typeface = Typeface.DEFAULT_BOLD
    setPadding(0, dp(14), 0, dp(6))
}
val frameScroll = HorizontalScrollView(this).apply {
    isHorizontalScrollBarEnabled = false
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
val frameRow = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
// Ô đầu: KHÔNG KHUNG HÌNH
run {
    val b = outlineButton("KHÔNG KHUNG").apply {
        setOnClickListener {
            // bỏ chọn mới
            selectedFrameThumb?.background = outline(0x00000000, 0xFFBDBDBD.toInt(), 1)
            selectedFrameThumb = null
            selectedFrameSlug = null

            // ẩn khung hình, khôi phục viền stroke cũ + padding mặc định
            frameOverlayLayer.background = null
            frameOverlayLayer.visibility = View.GONE
            updateBorder(borderLayer)
            contentLayer.setPadding(dp(16), dp(16), dp(16), dp(16))
        }
    }
    frameRow.addView(b)
    frameRow.addView(spaceH(dp(8)))
}
// Autoscan: quét mọi drawable tên "frame_<slug>_thumb" và tồn tại "frame_<slug>"
run {
    val slugs = scanFrameSlugs()
    slugs.forEach { slug ->
        val thumbName = "frame_${slug}" + "_thumb"
        val thumbId = resources.getIdentifier(thumbName, "drawable", packageName)
        if (thumbId != 0) {
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply { rightMargin = dp(8) }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(thumbId)
                background = outline(0x00000000, 0xFFBDBDBD.toInt(), 1)
                setOnClickListener { v ->
                    val self = v as ImageView
                    // bỏ viền cũ
                    selectedFrameThumb?.background = outline(0x00000000, 0xFFBDBDBD.toInt(), 1)
                    // chọn mới
                    selectedFrameThumb = self
                    selectedFrameSlug = slug
                    self.background = outline(0x00000000, 0xFF2F80ED.toInt(), 2)

                    // ẩn stroke; hiển thị khung hình + padding theo 9‑patch
                    borderLayer.visibility = View.GONE
                    frameOverlayLayer.visibility = View.VISIBLE
                    applyFrameSelection(frameOverlayLayer, contentLayer, slug)
                }
            }
            frameRow.addView(iv)
        }
    }
}
frameScroll.addView(frameRow)

content.addView(titleFrame)
content.addView(frameScroll)


        
        // ===== B5.2: Icon trang trí (Preview ONLY; chưa lưu) =====
        val titleIcon = TextView(this).apply {
            text = "Icon trang trí"
            setTextColor(0xFF111111.toInt())
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(14), 0, dp(6))
        }
        val iconScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val iconRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        // Nút KHÔNG ICON: ẩn icon trong preview
        val btnNoIcon = outlineButton("KHÔNG ICON").apply {
            setOnClickListener {
                selectedIconThumb?.background = outline(0x00000000, 0xFFBDBDBD.toInt(), 1)
                selectedIconThumb = null
                selectedIconKey = null
                iconLayer.setImageDrawable(null)
                iconLayer.visibility = View.GONE
            }
        }
        iconRow.addView(btnNoIcon)
        iconRow.addView(spaceH(dp(8)))

        // Auto: quét icon_XX_thumb -> hiển thị preview bằng icon_XX
        run {
            var i = 1
            while (true) {
                val key = "icon_%02d".format(i)
                val thumbName = key + "_thumb"
                val fullName = key
                val thumbId = resources.getIdentifier(thumbName, "drawable", packageName)
                val fullId = resources.getIdentifier(fullName, "drawable", packageName)
                if (thumbId == 0) break
                val iv = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply { rightMargin = dp(8) }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageResource(thumbId)
                    background = outline(0x00000000, 0xFFBDBDBD.toInt(), 1)
                    setOnClickListener { v ->
                        val self = v as ImageView
                        selectedIconThumb?.background = outline(0x00000000, 0xFFBDBDBD.toInt(), 1)
                        selectedIconThumb = self
                        selectedIconKey = key
                        self.background = outline(0x00000000, 0xFF2F80ED.toInt(), 2)
                        if (fullId != 0) {
                            iconLayer.setImageResource(fullId)
                            iconLayer.visibility = View.VISIBLE
                        }
                    }
                }
                iconRow.addView(iv)
                i++
            }
        }
        iconScroll.addView(iconRow)

        content.addView(titleIcon)
        content.addView(iconScroll)

content.addView(actionRow)
run {
    val d = resources.displayMetrics.density
    fun dp(x: Float) = (x * d).toInt()

    // Spacer 32dp để CÁCH XA nút Áp dụng
    content.addView(
        android.view.View(this),
        android.view.ViewGroup.MarginLayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            dp(32f)
        )
    )

    // Divider 1dp mờ phía trên footer
    val divider = android.view.View(this).apply {
        setBackgroundColor(0x40000000.toInt()) // #40000000 (đen 25%)
    }
    content.addView(
        divider,
        android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            dp(1f)
        )
    )

    // Footer container: dọc, canh giữa
    val footer = android.widget.LinearLayout(this).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        gravity = android.view.Gravity.CENTER_HORIZONTAL
        setPadding(0, dp(12f), 0, dp(24f))
    }
    content.addView(
        footer,
        android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    )

    // Các dòng nội dung footer
    fun android.widget.TextView.sz(sp: Float) =
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp)

    android.widget.TextView(this).apply {
        text = "The Meow Widget"
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        sz(14f)
        footer.addView(this)
    }

    android.widget.TextView(this).apply {
        text = "By GL Rose & AI GPT Friend"
        sz(12f)
        footer.addView(this)
    }

    android.widget.TextView(this).apply {
        text = "08.2025 – 09.2025"
        sz(12f)
        footer.addView(this)
    }

    android.widget.TextView(this).apply {
        text = "v1.0 · © 2025"
        sz(12f)
        setPadding(0, 0, 0, dp(8f))
        footer.addView(this)
    }

    android.widget.TextView(this).apply {
        text = "Cảm ơn bạn đã dùng ứng dụng."
        sz(12f)
        footer.addView(this)
    }

    android.widget.TextView(this).apply {
        text = "Chúc một ngày “meow” thiệt vui! 🐾"
        sz(12f)
        footer.addView(this)
    }
}

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

    private fun styleRadius(): Float = when (borderStyle) {
        "square" -> dp(0).toFloat()
        "round" -> dp(12).toFloat()
        "pill" -> dp(26).toFloat()
        else -> dp(12).toFloat()
    }

    private fun updateBackground(bgLayer: View) {
        val c = bgColorOrNull
        if (c == null) {
            bgLayer.visibility = View.GONE
            return
        }
        val d = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = styleRadius()
            setColor(c)
        }
        bgLayer.background = d
        bgLayer.visibility = View.VISIBLE
    }


    // ===== B5: Clip ảnh theo bán kính viền (Decor Preview) =====
    private fun updateFrameImageClip(frameImageLayer: ImageView) {
        if (borderStyle == "none") {
            // Không viền: trả ảnh về góc vuông nguyên gốc
            frameImageLayer.background = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                frameImageLayer.clipToOutline = false
            }
            return
        }
        // Có viền: bo góc ảnh theo bán kính của viền
        val d = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = styleRadius()
            setColor(0x00000000)
        }
        frameImageLayer.background = d
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            frameImageLayer.clipToOutline = true
        }
    }

    private fun updateBorder(borderLayer: View) {
        if (borderStyle == "none") {
            borderLayer.visibility = View.GONE
            return
        }
        val d = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = styleRadius()
            setColor(0x00000000) // transparent fill
            setStroke(dp(borderWidthDp), borderColor)
        }
        borderLayer.background = d
        borderLayer.visibility = View.VISIBLE
    }

    // [MEOW_FRAME] START — helpers: autoscan, padding từ 9‑patch, áp preview
/** Quét R.drawable để tìm mọi frame_<slug>_thumb có cặp frame_<slug>. */
private fun scanFrameSlugs(): List<String> {
    val out = ArrayList<String>()
    try {
        val f = R.drawable::class.java.fields
        for (field in f) {
            val name = field.name ?: continue
            if (name.startsWith("frame_") && name.endsWith("_thumb")) {
                val slug = name.removePrefix("frame_").removeSuffix("_thumb")
                // xác minh có file 9‑patch tương ứng
                val idFull = resources.getIdentifier("frame_${slug}", "drawable", packageName)
                if (idFull != 0) out.add(slug)
            }
        }
    } catch (_: Exception) { }
    return out.sorted()
}

/** Tính padding theo content area của 9‑patch, scale theo kích thước previewCard hiện tại. */
private fun computeFramePaddingPx(slug: String, outW: Int, outH: Int): Rect? {
    val resId = resources.getIdentifier("frame_${slug}", "drawable", packageName)
    if (resId == 0) return null
    val dr = try { resources.getDrawable(resId, null) } catch (_: Exception) { null }
    if (dr !is NinePatchDrawable) return null

    val base = Rect()
    dr.getPadding(base)
    val iw = dr.intrinsicWidth.coerceAtLeast(1)
    val ih = dr.intrinsicHeight.coerceAtLeast(1)
    val sx = outW.toFloat() / iw
    val sy = outH.toFloat() / ih
    return Rect(
        (base.left * sx).toInt(),
        (base.top * sy).toInt(),
        (base.right * sx).toInt(),
        (base.bottom * sy).toInt()
    )
}

/** Áp dụng khung hình vào preview: set background 9‑patch + padding cho content. */
private fun applyFrameSelection(frameOverlayLayer: View, contentLayer: FrameLayout, slug: String) {
    val resId = resources.getIdentifier("frame_${slug}", "drawable", packageName)
    if (resId == 0) return
    // set background 9‑patch để vẽ đúng stretch
    try { frameOverlayLayer.setBackgroundResource(resId) } catch (_: Exception) { return }

    // Lấy size thực của preview theo layout hiện tại
    frameOverlayLayer.post {
        val w = frameOverlayLayer.width.coerceAtLeast(1)
        val h = frameOverlayLayer.height.coerceAtLeast(1)
        val pad = computeFramePaddingPx(slug, w, h) ?: return@post

        // Nếu đang có icon preview -> cộng thêm phần tránh icon (đúng thông số đã chốt)
        val hasIcon = (selectedIconKey != null)
        val d = resources.displayMetrics.density
        val extraEnd = if (hasIcon) (86f * d).toInt() else 0
        val extraStart = if (hasIcon) (16f * d).toInt() else 0
        val extraTop = if (hasIcon) (8f * d).toInt() else 0

        val left = pad.left + extraStart
        val top = pad.top + extraTop
        val right = pad.right + extraEnd
        val bottom = pad.bottom

        contentLayer.setPadding(left, top, right, bottom)
    }
}
}
