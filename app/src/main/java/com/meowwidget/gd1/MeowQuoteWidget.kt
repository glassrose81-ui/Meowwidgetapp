
package com.meowwidget.gd1

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.widget.RemoteViews
import android.os.Build
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class MeowQuoteWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId, null)
        }
        scheduleNextTick(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (ACTION_TICK == action) {
            val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val now = Calendar.getInstance()
            val thisKey = slotKeyForNow(sp, now)
            val lastKey = sp.getString(KEY_LAST_FIRED_KEY, null)
            if (thisKey.isNotEmpty() && thisKey != lastKey) {
                val cur = sp.getInt(KEY_SEQ_CURRENT, 0)
                sp.edit()
                    .putInt(KEY_SEQ_CURRENT, cur + 1)
                    .putString(KEY_LAST_FIRED_KEY, thisKey)
                    .apply()
            }
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, MeowQuoteWidget::class.java))
            for (id in ids) updateSingleWidget(context, mgr, id, null)
            scheduleNextTick(context)
        } else if (AppWidgetManager.ACTION_APPWIDGET_UPDATE == action) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, MeowQuoteWidget::class.java))
            for (id in ids) updateSingleWidget(context, mgr, id, null)
            scheduleNextTick(context)
        }
    }

    // -------- Rendering --------
    private fun updateSingleWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, options: BundleLike?) {
        val now = Calendar.getInstance()
        val quote = computeTodayQuote(context, now)
        val views = RemoteViews(context.packageName, R.layout.bocuc_meow) // layout 1 dòng của widget
        views.setTextViewText(R.id.tvQuote, quote)

        // 3 mức chữ: 16 / 18 / 22 sp theo kích thước widget
        val size = classifySize(appWidgetManager, appWidgetId)
        val spSize = when (size) {
            SizeClass.SMALL -> 16f
            SizeClass.MEDIUM -> 18f
            SizeClass.LARGE -> 22f
        }
        views.setTextViewTextSize(R.id.tvQuote, android.util.TypedValue.COMPLEX_UNIT_SP, spSize)
        views.setTextColor(R.id.tvQuote, Color.BLACK)

        // Chạm → mở MeowSettings
        val intent = Intent(context, MeowSettingsActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.rootWidget, pi)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun classifySize(mgr: AppWidgetManager, id: Int): SizeClass {
        val opts = mgr.getAppWidgetOptions(id)
        val minW = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        return when {
            minW < 140 -> SizeClass.SMALL
            minW < 220 -> SizeClass.MEDIUM
            else -> SizeClass.LARGE
        }
    }

    enum class SizeClass { SMALL, MEDIUM, LARGE }

    // -------- Quote selection --------
    fun computeTodayQuote(context: Context, now: Calendar): String {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val source = sp.getString(KEY_SOURCE, "all") ?: "all"
        val slotsString = sp.getString(KEY_SLOTS, DEFAULT_SLOTS) ?: DEFAULT_SLOTS
        val addedRaw = sp.getString(KEY_ADDED, "") ?: ""
        val favRaw = sp.getString(KEY_FAVS, "") ?: ""

        val baseList = when (source) {
            "fav" -> toLines(favRaw)
            else  -> distinctPreserveOrder(loadDefaultCached(context) + toLines(addedRaw))
        }
        if (baseList.isEmpty()) return ""

        // Khởi tạo SEQ đúng 1 lần từ công thức cũ để giữ nguyên câu đang thấy
        if (!sp.getBoolean(KEY_SEQ_INIT_DONE, false)) {
            val legacyBase = ensurePlanBase(sp, baseList.size, now)
            val legacySlotIdx = currentSlotIndex(slotsString, now)
            val initSeq = legacyBase + legacySlotIdx
            sp.edit().putInt(KEY_SEQ_CURRENT, initSeq).putBoolean(KEY_SEQ_INIT_DONE, true).apply()
        }

        val seq = sp.getInt(KEY_SEQ_CURRENT, 0)
        val idx = ((seq % baseList.size) + baseList.size) % baseList.size
        return baseList[idx]
    }

    // -------- Slots & schedule --------
    private fun parseSlots(s: String): List<Pair<Int, Int>> {
        if (s.isBlank()) return emptyList()
        return s.split(',')
            .mapNotNull { it.trim() }
            .mapNotNull { token ->
                val parts = token.split(':')
                if (parts.size == 2) {
                    val h = parts[0].toIntOrNull()
                    val m = parts[1].toIntOrNull()
                    if (h != null && m != null && h in 0..23 && m in 0..59) Pair(h, m) else null
                } else null
            }
            .sortedWith(compareBy({ it.first }, { it.second }))
    }

    private fun currentSlotIndex(slotsString: String, now: Calendar): Int {
        val slots = parseSlots(slotsString)
        if (slots.isEmpty()) return 0
        var idx = -1
        for ((i, pair) in slots.withIndex()) {
            val (h, m) = pair
            val cal = (now.clone() as Calendar).apply {
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
            }
            if (now.timeInMillis >= cal.timeInMillis) idx = i
        }
        return kotlin.math.max(idx, 0)
    }

    private fun slotKeyForNow(sp: SharedPreferences, now: Calendar): String {
        val slotsString = sp.getString(KEY_SLOTS, DEFAULT_SLOTS) ?: DEFAULT_SLOTS
        val slots = parseSlots(slotsString)
        if (slots.isEmpty()) return ""
        var last: Pair<Int, Int>? = null
        for ((h, m) in slots) {
            val cal = (now.clone() as Calendar).apply {
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
            }
            if (now.timeInMillis >= cal.timeInMillis) last = Pair(h, m)
        }
        val (h, m) = last ?: slots.first()
        val y = now.get(Calendar.YEAR)
        val mon = now.get(Calendar.MONTH) + 1
        val d = now.get(Calendar.DAY_OF_MONTH)
        return String.format(Locale.US, "%04d%02d%02d-%02d:%02d", y, mon, d, h, m)
    }

    private fun scheduleNextTick(context: Context) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val slotsString = sp.getString(KEY_SLOTS, DEFAULT_SLOTS) ?: DEFAULT_SLOTS
        val slots = parseSlots(slotsString)
        if (slots.isEmpty()) return

        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply { set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        var found = false
        for ((h, m) in slots) {
            next.set(Calendar.HOUR_OF_DAY, h)
            next.set(Calendar.MINUTE, m)
            if (next.timeInMillis > now.timeInMillis) {
                found = true
                break
            }
        }
        if (!found) {
            val (h, m) = slots.first()
            next.add(Calendar.DAY_OF_YEAR, 1)
            next.set(Calendar.HOUR_OF_DAY, h)
            next.set(Calendar.MINUTE, m)
        }

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MeowQuoteWidget::class.java).setAction(ACTION_TICK)
        val pi = PendingIntent.getBroadcast(context, 9991, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
        }
    }

    // -------- Legacy base (giữ để init 1 lần; sau đó SEQ dùng lâu dài) --------
    private fun ensurePlanBase(sp: SharedPreferences, total: Int, now: Calendar): Int {
        val today = String.format(Locale.US, "%04d%02d%02d", now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
        val lastDay = sp.getString(KEY_PLAN_DAY, null)
        var base = sp.getInt(KEY_PLAN_IDX, 0)
        if (lastDay != today) {
            base = (base + 1) % kotlin.math.max(total, 1)
            sp.edit().putString(KEY_PLAN_DAY, today).putInt(KEY_PLAN_IDX, base).apply()
        }
        return base
    }

    // -------- Data helpers --------
    private fun toLines(s: String): List<String> =
        s.split('\n').mapNotNull { it.trim() }.filter { it.isNotEmpty() }

    private fun distinctPreserveOrder(list: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        val out = ArrayList<String>()
        for (x in list) if (seen.add(x)) out.add(x)
        return out
    }

    private fun loadDefaultCached(context: Context): List<String> {
        cachedDefault[ASSET_DEFAULT]?.let { return it }
        val lines = ArrayList<String>()
        try {
            context.assets.open(ASSET_DEFAULT).use { ins ->
                BufferedReader(InputStreamReader(ins)).use { br ->
                    var line: String? = br.readLine()
                    while (line != null) {
                        val t = line.trim()
                        if (t.isNotEmpty()) lines.add(t)
                        line = br.readLine()
                    }
                }
            }
        } catch (_: Exception) {}
        cachedDefault[ASSET_DEFAULT] = lines
        return lines
    }

    companion object {
        private const val PREF = "meow_settings"
        private const val KEY_SOURCE = "source"         // "all" | "fav"
        private const val KEY_SLOTS = "slots"           // "HH:MM,HH:MM,..."
        private const val KEY_ADDED = "added_lines"     // dòng do bạn thêm (\\n)
        private const val KEY_FAVS = "favs"             // dòng yêu thích (\\n)

        private const val KEY_PLAN_DAY = "plan_day"     // legacy
        private const val KEY_PLAN_IDX = "plan_idx"     // legacy

        private const val KEY_SEQ_CURRENT = "seq_current"
        private const val KEY_SEQ_INIT_DONE = "seq_init_done"
        private const val KEY_LAST_FIRED_KEY = "last_fired_key"

        private const val ACTION_TICK = "com.meowwidget.gd1.TICK"
        private const val ASSET_DEFAULT = "quotes_default.txt"
        private const val DEFAULT_SLOTS = "08:00,17:00,20:00"

        private val cachedDefault = ConcurrentHashMap<String, List<String>>()
    }
}

// Để code compile ổn trên môi trường này
typealias BundleLike = Any
