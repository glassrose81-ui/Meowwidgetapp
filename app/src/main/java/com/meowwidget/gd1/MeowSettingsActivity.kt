package com.meowwidget.gd1

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/**
 * Meow Settings – Ngăn Hệ thống (B1, fix1)
 * - Giao diện an toàn, không dùng thư viện ngoài.
 * - Khoảng cách giữa các phần ~8% chiều cao màn hình.
 * - Khoảng cách tiêu đề → nội dung ~4%.
 * - Không có lớp lót trắng; chữ nằm trực tiếp trên nền.
 * - Nút, khung theo màu nhấn xanh dương.
 * - Nút hành động chỉ hiển thị Toast (demo), chưa xử lý dữ liệu thật.
 * - Sửa lỗi LayoutParams: dùng LinearLayout.LayoutParams cho các view có weight.
 */
class MeowSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = ScrollView(this).apply { isFillViewport = true }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        root.addView(container, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Nền bông gòn (tùy chọn)
        try { container.setBackgroundResource(R.drawable.bg_settings_cotton) } catch (_: Exception) {}

        val blue = 0xFF2F80ED.toInt()

        // ===== Helper =====
        fun sectionTitle(text: String) = TextView(this).apply {
            this.text = text
            setTextColor(0xFF111111.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(typeface, Typeface.BOLD)
        }
        fun titleSpacing(): Int = (screenH() * 0.04f).toInt() // ~4%
        fun sectionGap(): Int = (screenH() * 0.08f).toInt()   // ~8%

        fun pillButton(text: String, solid: Boolean) = Button(this).apply {
            this.text = text
            setTextColor(if (solid) 0xFFFFFFFF.toInt() else blue)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            background = if (solid) {
                GradientDrawable().apply {
                    cornerRadius = dp(26).toFloat()
                    setColor(blue)
                }
            } else {
                GradientDrawable().apply {
                    cornerRadius = dp(26).toFloat()
                    setStroke(dp(2), blue)
                    setColor(0x00000000)
                }
            }
            minHeight = dp(48)
            minWidth = dp(120)
        }

        fun timeBox(hint: String) = EditText(this).apply {
            setHint(hint)
            setTextColor(0xFF111111.toInt())
            setHintTextColor(0xFF666666.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            inputType = InputType.TYPE_CLASS_DATETIME
            filters = arrayOf(InputFilter.LengthFilter(5)) // "HH:MM"
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setStroke(dp(2), blue)
                setColor(0x00000000)
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        fun labelSmall(text: String) = TextView(this).apply {
            this.text = text
            setTextColor(0xFF606060.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }

        fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

        // ===== Header =====
        val header = TextView(this).apply {
            text = "Meow Settings — hệ thống"
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(blue)
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, Typeface.BOLD)
        }
        container.addView(header, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ===== 1) Nguồn hiển thị =====
        container.addView(sectionTitle("Nguồn hiển thị"))
        container.addView(View(this), ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, titleSpacing()))

        val rowSource = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnAll = pillButton("Tất cả", solid = true)
        val btnFav = pillButton("Yêu thích", solid = false)

        rowSource.addView(btnAll)
        rowSource.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
        rowSource.addView(btnFav)
        container.addView(rowSource, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        container.addView(View(this), ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, sectionGap()))

        // ===== 2) Mốc giờ =====
        val rowTitleTime = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvTimeTitle = sectionTitle("Mốc giờ (tối đa 3)")
        val btnSaveTime = pillButton("Lưu mốc", solid = true)

        rowTitleTime.addView(tvTimeTitle, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rowTitleTime.addView(btnSaveTime)
        container.addView(rowTitleTime)

        container.addView(View(this), ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, titleSpacing()))

        val rowTime = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val et1 = timeBox("08:00")
        val et2 = timeBox("17:00")
        val et3 = timeBox("20:00")

        rowTime.addView(et1, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rowTime.addView(Space(this), ViewGroup.LayoutParams(dp(12), 1))
        rowTime.addView(et2, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rowTime.addView(Space(this), ViewGroup.LayoutParams(dp(12), 1))
        rowTime.addView(et3, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        container.addView(rowTime)

        btnSaveTime.setOnClickListener { toast("Đã lưu mốc (B1 – demo).") }

        container.addView(View(this), ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, sectionGap()))

        // ===== 3) Nhập dữ liệu =====
        container.addView(sectionTitle("Nhập dữ liệu"))
        container.addView(View(this), ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, titleSpacing()))

        val rowImport = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnPaste = pillButton("Dán quote", solid = true)
        val btnPickTxt = pillButton("Chọn tệp .TXT", solid = false)

        rowImport.addView(btnPaste, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rowImport.addView(Space(this), ViewGroup.LayoutParams(dp(12), 1))
        rowImport.addView(btnPickTxt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        container.addView(rowImport)

        container.addView(labelSmall("(Bấm để mở khung dán)"))

        btnPaste.setOnClickListener { toast("Mở khung dán (sẽ thêm ở bước kế).") }
        btnPickTxt.setOnClickListener { toast("Chọn tệp .TXT (sẽ thêm ở bước kế).") }

        container.addView(View(this), ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, sectionGap()))

        // ===== 4) Câu hôm nay =====
        container.addView(sectionTitle("Câu hôm nay"))
        container.addView(View(this), ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, titleSpacing()))

        val tvToday = TextView(this).apply {
            text = "Cuộc đời như dòng sông, uốn lượn qua từng khúc quanh của số phận."
            setTextColor(0xFF111111.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
        }
        container.addView(tvToday)

        val rowFav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rowFav.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
        val btnFavToday = pillButton("🐾  Yêu thích", solid = false)
        rowFav.addView(btnFavToday)
        container.addView(rowFav)

        container.addView(labelSmall("Nguồn đang dùng: Tất cả"))

        btnFavToday.setOnClickListener { toast("Đã đánh dấu (demo).") }

        container.addView(View(this), ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, sectionGap()))

        // ===== 5) Danh sách =====
        container.addView(sectionTitle("Danh sách"))
        container.addView(View(this), ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, titleSpacing()))

        val tvDefault = TextView(this).apply {
            text = "• Mặc định (chỉ xem) — (Tổng: 100)"
            setTextColor(0xFF111111.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
        }
        container.addView(tvDefault)
        container.addView(labelSmall("– Đừng đếm những vì sao đã tắt..."))
        container.addView(labelSmall("– Mỗi sớm mai thức dậy..."))

        val rowAllD = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rowAllD.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
        val tvAllDefault = TextView(this).apply {
            text = "Xem tất cả"
            setTextColor(blue)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        rowAllD.addView(tvAllDefault)
        container.addView(rowAllD)

        val tvAdded = TextView(this).apply {
            text = "• Bạn thêm (xem/xoá) — (Tổng: 5)"
            setTextColor(0xFF111111.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
        }
        container.addView(tvAdded)
        container.addView(labelSmall("– Khi nhìn lại quá khứ..."))

        val rowAllA = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rowAllA.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
        val tvAllAdded = TextView(this).apply {
            text = "Xem tất cả"
            setTextColor(blue)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        rowAllA.addView(tvAllAdded)
        container.addView(rowAllA)

        setContentView(root)
    }

    private fun dp(value: Int): Int {
        val d = resources.displayMetrics.density
        return (value * d).toInt()
    }

    private fun screenH(): Int = resources.displayMetrics.heightPixels
}
