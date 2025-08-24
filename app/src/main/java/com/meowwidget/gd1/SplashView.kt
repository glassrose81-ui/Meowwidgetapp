package com.meowwidget.gd1

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*

class SplashView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : View(context, attrs) {

...
    BitmapFactory.decodeResource(resources, R.drawable.petal1),
    BitmapFactory.decodeResource(resources, R.drawable.petal2),
    BitmapFactory.decodeResource(resources, R.drawable.petal3),
    BitmapFactory.decodeResource(resources, R.drawable.petal4)
  )

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

  // ============ Lưới & tọa độ (neo cố định) ============
  // 16 điểm như bạn chốt (C,R)
  private val coords: List<Pair<Int, Int>> = listOf(
    3 to 9, 3 to 4, 3 to 2, 1 to 1,
    6 to 5, 5 to 8, 4 to 6, 5 to 1,
    8 to 8, 10 to 3, 9 to 6, 8 to 4,
    8 to 2, 1 to 6, 5 to 3, 2 to 7
  )

  // Lưới 10×10, vùng rơi: 0–50% chiều cao contentRect
  private val cols = 10f
  private val rows = 10f

  // ============ Wave & hiệu ứng ============
  private val waveStarts = floatArrayOf(0f, 4.0f, 6.5f)
  private val waveDurations = floatArrayOf(5.8f, 5.4f, 5.4f)
  private val fadeTime = 0.35f
  private val stopMin = 0.47f
  private val stopMax = 0.51f
  private val rotAmpDeg = 12f
  private val rotFreqHz = 0.7f

  // ============ Nền & contentRect ============
  private var contentRect = Rect()         // vùng ảnh gốc fit đúng tỉ lệ
  private var blurredFull: Bitmap? = null  // nền mờ phủ full view (tạo 1 lần theo size)
  private var cropSrcForBlur = Rect()

  // ============ Thời gian & animator ============
  private var tNow = 0f
  private var tFrozen = 0f
  private var panelOpen = false

  private val animator = ValueAnimator.ofFloat(0f, 10f).apply {
    duration = 10_000
    interpolator = LinearInterpolator()
    addUpdateListener {
      val v = (it.animatedValue as Float)
      if (!panelOpen) {
        tNow = v
      } // panel mở = đóng băng thời gian hiển thị
      invalidate()
    }
    start()
  }

  // ============ Panel ẩn (tuỳ chọn) ============
  // Kiểu nền cho phần dư ngoài contentRect
  private enum class BgMode { MINT, BLUR, GRADIENT }

  private val prefs by lazy { context.getSharedPreferences("meow_splash", Context.MODE_PRIVATE) }
  private var bgMode: BgMode = BgMode.BLUR
  // Cỡ cánh (tính theo bề ngang mỗi ô)
  private var petalScaleRound = 0.42f  // tròn
  private var petalScaleOther = 0.48f  // còn lại

  private val rightEdgeFraction = 0.20f // vùng bấm mép phải để mở panel
  private val longPressMs = 3000L
  private var downInRightEdge = false
  private var longPressPosted = false
  private val longPressRunnable = Runnabl
...
  // ============ Layout / size changes ============
  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    if (w <= 0 || h <= 0) return

    // 1) Tính contentRect: fit/contain (giữ tỉ lệ ảnh) và căn giữa
    val bw = bg.width; val bh = bg.height
    val scale = min(w.toFloat() / bw, h.toFloat() / bh)
    val sw = (bw * scale).toInt()
    val sh = (bh * scale).toInt()
    val left = (w - sw) / 2
    val top = (h - sh) / 2
    contentRect = Rect(left, top, left + sw, top + sh)

    // 2) Tạo blurredFull: crop ảnh theo tỉ lệ màn hình → downscale mạnh → upscale
    val viewAspect = w.toFloat() / h.toFloat()
    val bmpAspect = bw.toFloat() / bh.toFloat()
    cropSrcForBlur = if (bmpAspect > viewAspect) {
      val srcW = (bh * viewAspect).toInt().coerceIn(1, bw)
      val l = ((bw - srcW) / 2).coerceAtLeast(0)
      Rect(l, 0, (l + srcW).coerceAtMost(bw), bh)
    } else {
      val srcH = (bw / viewAspect).toInt().coerceIn(1, bh)
      val t = ((bh - srcH) / 2).coerceAtLeast(0)
      Rect(0, t, bw, (t + srcH).coerceAtMost(bh))
    }
    // downscale → upscale
    val smallW = max(16, w / 16)
    val smallH = max(16, h / 16)
    val tmpSmall = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888)
    Canvas(tmpSmall).drawBitmap(bg, cropSrcForBlur, Rect(0, 0, smallW, smallH), paint)
    blurredFull?.recycle()
    blurredFull = Bitmap.createScaledBitmap(tmpSmall, w, h, true)
    tmpSmall.recycle()
  }

  // BEGIN GĐ1-PATCH: draw background (Blur/Teal/Gradient) respecting contentRect gaps only
  fun drawBackground(canvas: Canvas) {
    val w = width
    val h = height
    val topGap = contentRect.top.coerceAtLeast(0)
    val bottomGap = (h - contentRect.bottom).coerceAtLeast(0)

    fun drawTealBands() {
      val teal = Color.parseColor("#104C4A")
      paint.shader = null
      paint.color = teal
      if (topGap > 0) canvas.drawRect(0f, 0f, w.toFloat(), topGap.toFloat(), paint)
      if (bottomGap > 0) canvas.drawRect(0f, (h - bottomGap).toFloat(), w.toFloat(), h.toFloat(), paint)
    }

    fun drawGradientBands() {
      // Use the existing gradient palette but clip to gaps only
      val shaderTop = LinearGradient(
        0f, 0f, 0f, topGap.toFloat().coerceAtLeast(1f),
        intArrayOf(Color.parseColor("#F2FBFF"), Color.parseColor("#E6F7FF")),
        floatArrayOf(0f, 1f),
        Shader.TileMode.CLAMP
      )
      val shaderBottom = LinearGradient(
        0f, (h - bottomGap).toFloat(), 0f, h.toFloat(),
        intArrayOf(Color.parseColor("#F2FBFF"), Color.parseColor("#E6F7FF")),
        floatArrayOf(0f, 1f),
        Shader.TileMode.CLAMP
      )
      if (topGap > 0) {
        paint.shader = shaderTop
        canvas.drawRect(0f, 0f, w.toFloat(), topGap.toFloat(), paint)
      }
      if (bottomGap > 0) {
        paint.shader = shaderBottom
        canvas.drawRect(0f, (h - bottomGap).toFloat(), w.toFloat(), h.toFloat(), paint)
      }
      paint.shader = null
    }

    fun drawBottomBlurGap() {
      if (bottomGap <= 0) return

      // 1) Copy-block từ 83% chiều cao ảnh gốc → đáy
      val srcH = bg.height
      val srcW = bg.width
      val y83 = (0.83f * srcH).toInt().coerceIn(0, srcH - 1)
      val blockH = (srcH - y83).coerceAtLeast(1)
      val srcRect = Rect(0, y83, srcW, srcH)
      val block = Bitmap.createBitmap(bg, srcRect.left, srcRect.top, srcRect.width(), srcRect.height())

      // 2) Uniform zoom cho block cao = bottomGap
      val s = bottomGap.toFloat() / blockH.toFloat()
      val scaledW = max(1, (block.width * s).roundToInt())
      val scaledH = bottomGap.coerceAtLeast(1)
      val scaled = Bitmap.createScaledBitmap(block, scaledW, scaledH, true)
      block.recycle()

      // 3) Làm mờ gần Gaussian: downscale → upscale
      val smallW = max(1, scaledW / 14)
      val smallH = max(1, scaledH / 14)
      val tmpSmall = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888)
      val cSmall = Canvas(tmpSmall)
      cSmall.drawBitmap(scaled, Rect(0,0,scaledW,scaledH), Rect(0,0,smallW,smallH), paint)
      val blurred = Bitmap.createScaledBitmap(tmpSmall, scaledW, scaledH, true)
      tmpSmall.recycle()
      scaled.recycle()

      // 4) Dán vào bottomGap bằng BitmapShader(CLAMP) để mép trái/phải tự “kéo màu” biên
      val gapTop = (h - bottomGap).toFloat()
      val shader = BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
      val local = Matrix()
      val left = ((w - blurred.width) / 2f)
      local.setTranslate(left, gapTop)
      shader.setLocalMatrix(local)
      val save = canvas.saveLayer(RectF(0f, gapTop, w.toFloat(), h.toFloat()), null)
      paint.shader = shader
      canvas.drawRect(0f, gapTop, w.toFloat(), h.toFloat(), paint)

      // 5) Làm mềm đường ráp phía trên (DST_IN alpha gradient ~130px)
      val soften = min(130f, bottomGap.toFloat())
      val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
      maskPaint.shader = LinearGradient(
        0f, gapTop, 0f, gapTop + soften,
        intArrayOf(Color.argb(210, 0, 0, 0), Color.argb(255, 0, 0, 0)),
        floatArrayOf(0f, 1f),
        Shader.TileMode.CLAMP
      )
      maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
      canvas.drawRect(0f, gapTop, w.toFloat(), gapTop + soften, maskPaint)
      canvas.restoreToCount(save)
      paint.shader = null
      maskPaint.xfermode = null

      // 6) Dải sáng đáy nhẹ (0 → ~52 alpha)
      val bandH = min(200, bottomGap)
      if (bandH > 0) {
        val highlight = Paint(Paint.ANTI_ALIAS_FLAG)
        highlight.shader = LinearGradient(
          0f, (h - bandH).toFloat(), 0f, h.toFloat(),
          intArrayOf(Color.argb(0, 255,255,255), Color.argb(52, 255,255,255)),
          floatArrayOf(0f, 1f),
          Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, (h - bandH).toFloat(), w.toFloat(), h.toFloat(), highlight)
        highlight.shader = null
      }

      blurred.recycle()
    }

    when (bgMode) {
      BgMode.MINT -> {
        // Thay “mint phủ full” bằng TEAL #104C4A chỉ ở hai dải trên/dưới (không đè ảnh)
        drawTealBands()
      }
      BgMode.GRADIENT -> {
        // Gradient trắng chỉ áp vào 2 dải gap
        drawGradientBands()
      }
      BgMode.BLUR -> {
        // Chỉ lấp *dưới ảnh* bằng block 83%→đáy đã zoom + blur; phần trên giữ nguyên
        drawBottomBlurGap()
      }
    }
    // Ảnh gốc fit đúng tỉ lệ đặt lên contentRect
    canvas.drawBitmap(bg, null, contentRect, paint)
  }
  // END GĐ1-PATCH

  // ============ Chuyển (C,R) → (x,y) trong contentRect ============
  private fun colToX(c: Int): Float =
    contentRect.left 
...
        paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        canvas.save()
        canvas.translate(ax, y)
        canvas.rotate(angle)
        canvas.scale(s, s)
        canvas.drawBitmap(bmp, -bmp.width / 2f, -bmp.height / 2f, paint)
        canvas.restore()
      }
    }

    // 3) Textbox trên bảng gỗ
    drawTextBox(canvas)

    // 4) Panel ẩn
    if (panelOpen) drawPanel(canvas)

    // 5) schedule khung tiếp theo
    postInvalidateOnAnimation()
  }

  // BEGIN GĐ1-PATCH: Textbox on board (contentRect-relative)
  private fun drawTextBox(canvas: Canvas) {
    // Board box dựa theo contentRect, nới ±2% trục dọc: x 30–70%, y 58–75%
    val bx1 = (contentRect.left + 0.30f * contentRect.width()).toInt()
    val bx2 = (contentRect.left + 0.70f * contentRect.width()).toInt()
    val by1 = (contentRect.top  + 0.58f * contentRect.height()).toInt()
    val by2 = (contentRect.top  + 0.75f * contentRect.height()).toInt()
    val bw = (bx2 - bx1).coerceAtLeast(1)
    val bh = (by2 - by1).coerceAtLeast(1)

    // Padding ~3%
    val padX = (0.03f * bw).toInt()
    val padY = (0.03f * bh).toInt()
    val maxW = (bw - 2*padX).coerceAtLeast(1)
    val maxH = (bh - 2*padY).coerceAtLeast(1)

    // Nội dung + style
    val text = "Hoa hồng có gai nhọn còn Rose thì có sắc (nhọn)"
    val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    tp.color = Color.WHITE
    tp.textAlign = Paint.Align.LEFT
    tp.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) // DejaVu Serif Bold nếu đóng gói, fallback SERIF Bold

    // Auto-fit: binary search kích thước + greedy wrap
    fun wrap(linesOut: MutableList<String>, sizePx: Float): Pair<Int, Int> {
      tp.textSize = sizePx
      linesOut.clear()
      val words = text.split(" ")
      var cur = StringBuilder()
      var wmax = 0f
      val fmH = tp.fontMetrics
      val lineH = (fmH.descent - fmH.ascent)
      var totalH = 0f
      for (w in words) {
        val trial = if (cur.isEmpty()) w else cur.toString() + " " + w
        val tw = tp.measureText(trial)
        if (tw <= maxW) {
          cur.clear(); cur.append(trial)
        } else {
          if (cur.isNotEmpty()) {
            linesOut.add(cur.toString())
            wmax = max(wmax, tp.measureText(cur.toString()))
            totalH += lineH * 1.03f
          }
          cur.clear(); cur.append(w)
        }
      }
      if (cur.isNotEmpty()) {
        linesOut.add(cur.toString())
        wmax = max(wmax, tp.measureText(cur.toString()))
        totalH += lineH * 1.03f
      }
      return Pair(wmax.roundToInt(), totalH.roundToInt())
    }

    var lo = 10f
    var hi = bh.toFloat() // trần rộng rãi
    var bestSize = lo
    var bestLines: List<String> = listOf()
    while (hi - lo >= 0.5f) {
      val mid = (lo + hi) / 2f
      val tmp = mutableListOf<String>()
      val (mw, mh) = wrap(tmp, mid)
      if (mw <= maxW && mh <= maxH && tmp.isNotEmpty()) {
        bestSize = mid; bestLines = tmp.toList(); lo = mid + 0.5f
      } else hi = mid - 0.5f
    }
    tp.textSize = bestSize

    // Vẽ canh giữa box
    val fm = tp.fontMetrics
    val lineH = (fm.descent - fm.ascent) * 1.03f
    val blockH = (bestLines.size * lineH).toFloat()
    var y = by1 + padY + ((maxH - blockH) / 2f).coerceAtLeast(0f) - fm.ascent
    for (ln in bestLines) {
      val lw = tp.measureText(ln)
      val x = bx1 + padX + ((maxW - lw) / 2f)
      canvas.drawText(ln, x, y, tp)
      y += lineH
    }
  }
  // END GĐ1-PATCH
}

