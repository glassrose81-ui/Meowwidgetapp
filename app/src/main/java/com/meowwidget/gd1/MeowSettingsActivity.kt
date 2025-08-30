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

class MeowSettingsActivity : AppCompatActivity() {

    private val PREF = "meow_settings"
    private val KEY_SOURCE = "source"
    private val KEY_SLOTS = "slots"
    private val KEY_ADDED = "added_lines"
    private val KEY_FAVS = "favs"
    private val KEY_PLAN_DAY = "plan_day"
    private val KEY_PLAN_IDX = "plan_idx"

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

        container.addView(sectionTitle("Danh sách"))
        container.addView(View(this), ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, titleSpacing()))

        tvDefaultCount = TextView(this).apply { textSize = 18f; setTypeface(typeface, android.graphics.Typeface.BOLD) }
        tvAddedCount = TextView(this).apply { textSize = 18f; setTypeface(typeface, android.graphics.Typeface.BOLD) }
        container.addView(tvDefaultCount)
        container.addView(labelSmall("– Ví dụ: Đừng đếm những vì sao đã tắt..."))
        container.addView(labelSmall("– Ví dụ: Mỗi sớm mai thức dậy..."))
        val rowAllD = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rowAllD.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
        val tvAllDefault = TextView(this).apply { text = "Xem tất cả"; setTextColor(blue) }
        rowAllD.addView(tvAllDefault); container.addView(rowAllD)
        container.addView(TextView(this).apply { text = "• Bạn thêm (xem/xoá)"; textSize = 18f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
        val rowAllA = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rowAllA.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
        val tvAllAdded = TextView(this).apply { text = "Xem tất cả"; setTextColor(blue) }
        rowAllA.addView(tvAllAdded); container.addView(rowAllA)

        setContentView(root)

        val pref = getSharedPreferences(PREF, MODE_PRIVATE)
        val src = pref.getString(KEY_SOURCE, "all") ?: "all"
        setSourceUI(src)

        val slots = (pref.getString(KEY_SLOTS, "08:00,17:00,20:00") ?: "08:00,17:00,20:00").split(",")
        etH1.setText(slots.getOrNull(0) ?: "")
        etH2.setText(slots.getOrNull(1) ?: "")
        etH3.setText(slots.getOrNull(2) ?: "")

        refreshCountsAndToday()

        btnAll.setOnClickListener { pref.edit().putString(KEY_SOURCE, "all").apply(); setSourceUI("all"); refreshCountsAndToday() }
        btnFav.setOnClickListener { pref.edit().putString(KEY_SOURCE, "fav").apply(); setSourceUI("fav"); refreshCountsAndToday() }

        btnSaveTime.setOnClickListener {
            val s = parseSlotsToString(etH1.text.toString(), etH2.text.toString(), etH3.text.toString())
            pref.edit().putString(KEY_SLOTS, s).apply()
            toast("Đã lưu mốc: $s")
            refreshCountsAndToday()
        }

        btnPaste.setOnClickListener { openPasteDialog() }
        btnPickTxt.setOnClickListener { pickTxt() }

        btnFavToday.setOnClickListener {
            val q = tvToday.text.toString()
            toggleFav(q); refreshCountsAndToday()
        }

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

    private fun openPasteDialog() {
        val input = EditText(this).apply { hint = "Mỗi dòng là 1 câu"; minLines = 5; gravity = Gravity.TOP or Gravity.START }
        AlertDialog.Builder(this)
            .setTitle("Dán quote")
            .setView(input)
            .setPositiveButton("Thêm") { _, _ ->
                val raw = input.text?.toString() ?: ""
                val added = addLines(raw)
                toast("Đã thêm: $added")
                refreshCountsAndToday()
            }
            .setNegativeButton("Huỷ", null)
            .show()
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
        val t = hhmm.trim(); val ok = Regex(r"^\d{1,2}:\d{2}$")
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
        var idx = 0; for (i in slots.indices) if (nowM >= slots[i]) idx = i
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
