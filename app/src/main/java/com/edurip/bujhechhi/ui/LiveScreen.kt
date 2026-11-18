package com.edurip.bujhechhi.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edurip.bujhechhi.core.LiveQuiz
import com.edurip.bujhechhi.core.LiveSnapshot
import com.edurip.bujhechhi.core.LiveStudent
import com.edurip.bujhechhi.core.QuizSet
import com.edurip.bujhechhi.data.Settings
import com.edurip.bujhechhi.server.ClassEngine
import com.edurip.bujhechhi.server.NetInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun LiveScreen(onEnded: (Long) -> Unit, onEditQuiz: (Long) -> Unit) {
    val live by ClassEngine.live.collectAsState()
    val net by ClassEngine.net.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmEnd by remember { mutableStateOf(false) }
    var seenAlert by rememberSaveable { mutableIntStateOf(live.alertSeq) }
    var banner by remember { mutableStateOf(false) }

    KeepScreenOn()

    LaunchedEffect(live.alertSeq) {
        if (live.alertSeq > seenAlert) {
            seenAlert = live.alertSeq
            banner = true
            delay(12_000)
            banner = false
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ---- হেডার
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Indigo, Indigo2)))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(live.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
                Text(
                    "কোড ${bn(live.code)}  ·  ${bn(live.students.size)} জন যুক্ত",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.2f))
                    .clickable { confirmEnd = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("ক্লাস শেষ", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        if (banner) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Red)
                    .clickable { banner = false }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🔴", fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("অনেকেই বুঝেনি — আবার বুঝান", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        "${bn(live.red)} জন (${bn(percent(live.red, live.students.size))}%) লাল চেপেছে",
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 13.sp,
                    )
                }
                Text("✕", color = Color.White, fontSize = 18.sp)
            }
        }

        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
            listOf("ফিডব্যাক", "কুইজ", "কানেকশন").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontWeight = FontWeight.SemiBold) })
            }
        }

        Box(Modifier.weight(1f).navigationBarsPadding()) {
            when (tab) {
                0 -> FeedbackTab(live)
                1 -> LiveQuizTab(live, onEditQuiz)
                else -> ConnectTab(live, net)
            }
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("ক্লাস শেষ করবেন?") },
            text = { Text("সবার সংযোগ বন্ধ হবে এবং রিপোর্ট তৈরি হবে।") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnd = false
                    val id = ClassEngine.endClass()
                    onEnded(id)
                }) { Text("শেষ করুন", color = Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("চলুক") } },
        )
    }
}

private fun percent(part: Int, total: Int): Int = if (total <= 0) 0 else (part * 100 + total / 2) / total

@Composable
private fun KeepScreenOn() {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        val w = (ctx as? Activity)?.window
        w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

// ================================================================== ফিডব্যাক ট্যাব

@Composable
private fun FeedbackTab(live: LiveSnapshot) {
    var redFirst by rememberSaveable { mutableStateOf(true) }
    var selected by remember { mutableStateOf<LiveStudent?>(null) }

    val sorted = remember(live.students, redFirst) {
        if (redFirst) {
            live.students.sortedWith(
                compareBy<LiveStudent> {
                    when (it.status) {
                        "R" -> 0
                        "Y" -> 1
                        null -> 2
                        else -> 3
                    }
                }.thenBy { it.name },
            )
        } else {
            live.students
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { SummaryCard(live) }
        item(span = { GridItemSpan(maxLineSpan) }) { TopicComposer(live) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(listOf(Indigo, Indigo2)))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "শিক্ষার্থী (${bn(live.students.size)})",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                )
                Text("লাল আগে", fontSize = 14.sp, color = Color.White)
                Spacer(Modifier.width(8.dp))
                Switch(checked = redFirst, onCheckedChange = { redFirst = it })
            }
        }
        if (live.students.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BjCard {
                    Text("এখনও কেউ যুক্ত হয়নি", fontWeight = FontWeight.Bold)
                    Muted("“কানেকশন” ট্যাবে QR কোড আছে। শিক্ষার্থীরা স্ক্যান করলে এখানে নাম দেখা যাবে।", size = 15)
                }
            }
        }
        items(sorted, key = { it.sid }) { s -> StudentTile(s) { selected = s } }
    }

    selected?.let { s ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(s.name) },
            text = {
                Column {
                    Text(statusLabel(s.status), color = statusColor(s.status), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(6.dp))
                    Muted(if (s.online) "এখন যুক্ত আছে" else "সংযোগ বিচ্ছিন্ন (আবার যুক্ত হলে আগের অবস্থা ফিরে আসবে)")
                    if (s.shared) Muted("একই ফোনে একাধিক জন আছে")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ClassEngine.core.kick(s.sid)
                    selected = null
                }) { Text("ক্লাস থেকে সরান", color = Red) }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("বন্ধ") } },
        )
    }
}

@Composable
private fun SummaryCard(live: LiveSnapshot) {
    val total = live.students.size
    BjCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("এখন কেমন বুঝছে", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
            if (total > 0) {
                Text(
                    "বুঝেনি ${bn(percent(live.red, total))}%",
                    color = if (live.red > 0) Red else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        StackedBar(live.green, live.yellow, live.red, live.none)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Count("বুঝেছে", live.green, Green)
            Count("একটু", live.yellow, Amber)
            Count("বুঝেনি", live.red, Red)
            Count("বাকি", live.none, Gray)
        }
    }
}

@Composable
private fun Count(label: String, n: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(bn(n), color = color, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TopicComposer(live: LiveSnapshot) {
    var text by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        PrimaryButton("📤  শিক্ষার্থীদের পাঠ / বিষয় পাঠান", { open = true }, Modifier.fillMaxWidth())
        if (live.topic.isNotEmpty()) {
            Text(
                "এখন চলছে (বিষয় ${bn(live.topicNo)}): ${live.topic}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp),
            )
        }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("শিক্ষার্থীদের পাঠ") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (live.topic.isNotEmpty()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp),
                        ) {
                            Column {
                                Text("এখন চলছে (বিষয় ${bn(live.topicNo)})", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text(live.topic, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(if (live.topic.isEmpty()) "প্রথম বিষয়/কাজ লিখুন" else "নতুন বিষয়/কাজ লিখুন") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (live.topicHistory.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            live.topicHistory.forEach { h ->
                                Chip(if (h.length > 22) h.take(22) + "…" else h, selected = false, onClick = { text = h })
                            }
                        }
                    }
                    Muted("নতুন বিষয় পাঠালে সবার বাটন আবার খালি হবে।", Modifier.padding(top = 8.dp), size = 12)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        ClassEngine.core.setTopic(text)
                        text = ""
                        open = false
                    },
                ) { Text("📤 পাঠান", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("বন্ধ") } },
        )
    }
}

@Composable
private fun StudentTile(s: LiveStudent, onClick: () -> Unit) {
    val c = statusColor(s.status)
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .alpha(if (s.online) 1f else 0.5f)
            .clip(shape)
            .background(if (s.status == null) MaterialTheme.colorScheme.surface else c.copy(alpha = 0.16f))
            .border(BorderStroke(2.dp, if (s.status == null) MaterialTheme.colorScheme.outline else c), shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(99.dp)).background(c))
            Spacer(Modifier.width(8.dp))
            Text(s.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, modifier = Modifier.weight(1f))
            if (s.shared) Text("📱", fontSize = 14.sp)
        }
        Spacer(Modifier.height(4.dp))
        val sub = buildString {
            append(if (s.status == null) "—" else statusLabel(s.status))
            if (s.qOf > 0) {
                append(if (s.qOf == 1) (if (s.qDone == 1) "  ·  ✓ উত্তর দিয়েছে" else "  ·  উত্তর বাকি") else "  ·  ${bn(s.qDone)}/${bn(s.qOf)}")
            }
            if (!s.online) append("  ·  অফলাইন")
        }
        Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

// ================================================================== কুইজ ট্যাব

@Composable
private fun LiveQuizTab(live: LiveSnapshot, onEditQuiz: (Long) -> Unit) {
    val q = live.quiz
    if (q == null) QuizPicker(live, onEditQuiz) else QuizControl(live, q)
}

@Composable
private fun QuizPicker(live: LiveSnapshot, onEditQuiz: (Long) -> Unit) {
    val sets by produceState<List<QuizSet>?>(null, live.sessionId) {
        value = withContext(Dispatchers.IO) { ClassEngine.store.quizSets() }
    }
    var selectedId by rememberSaveable { mutableLongStateOf(0L) }
    var mode by rememberSaveable { mutableStateOf("step") }
    var secs by rememberSaveable { mutableIntStateOf(Settings.quizSecs) }
    val list = sets

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("কুইজ শুরু করুন")
        if (list == null) {
            Muted("লোড হচ্ছে…")
        } else if (list.isEmpty()) {
            BjCard {
                Text("কোনো কুইজ নেই", fontWeight = FontWeight.Bold)
                Muted("টপিক ধরে ৫-৭টি প্রশ্ন বানিয়ে নিন (AI-ও ব্যবহার করতে পারেন)।", size = 15)
            }
        } else {
            list.forEach { s ->
                val on = (if (selectedId == 0L) list.first().id else selectedId) == s.id
                BjCard(onClick = { selectedId = s.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (on) "🔘" else "⚪", fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Muted("${bn(s.questions.size)}টি প্রশ্ন")
                        }
                    }
                }
            }
            BjCard {
                Text("কীভাবে চলবে", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("একটা একটা করে", mode == "step", { mode = "step" })
                    Chip("সব প্রশ্ন একসাথে", mode == "all", { mode = "all" })
                }
                Muted(
                    if (mode == "step") "আপনি প্রতিটি প্রশ্ন ধরে ধরে এগোবেন, প্রতিটিতে আলাদা সময়।"
                    else "সব প্রশ্ন একবারে যাবে; মোট সময় = প্রশ্ন × নিচের সময়।",
                    Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("প্রতি প্রশ্নে ${bn(secs)} সেকেন্ড", modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { secs = (secs - 5).coerceAtLeast(5) }) { Text("−") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { secs = (secs + 5).coerceAtMost(300) }) { Text("+") }
                }
            }
            PrimaryButton(
                "▶  কুইজ শুরু করুন",
                {
                    val chosen = list.firstOrNull { it.id == selectedId } ?: list.first()
                    Settings.quizSecs = secs
                    ClassEngine.core.startQuiz(chosen, mode, secs)
                },
                Modifier.fillMaxWidth(),
                enabled = list.any { it.questions.isNotEmpty() },
            )
        }
        OutlinedButton(onClick = { onEditQuiz(0) }, modifier = Modifier.fillMaxWidth()) { Text("＋ নতুন কুইজ বানান") }
        if (live.topic.isEmpty()) Muted("টিপ: কুইজের আগে ফিডব্যাক ট্যাব থেকে বিষয় পাঠিয়ে নিলে রিপোর্ট আরও কাজের হয়।", size = 13)
    }
}

@Composable
private fun QuizControl(live: LiveSnapshot, q: LiveQuiz) {
    val total = live.students.size
    val isStep = q.mode == "step"
    val indices = if (isStep) listOf(q.cur) else q.questions.indices.toList()
    val pending = live.students.filter { s -> if (isStep) s.qDone == 0 else s.qDone < q.n }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BjCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("📝 ${q.title}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Muted(if (isStep) "প্রশ্ন ${bn(q.cur + 1)}/${bn(q.n)}" else "${bn(q.n)}টি প্রশ্ন একসাথে")
                }
                when (q.status) {
                    "running" -> Text(fmtClock(q.remainMs), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = if (q.remainMs < 6000) Red else MaterialTheme.colorScheme.primary)
                    "locked" -> Text("⏱ সময় শেষ", fontWeight = FontWeight.Bold, color = Amber)
                    else -> Text("✅ উত্তর দেখানো হয়েছে", fontWeight = FontWeight.Bold, color = Green)
                }
            }
        }

        indices.forEach { qi ->
            val question = q.questions[qi]
            val counts = q.counts[qi]
            val shown = qi in q.revealed
            BjCard {
                Text("প্রশ্ন ${bn(qi + 1)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(question.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(4.dp))
                question.options.forEachIndexed { oi, text ->
                    OptionBar(
                        label = OPTION_LETTERS.getOrElse(oi) { "•" },
                        text = text,
                        count = counts.getOrElse(oi) { 0 },
                        total = total,
                        correct = if (shown) oi == question.correct else null,
                    )
                }
                Muted("${bn(q.answeredPerQ[qi])}/${bn(total)} জন উত্তর দিয়েছে", Modifier.padding(top = 8.dp))
                if (shown && question.explanation.isNotBlank()) {
                    Muted("ব্যাখ্যা: ${question.explanation}", Modifier.padding(top = 4.dp), size = 13)
                }
            }
        }

        if (pending.isNotEmpty() && q.status != "reveal") {
            BjCard {
                Text("এখনও উত্তর দেয়নি (${bn(pending.size)})", fontWeight = FontWeight.Bold)
                Muted(pending.take(12).joinToString(", ") { it.name } + if (pending.size > 12) " …" else "", Modifier.padding(top = 4.dp), size = 14)
            }
        }

        if (q.status != "reveal") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { ClassEngine.core.quizExtend(10) }, modifier = Modifier.weight(1f)) { Text("+১০ সেকেন্ড") }
                PrimaryButton("✅ উত্তর দেখাও", { ClassEngine.core.quizReveal() }, Modifier.weight(1.4f))
            }
        }
        if (isStep && q.cur < q.n - 1) {
            if (q.status == "reveal") {
                PrimaryButton("পরের প্রশ্ন ▶", { ClassEngine.core.quizNext() }, Modifier.fillMaxWidth())
            } else {
                OutlinedButton(onClick = { ClassEngine.core.quizNext() }, modifier = Modifier.fillMaxWidth()) { Text("উত্তর না দেখিয়েই পরের প্রশ্নে যান ▶") }
            }
        }
        if (q.status == "reveal" && (!isStep || q.cur >= q.n - 1)) {
            PrimaryButton("কুইজ শেষ করুন", { ClassEngine.core.quizEnd() }, Modifier.fillMaxWidth())
        } else {
            TextButton(onClick = { ClassEngine.core.quizEnd() }, modifier = Modifier.fillMaxWidth()) { Text("কুইজ বন্ধ করুন", color = Red) }
        }
    }
}

// ================================================================== কানেকশন ট্যাব

@Composable
private fun ConnectTab(live: LiveSnapshot, net: NetInfo) {
    val ctx = LocalContext.current
    var pick by rememberSaveable { mutableIntStateOf(0) }
    val idx = pick.coerceIn(0, (net.addrs.size - 1).coerceAtLeast(0))
    val addr = net.addrs.getOrNull(idx)
    val base = addr?.let { "${it.ip}:${net.port}" }
    var fixedQr by rememberSaveable { mutableStateOf(true) }
    val url = base?.let { if (fixedQr) "http://$it/" else "http://$it/?c=${live.code}" }
    val screenW = LocalConfiguration.current.screenWidthDp
    val qr = (screenW - 80).coerceIn(180, 300).dp

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (url == null) {
            BjCard(Modifier.fillMaxWidth()) {
                Text("⚠️ নেটওয়ার্ক পাওয়া যায়নি", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Amber)
                Muted("ফোনের হটস্পট চালু করুন, অথবা ক্লাসরুমের ওয়াইফাই রাউটারে যুক্ত হোন। ইন্টারনেট না থাকলেও সমস্যা নেই।", Modifier.padding(top = 6.dp), size = 15)
                Spacer(Modifier.height(10.dp))
                PrimaryButton("হটস্পট সেটিংস খুলুন", { openHotspotSettings(ctx) }, Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("📌 স্থায়ী QR (প্রিন্টের জন্য)", fixedQr, { fixedQr = true })
                Chip("আজকের QR (কোডসহ)", !fixedQr, { fixedQr = false })
            }
            Text("শিক্ষার্থীরা এই QR স্ক্যান করুক", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            QrCode(url, qr)
            if (fixedQr) {
                Muted("এই QR প্রতিদিন একই থাকে — প্রিন্ট করে দেয়ালে লাগাতে পারেন। স্ক্যান করার পর শিক্ষার্থী নিচের ৪ অঙ্কের কোড লিখবে। কোডটি বোর্ডে লিখে দিন বা বলে দিন।", size = 14)
            }
            Text(
                "কোড  ${bn(live.code)}",
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            BjCard(Modifier.fillMaxWidth()) {
                Text("QR না চললে ব্রাউজারে এই ঠিকানা লিখুন", fontWeight = FontWeight.SemiBold)
                Text(base ?: "", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                Muted("তারপর কোড ${bn(live.code)} দিন। শিক্ষার্থীর ফোন অবশ্যই এই একই ওয়াইফাইতে যুক্ত থাকতে হবে।", Modifier.padding(top = 4.dp), size = 14)
            }
            if (net.addrs.size > 1) {
                Muted("একাধিক নেটওয়ার্ক পাওয়া গেছে। QR কাজ না করলে অন্যটি বেছে নিন:")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    net.addrs.forEachIndexed { i, a -> Chip("${a.ip} (${a.iface})", idx == i, { pick = i }) }
                }
            }
        }
        BjCard(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                Count("যুক্ত ফোন", live.devicesOnline, MaterialTheme.colorScheme.primary)
                Count("শিক্ষার্থী", live.students.size, MaterialTheme.colorScheme.primary)
            }
        }
        Muted("৮-১০ জনের বেশি হলে হটস্পটের বদলে একটি ওয়াইফাই রাউটার ব্যবহার করুন (ইন্টারনেট ছাড়াই চলবে)।", size = 13)
    }
}
