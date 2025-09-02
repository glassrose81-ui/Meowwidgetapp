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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MeowQuoteWidget : AppWidgetProvider() {

    companion object {
        // ===== SharedPreferences =====
        private const val PREF = "meow_settings"
        private const val KEY_SOURCE = "source"          // "all" | "fav"
        private const val KEY_SLOTS = "slots"            // "HH:MM,HH:MM,..."
        private const val KEY_ADDED = "added_lines"      // lines joined by '\n'
        private const val KEY_FAVS = "favs"              // lines joined by '\n'
        private const val KEY_PLAN_DAY = "plan_day"      // ddMMyy (từ phiên cũ, dùng để init SEQ nếu có)
        private const val KEY_PLAN_IDX = "plan_idx"      // Int (từ phiên cũ, dùng để init SEQ nếu có)

        // ===== Patch A – 1 nguồn sự thật =====
        private const val KEY_SEQ_CURRENT = "seq_current"          // Long counter
        private const val KEY_LAST_FIRED_SLOT = "last_fired_slot"  // "yyyyMMdd#slotIdx"

        // ===== Hẹn giờ =====
        private const val ACTION_TICK = "com.meowwidget.gd1.ACTION_WIDGET_TICK"

        // ===== Dữ liệu =====
        private const val ASSET_DEFAULT = "quotes_default.txt"
        private val DEFAULT_SLOTS = listOf(Pair(8, 0), Pair(17, 0), Pair(20, 0)) // fallback

        // Cache file assets để đọc nhanh
        @Volatile private var cachedDefault: List<String>? = null
        private val cachedLock = Any()
    }

    // ====== AppWidget lifecycle ======

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        for (id in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, id, null)
        }
        scheduleNextTick(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateSingleWidget(context, appWidgetManager, appWidgetId, newOptions)
        // Không reschedule ở đây; lịch vẫn còn nguyên
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (ACTION_TICK == intent.action) {
            // === Mốc nổ: tăng SEQ + update widget ===
            val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val slots = readSlots(sp.getString(KEY_SLOTS, null))
            val now = Calendar.getInstance()
            val slotIdx = currentSlotIndex(now, slots)
            val dayKey = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(now.time)
            val slotKey = "$dayKey#$slotIdx"
            val lastKey = sp.getString(KEY_LAST_FIRED_SLOT, null)

            if (lastKey != slotKey) {
                // Đảm bảo SEQ đã khởi tạo (mapping cũ được giữ)
                if (!sp.contains(KEY_SEQ_CURRENT)) {
                    val initSeq = initialSeqFromLegacy(sp, now, slots)
                    sp.edit().putLong(KEY_SEQ_CURRENT, initSeq).apply()
                }
                val seq = sp.getLong(KEY_SEQ_CURRENT, 0L)
                sp.edit()
                    .putLong(KEY_SEQ_CURRENT, seq + 1L)
                    .putString(KEY_LAST_FIRED_SLOT, slotKey)
                    .apply()
            }

            // Cập nhật tất cả widget
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, MeowQuoteWidget::class.java))
            for (id in ids) {
                updateSingleWidget(context, mgr, id, null)
            }
            // Lên lịch mốc kế tiếp
            scheduleNextTick(context)
            return
        }

        if (AppWidgetManager.ACTION_APPWIDGET_UPDATE == intent.action) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, MeowQuoteWidget::class.java))
            for (id in ids) updateSingleWidget(context, mgr, id, null)
            scheduleNextTick(context)
            return
        }
    }

    // ====== Rendering ======

    private fun updateSingleWidget(context: Context, mgr: AppWidgetManager, widgetId: Int, options: Bundle?) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val view = RemoteViews(context.packageName, R.layout.bocuc_meow)

        // Text content
        val quote = computeQuoteText(context, sp)

        // Text size theo chiều cao (3 mức) — 16/18/22sp như đã chốt
        val heightDp = extractStableHeightDp(mgr, widgetId, options)
        val spSize = when {
            heightDp < 120 -> 16f
            heightDp < 180 -> 18f
            else -> 22f
        }
        view.setTextViewText(R.id.widget_text, quote)
        view.setTextViewTextSize(R.id.widget_text, TypedValue.COMPLEX_UNIT_SP, spSize)

        // Tap mở Settings
        val intent = Intent(context, MeowSettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getActivity(context, 0, intent, flags)
        view.setOnClickPendingIntent(R.id.widget_text, pi)

        mgr.updateAppWidget(widgetId, view)
    }

    private fun extractStableHeightDp(mgr: AppWidgetManager, widgetId: Int, options: Bundle?): Int {
        val opt = options ?: mgr.getAppWidgetOptions(widgetId)
        val minH = opt?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
        return if (minH > 0) minH else 120
    }

    private fun computeQuoteText(context: Context, sp: android.content.SharedPreferences): String {
        // Init SEQ nếu thiếu (giữ mapping đang nhìn thấy)
        if (!sp.contains(KEY_SEQ_CURRENT)) {
            val slots = readSlots(sp.getString(KEY_SLOTS, null))
            val now = Calendar.getInstance()
            val initSeq = initialSeqFromLegacy(sp, now, slots)
            sp.edit().putLong(KEY_SEQ_CURRENT, initSeq).apply()
        }

        val src = sp.getString(KEY_SOURCE, "all") ?: "all"
        val defaults = loadDefaults(context)
        val added = (sp.getString(KEY_ADDED, "") ?: "")
            .split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val favs = (sp.getString(KEY_FAVS, "") ?: "")
            .split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        val allList = (defaults + added).distinct()
        val list = if (src == "fav") favs else allList

        if (list.isEmpty()) return "Chưa có câu nào. Hãy dán hoặc nạp .TXT."

        val seq = sp.getLong(KEY_SEQ_CURRENT, 0L)
        val idx = ((seq % list.size).toInt() + list.size) % list.size
        return list[idx]
    }

    // ====== Slots & schedule ======

    private fun scheduleNextTick(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MeowQuoteWidget::class.java).setAction(ACTION_TICK)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(context, 1001, intent, flags)

        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val slots = readSlots(sp.getString(KEY_SLOTS, null))
        val nextAt = nextTriggerTimeMillis(slots)

        if (nextAt > 0L) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt, pi)
        }
    }

    private fun readSlots(s: String?): List<Pair<Int, Int>> {
        if (s == null || s.isBlank()) return DEFAULT_SLOTS
        return parseSlots(s)
    }

    private fun nextTriggerTimeMillis(slots: List<Pair<Int, Int>>): Long {
        if (slots.isEmpty()) return 0L
        val now = Calendar.getInstance()
        var best: Calendar? = null
        for ((h, m) in slots) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = now.timeInMillis
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
            }
            if (cal.timeInMillis <= now.timeInMillis) cal.add(Calendar.DAY_OF_YEAR, 1)
            if (best == null || cal.timeInMillis < best!!.timeInMillis) best = cal
        }
        return best?.timeInMillis ?: 0L
    }

    private fun currentSlotIndex(now: Calendar, slots: List<Pair<Int, Int>>): Int {
        if (slots.isEmpty()) return 0
        val nowM = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        var idx = 0
        for (i in slots.indices) {
            val (h, m) = slots[i]
            val t = h * 60 + m
            if (nowM >= t) idx = i
        }
        return idx
    }

    private fun parseSlots(slotsString: String): List<Pair<Int, Int>> {
        val s = slotsString.trim()
        if (s.isEmpty()) return emptyList()
        val out = ArrayList<Pair<Int, Int>>()
        for (token in s.split(",")) {
            val t = token.trim()
            if (t.isEmpty()) continue
            val parts = t.split(":")
            if (parts.size != 2) continue
            val h = parts[0].toIntOrNull()
            val m = parts[1].toIntOrNull()
            if (h == null || m == null) continue
            if (h !in 0..23 || m !in 0..59) continue
            out.add(Pair(h, m))
        }
        return if (out.isEmpty()) DEFAULT_SLOTS else out.sortedWith(compareBy({ it.first }, { it.second }))
    }

    // Khởi tạo SEQ dựa trên mapping cũ (base + slotIdx) để không đổi câu ngay sau khi cập nhật
    private fun initialSeqFromLegacy(sp: android.content.SharedPreferences, now: Calendar, slots: List<Pair<Int, Int>>): Long {
        val slotIdx = currentSlotIndex(now, slots)
        val base = kotlin.math.max(0, sp.getInt(KEY_PLAN_IDX, 0))
        return (base + slotIdx).toLong()
    }

    // ====== Load dữ liệu ======

    private fun loadDefaults(context: Context): List<String> {
        val cache = cachedDefault
        if (cache != null) return cache
        synchronized(cachedLock) {
            val again = cachedDefault
            if (again != null) return again
            val list = ArrayList<String>()
            try {
                context.assets.open(ASSET_DEFAULT).use { ins ->
                    BufferedReader(InputStreamReader(ins)).use { br ->
                        var line: String?
                        while (true) {
                            line = br.readLine() ?: break
                            val t = line!!.trim()
                            if (t.isNotEmpty()) list.add(t)
                        }
                    }
                }
            } catch (_: Exception) {
                // ignore
            }
            cachedDefault = list
            return list
        }
    }
}
