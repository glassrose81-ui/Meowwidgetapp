package com.meowwidget.gd1

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min
import kotlin.math.roundToInt

class SplashView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ==== ẢNH NGUỒN (đổi tên id nếu ảnh của bạn khác) ====
    private val bgRes = R.drawable.meow_bg           // ảnh nền mèo
    private val petalResIds = intArrayOf(            // 4 cánh hoa đã cắt nền
        R.drawable.petal1,
        R.drawable.petal2,
        R.drawable.petal3,
        R.drawable.petal4
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val timeStart = System.currentTimeMillis()

    // Ảnh nền
    private val bg: Bitmap = BitmapFactory.decodeResource(resources, bgRes)

    // Ảnh cánh hoa: bản gốc + bản đã scale theo màn hình
    private val petalBitmapsOriginal: List<Bitmap> =
        petalResIds.map { BitmapFactory.decodeResource(resources, it) }

    private var petalBitmapsScaled: List<Bitmap> = emptyList()

    // Tham số “giống mp4”: 3 đợt rơi, cùng tốc độ, dừng ~47–51%
    private val stopMin = 0.47f
    private val stopMax = 0.51f
    private val wave1Start = 0L           // ms
    private val wave2Start = 4000L        // 4.0s
    private val wave3Start = 6500L        // 6.5s
    private val fallDurationMsWave1 = 5800L
    private val fallDurationMsWave2 = 5400L
    private val fallDurationMsWave3 = 5400L

    // Gió đong đưa nhẹ
    private val swayAmpRatio = 0.012f     // 1.2% chiều rộng
    private val swaySpeed = 1.2f          // tốc độ đong đưa

    // Lưới 10x10 và các toạ độ bạn đã chốt (áp cho cả 3 wave)
    // (#1..#17)
    private val coords: List<Pair<Int, Int>> = listOf(
        3 to 9, 3 to 4, 3 to 2, 1 to 1, 6 to 5, 5 to 8, 4 to 6,
        5 to 1, 8 to 8, 10 to 3, 9 to 6, 8 to 4, 8 to 2, 1 to 6, 5 to 3, 2 to 7
    )

    // Tính toạ độ neo (x, y) theo lưới; y tính trên nửa trên (0..50%)
    private fun colToX(c: Int, w: Int): Float = ((c - 0.5f) / 10f) * w
    private fun rowToY(r: Int, h: Int): Float = ((r - 0.5f) / 10f) * (h * 0.5f)

    // Scale ảnh cánh hoa theo kích thước màn hình (giữ tỉ lệ, không méo)
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val base = min(w, h).toFloat()
        val targetH = base * 0.06f     // ~6% cạnh ngắn → nhỏ–vừa như bản mp4
        val jitter = 0.10f             // ±10% để tự nhiên

        petalBitmapsScaled = petalBitmapsOriginal.map { bmp ->
            val aspect = bmp.height / bmp.width.toFloat()
            val rand = 1f + ((Math.random().toFloat() * 2f - 1f) * jitter) // 0.9..1.1
            val th = (targetH * rand).roundToInt().coerceAtLeast(1)
            val tw = (th / aspect).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bmp, tw, th, true)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // —— Vẽ nền mèo full màn —— //
        val dst = Rect(0, 0, width, height)
        canvas.drawBitmap(bg, null, dst, paint)

        if (petalBitmapsScaled.isEmpty()) return

        // Tính neo (x, yStop) theo lưới
        val anchors = coords.map { (c, r) ->
            val x = colToX(c, width)
            val yTopHalf = rowToY(r, height)
            // Vùng dừng ngẫu nhiên trong 47–51% màn
            val stopY = height * (stopMin + Math.random().toFloat() * (stopMax - stopMin))
            Triple(x, yTopHalf, stopY)
        }

        // Vẽ 3 wave
        val now = System.currentTimeMillis() - timeStart
        drawWave(canvas, now, wave1Start, fallDurationMsWave1, anchors, 0)
        drawWave(canvas, now, wave2Start, fallDurationMsWave2, anchors, 1)
        drawWave(canvas, now, wave3Start, fallDurationMsWave3, anchors, 2)

        // Gọi vẽ lại để animate
        postInvalidateOnAnimation()
    }

    private fun drawWave(
        canvas: Canvas,
        now: Long,
        waveStart: Long,
        duration: Long,
        anchors: List<Triple<Float, Float, Float>>,
        waveIndex: Int
    ) {
        if (now < waveStart) return
        val t = (now - waveStart).coerceAtMost(duration).toFloat() / duration.toFloat()

        // Từ cạnh trên (yStart = -hPetal) → tới yStop
        anchors.forEachIndexed { i, (x, yTopHalf, yStop) ->
            val bmp = petalBitmapsScaled[i % petalBitmapsScaled.size]
            val yStart = -bmp.height.toFloat()
            val yTarget = yStop
            val yNow = yStart + (yTarget - yStart) * t

            // Đong đưa
            val sway = (width * swayAmpRatio) *
                    kotlin.math.sin((now / 1000f + i * 0.37f + waveIndex) * swaySpeed)

            // Vẽ theo TÂM, không dùng Rect để tránh méo
            val halfW = bmp.width / 2f
            val halfH = bmp.height / 2f
            canvas.drawBitmap(bmp, x + sway - halfW, yNow - halfH, paint)
        }
    }
}
