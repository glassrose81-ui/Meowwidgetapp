package com.meowwidget.gd1

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class QuotesListActivity : AppCompatActivity() {

    // Dùng cùng "chìa" với MeowSettingsActivity
    private val PREF = "meow_settings"
    private val KEY_ADDED = "added_lines"
    private val KEY_FAVS = "favs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mode = intent.getStringExtra("mode") ?: "default"

        // UI rất gọn: 1 ListView + empty text
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val empty = TextView(this).apply {
            text = "Danh sách trống"
            textSize = 16f
            setPadding(32, 32, 32, 32)
        }
        val list = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        }
        root.addView(list)
        root.addView(empty)
        setContentView(root)
        list.emptyView = empty

        when (mode) {
            "fav" -> {
                supportActionBar?.title = "Yêu thích"
                val data = readFav().toMutableList()
                val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, data)
                list.adapter = adapter

                // Nhấn giữ để BỎ khỏi Yêu thích
                list.setOnItemLongClickListener { _, _, position, _ ->
                    val item = data[position]
                    val cur = readFav().toMutableList()
                    if (cur.remove(item)) {
                        saveFav(cur)
                        data.removeAt(position)
                        adapter.notifyDataSetChanged()
                        Toast.makeText(this, "Đã bỏ khỏi Yêu thích", Toast.LENGTH_SHORT).show()
                        if (data.isEmpty()) finish()
                    }
                    true
                }
            }
            "added" -> {
                supportActionBar?.title = "Bạn thêm"
                val data = readAdded().toMutableList()
                val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, data)
                list.adapter = adapter
            }
            else -> { // "default"
                supportActionBar?.title = "Mặc định"
                val data = readDefault().toMutableList()
                val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, data)
                list.adapter = adapter
            }
        }
    }

    // --- Helpers đọc/ghi ---
    private fun readAdded(): List<String> {
        val t = getSharedPreferences(PREF, MODE_PRIVATE).getString(KEY_ADDED, "") ?: ""
        return t.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun readFav(): List<String> {
        val t = getSharedPreferences(PREF, MODE_PRIVATE).getString(KEY_FAVS, "") ?: ""
        return t.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun saveFav(list: List<String>) {
        getSharedPreferences(PREF, MODE_PRIVATE).edit()
            .putString(KEY_FAVS, list.joinToString("\n"))
            .apply()
    }

    private fun readDefault(): List<String> {
        // Cố gắng đọc từ assets/QUOTE.txt (mỗi dòng 1 câu)
        return try {
            assets.open("QUOTE.txt").bufferedReader(Charsets.UTF_8).useLines { seq ->
                seq.map { it.trim() }.filter { it.isNotEmpty() }.toList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
