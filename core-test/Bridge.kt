package com.edurip.bujhechhi.core

/**
 * পরীক্ষার ব্রিজ: Node-এর WebSocket সার্ভার আর আসল ClassCore-এর মধ্যে stdin/stdout দিয়ে যোগাযোগ।
 * লাইন ফরম্যাট (ইনপুট):  M <conn> <json> | X <conn> | T <command...>
 * লাইন ফরম্যাট (আউটপুট): S <conn> <json> | Z <conn> | R <json>
 */
fun main() {
    val store = MemStore()
    listOf("রিয়া", "সুমি", "করিম", "নাজমা", "রফিক", "তানভীর").forEachIndexed { i, n -> store.roster.add(StudentRec(100L + i, n)) }
    val core = ClassCore(store)
    val lock = Any()
    core.transport = object : ClassCore.Transport {
        override fun send(connId: String, text: String) { synchronized(lock) { println("S $connId $text"); System.out.flush() } }
        override fun close(connId: String) { synchronized(lock) { println("Z $connId"); System.out.flush() } }
    }
    val quiz = QuizSet(1, "ভগ্নাংশ কুইজ", listOf(
        Question("১/২ + ১/২ = ?", listOf("১/৪", "১", "২", "০"), 1, "দুই অর্ধেক মিলে পূর্ণ এক।"),
        Question("৩/৪ এর হর কত?", listOf("৩", "৪", "৭", "১"), 1, "নিচের সংখ্যাটাই হর।"),
        Question("কোনটি সবচেয়ে বড়?", listOf("১/২", "১/৩", "১/৪", "১/৫"), 0, ""),
    ))
    Thread {
        while (true) { Thread.sleep(1000); core.tick() }
    }.apply { isDaemon = true }.start()
    fun reply(s: String) { synchronized(lock) { println("R $s"); System.out.flush() } }
    val reader = System.`in`.bufferedReader(Charsets.UTF_8)
    while (true) {
        val line = reader.readLine() ?: break
        val parts = line.split(" ", limit = 3)
        when (parts[0]) {
            "M" -> core.onMessage(parts[1], parts[2])
            "X" -> core.onClose(parts[1])
            "T" -> {
                val cmd = line.substring(2).split(" ", limit = 2)
                when (cmd[0]) {
                    "start" -> { val s = core.startSession(cmd.getOrElse(1) { "ক্লাস" }); reply(Json.write(mapOf("code" to s.code, "id" to s.id))) }
                    "topic" -> { core.setTopic(cmd[1]); reply("{}") }
                    "quiz" -> { val a = cmd[1].split(" "); reply(Json.write(mapOf("ok" to core.startQuiz(quiz, a[0], a[1].toInt())))) }
                    "reveal" -> { core.quizReveal(); reply("{}") }
                    "next" -> reply(Json.write(mapOf("ok" to core.quizNext())))
                    "quizend" -> { core.quizEnd(); reply("{}") }
                    "extend" -> { core.quizExtend(cmd[1].toInt()); reply("{}") }
                    "end" -> reply(Json.write(mapOf("id" to core.endSession())))
                    "shareset" -> reply(Json.write(mapOf("id" to core.shareSet(cmd[1].toInt()))))
                    "shareshow" -> { core.shareShow(cmd[1] == "1"); reply("{}") }
                    "sharegoto" -> { core.shareGoto(cmd[1].toInt()); reply("{}") }
                    "sharesave" -> { core.shareAllowSave(cmd[1] == "1"); reply("{}") }
                    "shareremove" -> { core.shareRemove(); reply("{}") }
                    "drawstart" -> { val a = cmd[1].split(" "); reply(Json.write(mapOf("k" to core.shareDrawStart(a[0], a[1].split(",").map { it.toDouble() })))) }
                    "drawmore" -> { val a = cmd[1].split(" "); core.shareDrawMore(a[0].toInt(), a[1].split(",").map { it.toDouble() }); reply("{}") }
                    "undo" -> { core.shareUndo(); reply("{}") }
                    "cleardraw" -> { core.shareClearDraw(); reply("{}") }
                    "imgok" -> { val a = cmd[1].split(" "); reply(Json.write(mapOf("ok" to core.shareAllowed(a[0].toLong(), a[1].toInt(), a[2], a[3] == "1")))) }
                    "snap" -> {
                        val s = core.snapshot()
                        reply(Json.write(mapOf(
                            "g" to s.green, "y" to s.yellow, "r" to s.red, "none" to s.none, "online" to s.devicesOnline,
                            "students" to s.students.map { mapOf("n" to it.name, "st" to it.status, "on" to it.online, "sh" to it.shared, "qd" to it.qDone, "qo" to it.qOf) },
                            "quiz" to s.quiz?.let { mapOf("status" to it.status, "cur" to it.cur, "counts" to it.counts) },
                        )))
                    }
                    "report" -> {
                        val id = cmd[1].toLong()
                        val r = Report.build(store.sessionRaw(id)!!)
                        reply(Json.write(mapOf(
                            "topics" to r.topics.map { mapOf("t" to it.text, "g" to it.green, "y" to it.yellow, "r" to it.red, "n" to it.none, "names" to it.redNames) },
                            "runs" to r.runs.map { run -> mapOf("title" to run.title, "q" to run.questions.map { mapOf("pct" to it.correctPct, "n" to it.answered) }, "scores" to run.scores.map { mapOf("n" to it.name, "c" to it.correct, "t" to it.total) }) },
                            "struggling" to r.struggling.map { mapOf("n" to it.name, "r" to it.reds, "w" to it.wrongs) },
                        )))
                    }
                }
            }
        }
    }
}
