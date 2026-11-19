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
import androidx.compose.ui.graphics.toArgb
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

fun hasMath(s: String): Boolean = s.contains('$') || s.contains("\\(") || s.contains("\\[")

private fun hex(c: androidx.compose.ui.graphics.Color): String = String.format("#%06X", 0xFFFFFF and c.toArgb())

/**
 * ক্লাস চলার সময় শিক্ষকের পর্দায় গণিতসহ প্রশ্ন + অপশনের ভোট-বার। একটি প্রশ্নের জন্য একটিই WebView;
 * ভোট বদলালে পেজ নতুন করে লোড না করে শুধু ডেটা বদলায় (ঝিলমিল হয় না)।
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MathQuestion(
    text: String,
    options: List<String>,
    counts: List<Int>,
    total: Int,
    correct: Int?,          // উত্তর দেখানো হলে সঠিক অপশন, নইলে null
    explanation: String?,   // উত্তর দেখানো হলে ব্যাখ্যা
) {
    val cs = androidx.compose.material3.MaterialTheme.colorScheme
    val fg = hex(cs.onSurface); val bar = hex(cs.surfaceVariant); val acc = hex(cs.primary); val green = hex(Green)
    val rows = options.mapIndexed { i, t ->
        val n = counts.getOrElse(i) { 0 }
        mapOf("l" to OPTION_LETTERS.getOrElse(i) { "•" }, "t" to t, "n" to bn(n), "f" to if (total > 0) n * 100 / total else 0, "r" to (correct != null && correct == i))
    }
    val data = Json.write(mapOf("q" to text, "o" to rows, "e" to (explanation ?: ""), "fg" to fg, "bar" to bar, "acc" to acc, "green" to green))
    val h = androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(120) }
    val state = androidx.compose.runtime.remember { arrayOf<Any?>(false, null) } // [loaded, latest data]
    state[1] = data
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(h.intValue.dp),
        factory = { c ->
            val main = android.os.Handler(android.os.Looper.getMainLooper())
            WebView(c).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = false
                setBackgroundColor(0)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = android.view.View.OVER_SCROLL_NEVER
                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun h(px: Int) { main.post { if (px > 0 && px != h.intValue) h.intValue = px } }
                }, "A")
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(v: WebView, url: String?) {
                        state[0] = true
                        v.evaluateJavascript("upd(${state[1]})", null)
                    }
                }
                loadDataWithBaseURL("file:///android_asset/student/", LIVE_PAGE, "text/html", "utf-8", null)
            }
        },
        update = { w -> if (state[0] == true && w.tag != data) { w.tag = data; w.evaluateJavascript("upd($data)", null) } },
    )
}

private val LIVE_PAGE = """
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<link rel="stylesheet" href="k/katex.min.css"><script src="k/katex.min.js"></script>
<style>
html,body{margin:0;padding:0;background:transparent}
body{font-family:sans-serif;font-size:17px;word-break:break-word;overflow:hidden}
.q{font-weight:700;font-size:17px;margin:0 0 6px}
.o{position:relative;margin-top:6px;border-radius:12px;overflow:hidden;min-height:44px}
.b{position:absolute;left:0;top:0;bottom:0;opacity:.28}
.c{position:relative;display:flex;align-items:center;gap:8px;padding:8px 12px;min-height:44px;box-sizing:border-box}
.c .t{flex:1;min-width:0;overflow-x:auto}.c .n{font-weight:700}
.e{margin-top:8px;font-size:13px;opacity:.8}
.katex-display{margin:.3em 0;overflow-x:auto;overflow-y:hidden}
</style></head><body><div id="r"></div><script>
function esc(s){return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}
var RE=/\$\$([\s\S]+?)\$\$|\\\[([\s\S]+?)\\\]|\\\(([\s\S]+?)\\\)|\$(?=\S)([^$\n]*?\S)\$(?!\d)/g;
function mt(s){s=String(s||'');var out='',last=0,m;RE.lastIndex=0;
 while((m=RE.exec(s))){out+=esc(s.slice(last,m.index));var d=m[1]!==undefined||m[2]!==undefined;
  var t=(m[1]!==undefined?m[1]:m[2]!==undefined?m[2]:m[3]!==undefined?m[3]:m[4]).trim();
  try{out+=katex.renderToString(t,{displayMode:d,throwOnError:false,strict:'ignore'})}catch(e){out+=esc(m[0])}
  last=m.index+m[0].length;}
 return out+esc(s.slice(last));}
function upd(D){
 document.body.style.color=D.fg;
 var h='<div class="q">'+mt(D.q)+'</div>';
 D.o.forEach(function(o){
  var tint=o.r?D.green:D.acc;
  h+='<div class="o" style="background:'+D.bar+'"><div class="b" style="width:'+o.f+'%;background:'+tint+'"></div>'+
     '<div class="c"><span class="t"'+(o.r?' style="font-weight:700;color:'+D.green+'"':'')+'>'+esc(o.l)+'&nbsp; '+mt(o.t)+(o.r?' &#10003;':'')+'</span><span class="n">'+esc(o.n)+'</span></div></div>';
 });
 if(D.e)h+='<div class="e">ব্যাখ্যা: '+mt(D.e)+'</div>';
 document.getElementById('r').innerHTML=h;
 requestAnimationFrame(function(){A.h(Math.ceil(document.getElementById('r').getBoundingClientRect().height)+4);});
}
</script></body></html>
""".trimIndent()
