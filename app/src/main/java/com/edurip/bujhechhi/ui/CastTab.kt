package com.edurip.bujhechhi.ui

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edurip.bujhechhi.core.LiveCast
import com.edurip.bujhechhi.server.ClassEngine
import kotlinx.coroutines.delay

/** শিক্ষকের স্ক্রিন শিক্ষার্থীদের দেখানোর নিয়ন্ত্রণ। শিক্ষার্থীরা শুধু দেখতে পারে। */
@Composable
fun CastTab(cast: LiveCast?) {
    val ctx = LocalContext.current
    var quality by remember { mutableStateOf(1) }
    val hub = ClassEngine.screenHub

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val d = r.data
        if (r.resultCode == Activity.RESULT_OK && d != null) {
            if (!ClassEngine.castStart(ctx, r.resultCode, d, quality)) {
                Toast.makeText(ctx, "স্ক্রিন শেয়ার শুরু করা যায়নি", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(ctx, "অনুমতি দেওয়া হয়নি", Toast.LENGTH_SHORT).show()
        }
    }

    // প্রতি ২ সেকেন্ডে দর্শক ও ডেটার হিসাব
    var viewers by remember { mutableStateOf(0) }
    var fps by remember { mutableStateOf(0.0) }
    var kbs by remember { mutableStateOf(0.0) }
    var lastF by remember { mutableLongStateOf(hub.framesPublished.get()) }
    var lastB by remember { mutableLongStateOf(hub.bytesServed.get()) }
    LaunchedEffect(cast != null) {
        while (true) {
            delay(2000)
            val f = hub.framesPublished.get()
            val b = hub.bytesServed.get()
            fps = (f - lastF) / 2.0
            kbs = (b - lastB) / 2048.0
            lastF = f; lastB = b
            viewers = hub.viewers()
        }
    }

    val green = Color(0xFF1B9E5A)
    val red = Color(0xFFD64545)
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (cast == null) {
            Text("📺 আপনার ফোনের স্ক্রিন সবাইকে দেখান", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(
                "শুরু করলে আপনার ফোনে যা দেখা যাবে, শিক্ষার্থীদের ফোনেও তাই দেখা যাবে। তারা শুধু দেখতে পারবে, কিছু করতে পারবে না।",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("ছবির মান", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            QualityChips(quality) { quality = it }
            Button(
                onClick = {
                    val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    launcher.launch(mpm.createScreenCaptureIntent())
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = green),
                shape = RoundedCornerShape(14.dp),
            ) { Text("▶ স্ক্রিন শেয়ার শুরু", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            Tips()
        } else {
            val paused = cast.paused
            Column(
                Modifier.fillMaxWidth().background(if (paused) Color(0xFFFFF3D6) else Color(0xFFE3F6EC), RoundedCornerShape(14.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(if (paused) "⏸ বিরতিতে — শিক্ষার্থীরা কিছু দেখছে না" else "🔴 লাইভ — সবাই আপনার স্ক্রিন দেখছে", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF222222))
                Text(
                    "দেখছে ${bn(viewers)} জন · ${String.format("%.1f", fps)} ছবি/সেকেন্ড · ${String.format("%.0f", kbs)} KB/s",
                    fontSize = 12.sp, color = Color(0xFF444444),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { ClassEngine.castPause(!paused) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (paused) green else Color(0xFFE29A1A)),
                    shape = RoundedCornerShape(12.dp),
                ) { Text(if (paused) "▶ আবার চালু" else "⏸ বিরতি", fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = { ClassEngine.castStop() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    border = BorderStroke(1.5.dp, red),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("⏹ বন্ধ", color = red, fontWeight = FontWeight.Bold) }
            }
            Text("ছবির মান", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            QualityChips(cast.quality) { ClassEngine.castQuality(it) }
            Text(
                "বিরতি দিলে শিক্ষার্থীদের পর্দা ফাঁকা হয়ে \"শিক্ষক একটু থামিয়েছেন\" লেখা দেখায় — তখন আপনি নির্ভয়ে অন্য অ্যাপ খুলতে পারেন। নোটিফিকেশন থেকেও বিরতি/বন্ধ করা যায়।",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Tips()
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun QualityChips(selected: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("কম ডেটা", "স্বাভাবিক", "ভালো").forEachIndexed { i, t ->
            FilterChip(selected = selected == i, onClick = { onPick(i) }, label = { Text(t, fontSize = 13.sp) })
        }
    }
}

@Composable
private fun Tips() {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("কাজের পরামর্শ", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("• শুরুর আগে \"বিরক্ত করবেন না\" (DND) চালু করুন, নইলে মেসেজ/নোটিফিকেশন সবাই দেখে ফেলবে।", fontSize = 12.sp)
        Text("• পাসওয়ার্ড বা ব্যক্তিগত অ্যাপ খোলার আগে বিরতি দিন।", fontSize = 12.sp)
        Text("• ফোন ঘোরালে স্ক্রিনও ঘুরবে; শিক্ষার্থীর পেজ নিজে মানিয়ে নেয়।", fontSize = 12.sp)
        Text("• ছবি পর্দা বদলালেই যায়, স্থির থাকলে ডেটা প্রায় লাগে না।", fontSize = 12.sp)
    }
}
