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
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import android.view.WindowManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName


class MeowSettingsActivity : AppCompatActivity() {

    private val PREF = "meow_settings"
    private val KEY_SOURCE = "source"           // "all" | "fav"
    private val KEY_SLOTS = "slots"             // "08:00,17:00,20:00"
    private val KEY_ADDED = "added_lines"       // text joined by '\n'
    private val KEY_FAVS = "favs"               // text joined by '\n' (quotes)
    private val KEY_PLAN_DAY = "plan_day"       // ddMMyy
    private val KEY_PLAN_IDX = "plan_idx"       // Int
    // NEW (anchor-based): tránh reset/dừng vòng
    private val KEY_ANCHOR_DAY = "anchor_day"       // yyyyMMdd
    private val KEY_ANCHOR_OFFSET = "anchor_offset" // Int

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

        // UI cơ bản (không dùng layout XML)
        val blue = 0xFF2F80ED.toInt()
        val root = ScrollView(this).apply { isFillViewport = true }
        root.setBackgroundResource(R.drawable.bg_settings_cotton)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        root.addView(container, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        fun sectionTitle(text: String) = TextView(this).apply {
            this.text = text
            setTextColor(0xFF111111.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        fun titleSpacing(): Int = (screenH()*0.04f).toInt()
        fun sectionGap(): Int = (screenH()*0.08f).toInt()

        fun pillButton(text: String, solid: Boolean) = Button(this).apply {
            this.text = text
            setTextColor(if (solid) 0xFFFFFFFF.toInt() else blue)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
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
   val btnDecor = pillButton("Trang trí Widget", true)
btnDecor.setOnClickListener { startActivity(android.content.Intent(this, com.meowwidget.gd1.ui.decor.WidgetDecorActivity::class.java)) }
container.addView(btnDecor)


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

        val tvAllDefault = TextView(this).apply { text = "Xem tất cả"; setTextColor(0xFF2F80ED.toInt()) }
        val rowAllD = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rowAllD.addView(tvDefaultCount, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rowAllD.addView(tvAllDefault)
        container.addView(rowAllD)

        container.addView(labelSmall("– Ví dụ: Đừng đếm những vì sao đã tắt..."))
        container.addView(labelSmall("– Ví dụ: Mỗi sớm mai thức dậy..."))

        val tvAllAdded = TextView(this).apply { text = "Xem tất cả"; setTextColor(0xFF2F80ED.toInt()) }
        val rowAdded = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rowAdded.addView(tvAddedCount, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rowAdded.addView(tvAllAdded)
        container.addView(rowAdded)

        // Yêu thích: dòng "Xem tất cả"
        val tvFavTitle = TextView(this).apply {
            text = "• Yêu thích"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val tvAllFav = TextView(this).apply { text = "Xem tất cả"; setTextColor(0xFF2F80ED.toInt()) }
        val rowFav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rowFav.addView(tvFavTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rowFav.addView(tvAllFav)
        container.addView(rowFav)
        tvAllFav.setOnClickListener {
            startActivity(Intent(this, QuotesListActivity::class.java).putExtra("mode", "fav"))
        }

        setContentView(root)

        // ===== Data binding =====
        val pref = getSharedPreferences(PREF, MODE_PRIVATE)
        val src = pref.getString(KEY_SOURCE, "all") ?: "all"
        setSourceUI(src)

        val slots = (pref.getString(KEY_SLOTS, "08:00,17:00,20:00") ?: "08:00,17:00,20:00").split(",")
        etH1.setText(slots.getOrNull(0) ?: "")
        etH2.setText(slots.getOrNull(1) ?: "")
        etH3.setText(slots.getOrNull(2) ?: "")

        refreshCountsAndToday()

        val btnAllRef = (rowSource.getChildAt(0) as Button)
        val btnFavRef = (rowSource.getChildAt(2) as Button)
        btnAllRef.setOnClickListener {
            pref.edit().putString(KEY_SOURCE, "all").apply()
            setSourceUI("all"); refreshCountsAndToday()
        }
        btnFavRef.setOnClickListener {
            pref.edit().putString(KEY_SOURCE, "fav").apply()
            setSourceUI("fav"); refreshCountsAndToday()
        }

        val btnSaveTimeRef = (rowTitleTime.getChildAt(1) as Button)
        btnSaveTimeRef.setOnClickListener {
            val s = parseSlotsToString(etH1.text.toString(), etH2.text.toString(), etH3.text.toString())
            pref.edit().putString(KEY_SLOTS, s).apply()
            val mgr = AppWidgetManager.getInstance(this)
val ids = mgr.getAppWidgetIds(ComponentName(this, MeowQuoteWidget::class.java))
sendBroadcast(Intent(this, MeowQuoteWidget::class.java).apply {
    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
})

            toast("Đã lưu mốc: $s")
            // Cập nhật anchor_day để giữ ổn định mapping khi đổi lịch
            val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val todayStr = sdf.format(Date())
            pref.edit().putString(KEY_ANCHOR_DAY, todayStr).apply()
            refreshCountsAndToday()
        }

        // Dán / Nạp TXT
        btnPaste.setOnClickListener { openPasteDialog { added ->
            toast("Đã thêm: $added"); refreshCountsAndToday()
        } }
        btnPickTxt.setOnClickListener { pickTxt() }

        // Yêu thích câu hôm nay
        btnFavToday.setOnClickListener {
            val q = tvToday.text.toString()
            val wasFav = (getSharedPreferences(PREF, MODE_PRIVATE).getString(KEY_FAVS, "") ?: "")
                .split("\n").contains(q)
            toggleFav(q)
            toast(if (wasFav) "Tạm biệt câu Yêu thích" else "Yêu câu này lắm nhoa, Meow")
            refreshCountsAndToday()
        }

        // Xem danh sách
        tvAllDefault.setOnClickListener {
            startActivity(Intent(this, QuotesListActivity::class.java).putExtra("mode", "default"))
        }
        tvAllAdded.setOnClickListener {
            startActivity(Intent(this, QuotesListActivity::class.java).putExtra("mode", "added"))
        }
    }

    private fun setSourceUI(src: String) {
        tvSourceNow.text = "Nguồn đang dùng: " + if (src == "fav") "Yêu thích" else "Tất cả"
    }

    private fun openPasteDialog(onDone: (Int)->Unit) {
        val input = EditText(this).apply { hint = "Mỗi dòng là 1 câu"; minLines = 5; gravity = Gravity.TOP or Gravity.START }
        val dlg = AlertDialog.Builder(this)
            .setTitle("Dán quote")
            .setView(input)
            .setPositiveButton("Thêm") { _, _ -> onDone(addLines(input.text?.toString() ?: "")) }
            .setNegativeButton("Huỷ", null)
            .create()
        dlg.setOnShowListener {
            dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dlg.show()
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
            val uri = data?.data ?: return
            val content = readTextFromUri(uri)
            val added = addLines(content)
            toast("Nạp tệp: +$added")
            refreshCountsAndToday()
        }
    }

    private fun readTextFromUri(uri: Uri): String {
        return try {
            contentResolver.openInputStream(uri)?.use { ins ->
                BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).readText()
            } ?: ""
        } catch (_: Exception) { "" }
    }

    private fun addLines(raw: String): Int {
        val lines = raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (lines.isEmpty()) return 0
        val pref = getSharedPreferences(PREF, MODE_PRIVATE)
        val cur = pref.getString(KEY_ADDED, "") ?: ""
        val curList = if (cur.isEmpty()) mutableListOf<String>() else cur.split("\n").toMutableList()
        var added = 0
        for (l in lines) if (!curList.contains(l)) { curList.add(l); added++ }
        pref.edit().putString(KEY_ADDED, curList.joinToString("\n")).apply()
        return added
    }

    private fun toggleFav(q: String) {
        val pref = getSharedPreferences(PREF, MODE_PRIVATE)
        val set = (pref.getString(KEY_FAVS, "") ?: "")
            .split("\n").filter { it.isNotEmpty() }.toMutableSet()
        if (set.contains(q)) set.remove(q) else set.add(q)
        pref.edit().putString(KEY_FAVS, set.joinToString("\n")).apply()
    }

    private fun refreshCountsAndToday() {
        val defaults = loadDefaultQuotes()
        val added = getAddedList()
        tvDefaultCount.text = "• Mặc định  — (Tổng: ${defaults.size})"
        tvAddedCount.text = "• Quote thêm  — (Tổng: ${added.size})"

        val pref = getSharedPreferences(PREF, MODE_PRIVATE)
        val src = pref.getString(KEY_SOURCE, "all") ?: "all"
        val favs = (pref.getString(KEY_FAVS, "") ?: "").split("\n").filter { it.isNotEmpty() }
        val allAll = (defaults + added).distinct()
        val list = if (src == "fav") favs else allAll
        if (list.isEmpty()) {
            tvToday.text = "Chưa có câu nào. Hãy dán hoặc nạp .TXT."
            btnFavToday.isEnabled = false
            return
        } else btnFavToday.isEnabled = true

        // --- NEW: Tính "bước" theo ngày + mốc (không chạy nền), tránh reset/dừng
        val slotsList = parseSlots((pref.getString(KEY_SLOTS, "08:00,17:00,20:00") ?: ""))
        val slotIdxToday = currentSlotIndex(nowMinutes(), slotsList)

        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val todayStr = sdf.format(Date())
        var anchorDay = pref.getString(KEY_ANCHOR_DAY, null)
        var anchorOffset = pref.getInt(KEY_ANCHOR_OFFSET, 0)

        if (anchorDay == null) {
            anchorDay = todayStr
            // Offset mặc định 0: hiển thị đúng câu theo mốc hiện tại, không nhảy
            pref.edit()
                .putString(KEY_ANCHOR_DAY, anchorDay)
                .putInt(KEY_ANCHOR_OFFSET, anchorOffset)
                .apply()
        }

        fun daysBetween(a: String, b: String): Long {
            return try {
                val fmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                val da = fmt.parse(a); val db = fmt.parse(b)
                val one = 24L * 60L * 60L * 1000L
                ((db!!.time / one) - (da!!.time / one))
            } catch (_: Exception) { 0L }
        }

        val slotsPerDay = max(1, slotsList.size)
        val days = daysBetween(anchorDay ?: todayStr, todayStr)
        var steps = days * slotsPerDay + slotIdxToday  // Long
val firstSlot = slotsList.minOrNull() ?: 0
val nowM = nowMinutes()
if (slotsList.isNotEmpty() && nowM < firstSlot) {
    // Đang trước mốc đầu của "ngày mới" → vẫn tính thuộc ngày hôm qua
    steps -= slotsPerDay.toLong()
}

        val idx = ((steps + anchorOffset).toInt() % list.size + list.size) % list.size
        tvToday.text = list[idx]
    }

    private fun loadDefaultQuotes(): List<String> = try {
        assets.open("quotes_default.txt").use { ins ->
            BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).readLines().map { it.trim() }.filter { it.isNotEmpty() }
        }
    } catch (_: Exception) { emptyList() }

    private fun getAddedList(): List<String> {
        val cur = getSharedPreferences(PREF, MODE_PRIVATE).getString(KEY_ADDED, "") ?: ""
        return if (cur.isEmpty()) emptyList() else cur.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun parseSlotsToString(a: String, b: String, c: String): String {
        val list = parseSlots("$a,$b,$c").take(3)
        return list.joinToString(",") { mmToHHMM(it) }
    }
    private fun parseSlots(s: String): List<Int> {
        val parts = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val out = mutableListOf<Int>()
        for (p in parts) {
            val mm = parseHHMM(p); if (mm != null) out.add(mm)
        }
        return out.distinct().sorted()
    }
    private fun parseHHMM(hhmm: String): Int? {
        val t = hhmm.trim(); val ok = Regex("""^\d{1,2}:\d{2}$""")
        if (!ok.matches(t)) return null
        val h = t.substringBefore(":").toIntOrNull() ?: return null
        val m = t.substringAfter(":").toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h*60 + m
    }
    private fun mmToHHMM(mm: Int): String {
        val h = (mm/60).toString().padStart(2,'0'); val m = (mm%60).toString().padStart(2,'0')
        return "$h:$m"
    }
    private fun nowMinutes(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY)*60 + cal.get(Calendar.MINUTE)
    }
    private fun currentSlotIndex(nowM: Int, slots: List<Int>): Int {
        if (slots.isEmpty()) return 0
        val s = slots.sorted()
        if (nowM < s.first()) return s.size - 1
        var idx = 0
        for (i in s.indices) {
            if (nowM >= s[i]) idx = i else break
        }
        return idx
    }

    private fun ensurePlanBase(size: Int): Int {
        val pref = getSharedPreferences(PREF, MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("ddMMyy", java.util.Locale.getDefault()).format(java.util.Date())
        val oldDay = pref.getString(KEY_PLAN_DAY, null)
        var base = kotlin.math.max(0, pref.getInt(KEY_PLAN_IDX, -1))
        if (oldDay == null) base = 0
        else if (oldDay != today) base = (base + 1) % kotlin.math.max(1, size)
        pref.edit().putString(KEY_PLAN_DAY, today).putInt(KEY_PLAN_IDX, base).apply()
        return base
    }

    private fun dp(v: Int): Int { val d = resources.displayMetrics.density; return (v*d).toInt() }
    private fun screenH(): Int = resources.displayMetrics.heightPixels
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
