package com.meowwidget.gd1

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class SplashView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // Ảnh nền & cánh hoa (tên resource GIỮ NGUYÊN như dự án của bạn)
    private val bg: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.bg)
    private val petals: List<Bitmap> = listOf(
        BitmapFactory.decodeResource(resources, R.drawable.petal1),
        BitmapFactory.decodeResource(resources, R.drawable.petal2),
        BitmapFactory.decodeResource(resources, R.drawable.petal3),
        BitmapFactory.decodeResource(resources, R.drawable.petal4)
    )

    // Thu nhỏ cánh hoa (duy nhất thay đổi kích thước hiển thị)
    private val PETAL_SCALE = 0.72f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Lưới 10x10 & toạ độ đã chốt (#1..#16)
    private val coords: List<Pair<Int, Int>> = listOf(
        3 to 9, 3 to 4, 3 to 2, 1 to 1, 6 to 5, 5 to 8, 4 to 6, 5 to 1,
        8 to 8, 10 to 3, 9 to 6, 8 to 4, 8 to 2, 1 to 6, 5 to 3, 2 to 7
    )

    // Thời điểm 3 wave & thời gian rơi (y như APK đầu)
    private val waveStartsMs = longArrayOf(0L, 4000L, 6500L)
    private val waveDurationsMs = longArrayOf(5800L, 5400L, 5400L)
    private val fadeTimeMs = 350L // 0.35s

    // Vùng dừng (theo chiều cao màn hình)
    private val stopMin = 0.47f
    private val stopMax = 0.51f

    // Gió đong đưa nhẹ, mượt
    private val swayAmpRatio = 0.012f // 1.2% bề rộng
    private val swaySpeed = 1.2f      // nhịp đong đưa

    // Thời gian bắt đầu
    private val t0 = SystemClock.uptimeMillis()

    // Neo theo cột (1..10)
    private fun colToX(c: Int, w: Int): Float = ((c - 0.5f) / 10f) * w

    // Ngẫu nhiên ổn định theo (wave, index) – không đổi mỗi frame
    private fun stableRandom01(seedA: Int, seedB: Int): Float {
        val v = ((seedA * 73856093) xor (seedB * 19349663)) * 0x9E3779B9.toInt()
        return ((v ushr 1) % 1_000_000) / 1_000_000f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // NỀN: vẽ giữ tỉ lệ (center-crop) → không bị méo trên mọi máy
        drawBgCenterCrop(canvas)

        val now = SystemClock.uptimeMillis() - t0
        val w = width
        val h = height
        if (w <= 0 || h <= 0) {
            postInvalidateOnAnimation()
            return
        }

        val swayAmp = w * swayAmpRatio

        // Vẽ 3 wave
        for (wave in 0 until 3) {
            val start = waveStartsMs[wave]
            val duration = waveDurationsMs[wave]
            val tWave = (now - start).toFloat()
            if (tWave < 0f) continue

            val progress = (tWave / duration).coerceIn(0f, 1f)

            for (i in coords.indices) {
                val (c, _) = coords[i]

                // X theo cột
                val ax = colToX(c, w)

                // Ảnh cánh
                val bmp = petals[i % petals.size]

                // Y dừng ổn định 47–51% màn
                val randStop = stopMin + stableRandom01(wave, i) * (stopMax - stopMin)
                val yStop = h * randStop

                // Xuất phát ngoài mép trên
                val yStart = -bmp.height.toFloat()

                // Vị trí rơi thẳng theo cột
                val yNow = if (progress >= 1f) yStop
                else yStart + (yStop - yStart) * progress

                // Alpha: chạm yStop → tan trong fadeTimeMs
                val alphaF = if (tWave <= duration) 1f
                else (1f - (tWave - duration) / fadeTimeMs.toFloat()).coerceIn(0f, 1f)
                if (alphaF <= 0f) continue

                // Gió nhẹ, mượt theo thời gian thực (không giật)
                val sway = if (progress >= 1f) 0f else {
                    val tSec = now / 1000f
                    (swayAmp * sin((tSec + i * 0.37f + wave) * (2f * Math.PI.toFloat() * (swaySpeed / 6f))))
                }

                // Vẽ theo TÂM + scale cánh
                paint.alpha = (alphaF * 255).toInt().coerceIn(0, 255)
                canvas.save()
                canvas.translate(ax + sway, yNow)
                canvas.scale(PETAL_SCALE, PETAL_SCALE)
                canvas.drawBitmap(bmp, -bmp.width / 2f, -bmp.height / 2f, paint)
                canvas.restore()
            }
        }

        postInvalidateOnAnimation()
    }

    // Nền center-crop: lấp khung, giữ tỉ lệ, cắt nhẹ mép nếu cần
    private fun drawBgCenterCrop(canvas: Canvas) {
        val vw = width
        val vh = height
        if (vw <= 0 || vh <= 0) return

        val bw = bg.width
        val bh = bg.height
        val viewAspect = vw.toFloat() / vh.toFloat()
        val bmpAspect  = bw.toFloat() / bh.toFloat()

        val src: Rect = if (bmpAspect > viewAspect) {
            // Ảnh rộng hơn màn → cắt hai bên
            val srcW = (bh * viewAspect).toInt()
            val left = ((bw - srcW) / 2).coerceAtLeast(0)
            Rect(left, 0, (left + srcW).coerceAtMost(bw), bh)
        } else {
            // Ảnh cao hơn màn → cắt trên/dưới
            val srcH = (bw / viewAspect).toInt()
            val top = ((bh - srcH) / 2).coerceAtLeast(0)
            Rect(0, top, bw, (top + srcH).coerceAtMost(bh))
        }

        val dst = Rect(0, 0, vw, vh)
        canvas.drawBitmap(bg, src, dst, paint)
    }
}
