package com.meowwidget.gd1

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*

class SplashView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // --- Assets ---
    private val bgBitmap: Bitmap by lazy {
        // Prefer meow_bg; fallback to bg
        val res = resources
        val id1 = res.getIdentifier("meow_bg", "drawable", context.packageName)
        val id = if (id1 != 0) id1 else res.getIdentifier("bg", "drawable", context.packageName)
        BitmapFactory.decodeResource(res, id)
    }

    private val petalBitmaps: List<Bitmap> by lazy {
        val res = resources
        // Collect petal1..petal40 if available
        val ids = (1..40).map { res.getIdentifier("petal$it", "drawable", context.packageName) }
            .filter { it != 0 }
        if (ids.isNotEmpty()) ids.map { BitmapFactory.decodeResource(res, it) }
        else {
            // Fallback to a single demo petal (named "petal" or "petal1")
            val fid = res.getIdentifier("petal", "drawable", context.packageName)
            val f = if (fid != 0) fid else res.getIdentifier("petal1", "drawable", context.packageName)
            listOfNotNull(if (f != 0) BitmapFactory.decodeResource(res, f) else null)
        }
    }

    // --- Layout helpers (computed on size change) ---
    private var bgDest: RectF = RectF()
    private var boardRect: RectF = RectF()

    // Prebuilt blurred backfill (cover-blur)
    private var blurredFill: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Quote text
    private val quote =
        "Hoa hồng có gai nhọn, Rose thì có sắc (nhọn)"
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }

    // --- Grid anchors (C,R) confirmed by you ---
    // Columns and rows are 1..10 normalized
    private val anchorsCR: List<Pair<Int, Int>> = listOf(
        3 to 9,
        3 to 4,
        3 to 2,
        1 to 1,
        6 to 5,
        5 to 8,
        4 to 6,
        5 to 1,
        8 to 8,
        10 to 3,
        9 to 6,
        8 to 4,
        8 to 2, // #14
        1 to 6, // #15
        5 to 3, // #16
        2 to 7  // #17
    )

    // Animation state for the "sheet" falling
    private var tAnim = 0f
    private val animator: ValueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        // Wave1 5.8s + Wave2 5.4 + Wave3 5.4 = loop ~16.6s
        // We animate a single 0..1 progress, and compute three waves from it deterministically.
        duration = 16600L
        interpolator = LinearInterpolator()
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            tAnim = animatedValue as Float
            invalidate()
        }
    }

    init {
        animator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0 || bgBitmap.width == 0 || bgBitmap.height == 0) return

        // Fit background by width (no vertical stretch)
        val scale = w.toFloat() / bgBitmap.width
        val destH = bgBitmap.height * scale
        val top = (h - destH) * 0.5f
        bgDest.set(0f, top, w.toFloat(), top + destH)

        // Board rectangle (relative to background art)
        // Tuned to the wooden board position in the image:
        val left = lerp(bgDest.left, bgDest.right, 0.28f)
        val right = lerp(bgDest.left, bgDest.right, 0.72f)
        val topB = lerp(bgDest.top, bgDest.bottom, 0.63f)
        val bottomB = lerp(bgDest.top, bgDest.bottom, 0.83f)
        boardRect.set(left, topB, right, bottomB)

        // Prepare blurred fill bitmap that matches the full view size.
        blurredFill = buildBlurredFill(w, h)
        // Text size to approximately fill 70% of board width
        textPaint.textSize = (boardRect.width() * 0.09f).coerceAtLeast(28f)
    }

    private fun buildBlurredFill(w: Int, h: Int): Bitmap? {
        // Cheap gaussian-like blur by downscaling then upscaling
        if (w <= 0 || h <= 0) return null
        // Make a center-crop of bg to view size first
        val src = bgBitmap
        val srcAspect = src.width / src.height.toFloat()
        val dstAspect = w / h.toFloat()
        val srcRect = if (srcAspect > dstAspect) {
            // crop width
            val newW = (src.height * dstAspect).toInt()
            val x = (src.width - newW) / 2
            Rect(x, 0, x + newW, src.height)
        } else {
            // crop height
            val newH = (src.width / dstAspect).toInt()
            val y = (src.height - newH) / 2
            Rect(0, y, src.width, y + newH)
        }

        // Draw to full-size canvas
        val full = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cFull = Canvas(full)
        cFull.drawColor(0xFF0F4D4D.toInt()) // fallback teal if anything fails
        cFull.drawBitmap(src, srcRect, Rect(0, 0, w, h), paint)

        // Downscale aggressively then upscale -> blur
        val dw = max(1, w / 24); val dh = max(1, h / 24)
        val tiny = Bitmap.createScaledBitmap(full, dw, dh, true)
        val blurred = Bitmap.createScaledBitmap(tiny, w, h, true)
        tiny.recycle()
        return blurred
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // 1) Draw blurred backfill (covers whole screen)
        blurredFill?.let { canvas.drawBitmap(it, 0f, 0f, paint) }
            ?: run { canvas.drawColor(0xFF0F4D4D.toInt()) }

        // 2) Draw the crisp background image into bgDest (no stretch)
        canvas.drawBitmap(bgBitmap, null, bgDest, paint)

        // 3) Draw quote, text boxed & centered inside boardRect (auto-wrap)
        drawWrappedCenteredText(canvas, quote, boardRect, textPaint)

        // 4) Draw petals as a "sheet" falling (3 waves using the same grid)
        drawPetals(canvas, w, h)
    }

    // --- Text layout helper ---
    private fun drawWrappedCenteredText(
        canvas: Canvas,
        text: String,
        box: RectF,
        tp: TextPaint
    ) {
        // Simple word wrapping
        val words = text.split(" ")
        val lines = ArrayList<String>()
        var current = StringBuilder()
        for (w in words) {
            val test = if (current.isEmpty()) w else "${current} $w"
            if (tp.measureText(test) <= box.width() * 0.92f) {
                current.clear(); current.append(test)
            } else {
                lines.add(current.toString()); current = StringBuilder(w)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())

        val fm = tp.fontMetrics
        val lineH = (fm.bottom - fm.top) * 1.05f
        val totalH = lineH * lines.size
        var y = box.centerY() - totalH / 2f - fm.top

        for (ln in lines) {
            canvas.drawText(ln, box.centerX(), y, tp)
            y += lineH
        }
    }

    // --- Petals ---
    private fun colToX(col: Int): Float = lerp(bgDest.left, bgDest.right, (col - 0.5f) / 10f)
    private fun rowToY(row: Int): Float = lerp(bgDest.top, bgDest.top + (bgDest.height() * 0.5f), (row - 0.5f) / 10f)

    private fun drawPetals(canvas: Canvas, vw: Float, vh: Float) {
        if (petalBitmaps.isEmpty()) return

        // Shared downward "grid offset" across all petals (kéo lưới)
        // 3 waves with same speed; stop/fade within 47-51% of canvas height
        val waves = arrayOf(
            0f to 5.8f, // startSec to durationSec
            5.8f to 5.4f,
            11.2f to 5.4f
        )
        val tSec = tAnim * 16.6f

        for ((start, dur) in waves) {
            val local = ((tSec - start) / dur).coerceIn(0f, 1f)
            if (local <= 0f || local > 1f) continue

            val yStart = -vh * 0.25f
            val yStop = vh * 0.49f
            val gridOffset = lerp(yStart, yStop, local)

            for ((idx, cr) in anchorsCR.withIndex()) {
                val bmp = petalBitmaps[idx % petalBitmaps.size]
                // scale petals (already reduced from previous huge size)
                val s = 0.35f // tuned small
                val bmpW = bmp.width * s
                val bmpH = bmp.height * s

                val ax = colToX(cr.first)
                val ayBase = rowToY(cr.second)
                val ay = ayBase + gridOffset

                // soft sway
                val sway = sin((local * Math.PI * 2).toFloat() + idx * 0.37f) * 7f
                val rot = sin((local * Math.PI * 2).toFloat() * 0.7f + idx) * 5f

                // fade within 47–51%
                val alpha =
                    if (ay < yStop) 1f
                    else (1f - ((ay - yStop) / (vh * 0.02f))).coerceIn(0f, 1f)

                paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
                canvas.save()
                canvas.translate(ax + sway, ay)
                canvas.rotate(rot)
                canvas.drawBitmap(bmp, -bmpW / 2f, -bmpH / 2f, paint)
                canvas.restore()
            }
        }
        paint.alpha = 255
    }
}
