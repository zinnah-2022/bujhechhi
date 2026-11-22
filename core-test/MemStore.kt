package com.edurip.bujhechhi.core

/** টেস্টের জন্য মেমোরি স্টোর। */
class MemStore : Store {
    private var seq = 0L
    private fun nid() = ++seq
    val roster = ArrayList<StudentRec>()
    class S(val id: Long, val title: String, val code: String, val started: Long) { var ended = 0L; val att = LinkedHashSet<Long>() }
    val sessions = ArrayList<S>()
    val topics = ArrayList<Triple<Long, Long, TopicRaw>>() // id, sessionId
    val responses = HashMap<Pair<Long, Long>, String>()
    val runs = ArrayList<Triple<Long, Long, RunRaw>>()
    val answers = HashMap<Triple<Long, Int, Long>, Pair<Int, Boolean>>()
    val quizzes = ArrayList<QuizSet>()

    override fun rosterAll() = roster.toList()
    override fun rosterFindOrCreate(name: String): StudentRec =
        roster.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: StudentRec(nid(), name).also { roster.add(it) }
    override fun rosterDelete(id: Long) { roster.removeAll { it.id == id }; bind.remove(id) }
    val bind = HashMap<Long, String>()
    override fun bindings(): Map<Long, String> = HashMap(bind)
    override fun bindSet(studentId: Long, cid: String) { bind[studentId] = cid }
    override fun bindClear(studentId: Long) { bind.remove(studentId) }
    override fun sessionCreate(title: String, code: String, startedAt: Long): Long { val s = S(nid(), title, code, startedAt); sessions.add(s); return s.id }
    override fun sessionEnd(id: Long, endedAt: Long) { sessions.first { it.id == id }.ended = endedAt }
    override fun sessionAttend(sessionId: Long, studentId: Long) { sessions.first { it.id == sessionId }.att.add(studentId) }
    override fun topicCreate(sessionId: Long, idx: Int, text: String, at: Long): Long { val id = nid(); topics.add(Triple(id, sessionId, TopicRaw(id, idx, text))); return id }
    override fun responseSave(topicId: Long, studentId: Long, status: String, at: Long) { responses[topicId to studentId] = status }
    override fun runCreate(sessionId: Long, snapshotJson: String, mode: String, at: Long): Long { val id = nid(); runs.add(Triple(id, sessionId, RunRaw(id, snapshotJson, mode))); return id }
    override fun answerSave(runId: Long, qIdx: Int, studentId: Long, opt: Int, correct: Boolean, at: Long) { answers[Triple(runId, qIdx, studentId)] = opt to correct }
    override fun quizSets() = quizzes.toList()
    override fun quizSave(set: QuizSet): Long { val id = if (set.id == 0L) nid() else set.id; quizzes.removeAll { it.id == id }; quizzes.add(set.copy(id = id)); return id }
    override fun quizDelete(id: Long) { quizzes.removeAll { it.id == id } }
    override fun sessionList() = sessions.map { meta(it) }
    private fun meta(s: S) = SessionMeta(s.id, s.title, s.started, s.ended, s.att.size, topics.count { it.second == s.id })
    override fun sessionRaw(id: Long): SessionRaw? {
        val s = sessions.firstOrNull { it.id == id } ?: return null
        val tps = topics.filter { it.second == id }.map { it.third }
        val tids = tps.map { it.id }.toSet()
        val rs = runs.filter { it.second == id }.map { it.third }
        val rids = rs.map { it.id }.toSet()
        return SessionRaw(
            meta(s),
            s.att.mapNotNull { a -> roster.firstOrNull { it.id == a } },
            tps,
            responses.filter { it.key.first in tids }.map { RespRaw(it.key.first, it.key.second, it.value) },
            rs,
            answers.filter { it.key.first in rids }.map { AnsRaw(it.key.first, it.key.second, it.key.third, it.value.first, it.value.second) },
        )
    }
    override fun sessionDelete(id: Long) { sessions.removeAll { it.id == id } }
}
