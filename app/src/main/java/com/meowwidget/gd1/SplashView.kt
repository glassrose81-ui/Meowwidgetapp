package com.meowwidget.gd1

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.animation.ValueAnimator
import kotlin.math.*

class SplashView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : View(context, attrs) {

  // Ảnh nền & cánh hoa (GIỮ NGUYÊN tên resource như dự án của bạn)
  private val bg: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.bg)
  private val petals: List<Bitmap> = listOf(
    BitmapFactory.decodeResource(resources, R.drawable.petal1),
    BitmapFactory.decodeResource(resources, R.drawable.petal2),
    BitmapFactory.decodeResource(resources, R.drawable.petal3),
    BitmapFactory.decodeResource(resources, R.drawable.petal4)
  )

  // Thu nhỏ cánh theo chốt: tròn -8% (~0.92), còn lại -5% (~0.95)
  // Tự nhận diện "tròn" theo tỉ lệ w/h gần 1
  private val petalScales: List<Float> = petals.map { bmp ->
    val ar = bmp.width.toFloat() / bmp.height.toFloat()
    if (abs(ar - 1f) <= 0.12f) 0.92f else 0.95f
  }

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
  private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

  // Lưới & vị trí (GIỮ NGUYÊN theo APK đầu)
  private val coords = listOf(
    3 to 9, 3 to 4, 3 to 2, 1 to 1,
    6 to 5, 5 to 8, 4 to 6, 5 to 1,
    8 to 8, 10 to 3, 9 to 6, 8 to 4,
    8 to 2, 1 to 6, 5 to 3, 2 to 7
  )

  // Wave theo thời điểm & thời lượng đã chốt
  private val waveStarts = floatArrayOf(0f, 4f, 6.5f)
  private val waveDurations = floatArrayOf(5.8f, 5.4f, 5.4f)
  private val fadeTime = 0.35f
  private val stopMin = 0.47f
  private val stopMax = 0.51f

  // Quay/đung đưa nhẹ (GIỮ NGUYÊN như APK đầu)
  private val rotAmpDeg = 12f
  private val rotFreqHz = 0.7f

  // Thời gian chạy 0→10s (GIỮ NGUYÊN)
  private var tNow = 0f
  private val animator = ValueAnimator.ofFloat(0f, 10f).apply {
    duration = 10_000
    interpolator = LinearInterpolator()
    addUpdateListener {
      tNow = it.animatedValue as Float
      invalidate()
    }
    start()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)

    // (1) NỀN: FIT + BLUR hai bên (không méo, không cắt nội dung)
    drawBgFitWithSideBlur(canvas)

    val cols = 10f
    val rows = 10f
    val topHalf = height * 0.5f

    fun colToX(c: Int): Float = ((c - 0.5f) / cols) * width
    fun rowToY(r: Int): Float = ((r - 0.5f) / rows) * topHalf

    // Neo theo lưới (tấm lưới kéo xuống)
    val anchors = coords.map { (c, r) -> Pair(colToX(c), rowToY(r)) }
    val yMax = anchors.maxOf { it.second }
    val offsetStart = -(yMax + 0.06f * height)                     // xuất phát ngoài mép trên
    val offsetEnd = (0.51f * height) - anchors.minOf { it.second } // dừng ~47–51%

    // Vẽ 3 wave (GIỮ NGUYÊN timing)
    for (w in 0 until 3) {
      val t0 = waveStarts[w]
      val ft = waveDurations[w]
      val dt = tNow - t0
      if (dt < 0f) continue

      val offset = if (dt <= ft) {
        val p = (dt / ft).coerceIn(0f, 1f)
        offsetStart + p * (offsetEnd - offsetStart)
      } else offsetEnd

      for (i in coords.indices) {
        val bmp = petals[i % petals.size]
        val (ax, ay) = anchors[i]
        var y = ay + offset

        // Dải dừng 47–51% (rải nhẹ trong dải để tự nhiên, GIỮ NGUYÊN)
        val yStop = height * (stopMin + (stopMax - stopMin) * (i / (coords.size - 1f)))
        var alpha = 1f
        if (y >= yStop) {
          val denom = (offsetEnd - offsetStart)
          val dtCross = if (denom != 0f) ft * ((yStop - ay - offsetStart) / denom) else dt
          val fadeElapsed = max(0f, dt - dtCross)
          alpha = (1f - fadeElapsed / fadeTime).coerceIn(0f, 1f)
          if (alpha <= 0f) continue
          y = yStop
        }

        val angle = rotAmpDeg * sin(2f * Math.PI.toFloat() * rotFreqHz * max(0f, dt) + i * 0.37f)
        paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)

        // (2) VẼ CÁNH: chỉ thu nhỏ khi vẽ (không đổi đường rơi/timing)
        val scale = petalScales[i % petalScales.size]
        canvas.save()
        canvas.translate(ax, y)
        canvas.rotate(angle)
        canvas.scale(scale, scale)
        canvas.drawBitmap(bmp, -bmp.width / 2f, -bmp.height / 2f, paint)
        canvas.restore()
      }
    }

    postInvalidateOnAnimation()
  }

  // ===== NỀN: fit + blur hai bên (không méo, không cắt nội dung) =====
  private fun drawBgFitWithSideBlur(canvas: Canvas) {
    val vw = width
    val vh = height
    if (vw <= 0 || vh <= 0) return

    val bw = bg.width
    val bh = bg.height
    val viewAspect = vw.toFloat() / vh.toFloat()
    val bmpAspect  = bw.toFloat() / bh.toFloat()

    // Lớp BLUR phía sau: dùng center-crop để phủ full màn, sau đó làm mờ
    val srcCrop: Rect = if (bmpAspect > viewAspect) {
      val srcW = (bh * viewAspect).toInt()
      val left = ((bw - srcW) / 2).coerceAtLeast(0)
      Rect(left, 0, (left + srcW).coerceAtMost(bw), bh)
    } else {
      val srcH = (bw / viewAspect).toInt()
      val top  = ((bh - srcH) / 2).coerceAtLeast(0)
      Rect(0, top, bw, (top + srcH).coerceAtMost(bh))
    }
    val dstFull = Rect(0, 0, vw, vh)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      // RenderEffect blur (API 31+)
      val blur = RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP)
      blurPaint.setRenderEffect(blur)
      canvas.drawBitmap(bg, srcCrop, dstFull, blurPaint)
      blurPaint.setRenderEffect(null)
    } else {
      // Fallback: không blur (vẫn làm nền đầy màn)
      canvas.drawBitmap(bg, srcCrop, dstFull, blurPaint)
    }

    // Lớp ẢNH CHÍNH FIT/CONTAIN phía trên: giữ tỉ lệ, không cắt, có viền hai bên
    val scale = min(vw.toFloat() / bw, vh.toFloat() / bh)
    val sw = (bw * scale).toInt()
    val sh = (bh * scale).toInt()
    val left = (vw - sw) / 2
    val top = (vh - sh) / 2
    val dstFit = Rect(left, top, left + sw, top + sh)
    canvas.drawBitmap(bg, null, dstFit, paint)
  }
}
