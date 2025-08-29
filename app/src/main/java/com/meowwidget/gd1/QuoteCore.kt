package com.meowwidget.gd1
// GĐ2-B1 — Quote import core (txt & paste) — nhẹ, an toàn, không đụng GĐ1
// Quy ước dữ liệu: mỗi dòng = 1 câu. Không tách tác giả. Lọc trùng theo chữ đã chuẩn hoá (trim + gộp khoảng trắng + không phân biệt hoa–thường).
// Lưu trữ: file nội bộ "quotes_user.txt" trong thư mục dữ liệu của ứng dụng. Không dùng mạng, không thu thập dữ liệu.
// Tích hợp mặc định: có thể gộp với bộ câu mặc định (nếu về sau có file "quotes_default.txt" trong assets). Nếu không có, vẫn chạy bình thường.

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.nio.charset.Charset

object QuoteCore {

    // Tên file lưu dữ liệu người dùng (mỗi dòng = 1 câu)
    private const val USER_FILE = "quotes_user.txt"
    // (Tùy chọn về sau) Tên file assets chứa câu mặc định. Không bắt buộc tồn tại.
    private const val DEFAULT_ASSET = "quotes_default.txt"

    data class ImportReport(
        val added: Int,        // số câu mới đã thêm
        val duplicates: Int,   // số câu bị bỏ vì trùng (trong lần nhập này hoặc trùng với kho hiện có)
        val totalUser: Int,    // tổng câu do bạn thêm (sau khi nhập)
        val totalAll: Int      // tổng câu dùng để hiển thị (mặc định + bạn thêm, đã khử trùng)
    )

    // Nhập bằng cách dán trực tiếp (mỗi dòng = 1 câu)
    fun importFromPlainText(ctx: Context, raw: String): ImportReport {
        val batch = parseLines(raw)
        return mergeIntoUserStore(ctx, batch)
    }

    // Nhập từ file .txt do bạn chọn (qua SAF). Không yêu cầu quyền đặc biệt.
    fun importFromTxtUri(ctx: Context, uri: Uri): ImportReport {
        val text = try {
            ctx.contentResolver.openInputStream(uri)?.bufferedReader(Charset.forName("UTF-8"))?.use { it.readText() }
        } catch (_: Exception) {
            null
        }
        val batch = parseLines(text ?: "")
        return mergeIntoUserStore(ctx, batch)
    }

    // Lấy toàn bộ câu để hiển thị (mặc định + bạn thêm), đã khử trùng và giữ thứ tự ổn định.
    fun getAllQuotes(ctx: Context): List<String> {
        val user = loadUserQuotes(ctx)
        val def = loadDefaultQuotesIfAny(ctx)
        return mergeDedup(def, user)
    }

    // Lấy riêng các câu do bạn thêm
    fun getUserQuotes(ctx: Context): List<String> = loadUserQuotes(ctx)

    // Số liệu nhanh
    fun getCounts(ctx: Context): Pair<Int, Int> {
        val user = loadUserQuotes(ctx).size
        val all = getAllQuotes(ctx).size
        return user to all
    }

    // --------------------------
    // Phần xử lý bên trong
    // --------------------------

    // Đọc từng dòng, làm sạch, loại dòng rỗng; khử trùng trong chính lô nhập (để không đếm trùng 2 lần)
    private fun parseLines(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val seq = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { collapseSpaces(it) }
        // khử trùng trong lô theo key chuẩn hoá
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (q in seq) {
            val k = normKey(q)
            if (seen.add(k)) out.add(q)
        }
        return out
    }

    // Gộp vào kho người dùng, khử trùng so với kho hiện có; trả về thống kê
    private fun mergeIntoUserStore(ctx: Context, batch: List<String>): ImportReport {
        val existing = loadUserQuotes(ctx)
        if (batch.isEmpty()) {
            val totals = getAllQuotes(ctx).size
            return ImportReport(added = 0, duplicates = 0, totalUser = existing.size, totalAll = totals)
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
        return ImportReport(added = added, duplicates = dupThisBatch, totalUser = out.size, totalAll = totals)
    }

    // Nén khoảng trắng trong câu: mọi chuỗi trắng thành 1 khoảng trắng đơn
    private fun collapseSpaces(s: String): String = s.replace(Regex("\\s+"), " ").trim()

    // Tạo key chuẩn hoá để so trùng: trim + nén khoảng trắng + lower-case (không phân biệt hoa-thường)
    private fun normKey(s: String): String = collapseSpaces(s).lowercase()

    // Đọc kho người dùng (quotes_user.txt), mỗi dòng 1 câu; nếu chưa có file thì trả về rỗng
    private fun loadUserQuotes(ctx: Context): List<String> {
        return try {
            val f = File(ctx.filesDir, USER_FILE)
            if (!f.exists()) return emptyList()
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

    // Lưu kho người dùng
    private fun saveUserQuotes(ctx: Context, quotes: List<String>) {
        val f = File(ctx.filesDir, USER_FILE)
        // bảo toàn thứ tự; mỗi dòng 1 câu
        f.writeText(quotes.joinToString(separator = "\n"), Charset.forName("UTF-8"))
    }

    // Tải kho mặc định từ assets nếu có (không bắt buộc). Nếu không có, trả về rỗng.
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

    // Gộp 2 danh sách và khử trùng theo thứ tự: mặc định trước, người dùng sau.
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
        pushAll(a)
        pushAll(b)
        return out
    }
}
