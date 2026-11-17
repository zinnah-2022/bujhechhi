package com.edurip.bujhechhi.core

import java.net.HttpURLConnection
import java.net.URL

/**
 * টপিক বা পাঠ্যাংশ দিলে Claude দিয়ে MCQ বানায়। ইন্টারনেট ও নিজের API কী লাগে
 * (শুধু প্রশ্ন বানানোর সময়; ক্লাসে ইন্টারনেট লাগে না)।
 */
object AiQuiz {

    const val DEFAULT_MODEL = "claude-sonnet-5-5"

    fun buildPrompt(topic: String, count: Int, level: String): String = """
        তুমি একজন অভিজ্ঞ বাংলাদেশি শিক্ষক। নিচের বিষয়ের উপর $count টি বহুনির্বাচনী প্রশ্ন (MCQ) বানাও।
        স্তর: $level

        বিষয়/পাঠ্যাংশ:
        $topic

        নিয়ম:
        - প্রশ্ন ও অপশন বাংলায় লেখো (গাণিতিক চিহ্ন বা ইংরেজি পরিভাষা প্রয়োজনে থাকতে পারে)।
        - প্রতিটি প্রশ্নে ঠিক ৪টি অপশন, সঠিক উত্তর একটিই।
        - ভুল অপশনগুলো এমন হবে যাতে শিক্ষার্থীর সাধারণ ভুল ধারণা ধরা পড়ে।
        - প্রতিটির জন্য ১-২ বাক্যে ছোট ব্যাখ্যা দাও।
        - শুধু JSON অ্যারে ফেরত দাও, আর কোনো লেখা বা কোড-ব্লক নয়। ফরম্যাট:
        [{"q":"প্রশ্ন","o":["অপশন১","অপশন২","অপশন৩","অপশন৪"],"c":0,"e":"ব্যাখ্যা"}]
        এখানে "c" হলো সঠিক অপশনের ইনডেক্স (০ থেকে শুরু)।
    """.trimIndent()

    fun buildRequestBody(model: String, prompt: String): String = Json.write(
        mapOf(
            "model" to model,
            "max_tokens" to 4000,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
        ),
    )

    /** মডেলের উত্তর থেকে প্রশ্নের তালিকা বের করে। কোড-ব্লক বা বাড়তি লেখা থাকলেও চলে। */
    fun parseQuestions(text: String): List<Question> {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val arr = Json.parseOrNull(text.substring(start, end + 1)).asList() ?: return emptyList()
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

    /** API-র পুরো উত্তর (JSON) থেকে লেখা অংশটুকু নেয়। */
    fun extractText(responseBody: String): String {
        val m = Json.parseOrNull(responseBody).asMap() ?: return ""
        val content = m["content"].asList() ?: return ""
        return content.mapNotNull { it.asMap()?.takeIf { b -> b.str("type") == "text" }?.str("text") }
            .joinToString("")
    }

    /** ব্লক করে (থ্রেডে ডাকতে হবে)। ব্যর্থ হলে Exception ছোঁড়ে, বার্তা বাংলায়। */
    fun generate(apiKey: String, model: String, topic: String, count: Int, level: String): List<Question> {
        if (apiKey.isBlank()) throw IllegalStateException("সেটিংসে API কী দিন")
        val conn = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 90000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            conn.setRequestProperty("x-api-key", apiKey.trim())
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            conn.outputStream.use { it.write(buildRequestBody(model.ifBlank { DEFAULT_MODEL }, buildPrompt(topic, count, level)).toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = Json.parseOrNull(body).asMap()?.get("error").asMap()?.str("message") ?: "HTTP $code"
                throw IllegalStateException(
                    when (code) {
                        401 -> "API কী ভুল"
                        429 -> "অনেক বেশি অনুরোধ, একটু পরে চেষ্টা করুন"
                        else -> msg
                    },
                )
            }
            val qs = parseQuestions(extractText(body))
            if (qs.isEmpty()) throw IllegalStateException("প্রশ্ন বানানো যায়নি, আবার চেষ্টা করুন")
            return qs
        } finally {
            conn.disconnect()
        }
    }
}
