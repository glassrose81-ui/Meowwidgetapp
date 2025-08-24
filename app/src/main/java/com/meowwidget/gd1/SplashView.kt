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

  // ============ Assets ============
  private val bg: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.bg)
  private val petalsSrc: List<Bitmap> = listOf(
    BitmapFactory.decodeResource(resources, R.drawable.petal1),
    BitmapFactory.decodeResource(resources, R.drawable.petal2),
    BitmapFactory.decodeResource(resources, R.drawable.petal3),
    BitmapFactory.decodeResource(resources, R.drawable.petal4)
  )

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

  // ============ Lưới & tọa độ (neo cố định) ============
  private val coords: List<Pair<Int, Int>> = listOf(
    3 to 9, 3 to 4, 3 to 2, 1 to 1,
    6 to 5, 5 to 8, 4 to 6, 5 to 1,
    8 to 8, 10 to 3, 9 to 6, 8 to 4,
    8 to 2, 1 to 6, 5 to 3, 2 to 7
  )
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
  private var contentRect = Rect()
  private var blurredFull: Bitmap? = null
  private var cropSrcForBlur = Rect()

  // ============ Văn bản trên bảng gỗ ============
  // Vùng chữ theo chuẩn bạn chốt: x:30–70%, y:60–73% trong contentRect
  private val boardRectNorm = RectF(0.30f, 0.60f, 0.70f, 0.73f)
  private val boardPadFrac = 0.09f // ~9% padding trong vùng chữ
  private val textColor = Color.parseColor("#F8EAD8") // kem ngà
  private val lineSpacing = 1.22f
  // Bạn có thể đổi chuỗi này sau; GĐ2 sẽ thay bằng nguồn động
  private val displayText = "Hoa hồng có gai nhọn,\nRose thì có sắc (nhọn)"

  // ============ Thời gian & animator ============
  private var tNow = 0f
  private var tFrozen = 0f
  private var panelOpen = false

  private val animator = ValueAnimator.ofFloat(0f, 10f).apply {
    duration = 10_000
    interpolator = LinearInterpolator()
    addUpdateListener {
      val v = (it.animatedValue as Float)
      if (!panelOpen) tNow = v
      invalidate()
    }
    start()
  }

  // ============ Panel ẩn ============
  private enum class BgMode { TEAL, BLUR, GRADIENT }

  private val prefs by lazy { context.getSharedPreferences("meow_splash", Context.MODE_PRIVATE) }
  private var bgMode: BgMode = BgMode.BLUR
  private var petalScaleRound = 0.42f  // theo bề ngang ô
  private var petalScaleOther = 0.48f

  private val rightEdgeFraction = 0.20f
  private val longPressMs = 3000L
  private var downInRightEdge = false
  private var longPressPosted = false
  private val longPressRunnable = Runnable {
    if (downInRightEdge) {
      panelOpen = true
      tFrozen = tNow
      invalidate()
    }
  }

  private var panelRect = RectF()
  private var btnClose = RectF()
  private var btnBg = RectF()
  private var btnRMinus = RectF()
  private var btnRPlus = RectF()
  private var btnOMinus = RectF()
  private var btnOPlus = RectF()

  init {
    // Map 0->TEAL, 1->BLUR, 2->GRADIENT (giữ tương thích lưu cũ)
    bgMode = when (prefs.getInt("bg_mode", 1)) {
      0 -> BgMode.TEAL
      1 -> BgMode.BLUR
      else -> BgMode.GRADIENT
    }
    petalScaleRound = prefs.getFloat("ps_round", 0.42f)
    petalScaleOther = prefs.getFloat("ps_other", 0.48f)
  }

  // ============ Helpers ============
  private fun withAlpha(color: Int, alpha: Int): Int {
    return (alpha.coerceIn(0,255) shl 24) or (color and 0x00FFFFFF)
  }

  private fun avgColor(bmp: Bitmap, src: Rect, samples: Int = 7): Int {
    var r = 0; var g = 0; var b = 0; var n = 0
    val stepX = max(1, src.width() / samples)
    val stepY = max(1, src.height() / samples)
    var y = src.top
    while (y < src.bottom) {
      var x = src.left
      while (x < src.right) {
        val c = bmp.getPixel(x.coerceIn(0,bmp.width-1), y.coerceIn(0,bmp.height-1))
        r += (c shr 16) and 0xFF
        g += (c shr 8) and 0xFF
        b += (c) and 0xFF
        n++
        x += stepX
      }
      y += stepY
    }
    if (n == 0) return Color.BLACK
    return Color.rgb(r/n, g/n, b/n)
  }

  // ============ Layout ============
  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    if (w <= 0 || h <= 0) return

    // Fit ảnh gốc vào contentRect (giữ tỉ lệ, căn giữa)
    val bw = bg.width; val bh = bg.height
    val scale = min(w.toFloat() / bw, h.toFloat() / bh)
    val sw = (bw * scale).toInt()
    val sh = (bh * scale).toInt()
    val left = (w - sw) / 2
    val top = (h - sh) / 2
    contentRect = Rect(left, top, left + sw, top + sh)

    // Nền mờ phủ full view: crop theo tỉ lệ màn rồi downscale→upscale
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
    val smallW = max(16, w / 16)
    val smallH = max(16, h / 16)
    val tmpSmall = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888)
    Canvas(tmpSmall).drawBitmap(bg, cropSrcForBlur, Rect(0, 0, smallW, smallH), paint)
    blurredFull?.recycle()
    blurredFull = Bitmap.createScaledBitmap(tmpSmall, w, h, true)
    tmpSmall.recycle()
  }

  // ============ Nền ============
  private fun drawBackground(canvas: Canvas) {
    when (bgMode) {
      BgMode.TEAL -> {
        canvas.drawColor(Color.parseColor("#174D4A"))
      }
      BgMode.GRADIENT -> {
        val shader = LinearGradient(
          0f, 0f, 0f, height.toFloat(),
          intArrayOf(Color.parseColor("#F2FBFF"), Color.parseColor("#E6F7FF")),
          floatArrayOf(0f, 1f),
          Shader.TileMode.CLAMP
        )
        paint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
      }
      BgMode.BLUR -> {
        // lớp blur phủ full
        blurredFull?.let { canvas.drawBitmap(it, 0f, 0f, paint) } ?: canvas.drawColor(Color.BLACK)

        // Ảnh gốc đặt trong contentRect (không kéo méo)
        canvas.drawBitmap(bg, null, contentRect, paint)

        // ---- Blur đậm hơn cho dải dưới (ngoài contentRect) ----
        val bottomTop = contentRect.bottom.toFloat()
        if (bottomTop < height) {
          // Lấy màu trung bình vùng đáy của ảnh gốc để tint
          val srcBottom = Rect(
            cropSrcForBlur.left,
            (cropSrcForBlur.bottom - max(8, cropSrcForBlur.height()/6)).coerceAtLeast(cropSrcForBlur.top),
            cropSrcForBlur.right,
            cropSrcForBlur.bottom
          )
          val tint = avgColor(bg, srcBottom)
          val cStart = withAlpha(tint, 0)
          val cEnd = withAlpha(tint, 230) // đậm để “nuốt” chi tiết
          val shader = LinearGradient(
            0f, bottomTop, 0f, height.toFloat(),
            intArrayOf(cStart, cEnd),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
          )
          paint.shader = shader
          canvas.drawRect(0f, bottomTop, width.toFloat(), height.toFloat(), paint)
          paint.shader = null
        }
      }
    }
  }

  // ============ (C,R) → (x,y) trong contentRect ============
  private fun colToX(c: Int): Float =
    contentRect.left + ((c - 0.5f) / cols) * contentRect.width()

  private fun rowToY(r: Int): Float {
    val usableH = contentRect.height() * 0.5f
    return contentRect.top + ((r - 0.5f) / rows) * usableH
  }

  // ============ Panel ẩn ============
  private fun layoutPanel() {
    val pw = width * 0.8f
    val ph = height * 0.38f
    val px = (width - pw) / 2f
    val py = height * 0.12f
    panelRect.set(px, py, px + pw, py + ph)

    val pad = 16f * resources.displayMetrics.density
    val rowH = 44f * resources.displayMetrics.scaledDensity
    val btnW = 120f * resources.displayMetrics.scaledDensity
    val small = 56f * resources.displayMetrics.scaledDensity

    btnClose.set(panelRect.right - pad - small, panelRect.top + pad, panelRect.right - pad, panelRect.top + pad + small)
    btnBg.set(panelRect.left + pad, panelRect.top + pad + rowH * 0f, panelRect.left + pad + btnW, panelRect.top + pad + rowH)

    btnRMinus.set(panelRect.left + pad, panelRect.top + pad + rowH * 1.2f, panelRect.left + pad + small, panelRect.top + pad + rowH * 1.2f + small)
    btnRPlus .set(btnRMinus.right + pad, btnRMinus.top, btnRMinus.right + pad + small, btnRMinus.bottom)

    btnOMinus.set(panelRect.left + pad, panelRect.top + pad + rowH * 2.2f, panelRect.left + pad + small, panelRect.top + pad + rowH * 2.2f + small)
    btnOPlus .set(btnOMinus.right + pad, btnOMinus.top, btnOMinus.right + pad + small, btnOMinus.bottom)
  }

  private fun drawPanel(canvas: Canvas) {
    layoutPanel()
    paint.color = 0x88000000.toInt()
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

    paint.color = Color.WHITE
    canvas.drawRoundRect(panelRect, 24f, 24f, paint)

    fun drawTextCenter(r: RectF, txt: String, sizeSp: Float = 16f, bold: Boolean = false) {
      paint.textSize = sizeSp * resources.displayMetrics.scaledDensity
      paint.color = Color.BLACK
      paint.isFakeBoldText = bold
      paint.textAlign = Paint.Align.CENTER
      val fm = paint.fontMetrics
      val cy = (r.top + r.bottom) / 2f - (fm.ascent + fm.descent) / 2f
      canvas.drawText(txt, (r.left + r.right) / 2f, cy, paint)
      paint.isFakeBoldText = false
    }

    // ×
    paint.color = Color.parseColor("#EEEEEE")
    canvas.drawRoundRect(btnClose, 14f, 14f, paint)
    drawTextCenter(btnClose, "×", 22f, true)

    // BG: Teal/Blur/Grad
    paint.color = Color.parseColor("#F5F5F5")
    canvas.drawRoundRect(btnBg, 14f, 14f, paint)
    drawTextCenter(btnBg, "BG: " + when (bgMode) {
      BgMode.TEAL -> "Teal"
      BgMode.BLUR -> "Blur"
      BgMode.GRADIENT -> "Grad"
    }, 15f, true)

    // R-/R+ (round)
    paint.color = Color.parseColor("#F7F7F7")
    canvas.drawRoundRect(btnRMinus, 12f, 12f, paint)
    drawTextCenter(btnRMinus, "R −", 14f, true)
    canvas.drawRoundRect(btnRPlus, 12f, 12f, paint)
    drawTextCenter(btnRPlus, "R +", 14f, true)

    // O-/O+ (other)
    canvas.drawRoundRect(btnOMinus, 12f, 12f, paint)
    drawTextCenter(btnOMinus, "O −", 14f, true)
    canvas.drawRoundRect(btnOPlus, 12f, 12f, paint)
    drawTextCenter(btnOPlus, "O +", 14f, true)

    // Thông tin hiện tại
    paint.textAlign = Paint.Align.LEFT
    paint.color = Color.DKGRAY
    paint.textSize = 14f * resources.displayMetrics.scaledDensity
    canvas.drawText("Round: ${"%.2f".format(petalScaleRound)} · Other: ${"%.2f".format(petalScaleOther)}",
      panelRect.left + 16f, panelRect.bottom - 18f * resources.displayMetrics.scaledDensity, paint)
  }

  // ============ Touch ============
  override fun onTouchEvent(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        val onRight = event.x >= width * (1f - rightEdgeFraction)
        downInRightEdge = onRight
        if (downInRightEdge && !panelOpen) {
          if (!longPressPosted) {
            longPressPosted = true
            postDelayed(longPressRunnable, longPressMs)
          }
        }
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        if (longPressPosted) {
          val stillOnRight = event.x >= width * (1f - rightEdgeFraction)
          if (!stillOnRight) downInRightEdge = false
        }
        if (panelOpen) return true
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        if (longPressPosted) {
          longPressPosted = false
          removeCallbacks(longPressRunnable)
        }
        if (panelOpen && event.actionMasked == MotionEvent.ACTION_UP) {
          val x = event.x; val y = event.y
          fun inside(r: RectF) = x >= r.left && x <= r.right && y >= r.top && y <= r.bottom
          var changed = false
          when {
            inside(btnClose) -> { panelOpen = false; invalidate(); return true }
            inside(btnBg) -> {
              bgMode = when (bgMode) {
                BgMode.TEAL -> BgMode.BLUR
                BgMode.BLUR -> BgMode.GRADIENT
                BgMode.GRADIENT -> BgMode.TEAL
              }
              prefs.edit().putInt("bg_mode", when (bgMode) {
                BgMode.TEAL -> 0; BgMode.BLUR -> 1; BgMode.GRADIENT -> 2
              }).apply()
              changed = true
            }
            inside(btnRMinus) -> { petalScaleRound = max(0.20f, petalScaleRound - 0.02f); changed = true }
            inside(btnRPlus)  -> { petalScaleRound = min(0.80f, petalScaleRound + 0.02f); changed = true }
            inside(btnOMinus) -> { petalScaleOther = max(0.20f, petalScaleOther - 0.02f); changed = true }
            inside(btnOPlus)  -> { petalScaleOther = min(0.80f, petalScaleOther + 0.02f); changed = true }
          }
          if (changed) {
            prefs.edit().putFloat("ps_round", petalScaleRound)
              .putFloat("ps_other", petalScaleOther).apply()
            invalidate()
          }
          return true
        }
        return true
      }
    }
    return super.onTouchEvent(event)
  }

  // ============ Vẽ chữ trong bảng gỗ ============
  private fun drawBoardText(canvas: Canvas) {
    // Tính vùng chữ theo contentRect + padding
    val bx = contentRect.left + boardRectNorm.left * contentRect.width()
    val by = contentRect.top  + boardRectNorm.top  * contentRect.height()
    val bw = (boardRectNorm.width()) * contentRect.width()
    val bh = (boardRectNorm.height()) * contentRect.height()
    val padX = bw * boardPadFrac
    val padY = bh * boardPadFrac
    val textRect = RectF(bx + padX, by + padY, bx + bw - padX, by + bh - padY)

    // Auto-fit cỡ chữ + word-wrap đơn giản
    val maxSize = 34f * resources.displayMetrics.scaledDensity
    val minSize = 16f * resources.displayMetrics.scaledDensity
    paint.color = textColor
    paint.textAlign = Paint.Align.LEFT
    paint.isFakeBoldText = false

    fun wrapLines(sizePx: Float): Pair<List<String>, Float> {
      paint.textSize = sizePx
      val words = displayText.replace("\n", " \n ").split(" ")
      val lines = mutableListOf<String>()
      var cur = StringBuilder()
      for (w in words) {
        if (w == "\n") {
          lines += cur.toString().trim()
          cur = StringBuilder()
          continue
        }
        val trial = (if (cur.isEmpty()) w else cur.toString() + " " + w)
        if (paint.measureText(trial) <= textRect.width()) {
          cur.clear(); cur.append(trial)
        } else {
          if (cur.isNotEmpty()) lines += cur.toString().trim()
          cur = StringBuilder(w)
        }
      }
      if (cur.isNotEmpty()) lines += cur.toString().trim()

      val fm = paint.fontMetrics
      val lineH = (fm.bottom - fm.top) * lineSpacing
      val totalH = lineH * lines.size
      return lines to totalH
    }

    var size = maxSize
    var lines: List<String>
    var totalH: Float
    while (true) {
      val (ls, h) = wrapLines(size)
      lines = ls; totalH = h
      if (totalH <= textRect.height() || size <= minSize) break
      size *= 0.94f
      if (size < minSize) { size = minSize; break }
    }

    // Vẽ căn giữa trong textRect
    paint.textSize = size
    val fm = paint.fontMetrics
    val lineH = (fm.bottom - fm.top) * lineSpacing
    var y = textRect.centerY() - totalH / 2f - (fm.ascent + fm.descent)/2f
    for (line in lines) {
      val x = textRect.centerX() - paint.measureText(line)/2f
      canvas.drawText(line, x, y, paint)
      y += lineH
    }
  }

  // ============ Vẽ toàn cảnh ============
  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)

    // 1) nền (fit + blur mạnh dải dưới)
    drawBackground(canvas)

    // 2) Neo chữ vào bảng (bên trong contentRect)
    drawBoardText(canvas)

    // 3) Petals theo “kéo lưới”
    val anchors = coords.map { (c, r) -> Pair(colToX(c), rowToY(r)) }
    val yMin = anchors.minOf { it.second }
    val yMax = anchors.maxOf { it.second }
    val offsetStart = (contentRect.top - yMax) - 0.06f * contentRect.height()
    val offsetEnd = (contentRect.top + contentRect.height() * stopMax) - yMin
    val baseTime = if (panelOpen) tFrozen else tNow

    for (w in 0 until 3) {
      val t0 = waveStarts[w]
      val ft = waveDurations[w]
      val dt = baseTime - t0
      if (dt < 0f) continue

      val offsetY = if (dt <= ft) {
        val p = (dt / ft).coerceIn(0f, 1f)
        offsetStart + p * (offsetEnd - offsetStart)
      } else offsetEnd

      for (i in anchors.indices) {
        val bmp = petalsSrc[i % petalsSrc.size]
        val (ax, ay) = anchors[i]
        var y = ay + offsetY

        val yStop = contentRect.top + contentRect.height() * (
          stopMin + (stopMax - stopMin) * (i / (anchors.size - 1f))
        )

        var alpha = 1f
        if (y >= yStop) {
          val denom = (offsetEnd - offsetStart)
          val dtCross = if (denom != 0f) ft * ((yStop - ay - offsetStart) / denom) else dt
          val fadeElapsed = max(0f, dt - dtCross)
          alpha = (1f - fadeElapsed / fadeTime).coerceIn(0f, 1f)
          if (alpha <= 0f) continue
          y = yStop
        }

        // cỡ cánh theo bề ngang ô
        val cellW = contentRect.width() / cols
        val ar = bmp.width.toFloat() / bmp.height.toFloat()
        val isRound = abs(ar - 1f) <= 0.12f
        val desiredW = (if (isRound) petalScaleRound else petalScaleOther) * cellW
        val s = (desiredW / bmp.width).coerceAtMost(1.5f).coerceAtLeast(0.05f)

        val angle = rotAmpDeg * sin(2f * Math.PI.toFloat() * rotFreqHz * max(0f, dt) + i * 0.37f)

        paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        canvas.save()
        canvas.translate(ax, y)
        canvas.rotate(angle)
        canvas.scale(s, s)
        canvas.drawBitmap(bmp, -bmp.width / 2f, -bmp.height / 2f, paint)
        canvas.restore()
      }
    }

    // 4) Panel ẩn
    if (panelOpen) drawPanel(canvas)

    // 5) khung tiếp theo
    postInvalidateOnAnimation()
  }
}
