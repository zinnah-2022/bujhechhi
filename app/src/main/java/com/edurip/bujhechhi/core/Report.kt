package com.edurip.bujhechhi.core

/** কাঁচা সারি থেকে রিপোর্ট ও পরিসংখ্যান বানায়। কোনো Android নির্ভরতা নেই। */
object Report {

    /** snapshotJson: {"title":"..","qs":[{"q":"..","o":[..],"c":1,"e":".."}]} */
    fun parseSnapshot(json: String): Pair<String, List<Question>> {
        val m = Json.parseOrNull(json).asMap() ?: return "" to emptyList()
        val title = m.str("title") ?: ""
        val qs = (m["qs"].asList() ?: emptyList<Any?>()).mapNotNull { q ->
            val qm = q.asMap() ?: return@mapNotNull null
            Question(
                text = qm.str("q") ?: "",
                options = (qm["o"].asList() ?: emptyList<Any?>()).map { it?.toString() ?: "" },
                correct = qm.int("c") ?: 0,
                explanation = qm.str("e") ?: "",
            )
        }
        return title to qs
    }

    fun snapshotJson(set: QuizSet): String = Json.write(
        mapOf(
            "title" to set.title,
            "qs" to set.questions.map {
                mapOf("q" to it.text, "o" to it.options, "c" to it.correct, "e" to it.explanation)
            },
        ),
    )

    fun build(raw: SessionRaw): SessionReport {
        val nameOf = raw.attendees.associate { it.id to it.name }
        val attendeeCount = raw.attendees.size

        val topicReports = raw.topics.sortedBy { it.idx }.map { t ->
            val rs = raw.responses.filter { it.topicId == t.id }
            val green = rs.count { it.status == "G" }
            val yellow = rs.count { it.status == "Y" }
            val red = rs.count { it.status == "R" }
            TopicReport(
                text = t.text,
                green = green,
                yellow = yellow,
                red = red,
                none = (attendeeCount - rs.size).coerceAtLeast(0),
                redNames = rs.filter { it.status == "R" }.mapNotNull { nameOf[it.studentId] }.sorted(),
            )
        }

        val wrongByStudent = HashMap<Long, Int>()
        val runReports = raw.runs.map { run ->
            val (title, qs) = parseSnapshot(run.snapshotJson)
            val ans = raw.answers.filter { it.runId == run.id }
            val qReports = qs.mapIndexed { i, q ->
                val qa = ans.filter { it.qIdx == i }
                val correct = qa.count { it.correct }
                val wrongOpts = qa.filter { !it.correct }.groupingBy { it.opt }.eachCount()
                val top = wrongOpts.maxByOrNull { it.value }?.key
                QuestionReport(
                    text = q.text,
                    answered = qa.size,
                    correct = correct,
                    correctPct = if (qa.isEmpty()) 0 else (correct * 100 + qa.size / 2) / qa.size,
                    topWrongOption = top?.let { q.options.getOrNull(it) },
                )
            }
            for (a in ans) if (!a.correct) wrongByStudent[a.studentId] = (wrongByStudent[a.studentId] ?: 0) + 1
            val scores = ans.groupBy { it.studentId }.map { (sid, list) ->
                ScoreRow(nameOf[sid] ?: "?", list.count { it.correct }, qs.size)
            }.sortedWith(compareByDescending<ScoreRow> { it.correct }.thenBy { it.name })
            RunReport(title, qReports, scores)
        }

        val redByStudent = HashMap<Long, Int>()
        for (r in raw.responses) if (r.status == "R") redByStudent[r.studentId] = (redByStudent[r.studentId] ?: 0) + 1

        val struggling = (redByStudent.keys + wrongByStudent.keys).map { sid ->
            StruggleRow(nameOf[sid] ?: "?", redByStudent[sid] ?: 0, wrongByStudent[sid] ?: 0)
        }.filter { it.reds + it.wrongs > 0 }
            .sortedWith(compareByDescending<StruggleRow> { it.reds * 2 + it.wrongs }.thenBy { it.name })

        return SessionReport(
            meta = raw.meta,
            attendeeNames = raw.attendees.map { it.name }.sorted(),
            topics = topicReports,
            runs = runReports,
            struggling = struggling,
        )
    }

    /** সব ক্লাস মিলিয়ে প্রতিটি শিক্ষার্থীর পরিসংখ্যান। */
    fun studentStats(roster: List<StudentRec>, sessions: List<SessionRaw>): List<StudentStat> {
        val g = HashMap<Long, Int>()
        val y = HashMap<Long, Int>()
        val r = HashMap<Long, Int>()
        val qc = HashMap<Long, Int>()
        val qt = HashMap<Long, Int>()
        val attended = HashMap<Long, Int>()
        for (s in sessions) {
            for (a in s.attendees) attended[a.id] = (attended[a.id] ?: 0) + 1
            for (x in s.responses) {
                val map = when (x.status) {
                    "G" -> g
                    "Y" -> y
                    else -> r
                }
                map[x.studentId] = (map[x.studentId] ?: 0) + 1
            }
            for (a in s.answers) {
                qt[a.studentId] = (qt[a.studentId] ?: 0) + 1
                if (a.correct) qc[a.studentId] = (qc[a.studentId] ?: 0) + 1
            }
        }
        return roster.map {
            StudentStat(
                id = it.id,
                name = it.name,
                sessions = attended[it.id] ?: 0,
                green = g[it.id] ?: 0,
                yellow = y[it.id] ?: 0,
                red = r[it.id] ?: 0,
                quizCorrect = qc[it.id] ?: 0,
                quizTotal = qt[it.id] ?: 0,
            )
        }
    }
}
