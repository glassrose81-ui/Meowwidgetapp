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
        private const val KEY_SEQ_CURRENT = "seq_current"
        private const val KEY_SEQ_INIT_DONE = "seq_init_done"
        private const val KEY_LAST_FIRED_KEY = "last_fired_key"
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

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (ACTION_TICK == action) {
            val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val now = Calendar.getInstance()
            val thisKey = slotKeyForNow(sp, now)
            val lastKey = sp.getString(KEY_LAST_FIRED_KEY, null)
            if (thisKey != lastKey && thisKey.isNotEmpty()) {
                val cur = sp.getInt(KEY_SEQ_CURRENT, 0)
                sp.edit().putInt(KEY_SEQ_CURRENT, cur + 1).putString(KEY_LAST_FIRED_KEY, thisKey).apply()
            }
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, MeowQuoteWidget::class.java))
            for (id in ids) {
                updateSingleWidget(context, mgr, id, null)
            }
            scheduleNextTick(context)
        } else if (AppWidgetManager.ACTION_APPWIDGET_UPDATE == action) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, MeowQuoteWidget::class.java))
            for (id in ids) {
                updateSingleWidget(context, mgr, id, null)
            }
            scheduleNextTick(context)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateSingleWidget(context, appWidgetManager, appWidgetId, newOptions)
        // không gọi scheduleNextTick ở đây để tránh trận mưa tick lúc kéo
    }

    // ====== Hiển thị 1 widget (tự co chữ cố định 18/20/24sp) ======
    private fun updateSingleWidget(context: Context, mgr: AppWidgetManager, widgetId: Int, options: Bundle?) {
        val nowMs = System.currentTimeMillis()
        val prev = lastUpdateMs[widgetId] ?: 0L
        if (nowMs - prev < DEBOUNCE_MS) return
        lastUpdateMs[widgetId] = nowMs

        val now = Calendar.getInstance()
        val quote = computeTodayQuote(context, now)

        // Quyết định cỡ chữ ổn định (không có tuỳ chọn boost)
        val heightDp = extractStableHeightDp(mgr, widgetId, options)
        val sizeClass = decideSizeClassWithHysteresis(widgetId, heightDp)
        val sp = when (sizeClass) {
            0 -> 18f
            1 -> 20f
            else -> 24f
        }

        val views = RemoteViews(context.packageName, R.layout.bocuc_meow).apply {
            setTextViewText(R.id.widget_text, quote)
            setTextViewTextSize(R.id.widget_text, TypedValue.COMPLEX_UNIT_SP, sp)
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
                setTextViewTextSize(R.id.widget_text, TypedValue.COMPLEX_UNIT_SP, 18f)
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

        // Initialize SEQ exactly once from legacy scheme to preserve current view
        if (!sp.getBoolean(KEY_SEQ_INIT_DONE, false)) {
            val legacyBase = ensurePlanBase(sp, baseList.size, now)
            val legacySlotIdx = currentSlotIndex(slotsString, now)
            val initSeq = legacyBase + legacySlotIdx
            sp.edit().putInt(KEY_SEQ_CURRENT, initSeq).putBoolean(KEY_SEQ_INIT_DONE, true).apply()
        }

        val seq = sp.getInt(KEY_SEQ_CURRENT, 0)
        val idx = if (baseList.isEmpty()) 0 else ((seq % baseList.size) + baseList.size) % baseList.size
        return baseList[idx]
    }
