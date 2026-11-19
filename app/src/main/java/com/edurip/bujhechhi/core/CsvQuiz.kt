package com.edurip.bujhechhi.core

/**
 * CSV (বা Excel থেকে কপি করা ট্যাব-ভাগ করা) লেখা থেকে প্রশ্ন বানায়।
 * কলাম: প্রশ্ন, অপশন A, B, C, D, সঠিক উত্তর, ব্যাখ্যা(ঐচ্ছিক)।
 * সঠিক উত্তর: A/B/C/D, a-d, 1-4, ক/খ/গ/ঘ অথবা অপশনের হুবহু লেখা।
 * কমা থাকা ঘর "…" দিয়ে ঘিরতে হয় (গাণিতিক $\frac{1}{2}$ এ কমা না থাকলে লাগে না)।
 */
object CsvQuiz {
    class Result(val questions: List<Question>, val errors: List<String>)

    fun parse(raw: String): Result {
        var text = raw.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        if (text.isBlank()) return Result(emptyList(), listOf("কিছু লেখা নেই"))
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
        val delim = when {
            firstLine.contains('\t') -> '\t'
            firstLine.count { it == ',' } >= firstLine.count { it == ';' } -> ','
            else -> ';'
        }
        val rows = rows(text, delim)
        val qs = ArrayList<Question>()
        val errs = ArrayList<String>()
        var n = 0
        rows.forEachIndexed { idx, row ->
            val cells = row.map { it.trim() }
            if (cells.all { it.isEmpty() }) return@forEachIndexed
            if (idx == 0 && looksLikeHeader(cells)) return@forEachIndexed
            n++
            val label = "প্রশ্ন ${n}"
            if (cells.size < 4) { errs.add("$label: কমপক্ষে প্রশ্ন, ২টি অপশন ও সঠিক উত্তর লাগবে"); return@forEachIndexed }
            val q = cells[0]
            // শেষ কলামগুলো: [..., সঠিক, ব্যাখ্যা?]। অপশনের সংখ্যা ২..৬; ৪-অপশনের ফরম্যাট ধরেই আগে চেষ্টা করি।
            var optEnd = 5.coerceAtMost(cells.size)           // opts = cells[1 until optEnd]
            var opts = cells.subList(1, optEnd)
            var ansCell = cells.getOrNull(optEnd).orEmpty()
            var expl = cells.drop(optEnd + 1).joinToString(", ").trim()
            var ci = answerIndex(ansCell, opts)
            if (ci < 0 && cells.size >= 4) {
                // অপশন ২-৩টি হলে
                for (k in 3 downTo 2) {
                    val o = cells.subList(1, (1 + k).coerceAtMost(cells.size))
                    val a = cells.getOrNull(1 + k).orEmpty()
                    val c = answerIndex(a, o)
                    if (c >= 0) { opts = o; ci = c; expl = cells.drop(2 + k).joinToString(", ").trim(); break }
                }
            }
            val kept = opts.filter { it.isNotEmpty() }
            if (q.isEmpty()) { errs.add("$label: প্রশ্ন ফাঁকা"); return@forEachIndexed }
            if (opts.size < 2 || kept.size < opts.size) { errs.add("$label: অপশন ফাঁকা বা কম"); return@forEachIndexed }
            if (ci < 0) { errs.add("$label: সঠিক উত্তর চেনা যায়নি (A-D বা ১-৪ লিখুন)"); return@forEachIndexed }
            qs.add(Question(q, opts, ci, expl))
        }
        if (qs.isEmpty() && errs.isEmpty()) errs.add("কোনো প্রশ্ন পাওয়া যায়নি")
        return Result(qs, errs)
    }

    private fun looksLikeHeader(c: List<String>): Boolean {
        val first = c[0].lowercase().trim().trimEnd(':', '*')
        val names = setOf("question", "questions", "q", "প্রশ্ন", "প্রশ্নসমূহ", "question text", "sl", "no", "ক্রমিক")
        val ans = setOf("answer", "ans", "correct", "correct answer", "সঠিক", "সঠিক উত্তর", "উত্তর")
        return first in names || (c.size >= 6 && c[5].lowercase().trim() in ans)
    }

    private fun answerIndex(a: String, opts: List<String>): Int {
        val s = a.trim()
        if (s.isEmpty()) return -1
        val t = s.trimEnd('.', ')', ':').trim()
        val bn = "০১২৩৪৫"
        if (t.length == 1) {
            val ch = t[0]
            val i = when (ch) {
                in 'A'..'F' -> ch - 'A'
                in 'a'..'f' -> ch - 'a'
                in '1'..'6' -> ch - '1'
                in '০'..'৫' -> bn.indexOf(ch) - 1
                'ক' -> 0; 'খ' -> 1; 'গ' -> 2; 'ঘ' -> 3; 'ঙ' -> 4
                else -> -1
            }
            if (i in opts.indices) return i
        }
        // "Option B" / "অপশন খ" ইত্যাদি
        val last = t.takeLast(1)
        if (t.length > 1 && (t.lowercase().startsWith("option") || t.startsWith("অপশন"))) return answerIndex(last, opts)
        return opts.indexOfFirst { it.equals(s, ignoreCase = true) }
    }

    /** RFC 4180 ধাঁচের পার্সার: "…" এর ভেতরে কমা, নতুন লাইন ও "" চলে। */
    private fun rows(text: String, d: Char): List<List<String>> {
        val out = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cur = StringBuilder()
        var inQ = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                inQ && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cur.append('"'); i++ }
                ch == '"' && (cur.isEmpty() || inQ) -> inQ = !inQ
                !inQ && ch == d -> { row.add(cur.toString()); cur.setLength(0) }
                !inQ && ch == '\n' -> { row.add(cur.toString()); cur.setLength(0); out.add(row); row = ArrayList() }
                else -> cur.append(ch)
            }
            i++
        }
        if (cur.isNotEmpty() || row.isNotEmpty()) { row.add(cur.toString()); out.add(row) }
        return out
    }
}
