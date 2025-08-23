package com.meowwidget.gd1
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat

class SplashActivity : AppCompatActivity() {
  private val splashDurationMs = 10_000L
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val root = FrameLayout(this)
    val splashView = SplashView(this)
    root.addView(splashView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

    val tv = AppCompatTextView(this).apply {
      text = "Hoa hồng có gai nhọn, Rose thì có sắc (nhọn)"
      setTextColor(Color.rgb(245,236,210)) // kem ngà
      typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD)
      TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 14, 64, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
      setLineSpacing(0f, 1.30f)
      textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
      post {
        val w = root.width; val h = root.height
        val lp = FrameLayout.LayoutParams((0.40f*w).toInt(), (0.13f*h).toInt())
        lp.leftMargin = (0.30f*w).toInt(); lp.topMargin = (0.60f*h).toInt()
        layoutParams = lp
      }
    }
    root.addView(tv)

    setContentView(root)
    Handler(Looper.getMainLooper()).postDelayed({
      startActivity(Intent(this, MainActivity::class.java)); finish()
    }, splashDurationMs)
  }
}
