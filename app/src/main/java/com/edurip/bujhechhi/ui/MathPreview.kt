package com.edurip.bujhechhi.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.edurip.bujhechhi.core.Json

/** শিক্ষার্থীরা যেভাবে দেখবে সেভাবে গণিতসহ প্রশ্ন দেখানো (অ্যাপের ভেতরের KaTeX, ইন্টারনেট লাগে না)। */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MathPreview(question: String, options: List<String>, expl: String, correct: Int, height: Dp = 190.dp) {
    val data = remember(question, options, expl, correct) {
        Json.write(mapOf("q" to question, "o" to options, "e" to expl, "c" to correct))
            .replace("<", "\\u003c") // </script> থেকে নিরাপদ
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(height),
        factory = { c ->
            WebView(c).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = false
                setBackgroundColor(0)
            }
        },
        update = { w ->
            if (w.tag != data) {
                w.tag = data
                w.loadDataWithBaseURL("file:///android_asset/student/", PAGE.replace("__DATA__", data), "text/html", "utf-8", null)
            }
        },
    )
}

private val PAGE = """
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<link rel="stylesheet" href="k/katex.min.css"><script src="k/katex.min.js"></script>
<style>
body{font-family:sans-serif;font-size:15px;margin:6px;color:#1b1b1f;word-break:break-word}
.q{font-weight:700;margin-bottom:6px}.o{padding:5px 8px;margin:3px 0;border:1px solid #cfd2dc;border-radius:10px}
.o.r{border-color:#1b9e5a;background:#e3f6ec}.e{margin-top:6px;color:#555;font-size:13px}
.katex-display{margin:.3em 0;overflow-x:auto;overflow-y:hidden}
</style></head><body><div id="r"></div><script>
var D = __DATA__;
function esc(s){return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}
var RE=/\$\$([\s\S]+?)\$\$|\\\[([\s\S]+?)\\\]|\\\(([\s\S]+?)\\\)|\$(?=\S)([^$\n]*?\S)\$(?!\d)/g;
function mt(s){s=String(s||'');var out='',last=0,m;RE.lastIndex=0;
 while((m=RE.exec(s))){out+=esc(s.slice(last,m.index));var d=m[1]!==undefined||m[2]!==undefined;
  var t=(m[1]!==undefined?m[1]:m[2]!==undefined?m[2]:m[3]!==undefined?m[3]:m[4]).trim();
  try{out+=katex.renderToString(t,{displayMode:d,throwOnError:false,strict:'ignore'})}catch(e){out+=esc(m[0])}
  last=m.index+m[0].length;}
 return out+esc(s.slice(last));}
var L=['A','B','C','D'],h='<div class="q">'+mt(D.q)+'</div>';
D.o.forEach(function(t,i){if(!String(t).trim())return;h+='<div class="o'+(i===D.c?' r':'')+'"><b>'+L[i]+'.</b> '+mt(t)+'</div>';});
if(D.e)h+='<div class="e">'+mt(D.e)+'</div>';
document.getElementById('r').innerHTML=h;
</script></body></html>
""".trimIndent()
