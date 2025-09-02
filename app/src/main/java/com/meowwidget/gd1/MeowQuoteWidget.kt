package com.meowwidget.gd1

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.Context.MODE_PRIVATE
import android.widget.RemoteViews
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class MeowQuoteWidget : AppWidgetProvider() {

    // Keys must mirror MeowSettingsActivity exactly
    private val PREF = "meow_settings"
    private val KEY_SOURCE = "source"           // "all" | "fav"
    private val KEY_SLOTS = "slots"             // "08:00,17:00,20:00"
    private val KEY_ADDED = "added_lines"       // text joined by '\n'
    private val KEY_FAVS = "favs"               // text joined by '\n' (quotes)
    private val KEY_PLAN_DAY = "plan_day"       // ddMMyy
    private val KEY_PLAN_IDX = "plan_idx"       // Int

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        // Prepare the quote exactly like MeowSettingsActivity
        val quote = computeTodayQuote(context)

        // Inflate RemoteViews and set click to open MeowSettingsActivity
        val views = RemoteViews(context.packageName, R.layout.bocuc_meow).apply {
            setTextViewText(R.id.widget_text, quote)
            val intent = Intent(context, MeowSettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getActivity(context, 0, intent, flags)
            setOnClickPendingIntent(R.id.widget_text, pi)
        }

        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    private fun computeTodayQuote(context: Context): String {
        val pref = context.getSharedPreferences(PREF, MODE_PRIVATE)

        // Source selection
        val src = pref.getString(KEY_SOURCE, "all") ?: "all"

        // Build lists
        val defaults = loadDefaultQuotes(context)
        val added = getAddedList(context)
        val favs = (pref.getString(KEY_FAVS, "") ?: "")
            .split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        val allAll = (defaults + added).distinct()
        val list = if (src == "fav") favs else allAll

        if (list.isEmpty()) {
            return "Chưa có câu nào. Hãy dán hoặc nạp .TXT."
        }

        val base = ensurePlanBase(context, list.size)
        val slotsStr = pref.getString(KEY_SLOTS, "08:00,17:00,20:00") ?: ""
        val slotIdx = currentSlotIndex(nowMinutes(), parseSlots(slotsStr))

        val idx = (base + slotIdx) % list.size
        return list[idx]
    }

    private fun loadDefaultQuotes(context: Context): List<String> = try {
        context.assets.open("quotes_default.txt").use { ins ->
            BufferedReader(InputStreamReader(ins, Charsets.UTF_8))
                .readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
    } catch (_: Exception) { emptyList() }

    private fun getAddedList(context: Context): List<String> {
        val cur = context.getSharedPreferences(PREF, MODE_PRIVATE).getString(KEY_ADDED, "") ?: ""
        return if (cur.isEmpty()) emptyList() else cur.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun parseSlots(s: String): List<Int> {
        val parts = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val out = mutableListOf<Int>()
        for (p in parts) {
            val mm = parseHHMM(p)
            if (mm != null) out.add(mm)
        }
        return out.distinct().sorted()
    }

    private fun parseHHMM(hhmm: String): Int? {
        val t = hhmm.trim()
        val ok = Regex("""^\d{1,2}:\d{2}$""")
        if (!ok.matches(t)) return null
        val parts = t.split(":")
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    private fun nowMinutes(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    private fun currentSlotIndex(nowM: Int, slots: List<Int>): Int {
        if (slots.isEmpty()) return 0
        var idx = 0
        for (i in slots.indices) if (nowM >= slots[i]) idx = i
        return idx
    }

    private fun ensurePlanBase(context: Context, size: Int): Int {
        val pref = context.getSharedPreferences(PREF, MODE_PRIVATE)
        val today = SimpleDateFormat("ddMMyy", Locale.getDefault()).format(Date())
        val oldDay = pref.getString(KEY_PLAN_DAY, null)
        var base = kotlin.math.max(0, pref.getInt(KEY_PLAN_IDX, -1))
        if (oldDay == null) base = 0
        else if (oldDay != today) base = (base + 1) % kotlin.math.max(1, size)
        pref.edit().putString(KEY_PLAN_DAY, today).putInt(KEY_PLAN_IDX, base).apply()
        return base
    }
}
