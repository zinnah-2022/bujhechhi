package com.edurip.bujhechhi.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edurip.bujhechhi.core.AiQuiz
import com.edurip.bujhechhi.core.CsvQuiz
import com.edurip.bujhechhi.core.Question
import com.edurip.bujhechhi.core.QuizSet
import com.edurip.bujhechhi.data.Settings
import com.edurip.bujhechhi.server.ClassEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** সম্পাদনার সময়ের প্রশ্ন: অপশন সবসময় ৪টি ঘর (ফাঁকা থাকলে সেভের সময় বাদ যায়)। */
private data class QEdit(
    val key: Long,
    val text: String = "",
    val opts: List<String> = listOf("", "", "", ""),
    val correct: Int = 0,
    val expl: String = "",
)

private var keySeq = 0L
private fun blank() = QEdit(key = ++keySeq)

private fun Question.toEdit(): QEdit {
    val o = options.take(4).toMutableList()
    while (o.size < 4) o.add("")
    return QEdit(++keySeq, text, o, correct.coerceIn(0, 3), explanation)
}

@Composable
fun QuizEditorScreen(id: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    val qs = remember { mutableStateListOf<QEdit>() }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    // এআই
    var aiTopic by rememberSaveable { mutableStateOf("") }
    var aiCount by rememberSaveable { mutableIntStateOf(5) }
    var aiLevel by rememberSaveable { mutableStateOf("মাঝারি") }
    var aiBusy by remember { mutableStateOf(false) }
    var aiMsg by remember { mutableStateOf("") }
    val aiImages = remember { mutableStateListOf<AiQuiz.Img>() }
    var brainOpen by remember { mutableStateOf(false) }
    var brainText by remember { mutableStateOf(Settings.aiBrain) }
    var brainOn by remember { mutableStateOf(Settings.aiBrain.isNotBlank()) }

    // CSV
    var csvText by rememberSaveable { mutableStateOf("") }
    var csvMsg by remember { mutableStateOf("") }
    val previews = remember { mutableStateListOf<Long>() }

    fun addCsv() {
        val r = CsvQuiz.parse(csvText)
        if (r.questions.isNotEmpty()) {
            if (qs.size == 1 && qs[0].text.isBlank() && qs[0].opts.all { it.isBlank() }) qs.clear()
            r.questions.forEach { qs.add(it.toEdit()) }
            csvText = ""
        }
        csvMsg = buildString {
            if (r.questions.isNotEmpty()) append("✅ ${bn(r.questions.size)}টি প্রশ্ন যোগ হয়েছে। নিচে দেখে নিন।")
            if (r.errors.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append("⚠️ ${bn(r.errors.size)}টি সমস্যা:\n").append(r.errors.take(5).joinToString("\n"))
                if (r.errors.size > 5) append("\n…")
            }
        }
    }

    var csvName by remember { mutableStateOf("") }
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val t = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            }
            val nm = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                }.getOrNull()
            }
            if (t.isNullOrBlank()) { csvMsg = "❌ ফাইল পড়া যায়নি"; csvName = "" } else { csvText = t.take(200_000); csvName = nm ?: "CSV ফাইল"; csvMsg = "" }
        }
    }

    fun saveDemo() {
        try {
            val bytes = CsvQuiz.DEMO.toByteArray(Charsets.UTF_8)
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val v = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "bujhechhi-demo.csv")
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/csv")
                }
                val u = ctx.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
                ctx.contentResolver.openOutputStream(u!!)!!.use { it.write(bytes) }
                Toast.makeText(ctx, "ডাউনলোড ফোল্ডারে সেভ হয়েছে: bujhechhi-demo.csv", Toast.LENGTH_LONG).show()
            } else {
                val f = java.io.File(ctx.cacheDir, "csv").apply { mkdirs() }.resolve("bujhechhi-demo.csv").also { it.writeBytes(bytes) }
                val u = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                ctx.startActivity(
                    android.content.Intent.createChooser(
                        android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/csv")
                            .putExtra(android.content.Intent.EXTRA_STREAM, u).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        "ডেমো CSV",
                    ),
                )
            }
        } catch (e: Exception) {
            Toast.makeText(ctx, "ডেমো ফাইল সেভ করা যায়নি", Toast.LENGTH_LONG).show()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val room = (4 - aiImages.size).coerceAtLeast(0)
            val got = withContext(Dispatchers.IO) { uris.take(room).mapNotNull { loadImage(ctx, it) } }
            aiImages.addAll(got)
            if (uris.size > room) Toast.makeText(ctx, "সর্বোচ্চ ৪টি ছবি নেওয়া যায়", Toast.LENGTH_SHORT).show()
            if (got.size < uris.take(room).size) Toast.makeText(ctx, "কিছু ছবি পড়া যায়নি", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(id) {
        if (id != 0L) {
            val set = withContext(Dispatchers.IO) { ClassEngine.store.quizSets().firstOrNull { it.id == id } }
            if (set != null) {
                title = set.title
                qs.clear()
                qs.addAll(set.questions.map { it.toEdit() })
            }
        }
        loaded = true
    }

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_LONG).show()

    fun save() {
        val out = ArrayList<Question>()
        qs.forEachIndexed { i, q ->
            if (q.text.isBlank() && q.opts.all { it.isBlank() }) return@forEachIndexed // পুরো ফাঁকা প্রশ্ন বাদ
            val kept = q.opts.mapIndexed { oi, t -> oi to t.trim() }.filter { it.second.isNotEmpty() }
            val ci = kept.indexOfFirst { it.first == q.correct }
            when {
                q.text.isBlank() -> { toast("প্রশ্ন ${bn(i + 1)}: প্রশ্ন লিখুন"); return }
                kept.size < 2 -> { toast("প্রশ্ন ${bn(i + 1)}: অন্তত ২টি অপশন দিন"); return }
                ci < 0 -> { toast("প্রশ্ন ${bn(i + 1)}: সঠিক উত্তরের অপশনটি ফাঁকা"); return }
            }
            out.add(Question(q.text.trim(), kept.map { it.second }, ci, q.expl.trim()))
        }
        if (out.isEmpty()) {
            toast("অন্তত একটি প্রশ্ন দিন")
            return
        }
        saving = true
        scope.launch {
            withContext(Dispatchers.IO) {
                ClassEngine.store.quizSave(QuizSet(id, title.trim().ifBlank { "নামহীন কুইজ" }, out))
            }
            saving = false
            onBack()
        }
    }

    fun generate() {
        if (aiTopic.isBlank() && aiImages.isEmpty()) {
            aiMsg = "আগে টপিক লিখুন বা ছবি দিন"
            return
        }
        aiBusy = true
        aiMsg = ""
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { AiQuiz.generate(Settings.apiKey, Settings.aiModel, aiTopic, aiCount, aiLevel, Settings.aiBrain, aiImages.toList()) }
            }
            aiBusy = false
            r.onSuccess { list ->
                if (qs.size == 1 && qs[0].text.isBlank() && qs[0].opts.all { it.isBlank() }) qs.clear()
                list.forEach { qs.add(it.toEdit()) }
                if (title.isBlank() && aiTopic.isNotBlank()) title = aiTopic.lines().first().take(40)
                aiMsg = "✅ ${bn(list.size)}টি প্রশ্ন যোগ হয়েছে। নিচে দেখে ঠিক করে নিন।"
            }
            r.onFailure { aiMsg = "❌ ${it.message ?: "ব্যর্থ হয়েছে"}" }
        }
    }

    if (brainOpen) {
        AlertDialog(
            onDismissRequest = { brainOpen = false },
            title = { Text("🧠 ব্রেইন") },
            text = {
                Column {
                    Muted("AI-কে স্থায়ী নির্দেশনা দিন। প্রতিবার প্রশ্ন বানানোর সময় এটি মানা হবে। যেমন: “নবম-দশম শ্রেণির মানে বানাও”, “অপশনে সংখ্যা বেশি রাখো”, “ব্যাখ্যা খুব ছোট দাও”।", size = 13)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = brainText,
                        onValueChange = { brainText = it },
                        label = { Text("নির্দেশনা") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { Settings.aiBrain = brainText; brainOn = brainText.isNotBlank(); brainOpen = false }) { Text("সেভ", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row {
                    if (brainOn) {
                        TextButton(onClick = { Settings.aiBrain = ""; brainText = ""; brainOn = false; brainOpen = false }) { Text("মুছুন", color = Red) }
                    }
                    TextButton(onClick = { brainOpen = false }) { Text("বন্ধ") }
                }
            },
        )
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← ফিরুন") }
            Text(
                if (id == 0L) "নতুন কুইজ" else "কুইজ সম্পাদনা",
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
            TextButton(onClick = { save() }, enabled = loaded && !saving) {
                Text("সেভ", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        LazyColumn(
            Modifier.weight(1f).navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("কুইজের নাম (যেমন: ভগ্নাংশ — ১ম ক্লাস)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                BjCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✨ AI দিয়ে প্রশ্ন বানান", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (!brainOn) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary)
                                .clickable { brainText = Settings.aiBrain; brainOpen = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                "🧠 ব্রেইন",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (!brainOn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                    Muted("টপিক লিখুন বা বই/খাতার ছবি দিন। ইন্টারনেট ও সেটিংসে API কী লাগবে।", Modifier.padding(top = 2.dp), size = 13)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = aiTopic,
                        onValueChange = { aiTopic = it },
                        label = { Text("টপিক / পাঠ্যাংশ") },
                        minLines = 2,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { picker.launch("image/*") },
                        enabled = !aiBusy && aiImages.size < 4,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("🖼️  ছবি আপলোড (${bn(aiImages.size)}/৪)") }
                    if (aiImages.isNotEmpty()) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            aiImages.forEachIndexed { i, img ->
                                val bmp = remember(img) { thumb(img) }
                                Box {
                                    if (bmp != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)),
                                        )
                                    }
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .clip(RoundedCornerShape(99.dp))
                                            .background(Color(0xCC000000))
                                            .clickable { aiImages.removeAt(i) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) { Text("✕", color = Color.White, fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Muted("কয়টি:", Modifier.padding(top = 8.dp))
                        listOf(5, 6, 7).forEach { n -> Chip("${bn(n)}টি", aiCount == n, { aiCount = n }) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Muted("স্তর:", Modifier.padding(top = 8.dp))
                        listOf("সহজ", "মাঝারি", "কঠিন").forEach { l -> Chip(l, aiLevel == l, { aiLevel = l }) }
                    }
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        if (aiBusy) "বানানো হচ্ছে…" else "✨  প্রশ্ন বানান",
                        { generate() },
                        Modifier.fillMaxWidth(),
                        enabled = !aiBusy,
                    )
                    if (aiMsg.isNotEmpty()) Text(aiMsg, modifier = Modifier.padding(top = 8.dp), fontSize = 14.sp)
                }
            }

            item {
                BjCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📋 CSV থেকে প্রশ্ন", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { saveDemo() }) { Text("⬇ ডেমো CSV", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp))
                            .border(androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(12.dp))
                            .clickable { csvPicker.launch("*/*") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (csvName.isEmpty()) "📂  CSV ফাইল আপলোড করুন" else "✅  $csvName",
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton("সাবমিট", { addCsv(); csvName = ""; csvText = "" }, Modifier.fillMaxWidth(), enabled = csvText.isNotBlank())
                    if (csvMsg.isNotEmpty()) Text(csvMsg, modifier = Modifier.padding(top = 8.dp), fontSize = 13.sp)
                }
            }

            itemsIndexed(qs, key = { _, q -> q.key }) { i, q ->
                BjCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("প্রশ্ন ${bn(i + 1)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        if (q.text.contains('$') || q.opts.any { it.contains('$') } || q.text.contains("\\(")) {
                            TextButton(onClick = { if (!previews.remove(q.key)) previews.add(q.key) }) {
                                Text(if (previews.contains(q.key)) "✕ প্রিভিউ" else "👁 গণিত প্রিভিউ")
                            }
                        }
                        TextButton(onClick = { qs.removeAt(i) }) { Text("মুছুন", color = Red) }
                    }
                    if (previews.contains(q.key)) {
                        MathPreview(q.text, q.opts, q.expl, q.correct)
                        Spacer(Modifier.height(6.dp))
                    }
                    OutlinedTextField(
                        value = q.text,
                        onValueChange = { qs[i] = q.copy(text = it) },
                        label = { Text("প্রশ্ন") },
                        minLines = 2,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Muted("সঠিক উত্তরের পাশের বোতাম চিহ্নিত করুন", size = 12)
                    q.opts.forEachIndexed { oi, t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = q.correct == oi, onClick = { qs[i] = q.copy(correct = oi) })
                            OutlinedTextField(
                                value = t,
                                onValueChange = { v -> qs[i] = q.copy(opts = q.opts.toMutableList().also { it[oi] = v }) },
                                label = { Text("অপশন ${OPTION_LETTERS[oi]}") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = q.expl,
                        onValueChange = { qs[i] = q.copy(expl = it) },
                        label = { Text("ব্যাখ্যা (ঐচ্ছিক — উত্তর দেখানোর পর শিক্ষার্থীরা দেখবে)") },
                        minLines = 1,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (qs.isEmpty()) {
                item { Muted("এখনও কোনো প্রশ্ন নেই। ওপরে AI দিয়ে বানান বা CSV থেকে যোগ করুন।", Modifier.fillMaxWidth().padding(vertical = 8.dp), size = 14) }
            }
            item {
                PrimaryButton(if (saving) "সেভ হচ্ছে…" else "💾  কুইজ সেভ করুন", { save() }, Modifier.fillMaxWidth(), enabled = loaded && !saving)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}


/** ছবি পড়ে ছোট করে (সর্বোচ্চ ১৫৬৮ পিক্সেল) JPEG base64 বানায়; ব্যর্থ হলে null। */
private fun loadImage(ctx: android.content.Context, uri: Uri): AiQuiz.Img? = try {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    var sample = 1
    val big = maxOf(opts.outWidth, opts.outHeight)
    while (big / sample > 3200) sample *= 2
    val bmp = ctx.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    if (bmp == null) {
        null
    } else {
        val scale = 1568f / maxOf(bmp.width, bmp.height)
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true) else bmp
        val bos = java.io.ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 85, bos)
        AiQuiz.Img("image/jpeg", Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP))
    }
} catch (e: Exception) {
    null
}

private fun thumb(img: AiQuiz.Img): Bitmap? = try {
    val b = Base64.decode(img.base64, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = 8 })
} catch (e: Exception) {
    null
}
