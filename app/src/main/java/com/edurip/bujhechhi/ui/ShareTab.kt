package com.edurip.bujhechhi.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.edurip.bujhechhi.core.LiveShare
import com.edurip.bujhechhi.server.ClassEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

internal val PEN_COLORS = listOf("#DC2626", "#2563EB", "#16A34A", "#111827")
private const val MAX_PAGES = 20

/** "প্রশ্ন শেয়ার" ট্যাব: ছবি/PDF শেয়ার, পাতা, দেখান/লুকান, সেভের অনুমতি ও লাইভ দাগ। */
@Composable
fun ShareTab(share: LiveShare?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_LONG).show()

    fun publish(make: suspend () -> List<ByteArray>) {
        if (busy) return
        busy = true
        scope.launch {
            val id = withContext(Dispatchers.IO) {
                try {
                    val pages = make()
                    if (pages.isEmpty()) -1L else ClassEngine.shareCreate(pages)
                } catch (e: Exception) {
                    -1L
                }
            }
            busy = false
            if (id <= 0L) toast("ছবি/PDF পড়া যায়নি, আবার চেষ্টা করুন") else toast("তৈরি হয়েছে। “সবাইকে দেখাও” চাপলে শিক্ষার্থীরা দেখবে।")
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) publish { uris.take(MAX_PAGES).mapNotNull { Pages.fromImage(ctx, it) } }
    }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) publish { Pages.fromPdf(ctx, uri, MAX_PAGES) }
    }
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = camUri
        if (ok && u != null) publish { listOfNotNull(Pages.fromImage(ctx, u)) }
    }
    fun openCamera() {
        try {
            val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
            val f = File(dir, "shot-${System.currentTimeMillis()}.jpg")
            val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            camUri = u
            camera.launch(u)
        } catch (e: Exception) {
            toast("ক্যামেরা খোলা যায়নি")
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (share == null) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF3949AB), Color(0xFF5C6BC0))))
                    .padding(18.dp),
            ) {
                Text("📄 প্রশ্ন শেয়ার", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                Text(
                    "প্রশ্নের ছবি তুলুন বা PDF দিন। সবাইকে দেখালে সবার ফোনে খুলবে, জুম করা যাবে।",
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PickTile("📷", "ক্যামেরা", "ছবি তুলুন", Color(0xFFE8F5E9), Color(0xFF2E7D32), !busy, Modifier.weight(1f)) { openCamera() }
                PickTile("🖼️", "গ্যালারি", "একাধিক ছবি", Color(0xFFE3F2FD), Color(0xFF1565C0), !busy, Modifier.weight(1f)) { gallery.launch("image/*") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PickTile("📑", "PDF", "সর্বোচ্চ ${bn(MAX_PAGES)} পাতা", Color(0xFFFFF3E0), Color(0xFFE65100), !busy, Modifier.weight(1f)) { pdf.launch("application/pdf") }
                Spacer(Modifier.weight(1f))
            }
            if (busy) Text("⏳ প্রস্তুত হচ্ছে…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        } else {
            ShareControls(share, busy, { openCamera() }, { gallery.launch("image/*") }, { pdf.launch("application/pdf") })
        }
    }
}

@Composable
private fun ShareControls(
    share: LiveShare,
    busy: Boolean,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onPdf: () -> Unit,
) {
    val ctx = LocalContext.current
    var drawing by rememberSaveable { mutableStateOf(false) }
    var color by rememberSaveable { mutableStateOf(PEN_COLORS[0]) }
    var confirmNew by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }

    val bmp = remember(share.id, share.page) {
        val f = File(ctx.filesDir, "share/${share.id}/${share.page}.jpg")
        try {
            BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    // অবস্থা
    BjCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (share.visible) "👁 এখন সবাই দেখছে" else "🙈 এখনও কেউ দেখছে না",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (share.visible) Green else MaterialTheme.colorScheme.onSurface,
                )
                Muted(if (share.n > 1) "মোট ${bn(share.n)} পাতা" else "১টি ছবি", size = 13)
            }
            if (share.visible) {
                OutlinedButton(onClick = { ClassEngine.core.shareShow(false) }) { Text("🙈 লুকাও") }
            } else {
                PrimaryButton("👁 সবাইকে দেখাও", { ClassEngine.core.shareShow(true) })
            }
        }
    }

    // পাতা
    if (share.n > 1) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { ClassEngine.core.shareGoto(share.page - 1) }, enabled = share.page > 0) { Text("◀ আগের") }
            Text(
                "পাতা ${bn(share.page + 1)} / ${bn(share.n)}",
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            OutlinedButton(onClick = { ClassEngine.core.shareGoto(share.page + 1) }, enabled = share.page < share.n - 1) { Text("পরের ▶") }
        }
    }

    // ছবি + দাগ
    if (bmp == null) {
        BjCard(Modifier.fillMaxWidth()) { Muted("ছবি দেখানো যাচ্ছে না। নতুন করে শেয়ার করুন।") }
    } else {
        val ratio = bmp.width.toFloat() / bmp.height.toFloat()
        SharePreview(bmp, share, drawing, color, Modifier.fillMaxWidth().aspectRatio(ratio), onFull = { full = true })
        Muted("দুই আঙুলে জুম · দুইবার ট্যাপে বড়/ছোট · ⛶ চাপলে পুরো পর্দা", size = 12)
        if (full) ShareFullscreen(share, bmp, drawing, { drawing = it }, color, { color = it }, { full = false })
    }

    // দাগ দেওয়ার যন্ত্র
    BjCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✏️ লাইভ দাগ", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Switch(checked = drawing, onCheckedChange = { drawing = it })
        }
        Muted(
            if (drawing) "এক আঙুলে দাগ, দুই আঙুলে জুম। সবার ফোনে লাইভ দেখা যাবে।" else "চালু করলে ছবির ওপর আঙুল দিয়ে দাগ/বৃত্ত দিতে পারবেন (তখন এই পাতা স্ক্রল হবে না)।",
            size = 13,
        )
        if (drawing) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PEN_COLORS.forEach { c ->
                    val sel = c == color
                    Box(
                        Modifier
                            .size(if (sel) 38.dp else 32.dp)
                            .clip(CircleShape)
                            .background(Color(AndroidColor.parseColor(c)))
                            .border(if (sel) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { color = c },
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { ClassEngine.core.shareUndo() }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("↩ আগেরটা") }
                TextButton(onClick = { ClassEngine.core.shareClearDraw() }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("🧹 সব", color = Red) }
            }
        }
    }

    // সেভের অনুমতি
    BjCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("⬇ শিক্ষার্থী ছবি সেভ করতে পারবে", fontWeight = FontWeight.Bold)
                Muted("বন্ধ থাকলে তাদের পর্দায় সেভ বোতাম আসবে না।", size = 13)
            }
            Switch(checked = share.allowSave, onCheckedChange = { ClassEngine.core.shareAllowSave(it) })
        }
    }

    // নতুন / মুছুন
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onCamera, enabled = !busy) { Text("📷 নতুন ছবি") }
        OutlinedButton(onClick = onGallery, enabled = !busy) { Text("🖼️ গ্যালারি") }
        OutlinedButton(onClick = onPdf, enabled = !busy) { Text("📑 PDF") }
        TextButton(onClick = { confirmNew = true }) { Text("🗑 শেয়ার মুছুন", color = Red) }
    }
    Muted("নতুন ছবি/PDF দিলে আগেরটা বদলে যাবে।", size = 12)

    if (confirmNew) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmNew = false },
            title = { Text("শেয়ার মুছবেন?") },
            text = { Text("শিক্ষার্থীদের পর্দা থেকেও সরে যাবে।") },
            confirmButton = { TextButton(onClick = { confirmNew = false; ClassEngine.shareRemove() }) { Text("মুছুন", color = Red) } },
            dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("থাক") } },
        )
    }
}

/** ছবি/PDF থেকে শেয়ারের JPEG পাতা বানানো (ব্যাকগ্রাউন্ড থ্রেডে ডাকুন)। */
private object Pages {
    private const val MAX_SIDE = 1600

    private fun jpeg(b: Bitmap): ByteArray {
        val bos = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, 82, bos)
        return bos.toByteArray()
    }

    fun fromImage(ctx: Context, uri: Uri): ByteArray? = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        val big = maxOf(opts.outWidth, opts.outHeight)
        while (big / sample > MAX_SIDE * 2) sample *= 2
        var bmp = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        if (bmp == null) {
            null
        } else {
            val rot = try {
                ctx.contentResolver.openInputStream(uri)?.use {
                    when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (e: Exception) {
                0f
            }
            val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height))
            if (rot != 0f || scale < 1f) {
                val m = Matrix()
                if (scale < 1f) m.postScale(scale, scale)
                if (rot != 0f) m.postRotate(rot)
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            jpeg(bmp)
        }
    } catch (e: Exception) {
        null
    }

    fun fromPdf(ctx: Context, uri: Uri, max: Int): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        val fd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: return out
        fd.use {
            val r = PdfRenderer(it)
            try {
                val n = minOf(r.pageCount, max)
                for (i in 0 until n) {
                    val page = r.openPage(i)
                    try {
                        val scale = MAX_SIDE.toFloat() / maxOf(page.width, page.height)
                        val w = (page.width * scale).toInt().coerceAtLeast(1)
                        val h = (page.height * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        AndroidCanvas(bmp).drawColor(AndroidColor.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        out.add(jpeg(bmp))
                        bmp.recycle()
                    } finally {
                        page.close()
                    }
                }
            } finally {
                r.close()
            }
        }
        return out
    }
}

@Composable
private fun PickTile(icon: String, title: String, sub: String, bg: Color, fg: Color, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(120.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Color.White), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(icon, fontSize = 26.sp)
        }
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = fg, modifier = Modifier.padding(top = 8.dp))
        Text(sub, fontSize = 11.sp, color = fg.copy(alpha = 0.75f))
    }
}
