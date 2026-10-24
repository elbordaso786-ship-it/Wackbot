package com.liam.whackbot

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** One screen: two big start buttons. The app walks you through any missing permission by itself. */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private var want: Boolean? = null      // null = nothing requested, false = test mode, true = auto play
    private var awaiting: String? = null   // which settings screen we sent the user to
    private var autoRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(48, 120, 48, 48)
        }
        col.addView(TextView(this).apply { text = "WhackBot"; textSize = 32f; gravity = Gravity.CENTER })
        status = TextView(this).apply { textSize = 15f; gravity = Gravity.CENTER; setPadding(0, 24, 0, 48) }
        col.addView(status)
        fun big(label: String, color: Int, action: () -> Unit) = col.addView(
            Button(this).apply {
                text = label; textSize = 18f; setBackgroundColor(color); setTextColor(Color.WHITE)
                setOnClickListener { action() }
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 220).apply { bottomMargin = 32 },
        )
        big("START - TEST MODE\n(shows what it would tap, taps nothing)", 0xFF2E7D32.toInt()) { begin(false) }
        big("START - AUTO PLAY\n(taps for real)", 0xFFC62828.toInt()) { begin(true) }
        big("STOP", 0xFF455A64.toInt()) {
            startService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
        }
        col.addView(TextView(this).apply {
            text = "After pressing START, open the game. A small bar at the top-left shows the mode. " +
                "Press MODE on it to switch test/auto, STOP to quit."
            textSize = 13f; gravity = Gravity.CENTER
        })
        setContentView(col)
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    private fun begin(auto: Boolean) { want = auto; awaiting = null; proceed() }

    private fun hasOverlay() = Settings.canDrawOverlays(this)
    private fun hasTapService() = TapService.instance != null

    private fun proceed() {
        val auto = want ?: return
        when {
            !hasOverlay() -> {
                awaiting = "overlay"
                Toast.makeText(this, "Turn ON 'Allow display over other apps' for WhackBot, then press Back", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            auto && !hasTapService() -> {
                awaiting = "tap"
                Toast.makeText(this, "Open 'Installed apps', tap WhackBot, turn it ON, then press Back", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            else -> {
                autoRequested = auto; want = null; awaiting = null
                val mpm = getSystemService(MediaProjectionManager::class.java)
                startActivityForResult(mpm.createScreenCaptureIntent(), 1001)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        status.text = when {
            Control.running -> "Running (${if (Control.autoEnabled) "AUTO" else "TEST"} mode)"
            else -> "Ready. Press a START button."
        }
        val a = awaiting
        if (a != null) {
            awaiting = null
            val ok = if (a == "overlay") hasOverlay() else hasTapService()
            if (ok) proceed() else { want = null; Toast.makeText(this, "That wasn't turned on - press START again", Toast.LENGTH_LONG).show() }
        }
    }

    @Deprecated("fine for a single request")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            startForegroundService(
                Intent(this, CaptureService::class.java)
                    .putExtra("code", resultCode).putExtra("data", data).putExtra("auto", autoRequested),
            )
            Toast.makeText(this, "Started - open the game now", Toast.LENGTH_LONG).show()
            moveTaskToBack(true)
        }
    }
}
