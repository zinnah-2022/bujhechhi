package com.edurip.bujhechhi.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edurip.bujhechhi.core.Report
import com.edurip.bujhechhi.core.ScoreRow
import com.edurip.bujhechhi.core.SessionReport
import com.edurip.bujhechhi.server.ClassEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface ReportState {
    data object Loading : ReportState
    data object Missing : ReportState
    data class Ready(val r: SessionReport) : ReportState
}

@Composable
fun ReportScreen(sessionId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val state by produceState<ReportState>(ReportState.Loading, sessionId) {
        value = withContext(Dispatchers.IO) {
            val raw = ClassEngine.store.sessionRaw(sessionId)
            if (raw == null) ReportState.Missing else ReportState.Ready(Report.build(raw))
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← ফিরুন") }
            Text("ক্লাসের রিপোর্ট", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 17.sp)
            val s = state
            if (s is ReportState.Ready) {
                TextButton(onClick = { shareReport(ctx, summaryText(s.r), whatsapp = true) }) {
                    Text("WhatsApp", color = Color(0xFF1FA855), fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { shareReport(ctx, summaryText(s.r), whatsapp = false) }) { Text("শেয়ার") }
            }
        }
        when (val s = state) {
            ReportState.Loading -> Muted("লোড হচ্ছে…", Modifier.padding(16.dp))
            ReportState.Missing -> Muted("রিপোর্ট পাওয়া যায়নি।", Modifier.padding(16.dp))
            is ReportState.Ready -> ReportBody(s.r)
        }
    }
}

@Composable
private fun ReportBody(r: SessionReport) {
    LazyColumn(
        Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BjCard {
                Text(r.meta.title, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Muted(fmtDate(r.meta.startedAt))
                val dur = if (r.meta.endedAt > r.meta.startedAt) "  ·  ${fmtDuration(r.meta.endedAt - r.meta.startedAt)}" else ""
                Muted("${bn(r.attendeeNames.size)} জন উপস্থিত  ·  ${bn(r.topics.size)}টি বিষয়$dur")
            }
        }

        if (r.struggling.isNotEmpty()) {
            item {
                BjCard {
                    Text("👀 যাদের দিকে নজর দেওয়া দরকার", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Muted("বেশি লাল চেপেছে বা কুইজে বেশি ভুল করেছে", size = 13)
                    Spacer(Modifier.height(8.dp))
                    r.struggling.take(12).forEach { s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(s.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text("🔴 ${bn(s.reds)}   ❌ ${bn(s.wrongs)}", fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        if (r.topics.isNotEmpty()) item { SectionTitle("বিষয় অনুযায়ী বোঝা") }
        items(r.topics) { t ->
            val total = t.green + t.yellow + t.red + t.none
            BjCard {
                Text(t.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                StackedBar(t.green, t.yellow, t.red, t.none)
                Spacer(Modifier.height(6.dp))
                Text(
                    "🟢 ${bn(t.green)}   🟡 ${bn(t.yellow)}   🔴 ${bn(t.red)}   ⚪ ${bn(t.none)}",
                    fontSize = 14.sp,
                )
                if (total > 0 && t.red * 100 >= total * 30) {
                    Text("⚠️ ${bn((t.red * 100 + total / 2) / total)}% বুঝেনি — এই বিষয়টা আবার দেখা দরকার", color = Red, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                }
                if (t.redNames.isNotEmpty()) {
                    Muted("বুঝেনি: ${t.redNames.joinToString(", ")}", Modifier.padding(top = 4.dp), size = 13)
                }
            }
        }

        r.runs.forEach { run ->
            item { SectionTitle("📝 ${run.title}") }
            item {
                BjCard {
                    Text("প্রশ্ন অনুযায়ী সঠিক উত্তর", fontWeight = FontWeight.Bold)
                    run.questions.forEachIndexed { i, q ->
                        Spacer(Modifier.height(10.dp))
                        Text("${bn(i + 1)}. ${q.text}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        if (q.answered == 0) {
                            StackedBar(0, 0, 0, 1, Modifier.padding(top = 4.dp))
                        } else {
                            StackedBar(q.correctPct, 0, 100 - q.correctPct, 0, Modifier.padding(top = 4.dp))
                        }
                        Muted("${bn(q.answered)} জন উত্তর দিয়েছে  ·  সঠিক ${bn(q.correctPct)}%", size = 13)
                        if (q.topWrongOption != null && q.answered > q.correct) {
                            Muted("সবচেয়ে বেশি ভুল উত্তর: ${q.topWrongOption}", size = 13)
                        }
                    }
                }
            }
            if (run.scores.isNotEmpty()) {
                item {
                    BjCard {
                        Text("স্কোর", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        run.scores.forEach { s ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(s.name, modifier = Modifier.weight(1f))
                                Text("${bn(s.correct)}/${bn(s.total)}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            BjCard {
                Text("উপস্থিত (${bn(r.attendeeNames.size)})", fontWeight = FontWeight.Bold)
                Muted(r.attendeeNames.joinToString(", ").ifEmpty { "কেউ আসেনি" }, Modifier.padding(top = 4.dp), size = 14)
            }
        }
        item {
            Muted("সব তথ্য শুধু এই ফোনে সংরক্ষিত আছে।", Modifier.padding(vertical = 8.dp), size = 13)
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * হোয়াটসঅ্যাপে পাঠানোর সারাংশ। *…* মোটা, _…_ বাঁকা। প্রশ্নের তালিকা নেই;
 * আছে ক্লাসে কী পড়ানো হলো (নামসহ কারা বুঝেনি) আর প্রত্যেকের কুইজ স্কোর।
 */
internal fun summaryText(r: SessionReport): String = buildString {
    appendLine("📚 *ক্লাসের সারাংশ*")
    appendLine("*${r.meta.title}*")
    val dur = if (r.meta.endedAt > r.meta.startedAt) "  ·  ⏱ ${fmtDuration(r.meta.endedAt - r.meta.startedAt)}" else ""
    appendLine("📅 ${fmtDate(r.meta.startedAt)}$dur")
    appendLine("👥 উপস্থিত: ${bn(r.attendeeNames.size)} জন")

    if (r.topics.isNotEmpty()) {
        appendLine()
        appendLine("📖 *ক্লাসে যা পড়ানো হয়েছে*")
        r.topics.forEachIndexed { i, t ->
            appendLine("${bn(i + 1)}) ${t.text}")
            appendLine("     🟢 ${bn(t.green)}   🟡 ${bn(t.yellow)}   🔴 ${bn(t.red)}")
            if (t.redNames.isNotEmpty()) appendLine("     _বুঝেনি:_ ${t.redNames.joinToString(", ")}")
        }
    }

    for (run in r.runs) {
        if (run.scores.isEmpty()) continue
        appendLine()
        appendLine("📝 *${run.title} — কে কত পেয়েছে*")
        val sorted = run.scores.sortedWith(compareByDescending<ScoreRow> { it.correct * 1000 / it.total.coerceAtLeast(1) }.thenBy { it.name })
        sorted.forEachIndexed { i, sc ->
            val key = sc.correct * 1000 / sc.total.coerceAtLeast(1)
            val rank = sorted.indexOfFirst { it.correct * 1000 / it.total.coerceAtLeast(1) == key }
            val medal = when (rank) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "▫️" }
            val pct = sc.correct * 100 / sc.total.coerceAtLeast(1)
            appendLine("$medal ${sc.name} — *${bn(sc.correct)}/${bn(sc.total)}* (${bn(pct)}%)")
        }
    }

    if (r.struggling.isNotEmpty()) {
        appendLine()
        appendLine("👀 *নজর দেওয়া দরকার:* " + r.struggling.take(8).joinToString(", ") { it.name })
    }
    appendLine()
    append("_বুঝেছি? অ্যাপ থেকে পাঠানো_")
}

/** সরাসরি WhatsApp (না থাকলে Business, তাও না থাকলে সাধারণ শেয়ার মেনু)। */
private fun shareReport(ctx: android.content.Context, text: String, whatsapp: Boolean) {
    fun intent(pkg: String?) = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).apply {
        if (pkg != null) setPackage(pkg)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (whatsapp) {
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            try {
                ctx.startActivity(intent(pkg))
                return
            } catch (e: android.content.ActivityNotFoundException) {
                // পরেরটা চেষ্টা
            }
        }
        android.widget.Toast.makeText(ctx, "WhatsApp পাওয়া যায়নি — সাধারণ শেয়ার খুলছে", android.widget.Toast.LENGTH_SHORT).show()
    }
    ctx.startActivity(Intent.createChooser(intent(null), "রিপোর্ট শেয়ার").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
