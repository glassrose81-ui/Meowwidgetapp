package com.meowwidget.gd1

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.animation.ValueAnimator
import kotlin.math.*

class SplashView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : View(context, attrs) {

  // ====== ASSETS ======
  private val bg: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.bg)
  private val petals: List<Bitmap> = listOf(
    BitmapFactory.decodeResource(resources, R.drawable.petal1),
    BitmapFactory.decodeResource(resources, R.drawable.petal2),
    BitmapFactory.decodeResource(resources, R.drawable.petal3),
    BitmapFactory.decodeResource(resources, R.drawable.petal4)
  )

  // Thu nhỏ cánh: tròn -8%, còn lại -5%
  private val petalScales: List<Float> = petals.map { bmp ->
    val ar = bmp.width.toFloat() / bmp.height.toFloat()
    if (abs(ar - 1f) <= 0.12f) 0.92f else 0.95f
  }

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

  // ====== LƯỚI & TỌA ĐỘ (giữ nguyên) ======
  private val coords = listOf(
    3 to 9, 3 to 4, 3 to 2, 1 to 1,
    6 to 5, 5 to 8, 4 to 6, 5 to 1,
    8 to 8, 10 to 3, 9 to 6, 8 to 4,
    8 to 2, 1 to 6, 5 to 3, 2 to 7
  )

  // ====== WAVE TIMING (giữ nguyên) ======
  private val waveStarts = floatArrayOf(0f, 4f, 6.5f)
  private val waveDurations = floatArrayOf(5.8f, 5.4f, 5.4f)
  private val fadeTime = 0.35f
  private val stopMin = 0.47f
  private val stopMax = 0.51f

  // Đong đưa nhẹ (giữ nguyên)
  private val rotAmpDeg = 12f
  private val rotFreqHz = 0.7f

  // Thời gian tổng 10s (giữ nguyên)
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

  // ====== NỀN: blur phổ thông (không cần API 31) ======
  private var blurredBg: Bitmap? = null
  private var fitDstRect = Rect()
  private var cropSrcForBlur = Rect()

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    if (w <= 0 || h <= 0) return

    // Tính crop center-crop để lấp đầy màn (lớp nền phía sau)
    val bw = bg.width; val bh = bg.height
    val viewAspect = w.toFloat() / h.toFloat()
    val bmpAspect  = bw.toFloat() / bh.toFloat()
    cropSrcForBlur = if (bmpAspect > viewAspect) {
      val srcW = (bh * viewAspect).toInt()
      val left = ((bw - srcW) / 2).coerceAtLeast(0)
      Rect(left, 0, (left + srcW).coerceAtMost(bw), bh)
    } else {
      val srcH = (bw / viewAspect).toInt()
      val top  = ((bh - srcH) / 2).coerceAtLeast(0)
      Rect(0, top, bw, (top + srcH).coerceAtMost(bh))
    }

    // Tạo ảnh mờ: downscale mạnh rồi upscale lại (blur tự nhiên, 1 lần duy nhất)
    val smallW = max(16, w / 16)
    val smallH = max(16, h / 16)
    val tmpSmall = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888)
    Canvas(tmpSmall).drawBitmap(bg, cropSrcForBlur, Rect(0, 0, smallW, smallH), paint)
    blurredBg = Bitmap.createScaledBitmap(tmpSmall, w, h, true)
    tmpSmall.recycle()

    // Ảnh chính fit/contain (không méo, có viền nếu cần)
    val scale = min(w.toFloat() / bw, h.toFloat() / bh)
    val sw = (bw * scale).toInt()
    val sh = (bh * scale).toInt()
    val left = (w - sw) / 2
    val top  = (h - sh) / 2
    fitDstRect = Rect(left, top, left + sw, top + sh)
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)

    // Lớp nền mờ phủ full
    blurredBg?.let { canvas.drawBitmap(it, 0f, 0f, paint) }
      ?: run { canvas.drawBitmap(bg, cropSrcForBlur, Rect(0, 0, width, height), paint) }

    // Ảnh nền gốc dạng fit/contain đặt phía trên (không méo)
    canvas.drawBitmap(bg, null, fitDstRect, paint)

    // ------ Lưới kéo xuống (giữ nguyên) ------
    val cols = 10f
    val rows = 10f
    val topHalf = height * 0.5f
    fun colToX(c: Int) = ((c - 0.5f) / cols) * width
    fun rowToY(r: Int) = ((r - 0.5f) / rows) * topHalf

    val anchors = coords.map { (c, r) -> Pair(colToX(c), rowToY(r)) }
    val yMax = anchors.maxOf { it.second }
    val offsetStart = -(yMax + 0.06f * height)
    val offsetEnd = (0.51f * height) - anchors.minOf { it.second }

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
}
