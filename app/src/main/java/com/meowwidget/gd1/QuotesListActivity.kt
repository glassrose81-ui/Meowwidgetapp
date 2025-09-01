package com.meowwidget.gd1

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.util.TypedValue

class QuotesListActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private var data: MutableList<String> = mutableListOf()
    private var mode: String = "default"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mode = intent.getStringExtra("mode") ?: "default"

        // Root layout
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Title
        val title = TextView(this).apply {
            text = when (mode) {
                "fav" -> "Danh sách Yêu thích"
                "added" -> "Danh sách Bạn thêm"
                else -> "Danh sách Mặc định (chỉ xem)"
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setPadding(0, 0, 0, dp(8))
            gravity = Gravity.START
        }

        listView = ListView(this)

        root.addView(
            title,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            listView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(root)

        // Load data & set adapter
        data = loadData(mode)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, data)
        listView.adapter = adapter

        // Long press to remove items for "added" and "fav"
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val item = data.getOrNull(position) ?: return@setOnItemLongClickListener true
            when (mode) {
                "added" -> {
                    removeFromAdded(item)
                    toast("Đã xoá khỏi 'Quote thêm'")
                    refresh()
                }
                "fav" -> {
                    removeFromFavs(item)
                    toast("Đã bỏ khỏi Yêu thích")
                    refresh()
                }
                else -> {
                    toast("Mục mặc định: chỉ xem")
                }
            }
            true
        }
    }

    private fun refresh() {
        data.clear()
        data.addAll(loadData(mode))
        adapter.notifyDataSetChanged()
    }

    private fun loadData(mode: String): MutableList<String> {
        return when (mode) {
            "fav" -> getFavList().toMutableList()
            "added" -> getAddedList().toMutableList()
            else -> loadDefaultQuotes().toMutableList()
        }
    }

    private fun loadDefaultQuotes(): List<String> {
        return try {
            assets.open(DEFAULT_FILE).bufferedReader().use { br ->
                br.readText()
            }
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getAddedList(): List<String> {
        val cur = getSharedPreferences(PREF, MODE_PRIVATE).getString(KEY_ADDED, "") ?: ""
        return if (cur.isEmpty()) emptyList() else cur.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun getFavList(): List<String> {
        val cur = getSharedPreferences(PREF, MODE_PRIVATE).getString(KEY_FAVS, "") ?: ""
        return if (cur.isEmpty()) emptyList() else cur.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun removeFromAdded(q: String) {
        val pref = getSharedPreferences(PREF, MODE_PRIVATE)
        val list = (pref.getString(KEY_ADDED, "") ?: "")
            .split("\n").map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()
        list.removeAll { it == q }
        pref.edit().putString(KEY_ADDED, list.joinToString("\n")).apply()
    }

    private fun removeFromFavs(q: String) {
        val pref = getSharedPreferences(PREF, MODE_PRIVATE)
        val set = (pref.getString(KEY_FAVS, "") ?: "")
            .split("\n").map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableSet()
        set.remove(q)
        pref.edit().putString(KEY_FAVS, set.joinToString("\n")).apply()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val PREF = "meow_settings"
        private const val KEY_ADDED = "added_lines"
        private const val KEY_FAVS = "favs"
        private const val DEFAULT_FILE = "quotes_default.txt"
    }
}
