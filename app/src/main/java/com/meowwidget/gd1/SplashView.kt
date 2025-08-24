package com.meowwidget.gd1
import android.content.Context
import android.graphics.*
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

  // Grid & anchors (C,R) as báº¡n Ä‘Ã£ chá»‘t (Ã¡p dá»¥ng cho cáº£ 3 wave)
  private val coords: List<Pair<Int,Int>> = listOf(
    3 to 9, 3 to 4, 3 to 2, 1 to 1,
    6 to 5, 5 to 8, 4 to 6, 5 to 1,
    8 to 8,10 to 3, 9 to 6, 8 to 4,
    8 to 2, 1 to 6, 5 to 3, 2 to 7
  )

  // Waves: thá»i Ä‘iá»ƒm & thá»i lÆ°á»£ng
  private val waveStarts = floatArrayOf(0f, 4f, 6.5f)
  private val waveDurations = floatArrayOf(5.8f, 5.4f, 5.4f)

  // Dáº£i dá»«ng
  private val stopMin = 0.47f
  private val stopMax = 0.51f
  private val fadeTime = 0.35f

  // Rung nháº¹
  private val rotAmpDeg = 12f
  private val rotFreqHz = 0.7f

  // Text trÃªn báº£ng gá»— (má»™t lá»›p, bÃ¡m báº£ng)
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.rgb(247, 241, 230) // kem ngÃ 
    typeface = Typeface.create("DejaVu Serif", Typeface.BOLD)
    textAlign = Paint.Align.CENTER
  }

  // Thá»i gian
  private var tNow = 0f
  private val animator = ValueAnimator.ofFloat(0f, 10f).apply {
    duration = 10_000
    interpolator = LinearInterpolator()
    addUpdateListener { tNow = it.animatedValue as Float; invalidate() }
    start()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)

    // ====== 1) Ná»€N: cover-blur toÃ n mÃ n + EXTRA BLUR CHá»ˆ á»ž DÆ¯á»šI (giáº£m ~10%) ======
    val iw = bg.width.toFloat()
    val ih = bg.height.toFloat()
    val screenAspect = width.toFloat() / height.toFloat()
    val imgAspect = iw / ih

    // "Cover" crop tá»« áº£nh gá»‘c Ä‘á»ƒ ná»n blur khÃ´ng cÃ³ viá»n Ä‘en
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

    // Ná»n blur "vá»«a"
    val blurBase = RenderEffect.createBlurEffect(40f, 40f, Shader.TileMode.CLAMP)
    val paintBlurBase = Paint().apply { renderEffect = blurBase }
    canvas.drawBitmap(bg, srcCover, dstFull, paintBlurBase)

    // TÃ­nh khung áº£nh theo tá»‰ lá»‡ tháº­t (letterbox) Ä‘á»ƒ biáº¿t mÃ©p dÆ°á»›i
    val scale = min(width / iw, height / ih)
    val sw = (iw * scale).toInt()
    val sh = (ih * scale).toInt()
    val left = (width - sw) / 2
    val top = (height - sh) / 2
    val contentBottom = top + sh

    // Extra blur chá»‰ á»Ÿ pháº§n dÆ°á»›i (giáº£m ~10% vs 60f)
    val blurStrong = RenderEffect.createBlurEffect(54f, 54f, Shader.TileMode.CLAMP)
    val paintBlurStrong = Paint().apply { renderEffect = blurStrong }
    val strongLayer = canvas.saveLayer(null, paintBlurStrong)
    // Phá»§ cover lÃªn layer blur máº¡nh
    canvas.drawBitmap(bg, srcCover, dstFull, null)
    // Mask Ä‘á»ƒ GIá»® blur máº¡nh á»Ÿ dÆ°á»›i (tá»« contentBottom -> Ä‘Ã¡y), mÆ°á»£t dáº§n
    val maskPaint = Paint().apply {
      xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
      shader = LinearGradient(
        0f, contentBottom.toFloat(),
        0f, height.toFloat(),
        0x00000000.toInt(),    // trong suá»‘t táº¡i mÃ©p áº£nh
        0xFF000000.toInt(),    // Ä‘áº­m dáº§n vá» Ä‘Ã¡y
        Shader.TileMode.CLAMP
      )
    }
    canvas.drawRect(0f, contentBottom.toFloat(), width.toFloat(), height.toFloat(), maskPaint)
    canvas.restoreToCount(strongLayer)

    // Váº½ áº£nh gá»‘c theo tá»‰ lá»‡ tháº­t lÃªn trÃªn (ná»™i dung khÃ´ng bá»‹ blur)
    val dstContent = Rect(left, top, left + sw, top + sh)
    canvas.drawBitmap(bg, null, dstContent, null)

    // ====== 2) PETALS: mÆ°a theo lÆ°á»›i (neo cá»™t), 3 wave, rung nháº¹, dá»«ng & tan ======
    val topHalf = height * 0.5f
    val cols = 10f; val rows = 10f

    fun colToX(c: Int): Float = ((c - 0.5f) / cols) * width
    fun rowToY(r: Int): Float = ((r - 0.5f) / rows) * topHalf

    val anchors = coords.map { (c, r) -> Pair(colToX(c), rowToY(r)) }
    val yMax = anchors.maxOf { it.second }
    val yMin = anchors.minOf { it.second }

    // offset Ä‘á»ƒ kÃ©o "táº¥m lÆ°á»›i" Ä‘i xuá»‘ng
    val offsetStart = -(yMax + 0.06f * height) // báº¯t Ä‘áº§u cao hÆ¡n khung má»™t chÃºt
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

        // Dáº£i dá»«ng riÃªng cho tá»«ng cÃ¡nh (á»•n Ä‘á»‹nh)
        val stopT = stopMin + (stopMax - stopMin) * ((i % 5) / 4f)
        val yStop = height * stopT

        var alpha = 1f
        var y = ay + offset

        if (y >= yStop) {
          // thá»i Ä‘iá»ƒm cháº¡m yStop
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

    // ====== 3) TEXT: bÃ¡m báº£ng gá»—, má»™t lá»›p ======
    // Khung báº£ng theo toáº¡ Ä‘á»™ pháº§n trÄƒm Ä‘Ã£ chá»‘t: x:30..70, y:60..73 (% mÃ n)
    val padPct = 0.08f // vÃ nh an toÃ n 8%
    val bx0 = (0.30f + padPct) * width
    val bx1 = (0.70f - padPct) * width
    val by0 = (0.60f + padPct) * height
    val by1 = (0.73f - padPct) * height
    val textRect = RectF(bx0, by0, bx1, by1)

    // VÃ­ dá»¥ text máº«u; cÃ³ thá»ƒ thay tá»« code/strings sau
    val message = "Hoa há»“ng cÃ³ gai nhá»n,\\nRose thÃ¬ cÃ³ sáº¯c (nhá»n)"

    drawAutoFitMultilineCentered(canvas, message, textRect, textPaint)
  }

  // === Utility: váº½ text tá»± co & báº» dÃ²ng, canh giá»¯a trong RectF ===
  private fun drawAutoFitMultilineCentered(canvas: Canvas, text: String, box: RectF, p: Paint) {
    // TÃ¡ch dÃ²ng theo \\n
    val lines = text.split("\\n".toRegex())
    // TÃ¬m cá»¡ chá»¯ lá»›n nháº¥t mÃ  tá»•ng chiá»u cao <= box.height vÃ  bá» ngang tá»«ng dÃ²ng <= box.width
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

    // Váº½ canh giá»¯a
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

