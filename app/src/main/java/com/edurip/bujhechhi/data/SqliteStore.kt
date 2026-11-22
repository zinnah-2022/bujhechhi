package com.edurip.bujhechhi.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.edurip.bujhechhi.core.AnsRaw
import com.edurip.bujhechhi.core.QuizSet
import com.edurip.bujhechhi.core.Report
import com.edurip.bujhechhi.core.RespRaw
import com.edurip.bujhechhi.core.RunRaw
import com.edurip.bujhechhi.core.SessionMeta
import com.edurip.bujhechhi.core.SessionRaw
import com.edurip.bujhechhi.core.StudentRec
import com.edurip.bujhechhi.core.Store
import com.edurip.bujhechhi.core.TopicRaw
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** SQL স্টেটমেন্টগুলো এক জায়গায়, যাতে আলাদাভাবে যাচাই করা যায়। */
object Sql {
    val SCHEMA = listOf(
        "CREATE TABLE student(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE COLLATE NOCASE, created INTEGER NOT NULL)",
        "CREATE TABLE session(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, code TEXT NOT NULL, started INTEGER NOT NULL, ended INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE attend(session_id INTEGER NOT NULL REFERENCES session(id) ON DELETE CASCADE, student_id INTEGER NOT NULL REFERENCES student(id) ON DELETE CASCADE, PRIMARY KEY(session_id, student_id))",
        "CREATE TABLE topic(id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL REFERENCES session(id) ON DELETE CASCADE, idx INTEGER NOT NULL, text TEXT NOT NULL, at INTEGER NOT NULL)",
        "CREATE TABLE response(topic_id INTEGER NOT NULL REFERENCES topic(id) ON DELETE CASCADE, student_id INTEGER NOT NULL REFERENCES student(id) ON DELETE CASCADE, status TEXT NOT NULL, at INTEGER NOT NULL, PRIMARY KEY(topic_id, student_id))",
        "CREATE TABLE quiz_set(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, created INTEGER NOT NULL, body TEXT NOT NULL)",
        "CREATE TABLE run(id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL REFERENCES session(id) ON DELETE CASCADE, snapshot TEXT NOT NULL, mode TEXT NOT NULL, at INTEGER NOT NULL)",
        "CREATE TABLE answer(run_id INTEGER NOT NULL REFERENCES run(id) ON DELETE CASCADE, q_idx INTEGER NOT NULL, student_id INTEGER NOT NULL REFERENCES student(id) ON DELETE CASCADE, opt INTEGER NOT NULL, correct INTEGER NOT NULL, at INTEGER NOT NULL, PRIMARY KEY(run_id, q_idx, student_id))",
        BIND_TABLE,
        "CREATE INDEX idx_topic_session ON topic(session_id)",
        "CREATE INDEX idx_run_session ON run(session_id)",
    )

    const val BIND_TABLE =
        "CREATE TABLE IF NOT EXISTS student_dev(student_id INTEGER PRIMARY KEY REFERENCES student(id) ON DELETE CASCADE, cid TEXT NOT NULL)"
    const val BIND_ALL = "SELECT student_id, cid FROM student_dev"
    const val BIND_SET = "INSERT OR REPLACE INTO student_dev(student_id, cid) VALUES(?, ?)"
    const val BIND_CLEAR = "DELETE FROM student_dev WHERE student_id = ?"
    const val CLOSE_DANGLING = "UPDATE session SET ended = started WHERE ended = 0"
    const val ROSTER_ALL = "SELECT id, name FROM student ORDER BY name COLLATE NOCASE"
    const val ROSTER_FIND = "SELECT id, name FROM student WHERE name = ?"
    const val ROSTER_INSERT = "INSERT OR IGNORE INTO student(name, created) VALUES(?, ?)"
    const val ROSTER_DELETE = "DELETE FROM student WHERE id = ?"
    const val SESSION_INSERT = "INSERT INTO session(title, code, started) VALUES(?, ?, ?)"
    const val SESSION_END = "UPDATE session SET ended = ? WHERE id = ?"
    const val ATTEND = "INSERT OR IGNORE INTO attend(session_id, student_id) VALUES(?, ?)"
    const val TOPIC_INSERT = "INSERT INTO topic(session_id, idx, text, at) VALUES(?, ?, ?, ?)"
    const val RESPONSE_SAVE = "INSERT OR REPLACE INTO response(topic_id, student_id, status, at) VALUES(?, ?, ?, ?)"
    const val RUN_INSERT = "INSERT INTO run(session_id, snapshot, mode, at) VALUES(?, ?, ?, ?)"
    const val ANSWER_SAVE = "INSERT OR REPLACE INTO answer(run_id, q_idx, student_id, opt, correct, at) VALUES(?, ?, ?, ?, ?, ?)"
    const val QUIZ_ALL = "SELECT id, title, body FROM quiz_set ORDER BY created DESC, id DESC"
    const val QUIZ_INSERT = "INSERT INTO quiz_set(title, created, body) VALUES(?, ?, ?)"
    const val QUIZ_UPDATE = "UPDATE quiz_set SET title = ?, body = ? WHERE id = ?"
    const val QUIZ_DELETE = "DELETE FROM quiz_set WHERE id = ?"
    const val SESSION_LIST =
        "SELECT s.id, s.title, s.started, s.ended, " +
            "(SELECT COUNT(*) FROM attend a WHERE a.session_id = s.id), " +
            "(SELECT COUNT(*) FROM topic t WHERE t.session_id = s.id) " +
            "FROM session s ORDER BY s.started DESC, s.id DESC"
    const val SESSION_ONE =
        "SELECT s.id, s.title, s.started, s.ended, " +
            "(SELECT COUNT(*) FROM attend a WHERE a.session_id = s.id), " +
            "(SELECT COUNT(*) FROM topic t WHERE t.session_id = s.id) " +
            "FROM session s WHERE s.id = ?"
    const val ATTENDEES =
        "SELECT st.id, st.name FROM attend a JOIN student st ON st.id = a.student_id WHERE a.session_id = ? ORDER BY st.name COLLATE NOCASE"
    const val TOPICS = "SELECT id, idx, text FROM topic WHERE session_id = ? ORDER BY idx"
    const val RESPONSES =
        "SELECT r.topic_id, r.student_id, r.status FROM response r JOIN topic t ON t.id = r.topic_id WHERE t.session_id = ?"
    const val RUNS = "SELECT id, snapshot, mode FROM run WHERE session_id = ? ORDER BY id"
    const val ANSWERS =
        "SELECT a.run_id, a.q_idx, a.student_id, a.opt, a.correct FROM answer a JOIN run r ON r.id = a.run_id WHERE r.session_id = ?"
    const val SESSION_DELETE = "DELETE FROM session WHERE id = ?"
}

/**
 * SQLite স্টোর। সব কাজ একটি আলাদা থ্রেডে ক্রমানুসারে চলে, তাই লেখার পরে পড়লে
 * সবসময় নতুন তথ্য পাওয়া যায়, আর নেটওয়ার্ক থ্রেড আটকায় না।
 */
class SqliteStore(context: Context) : Store {

    private class Helper(ctx: Context) : SQLiteOpenHelper(ctx, "bujhechhi.db", null, 2) {
        override fun onConfigure(db: SQLiteDatabase) {
            super.onConfigure(db)
            db.setForeignKeyConstraintsEnabled(true)
        }

        override fun onCreate(db: SQLiteDatabase) {
            for (s in Sql.SCHEMA) db.execSQL(s)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) db.execSQL(Sql.BIND_TABLE)
        }

        override fun onOpen(db: SQLiteDatabase) {
            super.onOpen(db)
            db.execSQL(Sql.CLOSE_DANGLING)
        }
    }

    private val helper = Helper(context.applicationContext)
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "bj-db").apply { isDaemon = true } }

    private fun <T> sync(block: (SQLiteDatabase) -> T): T =
        exec.submit(Callable { block(helper.writableDatabase) }).get()

    private fun async(block: (SQLiteDatabase) -> Unit) {
        exec.execute {
            try {
                block(helper.writableDatabase)
            } catch (e: Exception) {
                android.util.Log.w("SqliteStore", "async write failed", e)
            }
        }
    }

    // ------------------------------------------------------------ তালিকা

    override fun rosterAll(): List<StudentRec> = sync { db ->
        db.rawQuery(Sql.ROSTER_ALL, null).use { c ->
            val out = ArrayList<StudentRec>()
            while (c.moveToNext()) out.add(StudentRec(c.getLong(0), c.getString(1)))
            out
        }
    }

    override fun rosterFindOrCreate(name: String): StudentRec = sync { db ->
        db.execSQL(Sql.ROSTER_INSERT, arrayOf<Any>(name, System.currentTimeMillis()))
        db.rawQuery(Sql.ROSTER_FIND, arrayOf(name)).use { c ->
            c.moveToFirst()
            StudentRec(c.getLong(0), c.getString(1))
        }
    }

    override fun bindings(): Map<Long, String> = sync { db ->
        db.rawQuery(Sql.BIND_ALL, null).use { c ->
            val out = HashMap<Long, String>()
            while (c.moveToNext()) out[c.getLong(0)] = c.getString(1)
            out
        }
    }

    override fun bindSet(studentId: Long, cid: String) {
        sync { db -> db.execSQL(Sql.BIND_SET, arrayOf<Any>(studentId, cid)) }
    }

    override fun bindClear(studentId: Long) {
        sync { db -> db.execSQL(Sql.BIND_CLEAR, arrayOf<Any>(studentId)) }
    }

    override fun rosterDelete(id: Long) {
        sync { db -> db.execSQL(Sql.BIND_CLEAR, arrayOf<Any>(id)); db.execSQL(Sql.ROSTER_DELETE, arrayOf<Any>(id)) }
    }

    // ------------------------------------------------------------ ক্লাস

    override fun sessionCreate(title: String, code: String, startedAt: Long): Long = sync { db ->
        val cv = ContentValues()
        cv.put("title", title)
        cv.put("code", code)
        cv.put("started", startedAt)
        db.insertOrThrow("session", null, cv)
    }

    override fun sessionEnd(id: Long, endedAt: Long) {
        async { db -> db.execSQL(Sql.SESSION_END, arrayOf<Any>(endedAt, id)) }
    }

    override fun sessionAttend(sessionId: Long, studentId: Long) {
        async { db -> db.execSQL(Sql.ATTEND, arrayOf<Any>(sessionId, studentId)) }
    }

    override fun topicCreate(sessionId: Long, idx: Int, text: String, at: Long): Long = sync { db ->
        val cv = ContentValues()
        cv.put("session_id", sessionId)
        cv.put("idx", idx)
        cv.put("text", text)
        cv.put("at", at)
        db.insertOrThrow("topic", null, cv)
    }

    override fun responseSave(topicId: Long, studentId: Long, status: String, at: Long) {
        async { db -> db.execSQL(Sql.RESPONSE_SAVE, arrayOf<Any>(topicId, studentId, status, at)) }
    }

    override fun runCreate(sessionId: Long, snapshotJson: String, mode: String, at: Long): Long = sync { db ->
        val cv = ContentValues()
        cv.put("session_id", sessionId)
        cv.put("snapshot", snapshotJson)
        cv.put("mode", mode)
        cv.put("at", at)
        db.insertOrThrow("run", null, cv)
    }

    override fun answerSave(runId: Long, qIdx: Int, studentId: Long, opt: Int, correct: Boolean, at: Long) {
        async { db ->
            db.execSQL(
                Sql.ANSWER_SAVE,
                arrayOf<Any>(runId, qIdx, studentId, opt, if (correct) 1 else 0, at),
            )
        }
    }

    // ------------------------------------------------------------ কুইজ ব্যাংক

    override fun quizSets(): List<QuizSet> = sync { db ->
        db.rawQuery(Sql.QUIZ_ALL, null).use { c ->
            val out = ArrayList<QuizSet>()
            while (c.moveToNext()) {
                val (_, qs) = Report.parseSnapshot(c.getString(2))
                out.add(QuizSet(c.getLong(0), c.getString(1), qs))
            }
            out
        }
    }

    override fun quizSave(set: QuizSet): Long = sync { db ->
        val body = Report.snapshotJson(set)
        if (set.id == 0L) {
            val cv = ContentValues()
            cv.put("title", set.title)
            cv.put("created", System.currentTimeMillis())
            cv.put("body", body)
            db.insertOrThrow("quiz_set", null, cv)
        } else {
            db.execSQL(Sql.QUIZ_UPDATE, arrayOf<Any>(set.title, body, set.id))
            set.id
        }
    }

    override fun quizDelete(id: Long) {
        sync { db -> db.execSQL(Sql.QUIZ_DELETE, arrayOf<Any>(id)) }
    }

    // ------------------------------------------------------------ ইতিহাস

    private fun readMeta(c: android.database.Cursor) = SessionMeta(
        id = c.getLong(0),
        title = c.getString(1),
        startedAt = c.getLong(2),
        endedAt = c.getLong(3),
        attendees = c.getInt(4),
        topics = c.getInt(5),
    )

    override fun sessionList(): List<SessionMeta> = sync { db ->
        db.rawQuery(Sql.SESSION_LIST, null).use { c ->
            val out = ArrayList<SessionMeta>()
            while (c.moveToNext()) out.add(readMeta(c))
            out
        }
    }

    override fun sessionRaw(id: Long): SessionRaw? = sync { db ->
        val arg = arrayOf(id.toString())
        val meta = db.rawQuery(Sql.SESSION_ONE, arg).use { c -> if (c.moveToFirst()) readMeta(c) else null }
            ?: return@sync null
        val attendees = db.rawQuery(Sql.ATTENDEES, arg).use { c ->
            val l = ArrayList<StudentRec>()
            while (c.moveToNext()) l.add(StudentRec(c.getLong(0), c.getString(1)))
            l
        }
        val topics = db.rawQuery(Sql.TOPICS, arg).use { c ->
            val l = ArrayList<TopicRaw>()
            while (c.moveToNext()) l.add(TopicRaw(c.getLong(0), c.getInt(1), c.getString(2)))
            l
        }
        val responses = db.rawQuery(Sql.RESPONSES, arg).use { c ->
            val l = ArrayList<RespRaw>()
            while (c.moveToNext()) l.add(RespRaw(c.getLong(0), c.getLong(1), c.getString(2)))
            l
        }
        val runs = db.rawQuery(Sql.RUNS, arg).use { c ->
            val l = ArrayList<RunRaw>()
            while (c.moveToNext()) l.add(RunRaw(c.getLong(0), c.getString(1), c.getString(2)))
            l
        }
        val answers = db.rawQuery(Sql.ANSWERS, arg).use { c ->
            val l = ArrayList<AnsRaw>()
            while (c.moveToNext()) {
                l.add(AnsRaw(c.getLong(0), c.getInt(1), c.getLong(2), c.getInt(3), c.getInt(4) == 1))
            }
            l
        }
        SessionRaw(meta, attendees, topics, responses, runs, answers)
    }

    override fun sessionDelete(id: Long) {
        sync { db -> db.execSQL(Sql.SESSION_DELETE, arrayOf<Any>(id)) }
    }
}
