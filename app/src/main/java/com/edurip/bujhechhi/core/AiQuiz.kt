package com.edurip.bujhechhi.core

import java.net.HttpURLConnection
import java.net.URL

/**
 * টপিক বা পাঠ্যাংশ দিলে Claude দিয়ে MCQ বানায়। ইন্টারনেট ও নিজের API কী লাগে
 * (শুধু প্রশ্ন বানানোর সময়; ক্লাসে ইন্টারনেট লাগে না)।
 */
object AiQuiz {

    const val DEFAULT_MODEL = "claude-sonnet-5-5"

    /** ছবির ডেটা: mime (যেমন image/jpeg) ও base64 লেখা। */
    class Img(val mime: String, val base64: String)

    fun buildPrompt(topic: String, count: Int, level: String, extra: String = "", hasImages: Boolean = false): String {
        val src = buildString {
            if (hasImages) append("সংযুক্ত ছবি(গুলো)র লেখা ও বিষয়বস্তু পড়ে তার উপর ভিত্তি করে প্রশ্ন বানাও।\n")
            if (topic.isNotBlank()) append("বিষয়/পাঠ্যাংশ:\n").append(topic.trim()).append('\n')
        }
        val brain = if (extra.isBlank()) "" else "\nশিক্ষকের বিশেষ নির্দেশনা (এগুলো অবশ্যই মেনে চলবে):\n${extra.trim()}\n"
        return promptBody(count, level, src, brain)
    }

    private fun promptBody(count: Int, level: String, src: String, brain: String): String = """
        তুমি একজন অভিজ্ঞ বাংলাদেশি শিক্ষক। নিচের বিষয়ের উপর $count টি বহুনির্বাচনী প্রশ্ন (MCQ) বানাও।
        স্তর: $level

        $src$brain

        নিয়ম:
        - প্রশ্ন ও অপশন বাংলায় লেখো (গাণিতিক চিহ্ন বা ইংরেজি পরিভাষা প্রয়োজনে থাকতে পারে)।
        - প্রতিটি প্রশ্নে ঠিক ৪টি অপশন, সঠিক উত্তর একটিই।
        - ভুল অপশনগুলো এমন হবে যাতে শিক্ষার্থীর সাধারণ ভুল ধারণা ধরা পড়ে।
        - প্রতিটির জন্য ১-২ বাক্যে ছোট ব্যাখ্যা দাও।
        - গণিতের সূত্র, ভগ্নাংশ, ঘাত, বর্গমূল ইত্যাদি LaTeX-এ ডলার চিহ্নের ভেতরে লেখো, যেমন ${'$'}\\frac{1}{2}${'$'}, ${'$'}x^2${'$'}, ${'$'}\\sqrt{3}${'$'}। JSON স্ট্রিংয়ে প্রতিটি ব্যাকস্ল্যাশ দ্বিগুণ হবে। নতুন লাইন ব্যবহার করবে না।
        - শুধু JSON অ্যারে ফেরত দাও, আর কোনো লেখা বা কোড-ব্লক নয়। ফরম্যাট:
        [{"q":"প্রশ্ন","o":["অপশন১","অপশন২","অপশন৩","অপশন৪"],"c":0,"e":"ব্যাখ্যা"}]
        এখানে "c" হলো সঠিক অপশনের ইনডেক্স (০ থেকে শুরু)।
    """.trimIndent()

    fun buildRequestBody(model: String, prompt: String, images: List<Img> = emptyList()): String {
        val content: Any = if (images.isEmpty()) {
            prompt
        } else {
            images.map {
                mapOf(
                    "type" to "image",
                    "source" to mapOf("type" to "base64", "media_type" to it.mime, "data" to it.base64),
                )
            } + listOf(mapOf("type" to "text", "text" to prompt))
        }
        return Json.write(
            mapOf(
                "model" to model,
                "max_tokens" to 4000,
                "messages" to listOf(mapOf("role" to "user", "content" to content)),
            ),
        )
    }

    /** মডেলের উত্তর থেকে প্রশ্নের তালিকা বের করে। কোড-ব্লক বা বাড়তি লেখা থাকলেও চলে। */
    fun parseQuestions(text: String): List<Question> {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val arr = Json.parseOrNull(repairEscapes(text.substring(start, end + 1))).asList() ?: return emptyList()
        return arr.mapNotNull { item ->
            val m = item.asMap() ?: return@mapNotNull null
            val q = m.str("q")?.trim().orEmpty()
            val opts = (m["o"].asList() ?: emptyList<Any?>()).map { it?.toString()?.trim().orEmpty() }
                .filter { it.isNotEmpty() }
            val c = m.int("c") ?: return@mapNotNull null
            if (q.isEmpty() || opts.size < 2 || c !in opts.indices) return@mapNotNull null
            Question(q, opts, c, m.str("e")?.trim().orEmpty())
        }
    }

    /**
     * এআই অনেক সময় LaTeX-এর ব্যাকস্ল্যাশ একটি করে লেখে (\frac, \beta, \times)। JSON-এ \f \b \t চুপচাপ
     * অন্য অক্ষর হয়ে যায়, বাকিগুলো ভুল। তাই বৈধ JSON এস্কেপের (\" \\ \/ \uXXXX) বাইরের সব ব্যাকস্ল্যাশ,
     * এবং \b \f \n \r \t এর পরে ইংরেজি অক্ষর থাকলে (মানে LaTeX কমান্ড), দ্বিগুণ করি।
     */
    fun repairEscapes(s: String): String {
        val sb = StringBuilder(s.length + 16)
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch != '\\') { sb.append(ch); i++; continue }
            val n = s.getOrNull(i + 1)
            val n2 = s.getOrNull(i + 2)
            when {
                n == null -> sb.append("\\\\")
                n == '\\' -> { sb.append("\\\\"); i++ }
                n == '"' || n == '/' -> { sb.append(ch).append(n); i++ }
                n == 'u' && s.length >= i + 6 && s.substring(i + 2, i + 6).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } -> sb.append(ch)
                n in "bfnrt" && !(n2 != null && n2.isLetter() && n2.code < 128) -> sb.append(ch)
                else -> sb.append("\\\\")
            }
            i++
        }
        return sb.toString()
    }

    /** API-র পুরো উত্তর (JSON) থেকে লেখা অংশটুকু নেয়। */
    fun extractText(responseBody: String): String {
        val m = Json.parseOrNull(responseBody).asMap() ?: return ""
        val content = m["content"].asList() ?: return ""
        return content.mapNotNull { it.asMap()?.takeIf { b -> b.str("type") == "text" }?.str("text") }
            .joinToString("")
    }

    /** ব্লক করে (থ্রেডে ডাকতে হবে)। ব্যর্থ হলে Exception ছোঁড়ে, বার্তা বাংলায়। */
    fun generate(
        apiKey: String,
        model: String,
        topic: String,
        count: Int,
        level: String,
        extra: String = "",
        images: List<Img> = emptyList(),
    ): List<Question> {
        if (apiKey.isBlank()) throw IllegalStateException("সেটিংসে API কী দিন")
        val key = apiKey.trim()
        val gemini = isGeminiKey(key)
        val prompt = buildPrompt(topic, count, level, extra, images.isNotEmpty())

        // Gemini-তে একটি মডেল ব্যস্ত থাকলে নিজে থেকে পরেরটি চেষ্টা করে।
        val models: List<String> = if (gemini) {
            val first = if (model.isBlank() || model.startsWith("claude")) GEMINI_MODEL else model.trim()
            listOf(first, "gemini-2.5-flash", "gemini-flash-lite-latest", first).distinct().let { it + it.first() }
        } else {
            listOf(model.ifBlank { DEFAULT_MODEL })
        }

        var lastCode = 0
        var lastMsg = ""
        for ((i, m) in models.withIndex()) {
            val url: String
            val payload: String
            if (gemini) {
                url = "https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent"
                payload = buildGeminiBody(prompt, images)
            } else {
                url = "https://api.anthropic.com/v1/messages"
                payload = buildRequestBody(m, prompt, images)
            }
            val (code, body) = post(url, key, gemini, payload)
            if (code in 200..299) {
                val qs = parseQuestions(if (gemini) extractGeminiText(body) else extractText(body))
                if (qs.isNotEmpty()) return qs
                lastCode = 0
                lastMsg = "প্রশ্ন বানানো যায়নি, আবার চেষ্টা করুন"
                if (i < models.size - 1) continue else break
            }
            lastCode = code
            lastMsg = Json.parseOrNull(body).asMap()?.get("error").asMap()?.str("message") ?: "HTTP $code"
            if (code == 401 || (gemini && lastMsg.contains("API key", ignoreCase = true))) {
                throw IllegalStateException("API কী ভুল")
            }
            val busy = code == 429 || code == 500 || code == 503 || code == 504
            if (!busy) throw IllegalStateException(lastMsg)
            if (i < models.size - 1) Thread.sleep(2500)
        }
        throw IllegalStateException(
            when (lastCode) {
                429 -> "আজকের ফ্রি সীমা শেষ বা অনেক বেশি অনুরোধ। কিছুক্ষণ পরে আবার চেষ্টা করুন।"
                500, 503, 504 -> "এআই সার্ভারে এখন অনেক চাপ। ১-২ মিনিট পরে আবার “প্রশ্ন বানান” চাপুন।"
                else -> lastMsg
            },
        )
    }

    /** একটি HTTP অনুরোধ; (স্ট্যাটাস কোড, উত্তরের লেখা) ফেরত দেয়। */
    private fun post(url: String, key: String, gemini: Boolean, payload: String): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 120000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            if (gemini) {
                conn.setRequestProperty("x-goog-api-key", key)
            } else {
                conn.setRequestProperty("x-api-key", key)
                conn.setRequestProperty("anthropic-version", "2023-06-01")
            }
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return code to body
        } finally {
            conn.disconnect()
        }
    }

    const val GEMINI_MODEL = "gemini-flash-latest"

    /** Google (Gemini) কী `AIza` দিয়ে শুরু হয়; Anthropic কী `sk-ant-` দিয়ে। */
    fun isGeminiKey(key: String): Boolean = key.trim().startsWith("AIza")

    fun buildGeminiBody(prompt: String, images: List<Img> = emptyList()): String {
        val parts = images.map { mapOf("inline_data" to mapOf("mime_type" to it.mime, "data" to it.base64)) } +
            listOf(mapOf("text" to prompt))
        return Json.write(
            mapOf(
                "contents" to listOf(mapOf("role" to "user", "parts" to parts)),
                "generationConfig" to mapOf("temperature" to 0.7, "maxOutputTokens" to 8192),
            ),
        )
    }

    fun extractGeminiText(responseBody: String): String {
        val m = Json.parseOrNull(responseBody).asMap() ?: return ""
        val cand = m["candidates"].asList()?.firstOrNull().asMap() ?: return ""
        val parts = cand["content"].asMap()?.get("parts").asList() ?: return ""
        return parts.mapNotNull { it.asMap()?.str("text") }.joinToString("")
    }
}
