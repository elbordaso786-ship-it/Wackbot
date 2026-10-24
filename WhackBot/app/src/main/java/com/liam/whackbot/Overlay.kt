package com.liam.whackbot

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Small control pill pinned to the top-left (over the HUD, away from the holes so it never
 * pollutes detection). Red = AUTO TAP ON, green = preview only.
 */
class Overlay(private val ctx: Context, private val onToggle: () -> Unit, private val onStop: () -> Unit) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val ui = Handler(Looper.getMainLooper())
    private val text = TextView(ctx).apply { setTextColor(Color.WHITE); textSize = 11f; setPadding(12, 4, 12, 4) }
    private val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xCC224422.toInt())
        addView(text)
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(ctx).apply { this.text = "MODE"; textSize = 11f; setOnClickListener { onToggle() } })
        row.addView(Button(ctx).apply { this.text = "STOP"; textSize = 11f; setOnClickListener { onStop() } })
        addView(row)
    }

    fun show() {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 60 }
        ui.post { wm.addView(root, lp) }
    }

    fun set(s: String, auto: Boolean) = ui.post {
        text.text = s
        root.setBackgroundColor(if (auto) 0xDDCC0000.toInt() else 0xCC224422.toInt())
    }

    fun hide() = ui.post { runCatching { wm.removeView(root) } }
}
