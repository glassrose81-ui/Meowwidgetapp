package com.meowwidget.gd1

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputType
import android.view.View
import android.widget.*
import java.util.Locale

// GĐ2-B3 Test (đã sửa đúng quy trình, chỉ sửa màn test — không đụng core/Manifest)
// - Hiển thị đủ 3 mốc: mỗi ô 1 dòng (không tràn màn hình hẹp)
// - Nút "Giả lập sang ngày mới": cập nhật "Ngày kế hoạch" ngay, không tự quay về hôm nay
// - Thêm "Mốc đã lưu:" để xem nhanh lịch hiện hành

class QuoteTestActivity : Activity() {

    private val REQ_OPEN_TXT = 1001

    private lateinit var pasteBox: EditText
    private lateinit var pickBtn: Button
    private lateinit var importFromPasteBtn: Button
    private lateinit var countBtn: Button
    private lateinit var statusView: TextView
    private lateinit var previewView: TextView

    // B2/B3
    private lateinit var time1: EditText
    private lateinit var time2: EditText
    private lateinit var time3: EditText
    private lateinit var saveScheduleBtn: Button
    private lateinit var scheduleSavedView: TextView
    private lateinit var currentQuoteView: TextView
    private lateinit var nextChangeView: TextView
    private lateinit var planDateView: TextView
    private lateinit var planIndicesView: TextView
    private lateinit var advanceBtn: Button
    private lateinit var simulateNextDayBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Meow Test — B3 (fixed)"

        val root = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        root.addView(col)

        // --- B1: Nhập ---
        col.addView(TextView(this).apply { text = "B1 — Nhập dữ liệu (.txt hoặc dán trực tiếp)" })

        pasteBox = EditText(this).apply {
            hint = "Dán các câu vào đây (mỗi dòng = 1 câu)"
            minLines = 5
            maxLines = 10
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        col.addView(pasteBox, lpTop(8))

        importFromPasteBtn = Button(this).apply { text = "Xem trước & NHẬP từ khung dán" }
        col.addView(importFromPasteBtn, lpTop(8))

        pickBtn = Button(this).apply { text = "Chọn tệp .txt để nhập" }
        col.addView(pickBtn, lpTop(8))

        countBtn = Button(this).apply { text = "Xem tổng số hiện có" }
        col.addView(countBtn, lpTop(8))

        statusView = TextView(this).apply { text = "Trạng thái nhập sẽ hiện ở đây…" }
        col.addView(statusView, lpTop(8))

        previewView = TextView(this).apply { text = "Xem trước 5 dòng đầu…" }
        col.addView(previewView, lpTop(8))

        col.addView(divider())

        // --- B2/B3: Lịch & Câu hôm nay + Trạng thái kế hoạch ---
        col.addView(TextView(this).apply { text = "B2/B3 — Lịch đổi & Trạng thái kế hoạch" })

        // Hiển thị 3 mốc: mỗi ô một dòng (không còn hàng ngang bị tràn)
        col.addView(TextView(this).apply { text = "Mốc 1 (HH:MM)" }, lpTop(6))
        time1 = EditText(this).apply { hint = "HH:MM"; inputType = InputType.TYPE_CLASS_DATETIME; minEms = 6 }
        col.addView(time1, lpTop(4))

        col.addView(TextView(this).apply { text = "Mốc 2 (HH:MM)" }, lpTop(8))
        time2 = EditText(this).apply { hint = "HH:MM"; inputType = InputType.TYPE_CLASS_DATETIME; minEms = 6 }
        col.addView(time2, lpTop(4))

        col.addView(TextView(this).apply { text = "Mốc 3 (HH:MM)" }, lpTop(8))
        time3 = EditText(this).apply { hint = "HH:MM"; inputType = InputType.TYPE_CLASS_DATETIME; minEms = 6 }
        col.addView(time3, lpTop(4))

        saveScheduleBtn = Button(this).apply { text = "Lưu lịch (tối đa 3 mốc)" }
        col.addView(saveScheduleBtn, lpTop(10))

        scheduleSavedView = TextView(this).apply { text = "Mốc đã lưu: —" }
        col.addView(scheduleSavedView, lpTop(6))

        currentQuoteView = TextView(this).apply { text = "Câu hôm nay: (chưa có)" }
        col.addView(currentQuoteView, lpTop(12))

        nextChangeView = TextView(this).apply { text = "Mốc kế tiếp: (chưa có)" }
        col.addView(nextChangeView, lpTop(4))

        planDateView = TextView(this).apply { text = "Ngày kế hoạch (ddMMyy): —" }
        col.addView(planDateView, lpTop(8))

        planIndicesView = TextView(this).apply { text = "Chỉ số mốc hôm nay: —" }
        col.addView(planIndicesView, lpTop(4))

        advanceBtn = Button(this).apply { text = "Giả lập đến mốc kế tiếp" }
        col.addView(advanceBtn, lpTop(8))

        simulateNextDayBtn = Button(this).apply { text = "Giả lập sang ngày mới" }
        col.addView(simulateNextDayBtn, lpTop(8))

        setContentView(root)

        // --- Events B1 ---
        importFromPasteBtn.setOnClickListener {
            val raw = pasteBox.text?.toString() ?: ""
            previewView.text = buildPreview(raw).ifBlank { "Không có dòng hợp lệ để xem trước." }

            val report = QuoteCore.importFromPlainText(this, raw)
            statusView.text = "Nhập từ dán: +${report.added}, bỏ trùng ${report.duplicates}. Bạn thêm tổng: ${report.totalUser}. Tất cả: ${report.totalAll}."
            refreshToday()
        }

        pickBtn.setOnClickListener { openTxtPicker() }

        countBtn.setOnClickListener {
            val (user, all) = QuoteCore.getCounts(this)
            statusView.text = "Bạn đã thêm: $user. Tổng (mặc định + bạn): $all."
            previewView.text = buildPreviewFromAll()
            refreshToday()
        }

        // --- Events B2/B3 ---
        saveScheduleBtn.setOnClickListener {
            val mins = mutableListOf<Int>()
            parseTimeToMinutes(time1.text?.toString())?.let { mins.add(it) }
            parseTimeToMinutes(time2.text?.toString())?.let { mins.add(it) }
            parseTimeToMinutes(time3.text?.toString())?.let { mins.add(it) }
            val saved = QuoteCore.setScheduleMinutes(this, mins)
            Toast.makeText(this, "Đã lưu mốc: ${saved.joinToString { mmToHHMM(it) }}", Toast.LENGTH_SHORT).show()
            // cập nhật hiển thị mốc đã lưu
            scheduleSavedView.text = "Mốc đã lưu: " + saved.joinToString { mmToHHMM(it) }
            refreshToday()
        }

        advanceBtn.setOnClickListener {
            val q = QuoteCore.debugAdvanceToNextSlot(this)
            Toast.makeText(this, if (q != null) "Đã nhảy đến mốc kế tiếp." else "Không thể nhảy.", Toast.LENGTH_SHORT).show()
            refreshToday()
        }

        simulateNextDayBtn.setOnClickListener {
            val q = QuoteCore.debugSimulateNextDay(this)
            val newPlan = QuoteCore.getPlanDateKey(this) ?: "—"
            planDateView.text = "Ngày kế hoạch (ddMMyy): $newPlan"
            if (q != null) currentQuoteView.text = "Câu hôm nay: " + q
            Toast.makeText(this, if (q != null) "Đã tạo & lưu kế hoạch ngày mới: $newPlan" else "Không thể tạo kế hoạch.", Toast.LENGTH_SHORT).show()
            // Không gọi refreshToday() để khỏi quay về hôm nay.
        }

        // Fill schedule UI with current values
        val cur = QuoteCore.getScheduleMinutes(this)
        val hhmm = cur.map { mmToHHMM(it) }
        time1.setText(hhmm.getOrNull(0) ?: "")
        time2.setText(hhmm.getOrNull(1) ?: "")
        time3.setText(hhmm.getOrNull(2) ?: "")
        scheduleSavedView.text = "Mốc đã lưu: " + (if (cur.isEmpty()) "—" else hhmm.joinToString(", "))

        refreshToday()
    }

    private fun refreshToday() {
        val q = QuoteCore.getCurrentQuote(this)
        currentQuoteView.text = "Câu hôm nay: " + (q ?: "(chưa có)")
        val next = QuoteCore.getNextChangeMinuteOfDay(this)
        nextChangeView.text = "Mốc kế tiếp: " + (next?.let { mmToHHMM(it) } ?: "—")

        val d = QuoteCore.getPlanDateKey(this) ?: "—"
        planDateView.text = "Ngày kế hoạch (ddMMyy): $d"

        val idxArr = QuoteCore.getTodayPlanIndices(this)
        planIndicesView.text = if (idxArr.isEmpty()) "Chỉ số mốc hôm nay: —"
                               else "Chỉ số mốc hôm nay: " + idxArr.joinToString(", ")
    }

    private fun openTxtPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse("content://com.android.externalstorage.documents/document/primary:"))
        }
        try { startActivityForResult(intent, REQ_OPEN_TXT) }
        catch (e: Exception) { Toast.makeText(this, "Không mở được bộ chọn tệp.", Toast.LENGTH_SHORT).show() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_OPEN_TXT && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val report = QuoteCore.importFromTxtUri(this, uri)
                statusView.text = "Nhập từ .txt: +${report.added}, bỏ trùng ${report.duplicates}. Bạn thêm tổng: ${report.totalUser}. Tất cả: ${report.totalAll}."
                previewView.text = buildPreviewFromAll()
                refreshToday()
            } catch (e: Exception) {
                statusView.text = "Có lỗi khi đọc tệp. Hãy thử lại với file .txt (UTF-8)."
            }
        }
    }

    private fun buildPreview(raw: String): String {
        val lines = raw.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.replace(Regex("\\s+"), " ") }
            .take(5)
        return if (lines.isEmpty()) "" else "Xem trước (5 dòng):\n• " + lines.joinToString("\n• ")
    }

    private fun buildPreviewFromAll(): String {
        val all = QuoteCore.getAllQuotes(this)
        val head = all.take(5)
        return if (head.isEmpty()) "Kho đang trống." else "Một vài câu trong kho:\n• " + head.joinToString("\n• ")
    }

    private fun parseTimeToMinutes(text: String?): Int? {
        val t = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parts = t.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    private fun mmToHHMM(mins: Int): String {
        val h = (mins / 60) % 24
        val m = mins % 60
        return String.format(Locale.getDefault(), "%02d:%02d", h, m)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun lpTop(marginDp: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(marginDp) }
    private fun divider(): View = View(this).apply {
        setBackgroundColor(0xFFCCCCCC.toInt())
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(12); bottomMargin = dp(12) }
    }
}
