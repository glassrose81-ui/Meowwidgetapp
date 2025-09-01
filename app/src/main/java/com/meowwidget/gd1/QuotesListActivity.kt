
package com.meowwidget.gd1

import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader

class QuotesListActivity : AppCompatActivity() {
    private val PREF = "meow_settings"
    private val KEY_ADDED = "added_lines"
    private val FAV_PREFIX = "fav_"   // Đánh dấu Yêu thích: sp.putBoolean("fav_<nội_dung_câu>", true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mode = intent.getStringExtra("mode") ?: "default"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val list = ListView(this)
        root.addView(
            list,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(root)

        // Chọn dữ liệu hiển thị
        val data: MutableList<String> = when (mode) {
            "added" -> getAdded()
            "fav" -> getFavsFromPrefix()            // Đọc theo cờ boolean "fav_<quote>"
            else -> loadDefaultMutable()
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, data)
        list.adapter = adapter

        when (mode) {
            "added" -> {
                list.setOnItemLongClickListener { _, _, position, _ ->
                    val item = adapter.getItem(position) ?: return@setOnItemLongClickListener true
                    data.remove(item)
                    saveAdded(data)
                    adapter.notifyDataSetChanged()
                    Toast.makeText(this, "Đã xoá 1 câu.", Toast.LENGTH_SHORT).show()
                    true
                }
                Toast.makeText(this, "Giữ lâu để xoá (Bạn thêm).", Toast.LENGTH_SHORT).show()
            }

            "fav" -> {
                list.setOnItemLongClickListener { _, _, position, _ ->
                    val item = adapter.getItem(position) ?: return@setOnItemLongClickListener true
                    // Gỡ cờ yêu thích "fav_<quote>"
                    val sp = getSharedPreferences(PREF, MODE_PRIVATE)
                    sp.edit().remove(FAV_PREFIX + item).apply()

                    data.remove(item)
                    adapter.notifyDataSetChanged()
                    Toast.makeText(this, "Đã bỏ khỏi Yêu thích.", Toast.LENGTH_SHORT).show()
                    true
                }
                Toast.makeText(this, "Giữ lâu để bỏ khỏi Yêu thích.", Toast.LENGTH_SHORT).show()
            }

            else -> {
                Toast.makeText(this, "Danh sách mặc định (chỉ xem).", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadDefaultMutable(): MutableList<String> = try {
        assets.open("quotes_default.txt").use { ins ->
            BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableList()
        }
    } catch (_: Exception) {
        mutableListOf()
    }

    private fun getAdded(): MutableList<String> {
        val cur = getSharedPreferences(PREF, MODE_PRIVATE).getString(KEY_ADDED, "") ?: ""
        return if (cur.isEmpty()) mutableListOf()
        else cur.split("\\n".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    }

    private fun saveAdded(list: List<String>) {
        getSharedPreferences(PREF, MODE_PRIVATE).edit()
            .putString(KEY_ADDED, list.joinToString("\\n"))
            .apply()
    }

    // Đọc danh sách Yêu thích bằng cách duyệt toàn bộ keys và lấy những key có tiền tố "fav_"
    private fun getFavsFromPrefix(): MutableList<String> {
        val sp = getSharedPreferences(PREF, MODE_PRIVATE)
        val out = ArrayList<String>()
        for ((k, v) in sp.all) {
            if (k.startsWith(FAV_PREFIX) && v is Boolean && v) {
                out.add(k.removePrefix(FAV_PREFIX))
            }
        }
        return out
    }
}
