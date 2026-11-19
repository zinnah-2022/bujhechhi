package com.edurip.bujhechhi.server

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import com.edurip.bujhechhi.core.ClassCore
import com.edurip.bujhechhi.core.LiveSnapshot
import com.edurip.bujhechhi.core.SessionInfo
import com.edurip.bujhechhi.data.Settings
import com.edurip.bujhechhi.data.SqliteStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

data class NetInfo(val addrs: List<Addr>, val port: Int)

/** অ্যাপজুড়ে একটাই ক্লাস-ইঞ্জিন: ডাটাবেজ + যুক্তি + সার্ভার + টাইমার। */
object ClassEngine {
    private var app: Context? = null
    lateinit var store: SqliteStore
        private set
    lateinit var core: ClassCore
        private set

    private var host: KtorHost? = null
    private val timer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "bj-tick").apply { isDaemon = true } }
    private var ticking: ScheduledFuture<*>? = null
    private var tickCount = 0

    private val _live = MutableStateFlow(LiveSnapshot.EMPTY)
    val live: StateFlow<LiveSnapshot> = _live

    private val _net = MutableStateFlow(NetInfo(emptyList(), 0))
    val net: StateFlow<NetInfo> = _net

    @Synchronized
    fun init(ctx: Context) {
        if (app != null) return
        val a = ctx.applicationContext
        app = a
        Settings.init(a)
        store = SqliteStore(a)
        core = ClassCore(store)
        core.onChange = { _live.value = core.snapshot() }
        core.onRedAlert = { vibrateAlert() }
        applySettings()
    }

    fun applySettings() {
        core.redThresholdPct = Settings.redThresholdPct
        core.redMinCount = Settings.redMinCount
    }

    /** ক্লাস শুরু। সার্ভার চালু না হলে ব্যর্থ Result। */
    @Synchronized
    fun startClass(ctx: Context, title: String): Result<SessionInfo> {
        init(ctx)
        val a = app ?: return Result.failure(IllegalStateException("init হয়নি"))
        if (core.isActive()) return Result.success(SessionInfo(0, "", "", 0))
        val h = KtorHost(a, core)
        val port = try {
            h.start()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        host = h
        core.transport = h
        applySettings()
        deleteShareFiles()
        val info = core.startSession(title)
        Settings.lastTitle = info.title
        try {
            ContextCompat.startForegroundService(a, Intent(a, ClassService::class.java))
        } catch (e: Exception) {
            // সার্ভিস না চললেও ক্লাস চলবে, তবে স্ক্রিন বন্ধ হলে থেমে যেতে পারে
        }
        refreshNet(port)
        startTicker()
        _live.value = core.snapshot()
        return Result.success(info)
    }

    /** ক্লাস শেষ; শেষ হওয়া সেশনের আইডি ফেরত দেয়। */
    @Synchronized
    fun endClass(): Long {
        val id = core.endSession()
        deleteShareFiles()
        stopTicker()
        val h = host
        host = null
        // "ক্লাস শেষ" বার্তা শিক্ষার্থীদের কাছে পৌঁছানোর সময় দিয়ে তারপর সার্ভার বন্ধ
        if (h != null) {
            Thread {
                try {
                    Thread.sleep(500)
                } catch (e: InterruptedException) {
                    // উপেক্ষা
                }
                h.stop()
            }.start()
        }
        core.transport = null
        app?.let { a ->
            try {
                a.stopService(Intent(a, ClassService::class.java))
            } catch (e: Exception) {
                // উপেক্ষা
            }
        }
        _live.value = core.snapshot()
        return id
    }

    // ---- প্রশ্ন শেয়ার: ছবির পাতাগুলো ফোনে রেখে শেয়ার তৈরি

    private fun shareRoot(): File? = app?.let { File(it.filesDir, "share") }

    private fun deleteShareFiles(except: Long = -1L) {
        val root = shareRoot() ?: return
        root.listFiles()?.forEach { d -> if (d.name != except.toString()) d.deleteRecursively() }
    }

    /** JPEG পাতাগুলো সেভ করে নতুন শেয়ার বানায় (ব্যাকগ্রাউন্ড থ্রেডে ডাকুন)। আইডি ফেরত দেয়, ব্যর্থ হলে ০। */
    fun shareCreate(pages: List<ByteArray>): Long {
        val root = shareRoot() ?: return 0L
        if (pages.isEmpty()) return 0L
        val id = core.shareSet(pages.size)
        if (id == 0L) return 0L
        return try {
            val dir = File(root, id.toString())
            dir.mkdirs()
            pages.forEachIndexed { i, b -> File(dir, "$i.jpg").writeBytes(b) }
            deleteShareFiles(except = id)
            id
        } catch (e: Exception) {
            core.shareRemove()
            0L
        }
    }

    fun shareRemove() {
        core.shareRemove()
        deleteShareFiles()
    }

    fun refreshNet(port: Int = host?.port ?: 0) {
        _net.value = NetInfo(Net.localAddresses(), port)
    }

    private fun startTicker() {
        stopTicker()
        tickCount = 0
        ticking = timer.scheduleAtFixedRate({
            try {
                core.tick()
                tickCount++
                if (tickCount % 3 == 0) refreshNet()
            } catch (e: Exception) {
                // টিক ব্যর্থ হলেও পরেরবার চলবে
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun stopTicker() {
        ticking?.cancel(false)
        ticking = null
    }

    @Suppress("DEPRECATION")
    private fun vibrateAlert() {
        val a = app ?: return
        if (!Settings.vibrateOnAlert) return
        try {
            val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
                (a.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                a.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (v == null || !v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 350, 150, 350), -1))
            } else {
                v.vibrate(longArrayOf(0, 350, 150, 350), -1)
            }
        } catch (e: Exception) {
            // কম্পন না হলেও সমস্যা নেই
        }
    }
}
