package com.edurip.bujhechhi.core

/** নাম পরিষ্কার: NFC (বাংলা য় এক করতে), বাড়তি ফাঁকা বাদ। */
fun cleanName(s: String): String =
    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC).trim().replace(Regex("\\s+"), " ")

/** তালিকাভুক্ত শিক্ষার্থী (সব ক্লাসে একই আইডি)। */
data class StudentRec(val id: Long, val name: String)

data class Question(
    val text: String,
    val options: List<String>,
    val correct: Int,
    val explanation: String = "",
)

data class QuizSet(val id: Long, val title: String, val questions: List<Question>)

data class SessionInfo(val id: Long, val title: String, val code: String, val startedAt: Long)

data class SessionMeta(
    val id: Long,
    val title: String,
    val startedAt: Long,
    val endedAt: Long,   // 0 হলে এখনও শেষ হয়নি
    val attendees: Int,
    val topics: Int,
)

// ---- ডাটাবেজ থেকে আসা কাঁচা সারি (রিপোর্ট বানানোর জন্য) ----

data class TopicRaw(val id: Long, val idx: Int, val text: String)
data class RespRaw(val topicId: Long, val studentId: Long, val status: String)
data class RunRaw(val id: Long, val snapshotJson: String, val mode: String)
data class AnsRaw(val runId: Long, val qIdx: Int, val studentId: Long, val opt: Int, val correct: Boolean)

data class SessionRaw(
    val meta: SessionMeta,
    val attendees: List<StudentRec>,
    val topics: List<TopicRaw>,
    val responses: List<RespRaw>,
    val runs: List<RunRaw>,
    val answers: List<AnsRaw>,
)

/** সংরক্ষণের ইন্টারফেস। Android-এ SQLite, টেস্টে মেমোরি। */
interface Store {
    // তালিকা
    fun rosterAll(): List<StudentRec>
    fun rosterFindOrCreate(name: String): StudentRec
    fun rosterDelete(id: Long)

    // নাম ↔ ফোন বাঁধন (প্রথমবার যে ফোনে ঢুকেছে, নামটা সেই ফোনের)
    fun bindings(): Map<Long, String>
    fun bindSet(studentId: Long, cid: String)
    fun bindClear(studentId: Long)

    // ক্লাস
    fun sessionCreate(title: String, code: String, startedAt: Long): Long
    fun sessionEnd(id: Long, endedAt: Long)
    fun sessionAttend(sessionId: Long, studentId: Long)
    fun topicCreate(sessionId: Long, idx: Int, text: String, at: Long): Long
    fun responseSave(topicId: Long, studentId: Long, status: String, at: Long)
    fun runCreate(sessionId: Long, snapshotJson: String, mode: String, at: Long): Long
    fun answerSave(runId: Long, qIdx: Int, studentId: Long, opt: Int, correct: Boolean, at: Long)

    // কুইজ ব্যাংক
    fun quizSets(): List<QuizSet>
    fun quizSave(set: QuizSet): Long
    fun quizDelete(id: Long)

    // ইতিহাস ও রিপোর্ট
    fun sessionList(): List<SessionMeta>
    fun sessionRaw(id: Long): SessionRaw?
    fun sessionDelete(id: Long)
}

// ---- রিপোর্টের ফলাফল ----

data class TopicReport(
    val text: String,
    val green: Int,
    val yellow: Int,
    val red: Int,
    val none: Int,
    val redNames: List<String>,
)

data class QuestionReport(
    val text: String,
    val answered: Int,
    val correct: Int,
    val correctPct: Int,
    val topWrongOption: String?,
)

data class ScoreRow(val name: String, val correct: Int, val total: Int)

data class RunReport(val title: String, val questions: List<QuestionReport>, val scores: List<ScoreRow>)

data class StruggleRow(val name: String, val reds: Int, val wrongs: Int)

data class SessionReport(
    val meta: SessionMeta,
    val attendeeNames: List<String>,
    val topics: List<TopicReport>,
    val runs: List<RunReport>,
    val struggling: List<StruggleRow>,
)

data class StudentStat(
    val id: Long,
    val name: String,
    val sessions: Int,
    val green: Int,
    val yellow: Int,
    val red: Int,
    val quizCorrect: Int,
    val quizTotal: Int,
)
