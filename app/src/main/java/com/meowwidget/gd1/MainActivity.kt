package com.meowwidget.gd1
import android.graphics.Color
import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val root = FrameLayout(this)
    val tv = AppCompatTextView(this).apply {
      text = "The Meow Widget — Màn chính (placeholder)"
      setTextColor(Color.DKGRAY); textSize = 20f; textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
    }
    root.addView(tv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    setContentView(root)
  }
}
