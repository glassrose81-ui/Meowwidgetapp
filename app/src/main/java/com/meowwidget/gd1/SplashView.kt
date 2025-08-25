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
  private val bgMatrix = android.graphics.Matrix()


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
  private val longPressRunnable = Runnable {
    if (downInRightEdge) {
      panelOpen = true
      tFrozen = tNow // “đóng băng” tại thời điểm mở panel
      invalidate()
    }
  }

  // Vùng nút panel (tính động)
  private var panelRect = RectF()
  private var btnClose = RectF()
  private var btnBg = RectF()
  private var btnRMinus = RectF()
  private var btnRPlus = RectF()
  private var btnOMinus = RectF()
  private var btnOPlus = RectF()

  init {
    // Đọc cấu hình đã lưu (nếu có)
    bgMode = when (prefs.getInt("bg_mode", 1)) {
      0 -> BgMode.MINT
      1 -> BgMode.BLUR
      else -> BgMode.GRADIENT
    }
    petalScaleRound = prefs.getFloat("ps_round", 0.42f)
    petalScaleOther = prefs.getFloat("ps_other", 0.48f)
  }

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

  // ============ Vẽ nền với 3 chế độ phần dư ============
    // BEGIN GĐ1-PATCH: BG gaps only
  private fun drawBackground(canvas: Canvas) {
    val w = width
    val h = height
    val topGap = contentRect.top.coerceAtLeast(0)
    val bottomGap = (h - contentRect.bottom).coerceAtLeast(0)

    fun drawTealBands() {
      val teal = Color.parseColor("#104C4A")
      paint.shader = null; paint.color = teal
      if (topGap > 0) canvas.drawRect(0f, 0f, w.toFloat(), topGap.toFloat(), paint)
      if (bottomGap > 0) canvas.drawRect(0f, (h - bottomGap).toFloat(), w.toFloat(), h.toFloat(), paint)
    }
    fun drawGradientBands() {
      if (topGap > 0) {
        paint.shader = LinearGradient(0f, 0f, 0f, topGap.toFloat(),
          intArrayOf(Color.parseColor("#F2FBFF"), Color.parseColor("#E6F7FF")),
          floatArrayOf(0f,1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), topGap.toFloat(), paint)
      }
      if (bottomGap > 0) {
        paint.shader = LinearGradient(0f, (h - bottomGap).toFloat(), 0f, h.toFloat(),
          intArrayOf(Color.parseColor("#F2FBFF"), Color.parseColor("#E6F7FF")),
          floatArrayOf(0f,1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, (h - bottomGap).toFloat(), w.toFloat(), h.toFloat(), paint)
      }
      paint.shader = null
    }
    fun drawBottomBlurGap() {
      if (bottomGap <= 0) return
      val srcH = bg.height; val srcW = bg.width
      val y83 = (0.83f * srcH).toInt().coerceIn(0, srcH - 1)
      val blockH = (srcH - y83).coerceAtLeast(1)
      val srcRect = Rect(0, y83, srcW, srcH)
      val block = Bitmap.createBitmap(bg, srcRect.left, srcRect.top, srcRect.width(), srcRect.height())
      val s = bottomGap.toFloat() / blockH.toFloat()
      val scaledW = max(1, (block.width * s).roundToInt())
      val scaledH = bottomGap.coerceAtLeast(1)
      val scaled = Bitmap.createScaledBitmap(block, scaledW, scaledH, true)
      block.recycle()

      val smallW = max(1, scaledW / 14)
      val smallH = max(1, scaledH / 14)
      val tmpSmall = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888)
      Canvas(tmpSmall).drawBitmap(scaled, Rect(0,0,scaledW,scaledH), Rect(0,0,smallW,smallH), paint)
      val blurred = Bitmap.createScaledBitmap(tmpSmall, scaledW, scaledH, true)
      tmpSmall.recycle(); scaled.recycle()

      val gapTop = (h - bottomGap).toFloat()
      val bmpShader = BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
      val m = Matrix().apply { setTranslate(((w - blurred.width)/2f), gapTop) }
      bmpShader.setLocalMatrix(m)
      val save = canvas.saveLayer(RectF(0f, gapTop, w.toFloat(), h.toFloat()), null)
      paint.shader = bmpShader
      canvas.drawRect(0f, gapTop, w.toFloat(), h.toFloat(), paint)

      val soften = min(130f, bottomGap.toFloat())
      val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.shader = LinearGradient(0f, gapTop, 0f, gapTop + soften,
          intArrayOf(Color.argb(210,0,0,0), Color.argb(255,0,0,0)),
          floatArrayOf(0f,1f), Shader.TileMode.CLAMP)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
      }
      canvas.drawRect(0f, gapTop, w.toFloat(), gapTop + soften, mask)
      canvas.restoreToCount(save); paint.shader = null

      val bandH = min(200, bottomGap)
      
      blurred.recycle()
    }
    when (bgMode) {
      BgMode.MINT -> drawTealBands()
      BgMode.GRADIENT -> drawGradientBands()
      BgMode.BLUR -> {
      if (topGap > 0) {
        val bandH = (0.02f * bg.height).toInt().coerceAtLeast(1)
        val safeH = bandH.coerceAtMost(bg.height)
        if (safeH > 0) {
          val topBand = Bitmap.createBitmap(bg, 0, 0, bg.width, safeH)
          val px = Bitmap.createScaledBitmap(topBand, 1, 1, true)
          val avg = px.getPixel(0, 0)
          topBand.recycle(); px.recycle()
          paint.shader = null; paint.color = avg
          canvas.drawRect(0f, 0f, w.toFloat(), topGap.toFloat(), paint)
        }
      }
      drawBottomBlurGap()
    }
    }
    canvas.drawBitmap(bg, null, contentRect, paint)
bgMatrix.setRectToRect(
    android.graphics.RectF(0f, 0f, bg.width.toFloat(), bg.height.toFloat()),
    contentRect,
    android.graphics.Matrix.ScaleToFit.FILL
)

  }
  // END GĐ1-PATCH

  // ============ Chuyển (C,R) → (x,y) trong contentRect ============
  private fun colToX(c: Int): Float =
    contentRect.left + ((c - 0.5f) / cols) * contentRect.width()

  private fun rowToY(r: Int): Float {
    val usableH = contentRect.height() * 0.5f // chỉ 0–50% chiều cao
    return contentRect.top + ((r - 0.5f) / rows) * usableH
  }

  // ============ Panel ẩn: layout nút ============
  private fun layoutPanel() {
    val pw = width * 0.8f
    val ph = height * 0.38f
    val px = (width - pw) / 2f
    val py = height * 0.12f
    panelRect.set(px, py, px + pw, py + ph)

    val pad = 16f * resources.displayMetrics.density
    val rowH = 44f * resources.displayMetrics.density
    val btnW = 120f * resources.displayMetrics.density
    val small = 56f * resources.displayMetrics.density

    btnClose.set(panelRect.right - pad - small, panelRect.top + pad, panelRect.right - pad, panelRect.top + pad + small)
    btnBg.set(panelRect.left + pad, panelRect.top + pad + rowH * 0f, panelRect.left + pad + btnW, panelRect.top + pad + rowH)

    btnRMinus.set(panelRect.left + pad, panelRect.top + pad + rowH * 1.2f, panelRect.left + pad + small, panelRect.top + pad + rowH * 1.2f + small)
    btnRPlus .set(btnRMinus.right + pad, btnRMinus.top, btnRMinus.right + pad + small, btnRMinus.bottom)

    btnOMinus.set(panelRect.left + pad, panelRect.top + pad + rowH * 2.2f, panelRect.left + pad + small, panelRect.top + pad + rowH * 2.2f + small)
    btnOPlus .set(btnOMinus.right + pad, btnOMinus.top, btnOMinus.right + pad + small, btnOMinus.bottom)
  }

  // ============ Vẽ panel ẩn ============  // BEGIN GĐ1-PATCH: Textbox on board (contentRect-relative)
  private fun drawTextBox(canvas: Canvas) {
bx1 = (contentRect.left + 0.30f * contentRect.width())
// Neo theo ảnh gốc (L30–R70–T58–B74) rồi map sang canvas
val boardRectImg = android.graphics.RectF(
    0.30f * bg.width,
    0.58f * bg.height,
    0.70f * bg.width,
    0.74f * bg.height
)
val boardRectCanvas = android.graphics.RectF(boardRectImg)
bgMatrix.mapRect(boardRectCanvas)

val bx1 = boardRectCanvas.left.toInt()
val by1 = boardRectCanvas.top.toInt()
val bx2 = boardRectCanvas.right.toInt()
val by2 = boardRectCanvas.bottom.toInt()

    val bw = (bx2 - bx1).coerceAtLeast(1)
    val bh = (by2 - by1).coerceAtLeast(1)

    val padX = (0.03f * bw).toInt()
    val padY = (0.03f * bh).toInt()
    val maxW = (bw - 2*padX).coerceAtLeast(1)
    val maxH = (bh - 2*padY).coerceAtLeast(1)

    val text = "Hoa hồng có gai nhọn còn Rose thì có sắc (nhọn)"
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      textAlign = Paint.Align.LEFT
      typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }

    fun wrap(linesOut: MutableList<String>, sizePx: Float): Pair<Int, Int> {
      tp.textSize = sizePx
      linesOut.clear()

      val words = text.split(" ")
      val curLine = StringBuilder()
      val built = mutableListOf<String>()

      // Greedy wrap theo maxW
      for (wd in words) {
        val tryLine = if (curLine.isEmpty()) wd else curLine.toString() + " " + wd
        val tw = tp.measureText(tryLine)
        if (tw <= maxW) {
          curLine.clear(); curLine.append(tryLine)
        } else {
          if (curLine.isNotEmpty()) built.add(curLine.toString())
          curLine.clear(); curLine.append(wd)
        }
      }
      if (curLine.isNotEmpty()) built.add(curLine.toString())

      linesOut.addAll(built)

      var wmax = 0f
      for (ln in linesOut) wmax = max(wmax, tp.measureText(ln))

      val fm = tp.fontMetrics
      val lineH = (fm.descent - fm.ascent) * 1.15f
      val totalH = (linesOut.size * lineH).toInt()

      return Pair(wmax.toInt(), totalH)
    }

    // Binary search size to fit
    var lo = 10f
    var hi = bh.toFloat()
    var bestSize = lo
    var bestLines: List<String> = listOf()
    val tmp = mutableListOf<String>()
    while (hi - lo >= 0.5f) {
      tmp.clear()
      val mid = (lo + hi) / 2f
      val (mw, mh) = wrap(tmp, mid)
      if (mw <= maxW && mh <= maxH && tmp.isNotEmpty()) {
        bestSize = mid; bestLines = tmp.toList(); lo = mid + 0.5f
      } else hi = mid - 0.5f
    }
    tp.textSize = bestSize

    val fm2 = tp.fontMetrics
    val lineH2 = (fm2.descent - fm2.ascent) * 1.15f
    val blockH = bestLines.size * lineH2
    var y = by1 + padY + ((maxH - blockH) / 2f).coerceAtLeast(0f) - fm2.ascent
    for (ln in bestLines) {
      val lw = tp.measureText(ln)
      val x = bx1 + padX + ((maxW - lw) / 2f)
      canvas.drawText(ln, x, y, tp)
      y += lineH2
    }
  }
  // END GĐ1-PATCH


  private fun drawPanel(canvas: Canvas) {
    layoutPanel()
    // nền mờ
    paint.color = 0x88000000.toInt()
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    // thẻ panel
    paint.color = Color.WHITE
    canvas.drawRoundRect(panelRect, 24f, 24f, paint)

    // text helper
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

    // Nút close
    paint.color = Color.parseColor("#EEEEEE")
    canvas.drawRoundRect(btnClose, 14f, 14f, paint)
    drawTextCenter(btnClose, "×", 22f, true)

    // Nút cycle BG
    paint.color = Color.parseColor("#F5F5F5")
    canvas.drawRoundRect(btnBg, 14f, 14f, paint)
    drawTextCenter(btnBg, "BG: " + when (bgMode) {
      BgMode.MINT -> "Mint"
      BgMode.BLUR -> "Blur"
      BgMode.GRADIENT -> "Grad"
    }, 15f, true)

    // R- / R+ (round)
    paint.color = Color.parseColor("#F7F7F7")
    canvas.drawRoundRect(btnRMinus, 12f, 12f, paint)
    drawTextCenter(btnRMinus, "R −", 14f, true)
    canvas.drawRoundRect(btnRPlus, 12f, 12f, paint)
    drawTextCenter(btnRPlus, "R +", 14f, true)

    // O- / O+ (other)
    canvas.drawRoundRect(btnOMinus, 12f, 12f, paint)
    drawTextCenter(btnOMinus, "O −", 14f, true)
    canvas.drawRoundRect(btnOPlus, 12f, 12f, paint)
    drawTextCenter(btnOPlus, "O +", 14f, true)

    // Thông tin hiện tại
    val infoY = panelRect.bottom - 18f * resources.displayMetrics.scaledDensity
    paint.textAlign = Paint.Align.LEFT
    paint.color = Color.DKGRAY
    paint.textSize = 14f * resources.displayMetrics.scaledDensity
    canvas.drawText("Round: ${"%.2f".format(petalScaleRound)} · Other: ${"%.2f".format(petalScaleOther)}", panelRect.left + 16f, infoY, paint)
  }

  // ============ Xử lý chạm (mở/điều khiển panel) ============
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
        // nếu kéo ra khỏi vùng phải thì hủy long-press
        if (longPressPosted) {
          val stillOnRight = event.x >= width * (1f - rightEdgeFraction)
          if (!stillOnRight) {
            downInRightEdge = false
          }
        }
        // nếu panel đang mở: xử lý tap vào nút
        if (panelOpen) return true
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        if (longPressPosted) {
          longPressPosted = false
          removeCallbacks(longPressRunnable)
        }
        if (panelOpen && event.actionMasked == MotionEvent.ACTION_UP) {
          // click test các nút
          val x = event.x; val y = event.y
          fun inside(r: RectF) = x >= r.left && x <= r.right && y >= r.top && y <= r.bottom
          var changed = false
          when {
            inside(btnClose) -> { panelOpen = false; invalidate(); return true }
            inside(btnBg) -> {
              bgMode = when (bgMode) {
                BgMode.MINT -> BgMode.BLUR
                BgMode.BLUR -> BgMode.GRADIENT
                BgMode.GRADIENT -> BgMode.MINT
              }
              prefs.edit().putInt("bg_mode", when (bgMode) {
                BgMode.MINT -> 0; BgMode.BLUR -> 1; BgMode.GRADIENT -> 2
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

  // ============ Vẽ toàn cảnh ============
  override fun onDraw(canvas: Canvas) {
    // 3.x) Textbox trên bảng gỗ
    drawTextBox(canvas)

    super.onDraw(canvas)

    // 1) nền (giữ tỉ lệ + blur phần dư)
    drawBackground(canvas)

    // 2) tính anchor theo contentRect
    val anchors = coords.map { (c, r) -> Pair(colToX(c), rowToY(r)) }
    val yMin = anchors.minOf { it.second }
    val yMax = anchors.maxOf { it.second }

    // offset tuyến tính: từ trên (ngoài contentRect) xuống tới dải stopMax
    val offsetStart = (contentRect.top - yMax) - 0.06f * contentRect.height()
    val offsetEnd = (contentRect.top + contentRect.height() * stopMax) - yMin

    // 3) vẽ 3 wave (kéo lưới)
    val baseTime = if (panelOpen) tFrozen else tNow
    for (w in 0 until 3) {
      val t0 = waveStarts[w]
      val ft = waveDurations[w]
      val dt = baseTime - t0
      if (dt < 0f) continue

      val offsetY = if (dt <= ft) {
        val p = (dt / ft).coerceIn(0f, 1f)
        offsetStart + p * (offsetEnd - offsetStart)
      } else {
        offsetEnd
      }

      for (i in anchors.indices) {
        val bmp = petalsSrc[i % petalsSrc.size]
        val (ax, ay) = anchors[i]
        var y = ay + offsetY

        // dải dừng theo contentRect
        val yStop = contentRect.top + contentRect.height() * (
          stopMin + (stopMax - stopMin) * (i / (anchors.size - 1f))
        )

        var alpha = 1f
        if (y >= yStop) {
          // thời điểm cắt vào dải stop
          val denom = (offsetEnd - offsetStart)
          val dtCross = if (denom != 0f) ft * ((yStop - ay - offsetStart) / denom) else dt
          val fadeElapsed = max(0f, dt - dtCross)
          alpha = (1f - fadeElapsed / fadeTime).coerceIn(0f, 1f)
          if (alpha <= 0f) continue
          y = yStop
        }

        // cỡ cánh theo bề ngang ô lưới trong contentRect
        val cellW = contentRect.width() / cols
        val ar = bmp.width.toFloat() / bmp.height.toFloat()
        val isRound = abs(ar - 1f) <= 0.12f
        val desiredW = (if (isRound) petalScaleRound else petalScaleOther) * cellW
        val s = (desiredW / bmp.width).coerceAtMost(1.5f).coerceAtLeast(0.05f)

        // đong đưa nhẹ, không xô ngang
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

    // 5) schedule khung tiếp theo
    postInvalidateOnAnimation()
  }
}
