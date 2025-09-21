package com.meowwidget.gd1

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.graphics.BitmapFactory
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.NinePatchDrawable

class MeowQuoteWidget : AppWidgetProvider() {

    companion object {
        private const val PREF = "meow_settings"
        private const val KEY_SOURCE = "source"          // "all" | "fav"
        private const val KEY_SLOTS = "slots"            // "08:00,17:00,20:00"
        private const val KEY_ADDED = "added_lines"      // multi-line
        private const val KEY_FAVS = "favs"              // multi-line
        private const val KEY_PLAN_DAY = "plan_day"      // "ddMMyy"
        private const val KEY_PLAN_IDX = "plan_idx"      // Int
        private const val KEY_ANCHOR_DAY = "anchor_day"
        private const val KEY_ANCHOR_OFFSET = "anchor_offset"
        private const val ACTION_TICK = "com.meowwidget.gd1.ACTION_WIDGET_TICK"
        private const val ASSET_DEFAULT = "quotes_default.txt"

        private val DEFAULT_SLOTS = listOf(Pair(8, 0), Pair(17, 0), Pair(20, 0))

        // Cache nhẹ giúp mượt khi resize/tick
        @Volatile private var cachedDefault: List<String>? = null

        // Hysteresis & debounce theo từng widget
        private val lastSizeClass = ConcurrentHashMap<Int, Int>() // 0=small,1=medium,2=large
        private val lastUpdateMs = ConcurrentHashMap<Int, Long>()
        private val lastText = ConcurrentHashMap<Int, String>()
        private const val DEBOUNCE_MS = 400L
        private const val MARGIN_DP = 20 // đệm chống nhảy qua lại
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        for (id in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, id, null)
        }
        scheduleNextTick(context)
    }
override fun onEnabled(context: Context) {
    super.onEnabled(context)
    scheduleNextTick(context)
}

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (ACTION_TICK == intent.action) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, MeowQuoteWidget::class.java))
            for (id in ids) {
                updateSingleWidget(context, mgr, id, null)
            }
            
            scheduleNextTick(context)
        }
        else if (
    Intent.ACTION_TIME_CHANGED == intent.action ||
    Intent.ACTION_DATE_CHANGED == intent.action ||
    Intent.ACTION_TIMEZONE_CHANGED == intent.action ||
    Intent.ACTION_MY_PACKAGE_REPLACED == intent.action
) {
    scheduleNextTick(context)
    return
}

    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateSingleWidget(context, appWidgetManager, appWidgetId, newOptions)
        // không gọi scheduleNextTick ở đây để tránh trận mưa tick lúc kéo
    }

    // ====== Hiển thị 1 widget (tự co chữ 16/18/22 với hysteresis + debounce) ======
    private fun updateSingleWidget(context: Context, mgr: AppWidgetManager, widgetId: Int, options: Bundle?) {
        val nowMs = System.currentTimeMillis()
        val prev = lastUpdateMs[widgetId] ?: 0L
        if (nowMs - prev < DEBOUNCE_MS) return
        lastUpdateMs[widgetId] = nowMs

        val now = Calendar.getInstance()
        val quote = computeTodayQuote(context, now)

        // Quyết định cỡ chữ ổn định
        val heightDp = extractStableHeightDp(mgr, widgetId, options)
        val sizeClass = decideSizeClassWithHysteresis(widgetId, heightDp)
        val sp = when (sizeClass) {
            0 -> 16f
            1 -> 18f
            else -> 22f
        }
        // === B4.5: đọc lựa chọn trang trí & ước lượng kích thước nền/viền ===
        val decorSp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val decorTextColor = decorSp.getInt("decor_text_color", 0xFF111111.toInt())
        val borderStyle = decorSp.getString("decor_border_style", "none") ?: "none"
        val borderWidthDp = decorSp.getInt("decor_border_width", 2)
        val borderColor = decorSp.getInt("decor_border_color", 0xFF111111.toInt())
        val decorBgColor = decorSp.getInt("decor_bg_color", -1)
        val bgOrNull: Int? = if (decorBgColor == -1) null else decorBgColor
        val decorFont = decorSp.getString("decor_font", "sans") ?: "sans"
        val isSerif = decorFont == "serif"


        val opt = options ?: mgr.getAppWidgetOptions(widgetId)
        val minWdp = opt?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 120
        val minHdp = heightDp // đã ổn định theo extractStableHeightDp(...)
        val density = context.resources.displayMetrics.density
        val wPx = (minWdp * density).toInt().coerceAtLeast(1)
        val hPx = (minHdp * density).toInt().coerceAtLeast(1)


        val views = RemoteViews(context.packageName, R.layout.bocuc_meow).apply {
            setTextViewText(R.id.widget_text, quote)
            setTextViewTextSize(R.id.widget_text, TypedValue.COMPLEX_UNIT_SP, sp)

            // Font: đồng bộ nội dung/size/màu cho TextView serif và bật/tắt theo lựa chọn
            try { setTextViewText(R.id.widget_text_serif, quote) } catch (_: Exception) {}
            try { setTextViewTextSize(R.id.widget_text_serif, TypedValue.COMPLEX_UNIT_SP, sp) } catch (_: Exception) {}
            try { setTextColor(R.id.widget_text, decorTextColor) } catch (_: Exception) {}
            try { setTextColor(R.id.widget_text_serif, decorTextColor) } catch (_: Exception) {}
            try { setViewVisibility(R.id.widget_text, if (isSerif) View.GONE else View.VISIBLE) } catch (_: Exception) {}
            try { setViewVisibility(R.id.widget_text_serif, if (isSerif) View.VISIBLE else View.GONE) } catch (_: Exception) {}

            // === Padding to avoid overlay icon (only when icon present) ===
            try {
                val spPad = context.getSharedPreferences("meow_settings", Context.MODE_PRIVATE)
                val hasIconNow = !spPad.getString("decor_icon_key", null).isNullOrBlank()
                if (hasIconNow) {
                    val d = context.resources.displayMetrics.density
                    val padSide = (12f * d).toInt()
                    val padTop = (8f * d).toInt()
                    val padEnd = (86f * d).toInt()
                    try { setViewPadding(R.id.widget_text, padSide, padTop, padEnd, padSide) } catch (_: Exception) {}
                    try { setViewPadding(R.id.widget_text_serif, padSide, padTop, padEnd, padSide) } catch (_: Exception) {}
                } else {
                    val d = context.resources.displayMetrics.density
                    val side = (4f * d).toInt()
                    try { setViewPadding(R.id.widget_text, side, 0, side, side) } catch (_: Exception) {}
                    try { setViewPadding(R.id.widget_text_serif, side, 0, side, side) } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
            // [MEOW_FRAME] START — padding theo content area của khung hình (+ cộng thêm padEnd nếu có icon)
try {
    val spFrame = context.getSharedPreferences("meow_settings", Context.MODE_PRIVATE)
    val slug = spFrame.getString("decor_frame_key", null)?.trim()
    if (!slug.isNullOrEmpty() && !slug.equals("none", ignoreCase = true)) {
        val pad = computeFramePaddingPx(context, slug, wPx, hPx)
        if (pad != null) {
            // Cộng thêm padEnd khi có icon (đúng thông số: H=70dp, right=12dp, top=0; padEnd=86dp; padStart=16dp, padTop=8dp)
            val d = context.resources.displayMetrics.density
            val spPad = context.getSharedPreferences("meow_settings", Context.MODE_PRIVATE)
            val hasIconNow = !spPad.getString("decor_icon_key", null).isNullOrBlank()
            val extraEnd = if (hasIconNow) (86f * d).toInt() else 0
            val extraStart = if (hasIconNow) (16f * d).toInt() else 0
            val extraTop = if (hasIconNow) (8f * d).toInt() else 0

            val left = pad.left + extraStart
            val top = pad.top + extraTop
            val right = pad.right + extraEnd
            val bottom = pad.bottom

            try { setViewPadding(R.id.widget_text, left, top, right, bottom) } catch (_: Exception) {}
            try { setViewPadding(R.id.widget_text_serif, left, top, right, bottom) } catch (_: Exception) {}
        }
    }
} catch (_: Exception) { /* bỏ qua nếu thiếu 9‑patch */ }


            // Click: tạo 1 PendingIntent mở MeowSettingsActivity dùng chung cho sans & serif
            val clickIntent = Intent(context, MeowSettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            val clickFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val clickPi = PendingIntent.getActivity(context, 0, clickIntent, clickFlags)
            try { setOnClickPendingIntent(R.id.widget_text, clickPi) } catch (_: Exception) {}
            try { setOnClickPendingIntent(R.id.widget_text_serif, clickPi) } catch (_: Exception) {}

            // Áp màu chữ theo Trang trí
            try { setTextColor(R.id.widget_text, decorTextColor) } catch (_: Exception) {}
            // Áp bitmap nền/viền nếu có ImageView nền
            try {
                val bmp = buildDecorBitmap(context, wPx, hPx, borderStyle, borderWidthDp, borderColor, bgOrNull)
                setImageViewBitmap(R.id.widget_bg, bmp)

                // [MEOW_FRAME] START — overlay khung hình (nếu có) lên bitmap nền
try {
    overlayFrameIfAny(context, bmp, wPx, hPx)
    // vẽ lại vào widget_bg (ghi đè bức nền vừa set)
    setImageViewBitmap(R.id.widget_bg, bmp)
} catch (_: Exception) { /* an toàn: bỏ qua nếu thiếu tài nguyên */ }
            } catch (_: Exception) {
                // fallback: nếu thiếu widget_bg, chỉ áp nền phẳng (nếu có) lên TextView
                if (decorBgColor != -1) {
                    try { setInt(R.id.widget_text, "setBackgroundColor", decorBgColor) } catch (_: Exception) {}
                } else {
                    try { setInt(R.id.widget_text, "setBackgroundColor", 0x00000000) } catch (_: Exception) {}
                }
            }
            // Chạm -> mở MeowSettingsActivity
            val intent = Intent(context, MeowSettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getActivity(context, 0, intent, flags)
            setOnClickPendingIntent(R.id.widget_text, pi)
        }

        try {
            mgr.updateAppWidget(widgetId, views)
            lastText[widgetId] = quote
        } catch (_: Exception) {
            // Fallback an toàn
            val safe = lastText[widgetId] ?: quote
            val safeViews = RemoteViews(context.packageName, R.layout.bocuc_meow).apply {
                setTextViewText(R.id.widget_text, safe)
                setTextViewTextSize(R.id.widget_text, TypedValue.COMPLEX_UNIT_SP, 16f)
            }
            mgr.updateAppWidget(widgetId, safeViews)
        }
        
    }

    private fun extractStableHeightDp(mgr: AppWidgetManager, widgetId: Int, options: Bundle?): Int {
        val opt = options ?: mgr.getAppWidgetOptions(widgetId)
        // Ưu tiên MIN_HEIGHT (ổn định hơn khi người dùng đang kéo)
        val minH = opt?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
        return if (minH > 0) minH else 120
    }

    private fun decideSizeClassWithHysteresis(widgetId: Int, heightDp: Int): Int {
        // base thresholds
        val smallUp = 120
        val largeDown = 200
        val margin = MARGIN_DP

        val last = lastSizeClass[widgetId]
        val target = when {
            heightDp < smallUp -> 0  // small
            heightDp >= largeDown -> 2 // large
            else -> 1 // medium
        }
        if (last == null) {
            lastSizeClass[widgetId] = target
            return target
        }
        // Áp hysteresis để không nhảy qua lại khi ở gần ranh
        return when (last) {
            0 -> { // từ small lên medium nếu vượt smallUp + margin
                if (heightDp >= smallUp + margin) 1 else 0
            }
            1 -> {
                if (heightDp >= largeDown + margin) 2
                else if (heightDp < smallUp - margin) 0
                else 1
            }
            else -> { // from large xuống medium nếu rơi dưới largeDown - margin
                if (heightDp < largeDown - margin) 1 else 2
            }
        }.also { decided ->
            lastSizeClass[widgetId] = decided
        }
    }

    // ====== Tính "Câu hôm nay" (đồng bộ với Meow Settings) ======
    private fun computeTodayQuote(context: Context, now: Calendar): String {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val source = sp.getString(KEY_SOURCE, "all") ?: "all"
        val slotsString = sp.getString(KEY_SLOTS, "08:00,17:00,20:00") ?: "08:00,17:00,20:00"
        val addedRaw = sp.getString(KEY_ADDED, "") ?: ""
        val favRaw = sp.getString(KEY_FAVS, "") ?: ""

        val baseList = when (source) {
            "fav" -> toLines(favRaw)
            else  -> distinctPreserveOrder(loadDefaultCached(context) + toLines(addedRaw))
        }
        if (baseList.isEmpty()) return ""

        // --- App parity: anchor-day + offset (no per-day auto-increment)
        val slots = parseSlots(slotsString)
        val slotsPerDay = if (slots.isEmpty()) 1 else slots.size
        val slotIdxToday = currentSlotIndex(slotsString, now)

        // yyyyMMdd for 'today'
        val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
        val todayStr = sdf.format(now.time)
        val anchorDay = sp.getString(KEY_ANCHOR_DAY, null) ?: todayStr
        val anchorOffset = sp.getInt(KEY_ANCHOR_OFFSET, 0)

        fun daysBetween(a: String, b: String): Long {
            return try {
                val da = sdf.parse(a); val db = sdf.parse(b)
                val one = 24L * 60L * 60L * 1000L
                ((db!!.time / one) - (da!!.time / one))
            } catch (_: Exception) { 0L }
        }

        var days = daysBetween(anchorDay, todayStr)
        var steps = days * slotsPerDay + slotIdxToday

        // 0h fix: trước mốc đầu, coi như còn thuộc "hôm qua"
        if (slots.isNotEmpty()) {
            val first = slots.first()
            val firstMin = first.first * 60 + first.second
            val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            if (nowMin < firstMin) {
                steps -= 1L
            }
        }

        val idx = ((steps + anchorOffset).toInt() % baseList.size + baseList.size) % baseList.size
        return baseList[idx]
    }

    private fun loadDefaultCached(context: Context): List<String> {
        val cached = cachedDefault
        if (cached != null) return cached
        synchronized(this) {
            val again = cachedDefault
            if (again != null) return again
            val loaded = try {
                context.assets.open(ASSET_DEFAULT).use { input ->
                    BufferedReader(InputStreamReader(input)).readLines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                }
            } catch (_: Exception) {
                emptyList()
            }
            cachedDefault = loaded
            return loaded
        }
    }

    private fun toLines(raw: String): List<String> =
        raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    private fun distinctPreserveOrder(list: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        val out = ArrayList<String>(list.size)
        for (s in list) {
            val k = s.trim()
            if (k.isNotEmpty() && !seen.contains(k)) {
                seen.add(k)
                out.add(s)
            }
        }
        return out
    }

    private fun ensurePlanBase(sp: android.content.SharedPreferences, size: Int, now: Calendar): Int {
        val today = formatDay(now)
        val oldDay = sp.getString(KEY_PLAN_DAY, null)
        var base = max(0, sp.getInt(KEY_PLAN_IDX, -1))
        if (oldDay == null) {
            base = 0
        } else if (oldDay != today) {
            base = (base + 1) % max(1, size)
        }
        sp.edit().putString(KEY_PLAN_DAY, today).putInt(KEY_PLAN_IDX, base).apply()
        return base
    }

    private fun formatDay(now: Calendar): String {
        val d = now.get(Calendar.DAY_OF_MONTH)
        val m = now.get(Calendar.MONTH) + 1
        val y = now.get(Calendar.YEAR) % 100
        return String.format(Locale.getDefault(), "%02d%02d%02d", d, m, y)
    }

    // ====== Hẹn giờ mốc kế tiếp (nhẹ) ======
    private fun scheduleNextTick(context: Context) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val nextTime = nextSlotTimeMillis(sp.getString(KEY_SLOTS, "08:00,17:00,20:00") ?: "08:00,17:00,20:00")
        if (nextTime <= 0L) return

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MeowQuoteWidget::class.java).setAction(ACTION_TICK)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(context, 0, intent, flags)
        val whenMs = nextTime + 60_000L
        am.set(AlarmManager.RTC, whenMs, pi)

    }

    private fun nextSlotTimeMillis(slotsString: String): Long {
        val slots = parseSlots(slotsString)
        if (slots.isEmpty()) return 0L

        val now = Calendar.getInstance()
        var best: Calendar? = null
        for ((h, m) in slots) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
            }
            if (cal.timeInMillis <= now.timeInMillis) cal.add(Calendar.DAY_OF_YEAR, 1)
            if (best == null || cal.timeInMillis < best!!.timeInMillis) best = cal
        }
        return best?.timeInMillis ?: 0L
    }

    private fun parseSlots(slotsString: String): List<Pair<Int, Int>> {
        val s = slotsString.trim()
        if (s.isEmpty()) return DEFAULT_SLOTS
        val out = ArrayList<Pair<Int, Int>>()
        for (part in s.split(',')) {
            val t = part.trim()
            val hm = t.split(':')
            if (hm.size == 2) {
                val h = hm[0].toIntOrNull()
                val m = hm[1].toIntOrNull()
                if (h != null && m != null && h in 0..23 && m in 0..59) {
                    out.add(Pair(h, m))
                }
            }
        }
        return if (out.isEmpty()) DEFAULT_SLOTS else out
    }

    // "trước mốc đầu tiên -> 0; còn lại -> mốc lớn nhất <= hiện tại"
    private fun currentSlotIndex(slotsString: String, now: Calendar): Int {
        val slots = parseSlots(slotsString)
        if (slots.isEmpty()) return 0
        var last = 0
        for ((i, pair) in slots.withIndex()) {
            val (h, m) = pair
            val cal = Calendar.getInstance().apply {
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
            }
            if (now.timeInMillis >= cal.timeInMillis) last = i
        }
        return last
    }
}

// === B4.5 helper: vẽ bitmap nền + viền ===

// [MEOW_FRAME] START — helpers cho khung hình
private fun computeFramePaddingPx(context: Context, slug: String, destW: Int, destH: Int): android.graphics.Rect? {
    val name = "frame_${slug}"
    val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
    if (resId == 0) return null
    val dr = try { context.resources.getDrawable(resId, null) } catch (_: Exception) { null }
    if (dr !is NinePatchDrawable) return null

    // Lấy padding gốc từ 9‑patch
    val base = android.graphics.Rect()
    dr.getPadding(base)

    // Scale padding theo kích thước render thực tế
    val iw = dr.intrinsicWidth.coerceAtLeast(1)
    val ih = dr.intrinsicHeight.coerceAtLeast(1)
    val sx = destW.toFloat() / iw
    val sy = destH.toFloat() / ih
    return android.graphics.Rect(
        (base.left * sx).toInt(),
        (base.top * sy).toInt(),
        (base.right * sx).toInt(),
        (base.bottom * sy).toInt()
    )
}

/** Vẽ khung (nếu có) đè lên bitmap nền. Không làm gì nếu chưa chọn khung. */
private fun overlayFrameIfAny(context: Context, base: Bitmap, destW: Int, destH: Int) {
    val sp = context.getSharedPreferences("meow_settings", Context.MODE_PRIVATE)
    val slug = sp.getString("decor_frame_key", null)?.trim()
    if (slug.isNullOrEmpty() || slug.equals("none", ignoreCase = true)) return

    val name = "frame_${slug}"
    val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
    if (resId == 0) return
    val dr = try { context.resources.getDrawable(resId, null) } catch (_: Exception) { null }
    if (dr == null) return

    // Thiết lập bounds = full bitmap
    dr.setBounds(0, 0, destW, destH)
    val canvas = Canvas(base)
    dr.draw(canvas)
}
private fun buildDecorBitmap(
    context: Context,
    widthPx: Int,
    heightPx: Int,
    borderStyle: String,
    borderWidthDp: Int,
    borderColor: Int,
    bgColorOrNull: Int?
): Bitmap {
    val w = if (widthPx > 0) widthPx else 1
    val h = if (heightPx > 0) heightPx else 1
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    // === Overlay icon config (no roof) ===
    val spIcon = context.getSharedPreferences("meow_settings", Context.MODE_PRIVATE)
    val iconKey = spIcon.getString("decor_icon_key", null)
    val hasIcon = !iconKey.isNullOrBlank()
    val density = context.resources.displayMetrics.density
    val iconSizePx = (70f * density).toInt()
    val iconRightPx = (12f * density).toInt()


    // dp -> px cho độ dày viền
    val strokePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, borderWidthDp.toFloat(), context.resources.displayMetrics
    )

    // bo góc theo kiểu viền
    val radius = when (borderStyle) {
        "square" -> 0f
        "round"  -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, context.resources.displayMetrics)
        "pill"   -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 26f, context.resources.displayMetrics)
        else     -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, context.resources.displayMetrics)
    }

    val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())

    // tô nền (nếu có)
    if (bgColorOrNull != null) {
        val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = bgColorOrNull
        }
        canvas.drawRoundRect(rect, radius, radius, paintFill)
    }

    // ===== B5: nền ảnh (center-crop) + bo theo viền khi có viền =====
    run {
        val sp = context.getSharedPreferences("meow_settings", Context.MODE_PRIVATE)
        val bgMode = sp.getString("decor_bg_mode", "none") ?: "none"
        val bgKey = sp.getString("decor_bg_image", null)
        if (bgMode == "image" && !bgKey.isNullOrBlank()) {
            val resName = bgKey + "_full"
            val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
            if (resId != 0) {
                val src = BitmapFactory.decodeResource(context.resources, resId)
                if (src != null) {
                    val destRatio = w.toFloat() / h.toFloat()
                    val srcRatio = src.width.toFloat() / src.height.toFloat()
                    var srcLeft = 0; var srcTop = 0; var srcRight = src.width; var srcBottom = src.height
                    if (srcRatio > destRatio) {
                        val newW = (src.height * destRatio).toInt()
                        val dx = (src.width - newW) / 2
                        srcLeft = dx; srcRight = dx + newW
                    } else {
                        val newH = (src.width / destRatio).toInt()
                        val dy = (src.height - newH) / 2
                        srcTop = dy; srcBottom = dy + newH
                    }
                    val srcRect = Rect(srcLeft, srcTop, srcRight, srcBottom)
                    val dstRect = RectF(0f, 0f, w.toFloat(), h.toFloat())
                    val needClip = borderStyle != "none"
                    if (needClip) {
                        val path = Path().apply { addRoundRect(dstRect, radius, radius, Path.Direction.CW) }
                        canvas.save(); canvas.clipPath(path)
                    }
                    canvas.drawBitmap(src, srcRect, dstRect, null)
                    if (needClip) canvas.restore()
                    src.recycle()
                }
            }
        }
    }


    // vẽ viền (nếu không phải "none")
    if (borderStyle != "none") {
        val half = strokePx / 2f
        val rectStroke = RectF(half, half, w - half, h - half)
        val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            color = borderColor
        }
        canvas.drawRoundRect(rectStroke, radius, radius, paintStroke)
    }

    
    // Draw icon overlay (TOP|END) if present
    if (hasIcon) {
        val resIdIcon = context.resources.getIdentifier(iconKey, "drawable", context.packageName)
        if (resIdIcon != 0) {
            val iconSrc = BitmapFactory.decodeResource(context.resources, resIdIcon)
            if (iconSrc != null) {
                val left = (w - iconRightPx - iconSizePx).toFloat()
                val top = 0f
                val dst = RectF(left, top, left + iconSizePx, top + iconSizePx)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(iconSrc, null, dst, paint)
                try { iconSrc.recycle() } catch (_: Exception) {}
            }
        }
    }
return bmp
}
