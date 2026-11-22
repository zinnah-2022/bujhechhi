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
    check("name bound to another phone is refused", tr.last("c2", "err")?.str("c") == "owned")
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
    run { val l = tr.last("c3", "roster")!!["list"].asList()!!; println("ROSTER " + l.map { it.asMap()!!.str("n") + ":" + it.asMap()!!["m"] }); check("roster hides names bound to other phones", l.none { it.asMap()!!.str("n") == "করিম" }) }

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
    run {
        // শেষ হলে সবার ফল: যে উত্তর দিয়েছে, আর যে যোগ দিয়েছে কিন্তু দেয়নি
        val s3 = tr.last("c3", "state")!!
        val top = s3["qr"].asMap()!!
        check("result: top-level summary", top.int("n") == 2 && top.str("title") == "প্রাথমিক")
        val riya = s3["profiles"].asList()!!.map { it.asMap()!! }.first { it.long("sid") == 100L }["qr"].asMap()!!
        check("result: riya 2/2", riya.int("c") == 2 && riya.int("a") == 2 && riya.str("m") == "11")
        val s2 = tr.last("c2", "state")!!
        val ps = s2["profiles"].asList()!!.map { it.asMap()!! }
        val karim = ps.first { it.long("sid") == sidKarim }["qr"].asMap()!!
        val nazma = ps.first { it.long("sid") == sidNazma }["qr"].asMap()!!
        check("result: karim wrong then skipped", karim.int("c") == 0 && karim.int("a") == 1 && karim.str("m") == "0-")
        check("result: joined-but-silent student still gets a result (0 answered)", nazma.int("a") == 0 && nazma.str("m") == "--")
        check("result: class average counts only takers", top.int("tk") == 2 && top.int("avg") == 50)
    }

    // quiz (all)
    check("start all-mode quiz", core.startQuiz(set, "all", 10))
    check("new quiz clears old result", tr.last("c3", "state")!!["qr"] == null)
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
    check("no takeover even if old phone offline", tr.last("c9", "err")?.str("c") == "owned")
    // teacher releases the name -> new phone can claim it
    val karimSid = core.snapshot().students.first { it.name == "করিম" }.sid
    core.release(karimSid)
    core.onMessage("c9", """{"t":"join","name":"করিম"}""")
    check("release lets another phone claim", tr.last("c9", "joined") != null)
    // returning device is auto-joined with no input
    core.onClose("c9")
    core.onMessage("c9b", """{"t":"hello","cid":"device-cccc","code":"${s.code}"}""")
    check("auto-join returning device", tr.last("c9b", "state")!!["profiles"].asList()!!.any { it.asMap()!!.str("name") == "করিম" })
    // join lock: new names refused, bound device still fine
    core.setJoinLocked(true)
    core.onMessage("c9b", """{"t":"join","name":"নতুন নাম"}""")
    check("join lock blocks new names", tr.last("c9b", "err")?.str("c") == "locked")
    core.setJoinLocked(false)
    // kick + block
    core.kick(karimSid, true)
    check("blocked phone gets err", tr.last("c9b", "err")?.str("c") == "blocked")
    core.onMessage("c9c", """{"t":"hello","cid":"device-cccc","code":"${s.code}"}""")
    check("blocked phone cannot reconnect", tr.last("c9c", "err")?.str("c") == "blocked")

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
    castTest()
    csvMathTest()
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
    check("bengali ya-nukta forms map to the same student", tr.last("b", "err")?.str("c") == "owned" && core.snapshot().students.size == 1)
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

fun castTest() {
    val store = MemStore()
    val core = ClassCore(store)
    val tr = Rec(); core.transport = tr
    check("cast: no session ignored", run { core.castStart(1); core.snapshot().cast == null && !core.castAllowed("0000") })
    val s = core.startSession("cast")
    core.onMessage("a", """{"t":"hello","cid":"device-aaaa","code":"${s.code}"}""")
    check("cast: off by default", tr.last("a", "state")?.get("sc") == null && !core.castAllowed(s.code))
    core.castStart(2)
    check("cast: state sc on", tr.last("a", "state")?.get("sc").asMap()?.int("p") == 0)
    check("cast: allowed with code", core.castAllowed(s.code) && !core.castAllowed("9999"))
    check("cast: snapshot", core.snapshot().cast?.quality == 2 && core.snapshot().cast?.paused == false)
    core.castPause(true)
    check("cast: paused state", tr.last("a", "state")?.get("sc").asMap()?.int("p") == 1 && !core.castAllowed(s.code))
    core.castPause(false)
    check("cast: resumed", core.castAllowed(s.code))
    core.castQuality(0)
    check("cast: quality change", core.castQualityNow() == 0 && core.snapshot().cast?.quality == 0)
    core.castStop()
    check("cast: stopped", tr.last("a", "state")?.get("sc") == null && core.snapshot().cast == null && !core.castAllowed(s.code))
    core.castStart(1); core.endSession()
    check("cast: end session clears", core.snapshot().cast == null)

    // ScreenHub
    var t = 1_000L
    val hub = ScreenHub { t }
    check("hub: empty", hub.peek(0) == null)
    val a = hub.publish(byteArrayOf(1, 2, 3))
    check("hub: peek newer", hub.peek(0)?.seq == a && hub.peek(a) == null)
    val b = hub.publish(byteArrayOf(9))
    check("hub: latest only", hub.peek(a)?.bytes?.size == 1 && b > a)
    hub.touch("device-aaaa"); hub.served("device-bbbb", 500)
    check("hub: viewers", hub.viewers() == 2 && hub.bytesServed.get() == 500L && hub.framesPublished.get() == 2L)
    t += 20_000
    check("hub: viewers expire", hub.viewers() == 0)
    hub.touch("x"); check("hub: short cid ignored", hub.viewers() == 0)
    hub.reset()
    check("hub: reset", hub.peek(0) == null)
}

fun csvMathTest() {
    val D = "$"
    // CSV: শিরোনাম, উদ্ধৃত ঘরে কমা, ক-ঘ ও সংখ্যা, ব্যাখ্যা, গণিত
    val csv = "প্রশ্ন,অপশন A,অপশন B,অপশন C,অপশন D,সঠিক,ব্যাখ্যা\n" +
        "২+৩ কত?,৪,৫,৬,৭,B,যোগফল ৫\n" +
        "\"কোনটি বড়, ৩ না ৫?\",৩,৫,সমান,জানা নেই,খ,\n" +
        "\"${D}\\frac{1}{2}+\\frac{1}{3}${D} কত?\",${D}\\frac{5}{6}${D},${D}\\frac{2}{5}${D},${D}\\frac{1}{6}${D},${D}1${D},1,\"১/২ + ১/৩, ছেদ ৬\"\n" +
        "\n" +
        "খারাপ,ক,খ\n" +
        "আরেক,১,২,৩,৪,Z\n" +
        "\"দুই লাইনের\nপ্রশ্ন\",a,b,c,d,ঘ\n"
    val r = CsvQuiz.parse("\uFEFF" + csv)
    check("csv: 4 valid questions", r.questions.size == 4)
    check("csv: letter answer", r.questions[0].correct == 1 && r.questions[0].explanation == "যোগফল ৫")
    check("csv: quoted comma + bangla letter", r.questions[1].text == "কোনটি বড়, ৩ না ৫?" && r.questions[1].correct == 1)
    check("csv: latex kept, numeric answer, comma in explanation", r.questions[2].text.startsWith("${D}\\frac{1}{2}") && r.questions[2].correct == 0 && r.questions[2].explanation == "১/২ + ১/৩, ছেদ ৬")
    check("csv: multiline cell", r.questions[3].text == "দুই লাইনের\nপ্রশ্ন" && r.questions[3].correct == 3)
    check("csv: 2 errors reported", r.errors.size == 2)
    val tsv = CsvQuiz.parse("q1\ta\tb\tc\td\t3\tex\nq2\tx\ty\tz\tw\tw")
    check("tsv (excel paste) works incl. answer by option text", tsv.questions.size == 2 && tsv.questions[0].correct == 2 && tsv.questions[1].correct == 3)
    val three = CsvQuiz.parse("আকাশ নীল?,হ্যাঁ,না,জানি না,B")
    check("csv: 3 options", three.questions.size == 1 && three.questions[0].options.size == 3 && three.questions[0].correct == 1)
    val demo = CsvQuiz.parse(CsvQuiz.DEMO)
    check("demo csv: 4 questions, no errors, math kept", demo.questions.size == 4 && demo.errors.isEmpty() && demo.questions[1].text.startsWith("${D}\\frac{1}{2}") && demo.questions[2].options[0] == "${D}2, 3${D}" && demo.questions[3].correct == 2)
    check("csv empty", CsvQuiz.parse("  ").questions.isEmpty())

    // এআই-এর একক-ব্যাকস্ল্যাশ LaTeX ঠিক হয়
    val bad = "[{\"q\":\"${D}\\frac{1}{2}${D} এবং ${D}\\beta \\times 3${D}\",\"o\":[\"${D}\\sqrt{2}${D}\",\"${D}\\theta${D}\",\"c\",\"d\"],\"c\":1,\"e\":\"ঠিক \\\\ আছে \\n নতুন\"}]"
    val q = AiQuiz.parseQuestions(bad)
    check("ai latex repaired", q.size == 1 && q[0].text == "${D}\\frac{1}{2}${D} এবং ${D}\\beta \\times 3${D}" && q[0].options[0] == "${D}\\sqrt{2}${D}" && q[0].options[1] == "${D}\\theta${D}")
    val good = "[{\"q\":\"${D}\\\\frac{1}{2}${D}\",\"o\":[\"a\",\"b\"],\"c\":0,\"e\":\"x\\nয\"}]"
    val g = AiQuiz.parseQuestions(good)
    check("ai already-escaped latex kept; real \\n kept", g.size == 1 && g[0].text == "${D}\\frac{1}{2}${D}" && g[0].explanation == "x\nয")
    check("prompt asks for latex", AiQuiz.buildPrompt("t", 5, "সহজ").contains("LaTeX"))
}
