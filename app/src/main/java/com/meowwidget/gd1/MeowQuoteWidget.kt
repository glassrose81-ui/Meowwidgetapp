package com.meowwidget.gd1

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.widget.RemoteViews
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Calendar
import java.util.Locale

class MeowQuoteWidget : AppWidgetProvider() {

    companion object {
        private const val PREF = "meow_settings"
        private const val KEY_SOURCE = "source"          // "all" | "fav"
        private const val KEY_SLOTS = "slots"            // "08:00,17:00,20:00"
        private const val KEY_ADDED = "added_lines"      // multi-line
        private const val KEY_FAVS = "favs"              // multi-line
        private const val KEY_PLAN_DAY = "plan_day"      // "ddMMyy"
        private const val KEY_PLAN_IDX = "plan_idx"      // Int
        private const val ACTION_TICK = "com.meowwidget.gd1.ACTION_WIDGET_TICK"
        private const val ASSET_DEFAULT = "quotes_default.txt"

        private val DEFAULT_SLOTS = listOf(Pair(8, 0), Pair(17, 0), Pair(20, 0))
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        for (id in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, id)
        }
        scheduleNextTick(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (ACTION_TICK == intent.action) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, MeowQuoteWidget::class.java))
            for (id in ids) {
                updateSingleWidget(context, mgr, id)
            }
            scheduleNextTick(context)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle?) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateSingleWidget(context, appWidgetManager, appWidgetId)
        scheduleNextTick(context)
    }

    // ====== Hiển thị 1 widget (kèm tự co chữ 16/18/22sp) ======
    private fun updateSingleWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
        val now = Calendar.getInstance()
        val quote = computeTodayQuote(context, now)

        val views = RemoteViews(context.packageName, R.layout.bocuc_meow).apply {
            setTextViewText(R.id.widget_text, quote)
            // Chạm -> mở MeowSettingsActivity
            val intent = Intent(context, MeowSettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getActivity(context, 0, intent, flags)
            setOnClickPendingIntent(R.id.widget_text, pi)

            // Tự co chữ theo chiều cao hiện tại của widget
            val heightDp = extractWidgetHeightDp(mgr, widgetId)
            val sp = decideTextSp(heightDp)
            setTextViewTextSize(R.id.widget_text, TypedValue.COMPLEX_UNIT_SP, sp)
        }
        mgr.updateAppWidget(widgetId, views)
    }

    private fun extractWidgetHeightDp(mgr: AppWidgetManager, widgetId: Int): Int {
        val opt = mgr.getAppWidgetOptions(widgetId)
        val minH = opt?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
        val maxH = opt?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
        val h = if (maxH > 0) maxH else minH
        return if (h > 0) h else 120 // fallback hợp lý
    }

    private fun decideTextSp(heightDp: Int): Float {
        return when {
            heightDp < 120 -> 16f   // nhỏ
            heightDp < 200 -> 18f   // vừa
            else -> 22f             // lớn
        }
    }

    // ====== Tính "Câu hôm nay" (đồng bộ với Meow Settings) ======
    private fun computeTodayQuote(context: Context, now: Calendar): String {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val source = sp.getString(KEY_SOURCE, "all") ?: "all"
        val slotsString = sp.getString(KEY_SLOTS, "08:00,17:00,20:00") ?: "08:00,17:00,20:00"
        val addedRaw = sp.getString(KEY_ADDED, "") ?: ""
        val favRaw = sp.getString(KEY_FAVS, "") ?: ""

        val list = when (source) {
            "fav" -> toLines(favRaw)
            else  -> distinctPreserveOrder(loadDefault(context) + toLines(addedRaw))
        }
        if (list.isEmpty()) return "" // giữ nguyên hành vi: không tự rơi nguồn khác

        // Đồng bộ base theo ngày như Meow Settings
        val base = ensurePlanBase(sp, list.size, now)

        // Mốc giờ hiện tại: trước mốc đầu tiên -> 0; còn lại -> mốc lớn nhất <= hiện tại
        val slotIdx = currentSlotIndex(slotsString, now)

        val idx = ((base + slotIdx) % list.size + list.size) % list.size
        return list[idx]
    }

    private fun ensurePlanBase(sp: android.content.SharedPreferences, size: Int, now: Calendar): Int {
        val today = formatDay(now)
        val oldDay = sp.getString(KEY_PLAN_DAY, null)
        var base = kotlin.math.max(0, sp.getInt(KEY_PLAN_IDX, -1))
        if (oldDay == null) {
            base = 0
        } else if (oldDay != today) {
            base = (base + 1) % kotlin.math.max(1, size)
        }
        sp.edit().putString(KEY_PLAN_DAY, today).putInt(KEY_PLAN_IDX, base).apply()
        return base
    }

    private fun loadDefault(context: Context): List<String> {
        return try {
            context.assets.open(ASSET_DEFAULT).use { input ->
                BufferedReader(InputStreamReader(input)).readLines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            }
        } catch (_: Exception) {
            emptyList()
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
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pi)
        } catch (_: Exception) {
            am.setExact(AlarmManager.RTC_WAKEUP, nextTime, pi)
        }
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
