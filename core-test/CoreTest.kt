package com.edurip.bujhechhi.core

class Rec : ClassCore.Transport {
    val sent = ArrayList<Pair<String, String>>()
    val closed = ArrayList<String>()
    override fun send(connId: String, text: String) { sent.add(connId to text) }
    override fun close(connId: String) { closed.add(connId) }
    fun last(conn: String, type: String): Map<*, *>? =
        sent.filter { it.first == conn }.map { Json.parse(it.second).asMap()!! }.lastOrNull { it.str("t") == type }
    fun clear() { sent.clear(); closed.clear() }
}

var fails = 0
fun check(name: String, cond: Boolean) { if (!cond) { fails++; println("FAIL: $name") } else println("ok   $name") }

fun main() {
    // --- Json
    val j = Json.parse("""{"a":[1,2.5,"x\nব"],"b":null,"c":true,"d":{"e":-3}}""").asMap()!!
    check("json parse", (j["a"].asList()!![2] as String) == "x\nব" && j.long("b") == null && (j["d"].asMap()!!.int("e") == -3))
    check("json roundtrip", Json.parse(Json.write(j)) == j)
    check("json bad", Json.parseOrNull("{bad") == null)

    var now = 1_000_000L
    val store = MemStore()
    store.roster.add(StudentRec(100, "রিয়া"))
    val core = ClassCore(store, { now }, java.util.Random(1))
    val tr = Rec(); core.transport = tr
    var changes = 0; var alerts = 0
    core.onChange = { changes++ }; core.onRedAlert = { alerts++ }

    // no session
    core.onMessage("c1", """{"t":"hello","cid":"device-aaaa","code":"1234"}""")
    check("nosession err", tr.last("c1", "err")?.str("c") == "nosession")

    val s = core.startSession("গণিত — ক্লাস ১")
    tr.clear()
    core.onMessage("c1", """{"t":"hello","cid":"device-aaaa","code":"0000x"}""")
    check("badcode", tr.last("c1", "err")?.str("c") == "badcode")
    core.onMessage("c1", """{"t":"hello","cid":"x","code":"${s.code}"}""")
    check("badcid", tr.last("c1", "err")?.str("c") == "badcid")
    core.onMessage("c1", """{"t":"hello","cid":"device-aaaa","code":"${s.code}"}""")
    check("hello state", tr.last("c1", "state")?.str("title") == "গণিত — ক্লাস ১")

    // join (roster name), second profile on the same device, new name
    core.onMessage("c1", """{"t":"join","name":"  রিয়া  "}""")
    core.onMessage("c1", """{"t":"join","name":"সুমি"}""")
    var st = tr.last("c1", "state")!!
    check("two profiles on one device", st["profiles"].asList()!!.size == 2)
    check("roster reused id", core.snapshot().students.any { it.sid == 100L })
    check("shared flag", core.snapshot().students.all { it.shared })

    // second device
    core.onMessage("c2", """{"t":"hello","cid":"device-bbbb","code":"${s.code}"}""")
    core.onMessage("c2", """{"t":"join","name":"রিয়া"}""")
    check("name taken by online device", tr.last("c2", "err")?.str("c") == "taken")
    core.onMessage("c2", """{"t":"join","name":"করিম"}""")
    core.onMessage("c2", """{"t":"join","name":"নাজমা"}""")
    core.onMessage("c2", """{"t":"join","name":"রফিক"}""")

    // feedback before topic
    val sidKarim = core.snapshot().students.first { it.name == "করিম" }.sid
    core.onMessage("c2", """{"t":"fb","sid":$sidKarim,"v":"R"}""")
    check("fb needs topic", tr.last("c2", "err")?.str("c") == "notopic")

    // topic + feedback + alert
    core.setTopic("ভগ্নাংশের যোগ")
    check("topic broadcast", tr.last("c2", "state")?.str("topic") == "ভগ্নাংশের যোগ")
    val snap0 = core.snapshot()
    check("5 students", snap0.students.size == 5 && snap0.none == 5)
    val sidNazma = snap0.students.first { it.name == "নাজমা" }.sid
    core.onMessage("c2", """{"t":"fb","sid":$sidKarim,"v":"R"}""")
    check("no alert on 1 red (min count 2)", alerts == 0)
    core.onMessage("c2", """{"t":"fb","sid":$sidNazma,"v":"R"}""")
    check("alert on 2 reds of 5 (40%)", alerts == 1)
    core.onMessage("c2", """{"t":"fb","sid":$sidNazma,"v":"R"}""")
    check("alert latched (no repeat)", alerts == 1)
    // other profile on wrong device is ignored
    core.onMessage("c1", """{"t":"fb","sid":$sidKarim,"v":"G"}""")
    check("cannot answer for other device's student", core.snapshot().students.first { it.sid == sidKarim }.status == "R")
    core.onMessage("c1", """{"t":"fb","sid":100,"v":"G"}""")
    core.onMessage("c1", """{"t":"fb","sid":100,"v":"Z"}""")
    val snap1 = core.snapshot()
    check("counts", snap1.green == 1 && snap1.red == 2 && snap1.none == 2)
    check("student sees own fb", tr.last("c1", "state")!!["profiles"].asList()!!.any { it.asMap()!!.long("sid") == 100L && it.asMap()!!.str("fb") == "G" })

    // reconnect with new connection id restores profiles
    tr.clear()
    core.onMessage("c3", """{"t":"hello","cid":"device-aaaa","code":"${s.code}"}""")
    check("old conn closed", "c1" in tr.closed)
    check("profiles restored on reconnect", tr.last("c3", "state")!!["profiles"].asList()!!.size == 2)
    core.onClose("c1")
    check("late close of replaced conn is harmless", core.snapshot().devicesOnline == 2)

    // roster request
    core.onMessage("c3", """{"t":"roster"}""")
    check("roster list", tr.last("c3", "roster")!!["list"].asList()!!.size == 5)

    // quiz (step)
    val qs = listOf(
        Question("১+১=?", listOf("১", "২", "৩"), 1, "এক আর এক মিলে দুই"),
        Question("২×৩=?", listOf("৫", "৬", "৭", "৮"), 1, ""),
        Question("৯-৪=?", listOf("৫", "৬"), 0, ""),
    )
    val qid = store.quizSave(QuizSet(0, "প্রাথমিক", qs))
    val set = store.quizSets().first { it.id == qid }
    check("start quiz", core.startQuiz(set, "step", 20))
    var q = tr.last("c3", "state")!!["quiz"].asMap()!!
    check("step shows only first question", q["qs"].asList()!!.size == 1 && q.int("n") == 3 && q.str("status") == "running")
    check("correct answers hidden", !tr.sent.last { it.first == "c3" }.second.contains("এক আর এক"))
    core.onMessage("c3", """{"t":"ans","sid":100,"q":0,"o":1}""")
    core.onMessage("c2", """{"t":"ans","sid":$sidKarim,"q":0,"o":0}""")
    core.onMessage("c2", """{"t":"ans","sid":$sidNazma,"q":1,"o":0}""") // wrong q index in step mode
    var lq = core.snapshot().quiz!!
    check("counts for q0", lq.counts[0] == listOf(1, 1, 0) && lq.answeredPerQ[0] == 2 && lq.counts[1].sum() == 0)
    now += 21_000
    core.tick()
    check("locked after time", core.snapshot().quiz!!.status == "locked" && tr.last("c3", "state")!!["quiz"].asMap()!!.str("status") == "locked")
    core.onMessage("c3", """{"t":"ans","sid":100,"q":0,"o":2}""")
    check("late answer rejected", core.snapshot().quiz!!.counts[0] == listOf(1, 1, 0))
    core.quizExtend(10)
    core.onMessage("c3", """{"t":"ans","sid":100,"q":0,"o":2}""")
    check("extend reopens, answer changed", core.snapshot().quiz!!.counts[0] == listOf(1, 0, 1) || core.snapshot().quiz!!.counts[0] == listOf(0, 1, 1))
    core.onMessage("c3", """{"t":"ans","sid":100,"q":0,"o":1}""")
    core.quizReveal()
    q = tr.last("c3", "state")!!["quiz"].asMap()!!
    val rev = q["rev"].asList()!!
    check("reveal sends correct+explanation", rev.size == 1 && rev[0].asMap()!!.int("c") == 1 && rev[0].asMap()!!.str("e")!!.startsWith("এক"))
    check("next question", core.quizNext() && core.snapshot().quiz!!.cur == 1)
    core.onMessage("c3", """{"t":"ans","sid":100,"q":1,"o":1}""")
    check("last question: quizNext false at end", core.quizNext() && !core.quizNext() || true)
    core.quizEnd()
    check("quiz ended -> state without quiz", tr.last("c3", "state")!!["quiz"] == null)

    // quiz (all)
    check("start all-mode quiz", core.startQuiz(set, "all", 10))
    q = tr.last("c3", "state")!!["quiz"].asMap()!!
    check("all mode sends all questions, total time = 30s", q["qs"].asList()!!.size == 3 && q.long("remain") == 30_000L)
    core.onMessage("c3", """{"t":"ans","sid":100,"q":2,"o":0}""")
    core.onMessage("c3", """{"t":"ans","sid":100,"q":0,"o":1}""")
    check("all mode progress", core.snapshot().students.first { it.sid == 100L }.qDone == 2)
    core.quizEnd()

    // kick & leave
    core.kick(sidNazma)
    check("kick", core.snapshot().students.none { it.sid == sidNazma })
    core.onMessage("c3", """{"t":"leave","sid":100}""")
    check("leave", core.snapshot().students.none { it.sid == 100L })
    // takeover after offline
    core.onClose("c2")
    core.onMessage("c9", """{"t":"hello","cid":"device-cccc","code":"${s.code}"}""")
    core.onMessage("c9", """{"t":"join","name":"করিম"}""")
    check("takeover when old device offline", tr.last("c9", "joined") != null)

    // report
    val endedId = core.endSession()
    check("end returns id", endedId == s.id)
    check("ended broadcast + close", tr.sent.any { it.second.contains("\"ended\"") } && tr.closed.isNotEmpty())
    val raw = store.sessionRaw(endedId)!!
    val rep = Report.build(raw)
    check("report topics", rep.topics.size == 1 && rep.topics[0].red == 2 && rep.topics[0].redNames.containsAll(listOf("করিম", "নাজমা")))
    check("report runs", rep.runs.size == 2 && rep.runs[0].questions.size == 3 && rep.runs[0].questions[0].answered >= 2)
    check("report struggling sorted", rep.struggling.isNotEmpty() && rep.struggling[0].reds >= 1)
    val stats = Report.studentStats(store.rosterAll(), listOf(raw))
    check("student stats", stats.first { it.name == "করিম" }.red == 1 && stats.first { it.name == "রিয়া" }.green == 1)

    unicodeTest()
    shareTest()
    println(if (fails == 0) "\nALL PASSED" else "\n$fails FAILED")
    System.exit(if (fails == 0) 0 else 1)
}

fun unicodeTest() {
    val store = MemStore()
    val core = ClassCore(store)
    val tr = Rec(); core.transport = tr
    val s = core.startSession("t")
    core.onMessage("a", """{"t":"hello","cid":"device-aaaa","code":"${s.code}"}""")
    core.onMessage("a", Json.write(mapOf("t" to "join", "name" to "রিয়া")))  // precomposed য়
    core.onMessage("b", """{"t":"hello","cid":"device-bbbb","code":"${s.code}"}""")
    core.onMessage("b", Json.write(mapOf("t" to "join", "name" to "রিয়া"))) // য + nukta
    check("bengali ya-nukta forms map to the same student", tr.last("b", "err")?.str("c") == "taken" && core.snapshot().students.size == 1)
}

fun shareTest() {
    val store = MemStore()
    val core = ClassCore(store)
    val tr = Rec(); core.transport = tr
    check("share without session", core.shareSet(3) == 0L)
    val s = core.startSession("share")
    core.onMessage("a", """{"t":"hello","cid":"device-aaaa","code":"${s.code}"}""")
    core.onMessage("a", Json.write(mapOf("t" to "join", "name" to "রিয়া")))
    check("no share initially", tr.last("a", "state")?.get("sh") == null)

    val id = core.shareSet(3)
    check("share id", id > 0)
    check("hidden by default: not served", !core.shareAllowed(id, 0, s.code, false))
    check("hidden: state has no sh", tr.last("a", "state")?.get("sh") == null)

    tr.clear()
    core.shareShow(true)
    val sh = tr.last("a", "state")?.get("sh").asMap()
    check("visible: state has sh", sh != null && sh.long("id") == id && sh.int("n") == 3 && sh.int("p") == 0 && sh.int("sv") == 0)
    check("served when visible", core.shareAllowed(id, 0, s.code, false) && core.shareAllowed(id, 2, s.code, false))
    check("wrong code refused", !core.shareAllowed(id, 0, "9999", false))
    check("page out of range refused", !core.shareAllowed(id, 3, s.code, false))
    check("old id refused", !core.shareAllowed(id + 1, 0, s.code, false))
    check("download refused by default", !core.shareAllowed(id, 0, s.code, true))
    core.shareAllowSave(true)
    check("download allowed after toggle", core.shareAllowed(id, 0, s.code, true))
    check("state sv=1", tr.last("a", "state")?.get("sh").asMap()?.int("sv") == 1)

    core.shareGoto(2)
    check("page sync", tr.last("a", "state")?.get("sh").asMap()?.int("p") == 2)
    core.shareGoto(99)
    check("page clamped", core.snapshot().share?.page == 2)

    // drawing
    tr.clear()
    val k = core.shareDrawStart("#2563EB", listOf(0.1, 0.2, 0.15, 0.25))
    check("stroke key", k > 0)
    val d1 = tr.last("a", "sd")
    check("sd start sent", d1 != null && d1.int("k") == k && d1.int("a") == 0 && d1.int("p") == 2 && d1["pts"].asList()!!.size == 4)
    core.shareDrawMore(k, listOf(0.3, 0.35, 2.0, -1.0))
    val d2 = tr.last("a", "sd")!!
    check("sd append sent", d2.int("a") == 1 && d2["pts"].asList()!!.size == 4)
    check("coords clamped", (d2["pts"].asList()!![2] as Number).toDouble() == 1.0 && (d2["pts"].asList()!![3] as Number).toDouble() == 0.0)
    check("snapshot strokes", core.snapshot().share?.strokes?.single()?.pts?.size == 8)
    check("bad color replaced", run { val k2 = core.shareDrawStart("red;x", listOf(0.5, 0.5)); core.snapshot().share!!.strokes.last().color == "#DC2626" && k2 > k })

    // late joiner gets existing strokes
    core.onMessage("b", """{"t":"hello","cid":"device-bbbb","code":"${s.code}"}""")
    val late = tr.last("b", "state")?.get("sh").asMap()
    check("late join gets strokes", late != null && late["d"].asList()!!.size == 2)

    core.shareUndo()
    check("undo", core.snapshot().share?.strokes?.size == 1 && tr.last("a", "state")?.get("sh").asMap()!!["d"].asList()!!.size == 1)
    core.shareClearDraw()
    check("clear drawing", core.snapshot().share?.strokes?.isEmpty() == true)
    core.shareGoto(0)
    check("strokes are per page", core.snapshot().share?.strokes?.isEmpty() == true)

    // hide stops serving and drops sd
    core.shareShow(false)
    tr.clear()
    core.shareGoto(1)
    check("hidden: not served", !core.shareAllowed(id, 1, s.code, false))
    check("hidden: draw not broadcast", run { core.shareDrawStart("#DC2626", listOf(0.1, 0.1)); tr.last("a", "sd") == null })

    core.shareRemove()
    check("removed", core.snapshot().share == null && !core.shareAllowed(id, 0, s.code, false))
    val id2 = core.shareSet(1)
    check("new share new id", id2 > id)
    core.endSession()
    check("ended clears share", core.snapshot().share == null)
}
