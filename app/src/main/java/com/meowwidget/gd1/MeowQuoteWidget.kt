package com.meowwidget.gd1

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

class MeowQuoteWidget : AppWidgetProvider() {

    companion object {
        private const val PREF = "meow_settings"
        private const val KEY_SOURCE = "source"            // "all" | "fav"
        private const val KEY_SLOTS = "slots"              // "08:00,17:00,20:00"
        private const val KEY_ADDED = "added_lines"        // text joined by '\n'
        private const val KEY_FAVS = "favs"                // text joined by '\n'
        private const val KEY_ANCHOR_DAY = "anchor_day"    // yyyyMMdd
        private const val KEY_ANCHOR_OFFSET = "anchor_offset"

        private const val LAYOUT_ID = R.layout.bocuc_meow
        private const val TV_ID = R.id.tvQuote
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Giữ nguyên mọi cơ chế khác; chỉ cập nhật lại khi có broadcast
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(android.content.ComponentName(context, MeowQuoteWidget::class.java))
            onUpdate(context, mgr, ids)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, LAYOUT_ID)
        views.setTextViewText(TV_ID, computeTodayQuote(context))
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    // ===== Logic tính câu hôm nay (đồng bộ với App) =====
    private fun computeTodayQuote(context: Context): String {
        val pref = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        val defaults = loadDefaultQuotes(context)
        val added = getAddedList(pref)
        val allAll = (defaults + added).distinct()

        val src = pref.getString(KEY_SOURCE, "all") ?: "all"
        val favs = (pref.getString(KEY_FAVS, "") ?: "").split("\n").filter { it.isNotEmpty() }
        val list = if (src == "fav") favs else allAll
        if (list.isEmpty()) return "Chưa có câu nào"

        val slotsList = parseSlots(pref.getString(KEY_SLOTS, "08:00,17:00,20:00") ?: "")
        val slotIdxToday = currentSlotIndex(nowMinutes(), slotsList)

        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val todayStr = sdf.format(Date())
        var anchorDay = pref.getString(KEY_ANCHOR_DAY, null) ?: todayStr
        val anchorOffset = pref.getInt(KEY_ANCHOR_OFFSET, 0)

        val slotsPerDay = max(1, slotsList.size)
        val days = daysBetween(anchorDay, todayStr)
        var steps: Long = days * slotsPerDay + slotIdxToday

        // ---- FIX 0h: trước mốc đầu tiên vẫn tính thuộc hôm qua ----
        val firstSlot = slotsList.minOrNull() ?: 0
        val nowM = nowMinutes()
        if (slotsList.isNotEmpty() && nowM < firstSlot) {
            steps -= slotsPerDay.toLong()
        }
        // ------------------------------------------------------------

        val idx = ((steps + anchorOffset).toInt() % list.size + list.size) % list.size
        return list[idx]
    }

    // ===== Helpers =====
    private fun loadDefaultQuotes(context: Context): List<String> = try {
        context.assets.open("quotes_default.txt").use { ins ->
            BufferedReader(InputStreamReader(ins, Charsets.UTF_8))
                .readLines().map { it.trim() }.filter { it.isNotEmpty() }
        }
    } catch (_: Exception) { emptyList() }

    private fun getAddedList(pref: android.content.SharedPreferences): List<String> {
        val cur = pref.getString(KEY_ADDED, "") ?: ""
        return if (cur.isEmpty()) emptyList() else cur.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun parseSlots(s: String): List<Int> {
        val parts = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val out = mutableListOf<Int>()
        for (p in parts) {
            val mm = parseHHMM(p); if (mm != null) out.add(mm)
        }
        return out.distinct().sorted()
    }

    private fun parseHHMM(hhmm: String): Int? {
        val t = hhmm.trim(); val ok = Regex("""^\d{1,2}:\d{2}$""")
        if (!ok.matches(t)) return null
        val h = t.substringBefore(":").toIntOrNull() ?: return null
        val m = t.substringAfter(":").toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h*60 + m
    }

    private fun nowMinutes(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY)*60 + cal.get(Calendar.MINUTE)
    }

    private fun currentSlotIndex(nowM: Int, slots: List<Int>): Int {
        if (slots.isEmpty()) return 0
        val s = slots.sorted()
        if (nowM < s.first()) return s.size - 1
        var idx = 0
        for (i in s.indices) {
            if (nowM >= s[i]) idx = i else break
        }
        return idx
    }

    private fun daysBetween(a: String, b: String): Long {
        return try {
            val fmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val da = fmt.parse(a); val db = fmt.parse(b)
            val one = 24L * 60L * 60L * 1000L
            ((db!!.time / one) - (da!!.time / one))
        } catch (_: Exception) { 0L }
    }
}
