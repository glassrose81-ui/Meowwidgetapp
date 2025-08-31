package com.meowwidget.gd1

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

class MeowSettingsActivity : AppCompatActivity() {

    private val PREF = "meow_settings"
    private val KEY_SOURCE = "source"           // "all" | "fav"
    private val KEY_SLOTS = "slots"             // "08:00,17:00,20:00"
    private val KEY_ADDED = "added_lines"       // text joined by '\n'
    private val KEY_FAVS = "favs"               // text joined by '\n' (quotes)
    private val KEY_PLAN_DAY = "plan_day"       // ddMMyy
    private val KEY_PLAN_IDX = "plan_idx"       // Int

    private lateinit var tvSourceNow: TextView
    private lateinit var etH1: EditText
    private lateinit var etH2: EditText
    private lateinit var etH3: EditText
    private lateinit var tvToday: TextView
    private lateinit var btnFavToday: Button
    private lateinit var tvDefaultCount: TextView
    private lateinit var tvAddedCount: TextView

    private val REQ_PICK_TXT = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val blue = 0xFF2F80ED.toInt()
        val root = ScrollView(this).apply { isFillViewport = true }
        // Nền bông gòn (đổi tên drawable theo file của bạn, không kèm .png)
        try { root.setBackgroundResource(resources.getIdentifier("bg_cotton", "drawable", packageName)) } catch (_: Exception){}
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        root.addView(container, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        fun sectionTitle(text: String) = TextView(this).apply {
            this.text = text
            setTextColor(0xFF111111.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        fun titleSpacing(): Int = dp(12)
        fun sectionGap(): Int = dp(32)

        fun pillButton(text: String, solid: Boolean) = Button(this).apply {
            this.text = text
            setTextColor(if (solid) 0xFFFFFFFF.toInt() else blue)
            setTextSize(TypedValue.COMPLE_UNIT_SP, 18f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = if (solid) {
                android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(26).toFloat(); setColor(blue)
                }
            } else {
                android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(26).toFloat(); setStroke(dp(2), blue); setColor(0x00000000)
                }
            }
            minHeight = dp(48); minWidth = dp(120)
        }
        fun timeBox(hint: String) = EditText(this).apply {
            setHint(hint); setTextColor(0xFF111111.toInt()); setHintTextColor(0xFF666666.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); inputType = InputType.TYPE_CLASS_DATETIME
            filters = arrayOf(InputFilter.LengthFilter(5)); gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(14).toFloat(); setStroke(dp(2), blue); setColor(0x00000000)
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        fun labelSmall(t: String) = TextView(this).apply {
            text = t; setTextColor(0xFF606060.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }

        val header = TextView(this).apply {
            text = "Meow Settings — hệ thống"; setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(blue); setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        container.addView(header, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // 1) Nguồn hiển thị
        container.addView(sectionTitle("Nguồn hiển thị"))
        container.addView(View(this), ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, titleSpacing()))

        val rowSource = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnAll = pillButton("Tất cả", true)
        val btnFav = pillButton("Yêu thích", false)
        rowSource.addView(btnAll)
        rowSource.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
        rowSource.addView(btnFav)
        container.addView(rowSource)

        tvSourceNow = labelSmall("Nguồn đang dùng: …")
        container.addView(tvSourceNow)

        container.addView(View(this), ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, sectionGap()))

        // 2) Mốc giờ
        val rowTitleTime = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        rowTitleTime.addView(sectionTitle("Mốc giờ (tối đa 3)"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val btnSaveTime = pillButton("Lưu mốc", true)
        rowTitleTime.addView(btnSaveTime)
        container.addView(rowTitleTime)

        container.addView(View(this), ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, titleSpacing()))

        val rowTime = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        etH1 = timeBox("08:00"); etH2 = timeBox("17:00"); etH3 = timeBox("20:00")
        rowTime.addView(etH1, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rowTime.addView(Space(this), ViewGroup.LayoutParams(dp(12), 1))
        rowTime.addView(etH2, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rowTime.addView(Space(this), ViewGroup.LayoutParams(dp(12), 1))
        rowTime.addView(etH3, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        container.addView(rowTime)

        container.addView(View(this), ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, sectionGap()))

        // 3) Nhập dữ liệu
        container.addView(sectionTitle("Nhập dữ liệu"))
        container.addView(View(this), ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, titleSpacing()))

        val rowImport = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnPaste = pillButton("Dán quote", true)
        val btnPickTxt = pillButton("Chọn tệp .TXT", false)
        rowImport.addView(btnPaste, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rowImport.addView(Space(this), ViewGroup.LayoutParams(dp(12), 1))
        rowImport.addView(btnPickTxt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        container.addView(rowImport)
        container.addView(labelSmall("(Bấm để mở khung dán)"))

        container.addView(View(this), ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, sectionGap()))

        // 4) Câu hôm nay
        container.addView(sectionTitle("Câu hôm nay"))
        container.addView(View(this), ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, titleSpacing()))

        tvToday = TextView(this).apply {
            textSize = 18f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(0xFF111111.toInt())
        }
        container.addView(tvToday)

        val rowFavToday = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rowFavToday.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
        btnFavToday = pillButton("🐾  Yêu thích", false)
        rowFavToday.addView(btnFavToday)
        container.addView(rowFavToday)

        container.addView(View(this), ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, sectionGap()))

        // 5) Danh sách
        container.addView(sectionTitle("Danh sách"))
        container.addView(View(this), ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, titleSpacing()))

        tvDefaultCount = TextView(this).apply { textSize = 18f; setTypeface(typeface, android.graphics.Typeface.BOLD) }
        tvAddedCount = TextView(this).apply { textSize = 18f; setTypeface(typeface, android.graphics.Typeface.BOLD) }
        container.addView(tvDefaultCount)
        // mẫu ví dụ ngắn
        container.addView(labelSmall("– Ví dụ: Đừng đếm những vì sao đã tắt..."))
        container.addView(labelSmall("– Ví dụ: Mỗi sớm mai thức dậy..."))
        // Hàng "Xem tất cả" cho Mặc định (căn phải)
        run {
            val rowAllD = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowAllD.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
            val tvAllDefault = TextView(this).apply {
                text = "Xem tất cả"; setTextColor(blue); setOnClickListener {
                    startActivity(Intent(this@MeowSettingsActivity, QuotesListActivity::class.java).putExtra("mode", "default"))
                }
            }
            rowAllD.addView(tvAllDefault)
            container.addView(rowAllD)
        }

        // "Bạn thêm" + xem tất cả
        container.addView(TextView(this).apply {
            text = "• Bạn thêm"; textSize = 18f; setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        run {
            val rowAllA = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowAllA.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
            val tvAllAdded = TextView(this).apply {
                text = "Xem tất cả"; setTextColor(blue); setOnClickListener {
                    startActivity(Intent(this@MeowSettingsActivity, QuotesListActivity::class.java).putExtra("mode", "added"))
                }
            }
            rowAllA.addView(tvAllAdded)
            container.addView(rowAllA)
        }

        // --- Yêu thích (xem/xoá)
        container.addView(TextView(this).apply {
            text = "• Yêu thích (xem/xoá)"; textSize = 18f; setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        run {
            val rowAllF = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowAllF.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
            val tvAllFav = TextView(this).apply {
                text = "Xem tất cả"; setTextColor(0xFF2F80ED.toInt()); setOnClickListener {
                    val favCount = readFav().size
                    if (favCount == 0) toast("Chưa có câu nào trong Yêu thích")
                    else startActivity(Intent(this@MeowSettingsActivity, QuotesListActivity::class.java).putExtra("mode", "fav"))
                }
            }
            rowAllF.addView(tvAllFav)
            container.addView(rowAllF)
        }

        // Handlers
        val src = getSharedPreferences(PREF, MODE_PRIVATE).getString(KEY_SOURCE, "all") ?: "all"
        setSourceUI(src)
        btnAll.setOnClickListener { saveSource("all"); setSourceUI("all"); refreshCountsAndToday() }
        btnFav.setOnClickListener { saveSource("fav"); setSourceUI("fav"); refreshCountsAndToday() }

        val slots = (getSharedPreferences(PREF, MODE_PRIVATE).getString(KEY_SLOTS, "08:00,17:00,20:00") ?: "08:00,17:00,20:00").split(",")
        etH1.setText(slots.getOrNull(0) ?: "08:00")
        etH2.setText(slots.getOrNull(1) ?: "17:00")
        etH3.setText(slots.getOrNull(2) ?: "20:00")
        val btnSaveTime = (rowTitleTime.getChildAt(1) as Button)
        btnSaveTime.setOnClickListener {
            val s1 = etH1.text.toString().trim()
            val s2 = etH2.text.toString().trim()
            val s3 = etH3.text.toString().trim()
            val saved = listOf(s1, s2, s3).filter { it.matches(Regex("^\\d{2}:\\d{2}$")) && it != "00:00" }
            getSharedPreferences(PREF, MODE_PRIVATE).edit().putString(KEY_SLOTS, saved.joinToString(",")).apply()
            toast("Đã lưu mốc giờ"); refreshCountsAndToday()
        }

        btnPaste.setOnClickListener { openPasteDialog() }
        btnPickTxt.setOnClickListener { pickTxt() }

        btnFavToday.setOnClickListener {
            val q = tvToday.text.toString()
            val wasFav = readFav().contains(q)
            toggleFav(q)
            toast(if (wasFav) "Đã bỏ khỏi Yêu thích" else "Đã thêm vào Yêu thích")
            refreshCountsAndToday()
        }

        refreshCountsAndToday()
    }

    private fun openPasteDialog() {
        val input = EditText(this).apply { hint = "Mỗi dòng 1 câu"; minLines = 6; maxLines = 12 }
        val builder = AlertDialog.Builder(this)
            .setTitle("Dán quote")
            .setView(input)
            .setPositiveButton("Lưu") { d, _ ->
                val text = input.text.toString()
                val lines = text.split(Regex("\\r?\\n")).map { it.trim().trim('"') }.filter { it.isNotEmpty() }
                if (lines.isNotEmpty()) {
                    val cur = readAdded().toMutableList(); cur.addAll(lines); saveAdded(cur)
                    toast("Đã lưu +${lines.size} câu"); refreshCountsAndToday()
                } else toast("Chưa có nội dung để lưu")
                d.dismiss()
            }
            .setNegativeButton("Huỷ", null)

        builder.create().also { dlg ->
            dlg.setOnShowListener {
                dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
            dlg.show()
        }
    }

    private fun pickTxt() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "text/*"
        }
        startActivityForResult(intent, REQ_PICK_TXT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_TXT && resultCode == Activity.RESULT_OK) {
            val uri: Uri = data?.data ?: return
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val sb = StringBuilder()
            BufferedReader(InputStreamReader(contentResolver.openInputStream(uri)!!)).use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) sb.append(line).append('\n')
            }
            val lines = sb.toString().split(Regex("\\r?\\n")).map { it.trim().trim('"') }.filter { it.isNotEmpty() }
            if (lines.isNotEmpty()) {
                val cur = readAdded().toMutableList(); cur.addAll(lines); saveAdded(cur)
                toast("Đã nạp +${lines.size} câu từ tệp"); refreshCountsAndToday()
            } else toast("Tệp trống")
        }
    }

    private fun setSourceUI(src: String) {
        tvSourceNow.text = "Nguồn đang dùng: " + if (src == "fav") "Yêu thích" else "Tất cả"
    }

    private fun refreshCountsAndToday() {
        tvDefaultCount.text = "• Mặc định: ${readDefault().size} câu"
        tvAddedCount.text = "• Bạn thêm: ${readAdded().size} câu"
        tvToday.text = getTodayQuote()
    }

    private fun getTodayQuote(): String {
        val src = getSharedPreferences(PREF, MODE_PRIVATE).getString(KEY_SOURCE, "all") ?: "all"
        val list = if (src == "fav") readFav() else (readDefault() + readAdded())
        if (list.isEmpty()) return if (src == "fav") "Chưa có câu nào trong Yêu thích" else "Chưa có dữ liệu"
        val now = Calendar.getInstance()
        val today = java.text.SimpleDateFormat("ddMMyy", java.util.Locale.getDefault()).format(now.time)
        val prefs = getSharedPreferences(PREF, MODE_PRIVATE)
        val planDay = prefs.getString(KEY_PLAN_DAY, null)
        var idx = prefs.getInt(KEY_PLAN_IDX, 0)
        if (planDay == null || planDay != today) {
            prefs.edit().putString(KEY_PLAN_DAY, today).apply()
        }
        val slotIdx = currentSlotIndex()
        val pos = ((idx + slotIdx) % list.size + list.size) % list.size
        return list[pos]
    }

    private fun currentSlotIndex(): Int {
        val slots = (getSharedPreferences(PREF, MODE_PRIVATE).getString(KEY_SLOTS, "08:00,17:00,20:00") ?: "08:00,17:00,20:00")
            .split(",").mapNotNull { hhmmToMinutes(it.trim()) }
        if (slots.isEmpty()) return 0
        val nowMin = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        var idx = 0
        for (i in slots.indices) if (nowMin >= slots[i]) idx = i
        return idx
    }

    private fun hhmmToMinutes(s: String): Int? {
        val m = Regex("^(\\d{2}):(\\d{2})$").matchEntire(s) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val mi = m.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || mi !in 0..59) return null
        return h * 60 + mi
    }

    private fun saveSource(s: String) {
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putString(KEY_SOURCE, s).apply()
    }

    private fun readAdded(): List<String> {
        val t = getSharedPreferences(PREF, MODE_PRIVATE).getString(KEY_ADDED, "") ?: ""
        return t.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun saveAdded(list: List<String>) {
        getSharedPreferences(PREF, MODE_PRIVATE).edit()
            .putString(KEY_ADDED, list.joinToString("\n")).apply()
    }

    private fun readFav(): List<String> {
        val t = getSharedPreferences(PREF, MODE_PRIVATE).getString(KEY_FAVS, "") ?: ""
        return t.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun toggleFav(q: String) {
        val cur = readFav().toMutableList()
        if (cur.contains(q)) cur.remove(q) else cur.add(q)
        getSharedPreferences(PREF, MODE_PRIVATE).edit()
            .putString(KEY_FAVS, cur.joinToString("\n")).apply()
    }

    private fun readDefault(): List<String> {
        return try {
            assets.open("QUOTE.txt").bufferedReader(Charsets.UTF_8).useLines { seq ->
                seq.map { it.trim().trim('"') }.filter { it.isNotEmpty() }.toList()
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun dp(v: Int): Int = (resources.displayMetrics.density * v).toInt()

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
