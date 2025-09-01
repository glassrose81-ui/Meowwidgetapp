package com.meowwidget.gd1

import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class QuotesListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mode = intent.getStringExtra("mode") ?: "default"

        // UI đơn giản: tiêu đề + ListView
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val title = TextView(this).apply {
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            text = when (mode) {
                "fav" -> "Danh sách Yêu thích"
                "added" -> "Danh sách Bạn thêm"
                else -> "Danh sách Mặc định (chỉ xem)"
            }
        }
        val list = ListView(this)

        root.addView(
            title,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            list,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(root)

        // Dữ liệu
        val data: MutableList<String> = when (mode) {
            "added" -> loadAdded()
            "fav" -> loadFav()
            else -> loadDefaultMutable()
        }

        val adapter =
            ArrayAdapter(this, android.R.layout.simple_list_item_1, data)
        list.adapter = adapter

        // Nhấn giữ để xoá/bỏ yêu thích (tuỳ mode)
        list.setOnItemLongClickListener { _, _, position, _ ->
            val item = adapter.getItem(position) ?: return@setOnItemLongClickListener true

            when (mode) {
                "added" -> {
                    val sp = getSharedPreferences(PREF, MODE_PRIVATE)
                    val existing = sp.getString(KEY_ADDED, "") ?: ""
                    val newLines = existing
                        .lines()
                        .map { it.trim() }
                        .filter { it.isNotBlank() && it != item }
                        .joinToString("\n")
                    sp.edit().putString(KEY_ADDED, newLines).apply()

                    data.remove(item); adapter.notifyDataSetChanged()
                    Toast.makeText(this, "Đã xoá 1 câu.", Toast.LENGTH_SHORT).show()
                }

                "fav" -> {
                    val sp = getSharedPreferences(PREF, MODE_PRIVATE)
                    val raw = sp.getString(KEY_FAVS, "") ?: ""
                    val set = raw.lines().map { it.trim() }.filter { it.isNotBlank() }.toMutableSet()
                    val removed = set.remove(item)
                    sp.edit().putString(KEY_FAVS, set.joinToString("\n")).apply()

                    data.remove(item); adapter.notifyDataSetChanged()
                    Toast.makeText(this, "Đã bỏ khỏi Yêu thích.", Toast.LENGTH_SHORT).show()
                }
                        ?.key
                    if (key != null) sp.edit().remove(key).apply()

                    data.remove(item); adapter.notifyDataSetChanged()
                    Toast.makeText(this, "Đã bỏ khỏi Yêu thích.", Toast.LENGTH_SHORT).show()
                }

                else -> {
                    Toast.makeText(this, "Danh sách mặc định (chỉ xem).", Toast.LENGTH_SHORT).show()
                }
            }
            true
        }
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    private fun loadDefaultMutable(): MutableList<String> {
        return try {
            assets.open(DEFAULT_FILE).bufferedReader(Charsets.UTF_8).use { br ->
                br.readLines().map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
            }
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun loadAdded(): MutableList<String> {
        val sp = getSharedPreferences(PREF, MODE_PRIVATE)
        val raw = sp.getString(KEY_ADDED, "") ?: ""
        return raw.lines().map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
    }

    private fun loadFav(): MutableList<String> {
        val sp = getSharedPreferences(PREF, MODE_PRIVATE)
        val raw = sp.getString(KEY_FAVS, "") ?: ""
        return raw.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toMutableList()
    }
            .values
            .mapNotNull { it as? String }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toMutableList()
    }

    companion object {
        private const val PREF = "meow_settings"
        private const val KEY_ADDED = "added_lines"
        private const val KEY_FAVS = "favs"
        private const val FAV_PREFIX = "fav_"
        private const val DEFAULT_FILE = "quotes_default.txt"
    }
}
