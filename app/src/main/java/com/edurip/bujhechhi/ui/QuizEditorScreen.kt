package com.edurip.bujhechhi.ui

import android.widget.Toast
import androidx.compose.foundation.background
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

    LaunchedEffect(id) {
        if (id != 0L) {
            val set = withContext(Dispatchers.IO) { ClassEngine.store.quizSets().firstOrNull { it.id == id } }
            if (set != null) {
                title = set.title
                qs.clear()
                qs.addAll(set.questions.map { it.toEdit() })
            }
        }
        if (qs.isEmpty()) qs.add(blank())
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
        if (aiTopic.isBlank()) {
            aiMsg = "আগে টপিক বা পাঠ্যাংশ লিখুন"
            return
        }
        aiBusy = true
        aiMsg = ""
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { AiQuiz.generate(Settings.apiKey, Settings.aiModel, aiTopic, aiCount, aiLevel) }
            }
            aiBusy = false
            r.onSuccess { list ->
                if (qs.size == 1 && qs[0].text.isBlank() && qs[0].opts.all { it.isBlank() }) qs.clear()
                list.forEach { qs.add(it.toEdit()) }
                if (title.isBlank()) title = aiTopic.lines().first().take(40)
                aiMsg = "✅ ${bn(list.size)}টি প্রশ্ন যোগ হয়েছে। নিচে দেখে ঠিক করে নিন।"
            }
            r.onFailure { aiMsg = "❌ ${it.message ?: "ব্যর্থ হয়েছে"}" }
        }
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
                    Text("✨ AI দিয়ে প্রশ্ন বানান", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Muted("টপিকের নাম বা পাঠের অংশ লিখুন। ইন্টারনেট ও সেটিংসে API কী লাগবে।", Modifier.padding(top = 2.dp), size = 13)
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

            itemsIndexed(qs, key = { _, q -> q.key }) { i, q ->
                BjCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("প্রশ্ন ${bn(i + 1)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        TextButton(onClick = { if (qs.size > 1) qs.removeAt(i) else qs[i] = blank() }) { Text("মুছুন", color = Red) }
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
            item {
                OutlinedButton(onClick = { qs.add(blank()) }, modifier = Modifier.fillMaxWidth()) { Text("＋ আরেকটি প্রশ্ন") }
            }
            item {
                PrimaryButton(if (saving) "সেভ হচ্ছে…" else "💾  কুইজ সেভ করুন", { save() }, Modifier.fillMaxWidth(), enabled = loaded && !saving)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
