package com.meowwidget.gd1
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.animation.ValueAnimator
import kotlin.math.*

class SplashView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
  private val bg: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.bg)
  private val petals: List<Bitmap> = listOf(
    BitmapFactory.decodeResource(resources, R.drawable.petal1),
    BitmapFactory.decodeResource(resources, R.drawable.petal2),
    BitmapFactory.decodeResource(resources, R.drawable.petal3),
    BitmapFactory.decodeResource(resources, R.drawable.petal4)
  )
  // Lưới & vị trí đã chốt
  private val coords = listOf(3 to 9,3 to 4,3 to 2,1 to 1, 6 to 5,5 to 8,4 to 6,5 to 1, 8 to 8,10 to 3,9 to 6,8 to 4, 8 to 2,1 to 6,5 to 3,2 to 7)
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

  // Wave theo thời điểm đã chốt
  private val waveStarts = floatArrayOf(0f, 4f, 6.5f)
  private val waveDurations = floatArrayOf(5.8f, 5.4f, 5.4f)
  private val fadeTime = 0.35f
  private val stopMin = 0.47f; private val stopMax = 0.51f

  private val rotAmpDeg = 12f; private val rotFreqHz = 0.7f
  private var tNow = 0f
  private val animator = ValueAnimator.ofFloat(0f, 10f).apply {
    duration = 10_000; interpolator = LinearInterpolator()
    addUpdateListener { tNow = it.animatedValue as Float; invalidate() }; start()
  }
  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val dst = Rect(0, 0, width, height)
canvas.drawBitmap(bg, null, dst, paint)

val cols = 10f
val rows = 10f
val topHalf = height * 0.5f

fun colToX(c: Int): Float = ((c - 0.5f) / cols) * width
fun rowToY(r: Int): Float = ((r - 0.5f) / rows) * topHalf

val anchors = coords.map { (c, r) -> Pair(colToX(c), rowToY(r)) }
val yMax = anchors.maxOf { it.second }

    val offsetStart = -(yMax + 0.06f*height); val offsetEnd = (0.51f*height) - anchors.minOf { it.second }

    for (w in 0 until 3) {
      val t0 = waveStarts[w]; val ft = waveDurations[w]; val dt = tNow - t0; if (dt < 0f) continue
      val offset = if (dt <= ft) { val p=(dt/ft).coerceIn(0f,1f); offsetStart + p*(offsetEnd-offsetStart) } else { offsetEnd }

      for (i in coords.indices) {
        val bmp = petals[i % petals.size]
        val (ax, ay)=anchors[i]
        var y = ay + offset

        val yStop = height * (stopMin + (stopMax - stopMin) * (i / (coords.size-1f)))
        var alpha = 1f
        if (y >= yStop) {
          val denom=(offsetEnd-offsetStart)
          val dtCross = if (denom!=0f) ft * ((yStop - ay - offsetStart) / denom) else dt
          val fadeElapsed = max(0f, dt - dtCross)
          alpha = (1f - fadeElapsed / fadeTime).coerceIn(0f,1f)
          if (alpha <= 0f) continue
          y = yStop
        }

        val angle = rotAmpDeg * sin(2f * Math.PI.toFloat() * rotFreqHz * max(0f, dt) + i*0.37f)
        paint.alpha = (alpha*255).toInt().coerceIn(0,255)
        canvas.save(); canvas.translate(ax, y); canvas.rotate(angle)
        canvas.drawBitmap(bmp, -bmp.width/2f, -bmp.height/2f, paint); canvas.restore()
      }
    }
  }
}
