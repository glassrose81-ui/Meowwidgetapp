package com.meowwidget.gd1

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.Calendar

// GĐ2-B2 — QuoteCore.kt (B1 + B2: nhập .txt & dán + đổi theo ngày/1–3 mốc, tuần tự)
// Quy ước: mỗi dòng = 1 câu. Không tách tác giả. Chống trùng theo chữ đã chuẩn hoá (trim + gộp khoảng trắng + không phân biệt hoa–thường).
// Lưu dữ liệu người dùng: filesDir/quotes_user.txt (mỗi dòng 1 câu).
// Lịch mốc giờ: lưu trong SharedPreferences (mặc định 08:00). B3 mới làm phần bền vững nâng cao.

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

    private fun collapseSpaces(s: String): String = s.replace(Regex("\\s+"), " ").trim()
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

    // Trạng thái trong bộ nhớ (B3 sẽ làm bền vững sâu hơn)
    private var planDateKey: String? = null
    private var planIndices: IntArray? = null
    private var rollingCursor: Int = 0

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
            .edit().putString(KEY_SCHEDULE_MIN, csv).apply()
        planDateKey = null
        planIndices = null
        return finalList.toIntArray()
    }

    // Lấy mốc (mặc định [480])
    fun getScheduleMinutes(ctx: Context): IntArray {
        val csv = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SCHEDULE_MIN, null)
        return if (csv.isNullOrBlank()) intArrayOf(480)
        else csv.split(",").mapNotNull { it.toIntOrNull() }.filter { it in 0..1439 }.sorted().take(3).ifEmpty { listOf(480) }.toIntArray()
    }

    // Câu hiện tại theo "bây giờ"
    fun getCurrentQuote(ctx: Context): String? {
        val all = getAllQuotes(ctx)
        if (all.isEmpty()) return null
        ensurePlanForToday(ctx, all.size)
        val slot = currentSlotIndex(nowMinutes(), getScheduleMinutes(ctx))
        val idx = planIndices?.getOrNull(slot) ?: return null
        return all[idx]
    }

    // Mốc kế tiếp (phút trong ngày) hoặc null nếu đã qua mốc cuối
    fun getNextChangeMinuteOfDay(ctx: Context): Int? {
        val sched = getScheduleMinutes(ctx)
        val now = nowMinutes()
        for (m in sched) if (m > now) return m
        return null
    }

    // Cho test: chỉ số các câu trong "kế hoạch hôm nay"
    fun getTodayPlanIndices(ctx: Context): IntArray {
        val all = getAllQuotes(ctx).size
        ensurePlanForToday(ctx, all)
        return planIndices ?: IntArray(0)
    }

    // Cho test: nhảy tới mốc kế tiếp ngay
    fun debugAdvanceToNextSlot(ctx: Context): String? {
        val all = getAllQuotes(ctx)
        if (all.isEmpty()) return null
        ensurePlanForToday(ctx, all.size)
        val sched = getScheduleMinutes(ctx)
        val now = nowMinutes()
        val cur = currentSlotIndex(now, sched)
        val nextIdx = (cur + 1).coerceAtMost(sched.size - 1)
        val idx = planIndices?.getOrNull(nextIdx) ?: return null
        return all[idx]
    }

    // ------------------ Nội bộ B2 ------------------
    private fun ensurePlanForToday(ctx: Context, totalQuotes: Int) {
        if (totalQuotes <= 0) { planIndices = IntArray(0); return }
        val today = todayKey()
        if (planDateKey == today && planIndices != null && planIndices!!.isNotEmpty()) return

        val slots = getScheduleMinutes(ctx)
        val count = slots.size.coerceAtLeast(1)
        val plan = IntArray(count)

        var cur = rollingCursor.coerceAtLeast(0) % totalQuotes
        for (i in 0 until count) {
            var pick = cur % totalQuotes
            if (i > 0 && totalQuotes > 1) {
                val prev = plan[i - 1]
                if (pick == prev) { cur++; pick = cur % totalQuotes }
            }
            plan[i] = pick
            cur++
        }
        planDateKey = today
        planIndices = plan
        rollingCursor = cur % totalQuotes
    }

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

    private fun todayKey(): String {
        val c = Calendar.getInstance()
        val y = c.get(Calendar.YEAR)
        val m = c.get(Calendar.MONTH) + 1
        val d = c.get(Calendar.DAY_OF_MONTH)
        return String.format("%04d%02d%02d", y, m, d)
    }
}
