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
import androidx.core.content.ContextCompat

/**
 * Meow Settings – Ngăn Hệ thống (B1)
 * - Không thay đổi logic lõi.
 * - Giao diện đơn giản, không đòi hỏi thư viện khác.
 * - Khoảng cách giữa các phần ~8% chiều cao màn hình (ước lượng runtime).
 * - Khoảng cách tiêu đề → nội dung ~4%.
 * - Không có lớp lót trắng; chữ nằm trực tiếp trên nền.
 * - Nút, khung theo màu nhấn xanh dương.
 * - Các nút hành động chỉ hiển thị Toast (chưa triển khai thao tác thật ở B1).
 */
class MeowSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Nền bông gòn: nếu bạn đã thêm ảnh vào res/drawable/bg_settings_cotton.png,
        // đoạn setBackgroundResource bên dưới sẽ áp dụng nền ngay.
        // Nếu chưa thêm ảnh, app vẫn chạy bình thường (nền mặc định hệ thống).
        val root = ScrollView(this).apply {
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        root.addView(container, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Thử đặt nền nếu tài nguyên tồn tại
        try {
            container.setBackgroundResource(R.drawable.bg_settings_cotton)
        } catch (_: Exception) {
            // Bỏ qua nếu bạn chưa thêm ảnh vào drawable.
        }

        // Màu nhấn
        val blue = 0xFF2F80ED.toInt()

        // ===== Helper UI =====
        fun sectionTitle(text: String): TextView {
            return TextView(this).apply {
                this.text = text
                setTextColor(0xFF111111.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setTypeface(typeface, Typeface.BOLD)
            }
        }

        fun titleSpacing(): Int = (screenH() * 0.04f).toInt() // ~4%
        fun sectionGap(): Int = (screenH() * 0.08f).toInt()   // ~8%

        fun pillButton(text: String, solid: Boolean): Button {
            val b = Button(this).apply {
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
            return b
        }

        fun timeBox(hint: String): EditText {
            return EditText(this).apply {
                setHint(hint)
                setTextColor(0xFF111111.toInt())
                setHintTextColor(0xFF666666.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                inputType = InputType.TYPE_CLASS_DATETIME
                // 5 ký tự định dạng "HH:MM" (tối giản; ràng buộc nâng cao sẽ thêm sau)
                filters = arrayOf(InputFilter.LengthFilter(5))
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(2), blue)
                    setColor(0x00000000)
                }
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
        }

        fun labelSmall(text: String): TextView {
            return TextView(this).apply {
                this.text = text
                setTextColor(0xFF606060.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }
        }

        fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

        // ===== Header (đơn giản) =====
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

        val titleSpace1 = View(this).apply { minimumHeight = titleSpacing() }
        container.addView(titleSpace1, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, titleSpacing()))

        val rowSource = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val btnAll = pillButton("Tất cả", solid = true)
        val btnFav = pillButton("Yêu thích", solid = false)

        rowSource.addView(btnAll)
        val spacerS = Space(this)
        rowSource.addView(spacerS, LinearLayout.LayoutParams(0, 1, 1f))
        rowSource.addView(btnFav)

        container.addView(rowSource, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Khoảng trống giữa phần
        container.addView(View(this).apply { minimumHeight = sectionGap() },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sectionGap()))

        // ===== 2) Mốc giờ (tiêu đề + nút Lưu mốc cùng hàng) =====
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

        val titleSpace2 = View(this).apply { minimumHeight = titleSpacing() }
        container.addView(titleSpace2, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, titleSpacing()))

        val rowTime = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val et1 = timeBox("08:00")
        val et2 = timeBox("17:00")
        val et3 = timeBox("20:00")

        rowTime.addView(et1, ViewGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rowTime.addView(Space(this), ViewGroup.LayoutParams(dp(12), 1))
        rowTime.addView(et2, ViewGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rowTime.addView(Space(this), ViewGroup.LayoutParams(dp(12), 1))
        rowTime.addView(et3, ViewGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        container.addView(rowTime)

        // Hành vi nút lưu (tạm)
        btnSaveTime.setOnClickListener {
            toast("Đã lưu mốc (B1 – demo).")
        }

        // Khoảng trống giữa phần
        container.addView(View(this).apply { minimumHeight = sectionGap() },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sectionGap()))

        // ===== 3) Nhập dữ liệu =====
        container.addView(sectionTitle("Nhập dữ liệu"))

        val titleSpace3 = View(this).apply { minimumHeight = titleSpacing() }
        container.addView(titleSpace3, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, titleSpacing()))

        val rowImport = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val btnPaste = pillButton("Dán quote", solid = true)
        val btnPickTxt = pillButton("Chọn tệp .TXT", solid = false)

        rowImport.addView(btnPaste, ViewGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rowImport.addView(Space(this), ViewGroup.LayoutParams(dp(12), 1))
        rowImport.addView(btnPickTxt, ViewGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        container.addView(rowImport)

        val hintPaste = labelSmall("(Bấm để mở khung dán)")
        container.addView(hintPaste)

        btnPaste.setOnClickListener { toast("Mở khung dán (sẽ thêm ở bước kế).") }
        btnPickTxt.setOnClickListener { toast("Chọn tệp .TXT (sẽ thêm ở bước kế).") }

        // Khoảng trống giữa phần
        container.addView(View(this).apply { minimumHeight = sectionGap() },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sectionGap()))

        // ===== 4) Câu hôm nay =====
        container.addView(sectionTitle("Câu hôm nay"))

        val titleSpace4 = View(this).apply { minimumHeight = titleSpacing() }
        container.addView(titleSpace4, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, titleSpacing()))

        val tvToday = TextView(this).apply {
            text = "Cuộc đời như dòng sông, uốn lượn qua từng khúc quanh của số phận."
            setTextColor(0xFF111111.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
        }
        container.addView(tvToday)

        val rowFav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val spacerFav = Space(this)
        val btnFavToday = pillButton("🐾  Yêu thích", solid = false)

        rowFav.addView(spacerFav, LinearLayout.LayoutParams(0, 1, 1f))
        rowFav.addView(btnFavToday)

        container.addView(rowFav)

        container.addView(labelSmall("Nguồn đang dùng: Tất cả"))

        btnFavToday.setOnClickListener { toast("Đã đánh dấu (demo).") }

        // Khoảng trống giữa phần
        container.addView(View(this).apply { minimumHeight = sectionGap() },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sectionGap()))

        // ===== 5) Danh sách =====
        container.addView(sectionTitle("Danh sách"))

        val titleSpace5 = View(this).apply { minimumHeight = titleSpacing() }
        container.addView(titleSpace5, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, titleSpacing()))

        val tvDefault = TextView(this).apply {
            text = "• Mặc định (chỉ xem) — (Tổng: 100)"
            setTextColor(0xFF111111.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
        }
        container.addView(tvDefault)

        val tvD1 = labelSmall("– Đừng đếm những vì sao đã tắt...")
        val tvD2 = labelSmall("– Mỗi sớm mai thức dậy...")
        container.addView(tvD1); container.addView(tvD2)

        val tvAllDefault = TextView(this).apply {
            text = "Xem tất cả"
            setTextColor(blue)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        val rowAllD = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        rowAllD.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
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

        val tvAllAdded = TextView(this).apply {
            text = "Xem tất cả"
            setTextColor(blue)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        val rowAllA = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rowAllA.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
        rowAllA.addView(tvAllAdded)
        container.addView(rowAllA)

        // Gán root
        setContentView(root)
    }

    private fun dp(value: Int): Int {
        val d = resources.displayMetrics.density
        return (value * d).toInt()
    }

    private fun screenH(): Int = resources.displayMetrics.heightPixels
}
