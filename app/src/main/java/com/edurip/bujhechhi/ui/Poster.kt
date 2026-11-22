package com.edurip.bujhechhi.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream

/** দেয়ালে লাগানোর জন্য A4 অনুপাতের (১২৪০×১৭৫৪) পোস্টার ছবি: স্লোগান, QR ও নির্দেশনা। */
object Poster {
    private const val W = 1240
    private const val H = 1754

    fun render(url: String): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)

        val indigo = Color.parseColor("#4338CA")
        val ink = Color.parseColor("#1B1B2F")
        val soft = Color.parseColor("#5B5F7A")

        // ---- ওপরের ব্যান্ড: নাম ও স্লোগান
        val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = indigo }
        c.drawRect(0f, 0f, W.toFloat(), 320f, band)
        text(c, "বুঝেছি?", 120f, 8f, W, Color.WHITE, bold = true, center = true, top = 40f)
        text(c, "বুঝতে না পারলে লজ্জা নয় — এক ট্যাপেই জানিয়ে দিন!", 50f, 0f, W - 120, Color.WHITE, bold = false, center = true, top = 200f, left = 60f)

        // ---- শিরোনাম
        var y = 350f
        y += text(c, "ক্লাসে যুক্ত হতে QR স্ক্যান করুন", 56f, 0f, W - 120, ink, bold = true, center = true, top = y, left = 60f)

        // ---- QR
        val qr = 480
        val qx = (W - qr) / 2f
        y += 14f
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = indigo; style = Paint.Style.STROKE; strokeWidth = 10f }
        c.drawRoundRect(RectF(qx - 22, y - 22, qx + qr + 22, y + qr + 22), 36f, 36f, frame)
        drawQr(c, url, qx, y, qr.toFloat())
        y += qr + 40f

        // ---- হাতে লেখার ঠিকানা
        y += text(c, "QR না চললে ব্রাউজারে লিখুন:  $url", 32f, 0f, W - 120, soft, bold = false, center = true, top = y, left = 60f)
        y += 24f

        // ---- নির্দেশনা বক্স
        val boxTop = y
        val steps = listOf(
            "১.  ফোনের ওয়াইফাইতে শিক্ষকের হটস্পটে যুক্ত হোন (নাম ও পাসওয়ার্ড শিক্ষকের কাছে)",
            "২.  ফোনের ক্যামেরা দিয়ে এই QR স্ক্যান করুন",
            "৩.  শিক্ষকের বলা ৪ অঙ্কের কোড লিখুন",
            "৪.  নিজের নাম লিখে ক্লাসে যোগ দিন",
            "৫.  পড়া বুঝলে সবুজ, একটু বুঝলে হলুদ, না বুঝলে লাল বাটন চাপুন",
        )
        var sy = boxTop + 28f
        for (s in steps) sy += text(c, s, 34f, 0f, W - 200, ink, bold = false, center = false, top = sy, left = 100f) + 10f
        // বক্সটি হালকা দাগ দিয়ে ঘেরা
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C7C9E6"); style = Paint.Style.STROKE; strokeWidth = 4f }
        c.drawRoundRect(RectF(60f, boxTop, W - 60f, sy + 10f), 28f, 28f, outline)

        // ---- রঙের সংকেত
        // নিচের আশ্বাস-লেখা (H-70) থেকে নিরাপদ দূরত্বে রাখা হয়, যাতে লেখার ওপর না চড়ে
        val ly = minOf(sy + 44f, H - 190f)
        val dots = listOf(
            Triple("বুঝেছি", Color.parseColor("#16A34A"), 150f),
            Triple("একটু বুঝেছি", Color.parseColor("#D97706"), 480f),
            Triple("বুঝিনি", Color.parseColor("#DC2626"), 900f),
        )
        for ((label, col, x) in dots) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col }
            c.drawCircle(x, ly + 24f, 24f, p)
            text(c, label, 40f, 0f, 400, ink, bold = true, center = false, top = ly, left = x + 40f)
        }

        // ---- নিচে আশ্বাস
        text(c, "আপনার উত্তর শুধু শিক্ষক দেখতে পারবেন।", 36f, 0f, W - 120, soft, bold = false, center = true, top = (H - 70).toFloat(), left = 60f)
        return bmp
    }

    /** লেখা এঁকে তার উচ্চতা ফেরত দেয়। বড় লাইন নিজে থেকে ভেঙে নেয় (বাংলা শেপিংসহ)। */
    private fun text(
        c: Canvas,
        s: String,
        size: Float,
        spacing: Float,
        width: Int,
        color: Int,
        bold: Boolean,
        center: Boolean,
        top: Float,
        left: Float = 0f,
    ): Float {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val layout = StaticLayout.Builder.obtain(s, 0, s.length, tp, width)
            .setAlignment(if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(spacing, 1.1f)
            .build()
        c.save()
        c.translate(left, top)
        layout.draw(c)
        c.restore()
        return layout.height.toFloat()
    }

    private fun drawQr(c: Canvas, url: String, x: Float, y: Float, size: Float) {
        val m = QRCodeWriter().encode(
            url,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
        )
        val n = m.width
        val cell = size / n
        val p = Paint().apply { color = Color.BLACK; isAntiAlias = false }
        for (yy in 0 until n) for (xx in 0 until n) {
            if (m.get(xx, yy)) c.drawRect(x + xx * cell, y + yy * cell, x + (xx + 1) * cell + 0.5f, y + (yy + 1) * cell + 0.5f, p)
        }
    }

    /** গ্যালারিতে (Pictures/Bujhechhi) সেভ করে; সফল হলে ফাইলের বর্ণনা, ব্যর্থ হলে null। */
    fun save(ctx: Context, bmp: Bitmap): String? = try {
        val name = "bujhechhi-qr-poster.png"
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Bujhechhi")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
            if (uri == null) {
                null
            } else {
                ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                "গ্যালারি › Bujhechhi"
            }
        } else {
            val dir = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            if (dir == null) {
                null
            } else {
                val f = File(dir, name)
                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                f.absolutePath
            }
        }
    } catch (e: Exception) {
        null
    }
}
