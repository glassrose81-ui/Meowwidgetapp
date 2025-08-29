package com.meowwidget.gd1
// GĐ2-B1 Test Screen — "Meow Test" (chỉ để thử nhập .txt & dán).
// Không đụng GĐ1. Màn này chỉ để build thử và sẽ ẩn trong bản phát hành nếu bạn dùng khai báo debug như hướng dẫn.
// Đặt file này cạnh QuoteCore.kt (không cần package để khớp với QuoteCore.kt đã gửi).

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputType
import android.view.View
import android.widget.*

class QuoteTestActivity : Activity() {

    private val REQ_OPEN_TXT = 1001

    private lateinit var pasteBox: EditText
    private lateinit var pickBtn: Button
    private lateinit var importFromPasteBtn: Button
    private lateinit var countBtn: Button
    private lateinit var statusView: TextView
    private lateinit var previewView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = "Meow Test — B1 (nhập .txt & dán)"

        val root = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        root.addView(col)

        val info = TextView(this).apply {
            text = "B1 — Nhập dữ liệu\n• Dán trực tiếp (mỗi dòng = 1 câu)\n• Hoặc chọn tệp .txt (Notepad)\nSau khi nhập: sẽ lọc trùng và lưu trong máy."
        }
        col.addView(info)

        pasteBox = EditText(this).apply {
            hint = "Dán các câu vào đây (mỗi dòng = 1 câu)"
            minLines = 6
            maxLines = 10
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        col.addView(pasteBox, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })

        importFromPasteBtn = Button(this).apply { text = "Xem trước & NHẬP từ khung dán" }
        col.addView(importFromPasteBtn, lpTop(8))

        pickBtn = Button(this).apply { text = "Chọn tệp .txt để nhập" }
        col.addView(pickBtn, lpTop(8))

        countBtn = Button(this).apply { text = "Xem tổng số hiện có" }
        col.addView(countBtn, lpTop(8))

        statusView = TextView(this).apply {
            text = "Trạng thái sẽ hiển thị ở đây…"
        }
        col.addView(statusView, lpTop(12))

        previewView = TextView(this).apply {
            text = "Xem trước (tối đa 5 dòng)…"
        }
        col.addView(previewView, lpTop(8))

        setContentView(root)

        // Sự kiện
        importFromPasteBtn.setOnClickListener {
            val raw = pasteBox.text?.toString() ?: ""
            val preview = buildPreview(raw)
            previewView.text = preview.ifBlank { "Không có dòng hợp lệ để xem trước." }

            val report = QuoteCore.importFromPlainText(this, raw)
            statusView.text = "ĐÃ NHẬP từ DÁN: +${report.added} câu mới, bỏ trùng ${report.duplicates}. " +
                    "Bạn thêm tổng: ${report.totalUser}. Tất cả (mặc định + bạn): ${report.totalAll}."
        }

        pickBtn.setOnClickListener {
            openTxtPicker()
        }

        countBtn.setOnClickListener {
            val (user, all) = QuoteCore.getCounts(this)
            statusView.text = "Bạn đã thêm: $user câu. Tổng (mặc định + bạn): $all câu."
            previewView.text = buildPreviewFromAll()
        }
    }

    private fun openTxtPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*" // ưu tiên text/plain, nhưng text/* để linh hoạt
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse("content://com.android.externalstorage.documents/document/primary:"))
        }
        try {
            startActivityForResult(intent, REQ_OPEN_TXT)
        } catch (e: Exception) {
            Toast.makeText(this, "Không mở được bộ chọn tệp.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_OPEN_TXT && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val report = QuoteCore.importFromTxtUri(this, uri)
                statusView.text = "ĐÃ NHẬP từ .TXT: +${report.added} câu mới, bỏ trùng ${report.duplicates}. " +
                        "Bạn thêm tổng: ${report.totalUser}. Tất cả (mặc định + bạn): ${report.totalAll}."
                previewView.text = buildPreviewFromAll()
            } catch (e: Exception) {
                statusView.text = "Có lỗi khi đọc tệp. Hãy thử lại với file .txt chữ thuần."
            }
        }
    }

    // Xem trước 5 dòng đầu từ khung dán (lọc rỗng + trim + nén khoảng trắng)
    private fun buildPreview(raw: String): String {
        val lines = raw.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.replace(Regex("\\s+"), " ") }
            .take(5)
        return if (lines.isEmpty()) "" else "Xem trước (5 dòng đầu):\n• " + lines.joinToString("\n• ")
    }

    // Xem trước 5 câu đầu trong kho hiện có
    private fun buildPreviewFromAll(): String {
        val all = QuoteCore.getAllQuotes(this)
        val head = all.take(5)
        return if (head.isEmpty()) "Kho đang trống." else "Một vài câu trong kho:\n• " + head.joinToString("\n• ")
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun lpTop(marginDp: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(marginDp)
        }
}
