package com.edurip.bujhechhi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Indigo = Color(0xFF4338CA)
val Indigo2 = Color(0xFF6366F1)
val Green = Color(0xFF16A34A)
val Amber = Color(0xFFD97706)
val Red = Color(0xFFDC2626)
val Gray = Color(0xFF9CA3AF)

@Composable
fun BjTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFF8B8CFF),
            onPrimary = Color(0xFF14143A),
            background = Color(0xFF0F1020),
            onBackground = Color(0xFFEEF0FF),
            surface = Color(0xFF191A30),
            onSurface = Color(0xFFEEF0FF),
            surfaceVariant = Color(0xFF24265A),
            onSurfaceVariant = Color(0xFFB4B8DA),
            outline = Color(0xFF3A3D66),
        )
    } else {
        lightColorScheme(
            primary = Indigo,
            onPrimary = Color.White,
            background = Color(0xFFF3F4FB),
            onBackground = Color(0xFF1B1B2F),
            surface = Color.White,
            onSurface = Color(0xFF1B1B2F),
            surfaceVariant = Color(0xFFECEAFD),
            onSurfaceVariant = Color(0xFF5B5F7A),
            outline = Color(0xFFD9DBEC),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

// ---- বাংলা সংখ্যা ও সময় ----

private const val BN_DIGITS = "০১২৩৪৫৬৭৮৯"

fun bn(n: Int): String = bn(n.toString())
fun bn(n: Long): String = bn(n.toString())
fun bn(s: String): String {
    val sb = StringBuilder(s.length)
    for (c in s) sb.append(if (c in '0'..'9') BN_DIGITS[c - '0'] else c)
    return sb.toString()
}

fun fmtDate(ms: Long): String =
    SimpleDateFormat("d MMM yyyy, h:mm a", Locale("bn", "BD")).format(Date(ms))

fun fmtClock(ms: Long): String {
    val s = ((ms + 999) / 1000).coerceAtLeast(0)
    return bn("${s / 60}:${(s % 60).toString().padStart(2, '0')}")
}

fun fmtDuration(ms: Long): String {
    val m = (ms / 60000).coerceAtLeast(0)
    return if (m < 60) "${bn(m)} মিনিট" else "${bn(m / 60)} ঘণ্টা ${bn(m % 60)} মিনিট"
}

fun statusColor(st: String?): Color = when (st) {
    "G" -> Green
    "Y" -> Amber
    "R" -> Red
    else -> Gray
}

fun statusLabel(st: String?): String = when (st) {
    "G" -> "বুঝেছে"
    "Y" -> "একটু বুঝেছে"
    "R" -> "বুঝেনি"
    else -> "উত্তর দেয়নি"
}

// ---- ছোট ছোট উপাদান ----

@Composable
fun BjCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val m = if (onClick != null) modifier.clip(shape).clickable(onClick = onClick) else modifier
    Card(
        modifier = m,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) cs.primary else cs.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            color = if (selected) cs.onPrimary else cs.onSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, size: Int = 14) {
    Text(text, modifier = modifier, fontSize = size.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** সবুজ/হলুদ/লাল/ধূসর অনুপাতের লম্বা বার। */
@Composable
fun StackedBar(g: Int, y: Int, r: Int, none: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (g + y + r + none > 0) {
            if (g > 0) Box(Modifier.weight(g.toFloat()).fillMaxHeight().background(Green))
            if (y > 0) Box(Modifier.weight(y.toFloat()).fillMaxHeight().background(Amber))
            if (r > 0) Box(Modifier.weight(r.toFloat()).fillMaxHeight().background(Red))
            if (none > 0) Box(Modifier.weight(none.toFloat()).fillMaxHeight().background(Gray.copy(alpha = 0.45f)))
        }
    }
}

/** একটি অপশনের ভোট-বার (শিক্ষকের স্ক্রিনে)। */
@Composable
fun OptionBar(label: String, text: String, count: Int, total: Int, correct: Boolean?) {
    val frac = if (total > 0) count.toFloat() / total else 0f
    val tint = when (correct) {
        true -> Green
        false -> MaterialTheme.colorScheme.primary
        null -> MaterialTheme.colorScheme.primary
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (frac > 0f) {
            Box(Modifier.fillMaxWidth(frac).height(44.dp).background(tint.copy(alpha = 0.28f)))
        } else {
            Box(Modifier.fillMaxWidth().height(44.dp))
        }
        Row(
            Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$label  $text" + if (correct == true) "  ✓" else "",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                fontWeight = if (correct == true) FontWeight.Bold else FontWeight.Medium,
                color = if (correct == true) Green else MaterialTheme.colorScheme.onSurface,
            )
            Text(bn(count), fontWeight = FontWeight.Bold)
        }
    }
}

val OPTION_LETTERS = listOf("ক", "খ", "গ", "ঘ", "ঙ", "চ")

@Composable
fun QrCode(text: String, qrSize: Dp, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        try {
            QRCodeWriter().encode(
                text,
                BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
            )
        } catch (e: Exception) {
            null
        }
    }
    Box(
        modifier
            .size(qrSize)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(12.dp),
    ) {
        if (matrix != null) {
            Canvas(Modifier.fillMaxSize()) {
                val n = matrix.width
                val cell = this.size.minDimension / n
                for (y in 0 until n) {
                    for (x in 0 until n) {
                        if (matrix.get(x, y)) {
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset(x * cell, y * cell),
                                size = Size(cell + 0.6f, cell + 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}
