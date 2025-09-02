package com.meowwidget.gd1

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
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
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Cập nhật nội dung hiện tại và đặt hẹn giờ cho mốc kế tiếp
        updateAllWidgets(context)
        scheduleNextTick(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (ACTION_TICK == intent.action) {
            updateAllWidgets(context)
            scheduleNextTick(context) // Đặt hẹn giờ cho mốc tiếp theo
        }
    }

    // ====== Hiển thị "Câu hôm nay" ======
    private fun updateAllWidgets(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, MeowQuoteWidget::class.java))
        if (ids == null || ids.isEmpty()) return

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
        }
        for (id in ids) mgr.updateAppWidget(id, views)
    }

    private fun computeTodayQuote(context: Context, now: Calendar): String {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val source = sp.getString(KEY_SOURCE, "all") ?: "all"
        val slotsString = sp.getString(KEY_SLOTS, "") ?: ""
        val addedRaw = sp.getString(KEY_ADDED, "") ?: ""
        val favRaw = sp.getString(KEY_FAVS, "") ?: ""
        val planDay = sp.getString(KEY_PLAN_DAY, "") ?: ""
        val planIdx = sp.getInt(KEY_PLAN_IDX, 0)

        val allList = when (source) {
            "fav" -> toLines(favRaw)
            else  -> distinctPreserveOrder(loadDefault(context) + toLines(addedRaw))
        }

        if (allList.isEmpty()) {
            // Giữ nguyên tinh thần hiện tại: khi nguồn trống thì không tự rơi nguồn khác
            return ""
        }

        val slotIdx = currentSlotIndex(slotsString, now)
        val baseDay = parseDay(planDay, now) // số nguyên từ "ddMMyy" hoặc ngày hôm nay nếu trống
        val base = baseDay + planIdx
        val idx = ((base + slotIdx) % allList.size + allList.size) % allList.size
        return allList[idx]
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
            val key = s.trim()
            if (key.isNotEmpty() && !seen.contains(key)) {
                seen.add(key)
                out.add(s)
            }
        }
        return out
    }

    private fun parseDay(day: String, now: Calendar): Int {
        if (day.length == 6) { // ddMMyy
            return try {
                day.toInt()
            } catch (_: Exception) {
                todayAsInt(now)
            }
        }
        return todayAsInt(now)
    }

    private fun todayAsInt(now: Calendar): Int {
        val d = now.get(Calendar.DAY_OF_MONTH)
        val m = now.get(Calendar.MONTH) + 1
        val y = now.get(Calendar.YEAR) % 100
        return (d * 10000) + (m * 100) + y
    }

    // ====== Hẹn giờ mốc kế tiếp (nhẹ) ======
    private fun scheduleNextTick(context: Context) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val slotsString = sp.getString(KEY_SLOTS, "") ?: ""
        val nextTime = nextSlotTimeMillis(slotsString)
        if (nextTime <= 0L) return

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MeowQuoteWidget::class.java).setAction(ACTION_TICK)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(context, 0, intent, flags)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pi)
        } catch (_: Exception) {
            // Chạy trên thiết bị không hỗ trợ doze/idle: fallback nhẹ
            am.setExact(AlarmManager.RTC_WAKEUP, nextTime, pi)
        }
    }

    private fun nextSlotTimeMillis(slotsString: String): Long {
        val slots = parseSlots(slotsString)
        if (slots.isEmpty()) return 0L

        val now = Calendar.getInstance()
        val candidates = ArrayList<Calendar>(slots.size)

        for (hm in slots) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                set(Calendar.HOUR_OF_DAY, hm.first); set(Calendar.MINUTE, hm.second)
            }
            if (cal.timeInMillis <= now.timeInMillis) {
                // slot đã qua -> xét cho ngày mai
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            candidates.add(cal)
        }
        // lấy mốc sớm nhất trong tương lai
        var best: Calendar? = null
        for (c in candidates) if (best == null || c.timeInMillis < best!!.timeInMillis) best = c
        return best?.timeInMillis ?: 0L
    }

    private fun parseSlots(slotsString: String): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        val s = slotsString.trim()
        if (s.isEmpty()) return listOf(Pair(0, 0)) // 00:00 nếu trống
        val parts = s.split(',')
        for (p in parts) {
            val t = p.trim()
            val hm = t.split(':')
            if (hm.size == 2) {
                val h = hm[0].toIntOrNull()
                val m = hm[1].toIntOrNull()
                if (h != null && m != null && h in 0..23 && m in 0..59) {
                    out.add(Pair(h, m))
                }
            }
        }
        // đảm bảo có ít nhất 1 mốc
        return if (out.isEmpty()) listOf(Pair(0, 0)) else out
    }

    private fun currentSlotIndex(slotsString: String, now: Calendar): Int {
        val slots = parseSlots(slotsString)
        if (slots.isEmpty()) return 0
        var count = 0
        for ((h, m) in slots) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
            }
            if (cal.timeInMillis <= now.timeInMillis) count++
        }
        // "mốc gần nhất không vượt quá thời điểm hiện tại"
        return if (count == 0) slots.size - 1 else count - 1
    }
}
