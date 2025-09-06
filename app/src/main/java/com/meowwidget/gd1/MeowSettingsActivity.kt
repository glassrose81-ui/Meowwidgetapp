
package com.meowwidget.gd1

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MeowSettingsActivity : AppCompatActivity() {
    private lateinit var tvToday: TextView
    private lateinit var pref: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meow_settings)

        tvToday = findViewById(R.id.tvToday)
        pref = getSharedPreferences("MeowPrefs", Context.MODE_PRIVATE)

        updateTodayQuote()
    }

    private fun updateTodayQuote() {
        val list = loadQuotes() // danh sách câu trích dẫn
        if (list.isEmpty()) return

        val slots = parseSlots(pref.getString(KEY_SLOTS, "08:00,17:00,20:00") ?: "")
        val slotIdxToday = currentSlotIndex(nowMinutes(), slots)

        // --- Neo ngày & offset ---
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()).toInt()
        val anchorDay = pref.getInt("anchor_day", today)
        var anchorOffset = pref.getInt("anchor_offset", Int.MIN_VALUE)

        val daysSince = today - anchorDay
        val steps = daysSince * slots.size + slotIdxToday

        // nếu lần đầu thì đặt offset để giữ nguyên câu hiện tại
        if (anchorOffset == Int.MIN_VALUE) {
            anchorOffset = 0
            pref.edit()
                .putInt("anchor_day", today)
                .putInt("anchor_offset", anchorOffset)
                .apply()
        }

        val idx = ((steps + anchorOffset) % list.size + list.size) % list.size
        tvToday.text = list[idx]
    }

    private fun loadQuotes(): List<String> {
        // Tải danh sách quotes từ file hoặc SharedPreferences (giữ nguyên logic cũ)
        return listOf("Câu A", "Câu B", "Câu C")
    }

    private fun nowMinutes(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    private fun parseSlots(s: String): List<Pair<Int, Int>> {
        return s.split(",").mapNotNull {
            val parts = it.split(":")
            if (parts.size == 2) {
                val h = parts[0].toIntOrNull()
                val m = parts[1].toIntOrNull()
                if (h != null && m != null) h * 60 + m to 0 else null
            } else null
        }
    }

    private fun currentSlotIndex(now: Int, slots: List<Pair<Int, Int>>): Int {
        if (slots.isEmpty()) return 0
        for ((i, s) in slots.withIndex()) {
            if (now < s.first) return if (i == 0) slots.size - 1 else i - 1
        }
        return slots.size - 1
    }

    companion object {
        const val KEY_SLOTS = "KEY_SLOTS"
    }
}
