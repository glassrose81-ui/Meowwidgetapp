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

    setContentView(root)
    Handler(Looper.getMainLooper()).postDelayed({
      startActivity(Intent(this, MainActivity::class.java)); finish()
    }, splashDurationMs)
  }
}
