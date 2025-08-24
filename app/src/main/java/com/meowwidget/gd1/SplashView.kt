package com.meowwidget.gd1

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.animation.ValueAnimator
import android.graphics.LinearGradient
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderEffect
import android.graphics.Shader
import kotlin.math.*

class SplashView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

  // Assets
  private val bg: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.bg)
  private val petals: List<Bitmap> = listOf(
    BitmapFactory.decodeResource(resources, R.drawable.petal1),
    BitmapFactory.decodeResource(resources, R.drawable.petal2),
    BitmapFactory.decodeResource(resources, R.drawable.petal3),
    BitmapFactory.decodeResource(resources, R.drawable.petal4)
  )

  // Grid & anchors (C,R) chốt (áp dụng cho cả 3 wave)
  private val coords: List<Pair<Int,Int>> = listOf(
    3 to 9, 3 to 4, 3 to 2, 1 to 1,
    6 to 5, 5 to 8, 4 to 6, 5 to 1,
    8 to 8,10 to 3, 9 to 6, 8 to 4,
    8 to 2, 1 to 6, 5 to 3, 2 to 7
  )

  // Waves: thời điểm & thời lượng
  private val waveStarts = floatArrayOf(0f, 4f, 6.5f)
  private val waveDurations = floatArrayOf(5.8f, 5.4f, 5.4f)

  // Dải dừng
  private val stopMin = 0.47f
  private val stopMax = 0.51f
  private val fadeTime = 0.35f

  // Rung nhẹ
  private val rotAmpDeg = 12f
  private val rotFreqHz = 0.7f

  // Text trên bảng gỗ (một lớp, bám bảng)
  private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.rgb(247, 241, 230) // kem ngà
    typeface = Typeface.create("DejaVu Serif", Typeface.BOLD)
    textAlign = Paint.Align.CENTER
  }

  // Thời gian
  private var tNow = 0f
  private val animator = ValueAnimator.ofFloat(0f, 10f).apply {
    duration = 10_000
    interpolator = LinearInterpolator()
    addUpdateListener { tNow = it.animatedValue as Float; invalidate() }
    start()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)

    // ====== 1) NỀN: cover-blur toàn màn + EXTRA BLUR CHỈ Ở DƯỚI (giảm ~10%) ======
    val iw = bg.width.toFloat()
    val ih = bg.height.toFloat()
    val screenAspect = width.toFloat() / height.toFloat()
    val imgAspect = iw / ih

    // "Cover" crop từ ảnh gốc để nền blur không viền đen
    val srcCover: Rect = if (imgAspect > screenAspect) {
      val newW = (ih * screenAspect).toInt()
      val leftC = ((iw - newW) / 2f).toInt()
      Rect(leftC, 0, leftC + newW, ih.toInt())
    } else {
      val newH = (iw / screenAspect).toInt()
      val topC = ((ih - newH) / 2f).toInt()
      Rect(0, topC, iw.toInt(), topC + newH)
    }
    val dstFull = Rect(0, 0, width, height)

    // Nền blur "vừa"
    run {
      val paintBlurBase = Paint(Paint.ANTI_ALIAS_FLAG)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val fx = RenderEffect.createBlurEffect(40f, 40f, Shader.TileMode.CLAMP)
        paintBlurBase.setRenderEffect(fx)
      }
      canvas.drawBitmap(bg, srcCover, dstFull, paintBlurBase)
    }

    // Khung ảnh theo tỉ lệ thật (letterbox) để biết mép dưới
    val scale = min(width / iw, height / ih)
    val sw = (iw * scale).toInt()
    val sh = (ih * scale).toInt()
    val left = (width - sw) / 2
    val top = (height - sh) / 2
    val contentBottom = top + sh

    // Extra blur chỉ ở phần dưới (giảm ~10% so với 60f)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val paintBlurStrong = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val fx = RenderEffect.createBlurEffect(54f, 54f, Shader.TileMode.CLAMP)
        setRenderEffect(fx)
      }
      val layerId = canvas.saveLayer(null, paintBlurStrong)
      // Phủ cover lên layer blur mạnh
      canvas.drawBitmap(bg, srcCover, dstFull, null)
      // Mask: giữ blur mạnh ở dưới (từ contentBottom -> đáy), mượt dần
      val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        shader = LinearGradient(
          0f, contentBottom.toFloat(),
          0f, height.toFloat(),
          0x00000000.toInt(),    // trong suốt ở mép ảnh
          0xFF000000.toInt(),    // đậm dần về đáy
          Shader.TileMode.CLAMP
        )
      }
      canvas.drawRect(0f, contentBottom.toFloat(), width.toFloat(), height.toFloat(), maskPaint)
      canvas.restoreToCount(layerId)
    }

    // Vẽ ảnh gốc theo tỉ lệ thật lên trên (nội dung không bị blur)
    val dstContent = Rect(left, top, left + sw, top + sh)
    canvas.drawBitmap(bg, null, dstContent, null)

    // ====== 2) PETALS: mưa theo lưới (neo cột), 3 wave, rung nhẹ, dừng & tan ======
    val topHalf = height * 0.5f
    val cols = 10f; val rows = 10f

    fun colToX(c: Int): Float = ((c - 0.5f) / cols) * width
    fun rowToY(r: Int): Float = ((r - 0.5f) / rows) * topHalf

    val anchors = coords.map { (c, r) -> Pair(colToX(c), rowToY(r)) }
    val yMax = anchors.maxOf { it.second }
    val yMin = anchors.minOf { it.second }

    // offset để kéo "tấm lưới" đi xuống
    val offsetStart = -(yMax + 0.06f * height) // bắt đầu cao hơn khung một chút
    val stopYMin = height * stopMin
    val stopYMax = height * stopMax
    val offsetEnd = (stopYMax) - yMin

    val paintPetal = Paint(Paint.ANTI_ALIAS_FLAG)

    for (w in 0 until 3) {
      val t0 = waveStarts[w]
      val ft = waveDurations[w]
      val dt = tNow - t0
      if (dt < 0f) continue

      val offset = if (dt <= ft) {
        val p = (dt / ft).coerceIn(0f, 1f)
        offsetStart + p * (offsetEnd - offsetStart)
      } else {
        offsetEnd
      }

      for (i in coords.indices) {
        val bmp = petals[i % petals.size]
        val (ax, ay) = anchors[i]

        // Dải dừng riêng cho từng cánh (ổn định trong khoảng 47–51%)
        val stopT = stopMin + (stopMax - stopMin) * ((i % 5) / 4f)
        val yStop = height * stopT

        var alpha = 1f
        var y = ay + offset

        if (y >= yStop) {
          // thời điểm chạm yStop
          val denom = (offsetEnd - offsetStart)
          val tCross = if (denom != 0f) ft * ((yStop - ay - offsetStart) / denom) else dt
          val fadeElapsed = max(0f, dt - tCross)
          alpha = (1f - fadeElapsed / fadeTime).coerceIn(0f, 1f)
          if (alpha <= 0f) continue
          y = yStop
        }

        val angle = rotAmpDeg * sin(2f * Math.PI.toFloat() * rotFreqHz * max(0f, dt) + i * 0.37f)
        paintPetal.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        canvas.save()
        canvas.translate(ax, y)
        canvas.rotate(angle)
        canvas.drawBitmap(bmp, -bmp.width / 2f, -bmp.height / 2f, paintPetal)
        canvas.restore()
      }
    }

    // ====== 3) TEXT: bám bảng gỗ, một lớp ======
    // Khung bảng theo toạ độ phần trăm chốt: x:30..70, y:60..73 (% màn)
    val padPct = 0.08f // vành an toàn 8%
    val bx0 = (0.30f + padPct) * width
    val bx1 = (0.70f - padPct) * width
    val by0 = (0.60f + padPct) * height
    val by1 = (0.73f - padPct) * height
    val textRect = RectF(bx0, by0, bx1, by1)

    // Ví dụ text mẫu; có thể thay từ code/strings sau
    val message = "Hoa hồng có gai nhọn,\nRose thì có sắc (nhọn)"
    drawAutoFitMultilineCentered(canvas, message, textRect, textPaint)
  }

  // === Utility: vẽ text tự co & bẻ dòng, canh giữa trong RectF ===
  private fun drawAutoFitMultilineCentered(canvas: Canvas, text: String, box: RectF, p: Paint) {
    // Tách dòng theo \n
    val lines = text.split("\n".toRegex())
    // Tìm cỡ chữ lớn nhất mà tổng chiều cao <= box.height và bề ngang từng dòng <= box.width
    var lo = 8f; var hi = 120f
    fun fits(size: Float): Boolean {
      p.textSize = size
      val fm = p.fontMetrics
      val lineH = (fm.bottom - fm.top)
      val totalH = lineH * lines.size
      if (totalH > box.height()) return false
      for (ln in lines) {
        val w = p.measureText(ln)
        if (w > box.width()) return false
      }
      return true
    }
    while (hi - lo > 0.5f) {
      val mid = (lo + hi) / 2f
      if (fits(mid)) lo = mid else hi = mid
    }
    p.textSize = lo

    // Vẽ canh giữa
    val fm = p.fontMetrics
    val lineH = (fm.bottom - fm.top)
    val totalH = lineH * lines.size
    var y = box.centerY() - totalH / 2f - fm.top
    for (ln in lines) {
      canvas.drawText(ln, box.centerX(), y, p)
      y += lineH
    }
  }
}
