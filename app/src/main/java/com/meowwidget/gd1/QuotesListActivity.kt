package com.meowwidget.gd1

import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class QuotesListActivity : AppCompatActivity() {

    private val PREF = "meow_settings"
    private val KEY_ADDED = "added_lines"
    private val KEY_FAVS = "favs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mode = intent.getStringExtra("mode") ?: "default"

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

                // Long press: remove from Favorites
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

                // Long press: delete from "Added"
                list.setOnItemLongClickListener { _, _, position, _ ->
                    val item = data[position]
                    val cur = readAdded().toMutableList()
                    if (cur.remove(item)) {
                        saveAdded(cur)
                        data.removeAt(position)
                        adapter.notifyDataSetChanged()
                        Toast.makeText(this, "Đã xoá khỏi 'Bạn thêm'", Toast.LENGTH_SHORT).show()
                        if (data.isEmpty()) finish()
                    }
                    true
                }
            }
            else -> { // "default"
                supportActionBar?.title = "Mặc định"
                val data = readDefault().toMutableList()
                val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, data)
                list.adapter = adapter
            }
        }
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

    private fun saveFav(list: List<String>) {
        getSharedPreferences(PREF, MODE_PRIVATE).edit()
            .putString(KEY_FAVS, list.joinToString("\n")).apply()
    }

    private fun readDefault(): List<String> {
        return try {
            assets.open("QUOTE.txt").bufferedReader(Charsets.UTF_8).useLines { seq ->
                seq.map { it.trim() }.filter { it.isNotEmpty() }.toList()
            }
        } catch (_: Exception) { emptyList() }
    }
}
