package com.edurip.bujhechhi.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.edurip.bujhechhi.core.QuizSet
import com.edurip.bujhechhi.core.Report
import com.edurip.bujhechhi.core.SessionMeta
import com.edurip.bujhechhi.core.StudentStat
import com.edurip.bujhechhi.core.cleanName
import com.edurip.bujhechhi.data.Settings
import com.edurip.bujhechhi.server.ClassEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TABS = listOf("🏫" to "ক্লাস", "📝" to "কুইজ", "👥" to "শিক্ষার্থী", "🗂️" to "ইতিহাস", "⚙️" to "সেটিংস")

@Composable
fun HomeScreen(onReport: (Long) -> Unit, onEditQuiz: (Long) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(icon, fontSize = 20.sp) },
                        label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> StartTab()
                1 -> QuizTab(onEditQuiz)
                2 -> StudentsTab()
                3 -> HistoryTab(onReport)
                else -> SettingsTab()
            }
        }
    }
}

// ------------------------------------------------------------------ ক্লাস শুরু

@Composable
private fun StartTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf(Settings.lastTitle.ifBlank { "আজকের ক্লাস" }) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }

    fun start() {
        scope.launch {
            busy = true
            err = ""
            val r = withContext(Dispatchers.IO) { ClassEngine.startClass(ctx, title) }
            busy = false
            r.onFailure { err = "সার্ভার চালু করা যায়নি: ${it.message}" }
        }
    }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { start() }

    fun begin() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            perm.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            start()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
            Text(
                "বুঝেছি?",
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                style = androidx.compose.ui.text.TextStyle(
                    brush = Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFFEC4899), Color(0xFFF59E0B))),
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    append("কে বুঝেনি, সেটা ")
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFFEC4899), fontWeight = FontWeight.Bold))
                    append("লজ্জা ছাড়াই")
                    pop()
                    append(" জানুন — ক্লাস চলতে চলতেই।")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
            )
        }

        BjCard {
            Text("নতুন ক্লাস", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("ক্লাসের নাম (যেমন: গণিত — ভগ্নাংশ)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = if (busy) "চালু হচ্ছে…" else "▶  ক্লাস শুরু করুন",
                onClick = { begin() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            if (err.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(err, color = Red, fontWeight = FontWeight.SemiBold)
            }
        }

        BjCard {
            Text("শুরুর আগে ৩টি ধাপ", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))
            Step("১", "ফোনের হটস্পট চালু করুন (অথবা কোচিংয়ের ওয়াইফাই রাউটারে যুক্ত হোন)। ইন্টারনেট না থাকলেও চলবে।")
            Step("২", "“ক্লাস শুরু করুন” চাপুন। একটি QR কোড ও ৪ সংখ্যার কোড আসবে।")
            Step("৩", "শিক্ষার্থীরা ওয়াইফাইতে যুক্ত হয়ে QR স্ক্যান করবে, কোনো অ্যাপ ইনস্টল লাগবে না।")
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { openHotspotSettings(ctx) }, modifier = Modifier.fillMaxWidth()) {
                Text("হটস্পট সেটিংস খুলুন")
            }
        }
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 10.dp, vertical = 2.dp),
        ) {
            Text(n, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 15.sp, modifier = Modifier.weight(1f))
    }
}

fun openHotspotSettings(ctx: Context) {
    val tries = listOf(
        Intent("com.android.settings.TETHER_SETTINGS"),
        Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings"),
        Intent().setClassName("com.android.settings", "com.android.settings.Settings\$TetherSettingsActivity"),
        Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS),
        Intent(android.provider.Settings.ACTION_WIFI_SETTINGS),
        Intent(android.provider.Settings.ACTION_SETTINGS),
    )
    for (i in tries) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            return
        } catch (e: Exception) {
            // পরেরটা চেষ্টা
        }
    }
    android.widget.Toast.makeText(
        ctx,
        "সেটিংস খোলা যায়নি। ফোনের সেটিংস > হটস্পট থেকে নিজে চালু করুন।",
        android.widget.Toast.LENGTH_LONG,
    ).show()
}

// ------------------------------------------------------------------ কুইজ ব্যাংক

@Composable
private fun QuizTab(onEditQuiz: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    var rev by remember { mutableIntStateOf(0) }
    val sets by produceState<List<QuizSet>?>(null, rev) {
        value = withContext(Dispatchers.IO) { ClassEngine.store.quizSets() }
    }
    var delete by remember { mutableStateOf<QuizSet?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("কুইজ ব্যাংক")
        Muted("টপিক ধরে ৫-৭টি MCQ বানিয়ে রাখুন। ক্লাসে এক ট্যাপে শিক্ষার্থীদের পাঠানো যাবে।")
        PrimaryButton("＋  নতুন কুইজ বানান", { onEditQuiz(0) }, Modifier.fillMaxWidth())
        val list = sets
        if (list == null) {
            Muted("লোড হচ্ছে…")
        } else if (list.isEmpty()) {
            BjCard { Muted("এখনও কোনো কুইজ নেই। উপরের বাটন চেপে বানান, চাইলে AI দিয়েও বানাতে পারবেন।", size = 15) }
        } else {
            list.forEach { s ->
                BjCard(onClick = { onEditQuiz(s.id) }) {
                    Text(s.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Muted("${bn(s.questions.size)}টি প্রশ্ন")
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { onEditQuiz(s.id) }) { Text("সম্পাদনা") }
                        TextButton(onClick = { delete = s }) { Text("মুছুন", color = Red) }
                    }
                }
            }
        }
    }

    delete?.let { s ->
        AlertDialog(
            onDismissRequest = { delete = null },
            title = { Text("কুইজ মুছবেন?") },
            text = { Text("“${s.title}” মুছে যাবে। আগের ক্লাসের রিপোর্ট ঠিক থাকবে।") },
            confirmButton = {
                TextButton(onClick = {
                    delete = null
                    scope.launch {
                        withContext(Dispatchers.IO) { ClassEngine.store.quizDelete(s.id) }
                        rev++
                    }
                }) { Text("মুছুন", color = Red) }
            },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("থাক") } },
        )
    }
}

// ------------------------------------------------------------------ শিক্ষার্থী

@Composable
private fun StudentsTab() {
    val scope = rememberCoroutineScope()
    var rev by remember { mutableIntStateOf(0) }
    val stats by produceState<List<StudentStat>?>(null, rev) {
        value = withContext(Dispatchers.IO) {
            val st = ClassEngine.store
            val raws = st.sessionList().mapNotNull { st.sessionRaw(it.id) }
            Report.studentStats(st.rosterAll(), raws)
        }
    }
    var names by rememberSaveable { mutableStateOf("") }
    var behindFirst by rememberSaveable { mutableStateOf(false) }
    var delete by remember { mutableStateOf<StudentStat?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("শিক্ষার্থী তালিকা")
        Muted("তালিকায় নাম থাকলে ক্লাসে শিক্ষার্থীরা নিজের নাম বেছে নিতে পারবে। নতুন কেউ নিজে নাম লিখে যোগ দিলেও এখানে যুক্ত হয়ে যাবে।")
        BjCard {
            OutlinedTextField(
                value = names,
                onValueChange = { names = it },
                label = { Text("নাম লিখুন (প্রতি লাইনে একজন)") },
                minLines = 2,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton(
                "যোগ করুন",
                {
                    val list = names.lines().map { cleanName(it) }.filter { it.isNotEmpty() && it.length <= 40 }
                    names = ""
                    scope.launch {
                        withContext(Dispatchers.IO) { list.forEach { ClassEngine.store.rosterFindOrCreate(it) } }
                        rev++
                    }
                },
                Modifier.fillMaxWidth(),
                enabled = names.isNotBlank(),
            )
        }
        val list = stats
        if (list == null) {
            Muted("লোড হচ্ছে…")
        } else if (list.isEmpty()) {
            Muted("এখনও কোনো শিক্ষার্থী নেই।")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("মোট ${bn(list.size)} জন", Modifier.weight(1f))
                Text("পিছিয়ে থাকারা আগে", fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Switch(checked = behindFirst, onCheckedChange = { behindFirst = it })
            }
            val shown = if (behindFirst) {
                list.sortedByDescending { it.red * 2 + (it.quizTotal - it.quizCorrect) }
            } else {
                list
            }
            shown.forEach { s ->
                BjCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            if (s.sessions == 0) {
                                Muted("এখনও কোনো ক্লাসে আসেনি")
                            } else {
                                Muted("ক্লাস ${bn(s.sessions)}টি  ·  🟢 ${bn(s.green)}  🟡 ${bn(s.yellow)}  🔴 ${bn(s.red)}")
                                if (s.quizTotal > 0) {
                                    val pct = (s.quizCorrect * 100 + s.quizTotal / 2) / s.quizTotal
                                    Muted("কুইজে সঠিক ${bn(s.quizCorrect)}/${bn(s.quizTotal)} (${bn(pct)}%)")
                                }
                            }
                        }
                        TextButton(onClick = { delete = s }) { Text("✕", color = Red, fontSize = 18.sp) }
                    }
                }
            }
        }
    }

    delete?.let { s ->
        AlertDialog(
            onDismissRequest = { delete = null },
            title = { Text("${s.name}-কে মুছবেন?") },
            text = { Text("তালিকা থেকে সরে যাবে এবং এর আগের ক্লাসের ফিডব্যাক ও কুইজের তথ্যও মুছে যাবে।") },
            confirmButton = {
                TextButton(onClick = {
                    delete = null
                    scope.launch {
                        withContext(Dispatchers.IO) { ClassEngine.store.rosterDelete(s.id) }
                        rev++
                    }
                }) { Text("মুছুন", color = Red) }
            },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("থাক") } },
        )
    }
}

// ------------------------------------------------------------------ ইতিহাস

@Composable
private fun HistoryTab(onReport: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    var rev by remember { mutableIntStateOf(0) }
    val list by produceState<List<SessionMeta>?>(null, rev) {
        value = withContext(Dispatchers.IO) { ClassEngine.store.sessionList() }
    }
    var delete by remember { mutableStateOf<SessionMeta?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("আগের ক্লাস")
        val l = list
        if (l == null) {
            Muted("লোড হচ্ছে…")
        } else if (l.isEmpty()) {
            BjCard { Muted("এখনও কোনো ক্লাস হয়নি। ক্লাস শেষ হলে এখানে রিপোর্ট জমা হবে।", size = 15) }
        } else {
            l.forEach { s ->
                BjCard(onClick = { onReport(s.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Muted(fmtDate(s.startedAt))
                            Muted("${bn(s.attendees)} জন উপস্থিত  ·  ${bn(s.topics)}টি বিষয়")
                        }
                        TextButton(onClick = { delete = s }) { Text("✕", color = Red, fontSize = 18.sp) }
                    }
                }
            }
        }
    }

    delete?.let { s ->
        AlertDialog(
            onDismissRequest = { delete = null },
            title = { Text("ক্লাসের রিপোর্ট মুছবেন?") },
            text = { Text("“${s.title}” এর সব তথ্য মুছে যাবে।") },
            confirmButton = {
                TextButton(onClick = {
                    delete = null
                    scope.launch {
                        withContext(Dispatchers.IO) { ClassEngine.store.sessionDelete(s.id) }
                        rev++
                    }
                }) { Text("মুছুন", color = Red) }
            },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("থাক") } },
        )
    }
}

// ------------------------------------------------------------------ সেটিংস

@Composable
private fun SettingsTab() {
    val ctx = LocalContext.current
    var pct by remember { mutableIntStateOf(Settings.redThresholdPct) }
    var minCount by remember { mutableIntStateOf(Settings.redMinCount) }
    var vib by remember { mutableStateOf(Settings.vibrateOnAlert) }
    var secs by remember { mutableIntStateOf(Settings.quizSecs) }
    var key by remember { mutableStateOf(Settings.apiKey) }
    var model by remember { mutableStateOf(Settings.aiModel) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionTitle("সেটিংস")

        BjCard {
            Text("🔴 “আবার বুঝান” সতর্কতা", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Muted("লাল বাটন এর বেশি হলে আপনার স্ক্রিনে সতর্কতা আসবে।")
            Spacer(Modifier.height(8.dp))
            Text("যখন যুক্ত শিক্ষার্থীদের ${bn(pct)}% বুঝেনি বলবে", fontSize = 15.sp)
            Slider(
                value = pct.toFloat(),
                onValueChange = { pct = (it / 5).toInt() * 5 },
                onValueChangeFinished = {
                    Settings.redThresholdPct = pct
                    ClassEngine.applySettings()
                },
                valueRange = 10f..80f,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("কমপক্ষে ${bn(minCount)} জন লাল চাপলে", modifier = Modifier.weight(1f), fontSize = 15.sp)
                OutlinedButton(onClick = {
                    minCount = (minCount - 1).coerceAtLeast(1)
                    Settings.redMinCount = minCount
                    ClassEngine.applySettings()
                }) { Text("−") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    minCount = (minCount + 1).coerceAtMost(20)
                    Settings.redMinCount = minCount
                    ClassEngine.applySettings()
                }) { Text("+") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("সতর্কতায় ফোন কাঁপুক", modifier = Modifier.weight(1f), fontSize = 15.sp)
                Switch(checked = vib, onCheckedChange = {
                    vib = it
                    Settings.vibrateOnAlert = it
                })
            }
        }

        BjCard {
            Text("📝 কুইজের ডিফল্ট সময়", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("প্রতি প্রশ্নে ${bn(secs)} সেকেন্ড", modifier = Modifier.weight(1f), fontSize = 15.sp)
                OutlinedButton(onClick = {
                    secs = (secs - 5).coerceAtLeast(5)
                    Settings.quizSecs = secs
                }) { Text("−") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    secs = (secs + 5).coerceAtMost(300)
                    Settings.quizSecs = secs
                }) { Text("+") }
            }
        }

        BjCard {
            Text("✨ AI দিয়ে MCQ বানানো", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Muted("ঐচ্ছিক। নিজের Anthropic API কী দিলে টপিক লিখে প্রশ্ন বানিয়ে নিতে পারবেন। শুধু প্রশ্ন বানানোর সময় ইন্টারনেট লাগে, ক্লাসে নয়। কী শুধু এই ফোনে থাকে।")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it
                    Settings.apiKey = it
                },
                label = { Text("API কী") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = model,
                onValueChange = {
                    model = it
                    Settings.aiModel = it
                },
                label = { Text("মডেল (খালি রাখলে ডিফল্ট)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        BjCard {
            Text("📶 নেটওয়ার্ক ও ব্যাটারি", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Muted("৮-১০ জনের বেশি হলে ফোনের হটস্পট অনেক সময় সবাইকে ধরে না। তখন কোচিংয়ে একটি সস্তা ওয়াইফাই রাউটার রাখুন (ইন্টারনেট ছাড়াই চলবে) এবং আপনার ফোনও তাতে যুক্ত করুন।")
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { openHotspotSettings(ctx) }, modifier = Modifier.fillMaxWidth()) {
                Text("হটস্পট / ওয়াইফাই সেটিংস")
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    try {
                        val i = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(i)
                    } catch (e: Exception) {
                        // সেটিংস না খুললে উপেক্ষা
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ব্যাটারি সেভার থেকে এই অ্যাপ বাদ দিন") }
        }

        Muted("বুঝেছি? ১.০ — সব তথ্য শুধু আপনার ফোনে থাকে।")
        Spacer(Modifier.height(8.dp))
    }
}
