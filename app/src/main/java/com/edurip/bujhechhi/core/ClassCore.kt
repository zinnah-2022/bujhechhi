package com.edurip.bujhechhi.core

import java.util.Locale
import java.util.Random
import kotlin.math.max

// ---- শিক্ষকের স্ক্রিনের জন্য স্ন্যাপশট ----

data class LiveStudent(
    val sid: Long,
    val name: String,
    val status: String?,     // "G" | "Y" | "R" | null
    val online: Boolean,
    val shared: Boolean,     // একই ডিভাইসে একাধিক জন
    val qDone: Int,          // কুইজে কয়টা উত্তর দিয়েছে (step মোডে বর্তমান প্রশ্নে ০/১)
    val qOf: Int,            // কুইজ না থাকলে ০
)

data class LiveQuiz(
    val title: String,
    val mode: String,        // "step" | "all"
    val status: String,      // "running" | "locked" | "reveal"
    val cur: Int,
    val n: Int,
    val remainMs: Long,
    val questions: List<Question>,
    val counts: List<List<Int>>,   // প্রশ্ন × অপশন
    val answeredPerQ: List<Int>,
    val revealed: Set<Int>,
)

/** স্ক্রিন শেয়ারের অবস্থা (শিক্ষকের স্ক্রিনের জন্য)। quality: ০ কম ডেটা, ১ স্বাভাবিক, ২ ভালো। */
data class LiveCast(val paused: Boolean, val quality: Int)

data class LiveStroke(val k: Int, val color: String, val pts: List<Double>)

/** শেয়ার করা প্রশ্ন (ছবি/PDF-এর পাতা)। strokes শুধু বর্তমান পাতার দাগ। */
data class LiveShare(
    val id: Long,
    val n: Int,
    val page: Int,
    val visible: Boolean,
    val allowSave: Boolean,
    val strokes: List<LiveStroke>,
)

data class LiveSnapshot(
    val active: Boolean,
    val sessionId: Long,
    val title: String,
    val code: String,
    val startedAt: Long,
    val topic: String,
    val topicNo: Int,
    val topicHistory: List<String>,
    val students: List<LiveStudent>,
    val devicesOnline: Int,
    val green: Int,
    val yellow: Int,
    val red: Int,
    val none: Int,
    val alertSeq: Int,
    val quiz: LiveQuiz?,
    val share: LiveShare? = null,
    val cast: LiveCast? = null,
    val joinLocked: Boolean = false,
) {
    companion object {
        val EMPTY = LiveSnapshot(
            false, 0, "", "", 0, "", 0, emptyList(), emptyList(), 0, 0, 0, 0, 0, 0, null,
        )
    }
}

/**
 * ক্লাসের পুরো যুক্তি (state machine + প্রোটোকল)। Android বা Ktor-এর উপর নির্ভর করে না।
 * নেটওয়ার্ক স্তর শুধু [onMessage]/[onClose] ডাকে আর [Transport] দিয়ে উত্তর পাঠায়।
 */
class ClassCore(
    private val store: Store,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val random: Random = Random(),
) {
    interface Transport {
        fun send(connId: String, text: String)
        fun close(connId: String)
    }

    @Volatile var transport: Transport? = null
    @Volatile var onChange: (() -> Unit)? = null
    @Volatile var onRedAlert: (() -> Unit)? = null

    /** লাল বাটনের হার (শতাংশ) এর বেশি হলে সতর্কতা। */
    @Volatile var redThresholdPct: Int = 30
    @Volatile var redMinCount: Int = 2
    @Volatile var maxProfilesPerDevice: Int = 6

    private class Participant(val sid: Long, val name: String, var cid: String)

    private class Device(val cid: String) {
        var connId: String? = null
        val sids = LinkedHashSet<Long>()
    }

    private class Topic(val id: Long, val idx: Int, val text: String)

    private class QuizRun(val runId: Long, val set: QuizSet, val mode: String, val secsPer: Int) {
        var cur = 0
        var status = "running"
        var endsAt = 0L
        val answers = HashMap<Long, HashMap<Int, Int>>()
        val revealed = LinkedHashSet<Int>()
    }

    /** কুইজ শেষের ফল: প্রত্যেকের জন্য সঠিক/উত্তর দেওয়া সংখ্যা ও প্রশ্নভিত্তিক চিহ্ন ("1" ঠিক, "0" ভুল, "-" দেয়নি)। */
    private class ProfRes(val correct: Int, val answered: Int, val marks: String)
    private class QuizResult(val id: Long, val title: String, val n: Int, val per: Map<Long, ProfRes>, val avgPct: Int, val takers: Int)

    private class Stroke(val k: Int, val color: String) {
        val pts = ArrayList<Double>()
    }

    private class Share(val id: Long, val n: Int) {
        var page = 0
        var visible = false
        var allowSave = false
        var keySeq = 0
        val strokes = HashMap<Int, ArrayList<Stroke>>()
    }

    private val lock = Any()
    private var share: Share? = null
    private var shareSeq = 0L
    private var castOn = false
    private var castPaused = false
    private var castQ = 1
    private var session: SessionInfo? = null
    private val participants = LinkedHashMap<Long, Participant>()
    private val nameIndex = HashMap<String, Long>()
    private val devices = HashMap<String, Device>()
    private val connToCid = HashMap<String, String>()
    private var topic: Topic? = null
    private var topicNo = 0
    private val topicHistory = ArrayList<String>()
    private val fb = HashMap<Long, String>()
    private var run: QuizRun? = null
    private var lastResult: QuizResult? = null

    // নাম ↔ ফোন বাঁধন: প্রথমবার যে ফোনে (cid) ঢুকেছে নামটা সেই ফোনের; অন্য ফোন ঢুকতে পারে না
    private val bindings = HashMap<Long, String>()
    private val blockedCids = HashSet<String>()
    private val kickedSids = HashSet<Long>()
    private var joinLocked = false
    private var alertLatched = false
    private var alertSeq = 0
    private val roster = ArrayList<StudentRec>()

    private val outbox = ArrayList<Pair<String, String>>()
    private val closeQueue = ArrayList<String>()
    private var dirty = false
    private var alertPending = false

    private val cidRegex = Regex("[A-Za-z0-9_-]{6,64}")

    // ---------------------------------------------------------------- লকের ভেতরে কাজ, বাইরে পাঠানো

    private class Flush<T>(
        val result: T,
        val sends: List<Pair<String, String>>,
        val closes: List<String>,
        val changed: Boolean,
        val alert: Boolean,
    )

    private fun <T> locked(block: () -> T): T {
        val f = synchronized(lock) {
            val r = block()
            val fl = Flush(r, ArrayList(outbox), ArrayList(closeQueue), dirty, alertPending)
            outbox.clear()
            closeQueue.clear()
            dirty = false
            alertPending = false
            fl
        }
        val t = transport
        if (t != null) {
            for ((conn, text) in f.sends) t.send(conn, text)
            for (conn in f.closes) t.close(conn)
        }
        if (f.alert) onRedAlert?.invoke()
        if (f.changed) onChange?.invoke()
        return f.result
    }

    private fun out(connId: String, text: String) {
        outbox.add(connId to text)
    }

    private fun err(code: String) = Json.write(mapOf("t" to "err", "c" to code))

    // ---------------------------------------------------------------- শিক্ষকের কাজ

    fun startSession(title: String): SessionInfo = locked {
        if (session != null) endInternal()
        val t = title.trim().ifEmpty { "ক্লাস" }.take(80)
        val code = random.nextInt(10000).toString().padStart(4, '0')
        val now = clock()
        val id = store.sessionCreate(t, code, now)
        val info = SessionInfo(id, t, code, now)
        session = info
        participants.clear(); nameIndex.clear(); devices.clear(); connToCid.clear()
        topic = null; topicNo = 0; topicHistory.clear(); fb.clear(); run = null; lastResult = null; share = null; castOn = false; castPaused = false
        alertLatched = false
        roster.clear(); roster.addAll(store.rosterAll())
        bindings.clear(); bindings.putAll(store.bindings())
        blockedCids.clear(); kickedSids.clear(); joinLocked = false
        dirty = true
        info
    }

    /** ক্লাস শেষ করে সেশন আইডি ফেরত দেয় (না থাকলে ০)। */
    fun endSession(): Long = locked { endInternal() }

    private fun endInternal(): Long {
        val s = session ?: return 0L
        val ended = Json.write(mapOf("t" to "ended"))
        for (d in devices.values) {
            val c = d.connId ?: continue
            out(c, ended)
            closeQueue.add(c)
        }
        store.sessionEnd(s.id, clock())
        session = null
        blockedCids.clear(); kickedSids.clear(); joinLocked = false
        participants.clear(); nameIndex.clear(); devices.clear(); connToCid.clear()
        topic = null; fb.clear(); run = null; lastResult = null; share = null; castOn = false; castPaused = false
        dirty = true
        return s.id
    }

    fun setTopic(text: String) = locked {
        val s = session ?: return@locked
        val t = text.trim().take(500)
        if (t.isEmpty()) return@locked
        val id = store.topicCreate(s.id, topicNo, t, clock())
        topic = Topic(id, topicNo, t)
        topicNo++
        fb.clear()
        alertLatched = false
        topicHistory.remove(t)
        topicHistory.add(0, t)
        while (topicHistory.size > 8) topicHistory.removeAt(topicHistory.size - 1)
        broadcastState()
    }

    /** [block] হলে ওই ফোন এই ক্লাসে আর ঢুকতে পারবে না। */
    fun kick(sid: Long, block: Boolean = false) = locked {
        val p = participants.remove(sid) ?: return@locked
        nameIndex.remove(nameKey(p.name))
        fb.remove(sid)
        kickedSids.add(sid)
        val d = devices[p.cid]
        if (d != null) {
            d.sids.remove(sid)
            if (block) {
                blockedCids.add(p.cid)
                d.connId?.let { out(it, err("blocked")); closeQueue.add(it) }
            } else {
                pushState(d)
            }
        }
        dirty = true
    }

    /** নামটা ফোন থেকে ছাড়ানো: এখন নতুন ফোনে (বা যে কেউ) নামটা নিতে পারবে। ক্লাস না চললেও কাজ করে। */
    fun release(sid: Long) = locked {
        bindings.remove(sid)
        store.bindClear(sid)
        val p = participants.remove(sid)
        if (p != null) {
            nameIndex.remove(nameKey(p.name))
            fb.remove(sid)
            kickedSids.add(sid)
            devices[p.cid]?.let { d -> d.sids.remove(sid); pushState(d) }
        }
        dirty = true
    }

    /** শিক্ষক "নতুন যোগদান বন্ধ" করলে আগে থেকে যুক্ত/বাঁধা ফোন ছাড়া কেউ ঢুকতে পারবে না। */
    fun setJoinLocked(b: Boolean) = locked {
        joinLocked = b
        dirty = true
    }

    fun startQuiz(set: QuizSet, mode: String, secsPerQ: Int): Boolean = locked {
        val s = session ?: return@locked false
        if (set.questions.isEmpty()) return@locked false
        val m = if (mode == "all") "all" else "step"
        val secs = secsPerQ.coerceIn(5, 600)
        val runId = store.runCreate(s.id, Report.snapshotJson(set), m, clock())
        val r = QuizRun(runId, set, m, secs)
        val totalSecs = if (m == "all") secs.toLong() * set.questions.size else secs.toLong()
        r.endsAt = clock() + totalSecs * 1000
        run = r
        lastResult = null
        broadcastState()
        true
    }

    fun quizReveal() = locked {
        val r = run ?: return@locked
        r.status = "reveal"
        if (r.mode == "all") for (i in r.set.questions.indices) r.revealed.add(i) else r.revealed.add(r.cur)
        broadcastState()
    }

    /** step মোডে পরের প্রশ্ন। আর প্রশ্ন না থাকলে false। */
    fun quizNext(): Boolean = locked {
        val r = run ?: return@locked false
        if (r.mode != "step" || r.cur >= r.set.questions.size - 1) return@locked false
        r.cur++
        r.status = "running"
        r.endsAt = clock() + r.secsPer * 1000L
        broadcastState()
        true
    }

    fun quizExtend(seconds: Int) = locked {
        val r = run ?: return@locked
        if (r.status == "reveal") return@locked
        r.endsAt = max(clock(), r.endsAt) + seconds * 1000L
        r.status = "running"
        broadcastState()
    }

    fun quizEnd() = locked {
        val r = run ?: return@locked
        lastResult = buildResult(r)
        run = null
        broadcastState()
    }

    /** শেষ হওয়ার মুহূর্তে সবার ফল: যে যোগ দিয়েছে কিন্তু উত্তর দেয়নি, তার ফলও (০) থাকে। */
    private fun buildResult(r: QuizRun): QuizResult? {
        val qs = r.set.questions
        val asked = if (r.mode == "all") qs.size else {
            val curTouched = r.status == "reveal" || r.answers.values.any { it.containsKey(r.cur) }
            if (curTouched) r.cur + 1 else r.cur
        }
        if (asked <= 0) return null
        val per = HashMap<Long, ProfRes>()
        var sumPct = 0
        var takers = 0
        for (sid in participants.keys) {
            val mine = r.answers[sid]
            var c = 0
            var a = 0
            val sb = StringBuilder()
            for (i in 0 until asked) {
                val o = mine?.get(i)
                if (o == null) sb.append('-') else {
                    a++
                    if (o == qs[i].correct) { c++; sb.append('1') } else sb.append('0')
                }
            }
            per[sid] = ProfRes(c, a, sb.toString())
            if (a > 0) { sumPct += c * 100 / asked; takers++ }
        }
        return QuizResult(r.runId, r.set.title, asked, per, if (takers > 0) sumPct / takers else 0, takers)
    }

    /** প্রতি সেকেন্ডে হোস্ট এটা ডাকবে: সময় শেষ হলে উত্তর বন্ধ। */
    fun tick() = locked {
        val r = run ?: return@locked
        if (r.status == "running") {
            dirty = true
            if (clock() >= r.endsAt) {
                r.status = "locked"
                broadcastState()
            }
        }
    }

    fun refreshRoster() = locked {
        roster.clear()
        roster.addAll(store.rosterAll())
    }

    // ---------------------------------------------------------------- স্ক্রিন শেয়ার (শিক্ষার্থীরা শুধু দেখে)

    fun castStart(quality: Int) = locked {
        if (session == null) return@locked
        castOn = true
        castPaused = false
        castQ = quality.coerceIn(0, 2)
        broadcastState()
    }

    /** বিরতি: শিক্ষার্থীর পর্দা ফাঁকা হয়ে "একটু থামিয়েছেন" দেখায়; ছবি দেওয়াও বন্ধ। */
    fun castPause(paused: Boolean) = locked {
        if (!castOn || castPaused == paused) return@locked
        castPaused = paused
        broadcastState()
    }

    fun castQuality(q: Int) = locked {
        if (!castOn) return@locked
        castQ = q.coerceIn(0, 2)
        dirty = true
    }

    fun castStop() = locked {
        if (!castOn) return@locked
        castOn = false
        castPaused = false
        broadcastState()
    }

    /** স্ক্রিনের ছবি দেওয়ার আগে যাচাই: কোড মেলা, শেয়ার চালু ও বিরতিতে না থাকা। */
    fun castAllowed(code: String): Boolean = synchronized(lock) {
        val s = session ?: return false
        castOn && !castPaused && code == s.code
    }

    fun castQualityNow(): Int = synchronized(lock) { castQ }

    // ---------------------------------------------------------------- প্রশ্ন শেয়ার (ছবি/PDF পাতা)

    /** নতুন শেয়ার তৈরি (শুরুতে লুকানো)। ছবির ফাইল রাখা ও পাঠানো নেটওয়ার্ক স্তরের কাজ। আইডি ফেরত দেয়, না হলে ০। */
    fun shareSet(pages: Int): Long = locked {
        if (session == null || pages !in 1..40) return@locked 0L
        shareSeq++
        share = Share(shareSeq, pages)
        broadcastState()
        shareSeq
    }

    fun shareShow(v: Boolean) = locked {
        val sh = share ?: return@locked
        if (sh.visible == v) return@locked
        sh.visible = v
        broadcastState()
    }

    fun shareGoto(page: Int) = locked {
        val sh = share ?: return@locked
        val p = page.coerceIn(0, sh.n - 1)
        if (p == sh.page) return@locked
        sh.page = p
        broadcastState()
    }

    fun shareAllowSave(v: Boolean) = locked {
        val sh = share ?: return@locked
        if (sh.allowSave == v) return@locked
        sh.allowSave = v
        broadcastState()
    }

    fun shareRemove() = locked {
        if (share == null) return@locked
        share = null
        broadcastState()
    }

    /** নতুন দাগ শুরু; দাগের কী ফেরত দেয় (না হলে -১)। পয়েন্ট (x,y) ০..১ অনুপাতে, জোড়ায় জোড়ায়। */
    fun shareDrawStart(color: String, pts: List<Double>): Int = locked {
        val sh = share ?: return@locked -1
        val list = sh.strokes.getOrPut(sh.page) { ArrayList() }
        if (list.size >= 150) return@locked -1
        val c = if (Regex("#[0-9A-Fa-f]{6}").matches(color)) color else "#DC2626"
        val st = Stroke(++sh.keySeq, c)
        addPts(st, pts)
        list.add(st)
        sendDraw(sh, st, st.pts.toList(), false)
        dirty = true
        st.k
    }

    fun shareDrawMore(k: Int, pts: List<Double>) = locked {
        val sh = share ?: return@locked
        val st = sh.strokes[sh.page]?.firstOrNull { it.k == k } ?: return@locked
        val before = st.pts.size
        addPts(st, pts)
        if (st.pts.size > before) sendDraw(sh, st, st.pts.subList(before, st.pts.size).toList(), true)
        dirty = true
    }

    fun shareUndo() = locked {
        val sh = share ?: return@locked
        val list = sh.strokes[sh.page] ?: return@locked
        if (list.isEmpty()) return@locked
        list.removeAt(list.size - 1)
        broadcastState()
    }

    fun shareClearDraw() = locked {
        val sh = share ?: return@locked
        if (sh.strokes[sh.page].isNullOrEmpty()) return@locked
        sh.strokes.remove(sh.page)
        broadcastState()
    }

    /** ছবি পরিবেশনের আগে যাচাই: সেশনের কোড মেলা, শেয়ার দেখানো অবস্থায় থাকা, ডাউনলোডে অনুমতি। */
    fun shareAllowed(id: Long, page: Int, code: String, download: Boolean): Boolean = synchronized(lock) {
        val s = session ?: return false
        val sh = share ?: return false
        code == s.code && sh.id == id && page in 0 until sh.n && sh.visible && (!download || sh.allowSave)
    }

    private fun addPts(st: Stroke, pts: List<Double>) {
        var i = 0
        while (i + 1 < pts.size && st.pts.size < 6000) {
            val x = Math.round(pts[i].coerceIn(0.0, 1.0) * 10000) / 10000.0
            val y = Math.round(pts[i + 1].coerceIn(0.0, 1.0) * 10000) / 10000.0
            st.pts.add(x)
            st.pts.add(y)
            i += 2
        }
    }

    private fun sendDraw(sh: Share, st: Stroke, pts: List<Double>, append: Boolean) {
        if (!sh.visible) return
        val msg = Json.write(
            mapOf("t" to "sd", "id" to sh.id, "p" to sh.page, "k" to st.k, "c" to st.color, "a" to (if (append) 1 else 0), "pts" to pts),
        )
        for (d in devices.values) {
            val c = d.connId ?: continue
            out(c, msg)
        }
    }

    fun isActive(): Boolean = synchronized(lock) { session != null }

    // ---------------------------------------------------------------- নেটওয়ার্ক থেকে বার্তা

    fun onMessage(connId: String, text: String) = locked {
        val m = Json.parseOrNull(text).asMap() ?: return@locked
        when (m.str("t")) {
            "hello" -> hello(connId, m)
            "ping" -> out(connId, """{"t":"pong"}""")
            else -> {
                val cid = connToCid[connId] ?: return@locked
                val dev = devices[cid] ?: return@locked
                when (m.str("t")) {
                    "join" -> join(dev, m)
                    "leave" -> leave(dev, m)
                    "fb" -> feedback(dev, m)
                    "ans" -> answer(dev, m)
                    "roster" -> sendRoster(dev)
                }
            }
        }
    }

    fun onClose(connId: String) = locked {
        val cid = connToCid.remove(connId) ?: return@locked
        val dev = devices[cid] ?: return@locked
        if (dev.connId == connId) {
            dev.connId = null
            dirty = true
        }
    }

    private fun hello(connId: String, m: Map<*, *>) {
        val s = session
        if (s == null) {
            out(connId, err("nosession")); return
        }
        if (m.str("code") != s.code) {
            out(connId, err("badcode")); return
        }
        val cid = m.str("cid") ?: ""
        if (!cidRegex.matches(cid)) {
            out(connId, err("badcid")); return
        }
        if (cid in blockedCids) {
            out(connId, err("blocked")); closeQueue.add(connId); return
        }
        val dev = devices.getOrPut(cid) { Device(cid) }
        val old = dev.connId
        if (old != null && old != connId) {
            connToCid.remove(old)
            closeQueue.add(old)
        }
        dev.connId = connId
        connToCid[connId] = cid
        dev.sids.clear()
        for (p in participants.values) if (p.cid == cid) dev.sids.add(p.sid)
        autoJoin(dev, s)
        out(connId, stateJson(dev))
        dirty = true
    }

    /** এই ফোনে বাঁধা নামগুলো নিজে থেকেই ক্লাসে যোগ হয় — শিক্ষার্থীকে কিছুই লিখতে হয় না। */
    private fun autoJoin(dev: Device, s: SessionInfo) {
        val mine = bindings.entries.filter { it.value == dev.cid }.map { it.key }.sorted()
        for (sid in mine) {
            if (sid in participants || sid in kickedSids) continue
            if (dev.sids.size >= maxProfilesPerDevice || participants.size >= 300) break
            val rec = roster.firstOrNull { it.id == sid } ?: continue
            addParticipant(rec, dev, s)
        }
    }

    private fun addParticipant(rec: StudentRec, dev: Device, s: SessionInfo): Participant {
        val p = Participant(rec.id, rec.name, dev.cid)
        participants[p.sid] = p
        nameIndex[nameKey(p.name)] = p.sid
        store.sessionAttend(s.id, p.sid)
        dev.sids.add(p.sid)
        dirty = true
        return p
    }

    private fun bindIfFree(sid: Long, cid: String) {
        if (bindings[sid] == null) {
            bindings[sid] = cid
            store.bindSet(sid, cid)
        }
    }

    private fun join(dev: Device, m: Map<*, *>) {
        val s = session ?: return
        val connId = dev.connId ?: return
        val name = normName(m.str("name") ?: "")
        if (name.isEmpty() || name.length > 40) {
            out(connId, err("badname")); return
        }
        if (dev.cid in blockedCids) {
            out(connId, err("blocked")); return
        }
        val key = nameKey(name)
        // নামটা আগে থেকে চেনা (এই ক্লাসে বা তালিকায়) হলে তার বাঁধন যাচাই
        val knownSid = nameIndex[key]?.let { participants[it]?.sid } ?: roster.firstOrNull { nameKey(it.name) == key }?.id
        val bound = knownSid?.let { bindings[it] }
        if (bound != null && bound != dev.cid) {
            out(connId, err("owned")); return
        }
        if (joinLocked && bound != dev.cid) {
            out(connId, err("locked")); return
        }
        var existing: Participant? = nameIndex[key]?.let { participants[it] }
        if (existing != null && existing.cid == dev.cid) {
            dev.sids.add(existing.sid)
            bindIfFree(existing.sid, dev.cid)
            out(connId, joinedJson(existing.sid))
            pushState(dev)
            return
        }
        if (dev.sids.size >= maxProfilesPerDevice) {
            out(connId, err("limit")); return
        }
        var rec: StudentRec? = null
        if (existing == null) {
            if (participants.size >= 300) {
                out(connId, err("full")); return
            }
            rec = store.rosterFindOrCreate(name)
            if (roster.none { it.id == rec.id }) roster.add(rec)
            existing = participants[rec.id]
        }
        if (existing != null) {
            if (existing.cid == dev.cid) {
                dev.sids.add(existing.sid)
                bindIfFree(existing.sid, dev.cid)
                out(connId, joinedJson(existing.sid))
                pushState(dev)
                return
            }
            val other = devices[existing.cid]
            if (other != null && other.connId != null) {
                out(connId, err("taken")); return
            }
            other?.sids?.remove(existing.sid)
            existing.cid = dev.cid
            dev.sids.add(existing.sid)
            bindIfFree(existing.sid, dev.cid)
            out(connId, joinedJson(existing.sid))
            pushState(dev)
            dirty = true
            return
        }
        val r = rec ?: return
        val p = addParticipant(r, dev, s)
        kickedSids.remove(p.sid)
        bindIfFree(p.sid, dev.cid)
        out(connId, joinedJson(p.sid))
        pushState(dev)
        dirty = true
    }

    /** "ভুল নাম? সরিয়ে দিন": ক্লাস থেকে সরে, নামের বাঁধনও ছাড়ে (তাই পরের বার নিজে থেকে যোগ হবে না)। */
    private fun leave(dev: Device, m: Map<*, *>) {
        val sid = m.long("sid") ?: return
        if (!dev.sids.remove(sid)) return
        val p = participants.remove(sid)
        if (p != null) nameIndex.remove(nameKey(p.name))
        if (bindings[sid] == dev.cid) {
            bindings.remove(sid)
            store.bindClear(sid)
        }
        pushState(dev)
        dirty = true
    }

    private fun feedback(dev: Device, m: Map<*, *>) {
        val connId = dev.connId ?: return
        val sid = m.long("sid") ?: return
        val v = m.str("v") ?: return
        if (sid !in dev.sids || (v != "G" && v != "Y" && v != "R")) return
        val t = topic
        if (t == null) {
            out(connId, err("notopic")); return
        }
        fb[sid] = v
        store.responseSave(t.id, sid, v, clock())
        checkAlert()
        pushState(dev)
        dirty = true
    }

    private fun answer(dev: Device, m: Map<*, *>) {
        val sid = m.long("sid") ?: return
        val q = m.int("q") ?: return
        val o = m.int("o") ?: return
        if (sid !in dev.sids) return
        val r = run
        if (r == null || r.status != "running" || clock() >= r.endsAt) {
            pushState(dev); return
        }
        val qs = r.set.questions
        val okIndex = if (r.mode == "step") q == r.cur else q in qs.indices
        if (!okIndex || q !in qs.indices) return
        val question = qs[q]
        if (o !in question.options.indices) return
        r.answers.getOrPut(sid) { HashMap() }[q] = o
        store.answerSave(r.runId, q, sid, o, o == question.correct, clock())
        pushState(dev)
        dirty = true
    }

    /** শুধু নিজের নাম ও এখনও কারও না-হওয়া নাম পাঠায়; অন্য ফোনে বাঁধা নাম লুকানো থাকে। */
    private fun sendRoster(dev: Device) {
        val connId = dev.connId ?: return
        val list = roster
            .filter { val b = bindings[it.id]; b == dev.cid || (b == null && !joinLocked) }
            .sortedBy { it.name.lowercase(Locale.ROOT) }.take(400).map {
                mapOf(
                    "n" to it.name,
                    "j" to if (nameIndex.containsKey(nameKey(it.name))) 1 else 0,
                    "m" to if (bindings[it.id] == dev.cid) 1 else 0,
                )
            }
        out(connId, Json.write(mapOf("t" to "roster", "list" to list)))
    }

    private fun checkAlert() {
        val total = participants.size
        if (total == 0) return
        val reds = participants.keys.count { fb[it] == "R" }
        val trigger = reds >= redMinCount && reds * 100 >= redThresholdPct * total
        if (trigger && !alertLatched) {
            alertLatched = true
            alertSeq++
            alertPending = true
        } else if (!trigger && reds * 200 < redThresholdPct * total) {
            alertLatched = false
        }
    }

    // ---------------------------------------------------------------- বার্তা বানানো

    private fun joinedJson(sid: Long) = Json.write(mapOf("t" to "joined", "sid" to sid))

    private fun pushState(dev: Device) {
        val c = dev.connId ?: return
        out(c, stateJson(dev))
    }

    private fun broadcastState() {
        for (d in devices.values) pushState(d)
        dirty = true
    }

    private fun stateJson(dev: Device): String {
        val s = session ?: return err("nosession")
        val r = run
        val profiles = dev.sids.mapNotNull { sid ->
            val p = participants[sid] ?: return@mapNotNull null
            val m = LinkedHashMap<String, Any?>()
            m["sid"] = sid
            m["name"] = p.name
            m["fb"] = fb[sid]
            if (r != null) {
                val mine = r.answers[sid]
                val a = LinkedHashMap<String, Int>()
                if (mine != null) for ((k, v) in mine) a[k.toString()] = v
                m["a"] = a
            }
            val pr = lastResult?.per?.get(sid)
            if (r == null && pr != null) m["qr"] = mapOf("c" to pr.correct, "a" to pr.answered, "m" to pr.marks)
            m
        }
        val quiz: Any? = if (r == null) {
            null
        } else {
            val qs = r.set.questions
            val last = if (r.mode == "step") r.cur else qs.size - 1
            mapOf(
                "title" to r.set.title,
                "mode" to r.mode,
                "status" to r.status,
                "cur" to r.cur,
                "n" to qs.size,
                "remain" to (if (r.status == "running") max(0L, r.endsAt - clock()) else 0L),
                "total" to (if (r.mode == "all") r.secsPer.toLong() * qs.size else r.secsPer.toLong()) * 1000L,
                "qs" to (0..last).map { i -> mapOf("q" to qs[i].text, "o" to qs[i].options) },
                "rev" to r.revealed.filter { it <= last }.map { i ->
                    mapOf("i" to i, "c" to qs[i].correct, "e" to qs[i].explanation)
                },
            )
        }
        val t = topic
        val sh = share
        val shareView: Any? = if (sh == null || !sh.visible) {
            null
        } else {
            mapOf(
                "id" to sh.id,
                "n" to sh.n,
                "p" to sh.page,
                "sv" to (if (sh.allowSave) 1 else 0),
                "d" to (sh.strokes[sh.page] ?: emptyList<Stroke>()).map { mapOf("k" to it.k, "c" to it.color, "pts" to it.pts) },
            )
        }
        return Json.write(
            mapOf(
                "t" to "state",
                "title" to s.title,
                "code" to s.code,
                "tid" to (t?.id ?: 0L),
                "topic" to (t?.text ?: ""),
                "profiles" to profiles,
                "quiz" to quiz,
                "qr" to lastResult?.let { mapOf("id" to it.id, "title" to it.title, "n" to it.n, "avg" to it.avgPct, "tk" to it.takers) },
                "sh" to shareView,
                "sc" to (if (castOn) mapOf("p" to (if (castPaused) 1 else 0)) else null),
            ),
        )
    }

    // ---------------------------------------------------------------- শিক্ষকের স্ন্যাপশট

    fun snapshot(): LiveSnapshot = synchronized(lock) {
        val s = session ?: return LiveSnapshot.EMPTY
        val r = run
        val students = participants.values.map { p ->
            val d = devices[p.cid]
            var qDone = 0
            var qOf = 0
            if (r != null) {
                val mine = r.answers[p.sid]
                if (r.mode == "step") {
                    qOf = 1
                    qDone = if (mine?.containsKey(r.cur) == true) 1 else 0
                } else {
                    qOf = r.set.questions.size
                    qDone = mine?.size ?: 0
                }
            }
            LiveStudent(
                sid = p.sid,
                name = p.name,
                status = fb[p.sid],
                online = d?.connId != null,
                shared = (d?.sids?.size ?: 0) > 1,
                qDone = qDone,
                qOf = qOf,
            )
        }
        val g = students.count { it.status == "G" }
        val y = students.count { it.status == "Y" }
        val rd = students.count { it.status == "R" }

        val liveQuiz = r?.let { run0 ->
            val qs = run0.set.questions
            val counts = qs.indices.map { qi ->
                val c = IntArray(qs[qi].options.size)
                for ((sid, mine) in run0.answers) {
                    if (!participants.containsKey(sid)) continue
                    val o = mine[qi] ?: continue
                    if (o in c.indices) c[o]++
                }
                c.toList()
            }
            LiveQuiz(
                title = run0.set.title,
                mode = run0.mode,
                status = run0.status,
                cur = run0.cur,
                n = qs.size,
                remainMs = if (run0.status == "running") max(0L, run0.endsAt - clock()) else 0L,
                questions = qs,
                counts = counts,
                answeredPerQ = counts.map { it.sum() },
                revealed = LinkedHashSet(run0.revealed),
            )
        }
        LiveSnapshot(
            active = true,
            sessionId = s.id,
            title = s.title,
            code = s.code,
            startedAt = s.startedAt,
            topic = topic?.text ?: "",
            topicNo = topicNo,
            topicHistory = ArrayList(topicHistory),
            students = students,
            devicesOnline = devices.values.count { it.connId != null },
            green = g,
            yellow = y,
            red = rd,
            none = students.size - g - y - rd,
            alertSeq = alertSeq,
            joinLocked = joinLocked,
            quiz = liveQuiz,
            cast = if (castOn) LiveCast(castPaused, castQ) else null,
            share = share?.let { sh ->
                LiveShare(
                    sh.id, sh.n, sh.page, sh.visible, sh.allowSave,
                    (sh.strokes[sh.page] ?: emptyList<Stroke>()).map { LiveStroke(it.k, it.color, ArrayList(it.pts)) },
                )
            },
        )
    }

    // ---------------------------------------------------------------- নাম

    private fun normName(s: String): String = cleanName(s)

    private fun nameKey(s: String): String = normName(s).lowercase(Locale.ROOT)
}
