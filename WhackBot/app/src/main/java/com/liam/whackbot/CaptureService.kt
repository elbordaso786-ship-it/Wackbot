package com.liam.whackbot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import com.liam.whackbot.core.*

class CaptureService : Service() {
    companion object {
        const val ACTION_STOP = "com.liam.whackbot.STOP"
        const val TAG = "WhackBot"
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var overlay: Overlay? = null
    private lateinit var detector: Detector
    private val planner = TapPlanner()

    private var w = 0
    private var h = 0
    private var badFrames = 0
    private var lastFrameMs = 0L
    private var fps = 0f
    private var lastUiMs = 0L
    private var taps = 0
    private var wouldTap = 0
    private var latencyMs = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { shutdown("stopped"); return START_NOT_STICKY }
        if (Control.running) return START_NOT_STICKY
        startForegroundNow()
        val code = intent?.getIntExtra("code", 0) ?: 0
        val data = intent?.getParcelableExtra("data", Intent::class.java)
        if (data == null) { shutdown("no projection permission"); return START_NOT_STICKY }

        detector = Detector(loadReference(), loadTemplates())
        val b = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        w = b.width(); h = b.height()
        val dpi = resources.displayMetrics.densityDpi

        thread = HandlerThread("wb-capture").also { it.start() }
        val handler = Handler(thread!!.looper)

        val mpm = getSystemService(MediaProjectionManager::class.java)
        projection = mpm.getMediaProjection(code, data).also { p ->
            p.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { shutdown("screen capture stopped by system") }
            }, handler)
        }
        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        display = projection!!.createVirtualDisplay(
            "whackbot", w, h, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, handler,
        )
        reader!!.setOnImageAvailableListener({ r -> onFrame(r) }, handler)

        Control.autoEnabled = intent.getBooleanExtra("auto", false) && TapService.instance != null
        Control.running = true
        overlay = Overlay(this, onToggle = ::toggleMode, onStop = { shutdown("stopped from overlay") }).also { it.show() }
        Log.i(TAG, "capture started ${w}x$h")
        return START_NOT_STICKY
    }

    private fun toggleMode() {
        if (Control.autoEnabled) { Control.autoEnabled = false; return }
        if (TapService.instance == null) { overlay?.set("Enable the accessibility service first", false); return }
        planner.reset()
        Control.autoEnabled = true
    }

    private fun onFrame(r: ImageReader) {
        val img = r.acquireLatestImage() ?: return
        try {
            val t0 = SystemClock.elapsedRealtime()
            if (img.width != w || img.height != h) { shutdown("screen size changed"); return }
            val plane = img.planes[0]
            val buf = plane.buffer
            val rs = plane.rowStride
            val src = object : PixelSource {
                override val width = img.width
                override val height = img.height
                override fun rgb(x: Int, y: Int): Int {
                    val i = y * rs + x * 4
                    return ((buf.get(i).toInt() and 255) shl 16) or
                        ((buf.get(i + 1).toInt() and 255) shl 8) or (buf.get(i + 2).toInt() and 255)
                }
            }
            if (lastFrameMs != 0L) fps = 0.9f * fps + 0.1f * (1000f / (t0 - lastFrameMs).coerceAtLeast(1))
            lastFrameMs = t0

            val sceneOk = detector.sceneOk(src)
            var status = "ok"
            if (!sceneOk) {
                badFrames++
                status = "NOT ON GAME SCREEN - taps blocked"
                if (badFrames > 90 && Control.autoEnabled) { Control.autoEnabled = false; Log.w(TAG, "auto off: game screen lost") }
            } else {
                badFrames = 0
                val evals = detector.evaluate(src)
                val cmd = planner.plan(evals, t0)
                if (cmd != null) {
                    if (Control.autoEnabled) {
                        val svc = TapService.instance
                        if (svc == null) { Control.autoEnabled = false; status = "accessibility service lost" }
                        else {
                            svc.tap(cmd.refX * w / Ref.W.toFloat(), cmd.refY * h / Ref.H.toFloat())
                            taps++
                            latencyMs = 0.9f * latencyMs + 0.1f * (SystemClock.elapsedRealtime() - t0)
                            Log.i(TAG, "TAP hole=${cmd.holeId} ${cmd.label} score=${"%.2f".format(cmd.score)}")
                        }
                    } else {
                        wouldTap++
                        Log.i(TAG, "PREVIEW would tap hole=${cmd.holeId} ${cmd.label} score=${"%.2f".format(cmd.score)}")
                    }
                }
                evals.filter { it.state == State.AVOID }.forEach { Log.d(TAG, "avoid hole=${it.hole.id} ${it.best}") }
            }
            if (t0 - lastUiMs > 250) {
                lastUiMs = t0
                val mode = if (Control.autoEnabled) "AUTO TAP ON" else "PREVIEW"
                overlay?.set("$mode | ${"%.0f".format(fps)}fps | taps $taps | would-tap $wouldTap | ${"%.0f".format(latencyMs)}ms | $status", Control.autoEnabled)
            }
        } finally { img.close() }
    }

    private fun shutdown(reason: String) {
        Log.i(TAG, "shutdown: $reason")
        Control.autoEnabled = false
        Control.running = false
        overlay?.hide(); overlay = null
        runCatching { display?.release() }; display = null
        runCatching { reader?.close() }; reader = null
        runCatching { projection?.stop() }; projection = null
        thread?.quitSafely(); thread = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { if (Control.running) shutdown("destroyed"); super.onDestroy() }

    private fun startForegroundNow() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("wb", "WhackBot", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "wb")
            .setContentTitle("WhackBot is capturing the screen")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    private fun loadReference(): PixelSource {
        val bmp = assets.open("empty_reference.jpg").use { BitmapFactory.decodeStream(it) }
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return IntPixels(bmp.width, bmp.height, px)
    }

    private fun loadTemplates(): TemplateSet {
        val items = ArrayList<Pair<Label, FloatArray>>()
        for (name in assets.list("templates").orEmpty()) {
            val label = Label.valueOf(name.substringBefore("_").uppercase())
            val bmp = assets.open("templates/$name").use { BitmapFactory.decodeStream(it) }
            val px = IntArray(D * D)
            bmp.getPixels(px, 0, D, 0, 0, D, D)
            val arr = FloatArray(D * D * 3)
            for (i in px.indices) {
                arr[i * 3] = ((px[i] shr 16) and 255).toFloat()
                arr[i * 3 + 1] = ((px[i] shr 8) and 255).toFloat()
                arr[i * 3 + 2] = (px[i] and 255).toFloat()
            }
            items.add(label to arr)
        }
        return TemplateSet(items)
    }
}
