package com.edurip.bujhechhi.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.edurip.bujhechhi.MainActivity
import com.edurip.bujhechhi.R
import java.io.ByteArrayOutputStream

/**
 * শিক্ষকের ফোনের পর্দা ধরে ছোট JPEG ছবি বানিয়ে [ScreenHub]-এ রাখে।
 * পর্দা না বদলালে Android নতুন ছবি দেয় না, তাই স্থির পর্দায় কোনো ডেটাই যায় না।
 */
class ScreenCastService : Service() {
    private class Quality(val longSide: Int, val jpeg: Int, val fps: Int)

    private var projection: MediaProjection? = null
    private var vd: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var capW = 0
    private var capH = 0
    private var dpi = 0
    private var pending: Bitmap? = null
    private var lastSent = 0L
    private var held: ByteArray? = null
    private var cb: ComponentCallbacks? = null
    private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        live = this
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "স্ক্রিন শেয়ার", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                ClassEngine.castStop()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                ClassEngine.castPause(!ClassEngine.castPaused)
                return START_NOT_STICKY
            }
        }
        if (projection != null) return START_NOT_STICKY
        val rc = intent?.getIntExtra("rc", 0) ?: 0
        @Suppress("DEPRECATION")
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("data", Intent::class.java) else intent?.getParcelableExtra("data")
        // Android ১০+ এ প্রজেকশন নেওয়ার আগেই foreground সার্ভিস চালু থাকতে হয়
        val n = notification(false)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, n)
        }
        if (data == null) {
            ClassEngine.castStop()
            return START_NOT_STICKY
        }
        try {
            begin(rc, data)
        } catch (e: Exception) {
            ClassEngine.castStop()
        }
        return START_NOT_STICKY
    }

    private fun begin(rc: Int, data: Intent) {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val t = HandlerThread("bj-cast").also { it.start() }
        thread = t
        val h = Handler(t.looper)
        handler = h
        val proj = mpm.getMediaProjection(rc, data)
        projection = proj
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                if (!stopping) ClassEngine.castStop()
            }
        }, h)
        measure()
        val r = newReader()
        reader = r
        vd = proj.createVirtualDisplay(
            "bj-cast", capW, capH, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, h,
        )
        val c = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                handler?.post { rotate() }
            }

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() {}
        }
        cb = c
        registerComponentCallbacks(c)
    }

    /** পর্দার আসল মাপ; ধরার মাপ লম্বা দিকে সর্বোচ্চ ১৫০০ পিক্সেল। */
    private fun measure() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        var w: Int
        var h: Int
        if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            w = b.width(); h = b.height()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            w = dm.widthPixels; h = dm.heightPixels
        }
        dpi = resources.displayMetrics.densityDpi
        val s = minOf(1f, 1500f / maxOf(w, h))
        capW = (w * s).toInt().coerceAtLeast(2) and 1.inv()
        capH = (h * s).toInt().coerceAtLeast(2) and 1.inv()
    }

    private fun newReader(): ImageReader {
        val r = ImageReader.newInstance(capW, capH, PixelFormat.RGBA_8888, 2)
        r.setOnImageAvailableListener({ rd ->
            var img: Image? = null
            try {
                img = rd.acquireLatestImage()
                if (img != null) onFrame(img)
            } catch (e: Exception) {
                // একটি ছবি বাদ গেলে পরেরটা আসবে
            } finally {
                try { img?.close() } catch (e: Exception) { /* উপেক্ষা */ }
            }
        }, handler)
        return r
    }

    /** ফোন ঘোরালে নতুন মাপে আবার সাজানো। */
    private fun rotate() {
        if (stopping) return
        try {
            measure()
            val old = reader
            val r = newReader()
            reader = r
            vd?.resize(capW, capH, dpi)
            vd?.surface = r.surface
            old?.close()
        } catch (e: Exception) {
            // সাজানো না গেলে আগের মতোই চলবে
        }
    }

    private fun quality(): Quality = when (ClassEngine.core.castQualityNow()) {
        0 -> Quality(800, 50, 4)
        2 -> Quality(1500, 72, 8)
        else -> Quality(1100, 60, 6)
    }

    private fun onFrame(img: Image) {
        val plane = img.planes[0]
        val ps = plane.pixelStride
        val rowPad = plane.rowStride - ps * img.width
        var bmp = Bitmap.createBitmap(img.width + rowPad / ps, img.height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(plane.buffer)
        if (rowPad != 0) {
            val cropped = Bitmap.createBitmap(bmp, 0, 0, img.width, img.height)
            bmp.recycle()
            bmp = cropped
        }
        pending?.recycle()
        pending = bmp
        val h = handler ?: return
        h.removeCallbacks(flush)
        val wait = 1000L / quality().fps - (SystemClock.elapsedRealtime() - lastSent)
        // বেশি তাড়াতাড়ি এলে শেষ ছবিটা ধরে রেখে সময় হলে পাঠাই, যাতে শেষ অবস্থা হারিয়ে না যায়
        if (wait <= 0) flushNow() else h.postDelayed(flush, wait)
    }

    private val flush = Runnable { flushNow() }

    private fun flushNow() {
        val src = pending ?: return
        pending = null
        val q = quality()
        val longest = maxOf(src.width, src.height)
        val bmp = if (longest > q.longSide) {
            val s = q.longSide.toFloat() / longest
            Bitmap.createScaledBitmap(src, (src.width * s).toInt().coerceAtLeast(1), (src.height * s).toInt().coerceAtLeast(1), true)
        } else src
        val bos = ByteArrayOutputStream(64 * 1024)
        bmp.compress(Bitmap.CompressFormat.JPEG, q.jpeg, bos)
        if (bmp !== src) src.recycle()
        bmp.recycle()
        val bytes = bos.toByteArray()
        lastSent = SystemClock.elapsedRealtime()
        if (ClassEngine.castPaused) held = bytes else ClassEngine.screenHub.publish(bytes)
    }

    private fun resumed() {
        // বিরতির সময় যে শেষ ছবিটা জমা ছিল সেটা এখন দেখাই
        val b = held ?: return
        held = null
        ClassEngine.screenHub.publish(b)
    }

    private fun notification(paused: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun act(action: String, req: Int) = PendingIntent.getService(
            this, req, Intent(this, ScreenCastService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (paused) "⏸ স্ক্রিন শেয়ার বিরতিতে" else "📺 আপনার স্ক্রিন শিক্ষার্থীদের দেখানো হচ্ছে")
            .setContentText(if (paused) "শিক্ষার্থীরা এখন কিছু দেখছে না" else "পর্দায় যা হচ্ছে সবাই দেখছে")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, if (paused) "▶ চালু" else "⏸ বিরতি", act(ACTION_TOGGLE, 1))
            .addAction(0, "⏹ বন্ধ", act(ACTION_STOP, 2))
            .build()
    }

    private fun refreshNotification(paused: Boolean) {
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, notification(paused))
        } catch (e: Exception) {
            // উপেক্ষা
        }
    }

    override fun onDestroy() {
        stopping = true
        live = null
        try { cb?.let { unregisterComponentCallbacks(it) } } catch (e: Exception) { /* উপেক্ষা */ }
        try { handler?.removeCallbacksAndMessages(null) } catch (e: Exception) { /* উপেক্ষা */ }
        try { vd?.release() } catch (e: Exception) { /* উপেক্ষা */ }
        try { reader?.close() } catch (e: Exception) { /* উপেক্ষা */ }
        try { projection?.stop() } catch (e: Exception) { /* উপেক্ষা */ }
        try { thread?.quitSafely() } catch (e: Exception) { /* উপেক্ষা */ }
        pending?.recycle(); pending = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "cast_running"
        private const val NOTIF_ID = 2
        const val ACTION_TOGGLE = "com.edurip.bujhechhi.CAST_TOGGLE"
        const val ACTION_STOP = "com.edurip.bujhechhi.CAST_STOP"

        @Volatile private var live: ScreenCastService? = null

        /** বিরতি/চালুর সময় নোটিফিকেশন বদলায় ও জমানো ছবি ছাড়ে। */
        fun onPauseChanged(paused: Boolean) {
            val s = live ?: return
            s.refreshNotification(paused)
            if (!paused) s.handler?.post { s.resumed() }
        }
    }
}
