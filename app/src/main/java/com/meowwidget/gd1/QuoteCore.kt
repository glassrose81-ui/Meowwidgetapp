package com.meowwidget.gd1

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.Calendar

// GĐ2-B3 — QuoteCore.kt
// B1: nhập .txt & dán | B2: đổi theo ngày/1–3 mốc (tuần tự) | B3: GIỮ TRẠNG THÁI BỀN VỮNG (+ giả lập sang ngày mới cho test)
// Ghi chú: Ngày dùng định dạng ddMMyy (ví dụ 300825). Không đụng GĐ1. Không sửa Manifest.

object QuoteCore {

    // ---------- B1: Nhập & lưu ----------
    private const val USER_FILE = "quotes_user.txt"
    private const val DEFAULT_ASSET = "quotes_default.txt" // không bắt buộc có

    data class ImportReport(
        val added: Int,
        val duplicates: Int,
        val totalUser: Int,
        val totalAll: Int
    )

    fun importFromPlainText(ctx: Context, raw: String): ImportReport {
        val batch = parseLines(raw)
        return mergeIntoUserStore(ctx, batch)
    }

    fun importFromTxtUri(ctx: Context, uri: Uri): ImportReport {
        val text = try {
            ctx.contentResolver.openInputStream(uri)?.bufferedReader(Charset.forName("UTF-8"))?.use { it.readText() }
        } catch (_: Exception) { null }
        val batch = parseLines(text ?: "")
        return mergeIntoUserStore(ctx, batch)
    }

    fun getAllQuotes(ctx: Context): List<String> {
        val user = loadUserQuotes(ctx)
        val def = loadDefaultQuotesIfAny(ctx)
        return mergeDedup(def, user)
    }

    fun getUserQuotes(ctx: Context): List<String> = loadUserQuotes(ctx)

    fun getCounts(ctx: Context): Pair<Int, Int> {
        val user = loadUserQuotes(ctx).size
        val all = getAllQuotes(ctx).size
        return user to all
    }

    private fun parseLines(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val seq = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { collapseSpaces(it) }
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (q in seq) {
            val k = normKey(q)
            if (seen.add(k)) out.add(q)
        }
        return out
    }

    private fun mergeIntoUserStore(ctx: Context, batch: List<String>): ImportReport {
        val existing = loadUserQuotes(ctx)
        if (batch.isEmpty()) {
            val totals = getAllQuotes(ctx).size
            return ImportReport(0, 0, existing.size, totals)
        }
        val existingKeys = existing.asSequence().map { normKey(it) }.toMutableSet()
        val out = ArrayList(existing)
        var added = 0
        var dupThisBatch = 0
        for (q in batch) {
            val k = normKey(q)
            if (existingKeys.add(k)) {
                out.add(q)
                added++
            } else {
                dupThisBatch++
            }
        }
        saveUserQuotes(ctx, out)
        val totals = getAllQuotes(ctx).size
        return ImportReport(added, dupThisBatch, out.size, totals)
    }

    private fun collapseSpaces(s: String): String = s.replace(Regex("\s+"), " ").trim()
    private fun normKey(s: String): String = collapseSpaces(s).lowercase()

    private fun loadUserQuotes(ctx: Context): List<String> {
        return try {
            val f = File(ctx.filesDir, USER_FILE)
            if (!f.exists()) emptyList() else
                f.readLines(Charset.forName("UTF-8"))
                    .asSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { collapseSpaces(it) }
                    .toList()
        } catch (_: IOException) {
            emptyList()
        }
    }

    private fun saveUserQuotes(ctx: Context, quotes: List<String>) {
        val f = File(ctx.filesDir, USER_FILE)
        f.writeText(quotes.joinToString(separator = "\n"), Charset.forName("UTF-8"))
    }

    private fun loadDefaultQuotesIfAny(ctx: Context): List<String> {
        return try {
            ctx.assets.open(DEFAULT_ASSET).bufferedReader(Charset.forName("UTF-8")).use { br ->
                br.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { collapseSpaces(it) }
                    .toList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mergeDedup(a: List<String>, b: List<String>): List<String> {
        if (a.isEmpty() && b.isEmpty()) return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<String>(a.size + b.size)
        fun pushAll(src: List<String>) {
            for (q in src) {
                val k = normKey(q)
                if (seen.add(k)) out.add(q)
            }
        }
        pushAll(a); pushAll(b)
        return out
    }

    // ---------- B2: Đổi theo ngày / tối đa 3 mốc (tuần tự) ----------
    private const val PREFS = "quote_core_prefs"
    private const val KEY_SCHEDULE_MIN = "schedule_minutes" // CSV "480,720,1200"

    // ---------- B3: Trạng thái bền vững ----------
    private const val KEY_PLAN_DATE = "plan_date_ddMMyy"      // ví dụ "300825"
    private const val KEY_PLAN_INDICES = "plan_indices_csv"   // ví dụ "10,11,12"
    private const val KEY_ROLLING_CURSOR = "rolling_cursor"   // Int

    // Bộ nhớ tạm (cache runtime)
    private var memPlanDate: String? = null
    private var memPlanIndices: IntArray? = null
    private var memRollingCursor: Int = 0
    private var memLoaded: Boolean = false

    // Đặt mốc (phút 0..1439), 1..3 mốc. Trả về mảng đã lưu (đã sắp xếp).
    fun setScheduleMinutes(ctx: Context, minutes: List<Int>): IntArray {
        val clean = minutes.asSequence()
            .map { it.coerceIn(0, 1439) }
            .toSet()
            .toList()
            .sorted()
            .take(3)
        val finalList = if (clean.isEmpty()) listOf(480) else clean // mặc định 08:00
        val csv = finalList.joinToString(",")
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SCHEDULE_MIN, csv)
            // lịch thay đổi → xoá plan để tính lại hôm nay
            .remove(KEY_PLAN_DATE).remove(KEY_PLAN_INDICES).apply()
        // làm trống cache
        memPlanDate = null; memPlanIndices = null; memLoaded = false
        return finalList.toIntArray()
    }

    // Lấy mốc (mặc định [480])
    fun getScheduleMinutes(ctx: Context): IntArray {
        val csv = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SCHEDULE_MIN, null)
        return if (csv.isNullOrBlank()) intArrayOf(480)
        else csv.split(",").mapNotNull { it.toIntOrNull() }.filter { it in 0..1439 }.sorted().take(3).ifEmpty { listOf(480) }.toIntArray()
    }

    // --------- API chính B2/B3 ---------

    // Câu hiện tại theo "bây giờ"
    fun getCurrentQuote(ctx: Context): String? {
        val all = getAllQuotes(ctx)
        if (all.isEmpty()) return null
        ensurePlanForToday(ctx, all.size)
        val slot = currentSlotIndex(nowMinutes(), getScheduleMinutes(ctx))
        val idx = memPlanIndices?.getOrNull(slot) ?: return null
        return all[idx]
    }

    // Mốc kế tiếp (phút trong ngày) hoặc null nếu đã qua mốc cuối
    fun getNextChangeMinuteOfDay(ctx: Context): Int? {
        val sched = getScheduleMinutes(ctx)
        val now = nowMinutes()
        for (m in sched) if (m > now) return m
        return null
    }

    // Trả về các chỉ số của "kế hoạch hôm nay"
    fun getTodayPlanIndices(ctx: Context): IntArray {
        val all = getAllQuotes(ctx).size
        ensurePlanForToday(ctx, all)
        return memPlanIndices ?: IntArray(0)
    }

    // Trả về ngày kế hoạch hiện tại (ddMMyy) — để hiển thị test
    fun getPlanDateKey(ctx: Context): String? {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PLAN_DATE, null)
    }

    // (Test) Nhảy tới mốc kế tiếp ngay (không đợi đồng hồ)
    fun debugAdvanceToNextSlot(ctx: Context): String? {
        val all = getAllQuotes(ctx)
        if (all.isEmpty()) return null
        ensurePlanForToday(ctx, all.size)
        val sched = getScheduleMinutes(ctx)
        val now = nowMinutes()
        val cur = currentSlotIndex(now, sched)
        val nextIdx = (cur + 1).coerceAtMost(sched.size - 1)
        val idx = memPlanIndices?.getOrNull(nextIdx) ?: return null
        return all[idx]
    }

    // (Test) Giả lập sang "ngày mới": tạo kế hoạch cho ngày mai và lưu ngay
    fun debugSimulateNextDay(ctx: Context): String? {
        val all = getAllQuotes(ctx)
        if (all.isEmpty()) return null
        val tomorrow = nextDayKey(todayKey())
        generateAndPersistPlanForDate(ctx, all.size, tomorrow)
        val firstIdx = memPlanIndices?.getOrNull(0) ?: return null
        return all[firstIdx]
    }

    // ------------------ Nội bộ B3 ------------------

    private fun ensurePlanForToday(ctx: Context, totalQuotes: Int) {
        if (totalQuotes <= 0) { memPlanIndices = IntArray(0); return }
        loadPlanIfNeeded(ctx)
        val today = todayKey()
        val sched = getScheduleMinutes(ctx)
        val needRebuild = (memPlanDate != today) || (memPlanIndices == null) || (memPlanIndices?.size != sched.size)
        if (needRebuild) {
            generateAndPersistPlanForDate(ctx, totalQuotes, today)
        }
    }

    private fun generateAndPersistPlanForDate(ctx: Context, totalQuotes: Int, dateKey: String) {
        loadPlanIfNeeded(ctx)
        val sched = getScheduleMinutes(ctx)
        val count = sched.size.coerceAtLeast(1)
        val plan = IntArray(count)

        val safeTotal = if (totalQuotes <= 0) 1 else totalQuotes
        var cur = memRollingCursor.coerceAtLeast(0) % safeTotal
        for (i in 0 until count) {
            var pick = cur % safeTotal
            if (i > 0 && safeTotal > 1) {
                val prev = plan[i - 1]
                if (pick == prev) { cur++; pick = cur % safeTotal }
            }
            plan[i] = pick
            cur++
        }
        memPlanDate = dateKey
        memPlanIndices = plan
        memRollingCursor = if (safeTotal > 0) cur % safeTotal else 0
        persistPlan(ctx)
    }

    private fun loadPlanIfNeeded(ctx: Context) {
        if (memLoaded) return
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        memPlanDate = p.getString(KEY_PLAN_DATE, null)
        val csv = p.getString(KEY_PLAN_INDICES, null)
        memPlanIndices = csv?.split(",")?.mapNotNull { it.toIntOrNull() }?.toIntArray()
        memRollingCursor = p.getInt(KEY_ROLLING_CURSOR, 0)
        memLoaded = true
    }

    private fun persistPlan(ctx: Context) {
        val csv = memPlanIndices?.joinToString(",") ?: ""
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PLAN_DATE, memPlanDate)
            .putString(KEY_PLAN_INDICES, csv)
            .putInt(KEY_ROLLING_CURSOR, memRollingCursor)
            .apply()
    }

    // ------------------ Tiện ích thời gian ------------------

    private fun currentSlotIndex(nowMin: Int, sched: IntArray): Int {
        if (sched.isEmpty()) return 0
        var idx = 0
        for (i in sched.indices) {
            if (nowMin >= sched[i]) idx = i else break
        }
        return idx
    }

    private fun nowMinutes(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    // ddMMyy
    private fun todayKey(): String {
        val c = Calendar.getInstance()
        val d = c.get(Calendar.DAY_OF_MONTH)
        val m = c.get(Calendar.MONTH) + 1
        val y = c.get(Calendar.YEAR) % 100
        return String.format("%02d%02d%02d", d, m, y)
    }

    // ddMMyy → ddMMyy ngày mai
    private fun nextDayKey(cur: String): String {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_MONTH, 1)
        val d = c.get(Calendar.DAY_OF_MONTH)
        val m = c.get(Calendar.MONTH) + 1
        val y = c.get(Calendar.YEAR) % 100
        return String.format("%02d%02d%02d", d, m, y)
    }
}
