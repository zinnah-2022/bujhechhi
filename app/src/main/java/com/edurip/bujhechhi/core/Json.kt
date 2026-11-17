package com.edurip.bujhechhi.core

/**
 * ছোট নিজস্ব JSON পার্সার/রাইটার। কোনো বাইরের লাইব্রেরি লাগে না, তাই Android ও সাধারণ JVM
 * (টেস্ট) দুই জায়গাতেই একই কোড চলে।
 *
 * পার্স করলে পাওয়া যায়: Map<String, Any?>, List<Any?>, String, Long, Double, Boolean, null
 */
object Json {

    fun parse(s: String): Any? {
        val p = Parser(s)
        p.skipWs()
        val v = p.value()
        p.skipWs()
        if (p.i != s.length) throw IllegalArgumentException("trailing data at ${p.i}")
        return v
    }

    fun parseOrNull(s: String): Any? = try {
        parse(s)
    } catch (e: Exception) {
        null
    }

    fun write(v: Any?): String {
        val sb = StringBuilder()
        w(sb, v)
        return sb.toString()
    }

    private fun w(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> str(sb, v)
            is Boolean -> sb.append(if (v) "true" else "false")
            is Int, is Long, is Short, is Byte -> sb.append(v.toString())
            is Double -> if (v.isNaN() || v.isInfinite()) sb.append("null") else sb.append(v.toString())
            is Float -> if (v.isNaN() || v.isInfinite()) sb.append("null") else sb.append(v.toString())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(',')
                    first = false
                    str(sb, k.toString())
                    sb.append(':')
                    w(sb, value)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (x in v) {
                    if (!first) sb.append(',')
                    first = false
                    w(sb, x)
                }
                sb.append(']')
            }
            is IntArray -> w(sb, v.toList())
            is Array<*> -> w(sb, v.toList())
            else -> str(sb, v.toString())
        }
    }

    private fun str(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c.code < 0x20 || c == ' ' || c == ' ' ->
                    sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(val s: String) {
        var i = 0

        fun skipWs() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++
        }

        fun value(): Any? {
            if (i >= s.length) throw IllegalArgumentException("unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> string()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c in '0'..'9') number() else throw IllegalArgumentException("bad char '$c' at $i")
            }
        }

        private fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) throw IllegalArgumentException("bad literal at $i")
            i += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++ // {
            skipWs()
            if (i < s.length && s[i] == '}') {
                i++
                return m
            }
            while (true) {
                skipWs()
                if (i >= s.length || s[i] != '"') throw IllegalArgumentException("expected key at $i")
                val k = string()
                skipWs()
                if (i >= s.length || s[i] != ':') throw IllegalArgumentException("expected : at $i")
                i++
                skipWs()
                m[k] = value()
                skipWs()
                if (i >= s.length) throw IllegalArgumentException("unterminated object")
                if (s[i] == ',') {
                    i++
                    continue
                }
                if (s[i] == '}') {
                    i++
                    return m
                }
                throw IllegalArgumentException("expected , or } at $i")
            }
        }

        private fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++ // [
            skipWs()
            if (i < s.length && s[i] == ']') {
                i++
                return l
            }
            while (true) {
                skipWs()
                l.add(value())
                skipWs()
                if (i >= s.length) throw IllegalArgumentException("unterminated array")
                if (s[i] == ',') {
                    i++
                    continue
                }
                if (s[i] == ']') {
                    i++
                    return l
                }
                throw IllegalArgumentException("expected , or ] at $i")
            }
        }

        private fun string(): String {
            val sb = StringBuilder()
            i++ // opening quote
            while (true) {
                if (i >= s.length) throw IllegalArgumentException("unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) throw IllegalArgumentException("bad escape")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw IllegalArgumentException("bad unicode escape")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> throw IllegalArgumentException("bad escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun number(): Any {
            val st = i
            if (s[i] == '-') i++
            while (i < s.length && s[i] in '0'..'9') i++
            var isDouble = false
            if (i < s.length && s[i] == '.') {
                isDouble = true
                i++
                while (i < s.length && s[i] in '0'..'9') i++
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                isDouble = true
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                while (i < s.length && s[i] in '0'..'9') i++
            }
            val t = s.substring(st, i)
            return if (isDouble) t.toDouble() else (t.toLongOrNull() ?: t.toDouble())
        }
    }
}

// ---- Map/List থেকে নিরাপদে মান নেওয়ার সাহায্যকারী ----

fun Any?.asMap(): Map<*, *>? = this as? Map<*, *>
fun Any?.asList(): List<*>? = this as? List<*>

fun Map<*, *>.str(key: String): String? = (this[key] as? String)
fun Map<*, *>.long(key: String): Long? = when (val v = this[key]) {
    is Long -> v
    is Int -> v.toLong()
    is Double -> v.toLong()
    else -> null
}

fun Map<*, *>.int(key: String): Int? = long(key)?.let { if (it > Int.MAX_VALUE || it < Int.MIN_VALUE) null else it.toInt() }
