package com.meowwidget.gd1

import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import android.widget.ArrayAdapter
import android.widget.Toast


class QuotesListActivity : AppCompatActivity() {
    private val PREF = "meow_settings"
    private val KEY_ADDED = "added_lines"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mode = intent.getStringExtra("mode") ?: "default"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val list = ListView(this)
        root.addView(list, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        // Dữ liệu: luôn là MutableList để đưa thẳng vào ArrayAdapter
        val data: MutableList<String> = if (mode == "added") {
            getAdded()
        } else {
            loadDefaultMutable()
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, data)
        list.adapter = adapter
        else if (mode == "fav") {
    val sp = getSharedPreferences(PREF, MODE_PRIVATE)
    // Lấy toàn bộ quote (mặc định + bạn thêm), lọc những câu đang được đánh dấu yêu thích
    val all = getAllQuotes(this)
    val favs = all.filter { sp.getBoolean("fav_" + it, false) }.toMutableList()

    titleView.text = "Yêu thích (${favs.size})"   // đổi tên biến cho khớp với TextView tiêu đề của bạn
    val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, favs)
    list.adapter = adapter                         // đổi tên biến cho khớp với ListView của bạn

    // Nhấn giữ để bỏ khỏi Yêu thích
    list.setOnItemLongClickListener { _, _, position, _ ->
        val q = favs[position]
        sp.edit().remove("fav_" + q).apply()
        favs.removeAt(position)
        adapter.notifyDataSetChanged()
        Toast.makeText(this, "Đã bỏ khỏi Yêu thích", Toast.LENGTH_SHORT).show()
        true
    }
}


        if (mode == "added") {
            list.setOnItemLongClickListener { _, _, position, _ ->
                val item = adapter.getItem(position) ?: return@setOnItemLongClickListener true
                data.remove(item)
                saveAdded(data)
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "Đã xoá 1 câu.", Toast.LENGTH_SHORT).show()
                true
            }
            Toast.makeText(this, "Giữ lâu để xoá.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Danh sách mặc định (chỉ xem).", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadDefaultMutable(): MutableList<String> = try {
        assets.open("quotes_default.txt").use { ins ->
            BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableList()
        }
    } catch (_: Exception) { mutableListOf() }

    private fun getAdded(): MutableList<String> {
        val cur = getSharedPreferences(PREF, MODE_PRIVATE).getString(KEY_ADDED, "") ?: ""
        return if (cur.isEmpty()) mutableListOf()
        else cur.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    }

    private fun saveAdded(list: List<String>) {
        getSharedPreferences(PREF, MODE_PRIVATE).edit()
            .putString(KEY_ADDED, list.joinToString("\n"))
            .apply()
    }
}
