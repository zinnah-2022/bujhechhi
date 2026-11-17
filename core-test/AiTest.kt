package com.edurip.bujhechhi.core
fun main() {
    val txt = "এই নিন:\n```json\n[{\"q\":\"১+১?\",\"o\":[\"১\",\"২\",\"৩\",\"৪\"],\"c\":1,\"e\":\"দুই\"},{\"q\":\"bad\",\"o\":[\"a\"],\"c\":0},{\"q\":\"x\",\"o\":[\"a\",\"b\"],\"c\":5}]\n```"
    val qs = AiQuiz.parseQuestions(txt)
    check("ai parse keeps valid only", qs.size == 1 && qs[0].correct == 1 && qs[0].explanation == "দুই")
    val body = Json.write(mapOf("content" to listOf(mapOf("type" to "text", "text" to "[]"), mapOf("type" to "x"))))
    check("extract text", AiQuiz.extractText(body) == "[]")
    check("request body has model", AiQuiz.buildRequestBody("m", "p\"q").contains("\"model\":\"m\"") && AiQuiz.buildRequestBody("m", "p\"q").contains("p\\\"q"))
    check("snapshot roundtrip", Report.parseSnapshot(Report.snapshotJson(QuizSet(1, "T", qs))).second == qs)
    println(if (fails == 0) "AI OK" else "AI FAIL")
}
