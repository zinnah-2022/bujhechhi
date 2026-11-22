package com.edurip.bujhechhi.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.edurip.bujhechhi.core.LiveShare
import com.edurip.bujhechhi.server.ClassEngine

private const val MAX_ZOOM = 6f

/**
 * শিক্ষকের প্রিভিউ: দুই আঙুলে জুম, জুম করা অবস্থায় এক আঙুলে সরানো, দুইবার ট্যাপে জুম/রিসেট।
 * দাগ-মোড চালু থাকলে এক আঙুলে দাগ, দুই আঙুলে জুম। দাগের জায়গা ছবির মাপের ভগ্নাংশে (০..১) যায়,
 * তাই জুম থাকলেও শিক্ষার্থীর পর্দায় ঠিক জায়গাতেই পড়ে।
 */
@Composable
fun SharePreview(
    bmp: ImageBitmap,
    share: LiveShare,
    drawing: Boolean,
    color: String,
    modifier: Modifier = Modifier,
    bordered: Boolean = true,
    onFull: (() -> Unit)? = null,
) {
    var scale by remember(share.id, share.page) { mutableFloatStateOf(1f) }
    var off by remember(share.id, share.page) { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val shape = RoundedCornerShape(if (bordered) 12.dp else 0.dp)

    fun clamp(s: Float, o: Offset): Offset {
        val w = box.width.toFloat()
        val h = box.height.toFloat()
        return Offset(o.x.coerceIn(w * (1f - s), 0f), o.y.coerceIn(h * (1f - s), 0f))
    }

    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { box = it }
                .clip(shape)
                .then(if (bordered) Modifier.border(2.dp, if (drawing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape) else Modifier)
                .background(Color.White)
                .pointerInput(drawing) {
                    if (drawing) return@pointerInput
                    detectTapGestures(onDoubleTap = { p ->
                        if (scale > 1f) {
                            scale = 1f; off = Offset.Zero
                        } else {
                            val ns = 2.5f
                            scale = ns
                            off = clamp(ns, p - (p - off) * (ns / 1f))
                        }
                    })
                }
                .pointerInput(drawing, color, share.id, share.page) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)

                        // জুম/সরানো এক ধাপ
                        fun transform(ev: androidx.compose.ui.input.pointer.PointerEvent) {
                            val zoom = ev.calculateZoom()
                            val pan = ev.calculatePan()
                            val c = ev.calculateCentroid(useCurrent = false)
                            if (zoom != 1f || pan != Offset.Zero) {
                                val ns = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                                val r = ns / scale
                                scale = ns
                                off = clamp(ns, (c - (c - off) * r) + pan)
                                ev.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }

                        if (!drawing) {
                            do {
                                val ev = awaitPointerEvent()
                                if (ev.changes.any { it.isConsumed }) break
                                // এক আঙুলে স্ক্রল (পেজ) ঠিক রাখতে: জুম না থাকলে শুধু দুই আঙুলে কাজ
                                if (scale > 1f || ev.changes.size >= 2) transform(ev)
                            } while (ev.changes.any { it.pressed })
                            return@awaitEachGesture
                        }

                        // দাগ: প্রথম নড়াচড়ায় দাগ শুরু (দুই আঙুল নামলে আর শুরু হয় না); না নড়ে ছাড়লে একটি বিন্দু
                        down.consume()
                        fun nx(o: Offset) = (((o.x - off.x) / scale) / box.width.coerceAtLeast(1)).toDouble()
                        fun ny(o: Offset) = (((o.y - off.y) / scale) / box.height.coerceAtLeast(1)).toDouble()
                        var k = -1
                        var multi = false
                        val slop = viewConfiguration.touchSlop
                        val pending = ArrayList<Double>()
                        var lastSend = System.currentTimeMillis()
                        while (true) {
                            val ev = awaitPointerEvent()
                            if (ev.changes.size >= 2) {
                                multi = true
                                transform(ev)
                                if (ev.changes.none { it.pressed }) break
                                continue
                            }
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!multi && ch.pressed) {
                                val moved = (ch.position - down.position).getDistance() > slop
                                if (k < 0 && moved) k = ClassEngine.core.shareDrawStart(color, listOf(nx(down.position), ny(down.position)))
                                if (k >= 0) {
                                    pending.add(nx(ch.position)); pending.add(ny(ch.position))
                                    val now = System.currentTimeMillis()
                                    if (now - lastSend >= 80) {
                                        ClassEngine.core.shareDrawMore(k, ArrayList(pending))
                                        pending.clear(); lastSend = now
                                    }
                                }
                            }
                            ch.consume()
                            if (!ch.pressed) {
                                if (k < 0 && !multi && ch.changedToUp()) ClassEngine.core.shareDrawStart(color, listOf(nx(down.position), ny(down.position)))
                                break
                            }
                        }
                        if (k >= 0 && pending.isNotEmpty()) ClassEngine.core.shareDrawMore(k, ArrayList(pending))
                    }
                },
        ) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = off.x; translationY = off.y
                    transformOrigin = TransformOrigin(0f, 0f)
                },
            ) {
                Image(bmp, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize()) {
                    val sw = size.width * 0.006f
                    share.strokes.forEach { s ->
                        val c = try { Color(AndroidColor.parseColor(s.color)) } catch (e: Exception) { Color.Red }
                        val p = s.pts
                        if (p.size == 2) {
                            drawCircle(c, radius = sw / 2f, center = Offset((p[0] * size.width).toFloat(), (p[1] * size.height).toFloat()))
                        } else if (p.size >= 4) {
                            val path = androidx.compose.ui.graphics.Path()
                            path.moveTo((p[0] * size.width).toFloat(), (p[1] * size.height).toFloat())
                            var i = 2
                            while (i + 1 < p.size) {
                                path.lineTo((p[i] * size.width).toFloat(), (p[i + 1] * size.height).toFloat())
                                i += 2
                            }
                            drawPath(path, c, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
            }
        }
        if (onFull != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xAA000000))
                    .clickable { onFull() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) { Text("⛶ পূর্ণ পর্দা", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
        if (scale > 1.05f) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xAA000000))
                    .clickable { scale = 1f; off = Offset.Zero }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) { Text("🔍 ${String.format("%.1f", scale)}× · রিসেট", color = Color.White, fontSize = 12.sp) }
        }
    }
}

/** পুরো পর্দায় শেয়ার করা ছবি/PDF: জুম, পাতা বদল, দাগ ও দেখানো/লুকানোর নিয়ন্ত্রণসহ। */
@Composable
fun ShareFullscreen(
    share: LiveShare,
    bmp: ImageBitmap,
    drawing: Boolean,
    onDrawing: (Boolean) -> Unit,
    color: String,
    onColor: (String) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { w ->
                WindowCompat.setDecorFitsSystemWindows(w, false)
                WindowInsetsControllerCompat(w, view).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val ratio = bmp.width.toFloat() / bmp.height.toFloat()
                val fitW = if (maxWidth / maxHeight > ratio) maxHeight * ratio else maxWidth
                val fitH = if (maxWidth / maxHeight > ratio) maxHeight else maxWidth / ratio
                SharePreview(bmp, share, drawing, color, Modifier.width(fitW).size(fitW, fitH), bordered = false)
            }
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color(0x99000000))
                    .statusBarsPadding()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TextButton(onClick = onClose) { Text("✕ বন্ধ", color = Color.White, fontWeight = FontWeight.Bold) }
                if (share.n > 1) {
                    TextButton(onClick = { ClassEngine.core.shareGoto(share.page - 1) }, enabled = share.page > 0) { Text("◀", color = Color.White) }
                    Text("${bn(share.page + 1)}/${bn(share.n)}", color = Color.White, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { ClassEngine.core.shareGoto(share.page + 1) }, enabled = share.page < share.n - 1) { Text("▶", color = Color.White) }
                }
                TextButton(onClick = { onDrawing(!drawing) }) {
                    Text(if (drawing) "✏️ দাগ চালু" else "✏️ দাগ", color = if (drawing) Color(0xFFFBBF24) else Color.White, fontWeight = FontWeight.Bold)
                }
                if (drawing) {
                    PEN_COLORS.forEach { c ->
                        val sel = c == color
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (sel) 30.dp else 24.dp)
                                .clip(CircleShape)
                                .background(Color(AndroidColor.parseColor(c)))
                                .border(if (sel) 3.dp else 1.dp, Color.White, CircleShape)
                                .clickable { onColor(c) },
                        )
                    }
                    TextButton(onClick = { ClassEngine.core.shareUndo() }) { Text("↩", color = Color.White) }
                    TextButton(onClick = { ClassEngine.core.shareClearDraw() }) { Text("🧹", color = Color.White) }
                }
                TextButton(onClick = { ClassEngine.core.shareShow(!share.visible) }) {
                    Text(if (share.visible) "🙈 লুকাও" else "👁 দেখাও", color = if (share.visible) Color.White else Color(0xFF34D399), fontWeight = FontWeight.Bold)
                }
            }
            Text(
                "দুই আঙুলে জুম · দুইবার ট্যাপে বড়/ছোট",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            )
        }
    }
}
